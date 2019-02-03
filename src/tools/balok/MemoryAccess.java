package tools.balok;

import balok.causality.AccessMode;
import balok.causality.Event;
import balok.causality.PtpCausalityFactory;
import balok.causality.TaskView;
import balok.causality.vc.FlatController;
import balok.causality.vc.VectorEvent;
import balok.causality.vc.VectorClock;
import balok.vc.AllToAllOrdering;
import balok.ser.VectorEventSerializer;
import balok.ser.AllToAllOrderingSerializer;
//import rr.meta.SourceLocation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class MemoryAccess implements Serializable {

    private static final long serialVersionUID = 4L;

    private AccessMode mode;

    private int address;

    private int tid;

    private int ticket;

    private TaskView vc;

    private String file;

    private int line;

    private int offset;

    public MemoryAccess(AccessMode mode, int address, int tid, int ticket, TaskView vc, String file, int line, int offset) {
        this.mode = mode;
        this.address = address;
        this.tid = tid;
        this.ticket = ticket;
        this.vc = vc;
        this.file = file;
        this.line = line;
        this.offset = offset;
    }

    public AccessMode getMode() {
        return mode;
    }

    public void setMode(AccessMode mode) {
        this.mode = mode;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public int getTid() {
        return tid;
    }

    public void setTid(int tid) {
        this.tid = tid;
    }

    public int getTicket() {
        return ticket;
    }

    public void setTicket(int ticket) {
        this.ticket = ticket;
    }

    public TaskView getVC() {
        return vc;
    }

    public void setVC(TaskView vc) {
        this.vc = vc;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
       this.line = line; 
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    private void readObject(ObjectInputStream oInput) {
        try {
            setMode(oInput.readBoolean() ? AccessMode.READ : AccessMode.WRITE);
            setAddress(oInput.readInt());
            setTid(oInput.readInt());
            setTicket(oInput.readInt());
            // VectorEvent
            VectorEvent ve = VectorEventSerializer.readVectorEvent(PtpCausalityFactory.VECTOR_MUT, () -> {
                int data = 0;
                try {
                    data = oInput.readInt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return data;
            });
            // AllToAllOrdering
            AllToAllOrdering ordering = AllToAllOrderingSerializer.readOrdering(() -> {
                int data = 0;
                try {
                    data = oInput.readInt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return data;
            }); 
            setVC(new TaskView(ve, ordering));
            // location
            setFile(oInput.readUTF());
            setLine(oInput.readInt());
            setOffset(oInput.readInt());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeObject(ObjectOutputStream oOutput) {
        try {
            oOutput.writeBoolean(mode == AccessMode.READ);
            oOutput.writeInt(address);
            oOutput.writeInt(tid);
            oOutput.writeInt(ticket);
            // VectorEvent
            VectorEventSerializer.writeVectorEvent(i -> {
                try {
                    oOutput.writeInt(i);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, (VectorEvent)vc.getLocal());
            // AllToAllOrdering
            AllToAllOrderingSerializer.writeOrdering(i -> {
                try {
                    oOutput.writeInt(i);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, vc.getCyclic());
            // location
            oOutput.writeUTF(file);
            oOutput.writeInt(line);
            oOutput.writeInt(offset);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
