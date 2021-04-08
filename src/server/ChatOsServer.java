package server;

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
import fr.upem.net.chatos.datagram.TCPAbort;
import reader.OpCodeReader;
import reader.Reader.ProcessStatus;


public class ChatOsServer {
	private interface Context {
		void doRead() throws IOException;
		void doWrite() throws IOException;
	}
	
	public class ChatContext implements Context{
        final private SelectionKey key;
        final private SocketChannel sc;
        final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        final private Queue<Datagram> queue = new LinkedList<>();
        final private ChatOsServer server;
        
        
        final private ServerDatagramVisitor visitor = new ServerDatagramVisitor();
        final private OpCodeReader reader = new OpCodeReader();
        
        private Optional<String> login = Optional.empty();
        
        private boolean closed;
        
        private ChatContext(ChatOsServer server, SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }
        
        /**
         * Add a datagram to the queue
         * @param datagram
         */
        private void queueDatagram(Datagram datagram) {
        	queue.add(datagram);
        	System.out.println("added : " + datagram);
        	login.ifPresent(s -> System.out.println("to : " + s));
        	updateInterestOps();
        }
        
        /**
         * broadcast a message to every client connected
         * Send back Invalid Pseudonym if the sender is not associated with this context
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
        
        public void broadcast(TCPAccept message) {
        	var code = server.broadcast(message, this);
        	if (code != ErrorCode.OK) {
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        	}
        }
        
        public void broadcast(TCPConnect message) {
        	var code = server.broadcast(message, this);
        	if (code != ErrorCode.OK) {
        		queueDatagram(new TCPAbort(message.getSender(), message.getRecipient(), message.getPassword()));
        	}
        }
        
        /**
         * broadcast a message to a recipient if it is connected
         * send an ERROR packet to the client "OK" if the recipient is connected and
         * Unreachable User otherwise
         * Send back Invalid Pseudonym if the sender is not associated with this context
         * @param message
         */
        public void broadcast(PrivateMessage message) {
        	if (!message.getSender().equals(login.get())) {
        		queueDatagram(new ErrorCode(ErrorCode.INVALID_PSEUDONYM));
        		return;
        	}
    		queueDatagram(new ErrorCode(server.broadcast(message)));
        }
        
        public void closeContext() {
        	closed = true;
        }
        
        public boolean isConnected() {
        	return login.isPresent();
        }
        
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
        }
        
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
        	System.out.println(queue.size());
        	System.out.println("Hello");
        	key.interestOps(intOps);
        }
        
        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }
        
        /**
         * Performs the read action on sc
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
         * Process the content of bbin
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
         * Try to fill bbout from the queue
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
         * Performs the write action on sc
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
	//TODO
	private class TCPContext implements Context{
		
		private Optional<TCPContext> pairedContext = Optional.empty();
		private final Queue<ByteBuffer> otherQueue = new LinkedList<>();
		
		private final SelectionKey tcpContextKey;
		private final SocketChannel socketChannel;
		private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        
		private boolean closed;
        
        public TCPContext(SelectionKey tcpContextKey, SocketChannel socketChannel) {
        	System.out.println("Creating TCPContext");
			Objects.requireNonNull(tcpContextKey);
			Objects.requireNonNull(socketChannel);
			this.tcpContextKey = tcpContextKey;
			this.socketChannel = socketChannel;
			tcpContextKey.attach(this);
		}
        
        private void updateIntersts() {
        	updateInterestOps();
        	pairedContext.get().updateInterestOps();
        }
        
        private void updateInterestOps() {
        	System.out.println("------INTEREST OPS---------");
        	int intOps = 0;
        	if (!closed && bbin.hasRemaining()) {
        		intOps |= SelectionKey.OP_READ;
        	}
        	System.out.println(bbout);
        	if (bbout.position() != 0 || (pairedContext.isPresent() && pairedContext.get().otherQueue.size() != 0)){
        		intOps |= SelectionKey.OP_WRITE;
        	}
        	if (intOps == 0) {
        		silentlyClose();
        		pairedContext.get().silentlyClose();
        		return;
        	}
        	System.out.println(intOps);
        	tcpContextKey.interestOps(intOps);
        	System.out.println(socketChannel);
        	System.out.println(tcpContextKey);
        	System.out.println(selector.keys().contains(tcpContextKey));
        	System.out.println("InterestOps READ | WRITE : " + (SelectionKey.OP_READ | SelectionKey.OP_WRITE));
        	System.out.println("----------END INTEREST OPS---------");
        	
        }
        
        public void setPairedContext(TCPContext pairedContext) {
        	if (this.pairedContext.isPresent()) {
        		throw new IllegalStateException("Already paired");
        	}
			this.pairedContext = Optional.of(pairedContext);
		}
        
        private void silentlyClose() {
            try {
                socketChannel.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

		@Override
		public void doRead() throws IOException {
			if (socketChannel.read(bbin) == -1) {
				closed = true;
				return;
			}
			//TODO
			throw new UnsupportedClassVersionError();
		}

		@Override
		public void doWrite() throws IOException {
			bbout.flip();
			if (socketChannel.write(bbout) == -1) {
				closed = true;
				return;
			}
			bbout.compact();
			updateIntersts();
		}
	}
	
	/**
	 * 
	 * Class representing a TCP protocole message.
	 */
	private class TCPKey {
		private final String sender;
		private final String recipient;
		private final short password;
		
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
		private Optional<TCPContext> senderContext = Optional.empty();
		private Optional<TCPContext> recipientContext = Optional.empty();
		
		public boolean bothConnected(){
			return senderContext.isPresent() && recipientContext.isPresent();
		}
		
		public boolean connectSenderContext(SelectionKey key, SocketChannel sc) {
			if (senderContext.isPresent()) {
				return false;
			}
			var context = new TCPContext(key, sc);
			senderContext = Optional.of(context);
			context.bbout.put((new ErrorCode(ErrorCode.OK)).toByteBuffer(logger).get());
			return true;
		}
		
		public boolean connectRecipientContext(SelectionKey key, SocketChannel sc) {
			if (recipientContext.isPresent()) {
				return false;
			}
			var context = new TCPContext(key, sc);
			recipientContext = Optional.of(context);
			context.bbout.put((new ErrorCode(ErrorCode.OK)).toByteBuffer(logger).get());
			return true;
		}
		
		public void close() {
			senderContext.ifPresent((c) -> c.silentlyClose());
			recipientContext.ifPresent((c) -> c.silentlyClose());
		}
		
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
	
    /**
     * Add a new pair TCPKey/TCPLink to the map if possible
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
			return ErrorCode.TCP_IN_PROTOCOLE;//TODO
		}
		waitingTCPConnections.put(key, new TCPLink());
		clientLoginMap.get(message.getRecipient()).queueDatagram(message);
    	return ErrorCode.OK;
	}
    
    /**
     * Remove the pair TCPKey/TCPLink if it exists.
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
    
    /**
     * 
     * @param message the message TCPAccept
     * @param context the context to change to change to TCPContext
     * @return
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
	
	private final HashMap<TCPKey, TCPLink> waitingTCPConnections = new HashMap<>();
	
	
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
     * Add the pair pseudonym/context to the map only if the key is not in the map
     * @return true is the pseudonym is available
     */
    public boolean requestPseudonymAndAdd(String pseudo, ChatContext context) {
    	if (clientLoginMap.containsKey(pseudo)) {
    		return false;
    	}
    	clientLoginMap.put(pseudo, context);
    	return true;
    }
    
    /**
     * Broadcast a private message to the correct recipient if it exist
     * 
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
     * Broadcast a message to every person connected with the exception of the sender
     * 
     * @param message the message to broadcast
     * @param sender SelectionKey of the sender 
     */
    public void broadcast(MessageAll message, ChatContext sender) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(sender);
    	for (SelectionKey key : selector.keys()) {
    		if (key.isValid() && !key.isAcceptable() && !key.equals(sender.key)) {
    			var context = (ChatContext) key.attachment();
    			context.queueDatagram(message);
    		}
    	}
    }
    
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
    
    private void silentlyClose(SelectionKey key) {
        Channel sc = (Channel) key.channel();
        try {
        	logger.info("key closed");
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }
	
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=1){
            usage();
            return;
        }
        new ChatOsServer(Integer.parseInt(args[0])).launch();
    }

    private static void usage(){
        System.out.println("Usage : ChatOsServer port");
    }
    
    
    
    
    
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

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

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
