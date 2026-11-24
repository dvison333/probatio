import java.util.ArrayList;
import java.util.List;

/** Append-only record of money movements. */
class Ledger {
    record Entry(String orderId, long cents, String reason) {}

    private final List<Entry> entries = new ArrayList<>();

    synchronized void credit(String orderId, long cents, String reason) {
        entries.add(new Entry(orderId, cents, reason));
    }

    synchronized void debit(String orderId, long cents, String reason) {
        entries.add(new Entry(orderId, -cents, reason));
    }

    synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    synchronized long count(String reason) {
        return entries.stream().filter(e -> e.reason().equals(reason)).count();
    }
}
