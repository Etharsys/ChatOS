package fr.upem.net.chatos.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;

import fr.upem.net.chatos.frame.ErrorCode;
import fr.upem.net.chatos.frame.Frame;
import fr.upem.net.chatos.frame.TCPAbort;
import fr.upem.net.chatos.frame.TCPAccept;
import fr.upem.net.chatos.frame.TCPAsk;
import fr.upem.net.chatos.reader.ConnectionRequestReader;
import fr.upem.net.chatos.reader.FrameVisitor;
import fr.upem.net.chatos.reader.ErrorCodeReader;
import fr.upem.net.chatos.reader.OpCodeReader;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

class ChatContext implements Context {
	static private int MAX_STRING_SIZE = 1_024;
	static private Logger logger = Logger.getLogger(ChatContext.class.getName());

	private final SelectionKey key;
	private final SocketChannel sc;
	private final ChatOsClient client;

	private final int BUFFER_MAX_SIZE = (MAX_STRING_SIZE + Short.BYTES) * 3 + 1;

	private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_MAX_SIZE);
	private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_MAX_SIZE);

	private final Queue<Frame> queue = new LinkedList<>();
	private final OpCodeReader reader = new OpCodeReader();
	private final FrameVisitor<ChatContext> visitor = new FrameVisitor<>() {

		@Override
		public void visit(ConnectionRequestReader reader, ChatContext context) {
			// On ne devrait jamais arriver ici, on lit le paquet mais on l'ignore
			// Do nothing
		}

		@Override
		public void visit(SendPrivateMessageReader reader, ChatContext context) {
			var msg = reader.get();
			System.out.println(msg.getSender() + " says to you : " + msg.getMessage());
		}

		@Override
		public void visit(SendMessageAllReader reader, ChatContext context) {
			var msg = reader.get();
			System.out.println(msg.getSender() + " says to all : " + msg.getMessage());
		}

		@Override
		public void visit(ErrorCodeReader reader, ChatContext context) {
			System.out.println("Received an Error from the server : ");
			switch (reader.get().getErrorCode()) {
			case ErrorCode.ALREADY_CONNECTED:
				System.out.println("ALREADY_CONNECTED");
				break;
			case ErrorCode.INVALID_PSEUDONYM:
				System.out.println("INVALID_PSEUDONYM");
				break;
			case ErrorCode.NOT_CONNECTED:
				System.out.println("NOT_CONNECTED");
				break;
			case ErrorCode.OK:
				System.out.println("OK");
				break;
			case ErrorCode.PSEUDO_UNAVAILABLE:
				System.out.println("PSEUDO_UNAVAILABLE");
				break;
			case ErrorCode.TCP_IN_PROTOCOLE:
				System.out.println("TCP_IN_PROTOCOLE");
				break;
			case ErrorCode.TCP_NOT_IN_PROTOCOLE:
				System.out.println("TCP_NOT_IN_PROTOCOLE");
				break;
			case ErrorCode.UNREACHABLE_USER:
				System.out.println("UNREACHABLE_USER");
				break;
			default:
				System.out.println("UNKNOWN");
				break;
			}
		}

		@Override
		public void visit(TCPAskReader reader, ChatContext context) {
			context.treatTCPAsk(reader.get());
		}

		@Override
		public void visit(TCPAbortReader reader, ChatContext context) {
			context.treatTCPAbort(reader.get());
		}

		@Override
		public void visit(TCPConnectReader reader, ChatContext context) {
			// On ne devrait jamais arriver ici, on lit le paquet mais on l'ignore
			// Do nothing
		}

		@Override
		public void visit(TCPAcceptReader reader, ChatContext context) {
			context.treatTCPAccept(reader.get());
		}
	};

	private boolean closed = false;

	/**
	 * ChatContext contructor
	 * 
	 * @param key the selected key to attach to this context (client)
	 */
	public ChatContext(SelectionKey key, ChatOsClient client) {
		this.key = key;
		this.sc = (SocketChannel) key.channel();
		this.client = client;
	}

	/**
	 * @brief process the content of bbin
	 */
	private void processIn() {
		for (var ps = reader.process(bbin); ps != ProcessStatus.REFILL; ps = reader.process(bbin)) {
			if (ps == ProcessStatus.ERROR) {
				silentlyClose();
				return;
			} else {
				reader.accept(visitor, this);
				reader.reset();
			}
		}
	}

	/**
	 * @brief add a command to the commands queue
	 * @param frame the command to add
	 */
	public void queueCommand(Frame frame) {
		queue.add(frame);
		processOut();
		updateInterestOps();
	}

	/**
	 * @brief process the content of bbout
	 */
	private void processOut() {
		while (!queue.isEmpty()) {
			var frame = queue.peek();
			var optBB = frame.toByteBuffer(logger);
			if (optBB.isEmpty()) {
				queue.remove();
			}
			var bb = optBB.get();
			if (bb.remaining() <= bbout.remaining()) {
				queue.remove();
				bbout.put(bb);
			} else {
				break;
			}
		}
	}

	/**
	 * @brief update the interestOps of the key
	 */
	private void updateInterestOps() {
		var interesOps = 0;
		if (!closed && bbin.hasRemaining()) {
			interesOps |= SelectionKey.OP_READ;
		}
		if (bbout.position() != 0) {
			interesOps |= SelectionKey.OP_WRITE;
		}
		if (interesOps == 0) {
			silentlyClose();
			return;
		}
		key.interestOps(interesOps);
	}

	/**
	 * @brief silently close the socket channel
	 */
	private void silentlyClose() {
		try {
			sc.close();
		} catch (IOException ioe) {
			// ignore exception
		}
	}

	@Override
	public void doRead() throws IOException {
		if (sc.read(bbin) == -1) {
			closed = true;
		}
		processIn();
		updateInterestOps();
	}

	@Override
	public void doWrite() throws IOException {
		bbout.flip();
		sc.write(bbout);
		bbout.compact();
		processOut();
		updateInterestOps();
	}

	@Override
	public void doConnect() throws IOException {
		// Impossible
		key.interestOps(SelectionKey.OP_READ);
	}

	/**
	 * 
	 * @brief treat the specific request TCPAsk
	 * @param tcpAsk the frame which represent the request
	 */
	public void treatTCPAsk(TCPAsk tcpAsk) {
		Objects.requireNonNull(tcpAsk);
		client.treatTCPAsk(tcpAsk);
	}

	/**
	 * 
	 * @brief treat the specific request TCPAccept
	 * @param tcpAccept the frame which represent the request
	 */
	public void treatTCPAccept(TCPAccept tcpAccept) {
		Objects.requireNonNull(tcpAccept);
		client.treatTCPAccept(tcpAccept);
	}

	/**
	 * 
	 * @brief treat the specific request TCPAbort
	 * @param tcpAbort the frame which represent the request
	 */
	public void treatTCPAbort(TCPAbort tcpAbort) {
		Objects.requireNonNull(tcpAbort);
		client.treatTCPAbort(tcpAbort);
	}
}