package fr.upem.net.chatos.client;

import java.io.IOException;

public interface Context {
	
	/**
	 * @brief read from the socket channel (bbin should be in write mode before and after)
	 * @throws IOException when read throws it
	 */
	void doRead()    throws IOException;
	
	/**
	 * @brief write to the socket channel (bbout should be in write mode before and after)
	 * @throws IOException when write throws it
	 */
	void doWrite()   throws IOException;
	
	/**
	 * @brief proceed the socket channel connexion
	 * @throws IOException when the keys updates throws it
	 */
	void doConnect() throws IOException;
}