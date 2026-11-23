import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coupons and which order redeemed each one. Every method is thread-safe. */
class CouponStore {
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    private final Map<String, String> redeemedBy = new ConcurrentHashMap<>();

    void add(Coupon coupon) {
        coupons.put(coupon.code(), coupon);
    }

    Coupon find(String code) {
        return coupons.get(code);
    }

    /** Atomically records the redemption; false when another order already holds the coupon. */
    boolean claim(String code, String orderId) {
        return redeemedBy.putIfAbsent(code, orderId) == null;
    }

    String redeemedBy(String code) {
        return redeemedBy.get(code);
    }
}
