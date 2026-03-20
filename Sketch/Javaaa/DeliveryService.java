import java.util.*;

// Mocks the upstream delivery service that provides dasher activity data
public class DeliveryService {

    // Returns activities for a given dasher, sorted by timestamp
    public List<Activity> getActivities(int dasherId) {
        return mockData(dasherId);
    }

    private List<Activity> mockData(int dasherId) {
        List<Activity> activities = new ArrayList<>();

        if (dasherId == 1) {
            // Dasher 1: single delivery, picked up at t=0, dropped off at t=30
            // Expected pay: 30 min * $0.30 = $9.00
            activities.add(new Activity(100, Activity.Type.PICKUP, 0));
            activities.add(new Activity(100, Activity.Type.DROPOFF, 30));
        } else if (dasherId == 2) {
            // Dasher 2: two overlapping deliveries
            // t=0  pickup order 200
            // t=10 pickup order 201   (now 2 ongoing)
            // t=20 dropoff order 200  (back to 1 ongoing)
            // t=30 dropoff order 201
            //
            // t=0-10:  1 order, 10 min * 1 * $0.30 = $3.00
            // t=10-20: 2 orders, 10 min * 2 * $0.30 = $6.00
            // t=20-30: 1 order, 10 min * 1 * $0.30 = $3.00
            // Total = $12.00
            activities.add(new Activity(200, Activity.Type.PICKUP, 0));
            activities.add(new Activity(201, Activity.Type.PICKUP, 10));
            activities.add(new Activity(200, Activity.Type.DROPOFF, 20));
            activities.add(new Activity(201, Activity.Type.DROPOFF, 30));
        } else if (dasherId == 3) {
            // Dasher 3: no deliveries today
            // Expected pay: $0.00
        }

        return activities;
    }
}
