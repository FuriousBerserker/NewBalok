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

    public void add(TaskView view, AccessMode mode, SourceLocation loc, int threadID) {
        final AccessEntry<Epoch> prev;
        lock.lock();
        try {
            EpochSet<Epoch> prevReads = tracker.getReads();
            AccessEntry<Epoch> lastWrite = tracker.getLastWrite();
            prev = tracker.lookupConflict(lastWrite == null ? null : lastWrite.getAccess(),
                    lastWrite == null ? null : lastWrite.getValue(), prevReads, mode, view);
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
