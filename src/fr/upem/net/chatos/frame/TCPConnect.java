package fr.upem.net.chatos.frame;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import fr.upem.net.chatos.reader.OpCodeReader;

public class TCPConnect extends AbstractTCPFrame {

	/**
	 * TCPConnect constructor : packet TCPAbort datagram
	 * @param sender the pseudo of the TCP private connexion sender
	 * @param recipient the pseudo of the TCP private connexion recipient
	 * @param password the TCP private connexion password
	 */
	public TCPConnect(String sender, String recipient, short password) {
		super(sender, recipient, password);
	}

	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		return super.toByteBuffer(logger, OpCodeReader.TCPCONNECT_CODE);
	}

}
