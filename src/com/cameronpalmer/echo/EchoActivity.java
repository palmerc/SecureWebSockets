package com.cameronpalmer.echo;

import java.net.URI;
import java.net.URISyntaxException;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;




public class EchoActivity extends Activity implements WebSocketConnectionObserver {
	private static final String WS_ECHO_SERVER = "ws://echo.websocket.org";
	private static final String WSS_ECHO_SERVER = "wss://echo.websocket.org";

	private Handler mHandler;

	private EditText mMessageEditText;
	private ScrollView mResponseScrollView;
	private LinearLayout mResponseLinearLayout;
	private Button mConnectButton;
	private Button mSendButton;
	private Button mRepeatButton;

	private WebSocketConnection mConnection;
	private URI mServerURI;

	private int mMessageIndex = 1;
	private volatile boolean mIsConnected = false;
	private volatile boolean mIsRepeating = false;
	private volatile boolean mTLSEnabled = false;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_echo);

		this.mHandler = new Handler();

		this.mMessageEditText = (EditText) findViewById(R.id.message_text);
		this.mResponseScrollView = (ScrollView) findViewById(R.id.response_scrollView);
		this.mResponseLinearLayout = (LinearLayout) findViewById(R.id.response_linearLayout);

		this.mConnectButton = (Button) findViewById(R.id.connect_button);
		mConnectButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mIsConnected) {
					disconnect();
				} else {
					connect();
				}				
			}
		});

		this.mSendButton = (Button) findViewById(R.id.send_button);
		mSendButton.setEnabled(false);
		mSendButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				send();
			}
		});

		this.mRepeatButton = (Button) findViewById(R.id.repeat_button);
		mRepeatButton.setEnabled(false);
		mRepeatButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (!mIsRepeating) {
					mRepeatButton.setText("Stop");
					mSendButton.setEnabled(false);
					repeat();
				} else {
					mRepeatButton.setText("Repeat");
					mSendButton.setEnabled(true);
				}
				mIsRepeating = !mIsRepeating;
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_echo, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_tls_enable:
			tlsToggle(item);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mConnection == null) {
			this.mConnection = new WebSocketConnection();
		}
	}



	public void connect() {
		try {
			if (mTLSEnabled) {
				this.mServerURI = new URI(WSS_ECHO_SERVER);
			} else {
				this.mServerURI = new URI(WS_ECHO_SERVER);
			}

			mConnection.connect(mServerURI, this);
		} catch (WebSocketException e) {
			String message = e.getLocalizedMessage();
			Log.e(getClass().getCanonicalName(), message);
			displayResponse(message);
		} catch (URISyntaxException e) {
			String message = e.getLocalizedMessage();
			Log.e(getClass().getCanonicalName(), message);
			displayResponse(message);
		}
	}

	public void disconnect() {
		mConnection.disconnect();
	}

	public void tlsToggle(MenuItem item) {
		mTLSEnabled = !mTLSEnabled;

		if (mTLSEnabled) {
			item.setTitle("Disable TLS");
		} else {
			item.setTitle("Enable TLS");
		}
	}

	public void send() {
		if (mIsConnected) {
			String message = mMessageEditText.getText().toString();

			mConnection.sendTextMessage(message);
		}
	}

	public void repeat() {
		if (mIsConnected) {			
			mHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					if (mIsRepeating) {
						send();
						repeat();
					}
				}
			}, 1000);
		}
	}

	public void displayResponse(String message) {		
		TextView textView = new TextView(this);
		textView.setText(mMessageIndex + ", " + message);
		mResponseLinearLayout.addView(textView);

		mHandler.post(new Runnable() {

			@Override
			public void run() {
				mResponseScrollView.fullScroll(View.FOCUS_DOWN);				
			}
		});

		mMessageIndex++;
	}



	//
	// WebSocket Handler callbacks
	@Override
	public void onOpen() {
		this.mIsConnected = true;

		mConnectButton.setText("Disconnect");
		mSendButton.setEnabled(true);
		mRepeatButton.setEnabled(true);

		String message = "Connection opened to: " + WS_ECHO_SERVER;
		Log.d(getClass().getCanonicalName(), message);
		displayResponse(message);
	}

	@Override
	public void onClose(WebSocketCloseType code, String reason) {
		this.mIsConnected = false;
		this.mIsRepeating = false;

		mConnectButton.setText("Connect");
		mRepeatButton.setText("Repeat");
		mSendButton.setEnabled(false);
		mRepeatButton.setEnabled(false);

		String message = "Close: " + code.name() + ", " + reason;
		Log.d(getClass().getCanonicalName(), message);
		displayResponse(message);
	}

	@Override
	public void onTextMessage(String payload) {
		String message = "ECHO: " + payload;

		Log.d(getClass().getCanonicalName(), message);
		displayResponse(message);
	}

	@Override
	public void onRawTextMessage(byte[] payload) {
	}

	@Override
	public void onBinaryMessage(byte[] payload) {
	}
}
