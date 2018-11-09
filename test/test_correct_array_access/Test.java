
public class Test implements Runnable {
    
    public static int[] y = new int[10];
 
    @Override
    public void run() {
        y[0] = 2;
    }

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(new Test());
        t.start();
        System.out.println(y[5]);
        t.join();
    }
}
