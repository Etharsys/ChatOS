package fr.upem.net.chatos.reader;

/**
 * Interface representing datagrams readers able to read datagrams without OpCode
 * */
public interface DatagramReader<E> extends Reader<E>{
	
	/**
	 * 
	 * @brief accept the visitor method 
	 * @param <T> the parametized type 
	 * @param visitor the datagram visitor (parametized with T)
	 * @param context the actual context
	 */
	public <T> void accept(DatagramVisitor<T> visitor, T context);
	
}
