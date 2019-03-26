package tools.balok;

import balok.causality.AccessMode;
import balok.causality.TaskTracker;
import rr.meta.SourceLocation;

public class SyncMemoryTracker implements MemoryTracker {

    @Override
    public void onLastExclusiveAccess(BalokShadowLocation oldShadow, BalokShadowLocation newShadow) {

    }

    @Override
    public void onAccess(TaskTracker tracker, BalokShadowLocation loc, AccessMode mode, SourceLocation info, int threadID) {
        SyncShadowLocation shadow = (SyncShadowLocation)loc;
        shadow.add(tracker.createTimestamp(), mode, info, threadID);
    }

    @Override
    public BalokShadowLocation createLocation() {
        return new SyncShadowLocation();
    }

    @Override
    public void onSyncEvent(TaskTracker tracker) {
        // nothing to do
    }

    @Override
    public void onEnd(TaskTracker tracker) {
        // nothing to do
    }

}
