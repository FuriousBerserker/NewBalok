package tools.balok;

import balok.causality.*;
import rr.meta.SourceLocation;

import java.util.concurrent.locks.ReentrantLock;


public class SyncShadowLocation implements BalokShadowLocation {

    private final LocationTracker<Epoch> tracker;

    private final ReentrantLock lock;

    public SyncShadowLocation() {
        this.tracker = new LocationTracker<>();
        this.lock = new ReentrantLock();
    }

    public void add(Event<Epoch> view, AccessMode mode, SourceLocation loc, int threadID) {
        final AccessEntry<Epoch> prev;
        EpochSet<Epoch> reads = tracker.getReads();
        if (mode == AccessMode.READ && reads.mayContain(view)) {
            return;
        }
        Epoch w = tracker.getLastWrite();
        if (mode == AccessMode.WRITE && w != null && view.observe(w) == Causality.SOME_EQ) {
            return;
        }
        lock.lock();
        try {
            EpochSet<Epoch> prevReads = tracker.getReads();
            Epoch lastWrite = tracker.getLastWrite();
            prev = tracker.lookupConflict(lastWrite, prevReads, mode, view);
            if (prev != null) {
                System.out.println("Race Detected!");
                System.out.println("Access 1: " + prev.getAccess() + " " + prev.getValue());
                System.out.println("Access 2: " + mode + " " + view);
                if (mode == AccessMode.WRITE) {
                    tracker.unsafeAdd(mode, view);
                }
            } else {
                tracker.unsafeAdd(mode, view);
            }
        } finally {
            lock.unlock();
        }
    }
}
