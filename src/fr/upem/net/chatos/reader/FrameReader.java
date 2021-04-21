package fr.upem.net.chatos.reader;

/**
 * Interface representing frames readers able to read frames without OpCode
 * */
public interface FrameReader<E> extends Reader<E>{
	
	/**
	 * 
	 * @brief accept the visitor method 
	 * @param <T> the parametized type 
	 * @param visitor the frame visitor (parametized with T)
	 * @param context the actual context
	 */
	public <T> void accept(FrameVisitor<T> visitor, T context);
	
}
