package reader;

import java.util.Objects;

public class Message {
	private final String sender;
	//TODO may be null (currently)
	private final String recipient;
	private final String message;
	
	public Message(String sender, String recipient, String message) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(message);
		this.sender = sender;
		this.recipient = recipient;
		this.message = message;
	}
	
	public String getRecipient() {
		return recipient;
	}
	
	public String getSender() {
		return sender;
	}
	
	public String getMessage() {
		return message;
	}
}
