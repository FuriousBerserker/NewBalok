package tools.balok;

import balok.causality.Access;
import balok.causality.AccessMode;
import rr.meta.SourceLocation;

public final class MemoryAccess implements Access {
    private final AccessMode mode;
    private final SourceLocation loc;

    public MemoryAccess(AccessMode mode, SourceLocation loc) {
        this.mode = mode;
        this.loc = loc;
    }

    @Override
    public AccessMode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return mode.toString() + " " + loc.toString();
    }

    public String getSourceInfo() {return loc.toString();}
}
