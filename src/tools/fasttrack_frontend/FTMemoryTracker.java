package tools.fasttrack_frontend;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

public class FTMemoryTracker {

    private Kryo kryo;

    private File folderForLogs;

    private Output oOutput;

    private AtomicLong accessNum;

    private static int CACHE_SIZE = 100000;

    private HashSet<Integer> reads;

    private int[] readsOrder;

    private int tail;

    private int head;

    public FTMemoryTracker(Kryo kryo, File folderForLogs, AtomicLong accessNum, final int tid) {
        this.kryo = kryo;
        this.folderForLogs = folderForLogs;
        this.accessNum = accessNum;
        try {
            File threadLocalLog = new File(folderForLogs, tid + ".log");
            //oOutput = new Output(new GZIPOutputStream(new FileOutputStream(threadLocalLog)));
            oOutput = new Output(new LZ4BlockOutputStream(new FileOutputStream(threadLocalLog)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } /*catch (IOException e) {
            e.printStackTrace();
        }*/

        this.reads = new HashSet();
        this.readsOrder = new int[CACHE_SIZE + 1];
        this.tail = 0;
        this.head = 0;
    }

    public void onAccess(int address, boolean isWrite, int[] event, int ticket) {
        if (!isWrite) {
            if (containsRead(address)) {
                return;
            } else {
                cacheRead(address);
            }
        }
        int[] copy = Arrays.copyOf(event, event.length);
        FTSerializedState state = new FTSerializedState(address, isWrite, copy, ticket);
        kryo.writeObject(oOutput, state);
        accessNum.getAndIncrement();
    }

    public void onLastExclusiveAccess(int address, boolean isWrite, int epoch, int ticket, int tid) {
        int[] event = new int[tid + 1];
        event[tid] = epoch;
        FTSerializedState state = new FTSerializedState(address, isWrite, event, ticket);
        kryo.writeObject(oOutput, state);
        accessNum.getAndIncrement();
    }

    public void onEnd() {
        oOutput.close();
    }

    public void onSync() {
        clearCache();
    }

    public boolean containsRead(int objID) {
        return this.reads.contains(objID);
    }

    public void cacheRead(int objID) {
        if (this.isCacheFull()) {
            this.reads.remove(this.readsOrder[this.tail]);
            this.tail = (this.tail + 1) % (CACHE_SIZE + 1);
        }

        this.reads.add(objID);
        this.readsOrder[this.head] = objID;
        this.head = (this.head + 1) % (CACHE_SIZE + 1);
    }

    private void clearCache() {
        this.reads.clear();
        this.tail = 0;
        this.head = 0;
    }

    private boolean isCacheFull() {
        return (this.head + 1) % (CACHE_SIZE + 1) == this.tail;
    }

    private boolean isCacheEmpty() {
        return this.head == this.tail;
    }

    public static void setCacheSize(int size) {
        CACHE_SIZE = size;
    }


}
