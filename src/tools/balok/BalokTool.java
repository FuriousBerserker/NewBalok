package tools.balok;

import acme.util.Assert;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import acme.util.option.CommandLine;
import balok.causality.*;
import balok.causality.async.ShadowMemory;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.annotations.Abbrev;
import rr.event.*;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.state.ShadowVolatile;
import rr.tool.Tool;

import java.util.function.Supplier;

@Abbrev("Balok")
public class BalokTool extends Tool {

    //TODO: Currently we just hardcode the memFactory. Later we will get it from program properties
    private MpscUnboundedArrayQueue<ShadowMemory> queue = new MpscUnboundedArrayQueue<>(128);

    private final Supplier<MemoryTracker> memFactory = () -> new AsyncMemoryTracker(queue);

    private final PtpCausalityFactory vcFactory = PtpCausalityFactory.VECTOR_MUT;

    private final Decoration<ShadowLock, BalokLockState> lockVs = ShadowLock.makeDecoration("Balok:ShadowLock",
            DecorationFactory.Type.MULTIPLE, new DefaultValue<ShadowLock, BalokLockState>() {
                @Override
                public BalokLockState get(ShadowLock shadowLock) {
                    return new BalokLockState(vcFactory.createController());
                }
            });

    private final Decoration<ShadowVolatile, BalokVolatileState> volatileVs = ShadowVolatile.makeDecoration("Balok:ShadowVolatile",
            DecorationFactory.Type.MULTIPLE, new DefaultValue<ShadowVolatile, BalokVolatileState>() {
                @Override
                public BalokVolatileState get(ShadowVolatile shadowVolatile) {
                    return new BalokVolatileState(vcFactory.createController());
                }
            });

    public BalokTool(String name, Tool next, CommandLine commandLine) {
        super(name, next, commandLine);
    }

    protected static MemoryTracker ts_get_memTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_memTracker(ShadowThread st, MemoryTracker mt) { Assert.panic("Bad");  }

    protected static TaskTracker ts_get_taskTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_taskTracker(ShadowThread st, TaskTracker tt) { Assert.panic("Bad");  }

    private OffloadRaceDetection offload = new OffloadRaceDetection();

    private Thread raceDetectionThread = new Thread(offload);
    //TODO: getResource, ShodowMemoryBuilder, tick
    //TODO: VectorEpoch.join, wait/notify, should we do a FT3
    // wait/notify acquire/release barrier
    // compare async / sync, FlatController
    class OffloadRaceDetection implements Runnable {

        private ShadowMemory<MemoryAccess, Epoch> history;

        private boolean isEnd;

        public OffloadRaceDetection() {
            history = new ShadowMemory<>();
            isEnd = false;
        }

        public void end() {
            isEnd = true;
        }

        @Override
        public void run() {
            while (!isEnd) {
                if (!queue.isEmpty()) {
                    queue.drain(history::addAll);
                }
            }
            // guarantee all data is analyzed
            if (!queue.isEmpty()) {
                queue.drain(history::addAll);
            }
        }
    }

    @Override
    public void init() {
        System.out.println("Balok start");
        raceDetectionThread.start();
        super.init();
    }

    @Override
    public void fini() {
        offload.end();
        try {
            raceDetectionThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Balok end");
        super.fini();
    }

    //Need to initialize the taskTracker and memoryTracker for main thread, since there doeen't exist associated preStart method for main thread
    //Override create rather than preStart
    @Override
    public void create(NewThreadEvent ne) {
        //Initialize TaskManager and MemoryManager
        ShadowThread currentST = ne.getThread();
        ShadowThread parentST = ne.getThread().getParent();
        TaskTracker childTask = null;
        MemoryTracker childMem = null;
        if (parentST != null) {
            TaskTracker parentTask = ts_get_taskTracker(parentST);
            childTask = parentTask.createChild();
            //System.out.println(parentTask.createTimestamp());
            parentTask.afterSpawn();
            //System.out.println(parentTask.createTimestamp());
        } else {
            //main thread
            //TODO: need to hard code the implementation of ClockController
            childTask = new TaskTracker(vcFactory.createController());
            // increase the timestamp of the initial thread
            //childTask.produceEvent();
        }
        childMem = memFactory.get();
        ts_set_taskTracker(currentST, childTask);
        ts_set_memTracker(currentST, childMem);
        //Keep this hook for experiment of offload
        childMem.onSyncEvent(childTask);
        super.create(ne);
    }

    @Override
    public void stop(ShadowThread td) {
        TaskTracker task = ts_get_taskTracker(td);
        MemoryTracker mem = ts_get_memTracker(td);
        mem.onEnd(task);
        super.stop(td);
    }

    @Override
    public void postJoin(JoinEvent je) {
        TaskTracker task = ts_get_taskTracker(je.getThread());
        TaskTracker otherTask = ts_get_taskTracker(je.getJoiningThread());
        MemoryTracker mem = ts_get_memTracker(je.getThread());
        task.join(otherTask.createTimestamp());
        mem.onSyncEvent(task);
        super.postJoin(je);
    }

    @Override
    public void acquire(final AcquireEvent ae) {
        final TaskTracker task = ts_get_taskTracker(ae.getThread());
        final BalokLockState lockV = lockVs.get(ae.getLock());
        task.join(new TaskView(0, lockV.getV().createView(), null, null));
        super.acquire(ae);

    }

    @Override
    public void release(final ReleaseEvent re) {
        final TaskTracker task = ts_get_taskTracker(re.getThread());
        final BalokLockState lockV = lockVs.get(re.getLock());
        lockV.setV(lockV.getV().join(task.createTimestamp()));
        task.produceEvent();
        super.release(re);
    }

    @Override
    public void preWait(WaitEvent we) {
        // the thread will release the acquired lock before waiting
        final TaskTracker task = ts_get_taskTracker(we.getThread());
        final BalokLockState lockV = lockVs.get(we.getLock());
        //TODO: Could we apply the same optimization as FastTrack
        lockV.setV(lockV.getV().join(task.createTimestamp()));
        task.produceEvent();
        super.preWait(we);
    }

    @Override
    public void postWait(WaitEvent we) {
        // the thread will acquire the released lock before resuming execution
        final TaskTracker task = ts_get_taskTracker(we.getThread());
        final BalokLockState lockV = lockVs.get(we.getLock());
        task.join(new TaskView(0, lockV.getV().createView(), null, null));
        super.postWait(we);
    }

    @Override
    public void access(AccessEvent fae) {
        if (fae.getOriginalShadow() instanceof BalokShadowLocation) {
            //TODO: Figure out the usage of getShadow
            BalokShadowLocation shadow = (BalokShadowLocation)fae.getOriginalShadow();
            TaskTracker task = ts_get_taskTracker(fae.getThread());
            MemoryTracker mem = ts_get_memTracker(fae.getThread());
            //TODO: Need to convert the type of debug info between RoadRunner and Balok
            mem.onAccess(task, shadow, fae.isWrite() ? AccessMode.WRITE : AccessMode.READ, fae.getAccessInfo().getLoc());
        } else {
            super.access(fae);
        }
    }

    @Override
    public void volatileAccess(final VolatileAccessEvent vae) {
        // write to a volatile happens before subsequent read
        //TODO: is volatile write ordered ?
        final TaskTracker task = ts_get_taskTracker(vae.getThread());
        final BalokVolatileState volatileV = volatileVs.get(vae.getShadowVolatile());
        if (vae.isWrite()) {
            volatileV.setV(volatileV.getV().join(task.createTimestamp()));
            task.produceEvent();
        } else {
            task.join(new TaskView(0, volatileV.getV().createView(), null, null));
        }
        super.volatileAccess(vae);
    }


    public static boolean readFastPath(final ShadowVar shadow, final ShadowThread st) {
        return false;
    }

    public static boolean writeFastPath(final ShadowVar shadow, final ShadowThread st) {
        return false;
    }

    @Override
    public ShadowVar makeShadowVar(final AccessEvent event) {
        return new AsyncShadowLocation();
    }

}
