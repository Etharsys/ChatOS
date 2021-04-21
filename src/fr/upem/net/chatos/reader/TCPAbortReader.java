package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.datagram.TCPAbort;

public class TCPAbortReader extends AbstractTCPDatagramReader<TCPAbort> {
	@Override
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}

	@Override
	public TCPAbort get() {
		return new TCPAbort(super.getSender(), super.getRecipient(), super.getPassword());
	}
}
