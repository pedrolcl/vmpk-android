/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.Activity;

public interface MidiEngine {
	static final int STATUS_NOTEOFF = 0x80;
	static final int STATUS_NOTEON = 0x90;
	static final int STATUS_POLYAFT = 0xA0;
	static final int STATUS_CTLCHG = 0xB0;
	static final int STATUS_PROGRAM = 0xC0;
	static final int STATUS_CHANAFT = 0xD0;
	static final int STATUS_BENDER = 0xE0;

	static final int BENDER_MIN = -8192;
	static final int BENDER_MAX = 8191;
	static final int BENDER_MID = 0x2000;

	static final int CTL_MODULATION = 1;
	static final int CTL_VOLUME = 7;
	static final int CTL_PAN = 10;
	static final int CTL_EXPRESSION = 11;
	static final int CTL_SUSTAIN = 64;
	static final int CTL_REVERB = 91;
	static final int CTL_CHORUS = 93;
	static final int CTL_ALL_SOUNDS_OFF = 0x78;
	static final int CTL_RESET_ALL_CTL = 0x79;
	static final int CTL_ALL_NOTES_OFF = 0x7B;

	public void start(Activity activity);

	public void stop();

	public void pitchWheel(int channel, int num);

	public void channelPressure(int channel, int num);

	public void programChange(int channel, int num);

	public void controller(int channel, int ctl, int num);

	public void aftertouch(int channel, int note, int num);

	public void noteOn(int channel, int note, int vel);

	public void noteOff(int channel, int note, int vel);

	public void panic();

	public void reset();
}
