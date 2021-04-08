package fr.upem.net.chatos.datagram;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import fr.upem.net.chatos.reader.OpCodeReader;

public class ErrorCode implements Datagram {
	static public final byte OK                   = 1;
	static public final byte PSEUDO_UNAVAILABLE   = 2;
	static public final byte INVALID_PSEUDONYM    = 3;
	static public final byte UNREACHABLE_USER     = 4;
	static public final byte TCP_IN_PROTOCOLE     = 5;
	static public final byte TCP_NOT_IN_PROTOCOLE = 6;
	private       final byte errorCode;
	
	/**
	 * ErrorCode constructor : ErrorCode packet
	 * @param errorCode
	 */
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
	
	/**
	 * @brief get the request error code
	 * @return
	 */
	public byte getErrorCode() {
		return errorCode;
	}
	
	@Override
	public String toString() {
		return "Error : " + switch (errorCode) {
			case OK -> "OK";
			case PSEUDO_UNAVAILABLE -> "PSEUDO_UNAVAILABLE";
			case INVALID_PSEUDONYM  -> "INVALID_PSEUDONYM";
			case UNREACHABLE_USER 	-> "UNREACHABLE_USER";
			case TCP_IN_PROTOCOLE 	-> "TCP_IN_PROTOCOLE";
			case TCP_NOT_IN_PROTOCOLE -> "TCP_NOT_IN_PROTOCOLE";
			default -> "UNKOWN";
			};
	}
}
