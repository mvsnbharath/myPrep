import java.util.*;

public class RoundRobinTest {
    private static int passed = 0;
    private static int failed = 0;

    private static void assertEqual(String expected, String actual, String msg) {
        if (Objects.equals(expected, actual)) {
            passed++;
        } else {
            failed++;
            System.out.println("  FAIL: " + msg
                + " — expected \"" + expected + "\", got \"" + actual + "\"");
        }
    }

    // Test 1: Basic round-robin cycles through all backends in order
    static void testBasicRoundRobin() {
        System.out.println("Test 1: Basic Round Robin");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of("A", "B", "C"));
        assertEqual("A", lb.getNext(), "1st call");
        assertEqual("B", lb.getNext(), "2nd call");
        assertEqual("C", lb.getNext(), "3rd call");
        assertEqual("A", lb.getNext(), "4th call wraps around");
    }

    // Test 2: Unhealthy nodes are skipped
    static void testSkipUnhealthy() {
        System.out.println("Test 2: Skip Unhealthy");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of("A", "B", "C"));
        lb.markUnhealthy("B");
        assertEqual("A", lb.getNext(), "1st call");
        assertEqual("C", lb.getNext(), "2nd call skips B");
        assertEqual("A", lb.getNext(), "3rd call wraps");
    }

    // Test 3: Returns null when all backends are unhealthy
    static void testAllUnhealthy() {
        System.out.println("Test 3: All Unhealthy");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of("A", "B"));
        lb.markUnhealthy("A");
        lb.markUnhealthy("B");
        assertEqual(null, lb.getNext(), "all unhealthy returns null");
    }

    // Test 4: Returns null for empty backend list
    static void testEmptyBackends() {
        System.out.println("Test 4: Empty Backends");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of());
        assertEqual(null, lb.getNext(), "empty list returns null");
    }

    // Test 5: Node recovers and is included again
    static void testRecovery() {
        System.out.println("Test 5: Recovery");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of("A", "B", "C"));
        assertEqual("A", lb.getNext(), "1st call");
        lb.markUnhealthy("B");
        assertEqual("C", lb.getNext(), "2nd call skips B");
        lb.markHealthy("B");
        assertEqual("B", lb.getNext(), "B recovered, should be next");
    }

    // Test 6: Only one healthy node — should always return it
    static void testSingleHealthy() {
        System.out.println("Test 6: Single Healthy");
        RoundRobinBalancer lb = new RoundRobinBalancer(List.of("A", "B", "C"));
        lb.markUnhealthy("A");
        lb.markUnhealthy("C");
        assertEqual("B", lb.getNext(), "only B is healthy");
        assertEqual("B", lb.getNext(), "still only B");
    }

    public static void main(String[] args) {
        testBasicRoundRobin();
        testSkipUnhealthy();
        testAllUnhealthy();
        testEmptyBackends();
        testRecovery();
        testSingleHealthy();
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
    }
}
