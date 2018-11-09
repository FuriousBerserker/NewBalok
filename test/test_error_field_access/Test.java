
public class Test implements Runnable {
    
    public static int y;
    
    @Override
    public void run() {
        y = 2;
    }

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(new Test());
        t.start();
        System.out.println(y);
        t.join();
    }
}
