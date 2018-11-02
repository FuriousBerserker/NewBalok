package tools.balok;

import balok.causality.AccessMode;
import balok.causality.TaskTracker;
import rr.meta.SourceLocation;

public interface MemoryTracker {
    /**
     * Invoked whenever a task accesses a shared location.
     * @param tracker
     * @param loc
     * @param mode
     */
    void onAccess(TaskTracker tracker, BalokShadowLocation loc, AccessMode mode, SourceLocation info, int threadID);

    BalokShadowLocation createLocation();

    /**
     * Invoked whenever the task emits a synchronization event (thus updates its view).
     * @param tracker
     */
    void onSyncEvent(TaskTracker tracker);

    /**
     * Invoked whenever a task terminates its execution.
     * @param task
     */
    void onEnd(TaskTracker task);
}
