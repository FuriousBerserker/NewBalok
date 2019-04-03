package tools.fasttrack_frontend;

import java.nio.file.AccessMode;

public class FTSerializedState {

    private int address;

    private boolean isWrite;

    private int[] event;

    private int ticket;

    private int tid;

    public FTSerializedState(int address, boolean isWrite, int[] event, int ticket, int tid) {
        this.address = address;
        this.isWrite = isWrite;
        this.event = event;
        this.ticket = ticket;
        this.tid = tid;
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

    public int getTid() {
        return tid;
    }
}
