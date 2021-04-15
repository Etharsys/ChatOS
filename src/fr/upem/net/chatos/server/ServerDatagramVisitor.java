package fr.upem.net.chatos.server;

import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.reader.ConnectionRequestReader;
import fr.upem.net.chatos.reader.DatagramVisitor;
import fr.upem.net.chatos.reader.ErrorCodeReader;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;
import fr.upem.net.chatos.server.ChatOsServer.ChatContext;
import fr.upem.net.chatos.datagram.TCPAbort;

public class ServerDatagramVisitor implements DatagramVisitor<ChatContext> {
	
	@Override
	public void visit(ConnectionRequestReader reader, ChatContext context) {
		System.out.println("Server received ConnectionRequest with the login : " + reader.get());
		//TODO nothing (renvoyer ALREADYCONNECTED)
	}

	@Override
	public void visit(SendPrivateMessageReader reader, ChatContext context) {
		var message = reader.get();
		System.out.println("Server received PrivateMessage with the login : " );
		System.out.println("from : " + message.getSender());
		System.out.println("to   : " + message.getRecipient());
		System.out.println(message.getMessage());
		context.broadcast(message);
	}

	@Override
	public void visit(SendMessageAllReader reader, ChatContext context) {
		var message = reader.get();
		System.out.println("Server received Message to All with the login : " );
		System.out.println("from : " + message.getSender());
		System.out.println(message.getMessage());
		context.broadcast(message);
	}

	@Override
	public void visit(ErrorCodeReader reader, ChatContext context) {
		System.out.println("Received an error with op code : " + reader.get());
	}

	@Override
	public void visit(TCPAskReader reader, ChatContext context) {
		TCPAsk tcpAsk = reader.get();
		System.out.println("Received a TCPAsk with the arguments : ");
		System.out.println("Sender    : " + tcpAsk.getSender());
		System.out.println("Recipient : " + tcpAsk.getRecipient());
		System.out.println("Password  : " + tcpAsk.getPassword());
		context.broadcast(tcpAsk);
	}
	
	@Override
	public void visit(TCPAbortReader reader, ChatContext context) {
		TCPAbort tcpAbort = reader.get();
		System.out.println("Received a TCPAbort with the arguments : ");
		System.out.println("Sender    : " + tcpAbort.getSender());
		System.out.println("Recipient : " + tcpAbort.getRecipient());
		System.out.println("Password  : " + tcpAbort.getPassword());
		context.broadcast(tcpAbort);
	}

	@Override
	public void visit(TCPConnectReader reader, ChatContext context) {
		TCPConnect tcpConnect = reader.get();
		System.out.println("Received a TCPConnect with the arguments : ");
		System.out.println("Sender    : " + tcpConnect.getSender());
		System.out.println("Recipient : " + tcpConnect.getRecipient());
		System.out.println("Password  : " + tcpConnect.getPassword());
		//Do nothing
	}

	@Override
	public void visit(TCPAcceptReader reader, ChatContext context) {
		TCPAccept tcpAccept = reader.get();
		System.out.println("Received a TCPAccept with the arguments : ");
		System.out.println("Sender    : " + tcpAccept.getSender());
		System.out.println("Recipient : " + tcpAccept.getRecipient());
		System.out.println("Password  : " + tcpAccept.getPassword());
		//do nothin return error?
	}
}
