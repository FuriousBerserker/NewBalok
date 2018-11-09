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
        AccessEntry<MemoryAccess, Event<Epoch>> curr = new AccessEntry<>(new MemoryAccess(mode, loc, threadID), view);
        final AccessEntry<MemoryAccess, Epoch> prev;
        System.out.println("Current: " + curr.getAccess() + " " + curr.getValue() + " " + tracker.hashCode());
        //if (tracker.lookupConflict(curr) != null) { // try a racy-check
            lock.lock();
            try {
                prev = tracker.lookupConflict(curr);
                if (prev != null) {
                    System.out.println("Race Detected!");
                    System.out.println("Access 1: " + prev.getAccess() + " " + prev.getValue());
                    System.out.println("Access 2: " + curr.getAccess() + " " + curr.getValue());
                    if (mode == AccessMode.WRITE) {
                        tracker.unsafeAdd(curr);
                    }
                } else {
                    tracker.unsafeAdd(curr);
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
