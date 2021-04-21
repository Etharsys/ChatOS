package fr.upem.net.chatos.frame;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

public interface TCPFrame extends Frame{
	
	/**
	 * @brief get the TCP private connexion password
	 * @return the password
	 */
	public short getPassword();
	
	/**
	 * @brief get the recipient pseudonym
	 * @return the pseudonym
	 */
	public String getRecipient();
	
	/**
	 * @brief get the sender pseudonym
	 * @return the pseudonym
	 */
	public String getSender();
	
	Optional<ByteBuffer> toByteBuffer(Logger logger);
}
