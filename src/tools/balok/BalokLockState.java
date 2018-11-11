package tools.balok;

import balok.causality.TaskId;
import balok.causality.TaskView;

public class BalokLockState<T extends TaskId> {

    private TaskView<T> view;

    public BalokLockState() {
        this.view = null;
    }

    public BalokLockState(TaskView<T> view) {
        this.view = view;
    }

    public TaskView<T> getView() {
        return this.view;
    }

    public void setView(TaskView<T> view) {
        this.view = view;
    }

    @Override
    public String toString() {
        return view.toString();
    }
}
