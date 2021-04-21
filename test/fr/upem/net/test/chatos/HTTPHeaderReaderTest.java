package fr.upem.net.test.chatos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.upem.net.chatos.reader.HTTPHeaderReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

class HTTPHeaderReaderTest {
	private static final Charset ASCII = StandardCharsets.US_ASCII;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("HTTPHeaderReader")
	@Test
	public void ShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 60\r\n";
		bb.put(ASCII.encode(s));
		var header = new HTTPHeaderReader();
		assertEquals(ProcessStatus.DONE, header.process(bb));
	}
	
	@Tag("HTTPHeaderReader")
	@Test
	public void ShouldGetHTTPHeader() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 60\r\n";
		bb.put(ASCII.encode(s));
		var CRLF = new HTTPHeaderReader();
		CRLF.process(bb);
		assertEquals("HTTP/1.0" , CRLF.get().getVersion());
		assertEquals("200 OK"   , CRLF.get().getResponce_code());
		assertEquals("text/html", CRLF.get().getContent_type());
		assertEquals(60         , CRLF.get().getContentLength());
	}
	
	@Tag("HTTPHeaderReader")
	@Test
	public void ShouldNotReadToMuch() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 60\r\ndestrucspasouf";
		bb.put(ASCII.encode(s));
		var CRLF = new HTTPHeaderReader();
		CRLF.process(bb);
		bb.flip();
		assertEquals("destrucspasouf", ASCII.decode(bb).toString());
	}
	
	@Tag("HTTPHeaderReader")
	@Test
	public void ShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n";
		bb.put(ASCII.encode(s));
		var CRLF = new HTTPHeaderReader();
		assertEquals(ProcessStatus.REFILL, CRLF.process(bb));
	}
	
	@Tag("HTTPHeaderReader")
	@Test
	public void ShouldReturnDoneInTwoTimes() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n";
		bb.put(ASCII.encode(s));
		var CRLF = new HTTPHeaderReader();
		CRLF.process(bb);
		bb.put(ASCII.encode("Content-Length: 60\r\n"));
		assertEquals(ProcessStatus.DONE, CRLF.process(bb));
	}
	
}
