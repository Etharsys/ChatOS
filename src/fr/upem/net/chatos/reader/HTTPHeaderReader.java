package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;

import fr.upem.net.chatos.datagram.HTTPHeader;

public class HTTPHeaderReader implements Reader<HTTPHeader> {
	
	private enum State {
		DONE,
		WAITING_HEAD,
		WAITING_TYPE,
		WAITING_LENGTH,
		ERROR
	};
	
	private State state = State.WAITING_HEAD;
	
	private String version;
	private String responce_code; // 200 OK | 404 NotFound
	private String content_type;
	private int    content_length;
	
	private CRLFReader line_reader = new CRLFReader();
	
	@Override
	public ProcessStatus process(ByteBuffer bb) throws NumberFormatException {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.WAITING_HEAD) {
			var ps = line_reader.process(bb);
			switch (ps) {
				case REFILL: return ps;
				case DONE  : state = State.WAITING_TYPE;
							 var head = line_reader.get().split(" ");
							 if (head.length != 3) {
								 return ProcessStatus.ERROR;
							 }
							 version       = head[0];
							 responce_code = head[1] + " " + head[2];
							 line_reader.reset();
							 break;
				default    : state = State.ERROR;
							 return ps;
			}
		} if (state == State.WAITING_TYPE) {
			var ps = line_reader.process(bb);
			switch (ps) {
				case REFILL: return ps;
				case DONE  : state = State.WAITING_LENGTH;
							 var head = line_reader.get().split(" ");
							 if (head.length != 2) {
								 return ProcessStatus.ERROR;
							 }
							 content_type = head[1];
							 line_reader.reset();
							 break;
				default    : state = State.ERROR;
							 return ps;
			}
		}
		var ps = line_reader.process(bb);
		switch (ps) {
			case REFILL: return ps;
			case DONE  : state = State.DONE;
						 var head = line_reader.get().split(" ");
						 if (head.length != 2) {
							 return ProcessStatus.ERROR;
						 }
						 content_length = Integer.parseInt(head[1]);
						 return ps;
			default    : state = State.ERROR;
						 return ps;
		}
	}

	@Override
	public HTTPHeader get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return new HTTPHeader(version, responce_code, content_type, content_length);
	}

	@Override
	public void reset() {
		state = State.WAITING_HEAD;
		line_reader.reset();
	}
	
	
}
