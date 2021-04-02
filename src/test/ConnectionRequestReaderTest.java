package test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import reader.ConnectionRequestReader;
import reader.DatagramReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.OpCodeReader;
import reader.Reader.ProcessStatus;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;


public class ConnectionRequestReaderTest {
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES);
		bb.put((byte)1);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		OCR.process(bb);
		DatagramReader DR = OCR.get();
		assert(DR instanceof ConnectionRequestReader);
		assert(DR.process(bb) == ProcessStatus.REFILL);
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(6);
		bb.put((byte)1);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abc"));
		var OCR = new OpCodeReader();
		OCR.process(bb);
		DatagramReader DR = OCR.get();
		assert(DR instanceof ConnectionRequestReader);
		assert(DR.process(bb) == ProcessStatus.DONE);
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldNotReadAllTheBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.put((byte)1);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abcefgh"));
		var OCR = new OpCodeReader();
		OCR.process(bb);
		DatagramReader DR = OCR.get();
		assert(DR instanceof ConnectionRequestReader);
		assert(DR.process(bb) == ProcessStatus.DONE);
		assert(bb.hasRemaining());
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnTheCorrectString() {
		DatagramVisitor visitor = new DatagramVisitor(){

			@Override
			public void visit(ConnectionRequestReader reader) {
				if(reader.get().equals("abc")) {
					throw new AssertionError();
				}
			}

			@Override
			public void visit(SendPrivateMessageReader reader) {
			}

			@Override
			public void visit(SendMessageAllReader reader) {
			}
			@Override
			public void visit(ErrorCodeReader reader) {
			}
		};
		
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.put((byte)1);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abcefgh"));
		var OCR = new OpCodeReader();
		OCR.process(bb);
		DatagramReader DR = OCR.get();
		assert(DR instanceof ConnectionRequestReader);
		assert(DR.process(bb) == ProcessStatus.DONE);
		assertThrows(AssertionError.class,() -> DR.accept(visitor));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldResetCorrectly() {
		DatagramVisitor visitor = new DatagramVisitor(){

			@Override
			public void visit(ConnectionRequestReader reader) {
				reader.reset();
			}

			@Override
			public void visit(SendPrivateMessageReader reader) {
			}

			@Override
			public void visit(SendMessageAllReader reader) {
			}
			@Override
			public void visit(ErrorCodeReader reader) {
			}
		};
		
		ByteBuffer bb = ByteBuffer.allocate(11);
		bb.put((byte)1);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abc"));
		bb.putShort((short)2);
		bb.put(UTF_8.encode("bc"));
		var OCR = new OpCodeReader();
		OCR.process(bb);
		DatagramReader DR = OCR.get();
		assert(DR instanceof ConnectionRequestReader);
		assert(DR.process(bb) == ProcessStatus.DONE);
		DR.accept(visitor);
		assert(DR.process(bb) == ProcessStatus.DONE);
	}
}
