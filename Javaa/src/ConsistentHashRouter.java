import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Consistent Hash Router
 *
 * Instead of round-robin (request 1 → A, request 2 → B, ...),
 * consistent hashing maps each KEY to a specific node deterministically.
 *
 * HOW IT WORKS:
 * 1. Imagine a ring of numbers from 0 to 2^31 (Integer.MAX_VALUE).
 * 2. Each real node is placed at multiple points on the ring ("virtual nodes").
 *    e.g., "ServerA" → hash("ServerA-0"), hash("ServerA-1"), ... hash("ServerA-149")
 *    More virtual nodes = more even distribution.
 * 3. To route a key (e.g., "user:123"), hash it and walk clockwise on the ring
 *    until you hit a virtual node. That virtual node's real node handles the key.
 *
 * WHY IT'S BETTER THAN MODULO HASHING:
 * - With modulo: hash(key) % N. If N changes (node added/removed), almost ALL
 *   keys remap to different nodes → cache misses, state loss.
 * - With consistent hashing: only keys between the new/removed node and its
 *   predecessor remap. ~1/N keys move instead of ~all.
 *
 * EXAMPLE:
 *   Ring positions: A=100, B=300, C=500 (simplified)
 *   Key "order:42" hashes to 250 → walk clockwise → hits B at 300 → route to B
 *   Key "order:99" hashes to 450 → walk clockwise → hits C at 500 → route to C
 *   If B is removed: "order:42" (250) now walks to C (500). Only B's keys move.
 *
 * VIRTUAL NODES:
 *   Without virtual nodes, 3 servers might cluster on one side of the ring,
 *   giving one node 60% of traffic. Virtual nodes spread each server across
 *   many ring positions (default: 150), making distribution nearly uniform.
 */
public class ConsistentHashRouter {
    // TreeMap is a sorted map — perfect for the ring.
    // Key = hash position on the ring, Value = real node name.
    // TreeMap.ceilingEntry(hash) gives us "walk clockwise" in O(log n).
    private final TreeMap<Integer, String> ring;

    // How many virtual nodes per real node. More = better distribution.
    private final int virtualNodes;

    // Track which real nodes are in the ring (for removeNode).
    private final Set<String> nodes;

    public ConsistentHashRouter(int virtualNodes) {
        this.ring = new TreeMap<>();
        this.virtualNodes = virtualNodes;
        this.nodes = new HashSet<>();
    }

    /**
     * Add a real node to the ring.
     * Creates `virtualNodes` entries on the ring for this node.
     * Each virtual node is hashed to a different position.
     */
    public void addNode(String node) {
        nodes.add(node);
        for (int i = 0; i < virtualNodes; i++) {
            int hash = hash(node + "-" + i);
            ring.put(hash, node);
        }
    }

    /**
     * Remove a real node from the ring.
     * Removes all its virtual node entries.
     */
    public void removeNode(String node) {
        nodes.remove(node);
        for (int i = 0; i < virtualNodes; i++) {
            int hash = hash(node + "-" + i);
            ring.remove(hash);
        }
    }

    /**
     * Route a key to a node.
     *
     * 1. Hash the key to get a position on the ring.
     * 2. Find the first virtual node at or clockwise from that position.
     *    TreeMap.ceilingEntry() does this in O(log n).
     * 3. If we're past the last entry, wrap to the first entry on the ring
     *    (the ring is circular).
     * 4. Return the real node that owns that virtual node.
     */
    public String route(String key) {
        if (ring.isEmpty()) {
            return null;
        }

        int hash = hash(key);

        // ceilingEntry: smallest key >= hash (walk clockwise)
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            // Wrapped past the end of the ring → go to the first entry
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * Get how many virtual nodes are on the ring.
     */
    public int getRingSize() {
        return ring.size();
    }

    /**
     * Get the set of real nodes currently in the ring.
     */
    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    /**
     * Hash function using SHA-256 for excellent distribution.
     * Takes the first 4 bytes of the digest and converts to a positive int.
     */
    private int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes());
            // Take first 4 bytes → int, mask to positive
            return ((digest[0] & 0xFF) << 24
                  | (digest[1] & 0xFF) << 16
                  | (digest[2] & 0xFF) << 8
                  | (digest[3] & 0xFF)) & Integer.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
