/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

/*
 * OpenSL ES audio output for Sonivox EAS synthesizer in real time
 * Copyright (C) 2013-2025 Pedro López-Cabanillas <plcl@users.sf.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.pedrolcl.vmpk;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MIDISynth {

  public static final int REVERB_LARGE_HALL = 0;
  public static final int REVERB_HALL = 1;
  public static final int REVERB_CHAMBER = 2;
  public static final int REVERB_ROOM = 3;

  static {
    System.loadLibrary("midisynth");
  }

  private ByteBuffer context;

  /**
   * Constructor
   * 
   * @throws IOException if not supported.
   */
  public MIDISynth() throws IOException {
    context = open();
    if (context == null) {
      throw new IOException("Unsupported");
    }
  }

  /**
   * Must be called before this object is garbage collected. Safe to call more
   * than once.
   */
  public void close() {
    if (context != null) {
      close(context);
      context = null;
    }
  }

  /**
   * Starts the OpenSL audio stream; will have no effect if the object has already
   * been started. May
   * not be called after close() has been called.
   * 
   * @throws IOException if the stream cannot be started.
   */
  public void start() throws IOException {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    if (start(context) != 0) {
      throw new IOException("Unable to start OpenSL stream.");
    }
  }

  /**
   * Stops the OpenSL audio stream; will have no effect if the object has already
   * been started. May
   * not be called after close() has been called.
   */
  public void stop() {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    stop(context);
  }

  /**
   * May not be called after close() has been called.
   * 
   * @return true if the OpenSL audio stream filter is running.
   */
  public boolean isRunning() {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    return isRunning(context);
  }

  public void write(byte[] data) {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    write(context, data);
  }

  public void initReverb(int reverb_type) {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    initReverb(context, reverb_type);
  }

  public void initChorus(int chorus_type) {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    initChorus(context, chorus_type);
  }

  public void reverbWet(int amount) {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    setReverbWet(context, amount);
  }

  public void chorusLevel(int level) {
    if (context == null) {
      throw new IllegalStateException("Stream closed.");
    }
    setChorusLevel(context, level);
  }

  private static native ByteBuffer open();

  private static native void close(ByteBuffer ctx);

  private static native int start(ByteBuffer ctx);

  private static native void stop(ByteBuffer ctx);

  private static native boolean isRunning(ByteBuffer ctx);

  private static native void write(ByteBuffer ctx, byte[] data);

  private static native void initReverb(ByteBuffer ctx, int reverb_type);

  private static native void initChorus(ByteBuffer ctx, int chorus_type);

  private static native void setReverbWet(ByteBuffer ctx, int amount);

  private static native void setChorusLevel(ByteBuffer ctx, int level);

}
