package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.datagram.TCPAccept;

public class TCPAcceptReader extends AbstractTCPDatagramReader<TCPAccept> {

	@Override
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPAccept get() {
		return new TCPAccept(super.getSender(), super.getRecipient(), super.getPassword());
	}

}
