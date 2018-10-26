package tools.balok;

import balok.causality.ClockController;
import balok.causality.TaskView;

public class BalokBarrierState<T> {
    private ClockController<T> vc;

    public BalokBarrierState(ClockController<T> vc) {
        this.vc = vc;
    }

    public ClockController<T> getV() {
        return this.vc;
    }

    public void setV(ClockController<T> vc) {
        this.vc = vc;
    }

    public void join(TaskView<T> view) {
        vc = vc.join(view.getLocal());
    }

    @Override
    public String toString() {
        return vc.toString();
    }
}
