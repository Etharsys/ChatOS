package reader;

import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPDenied;

public class TCPDeniedReader extends AbstractTCPDatagramReader<TCPDenied> {
	@Override
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPDenied get() {
		return new TCPDenied(super.getSender(), super.getRecipient(), super.getPassword());
	}
}
