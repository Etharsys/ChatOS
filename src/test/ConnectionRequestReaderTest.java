package test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import reader.ConnectionRequestReader;
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
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assert(cr.process(bb) == ProcessStatus.REFILL);
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(6);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abc"));
		var OCR = new OpCodeReader();
		OCR.process(bb);
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assert(cr.process(bb) == ProcessStatus.DONE);
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldNotReadAllTheBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abcefgh"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assert(cr.process(bb) == ProcessStatus.DONE);
		assert(bb.hasRemaining());
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnTheCorrectString() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
				if(reader.get().equals("abc")) {
					throw new AssertionError();
				}
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
			}
			@Override
			public void visit(ErrorCodeReader reader, Void Context) {
			}
		};
		
		ByteBuffer bb = ByteBuffer.allocate(10);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abcefgh"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assert(cr.process(bb) == ProcessStatus.DONE);
		assertThrows(AssertionError.class,() -> cr.accept(visitor,null));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldResetCorrectly() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
				reader.reset();
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
			}
			@Override
			public void visit(ErrorCodeReader reader, Void Context) {
			}
		};
		
		ByteBuffer bb = ByteBuffer.allocate(11);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abc"));
		bb.putShort((short)2);
		bb.put(UTF_8.encode("bc"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assert(cr.process(bb) == ProcessStatus.DONE);
		cr.accept(visitor, null);
		assert(cr.process(bb) == ProcessStatus.DONE);
	}
}
