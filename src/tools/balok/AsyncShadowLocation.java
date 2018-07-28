package tools.balok;

import balok.causality.Epoch;
import balok.causality.async.AsyncLocationTracker;
import rr.state.ShadowVar;

public class AsyncShadowLocation implements ShadowLocation, ShadowVar {
    AsyncLocationTracker<MemoryAccess, Epoch> loc = new AsyncLocationTracker<>();
}
