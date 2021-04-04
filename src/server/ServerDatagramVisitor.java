package server;

import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;
import server.ChatOsServer.Context;

public class ServerDatagramVisitor implements DatagramVisitor<Context> {

	@Override
	public void visit(ConnectionRequestReader reader, Context context) {
		// TODO Auto-generated method stub
		System.out.println("Server received ConnectionRequest with the login : " + reader.get());
	}

	@Override
	public void visit(SendPrivateMessageReader reader, Context context) {
		// TODO Auto-generated method stub
		var msg = reader.get();
		System.out.println("Server received PrivateMessage with the login : " );
		System.out.println("from : " + msg.getSender());
		System.out.println("to : " + msg.getRecipient().get());
		System.out.println(msg.getMessage());
	}

	@Override
	public void visit(SendMessageAllReader reader, Context context) {
		// TODO Auto-generated method stub
		var msg = reader.get();
		System.out.println("Server received Message to All with the login : " );
		System.out.println("from : " + msg.getSender());
		System.out.println(msg.getMessage());
	}

	@Override
	public void visit(ErrorCodeReader reader, Context context) {
		// TODO Auto-generated method stub
		System.out.println("Received an error with op code : " + reader.get());
	}

}
