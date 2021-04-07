package fr.upem.net.chatos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import fr.upem.net.chatos.datagram.ConnectionRequest;
import fr.upem.net.chatos.datagram.Datagram;
import fr.upem.net.chatos.datagram.ErrorCode;
import fr.upem.net.chatos.datagram.MessageAll;
import fr.upem.net.chatos.datagram.PrivateMessage;
import fr.upem.net.chatos.datagram.TCPAccept;
import fr.upem.net.chatos.datagram.TCPAsk;
import fr.upem.net.chatos.datagram.TCPDenied;
import reader.OpCodeReader;
import reader.Reader.ProcessStatus;

public class ClientChatOs {
	private interface Context {
		void doRead() throws IOException;
		void doWrite() throws IOException;
		void doConnect() throws IOException;
	}
	
	public class ChatContext implements Context{
		
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ClientChatOs client;
		
		private final int BUFFER_MAX_SIZE = (MAX_STRING_SIZE + Short.BYTES) * 3 + 1;
		
		private final ByteBuffer bbin  = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		
		private final Queue<Datagram> queue = new LinkedList<>();
		private final OpCodeReader reader = new OpCodeReader();
		private final ClientDatagramVisitor visitor = new ClientDatagramVisitor();
		
		private boolean closed = false;
		
		/**
		 * Context contructor
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
			//TODO normalement c'est pas different de ClientChat sauf qu'on a un switch sur le type de message reÃ§u !
			System.out.println(bbin);
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
		 * @param bb the command to add
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
		
		/**
		 * @brief read from the socket channel (bbin should be in write mode before and after)
		 * @throws IOException when read throws it
		 */
		public void doRead() throws IOException {
			System.out.println("Reading...");
			if (sc.read(bbin) == -1) {
				closed = true;
			}
			processIn();
			updateInterestOps();
		}
		
		/**
		 * @brief write to the socket channel (bbout should be in write mode before and after)
		 * @throws IOException when write throws it
		 */
		public void doWrite() throws IOException {
			bbout.flip();
			sc.write(bbout);
			bbout.compact();
			processOut();
			updateInterestOps();
		}

		@Override
		public void doConnect() throws IOException {
			throw new AssertionError();
		}
		
		public void acceptTCP(TCPAsk tcpAsk){
			client.acceptTCPConnection(tcpAsk);
		}
	}
	
	public void acceptTCPConnection(TCPAsk tcpAsk){
		//TODO c'est marche pas
		if (TCPCommandMap.containsKey(tcpAsk.getSender())) {
			chatContext.queueCommand(new TCPDenied(tcpAsk.getSender(), tcpAsk.getRecipient(), tcpAsk.getPassword()));
			return;
		}
		try {
			
			var socket = SocketChannel.open();
			socket.configureBlocking(false);
			var key = socket.register(selector, SelectionKey.OP_CONNECT);
			key.attach(new TCPContextWaiter(key, sc, tcpAsk.getRecipient(), (new TCPAccept(tcpAsk.getSender(),tcpAsk.getRecipient(),tcpAsk.getPassword()).toByteBuffer(logger).get())));
			TCPCommandMap.put(tcpAsk.getSender(), new ArrayList<>());
		} catch(IOException e) {
			chatContext.queueCommand(new TCPDenied(tcpAsk.getSender(), tcpAsk.getRecipient(), tcpAsk.getPassword()));
		}
	}
	
	public class TCPContextWaiter implements Context{
		private final String recipient;
		
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bbin = ByteBuffer.allocate(2);
		private final ByteBuffer bbout;
		
		private boolean closed;
		
		public TCPContextWaiter(SelectionKey key, SocketChannel sc, String recipient, ByteBuffer buffer) {
			Objects.requireNonNull(key);
			Objects.requireNonNull(sc);
			Objects.requireNonNull(recipient);
			this.key = key;
			this.sc = sc;
			this.recipient = recipient;
			bbout = buffer;
		}
		
		private void updateInterestOps() {
        	int intOps = 0;
        	if (!closed && bbin.hasRemaining()) {
        		intOps |= SelectionKey.OP_READ;
        	}
        	if (!closed && bbout.position() > 0) {
        		intOps |= SelectionKey.OP_WRITE;
        	}
        	if (intOps == 0) {
        		silentlyClose();
        		return;
        	}
        	key.interestOps(intOps);
        }
		
		@Override
		public void doRead() throws IOException {
			if (sc.read(bbin) == -1) {
				closed = true;
        		return;
        	}
			processIn();
			updateInterestOps();
		}

		private void processIn() {
			if (!bbin.hasRemaining()) {
				if (bbin.get() != OpCodeReader.ERROR_PACKET_CODE) {
					System.out.println("Didn't receive ErrorCode");
				}
				var err = new ErrorCode(bbin.get());
				System.out.println("Received " + err);
				if (err.getErrorCode() != ErrorCode.OK) {
					closed = true;
				} else {
					var context = new TCPContext(key,sc,recipient);
					key.attach(context);
					context.updateInterestOps();
				}
			}
		}

		@Override
		public void doWrite() throws IOException {
			if (sc.write(bbout) == -1) {
				closed = true;
				return;
			}
		}
		
		private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

		@Override
		public void doConnect() throws IOException {
			System.out.println(sc.finishConnect());
        	if (!sc.finishConnect()) {
        		return;
        	}
        	key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
	}
	
	/**
	 * 
	 * Context of a TCP connection
	 */
	public class TCPContext implements Context{
		private final static int BUFFER_SIZE = 1024;
		//TODO
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
		
		private Optional<OpCodeReader> reader = Optional.of(new OpCodeReader());
		
		private final String recipient;
		
		private boolean closed;
		
		public TCPContext(SelectionKey key, SocketChannel sc, String recipient) {
			Objects.requireNonNull(key);
			Objects.requireNonNull(sc);
			Objects.requireNonNull(recipient);
			this.key = key;
			this.sc = sc;
			this.recipient = recipient;
		}
		
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
        	key.interestOps(intOps);
        }

		@Override
		public void doRead() throws IOException {
			// TODO Auto-generated method stub
			if (sc.read(bbin) == -1) {
        		closed = true;
        	}
			
		}

		@Override
		public void doWrite() throws IOException {
			// TODO Auto-generated method stub
			
		}
		
		private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

		@Override
		public void doConnect() throws IOException {
			throw new AssertionError();
		}
	}
	
	/* ----------------------------------------------------------------- */
	
	static private int       MAX_STRING_SIZE = 1_024;
	static private Logger    logger      = Logger.getLogger(ClientChatOs.class.getName());
	static private final int maxLoginLength = 32;
	
	private final Thread                     console;
	private final ArrayBlockingQueue<String> commandQueue; 
	
	/**
	 * Map of command for TCPContext
	 */
	private final HashMap<String,ArrayList<String>> TCPCommandMap = new HashMap<>();
	
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
					}
					datagram = new PrivateMessage(login,type[0].substring(1), type[1]);
				} else {
					//TCP le pas beau a faire
					var type = command.split(" ",2);
					if (type.length != 2) {
						System.out.println("not enough args");
					}
					var recipient = type[0].substring(1);
					if (TCPCommandMap.containsKey(recipient)) {
						//Le TCPContext existe déja ou est en cours de création.
						TCPCommandMap.get(recipient).add(type[1]);
						return;
					} else {
						//Le TCPContext n'a pas encore été demandé pour ce destinataire -> on le crée
						var list = new ArrayList<String>();
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
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context)key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
            	((Context)key.attachment()).doRead();
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
	
	private static void usage() {
		System.out.println("Usage : ClientChatOs login hostname port");
	}
	
}
