package fr.upem.net.chatos.client;

public interface TCPPContext extends Context{
	void queueCommand(String command);
	
	void close();
}
