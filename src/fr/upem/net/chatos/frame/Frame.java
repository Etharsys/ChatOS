package fr.upem.net.chatos.frame;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.logging.Logger;

public interface Frame {
	final int     MAX_STRING_SIZE = 1_024;
	final Charset UTF8_CHARSET    = Charset.forName("UTF-8");
	
	Optional<ByteBuffer> toByteBuffer(Logger logger);
}
