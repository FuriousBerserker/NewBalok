class Data {
    public int d;
}

public class Test extends Thread {
    
    private int x;

    public static Data data = new Data();

    @Override
    public void run() {
        try {
            synchronized(data) {
                x = 1;
                data.wait();
                System.out.println(x);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Test t = new Test();
        t.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized(data) {
            t.x = 2;
            data.notifyAll();
        }
        t.x = 3;
    }
}
