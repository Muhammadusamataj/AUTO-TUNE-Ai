package com.autotuneeq.mvp;

/** Coalesces bursts of media-session callbacks into at most one extra poll per 250 ms (battery/CPU on slow head units). */
public final class PollGate {
    public static final long SOON_MS = 250;
    private long dueAt = Long.MIN_VALUE / 2;
    /** @return delay for an extra poll, or -1 if a poll is already due within SOON_MS (or overdue). */
    public synchronized long requestSoon(long now) {
        if (dueAt - now <= SOON_MS) return -1;
        dueAt = now + SOON_MS; return SOON_MS;
    }
    /** Record that a poll was scheduled for now+delay. */
    public synchronized void scheduled(long now, long delay) { dueAt = now + delay; }
}
