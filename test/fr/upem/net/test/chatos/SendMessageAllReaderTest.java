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

public class SendMessageAllReaderTest {
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.put((byte)8);
		var SMA = new SendMessageAllReader();
		assertEquals(ProcessStatus.REFILL, SMA.process(bb));
	}
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"));
		var SMA = new SendMessageAllReader();
		assertEquals(ProcessStatus.DONE, SMA.process(bb));
	}
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldNotReadAllTheBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		var str = "ne doit pas etre lu";
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"))
			.put(UTF_8.encode(str));
		var SMA = new SendMessageAllReader();
		assertEquals(ProcessStatus.DONE, SMA.process(bb));
		assertEquals(str, UTF_8.decode(bb.flip()).toString());
	}
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldReturnTheCorrectMessage() {
		FrameVisitor<Void> visitor = new FrameVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
				var msg = reader.get();
				assertEquals("abc", msg.getSender());
				assertEquals("salut",msg.getMessage());
				throw new NullPointerException();
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
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"));
		var SMA = new SendMessageAllReader();
		assertEquals(ProcessStatus.DONE, SMA.process(bb));
		assertThrows(NullPointerException.class,()->SMA.accept(visitor, null));
	}
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldResetCorrectly() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)2)
			.put(UTF_8.encode("bc"));
		var SMA = new SendMessageAllReader();
		assertEquals(ProcessStatus.DONE,SMA.process(bb));
		SMA.reset();
		assertEquals(ProcessStatus.REFILL,SMA.process(bb));
	}
	
	@Tag("SendMessageAllReader")
	@Test
	public void SendMessageAllReaderShouldThrowWhenAcceptingTooEarly() {
		var SMA = new SendMessageAllReader();
		assertThrows(IllegalStateException.class, ()->SMA.get());
	}
}
