package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class MessageAll implements Datagram{
	private final String sender;
	private final String message;
	
	/**
	 * MessageAll constructor : public message datagram, MessageAll packet
	 * @param sender the pseudo of the message sender 
	 * @param message the message to send
	 */
	public MessageAll(String sender, String message) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(message);
		this.sender = sender;
		this.message = message;
	}
	
	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		var bblog = UTF8_CHARSET.encode(sender);
		var bbmsg = UTF8_CHARSET.encode(message);
		if (bbmsg.limit() > MAX_STRING_SIZE) {
			logger.info("Message exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		return Optional.of(ByteBuffer.allocate(1+2*Short.BYTES + bblog.limit() + bbmsg.limit())
				.put(OpCodeReader.SMA_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
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
	 * @brief get the message to send
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
}
