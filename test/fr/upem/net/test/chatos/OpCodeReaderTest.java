package fr.upem.net.test.chatos;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.upem.net.chatos.reader.ConnectionRequestReader;
import fr.upem.net.chatos.reader.DatagramVisitor;
import fr.upem.net.chatos.reader.ErrorCodeReader;
import fr.upem.net.chatos.reader.OpCodeReader;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

class OpCodeReaderTest {
	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldContainCRAfterOpCode1() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

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
		bb.put((byte)1)
			.putShort((short)3)
			.put(UTF_8.encode("abc"));
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(NullPointerException.class,()->OCR.accept(visitor, null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldContainSPMAfterOpCode2() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
				reader.get();
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
		bb.put((byte)2)
			.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short)3)
			.put(UTF_8.encode("def"))
			.putShort((short)5)
			.put(UTF_8.encode("salut"));
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(NullPointerException.class, () -> OCR.accept(visitor, null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldContainSMAAfterOpCode3() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
				reader.get();
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
		bb.put((byte)3)
			.putShort((short)3)
			.put(UTF_8.encode("abc"))
			.putShort((short) 5)
			.put(UTF_8.encode("salut"));
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(NullPointerException.class, () -> OCR.accept(visitor, null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnERRORWhenFacing6() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
			}
			@Override
			public void visit(ErrorCodeReader reader, Void Context) {
				reader.get();
				throw new NullPointerException();
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
		bb.put((byte)6)
			.put((byte)1);
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(NullPointerException.class, () -> OCR.accept(visitor, null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldThrowIfProcessingWhenDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(IllegalStateException.class, () -> OCR.process(bb));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldThrowWhenAcceptingTooEarly() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
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
		bb.put((byte)6);
		bb.put((byte)1);
		var OCR = new OpCodeReader();
		assertThrows(IllegalStateException.class, () -> OCR.accept(visitor,null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldCorrectlyReset() {
		DatagramVisitor<Void> visitor = new DatagramVisitor<Void>(){

			@Override
			public void visit(ConnectionRequestReader reader, Void Context) {
			}

			@Override
			public void visit(SendPrivateMessageReader reader, Void Context) {
			}

			@Override
			public void visit(SendMessageAllReader reader, Void Context) {
			}
			@Override
			public void visit(ErrorCodeReader reader, Void Context) {
				throw new NullPointerException();
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
		bb.put((byte)6)
			.put((byte)1)
			.put((byte)6)
			.put((byte)1);
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		OCR.reset();
		assertThrows(IllegalStateException.class,()->OCR.accept(visitor,null));
		assertEquals(ProcessStatus.DONE,OCR.process(bb));
		assertThrows(NullPointerException.class,()->OCR.accept(visitor,null));
	}
	
	@Tag("OpCodeReader")
	@Test
	public void OpCodeShouldReturnErrorWhenUnkownOpCode() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		bb.put((byte)27);
		var OCR = new OpCodeReader();
		assertEquals(ProcessStatus.ERROR,OCR.process(bb));
	}
}
