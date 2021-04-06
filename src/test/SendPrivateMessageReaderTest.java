package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.Reader.ProcessStatus;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;
import reader.TCPAskReader;

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
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

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
