package tools.balok;

import acme.util.Assert;
import acme.util.Util;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import acme.util.option.CommandLine;

import balok.causality.*;
import balok.causality.async.ShadowMemory;

import org.jctools.queues.MpscUnboundedArrayQueue;

import rr.annotations.Abbrev;
import rr.barrier.BarrierEvent;
import rr.barrier.BarrierListener;
import rr.barrier.BarrierMonitor;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.state.ShadowVolatile;
import rr.tool.RR;
import rr.tool.Tool;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.VolatileAccessEvent;
import rr.event.AccessEvent.Kind;
import rr.event.NewThreadEvent;
import rr.event.JoinEvent;
import rr.event.AcquireEvent;
import rr.event.ReleaseEvent;
import rr.event.WaitEvent;
import rr.event.AccessEvent;
import rr.meta.ArrayAccessInfo;
import rr.meta.FieldInfo;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

// finished: getResource, ShodowMemoryBuilder, tick
// finished: VectorEpoch.join, wait/notify, should we do a FT3
// finished: wait/notify acquire/release barrier
// TODO: compare async / sync, FlatController
@Abbrev("Balok")
public class BalokTool extends Tool implements BarrierListener<BalokBarrierState> {

    // TODO: Currently we just hardcode the memFactory. Later we will get it from program properties

    private MpscUnboundedArrayQueue<ShadowMemory> queue = new MpscUnboundedArrayQueue<>(128);

    private final Supplier<MemoryTracker> memFactory = () -> new AsyncMemoryTracker(queue);

    private final PtpCausalityFactory vcFactory = PtpCausalityFactory.VECTOR_MUT;

    public final ErrorMessage<FieldInfo> fieldErrors = ErrorMessages.makeFieldErrorMessage("Balok");

    public final ErrorMessage<ArrayAccessInfo> arrayErrors = ErrorMessages.makeArrayErrorMessage("Balok");

    private final Decoration<ShadowLock, BalokLockState> lockVs = ShadowLock.makeDecoration("Balok:ShadowLock",
            DecorationFactory.Type.MULTIPLE, new DefaultValue<ShadowLock, BalokLockState>() {
                @Override
                public BalokLockState get(ShadowLock shadowLock) {
                    return new BalokLockState();
                }
            });

    private final Decoration<ShadowVolatile, BalokVolatileState> volatileVs = ShadowVolatile.makeDecoration("Balok:ShadowVolatile",
            DecorationFactory.Type.MULTIPLE, new DefaultValue<ShadowVolatile, BalokVolatileState>() {
                @Override
                public BalokVolatileState get(ShadowVolatile shadowVolatile) {
                    return new BalokVolatileState();
                }
            });

    public BalokTool(String name, Tool next, CommandLine commandLine) {
        super(name, next, commandLine);
        new BarrierMonitor<BalokBarrierState>(this, new DefaultValue<Object, BalokBarrierState>() {
            @Override
            public BalokBarrierState get(Object o) {
                return new BalokBarrierState(o);
            }
        });
    }

    protected static MemoryTracker ts_get_memTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_memTracker(ShadowThread st, MemoryTracker mt) { Assert.panic("Bad");  }

    protected static TaskTracker ts_get_taskTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_taskTracker(ShadowThread st, TaskTracker tt) { Assert.panic("Bad");  }

    private Offload offload = new Offload();

    private Thread raceDetectionThread = new Thread(offload);

    @Override
    public void preDoBarrier(BarrierEvent<BalokBarrierState> be) {
        //System.out.println("preDoBarrier, barrier ID is " + be.getBarrier().getBarrierID());
        final ShadowThread st = be.getThread();
        final TaskTracker task = ts_get_taskTracker(st);
        final BalokBarrierState barrierState = be.getBarrier();
        task.beforeBarrier(barrierState.getBarrierID());
    }

    @Override
    public void postDoBarrier(BarrierEvent<BalokBarrierState> be) {
        //System.out.println("postDoBarrier, barrier ID is " + be.getBarrier().getBarrierID());
        final ShadowThread st = be.getThread();
        final TaskTracker task = ts_get_taskTracker(st);
        final BalokBarrierState barrierState = be.getBarrier();
        task.afterBarrier(barrierState.getBarrierID());
        System.out.println(task);
    }

    class Offload implements Runnable {

        private ShadowMemory<MemoryAccess, Epoch> history;

        private ExecutorService pool;

        private boolean isEnd;

        public Offload() {
            history = new ShadowMemory<>();
            pool = null;
            isEnd = false;
        }

        // Initialize the thread pool in init to make sure command line options have been parsed
        public void init() {
            pool = Executors.newFixedThreadPool(RR.raceDetectThreadsOption.get());
            Util.println("number of dedicated race detection threads: " + RR.raceDetectThreadsOption.get());
        }

        public void end() {
            isEnd = true;
        }

        @Override
        public void run() {
            while (!isEnd) {
                if (!queue.isEmpty()) {
                    queue.drain(this::raceDetection);
                }
            }
            // guarantee all data is analyzed
            if (!queue.isEmpty()) {
                queue.drain(this::raceDetection);
            }
        }

        public void raceDetection(ShadowMemory<MemoryAccess, Epoch> other) {
            List< Callable<Object> > tasks = history.generateParallelAddTask(other);
            try {
                List< Future<Object> > futures = pool.invokeAll(tasks);
                for (Future future: futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                Util.error("Race detection is interrupted");
                e.printStackTrace();
            } catch (ExecutionException e) {
                Util.error("Race detection throws an internal exception");
                e.printStackTrace();
            }
            Util.println("Offload race detection is done, tackle " + tasks.size() + " locations in parallel");
        }
    }

    @Override
    public void init() {
        Util.println("Balok start");
        offload.init();
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
        Util.println("Balok end");
        super.fini();
    }

    // Need to initialize the taskTracker and memoryTracker for every thread
    // Since roadrunner doesn't call preStart method for the initial thread, we override create rather than preStart
    @Override
    public void create(NewThreadEvent ne) {
        // Initialize taskTracker and memoryTracker
        final ShadowThread currentST = ne.getThread();
        final ShadowThread parentST = ne.getThread().getParent();
        TaskTracker childTask = null;
        MemoryTracker childMem = null;
        if (parentST != null) {
            TaskTracker parentTask = ts_get_taskTracker(parentST);
            childTask = parentTask.createChild();
            parentTask.afterSpawn();
        } else {
            // initial thread
            childTask = new TaskTracker(vcFactory.createController());
            // When creating the instance of TaskTracker for the initial thread,
            // the timestamp starts from 1, so here we don't need to increase it again
            //childTask.produceEvent();
        }
        childMem = memFactory.get();
        ts_set_taskTracker(currentST, childTask);
        ts_set_memTracker(currentST, childMem);
        // Keep this hook for SyncMemoryChecker
        childMem.onSyncEvent(childTask);
        super.create(ne);
    }

    @Override
    public void stop(ShadowThread td) {
        final TaskTracker task = ts_get_taskTracker(td);
        final MemoryTracker mem = ts_get_memTracker(td);
        mem.onEnd(task);
        //TODO: shall we set task and mem to null to help garbage collection?
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
        if (lockV.getView() != null) {
            task.join(lockV.getView());
        }
        super.acquire(ae);

    }

    @Override
    public void release(final ReleaseEvent re) {
        final TaskTracker task = ts_get_taskTracker(re.getThread());
        final BalokLockState lockV = lockVs.get(re.getLock());
        lockV.setView(task.createTimestamp());
        task.produceEvent();
        super.release(re);
    }

    @Override
    public void preWait(WaitEvent we) {
        // the thread will release the acquired lock before waiting
        final TaskTracker task = ts_get_taskTracker(we.getThread());
        final BalokLockState lockV = lockVs.get(we.getLock());
        lockV.setView(task.createTimestamp());
        task.produceEvent();
        super.preWait(we);
    }

    @Override
    public void postWait(WaitEvent we) {
        // the thread will acquire the released lock before resuming execution
        final TaskTracker task = ts_get_taskTracker(we.getThread());
        final BalokLockState lockV = lockVs.get(we.getLock());
        if (lockV.getView() != null) {
            task.join(lockV.getView());
        }
        super.postWait(we);
    }

    @Override
    public void access(AccessEvent ae) {
        if (ae.getOriginalShadow() instanceof BalokShadowLocation) {
            //TODO: Figure out the usage of getShadow
            BalokShadowLocation shadow = (BalokShadowLocation)ae.getOriginalShadow();
            TaskTracker task = ts_get_taskTracker(ae.getThread());
            MemoryTracker mem = ts_get_memTracker(ae.getThread());
            //TODO: Need to convert the type of debug info between RoadRunner and Balok
            mem.onAccess(task, shadow, ae.isWrite() ? AccessMode.WRITE : AccessMode.READ,
                    ae.getAccessInfo().getLoc(), ae.getThread().getTid());
        } else {
            super.access(ae);
        }
    }

    @Override
    public void volatileAccess(final VolatileAccessEvent vae) {
        // write to a volatile happens before subsequent reads
        //TODO: is volatile write ordered ?
        final TaskTracker task = ts_get_taskTracker(vae.getThread());
        final BalokVolatileState volatileV = volatileVs.get(vae.getShadowVolatile());
        if (vae.isWrite()) {
                volatileV.setView(task.createTimestamp());
                task.produceEvent();
        } else {
            if (volatileV.getView() != null) {
                task.join(volatileV.getView());
            }
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
        if (event.getKind() == Kind.VOLATILE) {
            VolatileAccessEvent vae = (VolatileAccessEvent)event;
            final ShadowThread st = event.getThread();
            final TaskTracker task = ts_get_taskTracker(st);
            final BalokVolatileState volatileV = volatileVs.get(vae.getShadowVolatile());
            volatileV.setView(task.createTimestamp());
            //TODO: do we need to increase the epoch
            return super.makeShadowVar(event);
        } else {
            // TODO: shall we store the source info in shadow memory to reduce the memory usage
            return new AsyncShadowLocation();
        }
    }

}
