package tools.fasttrack_frontend;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class FTSerializedStateSerializer extends Serializer<FTSerializedState> {
    @Override
    public void write(Kryo kryo, Output output, FTSerializedState state) {
        int[] event = state.getEvent();
        output.writeInt(state.getAddress());
        output.writeBoolean(state.isWrite());
        output.writeInt(event.length);
        output.writeInts(event);
        output.writeInt(state.getTicket());
        output.writeInt(state.getTid());
    }

    @Override
    public FTSerializedState read(Kryo kryo, Input input, Class<FTSerializedState> aClass) {
        int address = input.readInt();
        boolean isWrite = input.readBoolean();
        int eventSize = input.readInt();
        int[] event = input.readInts(eventSize);
        int ticket = input.readInt();
        int tid = input.readInt();
        return new FTSerializedState(address, isWrite, event, ticket, tid);
    }
}
