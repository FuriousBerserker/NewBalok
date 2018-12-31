package tools.balok;

import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.async.AsyncLocationTracker;
import balok.causality.async.DataRacePolicy;
import rr.tool.RR;

public class AsyncShadowLocation implements BalokShadowLocation {
    // When we create an async location tracker, we need to declare what to do in case of finding a data-race
    AsyncLocationTracker<Epoch> loc = new AsyncLocationTracker<>((mode1, ev1, mode2, ev2) -> {
        // We log any data race
        System.out.println("Race Detected!");
        if (RR.unitTestOption.get()) {
            if (!(ev1 instanceof TaskViewForDebug)) {
                throw new RuntimeException("ev1 should be an instance of TaskViewForDebug"); 
            }
            
            if (!(ev2 instanceof EpochForDebug)) {
                throw new RuntimeException("ev2 should be an instance of EpochForDebug"); 
            }
            System.out.println("Access 1: " + mode1 + " " + ((TaskViewForDebug)ev1).getLoc());
            System.out.println("Access 2: " + mode2 + " " + ((EpochForDebug)ev2).getLoc());
        } else {
            System.out.println("Access 1: " + mode1 + " " + ev1);
            System.out.println("Access 2: " + mode2 + " " + ev2);
        }
        // We say to ADD in case of a write so that that access is written to the shadow location
        // We say to DISCARD in the case of a read so that the operation can continue (ignoring reads) if a data-race
        // occurs.
        return mode1 == AccessMode.WRITE ? DataRacePolicy.ADD : DataRacePolicy.DISCARD;
    });
}
