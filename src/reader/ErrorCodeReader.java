package reader;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.upem.net.chatos.datagram.ErrorCode;

public class ErrorCodeReader implements DatagramReader<ErrorCode>{
	
	/**
	 * the status of the reader for a ErrorCode request
	 */
	private enum State {
		DONE,
		WAITING,
		ERROR
	};
	
	private State state = State.WAITING;
	private byte code;
	
	@Override
	public <T>void accept(DatagramVisitor<T> visitor, T context) {
		Objects.requireNonNull(visitor);
		visitor.visit(this,context);
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
		bb.flip();
		if (!bb.hasRemaining()) {
			bb.compact();
			return ProcessStatus.REFILL;
		}
		code = bb.get();

		bb.compact();
		state = State.DONE;
		return ProcessStatus.DONE;
	}

	@Override
	public ErrorCode get() {
		return new ErrorCode(code);
	}

	@Override
	public void reset() {
		state = State.WAITING;
	}
	
}
