import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent test suite for AtomicBalancer.
 * Tests correctness, distribution, health changes, and stress.
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

    private static void assertEqual(String expected, String actual, String msg) {
        if (Objects.equals(expected, actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + msg
                + " — expected \"" + expected + "\", got \"" + actual + "\"");
        }
    }

    // =========================================================================
    // Test 1: Basic round-robin in single-threaded mode
    // =========================================================================
    static void testBasicRoundRobin() {
        System.out.println("Test 1: Basic Round Robin (single-threaded)");
        AtomicBalancer lb = new AtomicBalancer(List.of("A", "B", "C"));
        assertEqual("A", lb.getNext(), "1st call");
        assertEqual("B", lb.getNext(), "2nd call");
        assertEqual("C", lb.getNext(), "3rd call");
        assertEqual("A", lb.getNext(), "4th call wraps around");
    }

    // =========================================================================
    // Test 2: Unhealthy nodes are skipped
    // =========================================================================
    static void testSkipUnhealthy() {
        System.out.println("Test 2: Skip Unhealthy");
        AtomicBalancer lb = new AtomicBalancer(List.of("A", "B", "C"));
        lb.markUnhealthy("B");
        assertEqual("A", lb.getNext(), "1st call");
        assertEqual("C", lb.getNext(), "2nd call skips B");
        assertEqual("C", lb.getNext(), "3rd call — B's slot consumed, C again");
    }

    // =========================================================================
    // Test 3: Multi-threaded — each backend gets roughly equal load
    // =========================================================================
    static void testConcurrentDistribution() throws Exception {
        System.out.println("Test 3: Concurrent Distribution");

        int numThreads = 10;
        int callsPerThread = 1000;
        int totalCalls = numThreads * callsPerThread;
        List<String> servers = List.of("A", "B", "C");

        AtomicBalancer lb = new AtomicBalancer(servers);
        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        for (String s : servers) counts.put(s, new AtomicInteger(0));

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        String result = lb.getNext();
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

        int sum = counts.values().stream().mapToInt(AtomicInteger::get).sum();
        assertTrue(sum == totalCalls, "total routed = " + totalCalls + " (got " + sum + ")");

        int expected = totalCalls / servers.size();
        for (String s : servers) {
            int count = counts.get(s).get();
            boolean inRange = count >= expected * 0.8 && count <= expected * 1.2;
            assertTrue(inRange, s + " got " + count + " calls (expected ~" + expected + " ±20%)");
        }
    }

    // =========================================================================
    // Test 4: Health toggling during concurrent routing — no crashes
    // =========================================================================
    static void testConcurrentHealthChanges() throws Exception {
        System.out.println("Test 4: Health Changes During Concurrent Routing");

        AtomicBalancer lb = new AtomicBalancer(List.of("A", "B", "C"));
        int numThreads = 8;
        int callsPerThread = 500;
        AtomicInteger errorCount = new AtomicInteger(0);
        Set<String> seen = ConcurrentHashMap.newKeySet();

        ExecutorService pool = Executors.newFixedThreadPool(numThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            String r = lb.getNext();
                            if (r != null) seen.add(r);
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

        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < 100; i++) {
                    lb.markUnhealthy("B");
                    Thread.sleep(1);
                    lb.markHealthy("B");
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(errorCount.get() == 0, "no exceptions (got " + errorCount.get() + ")");
        assertTrue(seen.contains("A"), "A was routed at least once");
        assertTrue(seen.contains("C"), "C was routed at least once");
    }

    // =========================================================================
    // Test 5: All unhealthy under concurrency — returns null, no crash
    // =========================================================================
    static void testAllUnhealthyConcurrent() throws Exception {
        System.out.println("Test 5: All Unhealthy Under Concurrency");

        AtomicBalancer lb = new AtomicBalancer(List.of("A", "B"));
        lb.markUnhealthy("A");
        lb.markUnhealthy("B");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger nonNull = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        if (lb.getNext() != null) nonNull.incrementAndGet();
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(nonNull.get() == 0, "all calls returned null (non-null: " + nonNull.get() + ")");
    }

    // =========================================================================
    // Test 6: Stress — 50 threads, 10k calls each, no crashes
    // =========================================================================
    static void testStress() throws Exception {
        System.out.println("Test 6: Stress Test");

        AtomicBalancer lb = new AtomicBalancer(List.of("S1", "S2", "S3", "S4", "S5"));
        int numThreads = 50;
        int callsPerThread = 10_000;
        AtomicInteger routed = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            String r = lb.getNext();
                            if (r != null) routed.incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally { latch.countDown(); }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(errors.get() == 0, "no exceptions (got " + errors.get() + ")");
        assertTrue(routed.get() == numThreads * callsPerThread,
            "all " + (numThreads * callsPerThread) + " routed (got " + routed.get() + ")");
    }

    // =========================================================================
    public static void main(String[] args) throws Exception {
        testBasicRoundRobin();
        testSkipUnhealthy();
        testConcurrentDistribution();
        testConcurrentHealthChanges();
        testAllUnhealthyConcurrent();
        testStress();
        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
    }
}
