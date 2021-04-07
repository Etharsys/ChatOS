package server;

import fr.upem.net.chatos.datagram.TCPAsk;
import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;
import reader.TCPAskReader;
import server.ChatOsServer.Context;

public class ServerDatagramVisitor implements DatagramVisitor<Context> {
	@Override
	public void visit(ConnectionRequestReader reader, Context context) {
		// TODO Auto-generated method stub
		System.out.println("Server received ConnectionRequest with the login : " + reader.get());
		context.requestPseudonym(reader.get());
	}

	@Override
	public void visit(SendPrivateMessageReader reader, Context context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		var message = reader.get();
		System.out.println("Server received PrivateMessage with the login : " );
		System.out.println("from : " + message.getSender());
		System.out.println("to : " + message.getRecipient());
		System.out.println(message.getMessage());
		context.broadcast(message);
	}

	@Override
	public void visit(SendMessageAllReader reader, Context context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		var message = reader.get();
		System.out.println("Server received Message to All with the login : " );
		System.out.println("from : " + message.getSender());
		System.out.println(message.getMessage());
		context.broadcast(message);
	}

	@Override
	public void visit(ErrorCodeReader reader, Context context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		System.out.println("Received an error with op code : " + reader.get());
	}

	@Override
	public void visit(TCPAskReader reader, Context context) {
		// TODO Auto-generated method stub
		TCPAsk tcpAsk = reader.get();
		System.out.println("Received a TCPAsk with the arguments : ");
		System.out.println("Sender : " + tcpAsk.getSender());
		System.out.println("Recipient : " + tcpAsk.getRecipient());
		System.out.println("Password : " + tcpAsk.getPassword());
	}
}
