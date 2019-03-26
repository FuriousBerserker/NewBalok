package tools.balok;

import balok.causality.AccessMode;
import balok.causality.TaskTracker;
import rr.meta.SourceLocation;

public class NoCheckMemoryTracker implements MemoryTracker {

    public static final BalokShadowLocation NO_CHECK = new BalokShadowLocation() {
    };

    @Override
    public void onLastExclusiveAccess(BalokShadowLocation oldShadow, BalokShadowLocation newShadow) {

    }

    @Override
    public void onAccess(TaskTracker tracker, BalokShadowLocation loc, AccessMode mode, SourceLocation info, int threadID) {
    }

    @Override
    public BalokShadowLocation createLocation() {
        return NO_CHECK;
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
