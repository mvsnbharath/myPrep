import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class RoundRobinLoadBalancer {
    private List<Server> servers;
    private int currentIndex;

    public RoundRobinLoadBalancer(List<Server> servers) {
        this.servers = servers;
        this.currentIndex = 0;
    }

    /**
     * Returns the next healthy server using round-robin selection.
     * Returns null if no healthy servers are available.
     */
    public Server getNextServer() {
        if (servers.isEmpty()) {
            return null;
        }

        int size = servers.size();
        int attempts = 0;
        Server candidate = null;

        do {
            currentIndex = currentIndex % size;
            candidate = servers.get(currentIndex);
            attempts++;
            currentIndex++;
        } while (!candidate.isHealthy() && attempts < size);

//        System.out.println("Candidate "+ candidate.getAddress());

        return candidate.isHealthy()? candidate: null;
    }

    /**
     * Marks the server with the given address as unhealthy.
     */
    public void markServerDown(String address) {
        for (Server server : servers) {
            if (server.getAddress().equals(address)) {
                server.setHealthy(false);
                return;
            }
        }
    }

    /**
     * Marks the server with the given address as healthy.
     */
    public void markServerUp(String address) {
        for (Server server : servers) {
            if (server.getAddress().equals(address)) {
                server.setHealthy(true);
                return;
            }
        }
    }

    /**
     * Removes a server from the pool by address.
     */
    public boolean removeServer(String address) {
        return servers.removeIf(s -> s.getAddress().equals(address));
    }

    /**
     * Adds a new server to the pool.
     */
    public void addServer(Server server) {
        servers.add(server);
    }

    /**
     * Returns the current number of servers in the pool.
     */
    public int getServerCount() {
        return servers.size();
    }
}
