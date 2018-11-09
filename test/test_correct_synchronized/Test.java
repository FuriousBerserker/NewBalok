public class Test implements Runnable {

    private int[] array;

    public Test(int[] array) {
        this.array = array;
    }

    @Override
    public void run() {
        for (int i = 0; i < array.length; i++) {
            synchronized (array) {
                array[i] += i;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int[] sharedArray = new int[10];
        for (int i = 0; i < sharedArray.length; i++) {
            sharedArray[i] = i;
        }
        Thread t1 = new Thread(new Test(sharedArray));
        Thread t2 = new Thread(new Test(sharedArray));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        for (int i = 0; i < sharedArray.length; i++) {
            System.out.println(sharedArray[i]);
        }
    }
}
