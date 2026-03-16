import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent test harness that tests all three balancer implementations
 * under multi-threaded conditions.
 *
 * Tests:
 *  1. Basic single-threaded round-robin (sanity check)
 *  2. Multi-threaded getNext() — all backends get roughly equal load
 *  3. Health changes during concurrent routing
 *  4. All unhealthy under concurrency — no exceptions, all return null
 *  5. Stress test — high thread count, no crashes
 */
public class ConcurrentBalancerTest {
    private static int passed = 0;
    private static int failed = 0;

    private static void assertTrue(boolean condition, String msg) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + msg);
        }
    }

    // =========================================================================
    // Test 1: Basic single-threaded sanity check for all three implementations
    // =========================================================================
    static void testBasicRoundRobin() {
        System.out.println("Test 1: Basic Round Robin (single-threaded)");

        // Synchronized
        SynchronizedBalancer sb = new SynchronizedBalancer(List.of("A", "B", "C"));
        assertTrue("A".equals(sb.getNext()), "Sync: 1st call = A");
        assertTrue("B".equals(sb.getNext()), "Sync: 2nd call = B");
        assertTrue("C".equals(sb.getNext()), "Sync: 3rd call = C");
        assertTrue("A".equals(sb.getNext()), "Sync: 4th call wraps to A");

        // ReadWriteLock
        ReadWriteLockBalancer rwb = new ReadWriteLockBalancer(List.of("A", "B", "C"));
        assertTrue("A".equals(rwb.getNext()), "RWLock: 1st call = A");
        assertTrue("B".equals(rwb.getNext()), "RWLock: 2nd call = B");
        assertTrue("C".equals(rwb.getNext()), "RWLock: 3rd call = C");
        assertTrue("A".equals(rwb.getNext()), "RWLock: 4th call wraps to A");

        // Atomic
        AtomicBalancer ab = new AtomicBalancer(List.of("A", "B", "C"));
        assertTrue("A".equals(ab.getNext()), "Atomic: 1st call = A");
        assertTrue("B".equals(ab.getNext()), "Atomic: 2nd call = B");
        assertTrue("C".equals(ab.getNext()), "Atomic: 3rd call = C");
        assertTrue("A".equals(ab.getNext()), "Atomic: 4th call wraps to A");
    }

    // =========================================================================
    // Test 2: Multi-threaded distribution — all backends get roughly equal load
    // =========================================================================
    static void testConcurrentDistribution() throws Exception {
        System.out.println("Test 2: Concurrent Distribution (multi-threaded)");

        int numThreads = 10;
        int callsPerThread = 1000;
        int totalCalls = numThreads * callsPerThread;
        List<String> servers = List.of("A", "B", "C");

        testDistribution("Sync", new SynchronizedBalancer(servers), numThreads, callsPerThread, servers, totalCalls);
        testDistribution("RWLock", new ReadWriteLockBalancer(servers), numThreads, callsPerThread, servers, totalCalls);
        testDistribution("Atomic", new AtomicBalancer(servers), numThreads, callsPerThread, servers, totalCalls);
    }

    /** Helper: run N threads, each calling getNext() M times, verify distribution */
    private static void testDistribution(String label, Object balancer,
            int numThreads, int callsPerThread, List<String> servers, int totalCalls) throws Exception {

        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        for (String s : servers) counts.put(s, new AtomicInteger(0));

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        String result = null;
                        if (balancer instanceof SynchronizedBalancer)
                            result = ((SynchronizedBalancer) balancer).getNext();
                        else if (balancer instanceof ReadWriteLockBalancer)
                            result = ((ReadWriteLockBalancer) balancer).getNext();
                        else if (balancer instanceof AtomicBalancer)
                            result = ((AtomicBalancer) balancer).getNext();

                        if (result != null) {
                            counts.get(result).incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Verify: total calls distributed, and no server gets 0
        int sum = counts.values().stream().mapToInt(AtomicInteger::get).sum();
        assertTrue(sum == totalCalls, label + ": total routed = " + totalCalls + " (got " + sum + ")");

        // Each server should get roughly 1/3 of calls (within 20% tolerance)
        int expected = totalCalls / servers.size();
        double tolerance = 0.2;
        for (String s : servers) {
            int count = counts.get(s).get();
            boolean inRange = count >= expected * (1 - tolerance) && count <= expected * (1 + tolerance);
            assertTrue(inRange, label + ": " + s + " got " + count
                + " calls (expected ~" + expected + " ±20%)");
        }
    }

    // =========================================================================
    // Test 3: Health changes during concurrent routing
    // =========================================================================
    static void testConcurrentHealthChanges() throws Exception {
        System.out.println("Test 3: Health Changes During Concurrent Routing");

        // Test with Atomic (most interesting case for concurrency)
        AtomicBalancer ab = new AtomicBalancer(List.of("A", "B", "C"));

        int numThreads = 8;
        int callsPerThread = 500;
        Set<String> validResults = ConcurrentHashMap.newKeySet();
        AtomicInteger nullCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(numThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(numThreads);

        // Reader threads: call getNext()
        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            String result = ab.getNext();
                            if (result != null) validResults.add(result);
                            else nullCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Writer thread: toggle health while readers are active
        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < 100; i++) {
                    ab.markUnhealthy("B");
                    Thread.sleep(1);
                    ab.markHealthy("B");
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        start.countDown(); // Release all threads at once
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(errorCount.get() == 0, "Atomic: no exceptions thrown (got " + errorCount.get() + ")");
        assertTrue(validResults.contains("A"), "Atomic: A was routed at least once");
        assertTrue(validResults.contains("C"), "Atomic: C was routed at least once");
    }

    // =========================================================================
    // Test 4: All unhealthy under concurrency — should return null, not crash
    // =========================================================================
    static void testAllUnhealthyConcurrent() throws Exception {
        System.out.println("Test 4: All Unhealthy Under Concurrency");

        SynchronizedBalancer sb = new SynchronizedBalancer(List.of("A", "B"));
        sb.markUnhealthy("A");
        sb.markUnhealthy("B");

        AtomicBalancer ab = new AtomicBalancer(List.of("A", "B"));
        ab.markUnhealthy("A");
        ab.markUnhealthy("B");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads * 2);
        AtomicInteger nonNullCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        if (sb.getNext() != null) nonNullCount.incrementAndGet();
                    }
                } finally { latch.countDown(); }
            });
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        if (ab.getNext() != null) nonNullCount.incrementAndGet();
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(nonNullCount.get() == 0, "All unhealthy: every call returned null (non-null count: " + nonNullCount.get() + ")");
    }

    // =========================================================================
    // Test 5: Stress test — many threads, no crashes
    // =========================================================================
    static void testStress() throws Exception {
        System.out.println("Test 5: Stress Test (50 threads, 10k calls each)");

        List<String> servers = List.of("S1", "S2", "S3", "S4", "S5");
        AtomicBalancer ab = new AtomicBalancer(servers);

        int numThreads = 50;
        int callsPerThread = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger totalRouted = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            String result = ab.getNext();
                            if (result != null) totalRouted.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(errors.get() == 0, "Stress: no exceptions (got " + errors.get() + ")");
        assertTrue(totalRouted.get() == numThreads * callsPerThread,
            "Stress: all " + (numThreads * callsPerThread) + " calls routed (got " + totalRouted.get() + ")");
    }

    // =========================================================================
    public static void main(String[] args) throws Exception {
        testBasicRoundRobin();
        testConcurrentDistribution();
        testConcurrentHealthChanges();
        testAllUnhealthyConcurrent();
        testStress();
        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
    }
}
