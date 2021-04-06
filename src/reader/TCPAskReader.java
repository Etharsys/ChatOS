package reader;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.upem.net.chatos.datagram.TCPAsk;

public class TCPAskReader implements DatagramReader<TCPAsk> {
	private enum State {DONE,WAITING_SENDER, WAITING_RECIPIENT,ERROR};
	
    private State state = State.WAITING_SENDER;
    private final StringReader stringReader = new StringReader();
    private String sender;
    private String recipient;
    
	@Override
	public ProcessStatus process(ByteBuffer bb) {
    	Objects.requireNonNull(bb);
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
        var ps = stringReader.process(bb);
        switch(ps) {
        case REFILL:
        	return ps;
        case DONE:
        	break;
        case ERROR:
        	state = State.ERROR;
        	return ps;
        }
        if (state == State.WAITING_SENDER) {
        	state = State.WAITING_RECIPIENT;
        	sender = stringReader.get();
        	stringReader.reset();
        	return process(bb);
        }
    	state = State.DONE;
    	recipient = stringReader.get();
    	return ProcessStatus.DONE;
	}

	@Override
	public TCPAsk get() {
		return new TCPAsk(sender, recipient);
	}

	@Override
	public void reset() {
        state= State.WAITING_SENDER;
        stringReader.reset();
	}

	@Override
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

}
