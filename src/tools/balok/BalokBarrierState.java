package tools.balok;

import balok.causality.ClockController;

public class BalokBarrierState {
    private ClockController vc;

    public BalokBarrierState(ClockController vc) {
        this.vc = vc;
    }

    public ClockController getV() {
        return this.vc;
    }

    public void setV(ClockController vc) {
        this.vc = vc;
    }
}
