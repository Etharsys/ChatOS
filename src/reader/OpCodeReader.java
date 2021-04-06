package reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import reader.Reader.ProcessStatus;

public class OpCodeReader{
	static public final byte CR_CODE = 1;
	static public final byte SPM_CODE = 2;
	static public final byte SMA_CODE = 3;
	static public final byte TCPASK_CODE = 4;
	static public final byte ERROR_PACKET_CODE = 6;
	
	private enum State {DONE,WAITING,ERROR};
	private State state = State.WAITING;
	private final ConnectionRequestReader CR = new ConnectionRequestReader();
	private final SendPrivateMessageReader SPM = new SendPrivateMessageReader();
	private final SendMessageAllReader SMA = new SendMessageAllReader();
	private final ErrorCodeReader ERROR = new ErrorCodeReader();
	private final TCPAskReader TCP = new TCPAskReader();
	//TODO les autres (TCP)
	private Optional<DatagramReader<?>> reader = Optional.empty();
	
	private ProcessStatus getReader(ByteBuffer bb) {
		if (bb.position() == 0) {
			return ProcessStatus.REFILL;
		}
		bb.flip();
		switch(bb.get()) {
		case CR_CODE:
			reader = Optional.of(CR);
			break;
		case SPM_CODE:
			reader = Optional.of(SPM);
			break;
		case SMA_CODE:
			reader = Optional.of(SMA);
			break;
		case TCPASK_CODE:
			reader = Optional.of(TCP);
		case 5:
			throw new UnsupportedClassVersionError();
		case ERROR_PACKET_CODE:
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
