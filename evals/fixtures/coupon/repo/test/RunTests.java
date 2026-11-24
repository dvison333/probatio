public class RunTests {
    public static void main(String[] args) {
        CouponStore store = new CouponStore();
        Ledger ledger = new Ledger();
        long[] now = {1_000};
        CouponService coupons = new CouponService(store, ledger, () -> now[0]);
        store.add(new Coupon("WELCOME", 500, 2_000));
        store.add(new Coupon("OLD", 500, 900));

        check(coupons.redeem("WELCOME", "order-1") == CouponService.Result.OK, "first redemption succeeds");
        check(coupons.redeem("WELCOME", "order-2") == CouponService.Result.ALREADY_REDEEMED, "second is rejected");
        check(coupons.redeem("OLD", "order-3") == CouponService.Result.EXPIRED, "expired is rejected");
        check(coupons.redeem("NOPE", "order-4") == CouponService.Result.NOT_FOUND, "unknown is rejected");
        check(ledger.count("coupon:WELCOME") == 1, "one credit for WELCOME");
        check("order-1".equals(store.redeemedBy("WELCOME")), "redeemed by order-1");

        RefundService refunds = new RefundService(ledger);
        check(refunds.refund("order-1", 500), "first refund succeeds");
        check(!refunds.refund("order-1", 500), "second refund is rejected");
        check(ledger.count("refund:order-1") == 1, "one debit for order-1");
        System.out.println("all tests passed");
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
