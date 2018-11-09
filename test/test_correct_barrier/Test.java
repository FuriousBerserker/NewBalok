import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class Test implements Runnable {

    public static int[] sharedArray;

    public static int sharedInteger;
    
    int index;

    CyclicBarrier barrier;

    public Test(int index, CyclicBarrier barrier) {
        this.index = index;
        this.barrier = barrier;
    }

    @Override
    public void run() {
        try {
            //sharedArray[0] = index;
            sharedArray[index] = index;
            barrier.await();
            sharedArray[(index * 2) % sharedArray.length] += index;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (BrokenBarrierException e) {
            e.printStackTrace();
        }
        
        try {
            //sharedArray[0] = index;
            barrier.await();
            sharedArray[(index * 2) % sharedArray.length] += index;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        sharedArray = new int[3];
        sharedInteger = 3;
        CyclicBarrier barrier = new CyclicBarrier(2);
        Thread t1 = new Thread(new Test(1, barrier));
        Thread t2 = new Thread(new Test(2, barrier));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        for (int i = 0; i < sharedArray.length; i++) {
            System.out.println(sharedArray[i]);
        }
        System.out.println(sharedInteger);
    }
}
