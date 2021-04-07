package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import reader.OpCodeReader;

public class ErrorCode implements Datagram {
	static public final byte OK = 1;
	static public final byte PSEUDO_UNAVAILABLE = 2;
	static public final byte INVALID_PSEUDONYM = 3;
	static public final byte UNREACHABLE_USER = 4;
	static public final byte TCP_DENIED = 5;
	static public final byte TCP_ACCEPT = 6;
	private final byte errorCode;
	
	public ErrorCode(byte errorCode) {
		this.errorCode = errorCode;
	}
	@Override
	public Optional<ByteBuffer> toByteBuffer(Logger logger) {
		return Optional.of(ByteBuffer.allocate(2)
				.put(OpCodeReader.ERROR_PACKET_CODE)
				.put(errorCode)
				.flip());
	}
	
	public byte getErrorCode() {
		return errorCode;
	}
}
