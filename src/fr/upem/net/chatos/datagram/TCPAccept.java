package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class TCPAccept extends AbstractTCPDatagram{
	
	/**
	 * TCPAccept constructor : packet TCPAccept datagram
	 * @param sender the pseudo of the TCP private connexion sender
	 * @param recipient the pseudo of the TCP private connexion recipient
	 * @param password the TCP private connexion password
	 */
	public TCPAccept(String sender, String recipient, short password) {
		super(sender, recipient, password);
	}

	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		return super.toByteBuffer(logger, OpCodeReader.TCPACCEPT_CODE);
	}
}
