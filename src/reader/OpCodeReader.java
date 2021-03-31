package reader;

import java.nio.ByteBuffer;

public class OpCodeReader implements Reader<DatagramReader>{
	private enum State {DONE,WAITING,ERROR};
	private State state = State.WAITING;
	private ConnectionRequestReader CR = new ConnectionRequestReader();
	private SendPrivateMessageReader SPM = new SendPrivateMessageReader();
	private SendMessageAllReader SMA = new SendMessageAllReader();
	private ErrorCodeReader ERROR = new ErrorCodeReader();
	//TODO les autres (TCP)
	private byte code;
	
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
	public DatagramReader get() {
		if (state!= State.DONE) {
            throw new IllegalStateException();
        }
		return switch(code) {
		case 1 -> CR;
		case 2 -> SPM;
		case 3 -> SMA;
		case 4 -> throw new UnsupportedOperationException();
		case 5 -> throw new UnsupportedOperationException();
		case 6 -> ERROR;
		//TODO fault tolerant (ne pas faire AssertionError c'est mal : peut etre une exception custome?)
		default -> throw new AssertionError();
		};
	}

	@Override
	public void reset() {
		state = State.WAITING;
	}

}
