package fr.upem.net.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Optional;

class TCPContext implements Context {
	static final private int BUFFER_SIZE = 1024;

	private Optional<TCPContext> pairedContext = Optional.empty();

	private final SelectionKey  tcpContextKey;
	private final SocketChannel socketChannel;
	private final ByteBuffer    bbout = ByteBuffer.allocate(BUFFER_SIZE);

	private boolean closed;

	/**
	 * TCPContext constructor
	 * @param tcpContextKey the selected key to attach to this context
	 * @param socketChannel the original socket channel
	 */
    public TCPContext(SelectionKey tcpContextKey, SocketChannel socketChannel, ByteBuffer buff) {
		Objects.requireNonNull(tcpContextKey);
		Objects.requireNonNull(socketChannel);
		Objects.requireNonNull(buff);
		this.tcpContextKey = tcpContextKey;
		this.socketChannel = socketChannel;
		tcpContextKey.attach(this);
		bbout.put(buff);
	}

    /**
     * 
	 * @brief update the interestOps of the key
	 */
    private void updateInterests() {
    	updateInterestOps();
    	pairedContext.get().updateInterestOps();
    }

    /**	
     * 
	 * @brief update the interestOps of the key
	 */
    private void updateInterestOps() {
    	int intOps = 0;
    	if (!closed && pairedContext.get().bbout.hasRemaining()) {
    		intOps |= SelectionKey.OP_READ;
    	}
    	if (bbout.position() != 0){
    		intOps |= SelectionKey.OP_WRITE;
    	}
    	if (intOps == 0) {
    		silentlyClose();
    		pairedContext.get().closed = true;
    		pairedContext.get().silentlyClose();
    		return;
    	}
    	tcpContextKey.interestOps(intOps);
    }

    /**
     * 
     * @brief set a link of the TCP private connexion between this context and an other
     * @param pairedContext the second context to link
     */
    public void setPairedContext(TCPContext pairedContext) {
    	Objects.requireNonNull(pairedContext);
    	if (this.pairedContext.isPresent()) {
    		throw new IllegalStateException("Already paired");
    	}
		this.pairedContext = Optional.of(pairedContext);
		updateInterestOps();
	}

    /**
     * 
	 * @brief silently close the socket channel
	 */
    @Override
    public void silentlyClose() {
        close();
        pairedContext.ifPresent(c->c.close());
    }
    
    private void close() {
    	try {
            socketChannel.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

	@Override
	public void doRead() throws IOException {
		if (socketChannel.read(pairedContext.get().bbout) == -1) {
			closed = true;
			return;
		}
		updateInterests();
	}

	@Override
	public void doWrite() throws IOException {
		bbout.flip();
		if (socketChannel.write(bbout) == -1) {
			closed = true;
			return;
		}
		bbout.compact();
		updateInterests();
	}
}