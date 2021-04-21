package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

import fr.upem.net.chatos.reader.Reader.ProcessStatus;

public class OpCodeReader{
	static public final byte CR_CODE           = 1;
	static public final byte SPM_CODE          = 2;
	static public final byte SMA_CODE          = 3;
	static public final byte TCPASK_CODE       = 4;
	static public final byte TCPACCEPT_CODE    = 5;
	static public final byte TCPABORT_CODE     = 6;
	static public final byte TCPCONNECT_CODE   = 7;
	static public final byte ERROR_PACKET_CODE = 8;
	
	/**
	 * the status of the reader for an initial packet
	 */
	private enum State {
		DONE,
		WAITING,
		ERROR
	};
	
	private State state = State.WAITING;
	
	//Readers
	private final ConnectionRequestReader  CR         = new ConnectionRequestReader();
	private final SendPrivateMessageReader SPM        = new SendPrivateMessageReader();
	private final SendMessageAllReader     SMA        = new SendMessageAllReader();
	private final ErrorCodeReader          ERROR      = new ErrorCodeReader();
	private final TCPAskReader             TCPAsk     = new TCPAskReader();
	private final TCPAcceptReader          TCPAccept  = new TCPAcceptReader();
	private final TCPAbortReader           TCPDenied  = new TCPAbortReader();
	private final TCPConnectReader         TCPConnect = new TCPConnectReader();
	
	private Optional<FrameReader<?>> reader = Optional.empty();
	
	/**
	 * 
	 * @brief initiate the reader in terms of the first byte of the bytebuffer
	 * @param bb the bytebuffer to parse
	 * @return the status of the current reader
	 */
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
			reader = Optional.of(TCPAsk);
			break;
		case TCPACCEPT_CODE:
			reader = Optional.of(TCPAccept);
			break;
		case TCPABORT_CODE:
			reader = Optional.of(TCPDenied);
			break;
		case TCPCONNECT_CODE:
			reader = Optional.of(TCPConnect);
			break;
		case ERROR_PACKET_CODE:
			reader = Optional.of(ERROR);
			break;
		default:
			return ProcessStatus.ERROR;
		}
		bb.compact();
		return ProcessStatus.DONE;
	}
	
	/**
	 * 
	 * @brief process the bytebuffer 
	 * @param bb the bytebuffer to parse
	 * @return the actual status of the reader
	 */
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
	
	/**
	 * 
	 * @brief accept the visitor method
	 * @param <T> the parametized type
	 * @param visitor the frame visitor (parametized with T)
	 * @param context the actual context
	 */
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		Objects.requireNonNull(visitor);
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		reader.get().accept(visitor, context);
	}

	/**
	 * 
	 * @brief reset the reader
	 */
	public void reset() {
		state = State.WAITING;
		if (reader.isPresent()) {
			reader.get().reset();
			reader = Optional.empty();
		}
	}
}
