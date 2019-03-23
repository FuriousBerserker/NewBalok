package tools.balok;

import java.util.concurrent.atomic.AtomicInteger;

public class TicketGenerator implements BalokShadowLocation {

    private static final AtomicInteger hashCodeGen = new AtomicInteger(-2147483648);

    private AtomicInteger ticketGen = new AtomicInteger(-2147483648);

    private int hashCode = hashCodeGen.getAndIncrement();

    public int getTicket() {
        return ticketGen.getAndIncrement();
    }

    public int getHashCode() {
        return this.hashCode;
    }
}
