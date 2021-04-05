package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class MessageAll implements Datagram{
	private final String sender;
	private final String message;
	
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
				.put(bbmsg));
	}

	public String getSender() {
		return sender;
	}
	
	public String getMessage() {
		return message;
	}
}
