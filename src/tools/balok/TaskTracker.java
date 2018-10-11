package tools.balok;

import balok.causality.ClockController;
import balok.causality.TaskView;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskTracker<T> {

    //We remove all code that tackles Phaser

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    //TODO: Don't need this field
    private static final AtomicInteger GLOBAL_TIME = new AtomicInteger();

    private int globalTime;

    private ClockController<T> ptp;

    private TaskView<T> cache;

    private boolean isCacheValid;

    public TaskTracker(ClockController<T> root, int globalTime) {
        this(globalTime, root);
    }

    public TaskTracker(ClockController<T> root) {
        this(root, GLOBAL_TIME.getAndIncrement());
    }

    private TaskTracker(int globalTime, ClockController local) {
        this.cache = null;
        this.globalTime = globalTime;
        this.ptp = local;
        this.isCacheValid = false;
    }

    public TaskTracker createChild() {
        return new TaskTracker(this.globalTime, this.ptp.spawnChild());
    }

    public void afterSpawn() {
        this.produceEvent();
    }

    public void produceEvent() {
        this.ptp = this.ptp.produceEvent();
        this.isCacheValid = false;
    }

    public void join(TaskView<T> other) {
        ClockController<T> join = this.ptp.join(other.getLocal());
        if (this.ptp != join) {
            this.ptp = join;
            this.isCacheValid = false;
        }
    }

    public TaskView<T> createTimestamp() {
        if (!this.isCacheValid) {
            this.cache = new TaskView(this.globalTime, this.ptp.createView(), null, null);
        }
        return this.cache;
    }

}
