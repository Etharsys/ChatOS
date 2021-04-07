package reader;

import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPConnect;

public class TCPAcceptReader extends AbstractTCPDatagramReader<TCPAccept> {

	@Override
	public <T> void accept(DatagramVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPAccept get() {
		return new TCPAccept(super.getSender(), super.getRecipient(), super.getPassword());
	}

}
