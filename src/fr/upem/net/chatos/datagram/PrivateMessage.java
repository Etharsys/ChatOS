package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class PrivateMessage implements Datagram{
	private final String sender;
	private final String recipient;
	private final String message;
	
	public PrivateMessage(String sender, String recipient, String message) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(recipient);
		Objects.requireNonNull(message);
		this.sender = sender;
		this.recipient = recipient;
		this.message = message;
	}

	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		var bblog = UTF8_CHARSET.encode(sender);
		var bbrec = UTF8_CHARSET.encode(recipient);
		var bbmsg = UTF8_CHARSET.encode(message);
		
		if (bbrec.limit() > MAX_STRING_SIZE) {
			logger.info("Recipient exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		if (bbmsg.limit() > MAX_STRING_SIZE) {
			logger.info("Message exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		return Optional.of(ByteBuffer.allocate(1+3*Short.BYTES + bblog.limit() + bbrec.limit() + bbmsg.limit())
				.put(OpCodeReader.SPM_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
				.putShort((short)bbrec.limit())
				.put(bbrec)
				.putShort((short)bbmsg.limit())
				.put(bbmsg));
	}
	
	public String getSender() {
		return sender;
	}
	
	public String getRecipient() {
		return recipient;
	}
	
	public String getMessage() {
		return message;
	}
}
