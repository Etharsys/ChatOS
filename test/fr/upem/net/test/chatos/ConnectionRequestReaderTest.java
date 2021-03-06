package fr.upem.net.test.chatos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.upem.net.chatos.reader.ConnectionRequestReader;
import fr.upem.net.chatos.reader.FrameVisitor;
import fr.upem.net.chatos.reader.ErrorCodeReader;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;


public class ConnectionRequestReaderTest {
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.put((byte)1);
		bb.put((byte)1);
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assertEquals(ProcessStatus.REFILL,cr.process(bb));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assertEquals(ProcessStatus.DONE,cr.process(bb));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldNotReadAllTheBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		var str = "ne doit pas etre lu";
		bb.putShort((short)3)
			.put(UTF_8.encode("abc" + str));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assertEquals(ProcessStatus.DONE,cr.process(bb));
		assertEquals(str,UTF_8.decode(bb.flip()).toString());
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldReturnTheCorrectString() {
		FrameVisitor<Void> visitor = new FrameVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
				if(reader.get().equals("abc")) {
					throw new NullPointerException();
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

			@Override
			public void visit(TCPAskReader tcpAskReader, Void context) {
			}

			@Override
			public void visit(TCPAbortReader tcpDeniedReader, Void context) {
			}

			@Override
			public void visit(TCPConnectReader tcpConnectReader, Void context) {
			}

			@Override
			public void visit(TCPAcceptReader tcpAcceptReader, Void context) {
			}
		};
		
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abcefgh"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assertEquals(ProcessStatus.DONE,cr.process(bb));
		assertThrows(NullPointerException.class,() -> cr.accept(visitor,null));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestShouldResetCorrectly() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3);
		bb.put(UTF_8.encode("abc"));
		bb.putShort((short)2);
		bb.put(UTF_8.encode("bc"));
		ConnectionRequestReader cr = new ConnectionRequestReader();
		assertEquals(ProcessStatus.DONE,cr.process(bb));
		cr.reset();
		assertEquals(ProcessStatus.DONE,cr.process(bb));
	}
	
	@Tag("ConnectionRequestReader")
	@Test
	public void ConnectionRequestReaderShouldThrowWhenAcceptingTooEarly() {
		var CR = new ConnectionRequestReader();
		assertThrows(IllegalStateException.class, ()->CR.get());
	}
}
