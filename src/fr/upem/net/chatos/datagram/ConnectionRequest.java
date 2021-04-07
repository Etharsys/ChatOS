package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class ConnectionRequest implements Datagram {
	private final String pseudo;
	
	public ConnectionRequest(String pseudo) {
		Objects.requireNonNull(pseudo);
		this.pseudo = pseudo;
	}
	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		var bblog = UTF8_CHARSET.encode(pseudo);
		if (bblog.limit() > 1024) {
			System.out.println("Pseudo is too long");
			return Optional.empty();
		}
		return Optional.of(ByteBuffer.allocate(1 + Short.BYTES + bblog.limit())
				.put(OpCodeReader.CR_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
				.flip());
	}

}