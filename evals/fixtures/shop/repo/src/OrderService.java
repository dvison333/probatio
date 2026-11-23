import java.util.Optional;

/** Places orders and moves a pending order forward once its payment outcome is known. */
class OrderService {
    private final OrderRepository orders;
    private final Inventory inventory;
    private final Payments payments;
    private final EventLog events;

    OrderService(OrderRepository orders, Inventory inventory, Payments payments, EventLog events) {
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
        this.events = events;
    }

    /** Returns the order's status after this attempt; a repeated order id returns the existing order's status. */
    OrderStatus place(Order order, long now) {
        Optional<OrderRecord> existing = orders.find(order.id());
        if (existing.isPresent()) {
            return existing.get().status();
        }
        Optional<String> reservation = inventory.reserve(order.sku(), order.quantity(), now);
        if (reservation.isEmpty()) {
            orders.save(new OrderRecord(order, null, OrderStatus.FAILED, "out of stock"));
            events.add("order " + order.id() + " failed: out of stock");
            return OrderStatus.FAILED;
        }
        OrderRecord record = new OrderRecord(order, reservation.get(), OrderStatus.PAYMENT_PENDING, null);
        orders.save(record);
        settle(record);
        return record.status();
    }

    /** Asks the gateway about a pending order's payment and finishes the order when the answer is definite. */
    void settle(OrderRecord record) {
        if (record.status() != OrderStatus.PAYMENT_PENDING) {
            return;
        }
        record.countPaymentAttempt();
        switch (payments.charge(record.paymentKey, record.order.cents())) {
            case CHARGED:
                if (inventory.commit(record.reservationId)) {
                    finish(record, OrderStatus.CONFIRMED, "paid");
                } else {
                    payments.refund(record.paymentKey);
                    finish(record, OrderStatus.FAILED, "stock no longer held");
                }
                break;
            case DECLINED:
                inventory.release(record.reservationId);
                finish(record, OrderStatus.FAILED, "payment declined");
                break;
            default:
                events.add("order " + record.order.id() + " still waiting for payment");
        }
    }

    private void finish(OrderRecord record, OrderStatus status, String why) {
        record.finish(status, why);
        events.add("order " + record.order.id() + " " + status + ": " + why);
    }
}
