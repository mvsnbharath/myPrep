import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public class MainTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Part 1: Basic Time-Based Payment ===");
        testSingleOrder();
        testTwoOverlappingOrders();
        testSequentialNonOverlappingOrders();
        testThreeConcurrentOrders();
        testSingleEventNoPayment();

        System.out.println("\n=== Part 2: Pickup Time Adjustment ===");
        testPickupTimeExclusion();
        testPickupTimeExclusionOriginalExample();
        testBothOrdersAtStoreSameTime();
        testPickupNoImpactOnSingleOrder();

        System.out.println("\n=== Part 3: Peak Hour Multiplier ===");
        testPeakPayFullOverlap();
        testPeakPayPartialOverlap();
        testPeakPayNoOverlap();
        testPeakPayWithPickupExclusion();
        testMultiplePeakWindows();
        testPeakPayOriginalExample();

        System.out.println("\n=== TimeUtils: getPeakMinutes Edge Cases ===");
        testGetPeakMinutes_NoOverlap();
        testGetPeakMinutes_CompleteOverlap();
        testGetPeakMinutes_PartialOverlapLeft();
        testGetPeakMinutes_PartialOverlapRight();
        testGetPeakMinutes_IntervalContainsWindow();
        testGetPeakMinutes_EmptyWindowList();
        testGetPeakMinutes_ExactBoundary();
        testGetPeakMinutes_MultipleWindows();
        testGetPeakMinutes_ZeroLengthInterval();

        System.out.println("\n========================================");
        System.out.printf("Results: %d passed, %d failed%n", passed, failed);
    }

    // --- Helpers ---

    private static void assertEquals(String testName, BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) == 0) {
            System.out.println("  PASS: " + testName);
            passed++;
        } else {
            System.out.println("  FAIL: " + testName + " — expected " + expected + " but got " + actual);
            failed++;
        }
    }

    private static void assertEqualsLong(String testName, long expected, long actual) {
        if (expected == actual) {
            System.out.println("  PASS: " + testName);
            passed++;
        } else {
            System.out.println("  FAIL: " + testName + " — expected " + expected + " but got " + actual);
            failed++;
        }
    }

    private static PaymentCalculator baseOnlyCalculator() {
        return new PaymentCalculator(List.of(new BasePayRule()));
    }

    private static PaymentCalculator fullCalculator() {
        return new PaymentCalculator(List.of(new BasePayRule(), new PeakPayRule()));
    }

    private static List<PeakWindow> noPeak() {
        return List.of();
    }

    // =============================================
    // Part 1: Basic Time-Based Payment
    // =============================================

    private static void testSingleOrder() {
        // A accepted at 10:00, fulfilled at 10:30 → 30 min * $0.50 = $15
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:30")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Single order 30 min = $15.00", new BigDecimal("15.00"), result);
    }

    private static void testTwoOverlappingOrders() {
        // The original Part 1 example → $20
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:30")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Two overlapping orders = $20.00", new BigDecimal("20.00"), result);
    }

    private static void testSequentialNonOverlappingOrders() {
        // A: 10:00-10:10, B: 10:20-10:30 → gap 10:10-10:20 with 0 orders
        // A: 10 min * 1 * 0.5 = $5, gap: $0, B: 10 min * 1 * 0.5 = $5 → $10
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:10"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:30")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Sequential non-overlapping = $10.00", new BigDecimal("10.00"), result);
    }

    private static void testThreeConcurrentOrders() {
        // A accepted 10:00, B accepted 10:00, C accepted 10:00, all fulfilled 10:10
        // 10:00→10:10: 3 orders * 10 min * $0.50 = $15
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "C", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:10"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "C", "10:10")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Three concurrent orders 10 min = $15.00", new BigDecimal("15.00"), result);
    }

    private static void testSingleEventNoPayment() {
        // Only one event — no interval to compute
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Single event = $0.00", BigDecimal.ZERO, result);
    }

    // =============================================
    // Part 2: Pickup Time Adjustment
    // =============================================

    private static void testPickupTimeExclusion() {
        // A accepted 10:00, B accepted 10:05
        // A at_pickup 10:10, A picked_up 10:15 → during 10:10-10:15 only B counts (max 1)
        // B fulfilled 10:20, A fulfilled 10:25
        // 10:00→10:05: 1 order * 5 min * 0.5 = 2.5
        // 10:05→10:10: 2 orders * 5 min * 0.5 = 5.0
        // 10:10→10:15: max(1, 2-1)=1 * 5 min * 0.5 = 2.5
        // 10:15→10:20: 2 orders * 5 min * 0.5 = 5.0
        // 10:20→10:25: 1 order * 5 min * 0.5 = 2.5
        // Total = 17.5
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:05"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:10"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:15"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:25")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Pickup time exclusion = $17.50", new BigDecimal("17.50"), result);
    }

    private static void testPickupTimeExclusionOriginalExample() {
        // Original Part 2 example → $35
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:15"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "B", "10:30"),
                new OrderActivity(ActivityType.PICKED_UP, "B", "10:35"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:40"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:50")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Part 2 original example = $35.00", new BigDecimal("35.00"), result);
    }

    private static void testBothOrdersAtStoreSameTime() {
        // Both at store simultaneously → max(1, 2-2) = max(1, 0) = 1
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:00"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:10"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "B", "10:10"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:20"),
                new OrderActivity(ActivityType.PICKED_UP, "B", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:30"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:30")
        );
        // 10:00→10:10: 2 * 10 * 0.5 = 10
        // 10:10→10:20: max(1, 2-2)=1 * 10 * 0.5 = 5 (both at store, min 1)
        // 10:20→10:30: 2 * 10 * 0.5 = 10
        // Total = 25
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Both at store same time = $25.00", new BigDecimal("25.00"), result);
    }

    private static void testPickupNoImpactOnSingleOrder() {
        // Single order with pickup — no other orders to exclude
        // A accepted 10:00, at_pickup 10:10, picked_up 10:15, fulfilled 10:30
        // 10:00→10:10: 1 * 10 * 0.5 = 5     (active=1, store=0)
        // 10:10→10:15: max(1, 1-1)=1 * 5 * 0.5 = 2.5  (active=1, store=1, min is 1)
        // 10:15→10:30: 1 * 15 * 0.5 = 7.5
        // Total = 15
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:10"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:15"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:30")
        );
        BigDecimal result = baseOnlyCalculator().calculate(activities, noPeak());
        assertEquals("Single order with pickup = $15.00", new BigDecimal("15.00"), result);
    }

    // =============================================
    // Part 3: Peak Hour Multiplier
    // =============================================

    private static void testPeakPayFullOverlap() {
        // Single order, entire duration in peak → $1.00/min instead of $0.50
        // 10:00→10:10, peak 10:00→10:30
        // BasePayRule: 0 non-peak min → $0
        // PeakPayRule: 10 peak min * 1 * $1.00 = $10
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:10")
        );
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:00"), LocalTime.parse("10:30")));
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("Full peak overlap = $10.00", new BigDecimal("10.00"), result);
    }

    private static void testPeakPayPartialOverlap() {
        // Single order 10:00→10:20, peak 10:10→10:30
        // 10:00→10:10: non-peak 10 min * 0.50 = $5
        // 10:10→10:20: peak 10 min * 1.00 = $10 (but this is one interval in the event stream)
        // Actually only one interval 10:00→10:20, split: 10 non-peak + 10 peak
        // Base: 1 * 0.50 * 10 = 5, Peak: 1 * 1.00 * 10 = 10 → $15
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:20")
        );
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:10"), LocalTime.parse("10:30")));
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("Partial peak overlap = $15.00", new BigDecimal("15.00"), result);
    }

    private static void testPeakPayNoOverlap() {
        // Single order entirely outside peak → same as base pay
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:10")
        );
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("11:00"), LocalTime.parse("12:00")));
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("No peak overlap = $5.00", new BigDecimal("5.00"), result);
    }

    private static void testPeakPayWithPickupExclusion() {
        // A accepted 10:00, B accepted 10:05
        // A at_pickup 10:10 (during peak), picked_up 10:15
        // Peak 10:10→10:20
        // Both fulfilled 10:25
        // 10:00→10:05: 1 order, no peak → 0.50*1*5 = 2.50
        // 10:05→10:10: 2 orders, no peak → 0.50*2*5 = 5.00
        // 10:10→10:15: max(1,2-1)=1, 5 peak min → base:0, peak:1.00*1*5 = 5.00
        // 10:15→10:20: 2 orders, 5 peak min → base:0, peak:1.00*2*5 = 10.00
        // 10:20→10:25: 2 orders, no peak → 0.50*2*5 = 5.00
        // Total = 27.50
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:05"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:10"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:15"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:25"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:25")
        );
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:10"), LocalTime.parse("10:20")));
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("Peak + pickup exclusion = $27.50", new BigDecimal("27.50"), result);
    }

    private static void testMultiplePeakWindows() {
        // Single order 10:00→10:40
        // Peaks: 10:05→10:15 and 10:25→10:35 → 20 peak min, 20 non-peak min
        // Base: 0.50 * 1 * 20 = 10, Peak: 1.00 * 1 * 20 = 20 → $30
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:40")
        );
        List<PeakWindow> peaks = List.of(
                new PeakWindow(LocalTime.parse("10:05"), LocalTime.parse("10:15")),
                new PeakWindow(LocalTime.parse("10:25"), LocalTime.parse("10:35"))
        );
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("Multiple peak windows = $30.00", new BigDecimal("30.00"), result);
    }

    private static void testPeakPayOriginalExample() {
        // Original Part 3 example → $45
        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:15"),
                new OrderActivity(ActivityType.PICKED_UP, "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "B", "10:25"),
                new OrderActivity(ActivityType.PICKED_UP, "B", "10:30"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:40"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:50")
        );
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:15"), LocalTime.parse("10:30")));
        BigDecimal result = fullCalculator().calculate(activities, peaks);
        assertEquals("Part 3 original example = $45.00", new BigDecimal("45.00"), result);
    }

    // =============================================
    // TimeUtils.getPeakMinutes Edge Cases
    // =============================================

    private static void testGetPeakMinutes_NoOverlap() {
        // Interval 10:00→10:10, peak 10:20→10:30 → 0
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:20"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:00"), LocalTime.parse("10:10"), peaks);
        assertEqualsLong("No overlap → 0 peak min", 0, result);
    }

    private static void testGetPeakMinutes_CompleteOverlap() {
        // Interval fully inside peak → all minutes are peak
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:00"), LocalTime.parse("11:00")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:10"), LocalTime.parse("10:30"), peaks);
        assertEqualsLong("Complete overlap → 20 peak min", 20, result);
    }

    private static void testGetPeakMinutes_PartialOverlapLeft() {
        // Interval starts before peak: 10:00→10:20, peak 10:10→10:30 → 10 min overlap
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:10"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:00"), LocalTime.parse("10:20"), peaks);
        assertEqualsLong("Partial overlap left → 10 peak min", 10, result);
    }

    private static void testGetPeakMinutes_PartialOverlapRight() {
        // Interval ends after peak: 10:20→10:40, peak 10:10→10:30 → 10 min overlap
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:10"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:20"), LocalTime.parse("10:40"), peaks);
        assertEqualsLong("Partial overlap right → 10 peak min", 10, result);
    }

    private static void testGetPeakMinutes_IntervalContainsWindow() {
        // Interval 10:00→11:00, peak 10:15→10:30 → 15 min overlap
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:15"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:00"), LocalTime.parse("11:00"), peaks);
        assertEqualsLong("Interval contains window → 15 peak min", 15, result);
    }

    private static void testGetPeakMinutes_EmptyWindowList() {
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:00"), LocalTime.parse("10:30"), List.of());
        assertEqualsLong("Empty window list → 0 peak min", 0, result);
    }

    private static void testGetPeakMinutes_ExactBoundary() {
        // Interval exactly matches peak window
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:00"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:00"), LocalTime.parse("10:30"), peaks);
        assertEqualsLong("Exact boundary match → 30 peak min", 30, result);
    }

    private static void testGetPeakMinutes_MultipleWindows() {
        // Two windows: 10:00→10:10 and 10:20→10:30, interval 10:05→10:25
        // Overlap with first: 10:05→10:10 = 5, overlap with second: 10:20→10:25 = 5 → total 10
        List<PeakWindow> peaks = List.of(
                new PeakWindow(LocalTime.parse("10:00"), LocalTime.parse("10:10")),
                new PeakWindow(LocalTime.parse("10:20"), LocalTime.parse("10:30"))
        );
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:05"), LocalTime.parse("10:25"), peaks);
        assertEqualsLong("Multiple windows → 10 peak min", 10, result);
    }

    private static void testGetPeakMinutes_ZeroLengthInterval() {
        // start == end → 0
        List<PeakWindow> peaks = List.of(new PeakWindow(LocalTime.parse("10:00"), LocalTime.parse("10:30")));
        long result = TimeUtils.getPeakMinutes(LocalTime.parse("10:10"), LocalTime.parse("10:10"), peaks);
        assertEqualsLong("Zero-length interval → 0 peak min", 0, result);
    }
}
