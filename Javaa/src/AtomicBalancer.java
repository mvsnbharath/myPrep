import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ===== OPTION 3: AtomicInteger + ConcurrentHashMap (Lock-Free Reads) =====
 *
 * The most performant approach for high-concurrency load balancing.
 * getNext() is effectively lock-free on the hot path.
 *
 * HOW IT WORKS:
 * - AtomicInteger uses CPU-level CAS (Compare-And-Swap) instructions.
 *   getAndIncrement() atomically reads and increments without locking.
 *   Each thread gets a UNIQUE counter value — no two threads get the same index.
 * - ConcurrentHashMap.newKeySet() provides a thread-safe Set without locking
 *   for reads (uses internal striped locking only for writes).
 * - The backend list is never mutated after construction, so it's safe to read
 *   from multiple threads. Health changes only touch the concurrent set.
 *
 * KEY INSIGHT:
 * - The counter grows unbounded (0, 1, 2, 3, ...) and we use modulo to wrap.
 * - AtomicInteger wraps around at Integer.MAX_VALUE → Integer.MIN_VALUE,
 *   so we use Math.floorMod() to handle negative values correctly.
 * - Each thread independently scans for a healthy backend starting from its
 *   assigned index — no contention on the hot path.
 *
 * ADVANTAGES:
 * - getNext() never blocks other getNext() calls — true parallelism.
 * - Under high load (1000s of threads), dramatically better than synchronized.
 * - No deadlock risk since there are no locks on the read path.
 * - Scales linearly with CPU cores.
 *
 * DISADVANTAGES:
 * - More complex to reason about correctness.
 * - "Round-robin" is approximate under concurrency: if thread A gets index 5
 *   and thread B gets index 6, B might finish first — so responses arrive
 *   out of order. True round-robin ordering is impossible without locking.
 * - Health changes are eventually consistent: a thread might start scanning
 *   just before a markUnhealthy() call and still route to that backend.
 *   (This is usually acceptable — the request will fail and retry.)
 * - AtomicInteger overflow: wraps to negative after ~2 billion calls.
 *   Math.floorMod handles this, but it's a subtlety you must know about.
 *
 * WHEN TO USE:
 * - High-concurrency production load balancers.
 * - When getNext() is in the critical latency path.
 * - When you can tolerate approximate ordering and eventual consistency.
 */
public class AtomicBalancer {
    // Immutable list — never modified after construction.
    // Safe for concurrent reads without synchronization.
    private final List<String> backends;

    // Thread-safe set. Reads don't block. Writes use fine-grained striped locks.
    private final Set<String> unhealthy;

    // Lock-free atomic counter. getAndIncrement() is a single CPU instruction (CAS).
    // Each call gets a unique value — no two threads ever get the same index.
    private final AtomicInteger counter;

    public AtomicBalancer(List<String> backends) {
        // Store as unmodifiable — guarantees no accidental mutation.
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
        this.unhealthy = ConcurrentHashMap.newKeySet();
        this.counter = new AtomicInteger(0);
    }

    /**
     * LOCK-FREE getNext().
     *
     * Step 1: Atomically grab and increment the counter.
     *         If two threads call this simultaneously, one gets 0, the other gets 1.
     *         Neither blocks.
     *
     * Step 2: Use the counter value to find the starting index (modulo list size).
     *         Math.floorMod handles negative numbers from integer overflow.
     *
     * Step 3: Scan forward for a healthy backend. The scan reads the concurrent set
     *         which is also lock-free for lookups.
     *
     * No locks are held at any point during this method.
     */
    public String getNext() {
        int n = backends.size();
        if (n == 0) {
            return null;
        }

        // Atomic: each thread gets a unique value.
        int start = counter.getAndIncrement();

        for (int i = 0; i < n; i++) {
            // Math.floorMod ensures non-negative result even if start overflows
            // to negative. Regular % in Java can return negative for negative inputs.
            //   -1 % 3 = -1  (wrong for array indexing!)
            //   Math.floorMod(-1, 3) = 2  (correct)
            int idx = Math.floorMod(start + i, n);
            String backend = backends.get(idx);
            if (!unhealthy.contains(backend)) {
                return backend;
            }
        }
        return null;
    }

    /**
     * ConcurrentHashMap.newKeySet().add() is thread-safe.
     * Internally uses striped locking — only locks a small segment of the map,
     * so concurrent adds to different segments don't block each other.
     */
    public void markUnhealthy(String backend) {
        unhealthy.add(backend);
    }

    public void markHealthy(String backend) {
        unhealthy.remove(backend);
    }

    public Set<String> getUnhealthy() {
        // Return a snapshot — caller gets a consistent point-in-time view.
        return new HashSet<>(unhealthy);
    }
}
