class Data {
    public int value;
}

public class Test implements Runnable {
    
    private Data data;

    public Test(Data data) {
        this.data = data;
    }

    public void inc() {
        data.value += 1;
    }

    @Override
    public void run() {
        inc();
    }

    public static void main(String[] args) throws Exception {
        Data d = new Data();
        Thread t1 = new Thread(new Test(d));
        t1.start();
        t1.join();
        Thread t2 = new Thread(new Test(d));
        t2.start();
        t2.join();
        System.out.println(d.value);
    }
}
