package tools.fasttrack_frontend;

import balok.causality.AccessMode;
import tools.balok.BalokShadowLocation;
import tools.util.Epoch;

public class ExclusiveFTState implements BalokShadowLocation {

    private int epoch;

    private boolean isWrite;

    public ExclusiveFTState(int epoch, boolean isWrite) {
        this.epoch = epoch;
        this.isWrite = isWrite;
    }

    public boolean isExclusive(int tid) {
        if (Epoch.tid(epoch) == tid) {
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

    // need to guarantee atomic invocation
    public void update(int epoch, boolean isWrite) {
        this.epoch = epoch;
        this.isWrite = isWrite;
    }
}
