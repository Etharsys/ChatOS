package reader;

import java.nio.ByteBuffer;
import java.util.Objects;


public class SendMessageAllReader implements DatagramReader<Message> {
	private enum State {DONE,WAITING_LOGIN, WAITING_MESSAGE,ERROR};
	
    private State state = State.WAITING_LOGIN;
    private final StringReader stringReader = new StringReader();
    private String login;
    private String message;

    
	@Override
	public <T>void accept(DatagramVisitor<T> visitor, T context) {
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
        if (state == State.WAITING_LOGIN) {
        	state = State.WAITING_MESSAGE;
        	login = stringReader.get();
        	stringReader.reset();
        	return process(bb);
        }
    	state = State.DONE;
    	message = stringReader.get();
    	return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return new Message(login, null, message);
    }

    @Override
    public void reset() {
        state= State.WAITING_LOGIN;
        stringReader.reset();
    }

}
