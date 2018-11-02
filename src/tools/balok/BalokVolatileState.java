package tools.balok;

import balok.causality.TaskView;

public class BalokVolatileState<T> {

    private TaskView<T> view;

    public BalokVolatileState() {
        this.view = null;
    }

    public BalokVolatileState(TaskView<T> view) {
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
