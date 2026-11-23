/** The external payment service. See the README for what it guarantees. */
interface PaymentGateway {
    enum Response { CHARGED, DECLINED }

    /** Thrown when the gateway does not answer in time. The charge may or may not have happened. */
    class Timeout extends Exception {
        Timeout(String message) {
            super(message);
        }
    }

    Response charge(String idempotencyKey, long cents) throws Timeout;

    void refund(String idempotencyKey);
}
