package fr.upem.net.chatos;

import fr.upem.net.chatos.ClientChatOs.Context;
import reader.ConnectionRequestReader;
import reader.DatagramVisitor;
import reader.ErrorCodeReader;
import reader.SendMessageAllReader;
import reader.SendPrivateMessageReader;

public class ClientDatagramVisitor implements DatagramVisitor<ClientChatOs.Context>{

	@Override
	public void visit(ConnectionRequestReader reader, Context context) {
		//On ne devrait jamais arriver ici, on lit le paquet mais on l'ignore
		//Do nothing
	}

	@Override
	public void visit(SendPrivateMessageReader reader, Context context) {
		// TODO Auto-generated method stub
		var msg = reader.get();
		System.out.println(msg.getSender() + " says to you " + msg.getMessage());
	}

	@Override
	public void visit(SendMessageAllReader reader, Context context) {
		// TODO Auto-generated method stub
		var msg = reader.get();
		System.out.println(msg.getSender() + " says to all " + msg.getMessage());
	}

	@Override
	public void visit(ErrorCodeReader reader, Context context) {
		// TODO Auto-generated method stub
		System.out.println("Received an error " + reader.get());
	}

}
