package test;

class Child implements Runnable {
    
    @Override
    public void run() {
        Correct.data += 1;
    }
}

public class Correct {
    
    static int data = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("original value of data: " + data);
        Thread t1 = new Thread(new Child());
        t1.start();
        t1.join();
        Thread t2 = new Thread(new Child());
        t2.start();
        t2.join();
        System.out.println("final value of data: " + data);
    }
}
