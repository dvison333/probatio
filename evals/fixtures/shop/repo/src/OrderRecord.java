/** An order and everything the shop has learned about it. */
class OrderRecord {
    final Order order;
    final String reservationId;
    final String paymentKey;
    private OrderStatus status;
    private String reason;
    private int paymentAttempts;

    OrderRecord(Order order, String reservationId, OrderStatus status, String reason) {
        this.order = order;
        this.reservationId = reservationId;
        this.paymentKey = "pay-" + order.id();
        this.status = status;
        this.reason = reason;
    }

    OrderStatus status() {
        return status;
    }

    String reason() {
        return reason;
    }

    int paymentAttempts() {
        return paymentAttempts;
    }

    void countPaymentAttempt() {
        paymentAttempts++;
    }

    void finish(OrderStatus finalStatus, String why) {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("order " + order.id() + " is already " + status);
        }
        status = finalStatus;
        reason = why;
    }
}
