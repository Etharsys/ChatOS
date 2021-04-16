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
import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPConnect;
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

class WaitingContext implements Context {
	static final private int BUFFER_SIZE = 1024;
	static private Logger logger = Logger.getLogger(WaitingContext.class.getName());
	
	final private SelectionKey    key;
    final private SocketChannel   sc;
    final private ByteBuffer      bbin  = ByteBuffer.allocate(BUFFER_SIZE);
    final private ByteBuffer      bbout = ByteBuffer.allocate(BUFFER_SIZE);
    final private Queue<Datagram> queue = new LinkedList<>();
    final private ChatOsServer    server;
        
    private boolean closed;
    
    final private OpCodeReader reader  = new OpCodeReader();
    
    //TODO Ca me parrait pas si mal au final mais c'est pas le meilleur truc je pense
    private boolean done;
        
    public WaitingContext(ChatOsServer server, SelectionKey key){
    	Objects.requireNonNull(server);
    	Objects.requireNonNull(key);
        this.key = key;
        this.sc = (SocketChannel) key.channel();
        this.server = server;
    }
        
       
    final private DatagramVisitor<WaitingContext> visitor = new DatagramVisitor<WaitingContext>() {
		@Override
		public void visit(ConnectionRequestReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			requestPseudonym(reader.get());
		}
			
		@Override
		public void visit(SendPrivateMessageReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			queueError(ErrorCode.NOT_CONNECTED);
		}

		@Override
		public void visit(SendMessageAllReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			queueError(ErrorCode.NOT_CONNECTED);
		}

		@Override
		public void visit(ErrorCodeReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			queueError(ErrorCode.NOT_CONNECTED);
		}

		@Override
		public void visit(TCPAskReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			queueError(ErrorCode.NOT_CONNECTED);
		}

		@Override
		public void visit(TCPAbortReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			queueError(ErrorCode.NOT_CONNECTED);
		}

		@Override
		public void visit(TCPConnectReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			computeTCPConnect(reader.get());
		}

		@Override
		public void visit(TCPAcceptReader reader, WaitingContext context) {
			Objects.requireNonNull(reader);
			Objects.requireNonNull(context);
			computeTCPAccept(reader.get());
		}
        	
    };

    /**
     * Test whether or not the error is OK, if it is mark this context as done, relay the error otherwise
     * @param error the error to test
     */
    private void computeTCPFrameAnswer(byte error) {
    	if (error == ErrorCode.OK) {
    		done = true;
    	} else {
    		queueError(error);
    	}
    }

    /**
     * Compute what to do with a TCPAccept message
     * @param message the message to compute
     */
    private void computeTCPAccept(TCPAccept message) {
    	computeTCPFrameAnswer(server.tryTCPAccept(message, key, sc));
    }

    /**
     * Compute what to do with a TCPConnect message
     * @param message the message to compute
     */
    private void computeTCPConnect(TCPConnect message) {
    	computeTCPFrameAnswer(server.tryTCPConnect(message, key, sc));
    }

    /**
     * Add an Error to the queue using the given error
     * @param error the error to queue
     */
    private void queueError(byte error) {
    	queue.add(new ErrorCode(error));
    }

    /**
     * Test if a pseudonym is available and add a new context using it if it is
     * @param pseudo the pseudonym to add
     */
    private void requestPseudonym(String pseudo) {
    	if (server.isAvailable(pseudo)) {
    		var context = new ChatContext(server, key, pseudo, bbin.flip());
    		context.queueFrame(new ErrorCode(ErrorCode.OK));
    		server.addChatContext(pseudo, context);
    		key.attach(context);
    		done = true;
    	} else {
    		queueError(ErrorCode.PSEUDO_UNAVAILABLE);
    	}
    }

    /**
     * 
	 * @brief update the interestOps of the key
	 */
    private void updateInterestOps() {
    	if (done) {
    		return;
    	}
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
    	var ps = reader.process(bbin);
		if (ps != ProcessStatus.REFILL) {
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

	/**
     * 
	 * @brief silently close the socket channel
	 */
    private void silentlyClose() {
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
}