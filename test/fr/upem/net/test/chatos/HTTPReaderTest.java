package fr.upem.net.test.chatos;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.upem.net.chatos.reader.HTTPReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

class HTTPReaderTest {
	private static final Charset ASCII = StandardCharsets.US_ASCII;
	private static final int BUFFER_SIZE = 1024;
	
	@Tag("HTTPReader")
	@Test
	public void ShouldReturnDone() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 3\r\n"
				+ "aaa";
		bb.put(ASCII.encode(s));
		var header = new HTTPReader();
		assertEquals(ProcessStatus.DONE, header.process(bb));
	}
	
	@Tag("HTTPReader")
	@Test
	public void ShouldGetContent() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 3\r\n"
				+ "aaa";
		bb.put(ASCII.encode(s));
		var responce = new HTTPReader();
		responce.process(bb);
		assertEquals("aaa" , responce.get().getContent());
	}
	
	@Tag("HTTPReader")
	@Test
	public void ShouldNotReadToMuch() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 3\r\n"
				+ "aaaDAUTRESTRUCS";
		bb.put(ASCII.encode(s));
		var responce = new HTTPReader();
		responce.process(bb);
		bb.flip();
		assertEquals("DAUTRESTRUCS", ASCII.decode(bb).toString());
	}
	
	@Tag("HTTPReader")
	@Test
	public void ShouldReturnRefill() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 3\r\n"
				+ "aa";
		bb.put(ASCII.encode(s));
		var responce = new HTTPReader();
		assertEquals(ProcessStatus.REFILL, responce.process(bb));
	}
	
	@Tag("HTTPReader")
	@Test
	public void ShouldReturnDoneInTwoTimes() {
		ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		String s = "HTTP/1.0 200 OK\r\n"
				+ "Content-Type: text/html\r\n"
				+ "Content-Length: 3\r\n"
				+ "aa";
		bb.put(ASCII.encode(s));
		var responce = new HTTPReader();
		responce.process(bb);
		bb.put(ASCII.encode("a"));
		assertEquals(ProcessStatus.DONE, responce.process(bb));
	}
	
}
