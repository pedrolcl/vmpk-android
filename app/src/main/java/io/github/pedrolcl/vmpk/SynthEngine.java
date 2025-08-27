/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SynthEngine implements MidiEngine {

	private MIDISynth synth = null;
	private int mReverb = MIDISynth.REVERB_HALL;
	private int mChorus = -1;

	public SynthEngine(Activity activity) {
		readSettings(activity);
	}

	private void readSettings(Activity activity) {
		try {
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
			String defaultReverb = activity.getResources().getString(R.string.default_reverb);
			String defaultChorus = activity.getResources().getString(R.string.default_chorus);
			mReverb = Integer.parseInt(sharedPrefs.getString("reverb", defaultReverb));
			mChorus = Integer.parseInt(sharedPrefs.getString("chorus", defaultChorus));
		} catch (Exception ex) {
			Log.d("SynthEngine", "Initialization", ex);
		}
	}

	public void start(Activity activity) {
		readSettings(activity);
		try {
			if (synth == null) {
				Log.d("SynthEngine", "start");
				synth = new MIDISynth();
			}
			synth.start();
			// aplicar settings: tipo de reverb y tipo de chorus
			synth.initReverb(mReverb);
			synth.initChorus(mChorus);
			if (mReverb > -1) {
				synth.reverbWet(25800);
			}
			if (mChorus > -1) {
				synth.chorusLevel(0);
			}
		} catch (Exception ex) {
			Log.e("SynthEngine", "Error:", ex);
		}
	}

	public void stop() {
		if (synth != null) {
			Log.d("SynthEngine", "stop");
			synth.stop();
			synth.close();
			synth = null;
		}
	}

	protected void sendMidi(int m, int n, int v) {
		if (synth != null) {
			byte msg[] = new byte[3];
			msg[0] = (byte) m;
			msg[1] = (byte) n;
			msg[2] = (byte) v;
			synth.write(msg);
		}
	}

	protected void sendMidi(int m, int n) {
		if (synth != null) {
			byte msg[] = new byte[2];
			msg[0] = (byte) m;
			msg[1] = (byte) n;
			synth.write(msg);
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
		switch (ctl) {
			case CTL_REVERB:
				if (synth != null)
					synth.reverbWet(num * 258);
				break;
			case CTL_CHORUS:
				if (synth != null)
					synth.chorusLevel(num * 258);
				break;
			default:
				sendMidi(STATUS_CTLCHG | channel, ctl, num);
				break;
		}
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
