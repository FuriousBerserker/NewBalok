package tools.balok;

import balok.causality.TaskId;
import balok.causality.TaskView;
import balok.causality.Event;
import balok.causality.Epoch;
import balok.vc.AllToAllOrdering;
import rr.meta.SourceLocation;

public class TaskViewForDebug<T extends TaskId> extends TaskView<T> {
    
    private SourceLocation loc;

    public TaskViewForDebug(Event<T> local, AllToAllOrdering cyclic, SourceLocation loc) {
        super(local, cyclic);
        this.loc = loc;
    }

    public SourceLocation getLoc() {
        return this.loc;
    }

    @Override
    public Epoch<T> project() {
        return new EpochForDebug<T>(getLocal().project(), getCyclic(), loc);
    }
}
