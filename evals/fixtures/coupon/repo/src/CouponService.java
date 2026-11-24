import java.util.function.LongSupplier;

class CouponService {
    enum Result { OK, NOT_FOUND, EXPIRED, ALREADY_REDEEMED }

    private final CouponStore store;
    private final Ledger ledger;
    private final LongSupplier clock;

    CouponService(CouponStore store, Ledger ledger, LongSupplier clock) {
        this.store = store;
        this.ledger = ledger;
        this.clock = clock;
    }

    Result redeem(String code, String orderId) {
        Coupon coupon = store.find(code);
        if (coupon == null) {
            return Result.NOT_FOUND;
        }
        if (coupon.expired(clock.getAsLong())) {
            return Result.EXPIRED;
        }
        if (store.isRedeemed(code)) {
            return Result.ALREADY_REDEEMED;
        }
        ledger.credit(orderId, coupon.cents(), "coupon:" + code);
        store.markRedeemed(code, orderId);
        return Result.OK;
    }
}
