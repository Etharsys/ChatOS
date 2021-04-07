package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

public interface TCPDatagram extends Datagram{
	public short getPassword();
	
	public String getRecipient();
	
	public String getSender();
	
	Optional<ByteBuffer> toByteBuffer(Logger logger);
}
