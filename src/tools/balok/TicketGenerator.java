package tools.balok;

import sun.misc.Unsafe;

import java.util.concurrent.atomic.AtomicInteger;

public class TicketGenerator implements BalokShadowLocation {

    public static final int TICKET_START = -2147483648;

    private static final AtomicInteger hashCodeGen = new AtomicInteger(-2147483648);

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long valueOffset;

    //private AtomicInteger ticketGen = new AtomicInteger(-2147483648);

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                    (TicketGenerator.class.getDeclaredField("ticketGen"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private final int hashCode = hashCodeGen.getAndIncrement();

    private volatile int ticketGen = TICKET_START + 1;

    public int getTicket() {
        return unsafe.getAndAddInt(this, valueOffset, 1);
    }

    public int getHashCode() {
        return this.hashCode;
    }

//    @Override
//    protected void finalize() {
//
//    }
}
