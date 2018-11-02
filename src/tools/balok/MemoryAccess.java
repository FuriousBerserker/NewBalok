package tools.balok;

import balok.causality.Access;
import balok.causality.AccessMode;
import rr.meta.SourceLocation;

public final class MemoryAccess implements Access {
    private final AccessMode mode;
    private final SourceLocation loc;
    private final int threadID;

    public MemoryAccess(AccessMode mode, SourceLocation loc, int  threadID) {
        this.mode = mode;
        this.loc = loc;
        this.threadID = threadID;
    }

    @Override
    public AccessMode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "Thread " + threadID + " " + mode.toString() + " " + loc.toString();
    }

    public String getSourceInfo() {return loc.toString();}
}
