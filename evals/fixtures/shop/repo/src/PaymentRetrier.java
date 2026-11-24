/** Asks the gateway again about orders whose payment outcome is still unknown. */
class PaymentRetrier {
    private final OrderRepository orders;
    private final OrderService service;

    PaymentRetrier(OrderRepository orders, OrderService service) {
        this.orders = orders;
        this.service = service;
    }

    void tick() {
        for (OrderRecord record : orders.withStatus(OrderStatus.PAYMENT_PENDING)) {
            service.settle(record);
        }
    }
}
