package fr.upem.net.chatos.reader;

import fr.upem.net.chatos.frame.TCPAsk;

public class TCPAskReader extends AbstractTCPFrameReader<TCPAsk> {
	@Override
	public TCPAsk get() {
		return new TCPAsk(super.getSender(), super.getRecipient(), super.getPassword());
	}

	@Override
	public <T> void accept(FrameVisitor<T> visitor, T context) {
		visitor.visit(this, context);
	}
}
