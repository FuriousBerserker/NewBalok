package tools.balok;

import acme.util.Assert;
import acme.util.option.CommandLine;
import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.PtpCausalityFactory;
import balok.causality.async.ShadowMemory;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.annotations.Abbrev;
import rr.barrier.BarrierEvent;
import rr.barrier.BarrierListener;
import rr.event.AccessEvent;
import rr.event.JoinEvent;
import rr.event.NewThreadEvent;
import rr.event.StartEvent;
import rr.state.ShadowThread;
import rr.state.ShadowVar;
import rr.tool.Tool;
import tools.fasttrack.FTBarrierState;

import java.util.function.Supplier;

@Abbrev("Balok")
public class BalokTool extends Tool implements BarrierListener<FTBarrierState> {

    //TODO: Currently we just hardcode the memFactory. Later we will get it from program properties
    private MpscUnboundedArrayQueue<ShadowMemory> queue = new MpscUnboundedArrayQueue<>(128);
    private final Supplier<MemoryTracker> memFactory = () -> new AsyncMemoryTracker(queue);

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
            parentTask.afterSpawn();
        } else {
            //main thread
            //TODO: need to hard code the implementation of ClockController
            childTask = new TaskTracker(PtpCausalityFactory.PREFIX.createController());
            // increase the timestamp of the initial thread
            childTask.produceEvent();
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
    public void access(AccessEvent fae) {
        if (fae.getOriginalShadow() instanceof ShadowLocation) {
            //TODO: Figure out the usage of getShadow
            TaskTracker task = ts_get_taskTracker(fae.getThread());
            MemoryTracker mem = ts_get_memTracker(fae.getThread());
            //TODO: Need to convert the type of debug info between RoadRunner and Balok
            mem.onAccess(task, (ShadowLocation)fae.getOriginalShadow(), fae.isWrite() ? AccessMode.WRITE : AccessMode.READ, fae.getAccessInfo().getLoc());
        } else {
            super.access(fae);
        }
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

    @Override
    public void preDoBarrier(BarrierEvent<FTBarrierState> be) {

    }

    @Override
    public void postDoBarrier(BarrierEvent<FTBarrierState> be) {

    }
}
