import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;

enum ActivityType {
    ORDER_ACCEPTED,
    ORDER_ARRIVED_AT_PICKUP,
    PICKED_UP,
    ORDER_FULFILLED
}

class OrderActivity {
    private final ActivityType activityType;
    private final String orderId;
    private final LocalTime datetime;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    public OrderActivity(ActivityType activityType, String orderId, String datetime) {
        this.activityType = activityType;
        this.orderId = orderId;
        this.datetime = LocalTime.parse(datetime, formatter);
    }

    public ActivityType getActivityType() { return activityType; }
    public String getOrderId() { return orderId; }
    public LocalTime getDatetime() { return datetime; }
}

class IntervalContext {
    private final LocalTime start;
    private final LocalTime end;
    private final int activeOrderCount;

    public IntervalContext(LocalTime start, LocalTime end, int activeOrderCount) {
        this.start = start;
        this.end = end;
        this.activeOrderCount = activeOrderCount;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public long getTotalMinutes() { return Duration.between(start, end).toMinutes(); }
    public int getActiveOrderCount() { return activeOrderCount; }
}

interface PaymentRule {
    BigDecimal calculate(IntervalContext intervalContext);
}

class BasePaymentRule implements PaymentRule {
    private static final BigDecimal BASE_PAY = new BigDecimal("0.5");

    public BigDecimal calculate(IntervalContext intervalContext) {
        if (intervalContext.getActiveOrderCount() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal activeOrders = BigDecimal.valueOf(intervalContext.getActiveOrderCount());
        return activeOrders.multiply(BASE_PAY).multiply(BigDecimal.valueOf(intervalContext.getTotalMinutes()));
    }
}

class PaymentCalculator {
    private final List<PaymentRule> paymentRules;

    public PaymentCalculator(List<PaymentRule> paymentRules) {
        this.paymentRules = paymentRules;
    }

    public BigDecimal finalPayment(List<OrderActivity> activities) {
        BigDecimal total = BigDecimal.ZERO;
        HashSet<String> activeOrders = new HashSet<>();
        HashSet<String> atStoreOrders = new HashSet<>();

        for (int i = 0; i < activities.size(); i++) {
            OrderActivity activity = activities.get(i);

            if (i > 0) {

                int activeOrderCount =  activeOrders.isEmpty() ? 0: Math.max(1, activeOrders.size()-atStoreOrders.size());

                IntervalContext intervalContext = new IntervalContext(
                        activities.get(i - 1).getDatetime(),
                        activity.getDatetime(),
                        activeOrderCount);

                for (PaymentRule rule : paymentRules) {
                    total = total.add(rule.calculate(intervalContext));
                }
            }

            switch (activity.getActivityType()) {
                case ORDER_ACCEPTED -> activeOrders.add(activity.getOrderId());
                case  ORDER_ARRIVED_AT_PICKUP -> atStoreOrders.add(activity.getOrderId());
                case  PICKED_UP -> atStoreOrders.remove((activity.getOrderId()));
                case ORDER_FULFILLED -> activeOrders.remove(activity.getOrderId());
            }
        }

        return total;
    }
}

public class Main {
    public static void main(String[] args) {
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

        List<PaymentRule> paymentRules = List.of(new BasePaymentRule());
        PaymentCalculator calculator = new PaymentCalculator(paymentRules);

        BigDecimal finalPayment = calculator.finalPayment(activities);
        System.out.println("Final Payment: "+ finalPayment);
    }
}
