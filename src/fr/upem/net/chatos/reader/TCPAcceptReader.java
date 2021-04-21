package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.frame.TCPAccept;

public class TCPAcceptReader extends AbstractTCPFrameReader<TCPAccept> {

	@Override
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPAccept get() {
		return new TCPAccept(super.getSender(), super.getRecipient(), super.getPassword());
	}

}
