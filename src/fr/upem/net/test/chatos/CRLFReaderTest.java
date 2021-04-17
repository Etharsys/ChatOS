package fr.upem.net.test.chatos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.upem.net.chatos.reader.CRLFReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

class CRLFReaderTest {
	private static final Charset ASCII = StandardCharsets.US_ASCII;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("CRLFReader")
	@Test
	public void ShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\nContent";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		assertEquals(ProcessStatus.DONE, CRLF.process(bb));
	}
	
	@Tag("CRLFReader")
	@Test
	public void ShouldGetSameString() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "coucoucoucou\r\n";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		CRLF.process(bb);
		assertEquals("coucoucoucou", CRLF.get());
	}
	
	@Tag("CRLFReader")
	@Test
	public void ShouldNotReadToMuch() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "coucoucoucou\r\nsalut";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		CRLF.process(bb);
		bb.flip();
		assertEquals("salut", ASCII.decode(bb).toString());
	}
	
	@Tag("CRLFReader")
	@Test
	public void ShouldReadEvenWithBackSlashIn() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "coucou\rcou\ncou\r\nsalut";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		CRLF.process(bb);
		assertEquals("coucou\rcou\ncou", CRLF.get());
	}
	
	@Tag("CRLFReader")
	@Test
	public void ShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "coucou\r";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		assertEquals(ProcessStatus.REFILL, CRLF.process(bb));
	}
	
	@Tag("CRLFReader")
	@Test
	public void ShouldReturnDoneInTwoTimes() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "aaa\r";
		bb.put(ASCII.encode(s));
		var CRLF = new CRLFReader();
		CRLF.process(bb);
		bb.put(ASCII.encode("\n"));
		assertEquals(ProcessStatus.DONE, CRLF.process(bb));
	}
	
}
