package tools.balok;

import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.TaskTracker;
import balok.causality.TaskView;
import balok.ser.SerializedFrame;
import balok.ser.SerializedFrameBuilder;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.meta.SourceLocation;
import rr.tool.RR;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

public class OutputAccessMemoryTracker implements MemoryTracker {
    private static final AtomicInteger codeGen = new AtomicInteger(Integer.MIN_VALUE);
    private final int code = codeGen.getAndIncrement();
    private SerializedFrameBuilder<Epoch> currentFrame = new SerializedFrameBuilder<>(512);
    private AtomicBoolean active = new AtomicBoolean(true);

    // private final ConcurrentLinkedQueue<MemoryAccess> accesses;

    private Kryo kryo;

    private File folderForLogs;

    private Output oOutput;

    private AtomicLong accessNum;

//    private static final int BUFFER_SIZE = 100;
//
//    private Frame[] buffer;
//
//    private int nextPos = 0;

    public OutputAccessMemoryTracker(Kryo kryo, File folderForLogs, AtomicLong accessNum, final int tid) {
        this.kryo = kryo;
        this.folderForLogs = folderForLogs;
        this.accessNum = accessNum;
        try {
            File threadLocalLog = new File(folderForLogs, tid + ".log");
            oOutput = new Output(new GZIPOutputStream(new FileOutputStream(threadLocalLog)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
//          buffer = new Frame[BUFFER_SIZE];
    }

    @Override
    public BalokShadowLocation createLocation() {
        return null;
    }

    @Override
    public void onAccess(TaskTracker tracker, BalokShadowLocation loc, AccessMode mode, SourceLocation info, int threadID) {
        //TODO: ADD DEBUGGING INFO
        if (mode == AccessMode.READ) {
            if (tracker.containsRead(loc.hashCode())) {
                return;
            } else {
                tracker.cacheRead(loc.hashCode());
            }
        }

        TaskView vc = tracker.createTimestamp();
        TicketGenerator ticketGen = (TicketGenerator)loc;
        int address = ticketGen.getHashCode();
        int ticket = ticketGen.getTicket();
//        if (RR.unitTestOption.get()) {
//            vc = new TaskViewForDebug(vc.getLocal(), vc.getCyclic(), info);
//        }
        //System.out.println(key.loc.hashCode() + ", " + ticket + ", " + (mode == AccessMode.READ ? 0 : 1) + ", " + tracker.createTimestamp().toString() + ", " + info);

        currentFrame.add(address, mode, vc, ticket);
        if (currentFrame.isFull()) {
            SerializedFrame<Epoch> frame = currentFrame.build();
            kryo.writeObject(oOutput, frame);
            accessNum.addAndGet(frame.size());
        }
    }

    @Override
    public void onSyncEvent(TaskTracker tracker) {
        //push(tracker);
    }

    @Override
    public void onEnd(TaskTracker tracker) {
        if (!currentFrame.isEmpty()) {
            SerializedFrame<Epoch> frame = currentFrame.build();
            kryo.writeObject(oOutput, frame);
            accessNum.addAndGet(frame.size());
        }
        oOutput.close();
        active.set(false);
    }

    @Override
    public int hashCode() {
        return code;
    }

    public boolean isRunning() {
        return active.get();
    }

    @Override
    public String toString() {
        return "(current=" + currentFrame + ")";
    }
}
