package fr.upem.net.chatos.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.reader.OpCodeReader;

class TCPContextWaiter implements TCPContext{
	private final String recipient;
	private final ChatOsClient client;
	
	private final SelectionKey contextKey;
	private final SocketChannel socket;
	private final ByteBuffer bbin = ByteBuffer.allocate(2);
	private final ByteBuffer bbout;
	private final Queue<String> commandQueue = new LinkedList<>();
	private final Queue<String> targetQueue  = new LinkedList<>();
	
	private boolean closed;
	
	/**
	 * TCPContextWaiter constructor
	 * @param contextKey the original context key
	 * @param socket the original socket channel
	 * @param recipient the pseudonym of the TCP private connexion recipient
	 * @param buffer the buffer of the TCPDatagram request
	 */
	public TCPContextWaiter(SelectionKey contextKey, SocketChannel socket, String recipient, ByteBuffer buffer,ChatOsClient client) {
		Objects.requireNonNull(contextKey);
		Objects.requireNonNull(socket);
		Objects.requireNonNull(recipient);
		Objects.requireNonNull(client);
		this.contextKey = contextKey;
		this.socket = socket;
		this.recipient = recipient;
		this.client = client;
		bbout = buffer;
	}
	
	public void launch() {
		contextKey.interestOps(SelectionKey.OP_CONNECT);
	}
	
	/**
	 * update the interestOps of the key
	 * @brief
	 */
	private void updateInterestOps() {
    	if (closed) {
    		silentlyClose();
    		return;
    	}
    	if (bbout.hasRemaining()) {
    		contextKey.interestOps(SelectionKey.OP_WRITE);
    	} else {
    		contextKey.interestOps(SelectionKey.OP_READ);
    	}
    }

	/**
	 * @brief add a command to the commands queue
	 * @param bb the command to add
	 */
	private void processIn() {
		if (!bbin.hasRemaining()) {
			bbin.flip();
			if (bbin.get() != OpCodeReader.ERROR_PACKET_CODE) {
				System.out.println("Didn't receive ErrorCode");
			}
			var err = new ErrorCode(bbin.get());
			System.out.println("Received " + err);
			if (err.getErrorCode() != ErrorCode.OK) {
				closed = true;
			} else {
				var context = new TCPHTTPContext(contextKey,socket,commandQueue, targetQueue, client, recipient);
				contextKey.attach(context);
				client.putContextInContextMap(recipient, context);
				System.out.println("Connection TCP with " + recipient + " enabled");
			}
		}
	}
	
	/**
	 * @brief silently close the socket channel
	 */
	private void silentlyClose() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
	
	@Override
	public void doRead() throws IOException {
		if (socket.read(bbin) == -1) {
			closed = true;
    		return;
    	}
		updateInterestOps();
		processIn();
	}

	@Override
	public void doConnect() throws IOException {
    	if (!socket.finishConnect()) {
    		return;
    	}
    	contextKey.interestOps(SelectionKey.OP_WRITE);
    }

	@Override
	public void doWrite() throws IOException {
		if (socket.write(bbout) == -1) {
			closed = true;
			return;
		}
		updateInterestOps();
	}

	@Override
	public void queueCommand(String command, String target) {
		commandQueue.add(command);
		targetQueue.add(target);
	}

	@Override
	public void close() {
		closed = true;
		silentlyClose();
	}
}