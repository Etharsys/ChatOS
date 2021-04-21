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
import fr.upem.net.chatos.reader.Reader.ProcessStatus;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;

public class SendPrivateMessageReaderTest {
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.put((byte)8);
		var SPM = new SendPrivateMessageReader();
		assertEquals(ProcessStatus.REFILL, SPM.process(bb));
	}
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)3)
			.put(UTF_8.encode("def"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"));
		var SPM = new SendPrivateMessageReader();
		assertEquals(ProcessStatus.DONE, SPM.process(bb));
	}
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldNotReadAllTheBuffer() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		var str = "ne doit pas etre lu";
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)3)
			.put(UTF_8.encode("def"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"))
			.put(UTF_8.encode(str));
		var SPM = new SendPrivateMessageReader();
		assertEquals(ProcessStatus.DONE, SPM.process(bb));
		assertEquals(str, UTF_8.decode(bb.flip()).toString());
	}
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldReturnTheCorrectMessage() {
		FrameVisitor<Void> visitor = new FrameVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
				var msg = reader.get();
				assertEquals("abc", msg.getSender());
				assertEquals("salut",msg.getMessage());
				var recipient = msg.getRecipient();
				assertEquals("def",recipient);
				throw new NullPointerException();
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
		bb.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)3)
			.put(UTF_8.encode("def"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"));
		var SPM = new SendPrivateMessageReader();
		assertEquals(ProcessStatus.DONE, SPM.process(bb));
		assertThrows(NullPointerException.class, ()->SPM.accept(visitor,null));
	}
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldResetCorrectly() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.putShort((short)3)
		.put(UTF_8.encode("abc"))
		.putShort((short)3)
		.put(UTF_8.encode("def"))
		.putShort((short)5)
		.put(UTF_8.encode("salut"));
		var SPM = new SendPrivateMessageReader();
		assertEquals(ProcessStatus.DONE,SPM.process(bb));
		SPM.reset();
		assertEquals(ProcessStatus.REFILL,SPM.process(bb));
	}
	
	@Tag("SendPrivateMessageReader")
	@Test
	public void SendPrivateMessageReaderShouldThrowWhenGettingTooEarly() {
		var SPM = new SendPrivateMessageReader();
		assertThrows(IllegalStateException.class, ()->SPM.get());
	}
}
