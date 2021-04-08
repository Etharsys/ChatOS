package fr.upem.net.chatos.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.ConnectionRequest;
import fr.upem.net.chatos.datagram.Datagram;
import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAbort;
import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPConnect;
import fr.upem.net.chatos.datagram.TCPDatagram;
import fr.upem.net.chatos.reader.OpCodeReader;
import fr.upem.net.chatos.reader.Reader.ProcessStatus;

public class ClientChatOs {
	
	private interface Context {
		
		/**
		 * @brief read from the socket channel (bbin should be in write mode before and after)
		 * @throws IOException when read throws it
		 */
		void doRead()    throws IOException;
		
		/**
		 * @brief write to the socket channel (bbout should be in write mode before and after)
		 * @throws IOException when write throws it
		 */
		void doWrite()   throws IOException;
		
		/**
		 * @brief proceed the socket channel connexion
		 * @throws IOException when the keys updates throws it
		 */
		void doConnect() throws IOException;
	}
	
	/* ----------------------------------------------------------------- */
	
	public class ChatContext implements Context{
		
		private final SelectionKey  key;
		private final SocketChannel sc;
		private final ClientChatOs  client;
		
		private final int BUFFER_MAX_SIZE = (MAX_STRING_SIZE + Short.BYTES) * 3 + 1;
		
		private final ByteBuffer bbin  = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		
		private final Queue<Datagram>       queue   = new LinkedList<>();
		private final OpCodeReader          reader  = new OpCodeReader();
		private final ClientDatagramVisitor visitor = new ClientDatagramVisitor();
		
		private boolean closed = false;
		
		/**
		 * ChatContext contructor
		 * @param key the selected key to attach to this context (client)
		 */
		private ChatContext(SelectionKey key, ClientChatOs client) {
			this.key = key;
			this.sc  = (SocketChannel) key.channel();
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
		 * @param datagram the command to add
		 */
		private void queueCommand(Datagram datagram) {
			queue.add(datagram);
			processOut();
			updateInterestOps();
		}
		
		/**
		 * @brief process the  content of bbout
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
		 * @brief update the interestOps of the key
		 */
		private void updateInterestOps() {
			var interesOps=0;
            if (!closed && bbin.hasRemaining()){
                interesOps|=SelectionKey.OP_READ;
            }
            if (bbout.position()!=0){
                interesOps|=SelectionKey.OP_WRITE;
            }
            if (interesOps==0){
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
				//ignore exception
			}
		}
		
		@Override
		public void doRead() throws IOException {
			System.out.println("Reading...");
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
			//Impossible
			key.interestOps(SelectionKey.OP_READ);
		}
		
		/**
		 * 
		 * @brief treat the specific request TCPAsk
		 * @param tcpAsk the datagram which represent the request
		 */
		public void treatTCPAsk(TCPAsk tcpAsk){
			Objects.requireNonNull(tcpAsk);
			client.treatTCPAsk(tcpAsk);
		}
		
		/**
		 * 
		 * @brief treat the specific request TCPAccept
		 * @param tcpAccept the datagram which represent the request
		 */
		public void treatTCPAccept(TCPAccept tcpAccept) {
			Objects.requireNonNull(tcpAccept);
			client.treatTCPAccept(tcpAccept);
		}
		
		/**
		 * 
		 * @brief treat the specific request TCPAbort
		 * @param tcpAbort the datagram which represent the request
		 */
		public void treatTCPAbort(TCPAbort tcpAbort) {
			Objects.requireNonNull(tcpAbort);
			client.treatTCPAbort(tcpAbort);
		}
	}
	
	/* ----------------------------------------------------------------- */

	/**
	 * 
	 * @brief proceed a private connexion TCP (connect packet handling)
	 * @param request the datagram representing the TCP private connexion
	 * @param recipient the pseudonym of the recipient client
	 * @param supplier a potential copy of the datagram
	 */
	private void connectTCP(TCPDatagram request, String recipient, Supplier<TCPDatagram> supplier) {
		if (TCPCommandMap.containsKey(request.getSender())) {
			//Already Connected
			chatContext.queueCommand(new TCPAbort(request.getSender(), request.getRecipient(), request.getPassword()));
			return;
		}
		try {
			var socket = SocketChannel.open();
			socket.configureBlocking(false);
			socket.connect(serverAddress);
			var newKey = socket.register(selector, SelectionKey.OP_CONNECT);
			newKey.attach(new TCPContextWaiter(newKey, socket, recipient, supplier.get().toByteBuffer(logger).get()));
		} catch(IOException e) {
			//Aborting connection
			chatContext.queueCommand(new TCPAbort(request.getSender(), request.getRecipient(), request.getPassword()));
		}
	}
	
	/**
	 * 
	 * @brief treat the TCPAsk request
	 * @param tcpAsk the datatgram representing the TCP private connexion
	 */
	public void treatTCPAsk(TCPAsk tcpAsk){
		Objects.requireNonNull(tcpAsk);
		connectTCP(tcpAsk, tcpAsk.getSender(), () -> new TCPAccept(tcpAsk.getSender(),tcpAsk.getRecipient(),tcpAsk.getPassword()));
		TCPCommandMap.put(tcpAsk.getSender(), new LinkedList<>());
	}
	
	/**
	 * 
	 * @brief treat the TCPAccept request
	 * @param tcpAccept the datatgram representing the TCP private connexion
	 */
	public void treatTCPAccept(TCPAccept tcpAccept) {
		Objects.requireNonNull(tcpAccept);
		connectTCP(tcpAccept, tcpAccept.getRecipient(), () -> new TCPConnect(tcpAccept.getSender(),tcpAccept.getRecipient(),tcpAccept.getPassword()));
	}
	
	/**
	 * 
	 * @brief treat the TCPAbort request
	 * @param tcpAbort the datatgram representing the TCP private connexion
	 */
	public void treatTCPAbort(TCPAbort tcpAbort) {
		Objects.requireNonNull(tcpAbort);
		if (TCPCommandMap.containsKey(tcpAbort.getRecipient()) || TCPCommandMap.containsKey(tcpAbort.getSender())) {
			if (TCPCommandMap.remove(tcpAbort.getRecipient()) != null) {
				System.out.println("TCP connection with " + tcpAbort.getRecipient() + " was aborted");
			} else {
				TCPCommandMap.remove(tcpAbort.getSender());
				System.out.println("TCP connection with " + tcpAbort.getSender() + " was aborted");
			}
		}
	}
		
	/* ----------------------------------------------------------------- */

	public class TCPContextWaiter implements Context{
		private final String recipient;
		
		private final SelectionKey contextKey;
		private final SocketChannel socket;
		private final ByteBuffer bbin = ByteBuffer.allocate(2);
		private final ByteBuffer bbout;
		
		private boolean closed;
		
		/**
		 * TCPContextWaiter constructor
		 * @param contextKey the original context key
		 * @param socket the original socket channel
		 * @param recipient the pseudonym of the TCP private connexion recipient
		 * @param buffer the buffer of the TCPDatagram request
		 */
		public TCPContextWaiter(SelectionKey contextKey, SocketChannel socket, String recipient, ByteBuffer buffer) {
			Objects.requireNonNull(contextKey);
			Objects.requireNonNull(socket);
			Objects.requireNonNull(recipient);
			this.contextKey = contextKey;
			this.socket = socket;
			this.recipient = recipient;
			bbout = buffer;
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
					var context = new TCPContext(contextKey,socket,recipient);
					contextKey.attach(context);
					context.updateInterestOps();
					TCPContextMap.put(recipient, context);
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
			System.out.println("coucou");
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
        	printSelectedKey(contextKey);
        }

		@Override
		public void doWrite() throws IOException {
			if (socket.write(bbout) == -1) {
				closed = true;
				return;
			}
			updateInterestOps();
		}
	}
	
	
	/* ----------------------------------------------------------------- */
	
	
	/**
	 * 
	 * Context of a TCP connection
	 */
	public class TCPContext implements Context{
		private final static int BUFFER_SIZE = 1_024;
		private final Charset    ASCII       = StandardCharsets.US_ASCII;
		
		private final SelectionKey key;
		private final SocketChannel sc;
		
		private final ByteBuffer bbin  = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
		
		private Optional<OpCodeReader> reader = Optional.of(new OpCodeReader());
		
		private final String recipient;
		
		private boolean closed;
		
		/**
		 * TCPContext constructor (TCP private connexion)
		 * @param key the original context key
		 * @param socket the original socket channel
		 * @param recipient the pseudonym of the TCP private connexion recipient
		 */
		public TCPContext(SelectionKey key, SocketChannel sc, String recipient) {
			logger.severe("Created TCP Context");
			Objects.requireNonNull(key);
			Objects.requireNonNull(sc);
			Objects.requireNonNull(recipient);
			this.key = key;
			this.sc = sc;
			this.recipient = recipient;
		}
		
		/**
		 * update the interestOps of the key
		 * @brief
		 */
		private void updateInterestOps() {
        	int intOps = 0;
        	if (!closed && bbin.hasRemaining() && !reader.isEmpty()) {
        		intOps |= SelectionKey.OP_READ;
        	}
        	if (bbout.position() > 0 || !(TCPCommandMap.get(recipient).isEmpty())){
        		intOps |= SelectionKey.OP_WRITE;
        	}
        	if (intOps == 0) {
        		silentlyClose();
        		return;
        	}
        	System.out.println(intOps);
        	key.interestOps(intOps);
        }
		
		/**
		 * @brief add a command to the commands queue
		 * @param bb the command to add
		 */
		private void processIn() {
			bbin.flip();
			System.out.println("From the TCP connection : " + ASCII.decode(bbin));
			bbin.clear();
		}

		/**
		 * @brief process the  content of bbout
		 */
		private void processOut() {
			while (TCPCommandMap.get(recipient).size() != 0) {
				var command = TCPCommandMap.get(recipient).peek();
				var bb = ASCII.encode(command);
				if (bbout.limit() >= bb.limit()) {
					bbout.put(bb);
					TCPCommandMap.get(recipient).poll();
				} else {
					break;
				}
			}
		}
		
		/**
		 * @brief silently close the socket channel
		 */
		private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
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
			processOut();
			logger.severe("coucou");
			bbout.flip();
			if (sc.write(bbout) == -1) {
				closed = true;
				return;
			}
			bbout.compact();
			updateInterestOps();
		}

		@Override
		public void doConnect() throws IOException {
			throw new AssertionError();
		}
	}
	
	/* ----------------------------------------------------------------- */
	
	static private int       MAX_STRING_SIZE = 1_024;
	static private Logger    logger          = Logger.getLogger(ClientChatOs.class.getName());
	static private final int maxLoginLength  = 32;
	
	private final Thread                     console;
	private final ArrayBlockingQueue<String> commandQueue; 
	
	/**
	 * Map of command for TCPContext
	 */
	private final HashMap<String,Queue<String>> TCPCommandMap = new HashMap<>();
	private final HashMap<String, TCPContext>   TCPContextMap = new HashMap<>();
	
	private final SocketChannel     sc;
	private final Selector          selector;
	private final InetSocketAddress serverAddress;
	
	private ChatContext chatContext;
	
	private final String login;
	private final int    maxCommands    = 10;
	
	/* ----------------------------------------------------------------- */
	
	/**
	 * Client ChatOs contructor
	 * @param login the client login
	 * @param serverAddress the server address
	 * @throws IOException when open(s) methods throws it
	 */
	public ClientChatOs(String login, InetSocketAddress serverAddress) throws IOException {
		this.serverAddress = serverAddress;
		this.login         = login;
		this.sc            = SocketChannel.open();
		this.selector      = Selector.open();
		this.commandQueue  = new ArrayBlockingQueue<>(maxCommands);
		this.console       = new Thread(this::consoleRun);
	}
	
	/**
	 * @brief the runnable method to handle the console
	 */
	private void consoleRun() {
		try (var scan = new Scanner(System.in)) {
			while (scan.hasNextLine()) {
				var command = scan.nextLine();
				treatCommand(command);
			}
		} catch (InterruptedException ie) {
			logger.info("Console thread has been interrupted");
		} finally {
			logger.info("Console thread stopping");
		}
	}
	
	/* ----------------------------------------------------------------- */
	
	/**
	 * @brief treat a command and add it to the commandQueue
	 * @param command the command to be treated
	 * @throws InterruptedException when wakeup throws it
	 */
	private void treatCommand(String command) throws InterruptedException {
		if (command == "") {
			logger.info("There are no use to send nothing ... ");
			return;
		}
		selector.wakeup();
		commandQueue.add(command);
	}
	
	/**
	 * @brief process the first command in commandQueue
	 */
	private void processCommands() {
		while (!commandQueue.isEmpty()) {
			var command = commandQueue.poll();
			
			Datagram datagram;
			if (!command.startsWith("@") && !command.startsWith("/")) {
				datagram = new MessageAll(login, command);
			} else {
				if (command.startsWith("@")) {
					var type = command.split(" ",2);
					if (type.length != 2) {
						System.out.println("not enough args");
						continue;
					}
					datagram = new PrivateMessage(login,type[0].substring(1), type[1]);
				} else {
					//TCP le pas beau a faire
					var type = command.split(" ",2);
					if (type.length != 2) {
						System.out.println("not enough args");
						continue;
					}
					var recipient = type[0].substring(1);
					if (TCPCommandMap.containsKey(recipient)) {
						//Le TCPContext existe d�ja ou est en cours de cr�ation.
						TCPCommandMap.get(recipient).add(type[1]);
						if (TCPContextMap.containsKey(recipient)) {
							TCPContextMap.get(recipient).updateInterestOps();
						}
						return;
					} else {
						//Le TCPContext n'a pas encore �t� demand� pour ce destinataire -> on le cr�e
						var list = new LinkedList<String>();
						list.add(type[1]);
						TCPCommandMap.put(recipient, list);
						//TODO random short & save it
						datagram = new TCPAsk(login, recipient,(short) 1);
					}
				}
			}
			chatContext.queueCommand(datagram);
		}
	}
	
	/* ----------------------------------------------------------------- */
	
	/**
	 * @throws IOException 
	 * @brief Initiate connection to the server by performing a blocking TCP connection waiting
	 * for server approval
	 */
	private boolean initiateConnection() throws IOException {
		/* Create the CR packet */
		var CR = new ConnectionRequest(login);
		var optBB = CR.toByteBuffer(logger);
		if (optBB.isEmpty()) {
			return false;
		}
		sc.write(optBB.get());
		
		/* Read the ErrorCode */
		var bb = ByteBuffer.allocate(2);
		if (!readFully(sc, bb)) {
			return false;
		}
		bb.flip();
		if (bb.get() != OpCodeReader.ERROR_PACKET_CODE) {
			System.out.println("Wrong packet from the server, terminating...");
			return false;
		};
		var err = bb.get();
		if (err != ErrorCode.OK) {
			switch(err) {
			case ErrorCode.PSEUDO_UNAVAILABLE:
				System.out.println("Pseudo already taken, please retry with another one");
				break;
			default:
				System.out.println("Unkown Error from the server");
				break;
			}
			return false;
		}
		System.out.println("Pseudonym was accepted");
		return true;
	}
	
	
	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
  		while(bb.hasRemaining()) {
  			if(sc.read(bb) == -1) {
  				return false;
  			}
  		}
  		return true;
  	}
	/**
	 * @brief launch the server
	 * @throws IOException when configureBlocking or connect throws it
	 */
	public void launch() throws IOException {
		/* Initiate connection with the server (waiting for confirmation */
		sc.connect(serverAddress);
		if (!initiateConnection()) {
			return;
		}
		
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_READ);
		chatContext = new ChatContext(key, this);
		key.attach(chatContext);
		 
		
		console.start();
		
		while (!Thread.interrupted()) {
			printKeys();
			try {
				selector.select(this::treatKey);
				processCommands();
			} catch (UncheckedIOException uioe) {
				throw uioe.getCause();
			}
		}
	}
	
	/**
	 * @brief change the context status in terms of the key
	 * @param key the key to check to change the context status
	 */
	private void treatKey(SelectionKey key) {
		printSelectedKey(key);
		
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context)key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
            	((Context)key.attachment()).doRead();
            }
            if (key.isValid() && key.isConnectable()) {
        		((Context)key.attachment()).doConnect();
        	}
        } catch(IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
	
	/* ----------------------------------------------------------------- */
	
	/**
	 * @brief main method starting a ChatOs client
	 * @param args usage : login hostname port
	 * @throws NumberFormatException when the port arg is not a number
	 * @throws IOException when ClientChatOs throw an IOException
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 3) {
			usage();
			return;
		}
		if (args[1].length() > maxLoginLength) {
			logger.info("Login size exceed the limit : " + maxLoginLength);
			return;
		}
		var isa = new InetSocketAddress(args[1], Integer.parseInt(args[2]));
		new ClientChatOs(args[0], isa).launch();
	}
	
	/**
	 * 
	 * @brief print the usage of the client
	 */
	private static void usage() {
		System.out.println("Usage : ClientChatOs login hostname port");
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
		if ((interestOps&SelectionKey.OP_READ)!=0)   list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0)  list.add("OP_WRITE");
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
		if (key.isReadable())   list.add("READ");
		if (key.isWritable())   list.add("WRITE");
		return String.join(" and ",list);
	}
	
}
