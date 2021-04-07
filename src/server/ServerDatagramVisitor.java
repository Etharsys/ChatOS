package server;

import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPDenied;
import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;
import reader.TCPAcceptReader;
import reader.TCPAskReader;
import reader.TCPConnectReader;
import reader.TCPDeniedReader;
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
	
	@Override
	public void visit(TCPDeniedReader reader, Context context) {
		// TODO Auto-generated method stub
		TCPDenied tcpDenied = reader.get();
		System.out.println("Received a TCPDenied with the arguments : ");
		System.out.println("Sender : " + tcpDenied.getSender());
		System.out.println("Recipient : " + tcpDenied.getRecipient());
		System.out.println("Password : " + tcpDenied.getPassword());
	}

	@Override
	public void visit(TCPConnectReader reader, Context context) {
		// TODO Auto-generated method stub
		TCPConnect tcpConnect = reader.get();
		System.out.println("Received a TCPDenied with the arguments : ");
		System.out.println("Sender : " + tcpConnect.getSender());
		System.out.println("Recipient : " + tcpConnect.getRecipient());
		System.out.println("Password : " + tcpConnect.getPassword());
	}

	@Override
	public void visit(TCPAcceptReader reader, Context context) {
		// TODO Auto-generated method stub
		TCPAccept tcpAccept = reader.get();
		System.out.println("Received a TCPDenied with the arguments : ");
		System.out.println("Sender : " + tcpAccept.getSender());
		System.out.println("Recipient : " + tcpAccept.getRecipient());
		System.out.println("Password : " + tcpAccept.getPassword());
	}
}
