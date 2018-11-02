package tools.balok;


public class BalokBarrierState {

    private Object barrier;

    public BalokBarrierState(Object barrier) {
        this.barrier = barrier;
    }

    public void setBarrier(Object barrier) {
        this.barrier = barrier;
    }

    public int getBarrierID() {
        return barrier.hashCode();
    }
}
