package fr.upem.net.chatos.reader;
import java.nio.ByteBuffer;

public class ShortReader implements Reader<Short> {

    private enum State {
    	DONE,
    	WAITING,
    	ERROR
    };

    private State            state      = State.WAITING;
    private final ByteBuffer internalbb = ByteBuffer.allocate(Short.BYTES); // write-mode
   
    private short value;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
        bb.flip();
        try {
            if (bb.remaining()<=internalbb.remaining()){
                internalbb.put(bb);
            } else {
                var oldLimit = bb.limit();
                bb.limit(internalbb.remaining());
                internalbb.put(bb);
                bb.limit(oldLimit);
            }
        } finally {
            bb.compact();
        }
        if (internalbb.hasRemaining()){
            return ProcessStatus.REFILL;
        }
        state=State.DONE;
        internalbb.flip();
        value=internalbb.getShort();
        return ProcessStatus.DONE;
    }

    @Override
    public Short get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state= State.WAITING;
        internalbb.clear();
    }
}