package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.upem.net.chatos.frame.PrivateMessage;

public class SendPrivateMessageReader implements FrameReader<PrivateMessage>{
	private enum State {
		DONE,
		WAITING_SENDER_LOGIN, 
		WAITING_LOGINR, 
		WAITING_MESSAGE,
		ERROR
	};
	
    private State              state        = State.WAITING_SENDER_LOGIN;
    private final StringReader stringReader = new StringReader();
    
    private String sender;
    private String recipient;
    private String message;

    
	@Override
	public <T>void accept(FrameVisitor<T> visitor, T context) {
		Objects.requireNonNull(visitor);
		visitor.visit(this, context);
	}
    
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
        switch(state) {
        case WAITING_SENDER_LOGIN:
        	state = State.WAITING_LOGINR;
        	sender = stringReader.get();
        	stringReader.reset();
        	return process(bb);
        case WAITING_LOGINR:
        	state = State.WAITING_MESSAGE;
        	recipient = stringReader.get();
        	stringReader.reset();
        	return process(bb);
        case WAITING_MESSAGE:
        	state = State.DONE;
        	message = stringReader.get();
        	return ps;
        default:
        	throw new AssertionError();
        }
    }

    @Override
    public PrivateMessage get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return new PrivateMessage(sender, recipient, message);
    }

    @Override
    public void reset() {
        state= State.WAITING_SENDER_LOGIN;
        stringReader.reset();
    }
}
