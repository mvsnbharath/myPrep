import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// --- Domain Models ---

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


class IntervalContext {
    private final LocalTime start;
    private final LocalTime end;
    private final int activeOrderCount;
    private final List<PeakWindow> peakWindows;

    public IntervalContext(LocalTime start, LocalTime end, int activeOrderCount, List<PeakWindow> peakWindows) {
        this.start = start;
        this.end = end;
        this.activeOrderCount = activeOrderCount;
        this.peakWindows = peakWindows;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public int getActiveOrderCount() { return activeOrderCount; }
    public long getTotalMinutes() { return Duration.between(start, end).toMinutes(); }
    public List<PeakWindow> getPeakWindows() { return peakWindows; }
}


interface PaymentRule {
    BigDecimal calculate(IntervalContext context);
}

// --- Concrete Rules ---

class BasePayRule implements PaymentRule {
    private static final BigDecimal BASE_RATE = new BigDecimal("0.50");

    @Override
    public BigDecimal calculate(IntervalContext context) {
        if (context.getActiveOrderCount() == 0) return BigDecimal.ZERO;

        long peakMinutes = TimeUtils.getPeakMinutes(context.getStart(), context.getEnd(), context.getPeakWindows());
        long nonPeakMinutes = context.getTotalMinutes() - peakMinutes;

        BigDecimal orderMultiplier = BigDecimal.valueOf(context.getActiveOrderCount());
        BigDecimal nonPeakPay = BASE_RATE.multiply(orderMultiplier).multiply(BigDecimal.valueOf(nonPeakMinutes));

        return nonPeakPay;
    }
}

class PeakPayRule implements PaymentRule {
    private static final BigDecimal PEAK_RATE = new BigDecimal("1.00");

    @Override
    public BigDecimal calculate(IntervalContext context) {
        if (context.getActiveOrderCount() == 0) return BigDecimal.ZERO;

        long peakMinutes = TimeUtils.getPeakMinutes(context.getStart(), context.getEnd(), context.getPeakWindows());
        if (peakMinutes == 0) return BigDecimal.ZERO;

        BigDecimal orderMultiplier = BigDecimal.valueOf(context.getActiveOrderCount());
        return PEAK_RATE.multiply(orderMultiplier).multiply(BigDecimal.valueOf(peakMinutes));
    }
}

// --- Time utility ---

class TimeUtils {
    public static long getPeakMinutes(LocalTime start, LocalTime end, List<PeakWindow> peakWindows) {
        long totalMinutes = 0;
        for (PeakWindow window : peakWindows) {
            LocalTime overlapStart = start.isAfter(window.getStart()) ? start : window.getStart();
            LocalTime overlapEnd = end.isBefore(window.getEnd()) ? end : window.getEnd();
            if (overlapStart.isBefore(overlapEnd)) {
                totalMinutes += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return totalMinutes;
    }
}

// --- Payment Calculator (orchestrator) ---

class PaymentCalculator {
    private final List<PaymentRule> rules;

    public PaymentCalculator(List<PaymentRule> rules) {
        this.rules = rules;
    }

    public BigDecimal calculate(List<OrderActivity> activities, List<PeakWindow> peakWindows) {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> activeOrders = new HashSet<>();
        Set<String> atStoreOrders = new HashSet<>();

        for (int i = 0; i < activities.size(); i++) {
            OrderActivity activity = activities.get(i);

            if (i > 0) {
                LocalTime prevTime = activities.get(i - 1).getTimestamp();
                LocalTime currTime = activity.getTimestamp();
                int ongoingOrders = activeOrders.isEmpty() ? 0
                        : Math.max(1, activeOrders.size() - atStoreOrders.size());

                IntervalContext context = new IntervalContext(prevTime, currTime, ongoingOrders, peakWindows);

                for (PaymentRule rule : rules) {
                    total = total.add(rule.calculate(context));
                }
            }

            switch (activity.getActivityType()) {
                case ORDER_ACCEPTED -> activeOrders.add(activity.getOrderId());
                case ORDER_ARRIVED_AT_PICKUP -> atStoreOrders.add(activity.getOrderId());
                case PICKED_UP -> atStoreOrders.remove(activity.getOrderId());
                case ORDER_FULFILLED -> activeOrders.remove(activity.getOrderId());
            }
        }

        return total;
    }
}

// --- Main ---

public class Main {
    public static void main(String[] args) {
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

        List<PeakWindow> peakWindows = List.of(
                new PeakWindow(LocalTime.parse("10:15"), LocalTime.parse("10:30"))
        );

        List<PaymentRule> rules = List.of(new BasePayRule(), new PeakPayRule());
        PaymentCalculator calculator = new PaymentCalculator(rules);

        BigDecimal finalPayment = calculator.calculate(activities, peakWindows);
        System.out.printf("Final Payment: $%.2f%n", finalPayment);
    }
}

