package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.logging.Logger;

public interface Datagram {
	final int MAX_STRING_SIZE = 1_024;
	final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	
	Optional<ByteBuffer> toByteBuffer(Logger logger);
}
