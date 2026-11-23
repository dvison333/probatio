import java.util.List;

public class RunTests {
    static final long TTL = 60_000;

    public static void main(String[] args) {
        paidOrderIsConfirmed();
        declinedOrderReturnsStock();
        outOfStockFails();
        lostAnswerIsRecoveredByRetry();
        abandonedReservationExpires();
        repeatedOrderIdIsNotPlacedTwice();
        System.out.println("all tests passed");
    }

    static void paidOrderIsConfirmed() {
        ScriptedGateway gateway = new ScriptedGateway();
        Shop shop = shop(gateway, 2);
        check(shop.place(new Order("o1", "book", 1, 1500)) == OrderStatus.CONFIRMED, "paid order is confirmed");
        check(shop.sold("book") == 1 && shop.available("book") == 1, "one sold, one left");
        check(gateway.holdsMoney("pay-o1"), "customer was charged");
    }

    static void declinedOrderReturnsStock() {
        ScriptedGateway gateway = new ScriptedGateway();
        gateway.script("pay-o1", List.of(ScriptedGateway.Step.DECLINE));
        Shop shop = shop(gateway, 1);
        check(shop.place(new Order("o1", "book", 1, 1500)) == OrderStatus.FAILED, "declined order fails");
        check(shop.available("book") == 1 && !gateway.holdsMoney("pay-o1"), "stock is back and nothing was charged");
    }

    static void outOfStockFails() {
        Shop shop = shop(new ScriptedGateway(), 1);
        check(shop.place(new Order("o1", "book", 2, 3000)) == OrderStatus.FAILED, "not enough stock");
        check(shop.available("book") == 1, "stock untouched");
    }

    static void lostAnswerIsRecoveredByRetry() {
        ScriptedGateway gateway = new ScriptedGateway();
        gateway.script("pay-o1", List.of(ScriptedGateway.Step.CHARGE_THEN_TIMEOUT));
        Shop shop = shop(gateway, 1);
        check(shop.place(new Order("o1", "book", 1, 1500)) == OrderStatus.PAYMENT_PENDING, "outcome unknown");
        shop.retryTick();
        check(shop.status("o1").orElseThrow() == OrderStatus.CONFIRMED, "retry learns the charge went through");
        check(shop.sold("book") == 1 && gateway.holdsMoney("pay-o1"), "sold once, charged once");
    }

    static void abandonedReservationExpires() {
        ManualClock clock = new ManualClock();
        ScriptedGateway gateway = new ScriptedGateway();
        gateway.script("pay-o1", List.of(ScriptedGateway.Step.TIMEOUT, ScriptedGateway.Step.DECLINE));
        Shop shop = new Shop(clock, gateway, TTL);
        shop.stock("book", 1);
        check(shop.place(new Order("o1", "book", 1, 1500)) == OrderStatus.PAYMENT_PENDING, "outcome unknown");
        check(shop.available("book") == 0, "stock is held");
        clock.advance(TTL);
        shop.reaperTick();
        check(shop.available("book") == 1, "expired reservation frees the stock");
        shop.retryTick();
        check(shop.status("o1").orElseThrow() == OrderStatus.FAILED, "declined on retry");
        check(shop.available("book") == 1, "stock is not returned twice");
    }

    static void repeatedOrderIdIsNotPlacedTwice() {
        ScriptedGateway gateway = new ScriptedGateway();
        Shop shop = shop(gateway, 2);
        Order order = new Order("o1", "book", 1, 1500);
        check(shop.place(order) == OrderStatus.CONFIRMED, "first placement");
        check(shop.place(order) == OrderStatus.CONFIRMED, "second placement reports the same order");
        check(shop.sold("book") == 1, "sold once");
    }

    static Shop shop(PaymentGateway gateway, int books) {
        Shop shop = new Shop(new ManualClock(), gateway, TTL);
        shop.stock("book", books);
        return shop;
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
