package reader;

import java.util.Objects;
import java.util.Optional;

public class Message {
	private final String sender;
	private final String recipient;
	private final String message;
	
	public Message(String sender, String recipient, String message) {
		Objects.requireNonNull(sender);
		Objects.requireNonNull(message);
		this.sender = sender;
		this.recipient = recipient;
		this.message = message;
	}
	
	public Optional<String> getRecipient() {
		if (recipient != null) {
			return Optional.of(recipient);
		}
		return Optional.empty();
	}
	
	public String getSender() {
		return sender;
	}
	
	public String getMessage() {
		return message;
	}
}
