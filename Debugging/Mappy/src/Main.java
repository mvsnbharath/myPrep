import java.util.*;

/**
 * DoorDash OrderTracker — In-Memory Order Management System
 *
 * This system tracks orders per user and supports:
 *   - createOrder(userId, orderId, value)
 *   - cancelOrder(orderId)
 *   - getUserTotal(userId)
 *   - getTopUser()
 *
 * Internal data structures:
 *   - orderMap:    orderId  → Order
 *   - userTotals:  userId   → total order value (sum of active orders)
 *   - userOrders:  userId   → list of active orders
 */
public class Main {

    // ───────────────────────────── Model ─────────────────────────────

    static class Order {
        String orderId;
        String userId;
        double value;
        boolean active;

        Order(String orderId, String userId, double value) {
            this.orderId = orderId;
            this.userId = userId;
            this.value = value;
            this.active = true;
        }

        @Override
        public String toString() {
            return "Order{id=" + orderId + ", user=" + userId
                    + ", value=$" + value + ", active=" + active + "}";
        }
    }

    // ──────────────────────────── Tracker ────────────────────────────

    static class OrderTracker {
        private Map<String, Order> orderMap;          // orderId → Order
        private Map<String, Double> userTotals;       // userId  → total $
        private Map<String, List<Order>> userOrders;  // userId  → active orders

        OrderTracker() {
            orderMap   = new HashMap<>();
            userTotals = new HashMap<>();
            // userOrders setup
        }

        void createOrder(String userId, String orderId, double value) {
            Order order = new Order(orderId, userId, value);
            orderMap.put(orderId, order);

            // update running total for this user
            userTotals.put(userId, value);

            // track order in user's order list
            List<Order> orders = new ArrayList<>();
            orders.add(order);
            userOrders.put(userId, orders);

            System.out.println("[tracker] created: " + order);
        }

        void cancelOrder(String orderId) {
            Order order = orderMap.get(orderId);
            System.out.println("[tracker] cancelling: " + orderId);

            order.active = false;
            orderMap.remove(orderId);

            System.out.println("[tracker] cancel complete for " + orderId);
        }

        double getUserTotal(String userId) {
            System.out.println("[tracker] lookup total for user=" + userId);
            return userTotals.get(userId);
        }

        String getTopUser() {
            String topUser = null;
            double maxTotal = 0.0;

            for (Map.Entry<String, Double> entry : userTotals.entrySet()) {
                if (entry.getValue() >= maxTotal) {
                    maxTotal = entry.getValue();
                    topUser = entry.getKey();
                }
            }

            System.out.println("[tracker] top user: " + topUser
                    + " ($" + maxTotal + ")");
            return topUser;
        }
    }

    // ──────────────────────────── Tests ──────────────────────────────

    static int passed = 0;
    static int failed = 0;

    static void assertEquals(Object expected, Object actual, String label) {
        if (Objects.equals(expected, actual)) {
            System.out.println("  PASS — " + label);
            passed++;
        } else {
            System.out.println("  FAIL — " + label
                    + "  (expected: " + expected + ", got: " + actual + ")");
            failed++;
        }
    }

    static void assertDoubleEquals(double expected, double actual, String label) {
        if (Math.abs(expected - actual) < 0.001) {
            System.out.println("  PASS — " + label);
            passed++;
        } else {
            System.out.println("  FAIL — " + label
                    + "  (expected: " + expected + ", got: " + actual + ")");
            failed++;
        }
    }

    // ── Test 1: basic creation ──
    static void testSingleOrder() {
        System.out.println("\n[test] Single Order");
        OrderTracker t = new OrderTracker();
        t.createOrder("alice", "ORD-001", 25.50);
        assertDoubleEquals(25.50, t.getUserTotal("alice"), "single order total");
    }

    // ── Test 2: multiple orders accumulate ──
    static void testMultipleOrdersSameUser() {
        System.out.println("\n[test] Multiple Orders — Same User");
        OrderTracker t = new OrderTracker();
        t.createOrder("alice", "ORD-001", 10.00);
        t.createOrder("alice", "ORD-002", 20.00);
        assertDoubleEquals(30.00, t.getUserTotal("alice"),
                "total should be 10 + 20 = 30");
    }

    // ── Test 3: cancel should reduce total ──
    static void testCancelReducesTotal() {
        System.out.println("\n[test] Cancel Reduces Total");
        OrderTracker t = new OrderTracker();
        t.createOrder("bob", "ORD-010", 15.00);
        t.createOrder("bob", "ORD-011", 25.00);
        t.cancelOrder("ORD-010");
        assertDoubleEquals(25.00, t.getUserTotal("bob"),
                "total after cancelling $15 order");
    }

    // ── Test 4: top user across multiple users ──
    static void testTopUser() {
        System.out.println("\n[test] Top User");
        OrderTracker t = new OrderTracker();
        t.createOrder("alice", "ORD-100", 50.00);
        t.createOrder("bob",   "ORD-101", 75.00);
        t.createOrder("carol", "ORD-102", 30.00);
        assertEquals("bob", t.getTopUser(), "top user should be bob ($75)");
    }

    // ── Test 5: unknown user total should be 0 ──
    static void testUnknownUserTotal() {
        System.out.println("\n[test] Unknown User Total");
        OrderTracker t = new OrderTracker();
        assertDoubleEquals(0.0, t.getUserTotal("ghost"),
                "unknown user should have $0 total");
    }

    // ── Test 6: cancel non-existent order ──
    static void testCancelNonExistentOrder() {
        System.out.println("\n[test] Cancel Non-existent Order");
        OrderTracker t = new OrderTracker();
        try {
            t.cancelOrder("FAKE-999");
            System.out.println("  FAIL — should have thrown for missing order");
            failed++;
        } catch (IllegalArgumentException e) {
            System.out.println("  PASS — threw IllegalArgumentException");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAIL — wrong exception type: "
                    + e.getClass().getSimpleName());
            failed++;
        }
    }

    // ─────────────────────────── Runner ──────────────────────────────

    public static void main(String[] args) {
        System.out.println("========== OrderTracker Test Suite ==========");

        runTest("testSingleOrder",             Main::testSingleOrder);
        runTest("testMultipleOrdersSameUser",   Main::testMultipleOrdersSameUser);
        runTest("testCancelReducesTotal",       Main::testCancelReducesTotal);
        runTest("testTopUser",                  Main::testTopUser);
        runTest("testUnknownUserTotal",         Main::testUnknownUserTotal);
        runTest("testCancelNonExistentOrder",   Main::testCancelNonExistentOrder);

        System.out.println("\n========== Results: " + passed + " passed, "
                + failed + " failed ==========");
    }

    static void runTest(String name, Runnable test) {
        try {
            test.run();
        } catch (Exception e) {
            System.out.println("  CRASH — " + name + ": "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());
            failed++;
        }
    }
}