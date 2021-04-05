package fr.upem.net.chatos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import reader.ErrorCode;
import reader.OpCodeReader;
import reader.Reader.ProcessStatus;

public class ClientChatOs {
	public class Context {
		
		private final SelectionKey key;
		private final SocketChannel sc;
		
		private final int BUFFER_MAX_SIZE = (MAX_STRING_SIZE + Short.BYTES) * 3 + 1;
		
		private final ByteBuffer bbin  = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_MAX_SIZE);
		
		private final Queue<ByteBuffer> queue     = new LinkedList<>();;
		private final OpCodeReader reader = new OpCodeReader();
		private final ClientDatagramVisitor visitor = new ClientDatagramVisitor();
		//Reader here !
		
		private boolean closed = false;
		
		/**
		 * Context contructor
		 * @param key the selected key to attach to this context (client)
		 */
		private Context(SelectionKey key) {
			this.key = key;
			this.sc  = (SocketChannel) key.channel();
		}
		
		/**
		 * @brief process the content of bbin
		 */
		private void processIn() {
			//TODO normalement c'est pas different de ClientChat sauf qu'on a un switch sur le type de message reçu !
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
		private void queueCommand(ByteBuffer bb) {
			queue.add(bb);
			processOut();
			updateInterestOps();
		}
		
		/**
		 * @brief process the  content of bbout
		 */
		private void processOut() {
			while (!queue.isEmpty()) {
				var bb = queue.peek();
				if (bb.remaining() <= bbout.remaining()) {
					queue.remove();
					bb.flip();
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
                interesOps=interesOps|SelectionKey.OP_READ;
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
		private void doRead() throws IOException {
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
		private void doWrite() throws IOException {
			bbout.flip();
			sc.write(bbout);
			bbout.compact();
			processOut();
			updateInterestOps();
		}
		
		/**
		 * @brief perform a connexion request to the server, destroy this client if refused
		 * @throws IOException when finishConnect throws it
		 */
		private void doConnect() throws IOException {
			if (sc.finishConnect()) {
				// TODO SEND HERE A CONNEXION REQUEST AND WAIT FOR THE ANSWER ! IF REFUSED CANCEL EVERYTHING
				key.interestOps(SelectionKey.OP_READ);
			}
		}
		
	}
	
	/* ----------------------------------------------------------------- */
	
	static private int       MAX_STRING_SIZE = 1_024;
	static private Logger    logger      = Logger.getLogger(ClientChatOs.class.getName());
	static private final int maxLoginLength = 32;

	private final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	
	private final Thread                     console;
	private final ArrayBlockingQueue<String> commandQueue; 
	
	private final SocketChannel     sc;
	private final Selector          selector;
	private final InetSocketAddress serverAddress;
	
	private Context uniqueContext;
	
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
	 * 
	 * @param message message to encode
	 * @return an Optional containing the datagram MessageToAll if the message is not too long.
	 * Optional.empty() otherwise
	 */
	private Optional<ByteBuffer> createMessageToAll(String message) {
		logger.info("adding message to queue : " + message  + " from : "
				+ login);
		
		var bblog = UTF8_CHARSET.encode(login);
		var bbmsg = UTF8_CHARSET.encode(message);
		if (bbmsg.limit() > MAX_STRING_SIZE) {
			logger.info("Message exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		return Optional.of(ByteBuffer.allocate(1+2*Short.BYTES + bblog.limit() + bbmsg.limit())
				.put(OpCodeReader.SMA_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
				.putShort((short)bbmsg.limit())
				.put(bbmsg));
	}
	
	/**
	 * 
	 * @param recipient recipient to send to
	 * @param message message to encode
	 * @return an Optional containing the datagram PrivateMessage if the message is not too long.
	 * Optional.empty() otherwise
	 */
	private Optional<ByteBuffer> createMessageToRecipient(String recipient, String message) {
		logger.info("adding message to queue : " + message  + " from : "
				+ login +" to : " + recipient);
		
		var bblog = UTF8_CHARSET.encode(login);
		var bbrec = UTF8_CHARSET.encode(recipient);
		var bbmsg = UTF8_CHARSET.encode(message);
		
		if (bbrec.limit() > MAX_STRING_SIZE) {
			logger.info("Recipient exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		if (bbmsg.limit() > MAX_STRING_SIZE) {
			logger.info("Message exceed the limit (1024), ignoring command");
			return Optional.empty();
		}
		return Optional.of(ByteBuffer.allocate(1+3*Short.BYTES + bblog.limit() + bbrec.limit() + bbmsg.limit())
				.put(OpCodeReader.SPM_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
				.putShort((short)bbrec.limit())
				.put(bbrec)
				.putShort((short)bbmsg.limit())
				.put(bbmsg));
	}
	
	/**
	 * @brief process the first command in commandQueue
	 */
	private void processCommands() {
		if (commandQueue.isEmpty())
			return;
		var command = commandQueue.poll();
		
		Optional<ByteBuffer> bb;
		if (!command.startsWith("@") && !command.startsWith("/")) {
			bb = createMessageToAll(command);
		} else {
			if (command.startsWith("@")) {
				var type = command.split(" ",2);
				if (type.length != 2) {
					System.out.println("not enough args");
				}
				bb = createMessageToRecipient(type[0].substring(1), type[1]);
			} else {
				//TCP le pas beau a faire
				logger.severe("Not yes implemented");
				bb = Optional.empty();
			}
		}
		if (bb.isPresent()) {
			uniqueContext.queueCommand(bb.get());
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
		var bblog = UTF8_CHARSET.encode(login);
		if (bblog.limit() > 1024) {
			System.out.println("Pseudo is too long");
			return false;
		}
		var buff = ByteBuffer.allocate(1 + Short.BYTES + bblog.limit())
				.put(OpCodeReader.CR_CODE)
				.putShort((short)bblog.limit())
				.put(bblog)
				.flip();
		System.out.println(buff);
		sc.write(buff);
		/* Read the ErrorCode */
		System.out.println("buffer was send");
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
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new Context(key);
		key.attach(uniqueContext);
		 
		
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
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
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
