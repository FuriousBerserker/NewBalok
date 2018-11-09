class Data {
    
    private int d;
    
    public int getD() {
        return d;
    }

    public void inc() {
        d += 1;
    }
    
}

public class Test extends Thread {
    
    static final int ITERS = 100;

    private Data data;

    public Test(Data data) {
        this.data = data;
    }

    @Override
    public void run() {
        for (int i = 0; i < ITERS; i++) {
            System.out.println(data.getD()); 
        }
    }

    public static void main(String args[]) throws Exception {
        Data data = new Data();
        data.inc();
        final Test t1 = new Test(data);
        final Test t2 = new Test(data);
        t1.start();
        // y++;
        t2.start();
        t1.join();
        t2.join();
        data.inc();
        System.out.println("Is it 2?");
    }
}
