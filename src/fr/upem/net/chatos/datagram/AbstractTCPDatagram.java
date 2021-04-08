package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

abstract class AbstractTCPDatagram implements TCPDatagram{
	private final String sender;
	private final String recipient;
	private final short  password;
	
	/**
	 * AbstractTCPDatagram Constructor : super class for datagrams requests
	 * @param sender pseudo of the sender TCP request
	 * @param recipient pseudo of the recipient TCP request
	 * @param password password of this TCP connexion
	 */
	AbstractTCPDatagram(String sender, String recipient, short password) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(recipient);
		this.sender = sender;
		this.recipient = recipient;
		this.password = password;
	}
	
	/**
	 * Get the TCP connexion password
	 */
	@Override
	public short getPassword() {
		return password;
	}
	
	/**
	 * Get the recipient pseudo of the TCP Request
	 */
	@Override
	public String getRecipient() {
		return recipient;
	}
	
	/**
	 * Get the sender pseudo of the TCP Request
	 */
	@Override
	public String getSender() {
		return sender;
	}
	
	/**
	 * @brief transform a TCP request into a bytebuffer
	 * @param logger for debuging
	 * @param opCode the request operation code 
	 * @return the potential bytebuffer of the request
	 */
	Optional<ByteBuffer> toByteBuffer(Logger logger, byte opCode) {
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
		
		return Optional.of(ByteBuffer.allocate(1+2*Short.BYTES + bbSend.limit() + bbRecipient.limit() + Short.BYTES)
				.put(opCode)
				.putShort((short)bbSend.limit())
				.put(bbSend)
				.putShort((short)bbRecipient.limit())
				.put(bbRecipient)
				.putShort(password)
				.flip());
	}
}
