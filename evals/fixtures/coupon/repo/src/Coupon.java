record Coupon(String code, long cents, long expiresAtMillis) {
    boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
