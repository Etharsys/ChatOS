package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.util.Objects;

import fr.upem.net.chatos.datagram.TCPFrame;

abstract class AbstractTCPDatagramReader<T extends TCPFrame> implements FrameReader<T> {
	
	/**
	 * the status of the reader for a TCP private connexion request
	 */
	private enum State {
		DONE, 
		WAITING_SENDER, 
		WAITING_RECIPIENT, 
		WAITING_PASSWORD, 
		ERROR
	};
	
    private       State        state        = State.WAITING_SENDER;
    private final StringReader stringReader = new StringReader();
    private final ShortReader  shortReader  = new ShortReader();
    private       String       sender;
    private       String       recipient;
    private       short        password;
    
    /**
     * 
     * @brief get the TCP private connexion password
     * @return
     */
    short getPassword() {
		return password;
	}
    
    /**
     * 
     * @brief get the sender pseudonym
     * @return the pseudonym
     */
    String getSender() {
		return sender;
	}
    
    /**
     * 
     * @brief get the recipient pseudonym
     * @return the pseudonym
     */
    String getRecipient() {
		return recipient;
	}
    
	@Override
	public ProcessStatus process(ByteBuffer bb) {
    	Objects.requireNonNull(bb);
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
        ProcessStatus ps;
        if (state == State.WAITING_PASSWORD) {
        	ps = shortReader.process(bb);
        } else {
        	ps = stringReader.process(bb);
        } 
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
        if (state == State.WAITING_RECIPIENT) {
        	state = State.WAITING_PASSWORD;
        	recipient = stringReader.get();
        	return process(bb);
        }
    	state = State.DONE;
    	password = shortReader.get();
    	return ProcessStatus.DONE;
	}
	
	@Override
	public void reset() {
        state= State.WAITING_SENDER;
        stringReader.reset();
        shortReader.reset();
	}
}
