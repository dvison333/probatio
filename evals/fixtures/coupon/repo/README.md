# Coupon service

Applies discount coupons and refunds to orders.

## Contract

- A coupon code is single-use: its amount is credited at most once, to one order.
- An expired coupon is never credited.
- An order is refunded at most once.

Requests arrive concurrently from several web workers that share one `CouponService` and one `RefundService`.

## Tests

```sh
sh test.sh
```
