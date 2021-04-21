package fr.upem.net.chatos.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import fr.upem.net.chatos.reader.HTTPReader;
import fr.upem.net.chatos.reader.StringReader;

/**
 * 
 * Context of a TCP connection
 */
class TCPHTTPContext implements TCPContext{
	private enum Status {
		WQ, //WAIT_QUESTION, // mode : read
		WA, //WAIT_ANSWER,   // mode : read 
		AN, //ANSWERING,     // mode : write
		SQ, //SEND_QUESTION, // mode : write
		RQ, //READ_QUESTION, // mode : read
	};
	
	private final static int BUFFER_SIZE = 1_024;
	static private Logger    logger          = Logger.getLogger(TCPHTTPContext.class.getName());
	private final Charset    ASCII       = StandardCharsets.US_ASCII;
	
	private final SelectionKey key;
	private final SocketChannel sc;
	
	private final ByteBuffer bbin  = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE);
	
	private final Queue<String> commandQueue = new LinkedList<>();
	private final Queue<String> targetQueue  = new LinkedList<>();
	
	private boolean closed;
	
	private Status state = Status.WQ;
			
	private String       HTTPanswer  = "";
	private List<String> fileLines   = List.of();
	private ByteBuffer   currentLine = ByteBuffer.allocate(0); 
	
	private final HTTPReader   httpreader   = new HTTPReader();
	private final StringReader stringReader = new StringReader();

	/**
	 * TCPContext constructor (TCP private connexion)
	 * @param key the original context key
	 * @param socket the original socket channel
	 * @param recipient the pseudonym of the TCP private connexion recipient
	 */
	public TCPHTTPContext(SelectionKey key, SocketChannel sc, Collection<String> commandQueue, Collection<String> targetQueue) {
		logger.severe("Created TCP Context");
		Objects.requireNonNull(key);
		Objects.requireNonNull(sc);
		Objects.requireNonNull(commandQueue);
		Objects.requireNonNull(targetQueue);
		if (targetQueue.size() != commandQueue.size()) {
			throw new IllegalArgumentException("Size between queues are not the same");
		}
		this.key = key;
		this.sc = sc;
		this.commandQueue.addAll(commandQueue);
		this.targetQueue.addAll(targetQueue);
		updateInterestOps();
	}

	/**
	 * @brief update the interestOps of the key
	 */
	private void updateInterestOps() {
		int intOps = 0;
		if (!closed && bbin.hasRemaining() 
				&& (state == Status.WQ || state == Status.WA || state == Status.RQ)) {
			intOps |= SelectionKey.OP_READ;
		}
		if (bbout.position() > 0 || !(commandQueue.isEmpty()) 
				|| (state == Status.SQ || state == Status.AN)){
			intOps |= SelectionKey.OP_WRITE;
		}
		if (intOps == 0) {
			silentlyClose();
			return;
		}
		key.interestOps(intOps);
	}		
	
	/**
	 * @throws IOException 
	 * @brief process the command of bbin
	 */
	private void processIn() throws IOException {
		logger.info("In " + state);
		switch (state) {
		case WQ :
			state = Status.RQ;
		case RQ : // HTTP answer (2)
			processInRequest();
			break;
		case SQ :
			state = Status.WA;
		case WA : // HTTP GET result (4)
			processInAnswer();
			break;
		case AN :
			state = Status.WQ;
		}
	}
	
	private void processInRequest() throws IOException {
		var ps = stringReader.process(bbin);
		switch (ps) {
		case ERROR : 
			logger.log(Level.SEVERE, "HTTP Reader get ERROR status");
			silentlyClose();
			return;
		case REFILL :
			return;
		case DONE :
			HTTPanswer = stringReader.get(); // TODO file !
			stringReader.reset();
			System.out.println(HTTPanswer);
			
			try {				
				fileLines = Files.readAllLines(Path.of(HTTPanswer), ASCII);
				var content_type = HTTPanswer.endsWith(".txt") ? "text" : "unknown";
				var length = fileLines.stream().collect(Collectors.summingInt(s -> s.length())) + fileLines.size();
				currentLine = ASCII.encode(
						"HTTP/1.0 200 OK\r\n"
								+ "Content-Type: " + content_type + "\r\n"
								+ "Content-Length: " + length + "\r\n");
			} catch (NoSuchFileException nsfe) {
				currentLine = ASCII.encode(
						"HTTP/1.0 404 NotFound\r\n"
								+ "Content-Type: unknown\r\n"
								+ "Content-Length: 0\r\n");
			}
			state = Status.AN;
		}
	}
	
	private void processInAnswer() throws IOException {
		var ps = httpreader.process(bbin);
		switch (ps) {
		case ERROR : 
			logger.log(Level.SEVERE, "HTTP Reader get ERROR status");
			silentlyClose();
			break;
		case REFILL :
			break;
		case DONE :
			var header = httpreader.get().getHeader();
			if (header.getResponce_code().equals("200 OK")) {				
				if (header.getContent_type().equals("text")) {
					System.out.println("HTTP GET result from the TCP connexion : \n" + httpreader.get().getContent());					
				}
				try (var bw = Files.newBufferedWriter(Path.of(targetQueue.poll()), ASCII, StandardOpenOption.WRITE,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					bw.write(httpreader.get().getContent());
				}
			} else {
				System.out.println("The requested file cannot be found, get ERROR 404 NotFound");
			}
			httpreader.reset();
			state = Status.WQ;
			break;
		}
	}

	/**
	 * @brief process the  content of bbout
	 */
	private void processOut() {
		logger.info("Out " + state);
		switch (state) {
		case WQ :
			state = Status.SQ;
		case SQ : // HTTP GET request (1)
			processOutRequest();
			break;
		case RQ :
			state = Status.AN;
		case AN : // HTTP result (3)
			processOutAnswer();
			break;
		case WA : //do nothing
			break;
		}
	}
	
	private void processOutRequest() {
		if (commandQueue.size() != 0) {
			var command = commandQueue.peek();
			var bb = ASCII.encode(command);
			if (bbout.limit() >= bb.limit() + Short.BYTES) {
				bbout.putShort((short) bb.limit());
				bbout.put(bb);
				commandQueue.poll();
				state = Status.WA;
			}
		}
	}
	
	private void processOutAnswer() {
		logger.info("current line : " + currentLine + ", " + fileLines.size());
		while (currentLine.hasRemaining() && bbout.hasRemaining()) {
			logger.info(fileLines.size() + ";");
			if (currentLine.remaining() <= bbout.remaining()) {
				bbout.put(currentLine);
			} else {
				var tmp = currentLine.limit();
				currentLine.limit(bbout.remaining());
				bbout.put(currentLine);
				currentLine.limit(tmp);					
			}
			if (!currentLine.hasRemaining() && fileLines.isEmpty()) {
				state = Status.WQ;
			} else {
				var line = fileLines.remove(0);
				logger.info(line + " : " + fileLines.size());
				currentLine = ASCII.encode(line + "\n");
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

	@Override
	public void queueCommand(String command, String target) {
		Objects.requireNonNull(command);
		Objects.requireNonNull(target);
		commandQueue.add(command);
		targetQueue.add(target);
		updateInterestOps();
	}

	@Override
	public void close() {
		closed = true;
		silentlyClose();
	}
}