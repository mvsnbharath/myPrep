import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free round-robin load balancer using AtomicInteger.
 * Designed for high-concurrency environments.
 */
public class AtomicBalancer {
    private final List<String> backends;
    private final Set<String> unhealthy;
    private final AtomicInteger counter;

    public AtomicBalancer(List<String> backends) {
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
        this.unhealthy = new HashSet<>();
        this.counter = new AtomicInteger(0);
    }

    public String getNext() {
        int n = backends.size();
        if (n == 0) {
            return null;
        }

        int start = counter.incrementAndGet();

        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            String backend = backends.get(idx);
            if (!unhealthy.contains(backend)) {
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

    public Set<String> getUnhealthy() {
        return new HashSet<>(unhealthy);
    }
}
