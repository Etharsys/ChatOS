package reader;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ConnectionRequestReader implements DatagramReader<String>{
	private final StringReader reader = new StringReader();

	@Override
	public <T>void accept(DatagramVisitor<T> visitor, T context) {
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
