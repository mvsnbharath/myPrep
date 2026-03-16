import java.util.*;

/**
 * ===== OPTION 1: synchronized =====
 *
 * The simplest thread-safety approach. Every public method is synchronized
 * on the same intrinsic lock (the instance itself).
 *
 * HOW IT WORKS:
 * - `synchronized` acquires a mutual-exclusion lock on entry, releases on exit.
 * - Only ONE thread can be inside any synchronized method at a time.
 * - Other threads block (wait in a queue) until the lock is released.
 *
 * ADVANTAGES:
 * - Dead simple to implement — just add `synchronized` keyword.
 * - Easy to reason about correctness — no concurrent access to shared state.
 * - No extra dependencies or classes needed.
 *
 * DISADVANTAGES:
 * - All threads serialize through a single lock, even if they're only reading.
 * - Under high concurrency, getNext() becomes a bottleneck — threads queue up.
 * - No distinction between read and write operations.
 * - If getNext() does any slow work (logging, metrics), all threads stall.
 *
 * WHEN TO USE:
 * - Low-to-moderate concurrency (< 10 threads).
 * - When simplicity matters more than throughput.
 * - Prototyping or internal tools.
 */
public class SynchronizedBalancer {
    private final List<String> backends;
    private final Set<String> unhealthy;
    private int current;

    public SynchronizedBalancer(List<String> backends) {
        this.backends = new ArrayList<>(backends);
        this.unhealthy = new HashSet<>();
        this.current = 0;
    }

    /**
     * synchronized = only one thread can execute this at a time.
     * While one thread is inside getNext(), all other threads calling
     * getNext(), markHealthy(), or markUnhealthy() will BLOCK and wait.
     */
    public synchronized String getNext() {
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
    }

    public synchronized void markUnhealthy(String backend) {
        unhealthy.add(backend);
    }

    public synchronized void markHealthy(String backend) {
        unhealthy.remove(backend);
    }

    public synchronized Set<String> getUnhealthy() {
        return new HashSet<>(unhealthy);
    }
}
