/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity implements SensorEventListener {

	private static final boolean DEVELOPER_MODE = false;

	private static final String PREFS_NAME = "vmpk_state";
	private static final int RESULT_SETTINGS = 1;
	private static final int DEFAULT_TAB = 2;
	private static final int DEFAULT_VELOCITY = 100;
	private static final int DEFAULT_GROUP = 0;
	private static final int DEFAULT_INSTRUMENT = 0;
	private static final int DEFAULT_CONTROLLER = 0;
	private static final int SWIPE_MIN_DISTANCE = 200;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

	private static final String STATE_TAB = "stateTab";
	private static final String STATE_CHANNEL = "stateChannel";
	private static final String STATE_OCTAVE = "stateOctave";
	private static final String STATE_VELOCITY = "stateVelociy";
	private static final String STATE_GROUP = "stateGroup";
	private static final String STATE_INSTRUMENT = "stateInstrument";
	private static final String STATE_CONTROLLER = "stateController";
	private static final String STATE_CONTROL = "stateControl#";

	private PianoView mPiano1 = null;
	private PianoView mPiano2 = null;
	private int mChannel = 0;
	private int mGrp = -1;
	private int mInst = -1;
	private int mPgm = -1;
	private ArrayAdapter<String> mAdapter;
	private String[] mAllInstruments;
	private Spinner mSpinGroups;
	private Spinner mSpinInst;
	private Spinner mSpinCtls;
	private MidiEngine mEngine;
	private TabHost mTabs = null;
	private TabWidget mTabWidget;
	private int[] mCtlState;
	private int[] mCtlNum;
	private int[] mCtlDefs;
	private Spinner mSpinChannel;
	private Spinner mSpinOctave;
	private Switch mSwitchMono;

	private float[] mGravity = new float[] { SensorManager.GRAVITY_EARTH, SensorManager.GRAVITY_EARTH,
			SensorManager.GRAVITY_EARTH };
	private SensorManager mSensorManager;
	private Sensor mSensor = null;
	private WindowManager mWindowManager;
	private Display mDisplay;

	private SeekBar mVelocityBar;
	private ToggleButton mVelocityTurn;
	private SeekBar mControlBar;
	private ToggleButton mControlTurn;
	private SeekBar mBenderBar;
	private ToggleButton mBenderTurn;

	private GestureDetector mGestureDetector = null;
	private boolean mFullScreen = false;
	private Menu mMenu = null;

	protected MenuItem mPendingAction = null;

	public MainActivity() {
		super();
		Log.d("MainActivity", "constructor");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (DEVELOPER_MODE) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
					.detectAll()
					.penaltyLog()
					.penaltyFlashScreen()
					.build());
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
					.penaltyLog()
					.build());
		}
		super.onCreate(savedInstanceState);
		Log.d("MainActivity", "onCreate");
		SettingChangeHelper.onMainActivityCreateApplySettings(this);
		setContentView(R.layout.activity_main);
		mGestureDetector = new GestureDetector(this, new GestureListener());
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		if (mSensor == null) {
			mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}
		if (mSensor != null) {
			Log.d("MainActivity", "sensor: " + mSensor.getName());
		}
		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		mDisplay = mWindowManager.getDefaultDisplay();

		boolean sendToSynth = SettingChangeHelper.getCurrentOutput(this);
		mEngine = sendToSynth ? new SynthEngine(this) : new NetworkMidi(this);

		mPiano1 = (PianoView) findViewById(R.id.pianoView1);
		if (mPiano1 != null) {
			mPiano1.setEngine(mEngine);
		}
		mPiano2 = (PianoView) findViewById(R.id.pianoView2);
		if (mPiano2 != null) {
			mPiano2.setEngine(mEngine);
		}

		/*
		 * Common tab
		 */
		mSpinChannel = (Spinner) findViewById(R.id.spinnerChannel);
		if (mSpinChannel != null) {
			mSpinChannel.setSelection(mPiano1.channel(), false);
			mSpinChannel.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
					mPiano1.setChannel(pos);
					mPiano2.setChannel(pos);
					mChannel = pos;
					resetCtlDefaults();
					applyCtlStates();
					mGrp = -1;
					mInst = -1;
					midiPanic();
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
		}

		mSpinOctave = (Spinner) findViewById(R.id.spinnerOctave);
		if (mSpinOctave != null) {
			mSpinOctave.setSelection(mPiano1.baseOctave(), false);
			mSpinOctave.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
					mPiano1.setBaseOctave(pos);
					mPiano2.setBaseOctave(pos - mPiano2.numberOfWholeOctaves());
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
		}

		Button btnReset = (Button) findViewById(R.id.btnReset);
		if (btnReset != null) {
			btnReset.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					midiReset();
					resetCtlDefaults();
					applyCtlStates();
				}
			});
		}

		/*
		 * Velocity tab
		 */

		final TextView velocityText = (TextView) findViewById(R.id.velocityText);
		mVelocityBar = (SeekBar) findViewById(R.id.velocityBar);
		if (mVelocityBar != null) {
			mVelocityBar.setProgress(DEFAULT_VELOCITY);
			mVelocityBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					mPiano1.setVelocity(progress);
					mPiano2.setVelocity(progress);
					velocityText.setText(String.valueOf(progress));
				}
			});
		}
		velocityText.setText(String.valueOf(mVelocityBar.getProgress()));
		mVelocityTurn = (ToggleButton) findViewById(R.id.velocityTurn);
		if (mVelocityTurn != null) {
			mVelocityTurn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked && mSensor == null) {
						warnSensorNull();
						mVelocityTurn.setChecked(false);
					}
					if (!isChecked)
						mVelocityBar.setProgress(100);
				}
			});
		}

		/*
		 * Instrument tab
		 */
		mAllInstruments = getResources().getStringArray(R.array.gm_instruments);
		mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSwitchMono = (Switch) findViewById(R.id.swMono);
		if (mSwitchMono != null) {
			mSwitchMono.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					mPiano1.setMono(isChecked);
					mPiano2.setMono(isChecked);
				}
			});
		}
		mSpinGroups = (Spinner) findViewById(R.id.spinnerGroups);
		if (mSpinGroups != null) {
			mSpinGroups.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
					Log.d("MainActivity", "mSpinGroups.onItemSelected. pos=" + pos);
					replaceInstrumentsForGroup(pos);
					mSpinInst.setSelection(mInst, false);
					changeEngineInstrument();
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
		}
		mSpinInst = (Spinner) findViewById(R.id.spinnerInstruments);
		if (mSpinInst != null) {
			mSpinInst.setAdapter(mAdapter);
			mSpinInst.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
					Log.d("MainActivity", "mSpinInst.onItemSelected. pos=" + pos);
					changeEngineInstrument();
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
		}

		Button btnPanic = (Button) findViewById(R.id.btnPanic);
		if (btnPanic != null) {
			btnPanic.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					midiPanic();
				}
			});
		}

		/*
		 * Controller tab
		 */
		final TextView controlText = (TextView) findViewById(R.id.controlText);
		mCtlNum = getResources().getIntArray(R.array.controller_values);
		mCtlDefs = getResources().getIntArray(R.array.controller_defaults);
		mCtlState = new int[mCtlDefs.length];
		resetCtlDefaults();
		mControlBar = (SeekBar) findViewById(R.id.controlBar);
		mSpinCtls = (Spinner) findViewById(R.id.spinControllers);
		if (mSpinCtls != null) {
			mSpinCtls.setSelection(0, false);
			mSpinCtls.setOnItemSelectedListener(new OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> arg0, View v, int pos, long id) {
					mControlBar.setProgress(mCtlState[pos]);
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
				}
			});
		}
		if (mControlBar != null) {
			mControlBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					int idx = mSpinCtls.getSelectedItemPosition();
					int ctl = mCtlNum[idx];
					mEngine.controller(mChannel, ctl, progress);
					mCtlState[idx] = progress;
					controlText.setText(String.valueOf(progress));
				}
			});
		}
		controlText.setText(String.valueOf(mControlBar.getProgress()));
		mControlTurn = (ToggleButton) findViewById(R.id.controlTurn);
		if (mControlTurn != null) {
			mControlTurn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked && mSensor == null) {
						warnSensorNull();
						mControlTurn.setChecked(false);
					}
					if (!isChecked)
						mControlBar.setProgress(0);
				}
			});
		}

		/*
		 * Bender tab
		 */
		final TextView benderText = (TextView) findViewById(R.id.benderText);
		mBenderBar = (SeekBar) findViewById(R.id.benderBar);
		if (mBenderBar != null) {
			mBenderBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					mBenderBar.setProgress(8192);
					// mEngine.pitchWheel(mChannel, 8192);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					// if (fromUser) {
					mEngine.pitchWheel(mChannel, progress);
					// }
					benderText.setText(String.valueOf(progress - 8192));
				}
			});
		}
		benderText.setText(String.valueOf(mBenderBar.getProgress() - 8192));
		mBenderTurn = (ToggleButton) findViewById(R.id.benderTurn);
		if (mBenderTurn != null) {
			mBenderTurn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked && mSensor == null) {
						warnSensorNull();
						mBenderTurn.setChecked(false);
					}
					if (!isChecked)
						mBenderBar.setProgress(8192);
				}
			});
		}

		/*
		 * Build tabs
		 */

		mTabs = (TabHost) findViewById(R.id.tabhost);
		mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
		Resources res = getResources();
		mTabs.setup();

		addTab(res.getString(R.string.action_common), R.id.tab_common);
		addTab(res.getString(R.string.action_velocity), R.id.tab_velocity);
		addTab(res.getString(R.string.action_instrument), R.id.tab_instrument);
		addTab(res.getString(R.string.action_controller), R.id.tab_controller);
		addTab(res.getString(R.string.action_bender), R.id.tab_bender);

		mTabs.setCurrentTab(DEFAULT_TAB);
		// changeTabbedControlsVisibility(!mFullScreen);
		applyPianoSettings();
	}

	private void warnSensorNull() {
		AlertDialog.Builder bldr = new AlertDialog.Builder(this);
		bldr.setTitle(R.string.sensornull_title);
		bldr.setMessage(R.string.sensornull_message);
		bldr.setNeutralButton(R.string.sensornull_button, null);
		bldr.show();
	}

	private void addTab(String labelId, int contentId) {
		TabHost.TabSpec spec = mTabs.newTabSpec("tab" + labelId);
		View tabIndicator = LayoutInflater.from(this).inflate(R.layout.tab_indicator, mTabWidget, false);
		TextView title = (TextView) tabIndicator.findViewById(R.id.title);
		title.setText(labelId);
		spec.setIndicator(tabIndicator);
		spec.setContent(contentId);
		mTabs.addTab(spec);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.d("MainActivity", "onStart");
		final SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
	}

	/*
	 * @Override
	 * protected void onStop() {
	 * super.onStop();
	 * Log.d("MainActivity", "onStop");
	 * }
	 * 
	 * @Override
	 * protected void onRestart() {
	 * super.onRestart();
	 * Log.d("MainActivity", "onRestart");
	 * }
	 */

	private void restoreState() {
		Log.d("MainActivity", "Restore the last state");
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
		mTabs.setCurrentTab(prefs.getInt(STATE_TAB, DEFAULT_TAB));
		mChannel = prefs.getInt(STATE_CHANNEL, mPiano1.channel());
		mSpinChannel.setSelection(mChannel, false);
		mPiano1.setChannel(mChannel);
		mPiano2.setChannel(mChannel);
		int octave = prefs.getInt(STATE_OCTAVE, mPiano1.baseOctave());
		mSpinOctave.setSelection(octave, false);
		mPiano1.setBaseOctave(octave);
		mPiano2.setBaseOctave(octave - mPiano2.numberOfWholeOctaves());
		int velocity = prefs.getInt(STATE_VELOCITY, DEFAULT_VELOCITY);
		mVelocityBar.setProgress(velocity);
		mPiano1.setVelocity(velocity);
		mPiano2.setVelocity(velocity);
		int grp = prefs.getInt(STATE_GROUP, DEFAULT_GROUP);
		Log.d("MainActivity", "Restore group:" + grp);
		mSpinGroups.setSelection(grp, false);
		replaceInstrumentsForGroup(grp);
		int inst = prefs.getInt(STATE_INSTRUMENT, DEFAULT_INSTRUMENT);
		Log.d("MainActivity", "Restore instrument:" + inst);
		mSpinInst.setSelection(inst, false);
		for (int i = 0; i < mCtlState.length; ++i) {
			String key = String.format("%s#%d", STATE_CONTROL, i);
			mCtlState[i] = prefs.getInt(key, mCtlDefs[i]);
			mEngine.controller(mChannel, mCtlNum[i], mCtlState[i]);
		}
		mSpinCtls.setSelection(prefs.getInt(STATE_CONTROLLER, DEFAULT_CONTROLLER), false);
		mGrp = -1;
		mInst = -1;
		changeEngineInstrument();
	}

	private void replaceInstrumentsForGroup(int grp) {
		int from = grp * 8;
		mAdapter.clear();
		mAdapter.addAll(Arrays.copyOfRange(mAllInstruments, from, from + 8));
	}

	private void saveState() {
		Log.d("MainActivity", "Save the current state");
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(STATE_TAB, mTabs.getCurrentTab());
		editor.putInt(STATE_CHANNEL, mChannel);
		editor.putInt(STATE_OCTAVE, mSpinOctave.getSelectedItemPosition());
		editor.putInt(STATE_VELOCITY, mVelocityBar.getProgress());
		editor.putInt(STATE_GROUP, mSpinGroups.getSelectedItemPosition());
		editor.putInt(STATE_INSTRUMENT, mSpinInst.getSelectedItemPosition());
		editor.putInt(STATE_CONTROLLER, mSpinCtls.getSelectedItemPosition());
		for (int i = 0; i < mCtlState.length; ++i) {
			String key = String.format("%s#%d", STATE_CONTROL, i);
			editor.putInt(key, mCtlState[i]);
		}
		editor.apply();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d("MainActivity", "onDestroy");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d("MainActivity", "onResume");
		mEngine.start(this);
		if (mSensor != null) {
			mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
		}
		restoreState();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d("MainActivity", "onPause");
		if (mSensor != null) {
			mSensorManager.unregisterListener(this);
		}
		mEngine.stop();
		saveState();
	}

	private void midiPanic() {
		mEngine.panic();
		mSpinGroups.setSelection(0, false);
		mSpinInst.setSelection(0, false);
		changeEngineInstrument();
		// mPiano.demo();
	}

	private void midiReset() {
		mEngine.reset();
		mSpinCtls.setSelection(0, false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		mMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_about) {
			showAbout();
			return true;
		} else if (item.getItemId() == R.id.action_help) {
			showHelp();
			return true;
		} else if (item.getItemId() == R.id.action_settings) {
			showSettings();
			return true;
		} else if (item.getItemId() == R.id.action_shownames) {
			toggleShowNoteNames(item);
			return true;
		} else if (item.getItemId() == R.id.action_fullscreen) {
			toggleFullScreen(item);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	protected void pendingAction() {
		if (mPendingAction != null) {
			if (mPendingAction.getItemId() == R.id.action_help) {
				showHelp();
			} else if (mPendingAction.getItemId() == R.id.action_settings) {
				showSettings();
			} else if (mPendingAction.getItemId() == R.id.action_fullscreen) {
				toggleFullScreen(mPendingAction);
			} else {
				mPendingAction = null;
			}
		}
	}

	@Override
	public void onBackPressed() {
		if (mFullScreen) {
			MenuItem item = mMenu.findItem(R.id.action_fullscreen);
			if (item != null) {
				toggleFullScreen(item);
				return;
			}
		}
		super.onBackPressed();
	}

	private void resetCtlDefaults() {
		for (int i = 0; i < mCtlDefs.length; ++i) {
			mCtlState[i] = mCtlDefs[i];
		}
	}

	private void applyCtlStates() {
		for (int i = 0; i < mCtlState.length; ++i) {
			mEngine.controller(mChannel, mCtlNum[i], mCtlState[i]);
		}
		mSpinCtls.setSelection(0, false);
		mControlBar.setProgress(mCtlState[0]);
	}

	private void changeEngineInstrument() {
		int newGrp = mSpinGroups.getSelectedItemPosition();
		int newInst = mSpinInst.getSelectedItemPosition();
		Log.d("MainActivity", "changeEngineInstrument. newGroup=" + newGrp + " newInstrument=" + newInst);
		if (mInst != newInst || mGrp != newGrp) {
			mInst = newInst;
			mGrp = newGrp;
			mPgm = mGrp * 8 + mInst;
			Log.d("MainActivity", "changeEngineInstrument. Program:" + mPgm);
			mEngine.programChange(mChannel, mPgm);
		}
	}

	private void changeTabbedControlsVisibility(boolean showing) {
		int visibility = showing ? View.VISIBLE : View.GONE;
		if (mTabs != null)
			mTabs.setVisibility(visibility);
	}

	private void toggleFullScreen(MenuItem item) {
		mFullScreen = !item.isChecked();
		Window w = getWindow();
		ActionBar actionBar = getActionBar();
		// View mMainView = findViewById(R.id.mainLayout);
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean secondKeybd = sharedPrefs.getBoolean("double_keyboard", true);
		item.setChecked(mFullScreen);
		if (w != null) {
			if (mFullScreen) {
				w.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
				w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				actionBar.hide(); // slides out
				changeTabbedControlsVisibility(false);
				// mMainView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
				// mMainView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
				// mMainView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
				if (secondKeybd) {
					mPiano2.setVisibility(View.VISIBLE);
				}
			} else {
				w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
				w.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
				actionBar.show(); // slides in
				changeTabbedControlsVisibility(true);
				// mMainView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
				mPiano2.setVisibility(View.INVISIBLE);
			}
		}
	}

	private void toggleShowNoteNames(MenuItem item) {
		boolean state = !item.isChecked();
		mPiano1.setShowNames(state);
		mPiano2.setShowNames(state);
		item.setChecked(state);
	}

	private void showSettings() {
		Log.d(getLocalClassName(), "showSettings()");
		Intent i = new Intent(this, SettingsActivity.class);
		startActivityForResult(i, RESULT_SETTINGS);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(getLocalClassName(), "onActivityResult()");
		switch (requestCode) {
			case RESULT_SETTINGS:
				applyPianoSettings();
				SettingChangeHelper.changeSettingsCheck(this);
				break;
		}
	}

	private void applyPianoSettings() {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		int numKeys = sharedPrefs.getInt("number_of_keys",
				getResources().getInteger(R.integer.default_keys));
		int firstKey = Integer.parseInt(sharedPrefs.getString("first_key",
				getResources().getString(R.string.default_firstKey)));
		int color = sharedPrefs.getInt("color", 0xff0099cc);
		// getResources().getColor(android.R.color.holo_blue_dark));

		mPiano1.setNumberOfKeys(numKeys);
		mPiano1.setFirstKey(firstKey);
		mPiano1.setHighlightColor(color);

		mPiano2.setNumberOfKeys(numKeys);
		mPiano2.setFirstKey(firstKey);
		mPiano2.setHighlightColor(color);
	}

	private void showHelp() {
		startActivity(new Intent(this, HelpActivity.class));
	}

	private void showAbout() {
		startActivity(new Intent(this, AboutActivity.class));
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mGestureDetector.onTouchEvent(event);
		return super.onTouchEvent(event);
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float sensorf = 0;
		if (!(mVelocityTurn.isChecked() || mControlTurn.isChecked() || mBenderTurn.isChecked())) {
			return;
		}
		if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
			mGravity[0] = event.values[0];
			mGravity[1] = event.values[1];
			// mGravity[2] = event.values[2];
		} else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			final float alpha = 0.8f;
			mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0];
			mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1];
			// mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2];
		} else
			return;

		switch (mDisplay.getRotation()) {
			case Surface.ROTATION_0:
				sensorf = mGravity[0];
				break;
			case Surface.ROTATION_90:
				sensorf = -mGravity[1];
				break;
			case Surface.ROTATION_180:
				sensorf = -mGravity[0];
				break;
			case Surface.ROTATION_270:
				sensorf = mGravity[1];
				break;
		}
		// Log.d("MainActivity", String.format("SensorChanged(%.2f)", sensorf));

		if (mBenderTurn.isChecked()) {
			mBenderBar.setProgress((int) (sensorf * 819.2f + 8191f));
		}

		if (mVelocityTurn.isChecked()) {
			mVelocityBar.setProgress((int) ((sensorf + SensorManager.GRAVITY_EARTH) * 6.47f));
		}

		if (mControlTurn.isChecked()) {
			mControlBar.setProgress((int) ((sensorf + SensorManager.GRAVITY_EARTH) * 6.47f));
		}
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			return mFullScreen;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			if (mFullScreen &&
					Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY &&
					Math.abs(e2.getRawY() - e1.getRawY()) > SWIPE_MIN_DISTANCE) {
				if (e1.getRawY() < e2.getRawY()) {
					getActionBar().show();
				} else {
					getActionBar().hide();
				}
				return true;
			}
			return false;
		}
	}

}
