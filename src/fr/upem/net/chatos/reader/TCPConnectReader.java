package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.frame.TCPConnect;

public class TCPConnectReader extends AbstractTCPFrameReader<TCPConnect> {
	@Override
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPConnect get() {
		return new TCPConnect(super.getSender(), super.getRecipient(), super.getPassword());
	}
}
