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
        if (backends.isEmpty()) {
            return null;
        }

        int n = backends.size();
        for (int i = 0; i < n - 1; i++) {
            int idx = (current + i) % n;
            String backend = backends.get(idx);
            if (!unhealthy.contains(backend)) {
                current = idx;
                return backend;
            }
        }
        return null;
    }

    public void markUnhealthy(String backend) {
        unhealthy.add(backend);
    }

    public void markHealthy(String backend) {
        unhealthy.remove(backend);
    }
}
