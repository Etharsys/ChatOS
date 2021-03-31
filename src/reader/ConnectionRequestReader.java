package reader;

import java.nio.ByteBuffer;
import java.util.Objects;

public class ConnectionRequestReader implements Reader<String>, DatagramReader{
	private final StringReader reader = new StringReader();

	@Override
	public void accept(DatagramVisitor visitor) {
		Objects.requireNonNull(visitor);
		visitor.visit(this);
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
