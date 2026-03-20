import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// --- Shared Domain Models ---

enum ActivityType {
    ORDER_ACCEPTED,
    ORDER_ARRIVED_AT_PICKUP,
    PICKED_UP,
    ORDER_FULFILLED
}

class OrderActivity {
    private final ActivityType activityType;
    private final String orderId;
    private final LocalTime timestamp;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public OrderActivity(ActivityType activityType, String orderId, String timestamp) {
        this.activityType = activityType;
        this.orderId = orderId;
        this.timestamp = LocalTime.parse(timestamp, FORMATTER);
    }

    public ActivityType getActivityType() { return activityType; }
    public String getOrderId() { return orderId; }
    public LocalTime getTimestamp() { return timestamp; }
}

class PeakWindow {
    private final LocalTime start;
    private final LocalTime end;

    public PeakWindow(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
}

// ========================================================================
// Part 1 — Basic Time-Based Payment (Strategy Pattern)
// IntervalContextV1: just knows start, end, and active order count
// Single rule: BasePayRuleV1 ($0.50 × orders × minutes)
// ========================================================================

class IntervalContextV1 {
    private final LocalTime start;
    private final LocalTime end;
    private final int activeOrderCount;

    public IntervalContextV1(LocalTime start, LocalTime end, int activeOrderCount) {
        this.start = start;
        this.end = end;
        this.activeOrderCount = activeOrderCount;
    }

    public long getTotalMinutes() { return Duration.between(start, end).toMinutes(); }
    public int getActiveOrderCount() { return activeOrderCount; }
}

interface PaymentRuleV1 {
    BigDecimal calculate(IntervalContextV1 context);
}

class BasePayRuleV1 implements PaymentRuleV1 {
    private static final BigDecimal BASE_RATE = new BigDecimal("0.50");

    @Override
    public BigDecimal calculate(IntervalContextV1 context) {
        if (context.getActiveOrderCount() == 0) return BigDecimal.ZERO;
        return BASE_RATE
                .multiply(BigDecimal.valueOf(context.getActiveOrderCount()))
                .multiply(BigDecimal.valueOf(context.getTotalMinutes()));
    }
}

class PaymentCalculatorV1 {
    private final List<PaymentRuleV1> rules;

    public PaymentCalculatorV1(List<PaymentRuleV1> rules) {
        this.rules = rules;
    }

    public BigDecimal calculate(List<OrderActivity> activities) {
        activities.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        BigDecimal total = BigDecimal.ZERO;
        Set<String> activeOrders = new HashSet<>();

        for (int i = 0; i < activities.size(); i++) {
            OrderActivity event = activities.get(i);

            if (i > 0 && !activeOrders.isEmpty()) {
                IntervalContextV1 ctx = new IntervalContextV1(
                        activities.get(i - 1).getTimestamp(),
                        event.getTimestamp(),
                        activeOrders.size()
                );
                for (PaymentRuleV1 rule : rules) {
                    total = total.add(rule.calculate(ctx));
                }
            }

            switch (event.getActivityType()) {
                case ORDER_ACCEPTED -> activeOrders.add(event.getOrderId());
                case ORDER_FULFILLED -> activeOrders.remove(event.getOrderId());
                default -> { }
            }
        }

        return total;
    }
}

// ========================================================================
// Part 2 — Pickup Time Adjustment (Strategy Pattern)
// IntervalContextV2: adds "ongoing" count (active minus at-store)
// Single rule: BasePayRuleV2 ($0.50 × ongoing × minutes)
// ========================================================================

class IntervalContextV2 {
    private final LocalTime start;
    private final LocalTime end;
    private final int ongoingOrderCount;

    public IntervalContextV2(LocalTime start, LocalTime end, int ongoingOrderCount) {
        this.start = start;
        this.end = end;
        this.ongoingOrderCount = ongoingOrderCount;
    }

    public long getTotalMinutes() { return Duration.between(start, end).toMinutes(); }
    public int getOngoingOrderCount() { return ongoingOrderCount; }
}

interface PaymentRuleV2 {
    BigDecimal calculate(IntervalContextV2 context);
}

class BasePayRuleV2 implements PaymentRuleV2 {
    private static final BigDecimal BASE_RATE = new BigDecimal("0.50");

    @Override
    public BigDecimal calculate(IntervalContextV2 context) {
        if (context.getOngoingOrderCount() == 0) return BigDecimal.ZERO;
        return BASE_RATE
                .multiply(BigDecimal.valueOf(context.getOngoingOrderCount()))
                .multiply(BigDecimal.valueOf(context.getTotalMinutes()));
    }
}

class PaymentCalculatorV2 {
    private final List<PaymentRuleV2> rules;

    public PaymentCalculatorV2(List<PaymentRuleV2> rules) {
        this.rules = rules;
    }

    public BigDecimal calculate(List<OrderActivity> activities) {
        activities.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        BigDecimal total = BigDecimal.ZERO;
        Set<String> activeOrders = new HashSet<>();
        Set<String> atStoreOrders = new HashSet<>();

        for (int i = 0; i < activities.size(); i++) {
            OrderActivity event = activities.get(i);

            if (i > 0) {
                int ongoing = activeOrders.isEmpty() ? 0
                        : Math.max(1, activeOrders.size() - atStoreOrders.size());

                IntervalContextV2 ctx = new IntervalContextV2(
                        activities.get(i - 1).getTimestamp(),
                        event.getTimestamp(),
                        ongoing
                );
                for (PaymentRuleV2 rule : rules) {
                    total = total.add(rule.calculate(ctx));
                }
            }

            switch (event.getActivityType()) {
                case ORDER_ACCEPTED          -> activeOrders.add(event.getOrderId());
                case ORDER_ARRIVED_AT_PICKUP -> atStoreOrders.add(event.getOrderId());
                case PICKED_UP               -> atStoreOrders.remove(event.getOrderId());
                case ORDER_FULFILLED         -> activeOrders.remove(event.getOrderId());
            }
        }

        return total;
    }
}

// ========================================================================
// Part 3 — Peak Hour Multiplier (Strategy Pattern)
// IntervalContextV3: adds start/end times + peak windows for rules to use
// Two rules: BasePayRuleV3 ($0.50 non-peak) + PeakPayRuleV3 ($1.00 peak)
// ========================================================================

class IntervalContextV3 {
    private final LocalTime start;
    private final LocalTime end;
    private final int ongoingOrderCount;
    private final List<PeakWindow> peakWindows;

    public IntervalContextV3(LocalTime start, LocalTime end, int ongoingOrderCount, List<PeakWindow> peakWindows) {
        this.start = start;
        this.end = end;
        this.ongoingOrderCount = ongoingOrderCount;
        this.peakWindows = peakWindows;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public long getTotalMinutes() { return Duration.between(start, end).toMinutes(); }
    public int getOngoingOrderCount() { return ongoingOrderCount; }
    public List<PeakWindow> getPeakWindows() { return peakWindows; }

    public long getPeakMinutes() {
        long total = 0;
        for (PeakWindow w : peakWindows) {
            LocalTime overlapStart = start.isAfter(w.getStart()) ? start : w.getStart();
            LocalTime overlapEnd = end.isBefore(w.getEnd()) ? end : w.getEnd();
            if (overlapStart.isBefore(overlapEnd)) {
                total += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return total;
    }

    public long getNonPeakMinutes() {
        return getTotalMinutes() - getPeakMinutes();
    }
}

interface PaymentRuleV3 {
    BigDecimal calculate(IntervalContextV3 context);
}

class BasePayRuleV3 implements PaymentRuleV3 {
    private static final BigDecimal BASE_RATE = new BigDecimal("0.50");

    @Override
    public BigDecimal calculate(IntervalContextV3 context) {
        if (context.getOngoingOrderCount() == 0) return BigDecimal.ZERO;
        return BASE_RATE
                .multiply(BigDecimal.valueOf(context.getOngoingOrderCount()))
                .multiply(BigDecimal.valueOf(context.getNonPeakMinutes()));
    }
}

class PeakPayRuleV3 implements PaymentRuleV3 {
    private static final BigDecimal PEAK_RATE = new BigDecimal("1.00");

    @Override
    public BigDecimal calculate(IntervalContextV3 context) {
        if (context.getOngoingOrderCount() == 0 || context.getPeakMinutes() == 0) return BigDecimal.ZERO;
        return PEAK_RATE
                .multiply(BigDecimal.valueOf(context.getOngoingOrderCount()))
                .multiply(BigDecimal.valueOf(context.getPeakMinutes()));
    }
}

class PaymentCalculatorV3 {
    private final List<PaymentRuleV3> rules;

    public PaymentCalculatorV3(List<PaymentRuleV3> rules) {
        this.rules = rules;
    }

    public BigDecimal calculate(List<OrderActivity> activities, List<PeakWindow> peakWindows) {
        activities.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        BigDecimal total = BigDecimal.ZERO;
        Set<String> activeOrders = new HashSet<>();
        Set<String> atStoreOrders = new HashSet<>();

        for (int i = 0; i < activities.size(); i++) {
            OrderActivity event = activities.get(i);

            if (i > 0) {
                int ongoing = activeOrders.isEmpty() ? 0
                        : Math.max(1, activeOrders.size() - atStoreOrders.size());

                IntervalContextV3 ctx = new IntervalContextV3(
                        activities.get(i - 1).getTimestamp(),
                        event.getTimestamp(),
                        ongoing,
                        peakWindows
                );
                for (PaymentRuleV3 rule : rules) {
                    total = total.add(rule.calculate(ctx));
                }
            }

            switch (event.getActivityType()) {
                case ORDER_ACCEPTED          -> activeOrders.add(event.getOrderId());
                case ORDER_ARRIVED_AT_PICKUP -> atStoreOrders.add(event.getOrderId());
                case PICKED_UP               -> atStoreOrders.remove(event.getOrderId());
                case ORDER_FULFILLED         -> activeOrders.remove(event.getOrderId());
            }
        }

        return total;
    }
}

// --- Main ---

public class Main {
    public static void main(String[] args) {

        // ============ Part 1 — Example 1 ============
        List<OrderActivity> ex1 = new ArrayList<>(List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED,  "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED,  "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:30")
        ));

        PaymentCalculatorV1 v1 = new PaymentCalculatorV1(List.of(new BasePayRuleV1()));
        System.out.printf("Part 1 — Final Payment: $%.2f%n", v1.calculate(ex1));
        // Expected: $20.00

        // ============ Part 2 — Example 2 ============
        List<OrderActivity> ex2 = new ArrayList<>(List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED,          "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED,          "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:15"),
                new OrderActivity(ActivityType.PICKED_UP,               "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "B", "10:30"),
                new OrderActivity(ActivityType.PICKED_UP,               "B", "10:35"),
                new OrderActivity(ActivityType.ORDER_FULFILLED,         "A", "10:40"),
                new OrderActivity(ActivityType.ORDER_FULFILLED,         "B", "10:50")
        ));

        PaymentCalculatorV2 v2 = new PaymentCalculatorV2(List.of(new BasePayRuleV2()));
        System.out.printf("Part 2 — Final Payment: $%.2f%n", v2.calculate(ex2));
        // Expected: $35.00

        // ============ Part 3 — Example 3 ============
        List<OrderActivity> ex3 = new ArrayList<>(List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED,          "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED,          "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "A", "10:15"),
                new OrderActivity(ActivityType.PICKED_UP,               "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_ARRIVED_AT_PICKUP, "B", "10:25"),
                new OrderActivity(ActivityType.PICKED_UP,               "B", "10:30"),
                new OrderActivity(ActivityType.ORDER_FULFILLED,         "A", "10:40"),
                new OrderActivity(ActivityType.ORDER_FULFILLED,         "B", "10:50")
        ));

        List<PeakWindow> peakWindows = List.of(
                new PeakWindow(LocalTime.parse("10:15"), LocalTime.parse("10:30"))
        );

        PaymentCalculatorV3 v3 = new PaymentCalculatorV3(List.of(new BasePayRuleV3(), new PeakPayRuleV3()));
        System.out.printf("Part 3 — Final Payment: $%.2f%n", v3.calculate(ex3, peakWindows));
        // Expected: $45.00
    }
}

