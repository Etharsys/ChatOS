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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
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

public class ChatOsClient {

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
			newKey.attach(new TCPContextWaiter(newKey, socket, recipient, supplier.get().toByteBuffer(logger).get(),this));
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
				TCPContextMap.remove(tcpAbort.getRecipient());//TODO close
				System.out.println("TCP connection with " + tcpAbort.getRecipient() + " was aborted");
			} else {
				TCPCommandMap.remove(tcpAbort.getSender());
				System.out.println("TCP connection with " + tcpAbort.getSender() + " was aborted");
			}
		}
	}
	
	/* ----------------------------------------------------------------- */
	static private Logger    logger          = Logger.getLogger(ChatOsClient.class.getName());
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
	public ChatOsClient(String login, InetSocketAddress serverAddress) throws IOException {
		this.serverAddress = serverAddress;
		this.login         = login;
		this.sc            = SocketChannel.open();
		this.selector      = Selector.open();
		this.commandQueue  = new ArrayBlockingQueue<>(maxCommands);
		this.console       = new Thread(this::consoleRun);
	}
	
	/**
	 * 
	 * @param recipient the login of the recipient
	 * @return the queue corresponding to the recipient
	 */
	public Queue<String> getTCPCommandQueue(String recipient) {
		return TCPCommandMap.get(recipient);
	}
	
	/**
	 * Add the TCPContext to the context queue (erase the old one)
	 * @param recipient the recipient linked to the context
	 * @param context the context to put in the map
	 */
	public void putContextInContextQueue(String recipient, TCPContext context) {
		Objects.requireNonNull(recipient);
		Objects.requireNonNull(context);
		TCPContextMap.put(recipient, context);
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
		new ChatOsClient(args[0], isa).launch();
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
