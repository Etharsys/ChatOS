package reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {
	private static final int STRING_SIZE = 1024;
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private enum State {DONE,WAITING_INT, WAITING_STRING,ERROR};
	
    private State state = State.WAITING_INT;
    private final ShortReader intReader = new ShortReader();
    private final ByteBuffer internalbb = ByteBuffer.allocate(STRING_SIZE); // write-mode
    private String value;

    
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_INT) {
	        var ps = intReader.process(bb);
	        switch(ps) {
	        case REFILL:
	        	return ps;
	        case DONE:
	        	state = State.WAITING_STRING;
	        	int size = intReader.get();
	        	if (size <= 0 || size > 1024) {
	        		state = State.ERROR;
	        		return ProcessStatus.ERROR;
	        	}
	        	internalbb.limit(size);
	        	break;
	        case ERROR:
	        	state = State.ERROR;
	        	return ps;
	        }
        }
        bb.flip();
        if (bb.remaining()<=internalbb.remaining()){
    		internalbb.put(bb);
        } else {
        	var oldLimit = bb.limit();
        	bb.limit(internalbb.remaining());
        	internalbb.put(bb);
        	bb.limit(oldLimit);
        }
        bb.compact();
        if (internalbb.hasRemaining()) {
        	return ProcessStatus.REFILL;
        }
    	state = State.DONE;
        internalbb.flip();
        value = UTF_8.decode(internalbb).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state= State.WAITING_INT;
        internalbb.clear().limit(Integer.BYTES);
        intReader.reset();
    }
}
