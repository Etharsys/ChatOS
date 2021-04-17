package fr.upem.net.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAbort;
import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPDatagram;


public class ChatOsServer {
	/*-----------------------TCP RELATED PART------------------------*/
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
			var context = new TCPContext(key, sc,new ErrorCode(ErrorCode.OK).toByteBuffer(logger).get());
			senderContext = Optional.of(context);
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
			var context = new TCPContext(key, sc, new ErrorCode(ErrorCode.OK).toByteBuffer(logger).get());
			recipientContext = Optional.of(context);
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
		clientLoginMap.get(message.getRecipient()).queueFrame(message);
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
    	
    	var key = new TCPKey(message.getSender(), message.getRecipient(), message.getPassword());
    	if (!waitingTCPConnections.containsKey(key)) {
    		return ErrorCode.TCP_NOT_IN_PROTOCOLE;
    	}
    	waitingTCPConnections.remove(key).close();
    	if (!clientLoginMap.containsKey(message.getSender())) {
    		return ErrorCode.UNREACHABLE_USER;
    	}
    	clientLoginMap.get(message.getSender()).queueFrame(message);
    	return ErrorCode.OK;
    }

    /**
     * 
     * @brief accept the connexion of a TCP private connexion if possible
     * @param message the tcp datagram request
     * @param context the concerned context
     * @param consumer the selected key (TCPKey on a consumer)
     * @return the ErrorCode in terms of some tests
     */
    private byte acceptConnectionTMP(TCPDatagram message, Consumer<TCPLink> consumer) {
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

    /**
     * @brief broadcast a message (TCPAccept) to the context
     * @param message the message TCPAccept
     * @param context the context to change to TCPContext
     * @return the ErrorCode calculated
     */
    public byte tryTCPAccept(TCPAccept message, SelectionKey key, SocketChannel sc) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(key);
    	Objects.requireNonNull(sc);
    	clientLoginMap.get(message.getSender()).queueFrame(message);
    	return acceptConnectionTMP(message, (link) -> {
        	link.connectRecipientContext(key, sc);
    	});
    }

    /**
     * 
     * @brief broadcast a message (TCPConnect) to the context
     * @param message the message TCPConnect
     * @param context the concerned context
     * @return the calculated ErrorCode
     */
    public byte tryTCPConnect(TCPConnect message, SelectionKey key, SocketChannel sc) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(key);
    	Objects.requireNonNull(sc);
    	System.out.println("starting TCPConnect");
    	return acceptConnectionTMP(message, (link) -> {
        	link.connectSenderContext(key, sc);
    	});
    }
	/*-----------------------END OF TCP RELATED PART------------------------*/
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
    	logger.info("Adding a new ChatContext");
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
    		context.queueFrame(message);
    		return ErrorCode.OK;
    	}
    }

    /**
     * 
     * @brief Broadcast a message to every person connected with the exception of the sender
     * @param message the message to broadcast
     * @param sender SelectionKey of the sender 
     */
    public void broadcast(MessageAll message, SelectionKey senderKey) {
    	Objects.requireNonNull(message);
    	Objects.requireNonNull(senderKey);
    	for (SelectionKey key : selector.keys()) {
    		if (key.isValid() && !key.isAcceptable() && !key.equals(senderKey) && key.attachment() instanceof ChatContext) {
    			var context = (ChatContext) key.attachment();
    			context.queueFrame(message);
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
				var login = ((ChatContext)key.attachment()).getLogin();
				clientLoginMap.remove(login);
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
		newKey.attach(new WaitingContext(this,newKey));
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
