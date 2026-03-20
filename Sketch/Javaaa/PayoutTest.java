public class PayoutTest {

    private PayoutService payoutService;

    public PayoutTest() {
        this.payoutService = new PayoutService(new DeliveryService());
    }

    public void testSingleDelivery() {
        // Dasher 1: 30 min * 1 * $0.30 = $9.00
        double payout = payoutService.calculatePayout(1);
        assertEqual(9.0, payout, "single delivery");
    }

    public void testOverlappingDeliveries() {
        // Dasher 2: 10*1*0.3 + 10*2*0.3 + 10*1*0.3 = 3 + 6 + 3 = $12.00
        double payout = payoutService.calculatePayout(2);
        assertEqual(12.0, payout, "overlapping deliveries");
    }

    public void testNoDeliveries() {
        // Dasher 3: no activities = $0.00
        double payout = payoutService.calculatePayout(3);
        assertEqual(0.0, payout, "no deliveries");
    }

    public void testUnknownDasher() {
        // Unknown dasher = $0.00
        double payout = payoutService.calculatePayout(999);
        assertEqual(0.0, payout, "unknown dasher");
    }

    // ---- helpers ----

    private void assertEqual(double expected, double actual, String testName) {
        if (Math.abs(expected - actual) < 0.001) {
            System.out.println("PASS: " + testName + " (expected=" + expected + ", actual=" + actual + ")");
        } else {
            System.out.println("FAIL: " + testName + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    public static void main(String[] args) {
        PayoutTest test = new PayoutTest();
        test.testSingleDelivery();
        test.testOverlappingDeliveries();
        test.testNoDeliveries();
        test.testUnknownDasher();
    }
}
