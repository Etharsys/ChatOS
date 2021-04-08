package server;

import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPAbort;
import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;
import reader.TCPAcceptReader;
import reader.TCPAskReader;
import reader.TCPConnectReader;
import reader.TCPDeniedReader;
import server.ChatOsServer.ChatContext;

public class ServerDatagramVisitor implements DatagramVisitor<ChatContext> {
	@Override
	public void visit(ConnectionRequestReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		System.out.println("Server received ConnectionRequest with the login : " + reader.get());
		context.requestPseudonym(reader.get());
	}

	@Override
	public void visit(SendPrivateMessageReader reader, ChatContext context) {
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
	public void visit(SendMessageAllReader reader, ChatContext context) {
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
	public void visit(ErrorCodeReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		System.out.println("Received an error with op code : " + reader.get());
	}

	@Override
	public void visit(TCPAskReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		TCPAsk tcpAsk = reader.get();
		System.out.println("Received a TCPAsk with the arguments : ");
		System.out.println("Sender : " + tcpAsk.getSender());
		System.out.println("Recipient : " + tcpAsk.getRecipient());
		System.out.println("Password : " + tcpAsk.getPassword());
		context.broadcast(tcpAsk);
	}
	
	@Override
	public void visit(TCPDeniedReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		if (!context.isConnected()) {
			context.closeContext();
		}
		TCPAbort tcpDenied = reader.get();
		System.out.println("Received a TCPDenied with the arguments : ");
		System.out.println("Sender : " + tcpDenied.getSender());
		System.out.println("Recipient : " + tcpDenied.getRecipient());
		System.out.println("Password : " + tcpDenied.getPassword());
	}

	@Override
	public void visit(TCPConnectReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		TCPConnect tcpConnect = reader.get();
		System.out.println("Received a TCPConnect with the arguments : ");
		System.out.println("Sender : " + tcpConnect.getSender());
		System.out.println("Recipient : " + tcpConnect.getRecipient());
		System.out.println("Password : " + tcpConnect.getPassword());
		context.broadcast(tcpConnect);
	}

	@Override
	public void visit(TCPAcceptReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		TCPAccept tcpAccept = reader.get();
		System.out.println("Received a TCPAccept with the arguments : ");
		System.out.println("Sender : " + tcpAccept.getSender());
		System.out.println("Recipient : " + tcpAccept.getRecipient());
		System.out.println("Password : " + tcpAccept.getPassword());
		context.broadcast(tcpAccept);
	}
}
