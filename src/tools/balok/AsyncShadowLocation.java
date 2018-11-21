package tools.balok;

import balok.causality.AccessMode;
import balok.causality.Epoch;
import balok.causality.MemoryAccess;
import balok.causality.async.AsyncLocationTracker;
import balok.causality.async.DataRacePolicy;
import rr.state.ShadowVar;

public class AsyncShadowLocation implements BalokShadowLocation {
    // When we create an async location tracker, we need to declare what to do in case of finding a data-race
    AsyncLocationTracker<Epoch> loc = new AsyncLocationTracker<>((mode, event, conflict) -> {
        // We log any data race
        System.out.println("Race Detected!");
        System.out.println("Access 1: " + conflict.getAccess() + " " + conflict.getValue());
        System.out.println("Access 2: " + mode + " " + event);
        // We say to ADD in case of a write so that that access is written to the shadow location
        // We say to DISCARD in the case of a read so that the operation can continue (ignoring reads) if a data-race
        // occurs.
        return mode == AccessMode.WRITE ? DataRacePolicy.ADD : DataRacePolicy.DISCARD;
    });
}
