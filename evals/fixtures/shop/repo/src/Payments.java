/** Talks to the gateway and turns its answers, and its silences, into one of three outcomes. */
class Payments {
    enum Outcome { CHARGED, DECLINED, UNKNOWN }

    private final PaymentGateway gateway;
    private final EventLog events;

    Payments(PaymentGateway gateway, EventLog events) {
        this.gateway = gateway;
        this.events = events;
    }

    Outcome charge(String key, long cents) {
        try {
            PaymentGateway.Response response = gateway.charge(key, cents);
            events.add("payment " + key + " " + response);
            return response == PaymentGateway.Response.CHARGED ? Outcome.CHARGED : Outcome.DECLINED;
        } catch (PaymentGateway.Timeout timeout) {
            events.add("payment " + key + " timed out");
            return Outcome.UNKNOWN;
        }
    }

    void refund(String key) {
        gateway.refund(key);
        events.add("refund " + key);
    }
}
