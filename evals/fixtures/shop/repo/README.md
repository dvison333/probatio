# Shop

Takes orders for stocked items: reserves stock, charges the customer through a payment gateway, and confirms the order.

## How it runs

- `Shop` is the only entry point. Each of its public methods runs under one lock, so operations never overlap; they interleave only as whole operations, in any order.
- `Shop.place` is called by web requests. `Shop.reaperTick` and `Shop.retryTick` are called by two independent timers, at arbitrary moments.
- Time comes from the injected `Clock` and never goes backwards.

## The payment gateway

The gateway is an external service and the only part that can fail:

- A charge returns `CHARGED` or `DECLINED`, or times out. After a timeout the outcome is unknown: the customer may or may not have been charged.
- Charges carry an idempotency key. Repeating a charge with the same key never charges twice; it returns the outcome of the charge that key already made, if any.
- Refunds use the same key and are idempotent.

`ScriptedGateway` is an in-memory gateway with exactly these behaviours, for tests.

## Contract

1. **No overselling.** For every item, the units in `CONFIRMED` orders never exceed the units stocked.
2. **No charge without goods.** Once no order is `PAYMENT_PENDING`, a customer has been charged (and not refunded) for an order if and only if that order is `CONFIRMED`.
3. **Stock accounting.** For every item, available + reserved + sold units always equal the units stocked.

## Tests

```sh
sh test.sh
```
