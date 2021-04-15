package fr.upem.net.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.Datagram;
import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPDatagram;
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
import fr.upem.net.chatos.datagram.TCPAbort;


public class ChatOsServer {
	
	private interface Context {
		
		/**
		 * @brief read from the socket channel (bbin should be in write mode before and after)
		 * @throws IOException when read throws it
		 */
		void doRead()  throws IOException;
		
		/**
		 * @brief write to the socket channel (bbout should be in write mode before and after)
		 * @throws IOException when write throws it
		 */
		void doWrite() throws IOException;
	}
	
	private class WaitingContext implements Context {
		final private SelectionKey    key;
        final private SocketChannel   sc;
        final private ByteBuffer      bbin  = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer      bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<Datagram> queue = new LinkedList<>();
        final private ChatOsServer    server;
        
        private boolean closed;
        
        //TODO pas ouf comme méthode
        private boolean done;
        
        private WaitingContext(ChatOsServer server, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }
        
        final private OpCodeReader          reader  = new OpCodeReader();
        final private DatagramVisitor<WaitingContext> visitor = new DatagramVisitor<WaitingContext>() {
			@Override
			public void visit(ConnectionRequestReader reader, WaitingContext context) {
				// TODO Auto-generated method stub
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
				// TODO Auto-generated method stub
				computeTCPConnect(reader.get());
			}

			@Override
			public void visit(TCPAcceptReader reader, WaitingContext context) {
				Objects.requireNonNull(reader);
				Objects.requireNonNull(context);
				// TODO Auto-generated method stub
				computeTCPAccept(reader.get());
			}
        	
        };
        
        private void computeTCPDatagramAnswer(byte error) {
        	if (error == ErrorCode.OK) {
        		done = true;
        	} else {
        		queueError(error);
        	}
        }
        
        private void computeTCPAccept(TCPAccept message) {
        	computeTCPDatagramAnswer(tryTCPAccept(message, this));
        }
        
        private void computeTCPConnect(TCPConnect message) {
        	computeTCPDatagramAnswer(tryTCPConnect(message, this));
        }
        
        private void queueError(byte error) {
        	queue.add(new ErrorCode(error));
        }
        
        private void requestPseudonym(String pseudo) {
        	if (server.isAvailable(pseudo)) {
        		var context = new ChatContext(server, key);
        		context.queueDatagram(new ErrorCode(ErrorCode.OK));
        		server.addChatContext(pseudo, context);
        		done = true;
        	} else {
        		queueError(ErrorCode.PSEUDO_UNAVAILABLE);
        	}
        }
        
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
	
	public class ChatContext implements Context {
		
        final private SelectionKey    key;
        final private SocketChannel   sc;
        final private ByteBuffer      bbin  = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer      bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<Datagram> queue = new LinkedList<>();
        final private ChatOsServer    server;
        
        
        final private ServerDatagramVisitor visitor = new ServerDatagramVisitor();
        final private OpCodeReader          reader  = new OpCodeReader();
        
        private Optional<String> login = Optional.empty();
        
        private boolean closed;
        
        /**
         * ChatContext constructor
         * @param server the Chat server
         * @param key the selected key to attach to this context (server)
         */
        private ChatContext(ChatOsServer server, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }
        
        /**
         * @brief Add a datagram to the queue
         * @param datagram the command to add
         */
        private void queueDatagram(Datagram datagram) {
        	queue.add(datagram);
        	System.out.println("added : " + datagram);
        	login.ifPresent(s -> System.out.println("to : " + s));
        	updateInterestOps();
        }
        
        /**
         * @brief broadcast a message to every client connected Send back Invalid Pseudonym if the sender is not associated with this context
         * @param message the message to broadcast
         */
        public void broadcast(MessageAll message) {
        	if (!message.getSender().equals(login.get())) {
        		queueDatagram(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
        		return;
        	}
        	server.broadcast(message, this);
        	queueDatagram(new ErrorCode(ErrorCode.OK));
        }
        
        /**
         * @brief broadcast a message to a recipient if it is connected
         * send an ERROR packet to the client "OK" if the recipient is connected and
         * Unreachable User otherwise
         * Send back Invalid Pseudonym if the sender is not associated with this context
         * @param message the message to broadcast
         */
        public void broadcast(PrivateMessage message) {
        	if (!message.getSender().equals(login.get())) {
        		queueDatagram(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
        		return;
        	}
    		queueDatagram(new ErrorCode(server.broadcast(message)));
        }
        
        /**
         * @brief broadcast a TCP private connexion ask request to a recipient
         * @param message the message to broadcast
         */
        public void broadcast(TCPAsk message) {
        	if (!message.getSender().equals(login.get())) {
        		queueDatagram(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        		return;
        	}
        	var code = server.broadcast(message);
        	queueDatagram(new ErrorCode(code));
        	if (code != ErrorCode.OK) {
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        	}
        }
        
        /**
         * @brief broadcast an acceptation of the TCP private connexion request to a recipient
         * @param message the message to broadcast
         */
        public void broadcast(TCPAccept message) {
        	var code = server.broadcast(message, this);
        	if (code != ErrorCode.OK) {
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        	}
        }
        
        /**
         * @brief broadcast a abortion of the TCP private connexion request to a recipient
         * @param message the message to broadcast
         */
        public void broadcast(TCPAbort message) {
        	if (!message.getRecipient().equals(login.get())) {
        		queueDatagram(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
        		return;
        	}
        	queueDatagram(new ErrorCode(server.broadcast(message, this)));
        }
        
        /**
         * @brief broadcast a TCP private connexion request to a recipient
         * @param message the message to broadcast
         */
        public void broadcast(TCPConnect message) {
        	var code = server.broadcast(message, this);
        	if (code != ErrorCode.OK) {
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        	}
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
         * @brief check if a client is connected by checking the presence of his login
         * @return the presence or not of the login
         */
        public boolean isConnected() {
        	return login.isPresent();
        }
        
        /**
         * 
         * @brief check is a sender pseudonym is valid in a request
         * @param pseudo the sender pseudo to check
         */
        public void requestPseudonym(String pseudo) {
        	if (server.requestPseudonymAndAdd(pseudo, this)) {
        		//Send OK
        		queueDatagram(new ErrorCode(ErrorCode.OK));
        		login = Optional.of(pseudo);
        	} else {
        		//Send NOT AVAILABLE
        		queueDatagram(new ErrorCode(ErrorCode.PSEUDO_UNAVAILABLE));
        		closed = true;
        	}
        	//TODO
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
        private void silentlyClose() {
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
	
	/*--------------------TCP RELATED PART-------------------------*/
	private class TCPContext implements Context {
		
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
        public TCPContext(SelectionKey tcpContextKey, SocketChannel socketChannel) {
			Objects.requireNonNull(tcpContextKey);
			Objects.requireNonNull(socketChannel);
			this.tcpContextKey = tcpContextKey;
			this.socketChannel = socketChannel;
			tcpContextKey.attach(this);
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
        //TODO deconnecter l'autre en cas de dï¿½connection de l'un
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
        	if (this.pairedContext.isPresent()) {
        		throw new IllegalStateException("Already paired");
        	}
			this.pairedContext = Optional.of(pairedContext);
		}
        
        /**
         * 
		 * @brief silently close the socket channel
		 */
        private void silentlyClose() {
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
	
	/**
	 * 
	 * Class representing a TCP protocole message.
	 */
	private class TCPKey {
		private final String sender;
		private final String recipient;
		private final short  password;
		
		/**
		 * TCPKey constructor
		 * @param sender the pseudonym of the sender client
		 * @param recipient the pseudonym of the recipient client
		 * @param password the TCP private connexion password
		 */
		public TCPKey(String sender, String recipient, short password) {
			Objects.requireNonNull(sender);
			Objects.requireNonNull(recipient);
			this.sender = sender;
			this.recipient = recipient;
			this.password = password;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TCPKey)){
				return false;
			}
			var tcpKey = (TCPKey) obj;
			return password == tcpKey.password
					&& sender.equals(tcpKey.sender)
					&& recipient.equals(tcpKey.recipient);
		}
		
		@Override
		public int hashCode() {
			return sender.hashCode()^recipient.hashCode()+password;
		}
	}
	
	/**
	 * 
	 * Class representing and ongoing TCP connection protocol waiting for both sides to connect
	 */
	private class TCPLink {
		private Optional<TCPContext> senderContext    = Optional.empty();
		private Optional<TCPContext> recipientContext = Optional.empty();
		
		/**
		 * 
		 * @brief check if a the both client in a TCP private connexion are present
		 * @return if the both client are present
		 */
		public boolean bothConnected(){
			return senderContext.isPresent() && recipientContext.isPresent();
		}
		
		/**
		 * 
		 * @brief connect the sender to this context
		 * @param key the context seleted key
		 * @param sc the context socket channel
		 * @return if the connexion is set (false if the sender is already connected / present)
		 */
		public boolean connectSenderContext(SelectionKey key, SocketChannel sc) {
			if (senderContext.isPresent()) {
				return false;
			}
			var context = new TCPContext(key, sc);
			senderContext = Optional.of(context);
			context.bbout.put((new ErrorCode(ErrorCode.OK)).toByteBuffer(logger).get());
			return true;
		}
		
		/**
		 * 
		 * @brief connect the recipient to this context
		 * @param key the context selected key
		 * @param sc the context selcket channel
		 * @return if the connexion is set (false if the recipient is already connected/ present)
		 */
		public boolean connectRecipientContext(SelectionKey key, SocketChannel sc) {
			if (recipientContext.isPresent()) {
				return false;
			}
			var context = new TCPContext(key, sc);
			recipientContext = Optional.of(context);
			context.bbout.put((new ErrorCode(ErrorCode.OK)).toByteBuffer(logger).get());
			return true;
		}
		
		/**
		 * 
		 * @brief close the sender & the recipient sockets channels
		 */
		public void close() {
			senderContext.ifPresent((c) -> c.silentlyClose());
			recipientContext.ifPresent((c) -> c.silentlyClose());
		}
		
		/**
		 * 
		 * @brief connect sender and recipient each other
		 */
		public void connect() {
			if (!bothConnected()) {
				throw new IllegalStateException("Missing connections");
			}
			senderContext.get().setPairedContext(recipientContext.get());
			recipientContext.get().setPairedContext(senderContext.get());
			senderContext.get().updateInterestOps();
			recipientContext.get().updateInterestOps();
		}
	}
	
	
	private final HashMap<TCPKey, TCPLink> waitingTCPConnections = new HashMap<>();
	
    /**
     * @brief Add a new pair TCPKey/TCPLink to the map if possible
     * @param message the message to send
     * @return TCP_IN_PROTOCOLE if the key is already in the map (duplicated request) 
     * UNREACHABLE USER if the recipient is not connected, OK otherwise
     */
    public byte broadcast(TCPAsk message) {
    	Objects.requireNonNull(message);
    	if (!clientLoginMap.containsKey(message.getRecipient())) {
    		System.out.println("UNREACHABLE");
    		return ErrorCode.UNREACHABLE_USER;
    	}
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
		if (waitingTCPConnections.containsKey(key)) {
			System.out.println("IN_PROTOCOLE");
			return ErrorCode.TCP_IN_PROTOCOLE;
		}
		waitingTCPConnections.put(key, new TCPLink());
		clientLoginMap.get(message.getRecipient()).queueDatagram(message);
    	return ErrorCode.OK;
	}
    
    /**
     * @brief Remove the pair TCPKey/TCPLink if it exists.
     * @param message the message to convey
     * @return  TCP_IN_PROTOCOLE if the key is not in the map (request not initiated)
     * UNREACHABLE USER if the recipient is not connected and OK otherwise
     */
    public byte broadcast(TCPAbort message) {
    	Objects.requireNonNull(message);
    	if (!clientLoginMap.containsKey(message.getSender())) {
    		return ErrorCode.UNREACHABLE_USER;
    	}
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
    	if (!waitingTCPConnections.containsKey(key)) {
    		return ErrorCode.TCP_NOT_IN_PROTOCOLE;
    	}
    	clientLoginMap.get(message.getSender()).queueDatagram(message);
    	waitingTCPConnections.remove(key).close();
    	return ErrorCode.OK;
    }
    
    /**
     * 
     * @brief accept the connexion of a TCP private connexion
     * @param message the datagram request
     * @param context the concerned context
     * @param consumer the selected key (TCPKey on a consumer)
     * @return the ErrorCode in terms of some tests
     */
    private byte acceptConnection(TCPDatagram message, ChatContext context, Consumer<TCPKey> consumer) {
    	if (!clientLoginMap.containsKey(message.getSender())) {
    		return ErrorCode.UNREACHABLE_USER;
    	}
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
    	if (!waitingTCPConnections.containsKey(key)) {
    		return ErrorCode.TCP_NOT_IN_PROTOCOLE;
    	}
    	consumer.accept(key);
    	return ErrorCode.OK;
    }
    
    //TODO
    /**
     * 
     * @brief accept the connexion of a TCP private connexion if possible
     * @param message the tcp datagram request
     * @param context the concerned context
     * @param consumer the selected key (TCPKey on a consumer)
     * @return the ErrorCode in terms of some tests
     */
    private byte acceptConnectionTMP(TCPDatagram message, WaitingContext context, Consumer<TCPLink> consumer) {
    	if (!clientLoginMap.containsKey(message.getSender())) {
    		return ErrorCode.UNREACHABLE_USER;
    	}
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
    	if (!waitingTCPConnections.containsKey(key)) {
    		return ErrorCode.TCP_NOT_IN_PROTOCOLE;
    	}
    	var link = waitingTCPConnections.get(key);
    	consumer.accept(link);
    	if (link.bothConnected()) {
    		link.connect();
    		waitingTCPConnections.remove(key);
    	}
    	return ErrorCode.OK;
    }
    
    public byte tryTCPAccept(TCPAccept message, WaitingContext context) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(context);
    	clientLoginMap.get(message.getSender()).queueDatagram(message);
    	return acceptConnectionTMP(message, context, (link) -> {
        	link.connectRecipientContext(context.key, context.sc);
    	});
    }
    
    public byte tryTCPConnect(TCPConnect message, WaitingContext context) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(context);
    	System.out.println("starting TCPConnect");
    	return acceptConnectionTMP(message, context, (link) -> {
        	link.connectSenderContext(context.key, context.sc);
    	});
    }
    
    /**
     * @brief broadcast a message (TCPAccept) to the context
     * @param message the message TCPAccept
     * @param context the context to change to TCPContext
     * @return the ErrorCode calculated
     */
    public byte broadcast(TCPAccept message, ChatContext context) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(context);
    	clientLoginMap.get(message.getSender()).queueDatagram(message);
    	return acceptConnection(message, context, (key) -> {
        	var link = waitingTCPConnections.get(key);
        	link.connectRecipientContext(context.key, context.sc);
        	if (link.bothConnected()) {
        		link.connect();
        		waitingTCPConnections.remove(key);
        	}
    	});
    }
    
    /**
     * 
     * @brief broadcast a message (TCPConnect) to the context
     * @param message the message TCPConnect
     * @param context the concerned context
     * @return the calculated ErrorCode
     */
    public byte broadcast(TCPConnect message, ChatContext context) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(context);
    	System.out.println("starting TCPConnect");
    	return acceptConnection(message, context, (key) -> {
        	var link = waitingTCPConnections.get(key);
        	link.connectSenderContext(context.key, context.sc);
        	if (link.bothConnected()) {
        		link.connect();
        		waitingTCPConnections.remove(key);
        	}
    	});
    }
    
    /**
     * 
     * @brief broadcast a message (TCPAbort) to the context
     * @param message the message TCPAbort
     * @param context the concerned context
     * @return the calculated ErrorCode
     */
    public byte broadcast(TCPAbort message, ChatContext context) {
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
    	if (!waitingTCPConnections.containsKey(key)) {
    		return ErrorCode.TCP_NOT_IN_PROTOCOLE;
    	}
    	clientLoginMap.get(message.getSender()).queueDatagram(message);
    	waitingTCPConnections.remove(key);
    	return ErrorCode.OK;
    }
	/*-----------------------END OF TCP RELATED PART------------------------*/
	
	
    static private int BUFFER_SIZE = 1_024;
    static private Logger logger = Logger.getLogger(ChatOsServer.class.getName());

    private final HashMap<String, ChatContext> clientLoginMap = new HashMap<>();
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
	
    public ChatOsServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }
    
    /**
     * @brief Return whether or not the pseudo is available
     * @param pseudo the pseudonym to check
     * @return True if the pseudonyme is available.
     */
    public boolean isAvailable(String pseudo) {
    	return !clientLoginMap.containsKey(pseudo);
    }
    
    /**
     * @brief Link the new created context to the pseudonym
     * 
     * @param pseudo the pseudonym to add
     * @param context the context to link
     * @throws IllegalArgumentException if the pseudonym is already taken
     */
    public void addChatContext(String pseudo, ChatContext context) {
    	if (clientLoginMap.containsKey(pseudo)) {
    		throw new IllegalArgumentException("Pseudo already taken");
    	}
    	clientLoginMap.put(pseudo, context);
    }

	/**
     * @brief Add the pair pseudonym/context to the map only if the key is not in the map
     * @return true if the pseudonym is available
     */
    public boolean requestPseudonymAndAdd(String pseudo, ChatContext context) {
    	if (clientLoginMap.containsKey(pseudo)) {
    		return false;
    	}
    	clientLoginMap.put(pseudo, context);
    	return true;
    }
    
    /**
     * 
     * @brief Broadcast a private message to the correct recipient if it exist
     * @param message the message to broadcast
     * @param sender SelectionKey of the sender
     * @param recipient String representing the recipient
     * @return if the recipient is connected to the server
     */
    public byte broadcast(PrivateMessage message) {
    	Objects.requireNonNull(message);
    	if (!clientLoginMap.containsKey(message.getRecipient())) {
    		return ErrorCode.UNREACHABLE_USER;
    	} else {
    		var context = clientLoginMap.get(message.getRecipient());
    		context.queueDatagram(message);
    		return ErrorCode.OK;
    	}
    }
    /**
     * 
     * @brief Broadcast a message to every person connected with the exception of the sender
     * @param message the message to broadcast
     * @param sender SelectionKey of the sender 
     */
    public void broadcast(MessageAll message, ChatContext sender) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(sender);
    	for (SelectionKey key : selector.keys()) {
    		if (key.isValid() && !key.isAcceptable() && !key.equals(sender.key) && key.attachment() instanceof ChatContext) {
    			var context = (ChatContext) key.attachment();
    			context.queueDatagram(message);
    		}
    	}
    }
    
    /**
     * 
     * @brief launch the server
     * @throws IOException when configureBlocking throws it
     */
    public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while(!Thread.interrupted()) {
			printKeys(); // for debug
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
		}
    }
    
    /**
     * 
     * @brief treat server key
     * @param key the server key
     */
    private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch(IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO,"Connection closed with client due to IOException",e);
			silentlyClose(key);
			if (key.attachment() instanceof ChatContext) {
				var login = ((ChatContext)key.attachment()).login;
				if (login.isPresent()) {
					clientLoginMap.remove(login.get());
				}
			}
		}
	}
    
    /**
     * 
     * @brief attach a new ChatContext to a key
     * @param key the server key
     * @throws IOException when accept throws it
     */
    private void doAccept(SelectionKey key) throws IOException {
    	ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		var newKey = sc.register(selector, SelectionKey.OP_READ);
		newKey.attach(new ChatContext(this,newKey));
    }
    
    /**
     * 
     * @brief silently close the socket channel
     * @param key the server key
     */
    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
        	logger.info("key closed");
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
	
    /**
     * 
     * @brief main method starting a ChatOs server
     * @param args usage : port
     * @throws NumberFormatException when the port arg is not a number
     * @throws IOException when ServerChatOs throw an IOException
     */
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=1){
            usage();
            return;
        }
        new ChatOsServer(Integer.parseInt(args[0])).launch();
    }

    /**
     * 
     * @brief print the usage of the server
     */
    private static void usage(){
        System.out.println("Usage : ChatOsServer port");
    }
    
    
    
    /**
	 * 
	 * @brief get a string format of a key
	 * @param key the key to parse in a string
	 * @return the string key
	 */
	private String interestOpsToString(SelectionKey key){
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
		if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
		return String.join("|",list);
	}

	/**
	 * 
	 * @brief print the client selectionned keys 
	 */
	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println(selectionKeySet);
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet){
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
			}
		}
	}

	/**
	 * 
	 * @brief get a string format of the remote address of a socket channel
	 * @param sc the interested socket channel
	 * @return the string format of the remote address
	 */
	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
			return "???";
		}
	}

	/**
	 * 
	 * @brief print a selected key
	 * @param key the key to print
	 */
	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	/**
	 * 
	 * @brief print the possible channel of a key
	 * @param key the key to examined
	 * @return a string format of possible actions
	 */
	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable()) list.add("ACCEPT");
		if (key.isReadable()) list.add("READ");
		if (key.isWritable()) list.add("WRITE");
		return String.join(" and ",list);
	}
}
