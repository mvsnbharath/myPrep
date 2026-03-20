import java.util.*;

public class PayoutService {

    private static final double BASE_PAY_PER_MINUTE = 0.30;

    private DeliveryService deliveryService;

    public PayoutService(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    // POST /payout  — input: dasherId, output: dollar amount
    public double calculatePayout(int dasherId) {
        List<Activity> activities = deliveryService.getActivities(dasherId);

        if (activities == null || activities.isEmpty()) {
            return 0.0;
        }

        // Sort by timestamp
        activities.sort(Comparator.comparingLong(a -> a.timestamp));

        // Walk through events, tracking number of ongoing deliveries at each transition
        double totalPay = 0.0;
        int ongoingDeliveries = 0;
        long prevTime = activities.get(0).timestamp;

        for (Activity act : activities) {
            long duration = act.timestamp - prevTime;

            if (duration > 0 && ongoingDeliveries > 0) {
                // multi order rate: ongoing * base
                totalPay += duration * ongoingDeliveries * BASE_PAY_PER_MINUTE;
            }

            if (act.type == Activity.Type.PICKUP) {
                ongoingDeliveries++;
            } else {
                ongoingDeliveries--;
            }

            prevTime = act.timestamp;
        }

        return totalPay;
    }
}
