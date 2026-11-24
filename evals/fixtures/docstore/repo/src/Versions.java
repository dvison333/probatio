/** Hands out document version numbers. */
class Versions {
    long first() {
        return 1;
    }

    long next(long current) {
        return current + 1;
    }
}
