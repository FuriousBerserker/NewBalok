package tools.balok;

import balok.causality.AccessMode;
import rr.meta.SourceLocation;

public interface MemoryTracker {
    /**
     * Invoked whenever a task accesses a shared location.
     * @param tracker
     * @param loc
     * @param mode
     */
    void onAccess(TaskTracker tracker, ShadowLocation loc, AccessMode mode, SourceLocation info);

    ShadowLocation createLocation();

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
