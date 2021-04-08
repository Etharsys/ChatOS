package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.datagram.TCPAsk;

public class TCPAskReader extends AbstractTCPDatagramReader<TCPAsk> {
	@Override
	public TCPAsk get() {
		return new TCPAsk(super.getSender(), super.getRecipient(), super.getPassword());
	}

	@Override
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}
}
