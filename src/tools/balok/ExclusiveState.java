package tools.balok;

import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.Event;

public class ExclusiveState implements BalokShadowLocation {

    private Event<Epoch> event;

    private AccessMode mode;

    private final int lastTid;

    public ExclusiveState(Event<Epoch> event, AccessMode mode, int lastTid) {
        this.event = event;
        this.mode = mode;
        this.lastTid = lastTid;
    }

    public boolean isEmpty() {
        return event == null;
    }

    public boolean isExclusive(int tid) {
        if (lastTid == tid) {
            return true;
        } else {
            return false;
        }
    }

    public Event<Epoch> getEvent() {
        return event;
    }

    public AccessMode getMode() {
        return mode;
    }

    public void setEvent(Event<Epoch> event) {
        this.event = event;
    }

    public void setMode(AccessMode mode) {
        this.mode = mode;
    }
}
