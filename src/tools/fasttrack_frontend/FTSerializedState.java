package tools.fasttrack_frontend;

import java.nio.file.AccessMode;

public class FTSerializedState {

    private int address;

    private boolean isWrite;

    private int[] event;

    private int ticket;

    public FTSerializedState() {

    }

    public FTSerializedState(int address, boolean isWrite, int[] event, int ticket) {
        this.address = address;
        this.isWrite = isWrite;
        this.event = event;
        this.ticket = ticket;
    }

    public int getAddress() {
        return address;
    }

    public boolean isWrite() {
        return isWrite;
    }

    public int[] getEvent() {
        return event;
    }

    public int getTicket() {
        return ticket;
    }

    public void update(int address, boolean isWrite, int[] event, int ticket) {
        this.address = address;
        this.isWrite = isWrite;
        this.event = event;
        this.ticket = ticket;
    }
}
