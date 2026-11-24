import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Stock per item. A reservation holds units for one order until it is committed, released, or expires. */
class Inventory {
    private record Reservation(String sku, int quantity, long expiresAt) {}

    private final long reservationTtlMillis;
    private final Map<String, Integer> stocked = new HashMap<>();
    private final Map<String, Integer> available = new HashMap<>();
    private final Map<String, Integer> sold = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final Set<String> committed = new HashSet<>();
    private long nextReservation = 1;

    Inventory(long reservationTtlMillis) {
        this.reservationTtlMillis = reservationTtlMillis;
    }

    void stock(String sku, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        stocked.merge(sku, units, Math::addExact);
        available.merge(sku, units, Integer::sum);
    }

    /** Holds units for an order, or returns empty when there are not enough. */
    Optional<String> reserve(String sku, int quantity, long now) {
        if (available(sku) < quantity) {
            return Optional.empty();
        }
        String id = "res-" + nextReservation++;
        available.merge(sku, -quantity, Integer::sum);
        reservations.put(id, new Reservation(sku, quantity, now + reservationTtlMillis));
        return Optional.of(id);
    }

    /** Returns held units to the shelf. Releasing a reservation that is gone does nothing. */
    void release(String reservationId) {
        Reservation reservation = reservations.remove(reservationId);
        if (reservation != null) {
            available.merge(reservation.sku(), reservation.quantity(), Integer::sum);
        }
    }

    /**
     * Turns held units into sold units. Returns false when the reservation no longer holds them: it was
     * released, it expired, or it never existed. Committing twice is safe: the second call reports success again.
     */
    boolean commit(String reservationId) {
        Reservation reservation = reservations.remove(reservationId);
        if (reservation == null) {
            return committed.contains(reservationId);
        }
        sold.merge(reservation.sku(), reservation.quantity(), Integer::sum);
        committed.add(reservationId);
        return true;
    }

    /** Releases every reservation whose time is up and returns how many there were. */
    int expire(long now) {
        List<String> due = new ArrayList<>();
        for (Map.Entry<String, Reservation> entry : reservations.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                due.add(entry.getKey());
            }
        }
        due.forEach(this::release);
        return due.size();
    }

    int stocked(String sku) {
        return stocked.getOrDefault(sku, 0);
    }

    int available(String sku) {
        return available.getOrDefault(sku, 0);
    }

    int sold(String sku) {
        return sold.getOrDefault(sku, 0);
    }

    int reserved(String sku) {
        int units = 0;
        for (Reservation reservation : reservations.values()) {
            if (reservation.sku().equals(sku)) {
                units += reservation.quantity();
            }
        }
        return units;
    }
}
