package tools.balok;

import balok.causality.*;
import balok.causality.async.Frame;
import balok.causality.async.FrameBuilder;
import balok.causality.async.ShadowMemory;
import balok.causality.async.ShadowMemoryBuilder;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.meta.SourceLocation;
import rr.tool.RR;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AsyncMemoryTracker implements MemoryTracker {
    private static final AtomicInteger codeGen = new AtomicInteger(Integer.MIN_VALUE);
    private final int code = codeGen.getAndIncrement();
    private FrameBuilder<Epoch> currentFrame = new FrameBuilder<>(512);
    private AtomicBoolean active = new AtomicBoolean(true);
    private AtomicInteger accessNum = new AtomicInteger(0);

    private final MpscUnboundedArrayQueue<Frame<Epoch>> queue;

    private ObjectOutputStream accessStream;

    public AsyncMemoryTracker(MpscUnboundedArrayQueue<Frame<Epoch>> queue) {
        this.queue = queue;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd-HHmmss").withZone(ZoneId.of("GMT-5"));
        // TODO: We need an initializer to decide the initialization of accessStream
        try {
            this.accessStream = new ObjectOutputStream(new FileOutputStream("access-" + formatter.format(Instant.now()) + ".log"));
        } catch (IOException e) {
            e.printStackTrace();
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
            accessNum.incrementAndGet();
            MemoryAccess ma = new MemoryAccess(mode, key.loc.hashCode(), threadID, ticket, vc, info.getFile(), info.getLine(), info.getOffset());
            synchronized (accessStream) {
                try {
                    accessStream.writeObject(ma);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        active.set(false);
        if (RR.outputAccessOption.get()) {
            try {
                accessStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Total number of memory accesses: " + accessNum.get());
        }
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
