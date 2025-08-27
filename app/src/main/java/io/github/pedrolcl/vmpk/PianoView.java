/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;

public class PianoView extends View {

	private Paint mWhiteBrush = null;
	private Paint mBlackBrush = null;
	private Paint mBlackPen = null;
	private Paint mWhitePen = null;
	private Paint mHiliteBrush = null;
	private Bitmap mBlackKeyBmp = null;
	private Rect mRect = null;
	private ArrayList<PianoKey> mKeys, mReversed;
	private SparseArray<PianoKey> mLast;
	private boolean mShowingNames = false;
	private MidiEngine mEngine;
	private String[] mNoteNames;
	private String[] mPercussionNames;
	private int mNumberOfKeys;
	private int mFirstKey = 0;
	private float mTextSize;
	private float mKeyWidth;
	private float mKeyHeight;
	private int mBaseOctave = 4;
	private int mChannel = 0;
	private int mVelocity = 100;
	private boolean mMono = false;
	private int mPlayingKeys = 0;

	class PianoKey {
		private int mNote = 0;
		private RectF mBounds;
		private boolean mIsBlack = false;
		private boolean mIsPressed = false;
		private Paint mBrush;
		private String mText = null;
		private String mTextPerc = null;
		private float mXText;
		private float mYText;

		PianoKey(boolean b, int n) {
			mIsBlack = b;
			mNote = n;
			mBrush = b ? mBlackBrush : mWhiteBrush;
		}

		void draw(Canvas canvas) {
			String txt = (mChannel == 9 ? mTextPerc : mText);
			canvas.drawRoundRect(mBounds, 4f, 4f, mIsPressed ? mHiliteBrush : mBrush);
			canvas.drawRoundRect(mBounds, 4f, 4f, mBlackPen);
			if (mIsBlack) {
				canvas.drawBitmap(mBlackKeyBmp, null, mBounds, null);
			}
			if (mShowingNames && txt != null) {
				canvas.save();
				canvas.rotate(-90f, mXText, mYText);
				canvas.drawText(txt, mXText, mYText, mIsBlack ? mWhitePen : mBlackPen);
				canvas.restore();
			}
		}

		void setBounds(float x, float y, float w, float h) {
			mBounds = new RectF(x, y, w, h);
			mXText = mBounds.left + mTextSize * 1.5f;
			mYText = mBounds.height() - mTextSize * .7f;
		}

		boolean checkTouched(float x, float y) {
			return mBounds.contains(x, y);
		}

		void setText(int note, int octave) {
			mText = mNoteNames[note] + String.valueOf(octave - 1);
			int n = note + octave * 12 - 27;
			if (n >= 0 && n < mPercussionNames.length) {
				mTextPerc = mPercussionNames[n];
			}
		}
	}

	private void commonCreation(Context context) {
		mNumberOfKeys = context.getResources().getInteger(R.integer.default_keys);
		mFirstKey = context.getResources().getInteger(R.integer.first_key);
		mKeys = new ArrayList<PianoKey>();
		mLast = new SparseArray<PianoKey>();
		mBlackKeyBmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.blkey);

		if (isInEditMode()) {
			mNoteNames = new String[12];
			mNoteNames[0] = "C";
			mNoteNames[1] = "C#";
			mNoteNames[2] = "D";
			mNoteNames[3] = "D#";
			mNoteNames[4] = "E";
			mNoteNames[5] = "F";
			mNoteNames[6] = "F#";
			mNoteNames[7] = "G";
			mNoteNames[8] = "G#";
			mNoteNames[9] = "A";
			mNoteNames[10] = "A#";
			mNoteNames[11] = "B";
			mPercussionNames = new String[1];
			mPercussionNames[0] = "Kick Me";
		} else {
			mNoteNames = context.getResources().getStringArray(R.array.note_names);
			mPercussionNames = context.getResources().getStringArray(R.array.gm_percussions);
		}

		mRect = new Rect();

		mWhiteBrush = new Paint(Paint.ANTI_ALIAS_FLAG);
		mWhiteBrush.setStyle(Paint.Style.FILL);
		mWhiteBrush.setColor(Color.WHITE);

		mBlackBrush = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBlackBrush.setStyle(Paint.Style.FILL);
		mBlackBrush.setColor(Color.BLACK);

		mHiliteBrush = new Paint(Paint.ANTI_ALIAS_FLAG);
		mHiliteBrush.setStyle(Paint.Style.FILL);
		// mHiliteBrush.setColor(context.getResources().getColor(android.R.color.holo_blue_dark));
		mHiliteBrush.setColor(0xff0099cc);

		mWhitePen = new Paint(Paint.ANTI_ALIAS_FLAG);
		mWhitePen.setStyle(Paint.Style.STROKE);
		mWhitePen.setTextSize(20.0f);
		mWhitePen.setColor(Color.WHITE);

		mBlackPen = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBlackPen.setStyle(Paint.Style.STROKE);
		mBlackPen.setTextSize(20.0f);
		mBlackPen.setColor(Color.BLACK);
	}

	public PianoView(Context context) {
		super(context);
		commonCreation(context);
	}

	public PianoView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		commonCreation(context);
	}

	public PianoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		commonCreation(context);
	}

	private void initKeys() {
		if (mRect == null || mRect.isEmpty()) {
			mRect = new Rect();
			getDrawingRect(mRect);
		}
		int numWhiteKeys = (int) Math.ceil(mNumberOfKeys * 7d / 12d);
		int limitOfKeys = mNumberOfKeys + mFirstKey;
		mKeyWidth = mRect.width() / numWhiteKeys;
		mKeyHeight = mKeyWidth * 4;
		if (mKeyHeight > mRect.height()) {
			mKeyHeight = mRect.height();
		}
		// Log.d(VIEW_LOG_TAG, String.format("initKeys - dRect: %s, numberOfKeys: %d",
		// mRect.toShortString(), mNumberOfKeys));
		// Log.d(VIEW_LOG_TAG, String.format("keyWidth=%f, keyHeight=%f", mKeyWidth,
		// mKeyHeight));
		mTextSize = mKeyWidth * .4f; // Text size
		mBlackPen.setTextSize(mTextSize);
		mWhitePen.setTextSize(mTextSize);
		mKeys.clear();
		int adj = mFirstKey % 12;
		if (adj >= 5)
			adj++;
		for (int i = mFirstKey; i < limitOfKeys; ++i) {
			PianoKey key = null;
			float x = 0;
			float y = 2;
			int ocs = i / 12 * 7;
			int n;
			int j = n = i % 12;
			if (j >= 5)
				j++;
			if ((j % 2) == 0) {
				key = new PianoKey(false, i);
				x = (float) (ocs + Math.floor((j - adj) / 2.0f)) * mKeyWidth;
				key.setBounds(x, y, x + mKeyWidth, y + mKeyHeight);
				// Log.d(VIEW_LOG_TAG, String.format("n=%d r=%s", i,
				// key.bounds.toShortString()));
				key.setText(n, i / 12 + mBaseOctave);
				mKeys.add(0, key);
			} else {
				key = new PianoKey(true, i);
				x = (float) (ocs + Math.floor((j - adj) / 2.0f)) * mKeyWidth + mKeyWidth * 6 / 10 + 1;
				key.setBounds(x, y, x + mKeyWidth * 8 / 10 - 1, y + mKeyHeight * 6 / 10);
				key.setText(n, i / 12 + mBaseOctave);
				// Log.d(VIEW_LOG_TAG, String.format("n=%d r=%s", i,
				// key.bounds.toShortString()));
				mKeys.add(key);
			}
		}
		mReversed = new ArrayList<PianoKey>(mKeys);
		Collections.reverse(mReversed);
		invalidate();
	}

	PianoKey getKeyForPos(float x, float y) {
		for (PianoKey k : mReversed) {
			if (k.checkTouched(x, y)) {
				return k;
			}
		}
		return null;
	}

	public void setShowNames(boolean state) {
		if (mShowingNames != state) {
			mShowingNames = state;
			invalidate();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int desiredWidth;
		int desiredHeight;
		int width;
		int height;

		int numWhiteKeys = (int) Math.ceil(mNumberOfKeys * 7d / 12d);

		mKeyWidth = widthSize / numWhiteKeys;
		mKeyHeight = mKeyWidth * 4;
		if (mKeyHeight > heightSize) {
			mKeyHeight = heightSize;
		}
		desiredHeight = (int) mKeyHeight;
		desiredWidth = (int) (mKeyWidth * numWhiteKeys);

		// Measure Width
		if (widthMode == MeasureSpec.EXACTLY) {
			// Must be this size
			width = widthSize;
		} else if (widthMode == MeasureSpec.AT_MOST) {
			// Can't be bigger than...
			width = Math.min(desiredWidth, widthSize);
		} else {
			// Be whatever you want
			width = desiredWidth;
		}
		// Measure Height
		if (heightMode == MeasureSpec.EXACTLY) {
			// Must be this size
			height = heightSize;
		} else if (heightMode == MeasureSpec.AT_MOST) {
			// Can't be bigger than...
			height = Math.min(desiredHeight, heightSize);
		} else {
			// Be whatever you want
			height = desiredHeight;
		}
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (changed) {
			mRect.set(left, top, right, bottom);
			initKeys();
		}
	};

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		for (PianoKey key : mKeys) {
			key.draw(canvas);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		switch (action & MotionEvent.ACTION_MASK) {

			case MotionEvent.ACTION_DOWN: {
				// Log.d(VIEW_LOG_TAG, "ACTION_DOWN");
				float x = ev.getX();
				float y = ev.getY();
				int pId = ev.getPointerId(0);
				PianoKey key = getKeyForPos(x, y);
				keyOn(key, pId);
				// mLast.put(pId, key);
				break;
			}

			case MotionEvent.ACTION_POINTER_DOWN: {
				// Log.d(VIEW_LOG_TAG, "ACTION_POINTER_DOWN:" + ev.getPointerCount());
				int p = getPointerIndex(action);
				// for (int p=0; p<ev.getPointerCount(); ++p) {
				float x = ev.getX(p);
				float y = ev.getY(p);
				int pId = ev.getPointerId(p);
				PianoKey key = getKeyForPos(x, y);
				keyOn(key, pId);
				// mLast.put(pId, key);
				// }
				invalidate();
				break;
			}

			case MotionEvent.ACTION_MOVE: {
				// Log.d(VIEW_LOG_TAG, "ACTION_MOVE:" + ev.getPointerCount());
				for (int p = 0; p < ev.getPointerCount(); ++p) {
					PianoKey lastKey = null;
					int pId = ev.getPointerId(p);
					lastKey = mLast.get(pId);
					if (lastKey != null && lastKey.mIsPressed) {
						float x = ev.getX(p);
						float y = ev.getY(p);
						PianoKey key = getKeyForPos(x, y);
						if (key == null || key != lastKey) {
							keyOff(lastKey, pId);
						}
						if (key != null && key != lastKey && !key.mIsPressed) {
							keyOn(key, pId);
							// mLast.put(pId, key);
						}
					}
				}
				invalidate();
				break;
			}

			case MotionEvent.ACTION_UP: {
				// Log.d(VIEW_LOG_TAG, "ACTION_UP");
				float x = ev.getX();
				float y = ev.getY();
				int pId = ev.getPointerId(0);
				PianoKey key = getKeyForPos(x, y);
				keyOff(key, pId);
				// mLast.delete(pId);
				break;
			}

			case MotionEvent.ACTION_POINTER_UP: {
				// Log.d(VIEW_LOG_TAG, "ACTION_POINTER_UP:" + ev.getPointerCount());
				int p = getPointerIndex(action);
				// for (int p=0; p<ev.getPointerCount(); ++p) {
				float x = ev.getX(p);
				float y = ev.getY(p);
				int pId = ev.getPointerId(p);
				PianoKey key = getKeyForPos(x, y);
				keyOff(key, pId);
				// mLast.delete(pId);
				// }
				break;
			}

			case MotionEvent.ACTION_CANCEL: {
				// Log.d(VIEW_LOG_TAG, "ACTION_CANCEL");
				for (PianoKey key : mKeys) {
					key.mIsPressed = false;
				}
				mLast.clear();
				invalidate();
				mPlayingKeys = 0;
				break;
			}

		}
		return true;
	}

	private int getPointerIndex(int action) {
		return (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
	}

	private void keyOn(PianoKey key, int id) {
		boolean accept = !mMono || (mMono && mPlayingKeys == 0);
		// Log.d(VIEW_LOG_TAG, "accept:" + accept);
		if (key != null && !key.mIsPressed && accept) {
			// Log.d(VIEW_LOG_TAG, "keyOn:" + key.mNote);
			if (mEngine != null) {
				mEngine.noteOn(mChannel, key.mNote + mBaseOctave * 12, mVelocity);
			}
			key.mIsPressed = true;
			invalidate();
			mLast.put(id, key);
			mPlayingKeys++;
		}
	}

	private void keyOff(PianoKey key, int id) {
		if (key != null && key.mIsPressed) {
			// Log.d(VIEW_LOG_TAG, "keyOff:" + key.mNote);
			if (mEngine != null) {
				mEngine.noteOff(mChannel, key.mNote + mBaseOctave * 12, mVelocity);
			}
			key.mIsPressed = false;
			invalidate();
			mLast.delete(id);
			mPlayingKeys--;
		}
	}

	public void setEngine(MidiEngine e) {
		if (e != null && e != mEngine) {
			mEngine = e;
		}
	}

	public void setHighlightColor(int color) {
		if (color != mHiliteBrush.getColor()) {
			mHiliteBrush.setColor(color);
		}
	}

	public int highlightColor() {
		return mHiliteBrush.getColor();
	}

	public void setNumberOfKeys(int nKeys) {
		if (nKeys != mNumberOfKeys) {
			mNumberOfKeys = nKeys;
			initKeys();
		}
	}

	public int numberOfKeys() {
		return mNumberOfKeys;
	}

	public int numberOfWholeOctaves() {
		return mNumberOfKeys / 12;
	}

	public void setFirstKey(int n) {
		if (n != mFirstKey) {
			mFirstKey = n;
			initKeys();
		}
	}

	public int firstKey() {
		return mFirstKey;
	}

	public void setBaseOctave(int n) {
		if (mBaseOctave != n) {
			mBaseOctave = n;
			initKeys();
		}
	}

	public int baseOctave() {
		return mBaseOctave;
	}

	public void setChannel(int n) {
		if (mChannel != n && n >= 0 && n < 16) {
			mChannel = n;
			invalidate();
		}
	}

	public int channel() {
		return mChannel;
	}

	public void setVelocity(int n) {
		if (mVelocity != n && n >= 0 && n < 128) {
			mVelocity = n;
		}
	}

	public int velocity() {
		return mVelocity;
	}

	PianoKey getKey(int note) {
		for (PianoKey k : mKeys) {
			if (k.mNote == note) {
				return k;
			}
		}
		return null;
	}

	public boolean isMono() {
		return mMono;
	}

	public void setMono(boolean newValue) {
		if (newValue != mMono) {
			mMono = newValue;
		}
	}

	// public void demo()
	// {
	// getKey(13).mIsPressed = true;
	// getKey(16).mIsPressed = true;
	// getKey(20).mIsPressed = true;
	// invalidate();
	// }
}
