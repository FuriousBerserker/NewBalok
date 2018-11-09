package tools.balok;

import balok.causality.*;
import rr.meta.SourceLocation;

import java.util.concurrent.locks.ReentrantLock;


public class SyncShadowLocation implements BalokShadowLocation {

    private final LocationTracker<MemoryAccess, Epoch> tracker;

    private final ReentrantLock lock;

    public SyncShadowLocation() {
        this.tracker = new LocationTracker<>();
        this.lock = new ReentrantLock();
    }

    public void add(TaskView view, AccessMode mode, SourceLocation loc, int  threadID) {
        MemoryAccess<SourceLocation> curr = MemoryAccess.get(mode, loc, threadID);
        final AccessEntry<MemoryAccess, Epoch> prev;
        //if (tracker.lookupConflict(curr) != null) { // try a racy-check
            lock.lock();
            try {
                EpochSet<MemoryAccess, Epoch> prevReads = tracker.getReads();
                AccessEntry<MemoryAccess, Epoch> lastWrite =  tracker.getLastWrite();
                prev = tracker.lookupConflict(lastWrite == null ? null : lastWrite.getAccess(),
                        lastWrite == null ? null : lastWrite.getValue(), prevReads, curr, view);
                if (prev != null) {
                    System.out.println("Race Detected!");
                    System.out.println("Access 1: " + prev.getAccess() + " " + prev.getValue());
                    System.out.println("Access 2: " + curr + " " + view);
                    if (mode == AccessMode.WRITE) {
                        tracker.unsafeAdd(curr, view);
                    }
                } else {
                    tracker.unsafeAdd(curr, view);
                }
            } finally {
                lock.unlock();
            }
//        } else {
//            lock.lock();
//            try {
//                tracker.unsafeAdd(curr);
//            } finally {
//                lock.unlock();
//            }
//        }
    }
}
