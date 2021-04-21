package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import fr.upem.net.chatos.frame.HTTPFrame;
import fr.upem.net.chatos.frame.HTTPHeader;


public class HTTPReader implements Reader<HTTPFrame> {
	private static final int     READER_SIZE = 1_024;
	private static final Charset ASCII       = StandardCharsets.US_ASCII;
	
	private enum State {
		DONE,
		WAITING_HEADER,
		WAITING_CONTENT,
		ERROR
	};
	
	private State state = State.WAITING_HEADER;
	
	private HTTPHeader       header;
	private HTTPHeaderReader headerReader = new HTTPHeaderReader();
	
	private final ByteBuffer internalbb = ByteBuffer.allocate(READER_SIZE);
	
	private String content;
	
	
	@Override
	public ProcessStatus process(ByteBuffer bb) {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.WAITING_HEADER) {
			//System.out.println(ASCII.decode(bb));
			var ps = headerReader.process(bb);
			switch(ps) {
		        case REFILL:
		        	return ps;
		        case DONE:
		        	state = State.WAITING_CONTENT;
		        	header = headerReader.get();
		        	headerReader.reset();
		        	break;
		        case ERROR:
		        	state = State.ERROR;
		        	return ps;
	        }
		}
		internalbb.limit(header.getContentLength());
		bb.flip();
		if (bb.remaining() < internalbb.remaining()) {
			internalbb.put(bb);
			bb.clear();
			return ProcessStatus.REFILL;
		} else {
			var tmp = bb.limit();
			bb.limit(internalbb.remaining());
			internalbb.put(bb);
			bb.limit(tmp);
			bb.compact();
			state = State.DONE;
			content = ASCII.decode(internalbb.flip()).toString();
			return ProcessStatus.DONE;
		}
	}

	@Override
	public HTTPFrame get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return new HTTPFrame(header, content);
	}

	@Override
	public void reset() {
		state = State.WAITING_HEADER;
		internalbb.clear();
		headerReader.reset();
	}
	
	
}
