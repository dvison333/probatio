/** Frees stock held by reservations that outlived their time, so abandoned checkouts do not block sales. */
class ReservationReaper {
    private final Inventory inventory;
    private final EventLog events;

    ReservationReaper(Inventory inventory, EventLog events) {
        this.inventory = inventory;
        this.events = events;
    }

    void tick(long now) {
        int expired = inventory.expire(now);
        if (expired > 0) {
            events.add("expired " + expired + " reservation(s)");
        }
    }
}
