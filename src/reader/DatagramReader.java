package reader;

/**
 * Interface representing datagrams readers able to read datagrams without OpCode
 * */
public interface DatagramReader<E> extends Reader<E>{
	public <T> void accept(DatagramVisitor<T> visitor, T context);
}
