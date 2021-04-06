package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class TCPAsk implements Datagram{
	private final String sender;
	private final String recipient;
	
	public TCPAsk(String sender, String recipient) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(recipient);
		this.sender = sender;
		this.recipient = recipient;
	}
	
	public String getRecipient() {
		return recipient;
	}
	
	public String getSender() {
		return sender;
	}

	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		var bbSend = UTF8_CHARSET.encode(sender);
		
		if (bbSend.limit() > MAX_STRING_SIZE) {
			logger.info("Login exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		var bbRecipient = UTF8_CHARSET.encode(recipient);
		if (bbRecipient.limit() > MAX_STRING_SIZE) {
			logger.info("Recipient exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		
		return Optional.of(ByteBuffer.allocate(1+2*Short.BYTES + bbSend.limit() + bbRecipient.limit())
				.put(OpCodeReader.TCPASK_CODE)
				.putShort((short)bbSend.limit())
				.put(bbSend)
				.putShort((short)bbRecipient.limit())
				.put(bbRecipient)
				.flip());
	}
}
