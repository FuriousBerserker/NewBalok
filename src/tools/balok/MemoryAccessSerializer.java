package tools.balok;

import balok.causality.AccessMode;
import balok.causality.PtpCausalityFactory;
import balok.causality.TaskView;
import balok.causality.vc.VectorEvent;
import balok.ser.AllToAllOrderingSerializer;
import balok.ser.VectorEventSerializer;
import balok.vc.AllToAllOrdering;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;


public class MemoryAccessSerializer extends Serializer<MemoryAccess> {

    @Override
    public void write(Kryo kryo, Output output, MemoryAccess memoryAccess) {
        output.writeBoolean(memoryAccess.getMode() == AccessMode.READ);
        output.writeInt(memoryAccess.getAddress());
        output.writeInt(memoryAccess.getTid());
        output.writeInt(memoryAccess.getTicket());
        // VectorEvent
        VectorEventSerializer.writeVectorEvent(i -> {
            output.writeInt(i);
        }, (VectorEvent)(memoryAccess.getVC()).getLocal());
        // AllToAllOrdering
        AllToAllOrderingSerializer.writeOrdering(i -> {
            output.writeInt(i);
        }, memoryAccess.getVC().getCyclic());
        // location
        output.writeString(memoryAccess.getFile());
        output.writeInt(memoryAccess.getLine());
        output.writeInt(memoryAccess.getOffset());
    }

    @Override
    public MemoryAccess read(Kryo kryo, Input input, Class<MemoryAccess> aClass) {
        MemoryAccess memoryAccess = new MemoryAccess();
        memoryAccess.setMode(input.readBoolean() ? AccessMode.READ : AccessMode.WRITE);
        memoryAccess.setAddress(input.readInt());
        memoryAccess.setTid(input.readInt());
        memoryAccess.setTicket(input.readInt());
        // VectorEvent
        VectorEvent ve = VectorEventSerializer.readVectorEvent(PtpCausalityFactory.VECTOR_MUT, () -> {
             return input.readInt();
        });
        // AllToAllOrdering
        AllToAllOrdering ordering = AllToAllOrderingSerializer.readOrdering(() -> {
            return input.readInt();
        });
        memoryAccess.setVC(new TaskView(ve, ordering));
        // location
        memoryAccess.setFile(input.readString());
        memoryAccess.setLine(input.readInt());
        memoryAccess.setOffset(input.readInt());
        return memoryAccess;
    }
}
