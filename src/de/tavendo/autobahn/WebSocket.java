package de.tavendo.autobahn;

import java.net.URI;

public interface WebSocket {

	/**
	 * Session handler for WebSocket sessions.
	 */
	public interface WebSocketConnectionObserver {
		public enum WebSocketCloseType {
			NORMAL,
			CANNOT_CONNECT, 
			CONNECTION_LOST, 
			PROTOCOL_ERROR,
			INTERNAL_ERROR,
			SERVER_ERROR,
			RECONNECT
		}

		/**
		 * Fired when the WebSockets connection has been established.
		 * After this happened, messages may be sent.
		 */
		public void onOpen();

		/**
		 * Fired when the WebSockets connection has deceased (or could
		 * not established in the first place).
		 *
		 * @param code       Close code.
		 * @param reason     Close reason (human-readable).
		 */
		public void onClose(WebSocketCloseType code, String reason);

		/**
		 * Fired when a text message has been received (and text
		 * messages are not set to be received raw).
		 *
		 * @param payload    Text message payload or null (empty payload).
		 */
		public void onTextMessage(String payload);

		/**
		 * Fired when a text message has been received (and text
		 * messages are set to be received raw).
		 *
		 * @param payload    Text message payload as raw UTF-8 or null (empty payload).
		 */
		public void onRawTextMessage(byte[] payload);

		/**
		 * Fired when a binary message has been received.
		 *
		 * @param payload    Binar message payload or null (empty payload).
		 */
		public void onBinaryMessage(byte[] payload);
	}

	public void connect(URI uri, WebSocketConnectionObserver handler) throws WebSocketException;
	public void connect(URI uri, WebSocketConnectionObserver handler, WebSocketOptions options) throws WebSocketException;
	public void disconnect();
	public boolean isConnected();
	public void sendBinaryMessage(byte[] payload);
	public void sendRawTextMessage(byte[] payload);
	public void sendTextMessage(String payload);
}
