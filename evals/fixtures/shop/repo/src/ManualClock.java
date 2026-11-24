/** A clock that tests move by hand. */
class ManualClock implements Clock {
    private long now;

    @Override
    public long now() {
        return now;
    }

    void advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("time never goes backwards");
        }
        now += millis;
    }
}
