package tools.balok;

import balok.causality.Epoch;
import balok.causality.MemoryAccess;
import balok.causality.async.AsyncLocationTracker;
import rr.state.ShadowVar;

public class AsyncShadowLocation implements BalokShadowLocation {
    AsyncLocationTracker<MemoryAccess, Epoch> loc = new AsyncLocationTracker<>();
}
