import java.util.*;

public class RoundRobinBalancer {
    private final List<String> backends;
    private final Set<String> unhealthy;

    private int current;

    public RoundRobinBalancer(List<String> backends) {
        this.backends = new ArrayList<>(backends);
        this.unhealthy = new HashSet<>();
        this.current = 0;
    }

    /**
     * Returns the next healthy backend in round-robin order.
     * Skips unhealthy backends. Returns null if all are unhealthy or list is empty.
     */
    public String getNext() {
        if (this.backends.isEmpty()) {
            return null;
        }

        int n = this.backends.size();
        for (int i = 0; i <= n - 1; i++) {
            int idx = (this.current) % n;
            String backend = backends.get(idx);
            if (!this.unhealthy.contains(backend)) {
                this.current = idx+1;
                return backend;
            }
        }
        return null;
    }

    public void markUnhealthy(String backend) {
        this.unhealthy.add(backend);
        int index = this.backends.indexOf(backend);
        this.backends.remove(index);
    }

    public void markHealthy(String backend) {
        this.unhealthy.remove(backend);
        this.backends.add(backend);
    }
}
