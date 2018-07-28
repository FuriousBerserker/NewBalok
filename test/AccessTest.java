package test;

public class AccessTest extends Thread {
   
   static class Data {
        public int id;
        public int val;

        public Data(int id, int val) {
            this.id = id;
            this.val = val;
        }
   };

   private int[] array;

   public static int s;

   private Data obj;

   public static final int ITER = 100;


   public AccessTest(int[] array, Data obj) {
        this.array = array;
        this.obj = obj;
   }

   @Override
   public void run() {
        for (int i = 0; i < ITER; i++) {
            array[i] = i;
        }
        s++;
        obj.id = ITER;
        obj.val = ITER;
   }

   public static void main(String[] args) throws Exception {
        int[] a = new int[ITER];
        Data d = new Data(1, 2);
        Thread t1 = new AccessTest(a, d);
        Thread t2 = new AccessTest(a, d);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("Completed!");
   }
}
