package tools.balok;

import balok.causality.ClockController;

public class BalokVolatileState {

    private ClockController vc;

    public BalokVolatileState(ClockController vc) {
        this.vc = vc;
    }

    public ClockController getV() {
        return this.vc;
    }

    public void setV(ClockController vc) {
        this.vc = vc;
    }
}
