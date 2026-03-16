import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ===== OPTION 2: ReentrantReadWriteLock =====
 *
 * A more granular locking approach that distinguishes between readers and writers.
 *
 * HOW IT WORKS:
 * - A ReadWriteLock has two locks: a READ lock and a WRITE lock.
 * - Multiple threads can hold the READ lock simultaneously (shared access).
 * - Only ONE thread can hold the WRITE lock, and NO readers can be active.
 * - This allows concurrent reads while writes are exclusive.
 *
 * THE CATCH FOR THIS USE CASE:
 * - getNext() both READS the backend list AND WRITES the `current` index.
 * - So getNext() needs the WRITE lock, not the read lock!
 * - This means in practice, getNext() calls still serialize.
 * - The read lock is only useful if we add read-only methods (e.g., getStatus()).
 *
 * ADVANTAGES:
 * - If you had read-only queries (get backend count, check health status),
 *   they could run concurrently without blocking each other.
 * - Explicit lock/unlock gives more control over critical sections.
 * - Reentrant: the same thread can acquire the lock multiple times without deadlock.
 * - Fair mode available: threads served in FIFO order (prevents starvation).
 *
 * DISADVANTAGES:
 * - More boilerplate than synchronized (try/finally blocks).
 * - For this specific use case, getNext() needs a write lock anyway,
 *   so it doesn't improve throughput much over synchronized.
 * - Easy to forget unlock in error paths → deadlock (must use try/finally).
 * - Slightly higher memory overhead than intrinsic locks.
 *
 * WHEN TO USE:
 * - When you have many readers and few writers.
 * - When read operations are expensive and you want them concurrent.
 * - When you need fairness guarantees.
 */
public class ReadWriteLockBalancer {
    private final List<String> backends;
    private final Set<String> unhealthy;
    private int current;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public ReadWriteLockBalancer(List<String> backends) {
        this.backends = new ArrayList<>(backends);
        this.unhealthy = new HashSet<>();
        this.current = 0;
    }

    /**
     * Uses WRITE lock because we mutate `current`.
     * If getNext() only read state, we could use rwLock.readLock() instead,
     * and multiple getNext() calls could run in parallel.
     */
    public String getNext() {
        rwLock.writeLock().lock();
        try {
            if (backends.isEmpty()) {
                return null;
            }

            int n = backends.size();
            for (int i = 0; i < n; i++) {
                int idx = (current + i) % n;
                String backend = backends.get(idx);
                if (!unhealthy.contains(backend)) {
                    current = idx + 1;
                    return backend;
                }
            }
            return null;
        } finally {
            // ALWAYS unlock in finally — if we throw an exception,
            // without this the lock is held forever → deadlock.
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Write lock — exclusive access. No readers or other writers allowed.
     */
    public void markUnhealthy(String backend) {
        rwLock.writeLock().lock();
        try {
            unhealthy.add(backend);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void markHealthy(String backend) {
        rwLock.writeLock().lock();
        try {
            unhealthy.remove(backend);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * READ lock — this is where ReadWriteLock shines.
     * Multiple threads can call this concurrently without blocking each other.
     * They only block if a writer is active.
     */
    public Set<String> getUnhealthy() {
        rwLock.readLock().lock();
        try {
            return new HashSet<>(unhealthy);
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
