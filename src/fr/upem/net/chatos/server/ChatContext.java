package fr.upem.net.chatos.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.Datagram;
import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAbort;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.reader.ConnectionRequestReader;
import fr.upem.net.chatos.reader.DatagramVisitor;
import fr.upem.net.chatos.reader.ErrorCodeReader;
import fr.upem.net.chatos.reader.OpCodeReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;
import fr.upem.net.chatos.reader.SendMessageAllReader;
import fr.upem.net.chatos.reader.SendPrivateMessageReader;
import fr.upem.net.chatos.reader.TCPAbortReader;
import fr.upem.net.chatos.reader.TCPAcceptReader;
import fr.upem.net.chatos.reader.TCPAskReader;
import fr.upem.net.chatos.reader.TCPConnectReader;

class ChatContext implements Context {
	static final private int BUFFER_SIZE = 1024;
	static private Logger logger = Logger.getLogger(ChatContext.class.getName());

    final private SelectionKey    key;
    final private SocketChannel   sc;
    final private ByteBuffer      bbin  = ByteBuffer.allocate(BUFFER_SIZE);
    final private ByteBuffer      bbout = ByteBuffer.allocate(BUFFER_SIZE);
    final private Queue<Datagram> queue = new LinkedList<>();
    final private ChatOsServer    server;

    final private DatagramVisitor<ChatContext> visitor = new DatagramVisitor<ChatContext>(){

    	@Override
    	public void visit(ConnectionRequestReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received ConnectionRequest");
    		context.queueFrame(new ErrorCode(ErrorCode.ALREADY_CONNECTED));
    	}

    	@Override
    	public void visit(SendPrivateMessageReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received PrivateMessage");
    		context.broadcast(reader.get());
    	}

    	@Override
    	public void visit(SendMessageAllReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received MessageAll");
    		context.broadcast(reader.get());
    	}

    	@Override
    	public void visit(ErrorCodeReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received ErrorCode");
    		//Do nothing
    	}

    	@Override
    	public void visit(TCPAskReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received TCPAsk");
    		context.broadcast(reader.get());
    	}

    	@Override
    	public void visit(TCPAbortReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received TCPAbort");
    		context.broadcast(reader.get());
    	}

    	@Override
    	public void visit(TCPConnectReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received TCPConnect");
    		context.queueFrame(new ErrorCode(ErrorCode.TCP_NOT_IN_PROTOCOLE));
    	}

    	@Override
    	public void visit(TCPAcceptReader reader, ChatContext context) {
    		Objects.requireNonNull(reader);
    		Objects.requireNonNull(context);
    		logger.info("Received TCPAccept");
    		context.queueFrame(new ErrorCode(ErrorCode.TCP_NOT_IN_PROTOCOLE));
    	}
    };

    final private OpCodeReader reader  = new OpCodeReader();

    private final String login;

    private boolean closed;

    /**
     * ChatContext constructor
     * @param server the Chat server
     * @param key the selected key to attach to this context (server)
     */
    public ChatContext(ChatOsServer server, SelectionKey key, String login, ByteBuffer buffer){
    	Objects.requireNonNull(server);
    	Objects.requireNonNull(key);
    	Objects.requireNonNull(login);
    	Objects.requireNonNull(buffer);
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
        this.login = login;
        this.bbin.put(buffer);
        key.attach(this);
    }

    /**
     * 
     * @return the login of the context
     */
    public String getLogin() {
		return login;
	}

    /**
     * @brief Add a frame to the queue
     * @param frame the command to add
     */
    public void queueFrame(Datagram frame) {
    	Objects.requireNonNull(frame);
    	queue.add(frame);
    	updateInterestOps();
    }

    /**
     * @brief broadcast a message to every client connected Send back Invalid Pseudonym if the sender is not associated with this context
     * @param message the message to broadcast
     */
    public void broadcast(MessageAll message) {
    	Objects.requireNonNull(message);
    	if (!message.getSender().equals(login)) {
    		queueFrame(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
    		return;
    	}
    	server.broadcast(message, key);
    	queueFrame(new ErrorCode(ErrorCode.OK));
    }

    /**
     * @brief broadcast a message to a recipient if it is connected
     * send an ERROR packet to the client "OK" if the recipient is connected and
     * Unreachable User otherwise
     * Send back Invalid Pseudonym if the sender is not associated with this context
     * @param message the message to broadcast
     */
    public void broadcast(PrivateMessage message) {
    	Objects.requireNonNull(message);
    	if (!message.getSender().equals(login)) {
    		queueFrame(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
    		return;
    	}
		queueFrame(new ErrorCode(server.broadcast(message)));
    }

    /**
     * @brief broadcast a TCP private connexion ask request to a recipient
     * @param message the message to broadcast
     */
    public void broadcast(TCPAsk message) {
    	Objects.requireNonNull(message);
    	if (!message.getSender().equals(login)) {
    		queueFrame(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
    		queueFrame(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
    		return;
    	}
    	var code = server.broadcast(message);
    	queueFrame(new ErrorCode(code));
    	if (code != ErrorCode.OK) {
    		queueFrame(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
    	}
    }

    /**
     * @brief broadcast a abortion of the TCP private connexion request to a recipient
     * @param message the message to broadcast
     */
    public void broadcast(TCPAbort message) {
    	Objects.requireNonNull(message);
    	if (!message.getRecipient().equals(login)) {
    		queueFrame(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
    		return;
    	}
    	queueFrame(new ErrorCode(server.broadcast(message)));
    }

    /**
     * 
     * @brief close the context
     */
    public void closeContext() {
    	closed = true;
    }

    /**
     * 
	 * @brief update the interestOps of the key
	 */
    private void updateInterestOps() {
    	int intOps = 0;
    	if (!closed && bbin.hasRemaining()) {
    		intOps |= SelectionKey.OP_READ;
    	}
    	if (bbout.position() > 0 || queue.size() != 0) {
    		intOps |= SelectionKey.OP_WRITE;
    	}
    	if (intOps == 0) {
    		silentlyClose();
    		return;
    	}
    	key.interestOps(intOps);
    }

    /**
     * 
	 * @brief silently close the socket channel
	 */
    @Override
    public void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
        
    /**
     * @brief Performs the read action on sc
     *
     * The convention is that both buffers are in write-mode before the call
     * to doRead and after the call
     *
     * @throws IOException
     */
    @Override
    public void doRead() throws IOException {
    	if (sc.read(bbin) == -1) {
    		closed = true;
    	}
		processIn();
    	updateInterestOps();
    }
        
    /**
     * @brief Process the content of bbin
     *
     * The convention is that bbin is in write-mode before the call
     * to process and after the call
     *
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
     * @brief Try to fill bbout from the queue
     *
     */
    private void processOut() {
		while (!queue.isEmpty()) {
			var datagram = queue.peek();
			var optBB = datagram.toByteBuffer(logger);
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
     * @brief Performs the write action on sc
     *
     * The convention is that both buffers are in write-mode before the call
     * to doWrite and after the call
     *
     * @throws IOException
     */
    @Override
    public void doWrite() throws IOException {
    	processOut();
    	bbout.flip();
    	sc.write(bbout);
    	bbout.compact();
    	updateInterestOps();
    }
}
