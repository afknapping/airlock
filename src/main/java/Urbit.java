import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class Urbit {

	public static final MediaType JSON
			= MediaType.get("application/json; charset=utf-8");


	private final OkHttpClient client;
	/**
	 * Code is the deterministic password
	 */
	private String code;

	/**
	 * URL is the location of the instance.
	 */
	private String url;

	/**
	 * UID will be used for the channel: The current unix time plus a random hex string
	 */
	private String uid;

	/**
	 * Last Event ID is an auto-updated index of which events have been sent over this channel
	 */
	private int lastEventId = 0;


	/**
	 * SSE Client is null for now; we don't want to start polling until it the channel exists
	 */
	private EventSource sseClient;

	public EventSource getSseClient() {
		return sseClient;
	}

	/**
	 * Cookie gets set when we log in.
	 */
	private String cookie;

	/**
	 * Ship can be set, in which case we can do some magic stuff like send chats
	 */
	private final String shipName;

	public String getShipName() {
		return shipName;
	}

	private final Gson gson;


	/**
	 * Constructs a new Urbit connection.
	 *
	 * @param url  The URL (with protocol and port) of the ship to be accessed
	 * @param code The access code for the ship at that address
	 */
	public Urbit(String url, String shipName, String code) {
		this.uid = Math.round(Math.floor(Instant.now().toEpochMilli())) + "-" + Urbit.hexString(6);
		this.code = code;
		this.url = url;

		this.client = new OkHttpClient();
		this.shipName = requireNonNullElse(shipName, "");

		gson = new Gson();


		// todo, use `Cookie` and CookieJar and stuff if necessary in the future. for now it's an overkill
		// todo, see if we want to punt up the IOException to the user or just consume it within the API or even make a custom exception (may be overkill).
	}

	/**
	 * This is basic interpolation to get the channel URL of an instantiated Urbit connection.
	 */
	public String channelUrl() {
		return this.url + "/~/channel/" + this.uid;
	}

	/**
	 * Connects to the Urbit ship. Nothing can be done until this is called.
	 */
	public Response connect() throws Exception {
		RequestBody formBody = new FormBody.Builder()
				.add("password", this.code)
				.build();

		Request request = new Request.Builder()
				.header("connection", "keep-alive")
				.url(this.url + "/~/login")
				.post(formBody)
				.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) throw new IOException("Error: " + response);

			// todo remove ugly requireNonNull or migrate to kotlin lmao
			System.out.println(requireNonNull(response.body(), "No response body").string());

			String cookieString = requireNonNull(response.header("set-cookie"), "No cookie given");
			Cookie cookie = Cookie.parse(request.url(), cookieString);
			requireNonNull(cookie, "Unable to parse cookie from string:" + cookieString);
			this.cookie = cookie.name() + "=" + cookie.value();
			System.out.println(this.cookie);
			return response;
		}
	}

	/**
	 * Returns (and initializes, if necessary) the SSE pipe for the appropriate channel.
	 */
	void initEventSource() {
		if (this.sseClient != null) {
			return;
		}
		Request sseRequest = new Request.Builder()
				.url(this.channelUrl())
				.header("connection", "keep-alive")
				.build();
		this.sseClient = EventSources.createFactory(this.client)
				.newEventSource(sseRequest, new EventSourceListener() {
					@Override
					public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
						super.onEvent(eventSource, id, type, data); // todo see if we need this or not
						try {
							ack(lastEventId);
						} catch (IOException e) {
							// todo make less ugly?
							e.printStackTrace();
						}
					}

					@Override
					public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
						super.onFailure(eventSource, t, response);
						System.err.println("Event Source Error: " + response);
					}
				});
	}


	/**
	 * Returns the next event ID for the appropriate channel.
	 */
	int getEventId() {
		this.lastEventId++;
		return this.lastEventId;
	}

	/**
	 * Acknowledges an event.
	 *
	 * @param eventId The event to acknowledge.
	 */
	public Response ack(int eventId) throws IOException {
		JsonObject ackObj = new JsonObject();
		ackObj.addProperty("event-id", eventId);

		JsonArray ackData = new JsonArray();
		ackData.add(ackObj);

		return this.sendMessage("ack", ackData);
	}

	/**
	 * This is a wrapper method that can be used to send any action with data.
	 * <p>
	 * Every message sent has some common parameters, like method, headers, and data
	 * structure, so this method exists to prevent duplication.
	 *
	 * @param action   The action to send
	 * @param jsonData The data to send with the action
	 */
	public Response sendMessage(String action, JsonArray jsonData) throws IOException {

		// MARK - Prepend `id` and `action` metadata to `jsonData` payload
		JsonArray fullJsonDataArray = jsonData.deepCopy(); // todo seems like a wasteful way to do it; possibly refactor
		// extract out first object
		assert fullJsonDataArray.get(0).isJsonObject();
		JsonObject fullJsonData = fullJsonDataArray.get(0).getAsJsonObject();

		// add metadata
		fullJsonData.addProperty("id", this.getEventId());
		fullJsonData.addProperty("action", action);


		String jsonString = gson.toJson(fullJsonDataArray);
		System.out.println(jsonString);

		RequestBody requestBody = RequestBody.create(jsonString, JSON);

		System.out.println("Current cookie is " + cookie);
		Request request = new Request.Builder()
				.url(this.channelUrl())
				.header("Cookie", this.cookie) // todo maybe move to using `Headers` object
				.header("Connection", "keep-alive") // todo see what the difference between header and addHeader is
				.header("Content-Type", "application/json")
				.put(requestBody)
				.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				System.out.println(requireNonNull(response.body()).string());
				throw new IOException("Error: " + response);
			}

			System.out.println(requireNonNull(response.body(), "No response body").string());

			return response;
		}
	}

	/**
	 * Pokes a ship with data.
	 *
	 * @param ship The ship to poke
	 * @param app  The app to poke
	 * @param mark The mark of the data being sent
	 * @param json The data to send
	 */
	public Response poke(
			@Nullable String ship,
			@NotNull String app,
			@NotNull String mark,
			@NotNull String json // todo maybe migrate type to JsonObject
	) throws IOException {

		ship = requireNonNullElse(ship, this.shipName);
		JsonObject pokeDataObj = new JsonObject();
		pokeDataObj.addProperty("ship", ship);
		pokeDataObj.addProperty("app", app);
		pokeDataObj.addProperty("mark", mark);
		pokeDataObj.addProperty("json", json);
		JsonArray pokeData = new JsonArray();
		pokeData.add(pokeDataObj);

		return this.sendMessage("poke", pokeData);
	}

	/**
	 * Subscribes to a path on an app on a ship.
	 *
	 * @param ship The ship to subscribe to
	 * @param app  The app to subsribe to
	 * @param path The path to which to subscribe
	 */
	public Response subscribe(
			@Nullable String ship,
			@NotNull String app,
			@NotNull String path
	) throws IOException {
		ship = requireNonNullElse(ship, this.shipName);
		JsonObject subscribeDataObj = new JsonObject();
		subscribeDataObj.addProperty("ship", ship);
		subscribeDataObj.addProperty("app", app);
		subscribeDataObj.addProperty("path", path);

		JsonArray subscribeData = new JsonArray();
		subscribeData.add(subscribeDataObj);
		return this.sendMessage("subscribe", subscribeData);
	}

	/**
	 * Unsubscribes to a given subscription.
	 *
	 * @param subscription The subscription to unsubscribe from
	 */
	public Response unsubscribe(String subscription) throws IOException {
		JsonObject unsubscribeDataObj = new JsonObject();
		unsubscribeDataObj.addProperty("subscription", subscription);

		JsonArray unsubscribeData = new JsonArray();
		unsubscribeData.add(unsubscribeDataObj);

		return this.sendMessage("unsubscribe", unsubscribeData);
	}

	/**
	 * Deletes the connection to a channel.
	 */
	public Response delete() throws IOException {
		JsonObject deleteDataObj = new JsonObject(); // no data necessary
		JsonArray deleteData = new JsonArray();
		deleteData.add(deleteDataObj);

		return this.sendMessage("delete", deleteData);
	}

	/**
	 * Utility function to connect to a ship that has its *.arvo.network domain configured.
	 *
	 * @param name Name of the ship e.g. zod
	 * @param code Code to log in
	 */
	@NotNull
	static Urbit onArvoNetwork(String name, String code) {
		return new Urbit("https://" + name + ".arvo.network", name, code);
	}

	/**
	 * Returns a hex string of given length.
	 * <p>
	 * Poached from StackOverflow.
	 *
	 * @param len Length of hex string to return.
	 */
	static String hexString(int len) {
		final int maxlen = 8;
		final double min = Math.pow(16, Math.min(len, maxlen) - 1);
		final double max = Math.pow(16, Math.min(len, maxlen)) - 1;
		final double n = Math.floor(Math.random() * (max - min + 1)) + min;

		StringBuilder r = new StringBuilder(Integer.toString((int) Math.round(n), 16));
		while (r.toString().length() < len) {
			r.append(Urbit.hexString(len - maxlen));
		}
		return r.toString();
	}

	/**
	 * Generates a random UID.
	 * <p>
	 * Copied from https://github.com/urbit/urbit/blob/137e4428f617c13f28ed31e520eff98d251ed3e9/pkg/interface/src/lib/util.js#L3
	 */
	static String uid() {
		StringBuilder str = new StringBuilder("0v");
		str.append(Math.ceil(Math.random() * 8)).append('.');
		for (int i = 0; i < 5; i++) {
			String entropy = Integer.toString((int) Math.round(Math.ceil(Math.random() * 10000000)), 32); // todo check to see if this is equivalent with js number behaviours
			// pad entropy with zeroes
			entropy = "00000" + entropy;
			entropy = entropy.substring(entropy.length() - 5);
			str.append(entropy).append('.');
		}
		return str.substring(0, str.length() - 1);
	}
}