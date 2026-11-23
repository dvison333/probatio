import java.util.List;
import java.util.Optional;

/** The entry point. Every public method holds the shop's lock for its whole duration. */
class Shop {
    private final Clock clock;
    private final Inventory inventory;
    private final OrderRepository orders = new OrderRepository();
    private final EventLog events = new EventLog();
    private final OrderService service;
    private final ReservationReaper reaper;
    private final PaymentRetrier retrier;

    Shop(Clock clock, PaymentGateway gateway, long reservationTtlMillis) {
        this.clock = clock;
        this.inventory = new Inventory(reservationTtlMillis);
        this.service = new OrderService(orders, inventory, new Payments(gateway, events), events);
        this.reaper = new ReservationReaper(inventory, events);
        this.retrier = new PaymentRetrier(orders, service);
    }

    synchronized void stock(String sku, int units) {
        inventory.stock(sku, units);
    }

    synchronized OrderStatus place(Order order) {
        return service.place(order, clock.now());
    }

    synchronized void reaperTick() {
        reaper.tick(clock.now());
    }

    synchronized void retryTick() {
        retrier.tick();
    }

    synchronized Optional<OrderStatus> status(String orderId) {
        return orders.find(orderId).map(OrderRecord::status);
    }

    synchronized boolean anyPending() {
        return !orders.withStatus(OrderStatus.PAYMENT_PENDING).isEmpty();
    }

    synchronized int confirmedUnits(String sku) {
        return orders.confirmedUnits(sku);
    }

    synchronized int stocked(String sku) {
        return inventory.stocked(sku);
    }

    synchronized int available(String sku) {
        return inventory.available(sku);
    }

    synchronized int reserved(String sku) {
        return inventory.reserved(sku);
    }

    synchronized int sold(String sku) {
        return inventory.sold(sku);
    }

    synchronized List<String> events() {
        return events.lines();
    }
}
