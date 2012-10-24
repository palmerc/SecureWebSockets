/******************************************************************************
 *
 *  Copyright 2011-2012 Tavendo GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package de.tavendo.autobahn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver.WebSocketCloseNotification;
import de.tavendo.autobahn.WebSocketMessage.WebSocketCloseCode;

public class WebSocketConnection implements WebSocket {
	private static final String TAG = WebSocketConnection.class.getName();
	private static final String WS_URI_SCHEME = "ws";
	private static final String WSS_URI_SCHEME = "wss";
	private static final String WS_CONNECTOR = "WebSocketConnector";
	private static final String WS_WRITER = "WebSocketWriter";
	private static final String WS_READER = "WebSocketReader";

	protected Handler mHandler;

	protected WebSocketReader mWebSocketReader;
	protected WebSocketWriter mWebSocketWriter;
	protected HandlerThread mWriterThread;

	protected SocketChannel mTransportChannel;
	protected SSLEngine mSSLEngine;

	private URI mWebSocketURI;
	private String[] mWsSubprotocols;

	private WebSocket.WebSocketConnectionObserver mWebSocketObserver;

	protected WebSocketOptions mOptions;
	private boolean mPreviousConnection = false;



	public WebSocketConnection() {
		Log.d(TAG, "WebSocket connection created.");
		createHandler();
	}



	//
	// Forward to the writer thread
	public void sendTextMessage(String payload) {
		mWebSocketWriter.forward(new WebSocketMessage.TextMessage(payload));
	}


	public void sendRawTextMessage(byte[] payload) {
		mWebSocketWriter.forward(new WebSocketMessage.RawTextMessage(payload));
	}


	public void sendBinaryMessage(byte[] payload) {
		mWebSocketWriter.forward(new WebSocketMessage.BinaryMessage(payload));
	}



	public boolean isConnected() {
		return mTransportChannel != null && mTransportChannel.isConnected();
	}



	private void failConnection(WebSocketCloseNotification code, String reason) {
		Log.d(TAG, "fail connection [code = " + code + ", reason = " + reason);

		if (mWebSocketReader != null) {
			mWebSocketReader.quit();

			try {
				mWebSocketReader.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			Log.d(TAG, "mReader already NULL");
		}

		if (mWebSocketWriter != null) {
			mWebSocketWriter.forward(new WebSocketMessage.Quit());

			try {
				mWriterThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			Log.d(TAG, "mWriter already NULL");
		}

		if (mTransportChannel != null) {
			try {
				mTransportChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			Log.d(TAG, "mTransportChannel already NULL");
		}

		onClose(code, reason);

		Log.d(TAG, "worker threads stopped");
	}



	public void connect(URI webSocketURI, WebSocket.WebSocketConnectionObserver handler) throws WebSocketException {
		connect(webSocketURI, handler, new WebSocketOptions());
	}

	public void connect(URI webSocketURI, WebSocket.WebSocketConnectionObserver handler, WebSocketOptions options) throws WebSocketException {
		connect(webSocketURI, null, handler, options);
	}

	public void connect(URI webSocketURI, String[] subprotocols, WebSocket.WebSocketConnectionObserver connectionObserver, WebSocketOptions options) throws WebSocketException {
		if (mTransportChannel != null && mTransportChannel.isConnected()) {
			throw new WebSocketException("already connected");
		}

		if (webSocketURI == null) {
			throw new WebSocketException("WebSockets URI null.");
		} else {
			this.mWebSocketURI = webSocketURI;
			if (!mWebSocketURI.getScheme().equals(WS_URI_SCHEME) && !mWebSocketURI.getScheme().equals(WSS_URI_SCHEME)) {
				throw new WebSocketException("unsupported scheme for WebSockets URI");
			}

			this.mWsSubprotocols = subprotocols;
			this.mWebSocketObserver = connectionObserver;
			this.mOptions = new WebSocketOptions(options);

			connect();
		}
	}

	public void disconnect() {
		if (mWebSocketWriter != null) {
			mWebSocketWriter.forward(new WebSocketMessage.Close());
		} else {
			Log.d(TAG, "Could not send WebSocket Close .. writer already null");
		}

		this.mPreviousConnection = false;
	}

	/**
	 * Reconnect to the server with the latest options 
	 * @return true if reconnection performed
	 */
	public boolean reconnect() {
		if (!isConnected() && (mWebSocketURI != null)) {
			connect();
			return true;
		}
		return false;
	}

	public void connect() {
		SocketThread socketThread = new SocketThread(mWebSocketURI, mOptions);
		
		socketThread.start();
		synchronized (socketThread) {
			try {
				socketThread.wait();
			} catch (InterruptedException e) {
			}
		}
		
		this.mTransportChannel = socketThread.getSocketChannel();
		if (mTransportChannel == null) {
			onClose(WebSocketCloseNotification.CANNOT_CONNECT, socketThread.getFailureMessage());
		} else if (mTransportChannel.isConnected()) {
			try {
				createReader();
				createWriter();

				WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(mWebSocketURI, null, mWsSubprotocols);
				mWebSocketWriter.forward(hs);
			} catch (Exception e) {
				onClose(WebSocketCloseNotification.INTERNAL_ERROR, e.getLocalizedMessage());
			}
		} else {
			onClose(WebSocketCloseNotification.CANNOT_CONNECT, "could not connect to WebSockets server");
		}
	}
	
	/**
	 * Perform reconnection
	 * 
	 * @return true if reconnection was scheduled
	 */
	protected boolean scheduleReconnect() {
		/**
		 * Reconnect only if:
		 *  - connection active (connected but not disconnected)
		 *  - has previous success connections
		 *  - reconnect interval is set
		 */
		int interval = mOptions.getReconnectInterval();
		boolean shouldReconnect = mTransportChannel.isConnected() && mPreviousConnection && (interval > 0);
		if (shouldReconnect) {
			Log.d(TAG, "WebSocket reconnection scheduled");
			mHandler.postDelayed(new Runnable() {

				public void run() {
					Log.d(TAG, "WebSocket reconnecting...");
					reconnect();
				}
			}, interval);
		}
		return shouldReconnect;
	}

	/**
	 * Common close handler
	 * 
	 * @param code       Close code.
	 * @param reason     Close reason (human-readable).
	 */
	private void onClose(WebSocketCloseNotification code, String reason) {
		boolean reconnecting = false;

		if ((code == WebSocketCloseNotification.CANNOT_CONNECT) || (code == WebSocketCloseNotification.CONNECTION_LOST)) {
			reconnecting = scheduleReconnect();
		}

		if (mWebSocketObserver != null) {
			try {
				if (reconnecting) {
					mWebSocketObserver.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.RECONNECT, reason);
				} else {
					mWebSocketObserver.onClose(code, reason);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			Log.d(TAG, "mWebSocketObserver already NULL");
		}
	}


	/**
	 * Create master message handler.
	 */
	protected void createHandler() {
		this.mHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {

				if (msg.obj instanceof WebSocketMessage.TextMessage) {

					WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) msg.obj;

					if (mWebSocketObserver != null) {
						mWebSocketObserver.onTextMessage(textMessage.mPayload);
					} else {
						Log.d(TAG, "could not call onTextMessage() .. handler already NULL");
					}

				} else if (msg.obj instanceof WebSocketMessage.RawTextMessage) {

					WebSocketMessage.RawTextMessage rawTextMessage = (WebSocketMessage.RawTextMessage) msg.obj;

					if (mWebSocketObserver != null) {
						mWebSocketObserver.onRawTextMessage(rawTextMessage.mPayload);
					} else {
						Log.d(TAG, "could not call onRawTextMessage() .. handler already NULL");
					}

				} else if (msg.obj instanceof WebSocketMessage.BinaryMessage) {

					WebSocketMessage.BinaryMessage binaryMessage = (WebSocketMessage.BinaryMessage) msg.obj;

					if (mWebSocketObserver != null) {
						mWebSocketObserver.onBinaryMessage(binaryMessage.mPayload);
					} else {
						Log.d(TAG, "could not call onBinaryMessage() .. handler already NULL");
					}

				} else if (msg.obj instanceof WebSocketMessage.Ping) {

					WebSocketMessage.Ping ping = (WebSocketMessage.Ping) msg.obj;
					Log.d(TAG, "WebSockets Ping received");

					// reply with Pong
					WebSocketMessage.Pong pong = new WebSocketMessage.Pong();
					pong.mPayload = ping.mPayload;
					mWebSocketWriter.forward(pong);

				} else if (msg.obj instanceof WebSocketMessage.Pong) {

					@SuppressWarnings("unused")
					WebSocketMessage.Pong pong = (WebSocketMessage.Pong) msg.obj;

					Log.d(TAG, "WebSockets Pong received");

				} else if (msg.obj instanceof WebSocketMessage.Close) {

					WebSocketMessage.Close close = (WebSocketMessage.Close) msg.obj;

					Log.d(TAG, "WebSockets Close received (" + close.getCode() + " - " + close.getReason() + ")");

					mWebSocketWriter.forward(new WebSocketMessage.Close(WebSocketCloseCode.NORMAL));

				} else if (msg.obj instanceof WebSocketMessage.ServerHandshake) {

					WebSocketMessage.ServerHandshake serverHandshake = (WebSocketMessage.ServerHandshake) msg.obj;

					Log.d(TAG, "opening handshake received");

					if (serverHandshake.mSuccess) {
						if (mWebSocketObserver != null) {
							mWebSocketObserver.onOpen();
						} else {
							Log.d(TAG, "could not call onOpen() .. handler already NULL");
						}
						mPreviousConnection = true;
					}

				} else if (msg.obj instanceof WebSocketMessage.ConnectionLost) {

					@SuppressWarnings("unused")
					WebSocketMessage.ConnectionLost connectionLost = (WebSocketMessage.ConnectionLost) msg.obj;
//					connectionLost.
					failConnection(WebSocketCloseNotification.CONNECTION_LOST, "WebSockets connection lost");

				} else if (msg.obj instanceof WebSocketMessage.ProtocolViolation) {

					@SuppressWarnings("unused")
					WebSocketMessage.ProtocolViolation protocolViolation = (WebSocketMessage.ProtocolViolation) msg.obj;
					failConnection(WebSocketCloseNotification.PROTOCOL_ERROR, "WebSockets protocol violation");

				} else if (msg.obj instanceof WebSocketMessage.Error) {

					WebSocketMessage.Error error = (WebSocketMessage.Error) msg.obj;
					failConnection(WebSocketCloseNotification.INTERNAL_ERROR, "WebSockets internal error (" + error.mException.toString() + ")");

				} else if (msg.obj instanceof WebSocketMessage.ServerError) {

					WebSocketMessage.ServerError error = (WebSocketMessage.ServerError) msg.obj;
					failConnection(WebSocketCloseNotification.SERVER_ERROR, "Server error " + error.mStatusCode + " (" + error.mStatusMessage + ")");

				} else {

					processAppMessage(msg.obj);

				}
			}
		};
	}


	protected void processAppMessage(Object message) {
	}


	/**
	 * Create WebSockets background writer.
	 */
	protected void createWriter() {

		mWriterThread = new HandlerThread(WS_WRITER);
		mWriterThread.start();
		mWebSocketWriter = new WebSocketWriter(mWriterThread.getLooper(), mHandler, mTransportChannel, mOptions);

		Log.d(TAG, "WS writer created and started");
	}


	/**
	 * Create WebSockets background reader.
	 */
	protected void createReader() {

		mWebSocketReader = new WebSocketReader(mHandler, mTransportChannel, mOptions, WS_READER);
		mWebSocketReader.start();

		Log.d(TAG, "WS reader created and started");
	}
	
	
	
	private static class SocketThread extends Thread {	
		private final URI mWebSocketURI;
		private final WebSocketOptions mWebSocketOptions;
		
		private SocketChannel mSocketChannel = null;
		private String mFailureMessage = null;
		
		
		
		public SocketThread(URI uri, WebSocketOptions options) {
			this.setName(WS_CONNECTOR);
			this.mWebSocketURI = uri;
			this.mWebSocketOptions = options;
		}
		
		
		
		@Override
		public void run() {
			synchronized (this) {
				connect();
				notifyAll();
			}
		}



		public void connect() {	
			try {
				String host = mWebSocketURI.getHost();
				int port = mWebSocketURI.getPort();
				if (port == -1) {
					if (mWebSocketURI.getScheme().equals(WSS_URI_SCHEME)) {
						port = 443;
					} else {
						port = 80;
					}
				}

				SocketChannel socketChannel = SocketChannel.open();
				socketChannel.socket().connect(new InetSocketAddress(host, port), mWebSocketOptions.getSocketConnectTimeout());
				socketChannel.socket().setSoTimeout(mWebSocketOptions.getSocketReceiveTimeout());
				socketChannel.socket().setTcpNoDelay(mWebSocketOptions.getTcpNoDelay());
				this.mSocketChannel = socketChannel;
			} catch (IOException e) {
				this.mFailureMessage = e.getLocalizedMessage();
			}
		}
		
		public SocketChannel getSocketChannel() {
			return mSocketChannel;
		}
		public String getFailureMessage() {
			return mFailureMessage;
		}
	}
}
