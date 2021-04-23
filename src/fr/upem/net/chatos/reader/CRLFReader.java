package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CRLFReader implements Reader<String> {
	private static final int     LINE_SIZE = 1_024;
	private static final Charset ASCII     = StandardCharsets.US_ASCII;
	
	private enum State {
		DONE,
		WAITING_CR,
		WAITING_LF,
		ERROR
	};
	
	private State state = State.WAITING_CR;
	
	private final ByteBuffer internalbb = ByteBuffer.allocate(LINE_SIZE);
	
	private String line;

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (!internalbb.hasRemaining()) {
			return ProcessStatus.ERROR;
		}
		bb.flip();
		while (bb.hasRemaining()) {
			var next_c = bb.get();
			if (state == State.WAITING_CR) { // si prochain char == \r -> LF
				if ((char) next_c == '\r') {
					state = State.WAITING_LF;
				}
			} else { // si prochain char == \n -> DONE sinon CR (| LF si \r)
				if ((char) next_c == '\n') {
					state = State.DONE;
					internalbb.flip();
					internalbb.limit(internalbb.limit() - 1);
					line = ASCII.decode(internalbb).toString();
					bb.compact();
					return ProcessStatus.DONE;
				} 
				if (next_c != '\r') {
					state = State.WAITING_CR;
				}
			}
			internalbb.put(next_c);
		}
		bb.clear();
		return ProcessStatus.REFILL;
	}

	@Override
	public String get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return line;
	}

	@Override
	public void reset() {
		state = State.WAITING_CR;
		internalbb.clear();
	}
	
	
}
