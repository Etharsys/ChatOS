package reader;

import java.nio.ByteBuffer;

//TODO le cas TCP connect
public class ErrorCodeReader implements Reader<Byte>, DatagramReader{
	private enum State {DONE,WAITING,ERROR};
	private State state = State.WAITING;
	private byte code;
	
	@Override
	public void accept(DatagramVisitor visitor) {
		visitor.visit(this);
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
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
	public Byte get() {
		return code;
	}

	@Override
	public void reset() {
		state = State.WAITING;
	}
	
}
