/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

// thread independiente para gestionar el envío en segundo plano,
// AsyncTask: http://developer.android.com/reference/android/os/AsyncTask.html
public class NetworkMidi implements MidiEngine {

	private MulticastSocket mSocket = null;
	private WifiManager mWifi = null;
	private WifiManager.MulticastLock mLock = null;

	// settings para el numero de puerto y grupo multicast
	static final int DEFAULT_PORT_NO = 21928;
	static final String DEFAULT_GROUP_ADDR = "225.0.0.37";
	// static final int TIME_TO_LIVE = 1;

	private int mPort;
	private InetAddress mGroupAddr;

	private class SocketSenderTask extends AsyncTask<byte[], Void, Void> {
		@Override
		protected Void doInBackground(byte[]... args) {
			try {
				for (byte[] data : args) {
					DatagramPacket pack = new DatagramPacket(data, data.length, mGroupAddr, mPort);
					// mSocket.setTimeToLive(TIME_TO_LIVE);
					mSocket.send(pack);
				}
			} catch (Exception e) {
				Log.e("NetworkMidi", "Packet Sending Error", e);
			}
			return null;
		}
	}

	private NetworkInfo getConnectedWifiNetworkInfo(Activity activity) {
		ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
		/* This is deprecated in API level = 21: */
		NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return info;
		/*
		 * This snippet needs API >= 21:
		 * for(android.net.Network nw : connManager.getAllNetworks()) {
		 * NetworkInfo info = connManager.getNetworkInfo(nw);
		 * if (info != null && nwi.getType() == ConnectivityManager.TYPE_WIFI) {
		 * return info;
		 * }
		 * }
		 * return null;
		 */
	}

	public NetworkMidi(Activity activity) {
		try {
			mWifi = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
			NetworkInfo info = getConnectedWifiNetworkInfo(activity);
			if (info == null || !info.isConnectedOrConnecting()) {
				alertDialog(activity);
			}
			readSettings(activity);
		} catch (Exception ex) {
			alertDialog(activity);
			Log.e("NetworkMidi", "Constructor error", ex);
		}
	}

	private void alertDialog(Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.wifi_dialog_message)
				.setTitle(R.string.wifi_dialog_title)
				.setPositiveButton(android.R.string.ok, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void readSettings(Activity activity) {
		try {
			mGroupAddr = InetAddress.getByName(DEFAULT_GROUP_ADDR);
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
			int defaultPort = activity.getResources().getInteger(R.integer.default_port);
			String defaultGroupAddr = activity.getResources().getString(R.string.default_address);
			mPort = sharedPrefs.getInt("port_number", defaultPort);
			mGroupAddr = InetAddress.getByName(sharedPrefs.getString("ip_address", defaultGroupAddr));
		} catch (Exception ex) {
			Log.e("NetworkMidi", "Initialization Error", ex);
			mPort = DEFAULT_PORT_NO;
		}
	}

	@Override
	public void start(Activity activity) {
		readSettings(activity);
		try {
			mSocket = new MulticastSocket(mPort);
			WifiInfo winfo = mWifi.getConnectionInfo();
			int intaddr = winfo.getIpAddress();
			byte[] byteaddr = new byte[] {
					(byte) (intaddr & 0xff),
					(byte) (intaddr >> 8 & 0xff),
					(byte) (intaddr >> 16 & 0xff),
					(byte) (intaddr >> 24 & 0xff)
			};
			NetworkInterface nic = NetworkInterface.getByInetAddress(InetAddress.getByAddress(byteaddr));
			mSocket.setNetworkInterface(nic);
			mSocket.joinGroup(mGroupAddr);
			if (mWifi != null && mLock == null) {
				mLock = mWifi.createMulticastLock("Log_Tag");
				mLock.acquire();
			}
		} catch (Exception ex) {
			alertDialog(activity);
			Log.e("NetworkMidi", "Socket Error", ex);
		}
	}

	@Override
	public void stop() {
		if (mLock != null) {
			mLock.release();
			mLock = null;
		}
	}

	private void sendMidi(byte[] data) {
		SocketSenderTask task = new SocketSenderTask();
		task.execute(data);
	}

	protected void sendMidi(int m, int n, int v) {
		if (mSocket != null && mLock != null) {
			byte msg[] = new byte[3];
			msg[0] = (byte) m;
			msg[1] = (byte) n;
			msg[2] = (byte) v;
			sendMidi(msg);
		}
	}

	protected void sendMidi(int m, int n) {
		if (mSocket != null && mLock != null) {
			byte msg[] = new byte[2];
			msg[0] = (byte) m;
			msg[1] = (byte) n;
			sendMidi(msg);
		}
	}

	@Override
	public void pitchWheel(int channel, int num) {
		// num >= 0, num <= 16384
		int lsb = num % 0x80;
		int msb = num / 0x80;
		sendMidi(STATUS_BENDER | channel, lsb, msb);
	}

	@Override
	public void channelPressure(int channel, int num) {
		sendMidi(STATUS_CHANAFT | channel, num);
	}

	@Override
	public void programChange(int channel, int num) {
		sendMidi(STATUS_PROGRAM | channel, num);
	}

	@Override
	public void controller(int channel, int ctl, int num) {
		sendMidi(STATUS_CTLCHG | channel, ctl, num);
	}

	@Override
	public void aftertouch(int channel, int note, int num) {
		sendMidi(STATUS_POLYAFT | channel, note, num);
	}

	@Override
	public void noteOn(int channel, int note, int vel) {
		sendMidi(STATUS_NOTEON | channel, note, vel);
	}

	@Override
	public void noteOff(int channel, int note, int vel) {
		sendMidi(STATUS_NOTEOFF | channel, note, vel);
	}

	@Override
	public void panic() {
		for (int ch = 0; ch < 16; ++ch) {
			controller(ch, CTL_ALL_NOTES_OFF, 0);
		}
	}

	@Override
	public void reset() {
		for (int ch = 0; ch < 16; ++ch) {
			controller(ch, CTL_RESET_ALL_CTL, 0);
		}
	}

}
