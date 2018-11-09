package tools.balok;

import acme.util.Util;
import balok.causality.Epoch;
import balok.causality.async.ShadowMemory;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.tool.RR;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public enum DetectionStrategy {

    SYNC {
        @Override
        public BalokShadowLocation createShadowLocation() {
            return new SyncShadowLocation();
        }

        @Override
        public MemoryTracker createMemoryTracker() {
            return new SyncMemoryTracker();
        }

        @Override
        public void init() {
            // nothing to do
        }

        @Override
        public void fini() {
            // nothing to do
        }
    },

    ASYNC {
        private MpscUnboundedArrayQueue<ShadowMemory> queue = new MpscUnboundedArrayQueue<>(128);

        private Offload offload = new Offload();

        private Thread raceDetectionThread = new Thread(offload);
        @Override
        public BalokShadowLocation createShadowLocation() {
            return new AsyncShadowLocation();
        }

        @Override
        public MemoryTracker createMemoryTracker() {
            return new AsyncMemoryTracker(queue);
        }

        @Override
        public void init() {
            offload.init();
            raceDetectionThread.start();
        }

        @Override
        public void fini() {
            offload.end();
            try {
                raceDetectionThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        class Offload implements Runnable {

            private ShadowMemory<MemoryAccess, Epoch> history;

            private ExecutorService pool;

            private final AtomicBoolean isEnd;

            public Offload() {
                history = new ShadowMemory<>();
                pool = null;
                isEnd = new AtomicBoolean(false);

            }

            // Initialize the thread pool in init to make sure command line options have been parsed
            public void init() {
                pool = Executors.newFixedThreadPool(RR.raceDetectThreadsOption.get());
                Util.println("number of dedicated race detection threads: " + RR.raceDetectThreadsOption.get());
            }

            public void end() {
                isEnd.set(true);
            }

            @Override
            public void run() {
                while (!isEnd.get()) {
                    if (!queue.isEmpty()) {
                        queue.drain(this::raceDetection);
                    }
                }
                // guarantee all data is analyzed
                if (!queue.isEmpty()) {
                    queue.drain(this::raceDetection);
                    pool.shutdown();
                }
            }

            public void raceDetection(ShadowMemory<MemoryAccess, Epoch> other) {
                List< Callable<Object> > tasks = history.generateParallelAddTask(other);
                try {
                    List<Future<Object>> futures = pool.invokeAll(tasks);
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
    };
    public abstract BalokShadowLocation createShadowLocation();

    public abstract MemoryTracker createMemoryTracker();

    public abstract void init();

    public abstract void fini();
}
