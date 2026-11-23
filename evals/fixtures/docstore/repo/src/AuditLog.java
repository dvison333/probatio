import java.util.ArrayList;
import java.util.List;

/** Append-only record of store changes, in the order they were applied. */
class AuditLog {
    record Entry(String action, String id, long version) {}

    private final List<Entry> entries = new ArrayList<>();

    synchronized void record(String action, String id, long version) {
        entries.add(new Entry(action, id, version));
    }

    synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }
}
