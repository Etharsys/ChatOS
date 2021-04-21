package fr.upem.net.chatos.frame;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import fr.upem.net.chatos.reader.OpCodeReader;

public class PrivateMessage implements Frame{
	private final String sender;
	private final String recipient;
	private final String message;
	
	/**
	 * PrivateMessage constructor : private message datagram, PrivateMessage packet
	 * @param sender the pseudo of the message sender
	 * @param recipient the pseudo of the recipient who will get the message
	 * @param message the message to send
	 */
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
				.put(bbmsg)
				.flip());
	}
	
	/**
	 * @brief get the sender pseudonym
	 * @return the pseudonym
	 */
	public String getSender() {
		return sender;
	}
	
	/**
	 * @brief get the recipient pseudonym
	 * @return the pseudonym
	 */
	public String getRecipient() {
		return recipient;
	}
	
	/**
	 * @brief get the message to send
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
}
