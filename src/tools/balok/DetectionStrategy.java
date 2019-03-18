package tools.balok;

import acme.util.Util;
import balok.causality.Epoch;
import balok.causality.async.Frame;
import balok.causality.async.ShadowMemory;
import balok.ser.SerializedFrame;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import org.jctools.queues.MpscUnboundedArrayQueue;
import rr.tool.RR;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

public enum DetectionStrategy {

    NOCHECK {
        @Override
        public BalokShadowLocation createShadowLocation() {
            return NoCheckMemoryTracker.NO_CHECK;
        }

        @Override
        public MemoryTracker createMemoryTracker(final int tid) {
            return new NoCheckMemoryTracker();
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

    SYNC {
        @Override
        public BalokShadowLocation createShadowLocation() {
            return new SyncShadowLocation();
        }

        @Override
        public MemoryTracker createMemoryTracker(final int tid) {
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
        private MpscUnboundedArrayQueue<Frame<Epoch>> queue = new MpscUnboundedArrayQueue<>(128);
 
        private Offload offload = new Offload();

        private Thread raceDetectionThread = new Thread(offload);

        // private ConcurrentLinkedQueue<MemoryAccess> accesses = new ConcurrentLinkedQueue<>();

        private AtomicLong accessNum = new AtomicLong();

        private Kryo kryo;

        private File folder;

        @Override
        public BalokShadowLocation createShadowLocation() {
            return new AsyncShadowLocation();
        }

        @Override
        public MemoryTracker createMemoryTracker(final int tid) {
            return new AsyncMemoryTracker(queue, kryo, folder, accessNum, tid);
        }

        @Override
        public void init() {
            if (RR.outputAccessOption.get()) {
                kryo = new Kryo();
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
                    System.out.println("Unable to create / clear folder \"" + folder.getAbsolutePath() + "\"");
                    System.exit(1);
                }
            }
            offload.init();
            raceDetectionThread.setPriority(Thread.MAX_PRIORITY);
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
            if (RR.outputAccessOption.get()) {
                System.out.println("The number of memory accesses: " + accessNum.get());
            }
        }

        private boolean prepareFolder(File f) {
            if (f.exists()) {
                if (!f.isDirectory() || !f.canWrite()) {
                    return false;
                } else {
                    for (File file : f.listFiles()) {
                        file.delete();
                    }
                    return true;
                }
            } else {
                return f.mkdir();
            }
        }

        class Offload implements Runnable {

            private ShadowMemory<Epoch> history;

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
                        doRaceDetection();
                    }
                }
                // guarantee all data is analyzed
                if (!queue.isEmpty()) {
                    doRaceDetection();
                    pool.shutdown();
                }
            }

            public void doRaceDetection() {
                ArrayList<Frame<Epoch>> frames = new ArrayList<>();
                queue.drain(frames::add);
                int frameCount = frames.size();
                long start = System.currentTimeMillis();
                for (Frame<Epoch> frame : frames) {
                    frame.addTo(history);
                }
            }

            public void raceDetection(ShadowMemory<Epoch> other) {
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

    public abstract MemoryTracker createMemoryTracker(final int tid);

    public abstract void init();

    public abstract void fini();
}
