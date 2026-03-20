public class OrderActivity{
    public ActivityType activityType;
    public String orderId;
    public String datetime;

    public OrderActivity(ActivityType activityType, String orderId, String datetime) {
        this.activityType = activityType;
        this.orderId = orderId;
        this.datetime = datetime;
    }

}