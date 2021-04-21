package fr.upem.net.chatos.frame;

import java.util.Objects;

public class HTTPFrame {
	private final HTTPHeader header;
	private final String     content;

	public HTTPFrame(HTTPHeader header, String content) {
		Objects.requireNonNull(content);
		this.header  = header;
		this.content = content;
	}
	
	public String getContent() {
		return content;
	}
	
	public HTTPHeader getHeader() {
		return header;
	}

}
