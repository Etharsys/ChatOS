package reader;

/**
 * Interface representing datagrams readers able to read datagrams without OpCode
 * */
public interface DatagramReader{
	public void accept(DatagramVisitor visitor);
}
