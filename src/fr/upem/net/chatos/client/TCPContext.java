package fr.upem.net.chatos.client;

public interface TCPContext extends Context{
	void queueCommand(String command);
	
	void close();
}
