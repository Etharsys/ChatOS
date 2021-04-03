package reader;

public interface DatagramVisitor<T> {
//TODO public void visit(SpecificType (implements DatagramReader))
	public void visit(ConnectionRequestReader reader, T context);
	
	public void visit(SendPrivateMessageReader reader, T context);
	
	public void visit(SendMessageAllReader reader, T context);
	
	public void visit(ErrorCodeReader reader, T context);
}
