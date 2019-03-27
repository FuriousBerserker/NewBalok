package tools.fasttrack_frontend;

import balok.causality.AccessMode;
import tools.balok.BalokShadowLocation;

public class ExclusiveFTState implements BalokShadowLocation {

    private int epoch;

    private boolean isWrite;

    private final int lastTid;

    public ExclusiveFTState(int epoch, boolean isWrite, int lastTid) {
        this.epoch = epoch;
        this.isWrite = isWrite;
        this.lastTid = lastTid;
    }

    public boolean isExclusive(int tid) {
        if (lastTid == tid) {
            return true;
        } else {
            return false;
        }
    }

    public int getEpoch() {
        return epoch;
    }

    public boolean isWrite() {
        return isWrite;
    }

    public int getLastTid() {
        return lastTid;
    }
}
