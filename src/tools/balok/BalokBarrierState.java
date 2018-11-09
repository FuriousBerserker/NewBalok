package tools.balok;

import balok.causality.BarrierState;

public class BalokBarrierState extends BarrierState {

    private Object barrier;

    public BalokBarrierState(Object barrier) {
        super(barrier.hashCode());
        this.barrier = barrier;
    }

    public BalokBarrierState(Object barrier, int barrierID) {
        super(barrierID);
        this.barrier = barrier;
    }

    public void setBarrier(Object barrier) {
        this.barrier = barrier;
    }

}
