package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.datagram.TCPConnect;

public class TCPConnectReader extends AbstractTCPDatagramReader<TCPConnect> {
	@Override
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPConnect get() {
		return new TCPConnect(super.getSender(), super.getRecipient(), super.getPassword());
	}
}
