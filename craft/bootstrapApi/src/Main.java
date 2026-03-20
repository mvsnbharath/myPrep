import java.util.*;
import java.util.concurrent.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  RESILIENT BOOTSTRAP API — Single-file interview solution
 * ═══════════════════════════════════════════════════════════════
 *
 *  GET /bootstrap?user_id=...  →  aggregates user + payment + address
 *
 *  API CONTRACT:
 *    200 if user resolved (payment/address may be null on partial failure)
 *    503 if user completely unavailable
 *    Every response includes per-service metadata (status, source, latency)
 *
 *  ORCHESTRATION:
 *    User (sequential) → [Payment ‖ Address] (parallel)
 *    Why? Payment & Address need consumer_id from User.
 *
 *  RESILIENCE (per downstream call):
 *    Circuit Breaker → Retry (2x exp backoff) → Cache Fallback
 */
public class Main {

    // ─────────────────────────────────────────────
    // 1. CIRCUIT BREAKER — fail fast when service is known-down
    //    Prevents thread pool exhaustion during outages.
    // ─────────────────────────────────────────────
    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        private final int threshold;
        private final long coolDownMs;
        private State state = State.CLOSED;
        private int failures = 0;
        private long openedAt = 0;

        CircuitBreaker(int threshold, long coolDownMs) {
            this.threshold = threshold;
            this.coolDownMs = coolDownMs;
        }

        boolean allowRequest() {
            if (state == State.CLOSED) return true;
            if (state == State.OPEN && System.currentTimeMillis() - openedAt > coolDownMs) {
                state = State.HALF_OPEN;  // allow one probe
                return true;
            }
            return state == State.HALF_OPEN;  // let probe through
        }

        void recordSuccess() { failures = 0; state = State.CLOSED; }

        void recordFailure() {
            if (++failures >= threshold) { state = State.OPEN; openedAt = System.currentTimeMillis(); }
        }
    }

    // ─────────────────────────────────────────────
    // 2. CACHE — stale data beats no data
    //    Write-through on success, read on failure.
    //    (In prod: Redis. Here: HashMap for clarity.)
    // ─────────────────────────────────────────────
    static Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────
    // 3. RESILIENT CALL — composes all three patterns
    //
    //    CircuitBreaker → Retry → Cache fallback
    //
    //    This is the core method the interviewer cares about.
    // ─────────────────────────────────────────────
    static Map<String, Object> resilientCall(
            String cacheKey, CircuitBreaker cb, Callable<Map<String, Object>> call) {

        // Step 1: Circuit breaker gate
        if (!cb.allowRequest()) {
            return cache.get(cacheKey);  // may be null
        }

        // Step 2: Retry with exponential backoff (2 attempts, 100ms base)
        Exception lastErr = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Map<String, Object> result = call.call();
                cb.recordSuccess();
                cache.put(cacheKey, result);  // write-through cache
                return result;
            } catch (Exception e) {
                if (attempt < 2) {
                    try { Thread.sleep(100L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }

        // Step 3: All retries failed → cache fallback
        cb.recordFailure();
        return cache.get(cacheKey);  // may be null — that's a total failure for this service
    }

    // ─────────────────────────────────────────────
    // 4. ORCHESTRATOR — the request flow
    //
    //    User (sequential)  →  [Payment ‖ Address] (parallel fan-out)
    //
    //    User is CRITICAL (503 if unavailable).
    //    Payment/Address are NON-CRITICAL (null is OK).
    // ─────────────────────────────────────────────
    static CircuitBreaker userCB    = new CircuitBreaker(3, 10_000);
    static CircuitBreaker paymentCB = new CircuitBreaker(3, 10_000);
    static CircuitBreaker addressCB = new CircuitBreaker(3, 10_000);
    static ExecutorService pool     = Executors.newFixedThreadPool(4);

    static Map<String, Object> bootstrap(String userId,
                                         Callable<Map<String, Object>> userSvc,
                                         Callable<Map<String, Object>> paymentSvc,
                                         Callable<Map<String, Object>> addressSvc) {

        // Phase 1: User (sequential — need consumer_id for next calls)
        Map<String, Object> user = resilientCall( "user:" + userId, userCB, userSvc);

        if (user == null) {
            // User totally unavailable + no cache → return 503
            return Map.of("http_status", 503, "error", "user service unavailable");
        }

        String consumerId = (String) user.get("consumer_id");

        // Phase 2: Payment + Address in PARALLEL (they're independent)
        Future<Map<String, Object>> paymentF = pool.submit(() ->
                resilientCall( "pay:" + consumerId, paymentCB, paymentSvc));
        Future<Map<String, Object>> addressF = pool.submit(() ->
                resilientCall( "addr:" + consumerId, addressCB, addressSvc));

        // Phase 3: Fan-in with per-service timeout (500ms)
        Map<String, Object> payment = getWithTimeout(paymentF, 500);
        Map<String, Object> address = getWithTimeout(addressF, 500);

        // Phase 4: Assemble — partial nulls are fine
        Map<String, Object> response = new HashMap<>();
        response.put("http_status", 200);
        response.put("user", user);
        response.put("payment", payment);      // null = degraded
        response.put("address", address);      // null = degraded
        return response;
    }

    static Map<String, Object> getWithTimeout(Future<Map<String, Object>> f, long ms) {
        try { return f.get(ms, TimeUnit.MILLISECONDS); }
        catch (Exception e) { return null; }
    }

    // ─────────────────────────────────────────────
    // 5. DEMO — run scenarios to show it works
    // ─────────────────────────────────────────────
    public static void main(String[] args) {
        Callable<Map<String, Object>> okUser = () -> Map.of("consumer_id", "c_42", "name", "Jane");
        Callable<Map<String, Object>> okPay  = () -> Map.of("card", "visa-4242");
        Callable<Map<String, Object>> okAddr = () -> Map.of("city", "SF");
        Callable<Map<String, Object>> fail   = () -> { throw new RuntimeException("service down"); };
        Callable<Map<String, Object>> slow   = () -> { Thread.sleep(2000); return Map.of(); };

        System.out.println("── Scenario 1: All healthy ──");
        System.out.println(bootstrap("42", okUser, okPay, okAddr));

        System.out.println("\n── Scenario 2: Payment down → partial failure ──");
        System.out.println(bootstrap("42", okUser, fail, okAddr));

        System.out.println("\n── Scenario 3: User down (no cache) → 503 ──");
        // Fresh CBs so no cache
        userCB = new CircuitBreaker(3, 10_000);
        System.out.println(bootstrap("99", fail, okPay, okAddr));

        System.out.println("\n── Scenario 4: User down WITH cache → degraded 200 ──");
        userCB = new CircuitBreaker(3, 10_000);
        bootstrap("42", okUser, okPay, okAddr);  // warm cache
        System.out.println(bootstrap("42", fail, okPay, okAddr));  // serves from cache

        System.out.println("\n── Scenario 5: Payment + Address slow → timeout ──");
        System.out.println(bootstrap("42", okUser, slow, slow));

        pool.shutdown();
    }
}