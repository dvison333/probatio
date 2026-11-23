import java.util.concurrent.atomic.AtomicLong;

/** Hands out document version numbers. */
class Versions {
    private final AtomicLong sequence = new AtomicLong();

    long first() {
        return sequence.incrementAndGet();
    }

    long next(long current) {
        return sequence.incrementAndGet();
    }
}
