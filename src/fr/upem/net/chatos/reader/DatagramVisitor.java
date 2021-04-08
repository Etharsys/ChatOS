package fr.upem.net.chatos.reader;

public interface DatagramVisitor<T> {
	
	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a connexion request
	 * @param context the actual context
	 */
	public void visit(ConnectionRequestReader reader, T context);
	
	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a private message reader
	 * @param context the actual context
	 */
	public void visit(SendPrivateMessageReader reader, T context);
	
	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a public message reader
	 * @param context the actual context
	 */
	public void visit(SendMessageAllReader reader, T context);
	
	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for an error reader
	 * @param context the actual context
	 */
	public void visit(ErrorCodeReader reader, T context);

	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a TCP private connexion ask request
	 * @param context the actual context
	 */
	public void visit(TCPAskReader tcpAskReader, T context);

	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a TCP private connexion abort request
	 * @param context the actual context
	 */
	public void visit(TCPAbortReader tcpAbortReader, T context);

	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a TCP private connexion connect request
	 * @param context the actual context
	 */
	public void visit(TCPConnectReader tcpConnectReader, T context);

	/**
	 * 
	 * @brief visit the datagram visitor
	 * @param reader the reader for a TCP private connexion accept request
	 * @param context the actual context
	 */
	public void visit(TCPAcceptReader tcpAcceptReader, T context);
}
