package reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import reader.Reader.ProcessStatus;

public class OpCodeReader{
	private enum State {DONE,WAITING,ERROR};
	private State state = State.WAITING;
	private final ConnectionRequestReader CR = new ConnectionRequestReader();
	private final SendPrivateMessageReader SPM = new SendPrivateMessageReader();
	private final SendMessageAllReader SMA = new SendMessageAllReader();
	private final ErrorCodeReader ERROR = new ErrorCodeReader();
	//TODO les autres (TCP)
	private Optional<DatagramReader<?>> reader = Optional.empty();
	
	private ProcessStatus getReader(ByteBuffer bb) {
		bb.flip();
		switch(bb.get()) {
		case 1:
			reader = Optional.of(CR);
			break;
		case 2:
			reader = Optional.of(SPM);
			break;
		case 3:
			reader = Optional.of(SMA);
			break;
		case 4:
			throw new UnsupportedClassVersionError();
		case 5:
			throw new UnsupportedClassVersionError();
		case 6:
			reader = Optional.of(ERROR);
			break;
		default:
			return ProcessStatus.ERROR;
		}
		bb.compact();
		return ProcessStatus.DONE;
	}
	
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
		if (reader.isPresent()) {
			var ps = reader.get().process(bb);
			switch(ps) {
			case DONE:
				state = State.DONE;
				return ps;
			case ERROR:
				state = State.ERROR;
				return ps;
			case REFILL:
				return ps;
			}
		} else {
			var ps = getReader(bb);
			switch(ps) {
			case DONE:
				break;
			case REFILL:
				return ps;
			case ERROR:
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			return process(bb);
		}
		return ProcessStatus.REFILL;
	}
	
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		Objects.requireNonNull(visitor);
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		reader.get().accept(visitor, context);
	}

	public void reset() {
		state = State.WAITING;
		if (reader.isPresent()) {
			reader.get().reset();
			reader = Optional.empty();
		}
	}
}
