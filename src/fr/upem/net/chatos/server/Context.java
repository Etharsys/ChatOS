package fr.upem.net.chatos.server;

import java.io.IOException;

interface Context {
		
		/**
		 * @brief read from the socket channel (bbin should be in write mode before and after)
		 * @throws IOException when read throws it
		 */
		void doRead()  throws IOException;
		
		/**
		 * @brief write to the socket channel (bbout should be in write mode before and after)
		 * @throws IOException when write throws it
		 */
		void doWrite() throws IOException;
		
		/**
		 * @brief Close a context
		 */
		void silentlyClose();
}