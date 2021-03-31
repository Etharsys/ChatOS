package reader;

public interface DatagramVisitor {
//TODO public void visit(SpecificType (implements DatagramReader))
	public void visit(ConnectionRequestReader reader);
	
	public void visit(SendPrivateMessageReader reader);
	
	public void visit(SendMessageAllReader reader);
	
	public void visit(ErrorCodeReader reader);
}
