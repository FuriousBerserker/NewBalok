package tools.balok;

import balok.causality.ClockController;

public class BalokLockState {

    private ClockController vc;

    public BalokLockState(ClockController vc) {
        this.vc = vc;
    }

    public ClockController getV() {
        return this.vc;
    }

    public void setV(ClockController vc) {
        this.vc = vc;
    }
}
