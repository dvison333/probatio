import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An in-memory gateway whose answers a test scripts per idempotency key. Unscripted charges succeed. */
class ScriptedGateway implements PaymentGateway {
    enum Step {
        CHARGE,
        DECLINE,
        /** Times out without charging. */
        TIMEOUT,
        /** Charges the customer, then times out before answering. */
        CHARGE_THEN_TIMEOUT
    }

    private final Map<String, Deque<Step>> scripts = new HashMap<>();
    private final Map<String, Response> settled = new HashMap<>();
    private final Map<String, Long> charged = new HashMap<>();
    private final Set<String> refunded = new HashSet<>();

    void script(String idempotencyKey, List<Step> steps) {
        scripts.put(idempotencyKey, new ArrayDeque<>(steps));
    }

    @Override
    public Response charge(String idempotencyKey, long cents) throws Timeout {
        Response earlier = settled.get(idempotencyKey);
        if (earlier != null) {
            return earlier;
        }
        Deque<Step> script = scripts.get(idempotencyKey);
        Step step = script == null || script.isEmpty() ? Step.CHARGE : script.poll();
        switch (step) {
            case TIMEOUT:
                throw new Timeout("no answer for " + idempotencyKey);
            case DECLINE:
                settled.put(idempotencyKey, Response.DECLINED);
                return Response.DECLINED;
            case CHARGE_THEN_TIMEOUT:
                settle(idempotencyKey, cents);
                throw new Timeout("answer lost for " + idempotencyKey);
            default:
                settle(idempotencyKey, cents);
                return Response.CHARGED;
        }
    }

    private void settle(String idempotencyKey, long cents) {
        settled.put(idempotencyKey, Response.CHARGED);
        charged.put(idempotencyKey, cents);
    }

    @Override
    public void refund(String idempotencyKey) {
        if (charged.containsKey(idempotencyKey)) {
            refunded.add(idempotencyKey);
        }
    }

    /** True while the customer has paid for this key and has not been refunded. */
    boolean holdsMoney(String idempotencyKey) {
        return charged.containsKey(idempotencyKey) && !refunded.contains(idempotencyKey);
    }
}
