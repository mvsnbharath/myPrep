import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;

class OrderActivity{
    public ActivityType activityType;
    public String orderId;
    public String datetime;

    public OrderActivity(ActivityType activityType, String orderId, String datetime) {
        this.activityType = activityType;
        this.orderId = orderId;
        this.datetime = datetime;
    }
}

enum ActivityType {
    ORDER_ACCEPTED,
    ORDER_FULFILLED
}


public class Main {

    private static final float BASE_PAY = 0.5f;

    public static void main(String[] args) {


        List<OrderActivity> activities = List.of(
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "A", "10:00"),
                new OrderActivity(ActivityType.ORDER_ACCEPTED, "B", "10:10"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "A", "10:20"),
                new OrderActivity(ActivityType.ORDER_FULFILLED, "B", "10:30")
        );

        float finalPayment = new Main().finalPayment(activities);
        System.out.println("Final Payment: " + finalPayment);

    }

    public float finalPayment(List<OrderActivity> activities) {
        float total = 0.0f;
        HashSet<String> activeOrders = new HashSet<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for(int i=0; i<activities.size(); i++){
            ActivityType type = activities.get(i).activityType;
            String orderId = activities.get(i).orderId;

            if (i>0){
                LocalTime prevTime = LocalTime.parse(activities.get(i-1).datetime, formatter);
                LocalTime currTime = LocalTime.parse(activities.get(i).datetime, formatter);
                long minutes = Duration.between(prevTime, currTime).toMinutes();
                total += (BASE_PAY * activeOrders.size() * minutes);
            }

            if(type == ActivityType.ORDER_ACCEPTED){
                activeOrders.add(orderId);
            }else{
                activeOrders.remove(orderId);
            }


        }

        return total;
    }
}

 
    

