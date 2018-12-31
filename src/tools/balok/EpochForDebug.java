package tools.balok;

import balok.causality.TaskId;
import balok.causality.Epoch;
import balok.causality.Event;
import balok.vc.AllToAllOrdering;
import rr.meta.SourceLocation;

public class EpochForDebug<T extends TaskId> extends Epoch<T> {
    
    private SourceLocation loc;

    public EpochForDebug(T local, AllToAllOrdering cyclic, SourceLocation loc) {
        super(local, cyclic);
        this.loc = loc;
    }

    public SourceLocation getLoc() {
        return this.loc;
    }
}
