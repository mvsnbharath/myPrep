// Represents a single delivery event (pickup or dropoff) for a dasher
public class Activity {
    public enum Type { PICKUP, DROPOFF }

    public int deliveryId;
    public Type type;
    public long timestamp; // epoch minutes (simple for calculation)

    public Activity(int deliveryId, Type type, long timestamp) {
        this.deliveryId = deliveryId;
        this.type = type;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return type + " delivery=" + deliveryId + " t=" + timestamp;
    }
}
