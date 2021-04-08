package fr.upem.net.chatos;

import fr.upem.net.chatos.ClientChatOs.ChatContext;
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
	public void visit(TCPDeniedReader reader, ChatContext context) {
		// TODO Auto-generated method stub
		TCPAbort tcpDenied = reader.get();
		System.out.println("Received a TCPDenied with the arguments : ");
		System.out.println("Sender : " + tcpDenied.getSender());
		System.out.println("Recipient : " + tcpDenied.getRecipient());
		System.out.println("Password : " + tcpDenied.getPassword());
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
