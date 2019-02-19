package tools.balok;

import balok.causality.*;
import balok.causality.async.Frame;
import balok.causality.async.FrameBuilder;
import balok.causality.async.ShadowMemory;
import balok.causality.async.ShadowMemoryBuilder;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.meta.SourceLocation;
import rr.tool.RR;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncMemoryTracker implements MemoryTracker {
    private static final AtomicInteger codeGen = new AtomicInteger(Integer.MIN_VALUE);
    private final int code = codeGen.getAndIncrement();
    private FrameBuilder<Epoch> currentFrame = new FrameBuilder<>(512);
    private AtomicBoolean active = new AtomicBoolean(true);

    private final MpscUnboundedArrayQueue<Frame<Epoch>> queue;

    // private final ConcurrentLinkedQueue<MemoryAccess> accesses;

    private Kryo kryo;

    private Output oOutput;

    private AtomicLong accessNum;

    private static final int BUFFER_SIZE = 10000;

    private MemoryAccess[] buffer;

    private int nextPos = 0;

    public AsyncMemoryTracker(MpscUnboundedArrayQueue<Frame<Epoch>> queue, Kryo kryo, Output oOutput, AtomicLong accessNum) {
        this.queue = queue;
        this.kryo = kryo;
        this.oOutput = oOutput;
        this.accessNum = accessNum;
        if (RR.outputAccessOption.get()) {
            buffer = new MemoryAccess[BUFFER_SIZE];
        }
    }

    @Override
    public BalokShadowLocation createLocation() {
        return new AsyncShadowLocation();
    }

    @Override
    public void onAccess(TaskTracker tracker, BalokShadowLocation loc, AccessMode mode, SourceLocation info, int threadID) {
        //TODO: ADD DEBUGGING INFO
        if (mode == AccessMode.READ) {
            if (tracker.containsRead(loc)) {
                return;
            } else {
                tracker.cacheRead(loc);
            }
        }
        AsyncShadowLocation key = (AsyncShadowLocation) loc;
        TaskView vc = tracker.createTimestamp();
        if (RR.unitTestOption.get()) {
            vc = new TaskViewForDebug(vc.getLocal(), vc.getCyclic(), info);
        }
        // A fast-path for checking if the thread already touched the shadow location (remove dups)
        //TODO: temporarily disable it and require further reparation
//        if (key.loc.alreadyIn(mode, vc)) {
//            return;
//        }
        int ticket = key.loc.createTicket();
        //System.out.println(key.loc.hashCode() + ", " + ticket + ", " + (mode == AccessMode.READ ? 0 : 1) + ", " + tracker.createTimestamp().toString() + ", " + info);
        if (RR.outputAccessOption.get()) {
            MemoryAccess ma = new MemoryAccess(mode, key.loc.hashCode(), threadID, ticket, vc, info.getFile(), info.getLine(), info.getOffset());
            buffer[nextPos] = ma;
            nextPos++;
            if (nextPos == BUFFER_SIZE) {
                synchronized (oOutput) {
                    for (MemoryAccess access : buffer) {
                        kryo.writeObject(oOutput, access);
                    }
                }
                nextPos = 0;
                accessNum.addAndGet(BUFFER_SIZE);
            }
        } else {
            if (!key.loc.tryAdd(mode, vc, ticket)) {
                currentFrame.add(key.loc, mode, vc, ticket);
                if (currentFrame.isFull()) {
                    queue.add(currentFrame.build());
                }
            }
        }
    }

    @Override
    public void onSyncEvent(TaskTracker tracker) {
        //push(tracker);
    }

    @Override
    public void onEnd(TaskTracker tracker) {
        Frame frame = currentFrame.isEmpty() ? null : currentFrame.build();
        if (frame != null) {
            queue.add(frame);
        }

        if (RR.outputAccessOption.get() && nextPos != 0) {
            synchronized (oOutput) {
                for (int i = 0; i < nextPos; i++) {
                    kryo.writeObject(oOutput, buffer[i]);
                }
            }
            accessNum.addAndGet(nextPos);
            nextPos = 0;
        }

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
