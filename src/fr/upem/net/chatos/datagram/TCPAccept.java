package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class TCPAccept extends AbstractTCPDatagram{
	public TCPAccept(String sender, String recipient, short password) {
		super(sender, recipient, password);
	}

	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		return super.toByteBuffer(logger, OpCodeReader.TCPACCEPT_CODE);
	}
}
