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
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.Datagram;
import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAsk;
import reader.OpCodeReader;
import reader.Reader.ProcessStatus;


public class ChatOsServer {
	public class Context {
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
        
        private Context(ChatOsServer server, SelectionKey key){
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
        		return;
        	}
        	if (server.broadcast(message)) {
        		queueDatagram(new ErrorCode(ErrorCode.OK));
        	} else {
        		queueDatagram(new ErrorCode(ErrorCode.UNREACHABLE_USER));
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
        	if (server.broadcast(message, message.getRecipient())) {
        		queueDatagram(new ErrorCode(ErrorCode.OK));
        	} else {
        		queueDatagram(new ErrorCode(ErrorCode.UNREACHABLE_USER));
        	}
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
        private void doRead() throws IOException {
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
					System.out.println(bb);
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
        private void doWrite() throws IOException {
        	processOut();
        	bbout.flip();
        	System.out.println("Sending message : " + bbout);
        	sc.write(bbout);
        	bbout.compact();
        	updateInterestOps();
        }
	}
	
	//TODO
	public class TCPContext {
		final private SelectionKey key;
        final private SocketChannel sc;
        final private ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
        final private ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
        
        private boolean isConnected;
        
        public TCPContext(SelectionKey key, SocketChannel sc) {
        	System.out.println("Creating TCPContext");
        	
			Objects.requireNonNull(key);
			Objects.requireNonNull(sc);
			this.key = key;
			this.sc = sc;
		}
	}
	
    static private int BUFFER_SIZE = 1_024;
    static private Logger logger = Logger.getLogger(ChatOsServer.class.getName());

    private final HashMap<String, Context> clientLoginMap = new HashMap<>();
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
    public boolean requestPseudonymAndAdd(String pseudo, Context context) {
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
    public boolean broadcast(PrivateMessage message, String recipient) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(recipient);
    	if (!clientLoginMap.containsKey(recipient)) {
    		return false;
    	} else {
    		var context = clientLoginMap.get(recipient);
    		context.queueDatagram(message);
    		return true;
    	}
    }
    
    public boolean broadcast(TCPAsk message) {
		
		// TODO Auto-generated method stub
    	return false;
	}
    /**
     * Broadcast a message to every person connected with the exception of the sender
     * 
     * @param message the message to broadcast
     * @param sender SelectionKey of the sender 
     */
    public void broadcast(MessageAll message, Context sender) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(sender);
    	for (SelectionKey key : selector.keys()) {
    		if (key.isValid() && !key.isAcceptable() && !key.equals(sender.key)) {
    			var context = (Context) key.attachment();
    			context.queueDatagram(message);
    		}
    	}
    }
    
    public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while(!Thread.interrupted()) {
			printKeys(); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
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
			var login = ((Context)key.attachment()).login;
			if (login.isPresent()) {
				clientLoginMap.remove(login.get());
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
		var newKey = sc.register(selector, SelectionKey.OP_READ,ByteBuffer.allocate(BUFFER_SIZE));
		newKey.attach(new Context(this,newKey));
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
