package airlock.agent.chat;

import airlock.Urbit;

import java.time.Instant;

public class ChatUtils {

	/**
	 * For reference, it creates a payload of the following form.
	 *
	 * <pre>
	 *  {@code
	 * Map<String, Object> payload = Map.of(
	 *  "message", Map.of(
	 *      "path", "/~zod/test", // different chat
	 *      "envelope", Map.of(
	 *          "uid", Urbit.uid(),
	 *          "number", 1,
	 *          "author", "~zod",
	 *          "when", Instant.now().toEpochMilli(),
	 *          "letter", Map.of("text", primaryChatViewTestMessage)
	 *      )
	 *     )
	 * );
	 * }
	 * </pre>
	 * @param path path to the chat
	 * @param uid uid of the message
	 * @param author author of the message
	 * @param when when the message was sent
	 * @param textContent text content of the message
	 * @return returns a message payload
	 */
	public static MessagePayload createMessagePayload(String path, String uid, String author, long when, String textContent) {
		return new MessagePayload(
				new Message(
						new Envelope(
								uid,
								1, // dummy value
								author,
								when,
								new Letter(
										textContent
								)
						),
						path
				)
		);
	}

	public static MessagePayload createMessagePayload(String path, String author, long when, String textContent) {
		return ChatUtils.createMessagePayload(path, Urbit.uid(), author, when, textContent);
	}

	public static MessagePayload createMessagePayload(String path, String author, String textContent) {
		return ChatUtils.createMessagePayload(path, Urbit.uid(), author, Instant.now().toEpochMilli(), textContent);
	}

}
