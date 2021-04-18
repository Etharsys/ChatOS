package fr.upem.net.chatos.datagram;

public class HTTPHeader {
	private final String version;
	private final String responce_code;
	private final String content_type;
	private final int    content_length;

	public HTTPHeader(String version, String responce_code, String content_type, int content_length) {
		if (!version.equals("HTTP/1.0")) {
			throw new IllegalArgumentException("HTTP version is not supported, " + version + " != HTTP/1.0");
		}
		if (!responce_code.equals("200 OK") && !responce_code.equals("404 NotFound")) {
			throw new IllegalArgumentException("Error not supported");
		}
		if (!content_type.equals("text/html") && !content_type.equals("pdf/html")) {
			throw new IllegalArgumentException("Content type not supported");
		}
		if (content_length < 0 || content_length > 1_024) {
			throw new IllegalArgumentException("Content length need to be between 0 and 1024");
		}
		this.version = version;
		this.responce_code = responce_code;
		this.content_type = content_type;
		this.content_length = content_length;
	}

	public int getContentLength() {
		return content_length;
	}
	
	public String getContent_type() {
		return content_type;
	}
	
	public String getResponce_code() {
		return responce_code;
	}
	
	public String getVersion() {
		return version;
	}
}
