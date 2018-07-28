package tools.balok;

import balok.causality.AccessEntry;
import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.Event;
import balok.causality.async.ShadowMemory;
import balok.causality.async.ShadowMemoryBuilder;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.meta.SourceLocation;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncMemoryTracker implements MemoryTracker {
    private static final AtomicInteger codeGen = new AtomicInteger(Integer.MIN_VALUE);
    private final int code = codeGen.getAndIncrement();
    private ShadowMemoryBuilder<MemoryAccess, Epoch> currentFrame = new ShadowMemoryBuilder<>(512);
    private AtomicBoolean active = new AtomicBoolean(true);

    //TODO: Import JCTools
    private final MpscUnboundedArrayQueue<ShadowMemory> queue;

    public AsyncMemoryTracker(MpscUnboundedArrayQueue<ShadowMemory> queue) {
        this.queue = queue;
    }

    @Override
    public ShadowLocation createLocation() {
        return new AsyncShadowLocation();
    }

    @Override
    public void onAccess(TaskTracker tracker, ShadowLocation loc, AccessMode mode, SourceLocation info) {
        // XXX: ADD DEBUGGING INFO
        AsyncShadowLocation key = (AsyncShadowLocation) loc;
        AccessEntry<MemoryAccess, Event<Epoch>> acc = new AccessEntry<>(new MemoryAccess(mode, info), tracker.createTimestamp());
        int ticket = key.loc.createTicket();
        System.out.println(ticket);
        if (!key.loc.tryAdd(acc, ticket)) {
            currentFrame.add(key.loc, acc, ticket);
            if (currentFrame.isFull()) {
                queue.add(currentFrame.build());
            }
        }
    }

    @Override
    public void onSyncEvent(TaskTracker tracker) {
        //push(tracker);
    }

    @Override
    public void onEnd(TaskTracker tracker) {
        ShadowMemory frame = currentFrame.isEmpty() ? null : currentFrame.build();
        if (frame != null) {
            queue.add(frame);
        }
        active.set(false);
    }

    public ArrayList<ShadowMemory<MemoryAccess, Epoch>> consume() {
        ArrayList<ShadowMemory<MemoryAccess, Epoch>> result = new ArrayList<>();
        queue.drain(result::add);
        return result;
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
