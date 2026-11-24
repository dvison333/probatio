import java.util.ArrayList;
import java.util.List;

/** What happened, for support staff. */
class EventLog {
    private final List<String> lines = new ArrayList<>();

    void add(String line) {
        lines.add(line);
    }

    List<String> lines() {
        return List.copyOf(lines);
    }
}
