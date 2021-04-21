package fr.upem.net.chatos.reader;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ConnectionRequestReader implements FrameReader<String>{
	
	private final StringReader reader = new StringReader();

	@Override
	public <T>void accept(FrameVisitor<T> visitor, T context) {
		Objects.requireNonNull(visitor);
		visitor.visit(this, context);
	}
	
	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		return reader.process(bb);
	}

	@Override
	public String get() {
		return reader.get();
	}

	@Override
	public void reset() {
		reader.reset();
	}
}
