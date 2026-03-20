import java.util.*;

public class ConsistentHashTest {
    private static int passed = 0;
    private static int failed = 0;

    private static void assertTrue(boolean condition, String msg) {
        if (condition) { passed++; }
        else { failed++; System.out.println("  FAIL: " + msg); }
    }

    private static void assertEqual(Object expected, Object actual, String msg) {
        if (Objects.equals(expected, actual)) { passed++; }
        else { failed++; System.out.println("  FAIL: " + msg
            + " — expected " + expected + ", got " + actual); }
    }

    // =========================================================================
    // Test 1: Empty ring returns null
    // =========================================================================
    static void testEmptyRing() {
        System.out.println("Test 1: Empty Ring");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        assertEqual(null, router.route("anyKey"), "empty ring → null");
        assertEqual(0, router.getRingSize(), "ring size = 0");
    }

    // =========================================================================
    // Test 2: Single node — all keys route to it
    // =========================================================================
    static void testSingleNode() {
        System.out.println("Test 2: Single Node");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("ServerA");
        assertEqual("ServerA", router.route("user:1"), "key 1 → ServerA");
        assertEqual("ServerA", router.route("user:2"), "key 2 → ServerA");
        assertEqual("ServerA", router.route("order:999"), "key 3 → ServerA");
        assertEqual(150, router.getRingSize(), "ring has 150 virtual nodes");
    }

    // =========================================================================
    // Test 3: Same key always routes to same node (deterministic)
    // =========================================================================
    static void testDeterministic() {
        System.out.println("Test 3: Deterministic Routing");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("A");
        router.addNode("B");
        router.addNode("C");

        String result1 = router.route("user:42");
        String result2 = router.route("user:42");
        String result3 = router.route("user:42");
        assertEqual(result1, result2, "same key → same node (call 2)");
        assertEqual(result1, result3, "same key → same node (call 3)");
    }

    // =========================================================================
    // Test 4: Keys distribute across nodes (not all to one)
    // =========================================================================
    static void testDistribution() {
        System.out.println("Test 4: Distribution");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("A");
        router.addNode("B");
        router.addNode("C");

        Map<String, Integer> counts = new HashMap<>();
        counts.put("A", 0);
        counts.put("B", 0);
        counts.put("C", 0);

        int totalKeys = 10000;
        for (int i = 0; i < totalKeys; i++) {
            String node = router.route("key:" + i);
            counts.merge(node, 1, Integer::sum);
        }

        System.out.println("    Distribution: " + counts);

        // Each node should get roughly 1/3 of keys (within 15% tolerance)
        int expected = totalKeys / 3;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            boolean inRange = e.getValue() >= expected * 0.85 && e.getValue() <= expected * 1.15;
            assertTrue(inRange, e.getKey() + " got " + e.getValue()
                + " keys (expected ~" + expected + " ±15%)");
        }
    }

    // =========================================================================
    // Test 5: Removing a node — only that node's keys move
    // =========================================================================
    static void testMinimalRemapping() {
        System.out.println("Test 5: Minimal Remapping on Node Removal");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("A");
        router.addNode("B");
        router.addNode("C");

        // Record where each key goes BEFORE removal
        int totalKeys = 10000;
        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            before.put(key, router.route(key));
        }

        // Remove node B
        router.removeNode("B");

        // Check how many keys moved
        int moved = 0;
        int stayed = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            String after = router.route(key);
            if (!after.equals(before.get(key))) {
                moved++;
                // Keys that moved should have been on B before
                assertTrue("B".equals(before.get(key)),
                    key + " moved but wasn't on B (was on " + before.get(key) + ")");
            } else {
                stayed++;
            }
        }

        System.out.println("    Moved: " + moved + ", Stayed: " + stayed);

        // Only ~1/3 of keys should move (the ones that were on B)
        assertTrue(moved < totalKeys * 0.45,
            "too many keys moved: " + moved + " (expected < " + (int)(totalKeys * 0.45) + ")");
        assertTrue(moved > totalKeys * 0.15,
            "too few keys moved: " + moved + " (expected > " + (int)(totalKeys * 0.15) + ")");
    }

    // =========================================================================
    // Test 6: Adding a node — takes ~1/N of keys from each existing node
    // =========================================================================
    static void testAddNode() {
        System.out.println("Test 6: Adding a Node");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("A");
        router.addNode("B");

        int totalKeys = 10000;
        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            before.put(key, router.route(key));
        }

        // Add node C
        router.addNode("C");

        int movedToC = 0;
        int stayed = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            String after = router.route(key);
            if (!after.equals(before.get(key))) {
                movedToC++;
                // Moved keys should now be on C
                assertEqual("C", after, key + " moved but not to C");
            } else {
                stayed++;
            }
        }

        System.out.println("    Moved to C: " + movedToC + ", Stayed: " + stayed);

        // C should take roughly 1/3 of all keys
        assertTrue(movedToC > totalKeys * 0.2,
            "C got too few keys: " + movedToC);
        assertTrue(movedToC < totalKeys * 0.45,
            "C got too many keys: " + movedToC);
    }

    // =========================================================================
    // Test 7: Node re-added gets same keys back
    // =========================================================================
    static void testReaddNode() {
        System.out.println("Test 7: Re-add Node");
        ConsistentHashRouter router = new ConsistentHashRouter(150);
        router.addNode("A");
        router.addNode("B");
        router.addNode("C");

        // Record routing with all 3 nodes
        int totalKeys = 1000;
        Map<String, String> withAll = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            withAll.put(key, router.route(key));
        }

        // Remove and re-add B
        router.removeNode("B");
        router.addNode("B");

        // Routing should be identical
        int mismatches = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            if (!router.route(key).equals(withAll.get(key))) {
                mismatches++;
            }
        }

        assertEqual(0, mismatches, "re-adding node restores exact same routing");
    }

    // =========================================================================
    public static void main(String[] args) {
        testEmptyRing();
        testSingleNode();
        testDeterministic();
        testDistribution();
        testMinimalRemapping();
        testAddNode();
        testReaddNode();
        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");
    }
}
