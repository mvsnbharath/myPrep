public class Server {
    private final String address;
    private boolean healthy;

    public Server(String address) {
        this.address = address;
        this.healthy = true;
    }

    public String getAddress() {
        return address;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public String toString() {
        return "Server(" + address + ", " + (healthy ? "UP" : "DOWN") + ")";
    }
}
