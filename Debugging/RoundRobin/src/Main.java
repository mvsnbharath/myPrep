import java.util.*;

public class Main {
    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Round-Robin Load Balancer Tests ===\n");

        testBasicRoundRobin();
        testSkipUnhealthyServer();
        testAllServersDown();
        testRemoveServerContinuesRouting();
        testAddServer();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
    }

    static void check(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + testName);
        } else {
            failed++;
            System.out.println("  FAIL: " + testName);
        }
    }

//     ── Test 1: servers should be returned in round-robin order ──
    static void testBasicRoundRobin() {
        System.out.println("[testBasicRoundRobin]");
        List<Server> servers = new ArrayList<>(Arrays.asList(
            new Server("server-1"),
            new Server("server-2"),
            new Server("server-3")
        ));
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(servers);

        check("1st request → server-1", lb.getNextServer().getAddress().equals("server-1"));
        check("2nd request → server-2", lb.getNextServer().getAddress().equals("server-2"));
        check("3rd request → server-3", lb.getNextServer().getAddress().equals("server-3"));
        check("4th request wraps → server-1", lb.getNextServer().getAddress().equals("server-1"));
    }

    // ── Test 2: unhealthy servers should be skipped ──
    static void testSkipUnhealthyServer() {
        System.out.println("[testSkipUnhealthyServer]");
        List<Server> servers = new ArrayList<>(Arrays.asList(
            new Server("server-1"),
            new Server("server-2"),
            new Server("server-3")
        ));
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(servers);

        // Simulate health-check reporting server-2 is down
        int unhealthyId = 2;
        lb.markServerDown("server-" + unhealthyId);

        Set<String> routed = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Server s = lb.getNextServer();
            if (s != null) routed.add(s.getAddress());
        }

        check("server-2 is never routed to", !routed.contains("server-2"));
        check("server-1 is routed to", routed.contains("server-1"));
        check("server-3 is routed to", routed.contains("server-3"));
    }

    // ── Test 3: all servers down → should return null ──
    static void testAllServersDown() {
        System.out.println("[testAllServersDown]");
        List<Server> servers = new ArrayList<>(Arrays.asList(
            new Server("server-1"),
            new Server("server-2")
        ));
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(servers);

        for (Server s : servers) {
            s.setHealthy(false);
        }

        Server result = lb.getNextServer();
        check("returns null when all servers down", result == null);
    }

    // ── Test 4: removing a server, remaining servers still get traffic ──
    static void testRemoveServerContinuesRouting() {
        System.out.println("[testRemoveServerContinuesRouting]");
        List<Server> servers = new ArrayList<>(Arrays.asList(
            new Server("server-1"),
            new Server("server-2"),
            new Server("server-3")
        ));
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(servers);

        lb.getNextServer();
        lb.getNextServer();
        lb.removeServer("server-1");

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            Server s = lb.getNextServer();
            if (s != null) {
                counts.merge(s.getAddress(), 1, Integer::sum);
            }
        }

        check("server-1 not routed after removal", !counts.containsKey("server-1"));
        check("server-2 receives requests", counts.containsKey("server-2"));
        check("server-3 receives requests", counts.containsKey("server-3"));
        check("even distribution after removal",
            counts.getOrDefault("server-2", 0).equals(counts.getOrDefault("server-3", 0)));
    }

    // ── Test 5: dynamically added server participates in routing ──
    static void testAddServer() {
        System.out.println("[testAddServer]");
        List<Server> servers = new ArrayList<>(Arrays.asList(
            new Server("server-1"),
            new Server("server-2")
        ));
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(servers);

        lb.getNextServer();
        lb.addServer(new Server("server-3"));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            Server s = lb.getNextServer();
            if (s != null) {
                counts.merge(s.getAddress(), 1, Integer::sum);
            }
        }

        check("all 3 servers receive requests", counts.size() == 3);
        check("new server-3 is included in routing", counts.containsKey("server-3"));
    }
}