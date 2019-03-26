package tools.balok;

import acme.util.Assert;
import acme.util.Util;
import acme.util.decorations.Decoration;
import acme.util.decorations.DecorationFactory;
import acme.util.decorations.DefaultValue;
import acme.util.option.CommandLine;
import balok.causality.AccessMode;
import balok.causality.PtpCausalityFactory;
import balok.causality.TaskTracker;
import balok.ser.SerializedFrame;
import com.esotericsoftware.kryo.Kryo;
import javafx.concurrent.Task;
import rr.annotations.Abbrev;
import rr.barrier.BarrierEvent;
import rr.barrier.BarrierListener;
import rr.barrier.BarrierMonitor;
import rr.error.ErrorMessage;
import rr.error.ErrorMessages;
import rr.event.*;
import rr.event.AccessEvent.Kind;
import rr.meta.ArrayAccessInfo;
import rr.meta.FieldInfo;
import rr.state.ShadowLock;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.state.ShadowVolatile;
import rr.tool.RR;
import rr.tool.Tool;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// finished: getResource, ShodowMemoryBuilder, tick
// finished: VectorEpoch.join, wait/notify, should we do a FT3
// finished: wait/notify acquire/release barrier
// TODO: compare async / sync, FlatController
@Abbrev("BalokFE")
public class BalokFrontEndTool extends Tool implements BarrierListener<BalokBarrierState> {

    private HashSet<Integer> tids = new HashSet<>();

    //private Phaser phaser = new Phaser(1);

    private final PtpCausalityFactory vcFactory = PtpCausalityFactory.VECTOR_MUT;

    private AtomicLong accessNum = new AtomicLong();

    private Kryo kryo = new Kryo();

    private File folder;

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

    public BalokFrontEndTool(String name, Tool next, CommandLine commandLine) {
        super(name, next, commandLine);
        new BarrierMonitor<BalokBarrierState>(this, new DefaultValue<Object, BalokBarrierState>() {
            @Override
            public BalokBarrierState get(Object o) {
                BalokBarrierState barrierState = null;
                barrierState = new BalokBarrierState(o);
                return barrierState;
            }
        });
    }

    protected static MemoryTracker ts_get_memTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_memTracker(ShadowThread st, MemoryTracker mt) { Assert.panic("Bad");  }

    protected static TaskTracker ts_get_taskTracker(ShadowThread st) { Assert.panic("Bad");	return null; }
    protected static void ts_set_taskTracker(ShadowThread st, TaskTracker tt) { Assert.panic("Bad");  }

    @Override
    public void preDoBarrier(BarrierEvent<BalokBarrierState> be) {
        //System.out.println("preDoBarrier, barrier ID is " + be.getBarrier().getBarrierId());
        final ShadowThread st = be.getThread();
        final TaskTracker task = ts_get_taskTracker(st);
        final BalokBarrierState barrierState = be.getBarrier();
        task.beforeBarrier(barrierState);
    }

    @Override
    public void postDoBarrier(BarrierEvent<BalokBarrierState> be) {
        //System.out.println("postDoBarrier, barrier ID is " + be.getBarrier().getBarrierId());
        final ShadowThread st = be.getThread();
        final TaskTracker task = ts_get_taskTracker(st);
        final BalokBarrierState barrierState = be.getBarrier();
        task.afterBarrier(barrierState);
        //System.out.println(task);
    }

    @Override
    public void preStart(StartEvent se) {
        //phaser.register();
    }

    @Override
    public void init() {
        Util.println("Balok frontend start");
        Util.println("Output memory accesses for backend race detection");
        kryo.setReferences(false);
        kryo.setRegistrationRequired(true);
        kryo.register(SerializedFrame.class, new FrameSerializer());
        String folderName = null;
        if (RR.folderOption.get() != null) {
            folderName = RR.folderOption.get();
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd-HHmmss").withZone(ZoneId.of("GMT-5"));
            folderName = "access-" + formatter.format(Instant.now()) + ".log";
        }
        folder = new File(folderName);
        if (!prepareFolder(folder)) {
            Util.println("Unable to create / clear folder \"" + folder.getAbsolutePath() + "\"");
            System.exit(1);
        }
        super.init();
    }

    @Override
    public void fini() {
        //phaser.arriveAndAwaitAdvance();
//        while (RRMain.numRunningThreads() != 0) {
//
//        }
        for (int tid : tids) {
            Util.println("Stop TaskTracker and MemoryTracker for thread " + tid + " before exit");
            ShadowThread st = ShadowThread.get(tid);
            TaskTracker task = ts_get_taskTracker(st);
            MemoryTracker mem = ts_get_memTracker(st);
            mem.onEnd(task);
        }
        Util.println("Number of memory accesses: " + accessNum.get());
        Util.println("Balok frontend end");
        super.fini();
    }

    // Need to initialize the taskTracker and memoryTracker for every thread
    // Since roadrunner doesn't call preStart method for the initial thread, we override create() rather than preStart()
    @Override
    public void create(NewThreadEvent ne) {
        // Initialize taskTracker and memoryTracker
        final ShadowThread currentST = ne.getThread();
        final ShadowThread parentST = ne.getThread().getParent();
        TaskTracker childTask = null;
        MemoryTracker childMem = null;
        if (parentST != null) {
            TaskTracker parentTask = ts_get_taskTracker(parentST);
            childTask = parentTask.createChild(currentST.getTid());
            parentTask.afterSpawn();
        } else {
            // initial thread
            childTask = new TaskTracker(currentST.getTid(), vcFactory.createController(ne.getThread().getTid()));
            // When creating the instance of TaskTracker for the initial thread,
            // the timestamp starts from 1, so here we don't need to increase it again
            //childTask.produceEvent();
        }
        childMem = new OutputAccessMemoryTracker(kryo, folder, accessNum, currentST.getTid());
        ts_set_taskTracker(currentST, childTask);
        ts_set_memTracker(currentST, childMem);
        // Keep this hook for SyncMemoryChecker
        childMem.onSyncEvent(childTask);
        tids.add(currentST.getTid());
        super.create(ne);
    }

    @Override
    public void stop(ShadowThread td) {
        final TaskTracker task = ts_get_taskTracker(td);
        final MemoryTracker mem = ts_get_memTracker(td);
        //System.out.println("[debug] thread " + td.getTid() + " is end");
        mem.onEnd(task);
        //phaser.arriveAndDeregister();
        //TODO: shall we set task and mem to null to help garbage collection?
        tids.remove(td.getTid());
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
        if (ae.getOriginalShadow() instanceof ExclusiveState) {
            ExclusiveState es = (ExclusiveState)ae.getOriginalShadow();
            ShadowThread st = ae.getThread();
            TaskTracker task = ts_get_taskTracker(st);
            MemoryTracker mem = ts_get_memTracker(st);
            if (es.isExclusive(st.getTid())) {
                // exclusive access
                ExclusiveState newEs = new ExclusiveState(task.createTimestamp(), ae.isWrite() ? AccessMode.WRITE : AccessMode.READ, st.getTid());
                if (!ae.putShadow(newEs)) {
                    // fail to update Shadow, not exclusive anymore
                    TicketGenerator tg = (TicketGenerator) ae.getOriginalShadow();
                    mem.onAccess(task, tg, ae.isWrite() ? AccessMode.WRITE : AccessMode.READ,
                            ae.getAccessInfo().getLoc(), st.getTid());
                }
            } else {
                // not exclusive access
                TicketGenerator newTg = new TicketGenerator();
                boolean isSuccess = false;
                boolean isLoop = false;
                ShadowVar oldShadow = null;
                do {
                    oldShadow = ae.getOriginalShadow();
                    if (oldShadow instanceof ExclusiveState) {
                        isSuccess = ae.putShadow(newTg);
                        isLoop = !isSuccess;
                    } else {
                        isLoop = false;
                    }
                } while (isLoop);

                if (isSuccess) {
                    // putShadow() will not update originalShadow when the update succeeds
                    ae.putOriginalShadow(newTg);
                    mem.onLastExclusiveAccess((BalokShadowLocation) oldShadow, newTg);
                }

                mem.onAccess(task, (BalokShadowLocation) ae.getOriginalShadow(), ae.isWrite() ? AccessMode.WRITE : AccessMode.READ,
                        ae.getAccessInfo().getLoc(), st.getTid());
            }
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
            //TODO: buggy code, each thread should only overwrite its own epoch
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
        if (shadow instanceof TicketGenerator) {
            TaskTracker task = ts_get_taskTracker(st);
            MemoryTracker mem = ts_get_memTracker(st);
            mem.onAccess(task, (BalokShadowLocation)shadow, AccessMode.READ, null, st.getTid());
            return true;
        } else {
            return false;
        }
    }

    public static boolean writeFastPath(final ShadowVar shadow, final ShadowThread st) {
        if (shadow instanceof TicketGenerator) {
            TaskTracker task = ts_get_taskTracker(st);
            MemoryTracker mem = ts_get_memTracker(st);
            mem.onAccess(task, (BalokShadowLocation)shadow, AccessMode.WRITE, null, st.getTid());
            return true;
        } else {
            return false;
        }
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
            // we treat initialization as a write
            ShadowThread st = event.getThread();
            TaskTracker task = ts_get_taskTracker(st);
            return new ExclusiveState(task.createTimestamp(), AccessMode.WRITE, st.getTid());
        }
    }

    private boolean prepareFolder(File f) {
        if (f.exists()) {
            if (!f.isDirectory() || !f.canWrite()) {
                return false;
            } else {
                for (File file : f.listFiles()) {
                    if (!file.delete()) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            return f.mkdir();
        }
    }

}
