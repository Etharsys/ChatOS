package fr.upem.net.chatos.client;

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
import fr.upem.net.chatos.client.ClientChatOs.ChatContext;
import fr.upem.net.chatos.datagram.TCPAbort;

public class ClientDatagramVisitor implements DatagramVisitor<ClientChatOs.ChatContext>{

	@Override
	public void visit(ConnectionRequestReader reader, ChatContext context) {
		//On ne devrait jamais arriver ici, on lit le paquet mais on l'ignore
		//Do nothing
	}

	@Override
	public void visit(SendPrivateMessageReader reader, ChatContext context) {
		var msg = reader.get();
		System.out.println(msg.getSender() + " says to you : " + msg.getMessage());
	}

	@Override
	public void visit(SendMessageAllReader reader, ChatContext context) {
		var msg = reader.get();
		System.out.println(msg.getSender() + " says to all : " + msg.getMessage());
	}

	@Override
	public void visit(ErrorCodeReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		System.out.println("Received an error " + reader.get());
	}

	@Override
	public void visit(TCPAskReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		TCPAsk tcpAsk = reader.get();
		System.out.println("Received a TCPAsk with the arguments : ");
		System.out.println("Sender : " + tcpAsk.getSender());
		System.out.println("Recipient : " + tcpAsk.getRecipient());
		System.out.println("Password : " + tcpAsk.getPassword());
		context.treatTCPAsk(tcpAsk);
	}

	@Override
	public void visit(TCPAbortReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		TCPAbort tcpAbort = reader.get();
		System.out.println("Received a TCPAbort with the arguments : ");
		System.out.println("Sender : " + tcpAbort.getSender());
		System.out.println("Recipient : " + tcpAbort.getRecipient());
		System.out.println("Password : " + tcpAbort.getPassword());
		context.treatTCPAbort(tcpAbort);
	}

	@Override
	public void visit(TCPConnectReader reader, ChatContext context) {
		//On ne devrait jamais arriver ici, on lit le paquet mais on l'ignore
		//Do nothing
		TCPConnect tcpConnect = reader.get();
		System.out.println("Received a TCPConnect with the arguments : ");
		System.out.println("Sender : " + tcpConnect.getSender());
		System.out.println("Recipient : " + tcpConnect.getRecipient());
		System.out.println("Password : " + tcpConnect.getPassword());
	}

	@Override
	public void visit(TCPAcceptReader reader, ChatContext context) {
		TCPAccept tcpAccept = reader.get();
		System.out.println("Received a TCPAccept with the arguments : ");
		System.out.println("Sender : " + tcpAccept.getSender());
		System.out.println("Recipient : " + tcpAccept.getRecipient());
		System.out.println("Password : " + tcpAccept.getPassword());
		context.treatTCPAccept(tcpAccept);
	}
}
