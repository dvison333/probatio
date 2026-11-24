import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class RefundService {
    private final Ledger ledger;
    private final Map<String, Long> refunded = new ConcurrentHashMap<>();

    RefundService(Ledger ledger) {
        this.ledger = ledger;
    }

    /** Returns false when the order was already refunded. */
    boolean refund(String orderId, long cents) {
        if (refunded.putIfAbsent(orderId, cents) != null) {
            return false;
        }
        ledger.debit(orderId, cents, "refund:" + orderId);
        return true;
    }
}
