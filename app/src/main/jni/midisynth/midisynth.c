/*
 * OpenSL ES audio output for Sonivox EAS synthesizer in real time
 * Copyright (C) 2013 Pedro López-Cabanillas <plcl@users.sf.net>
 *
 * Based on opensl_stream, by Peter Brinkmann
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

#include <jni.h>
#include <stdlib.h>
#include <android/log.h>
#include <eas.h>
#include <eas_reverb.h>
#include <eas_chorus.h>
#include <opensl_stream.h>

#define LOGI(...) \
  __android_log_print(ANDROID_LOG_INFO, "midisynth", __VA_ARGS__)
#define LOGW(...) \
  __android_log_print(ANDROID_LOG_WARN, "midisynth", __VA_ARGS__)

struct LibraryContext {
  int sampleRate, bufferSize, channels;
  EAS_DATA_HANDLE easData;
  EAS_HANDLE easHandle;
  OPENSL_STREAM *os;
};

// Audio processing callback
static void process(void *context, int sample_rate, int buffer_frames,
					int input_channels, const short *input_buffer,
					int output_channels, short *output_buffer)
{
    EAS_RESULT eas_res;
    EAS_I32 numGen;
    EAS_PCM *buffer;
	struct LibraryContext *lc = (struct LibraryContext *) context;
    if (lc != NULL && lc->easData != NULL)
    {
		buffer = (EAS_PCM *) output_buffer;
		eas_res = EAS_Render(lc->easData, buffer, lc->bufferSize, &numGen);
		if (eas_res != EAS_SUCCESS) {
			//LOGW("EAS_Render error: %ld", eas_res);
		}
    }
}

JNIEXPORT jobject JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_open(JNIEnv *env, jclass clazz)
{
  EAS_RESULT eas_res;
  EAS_DATA_HANDLE dataHandle;
  EAS_HANDLE handle;
  int bufferFrames = 0;

  const S_EAS_LIB_CONFIG *easConfig = EAS_Config();
  if (easConfig == NULL) {
	  //LOGW("EAS_Config returns null");
	  return NULL;
  }

  eas_res = EAS_Init(&dataHandle);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_Init error: %ld", eas_res);
	return NULL;
  }

  eas_res = EAS_OpenMIDIStream(dataHandle, &handle, NULL);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_OpenMIDIStream error: %ld", eas_res);
	EAS_Shutdown(dataHandle);
	return NULL;
  }

  struct LibraryContext *lc = malloc(sizeof(struct LibraryContext));
  if (lc) {
    lc->os = opensl_open(easConfig->sampleRate, 0, easConfig->numChannels, easConfig->mixBufferSize, process, lc);
    if (!lc->os) {
      //LOGW("opensl_open returns null");
      free(lc);
      lc = NULL;
      EAS_Shutdown(dataHandle);
      return NULL;
    }
    lc->easData = dataHandle;
    lc->easHandle = handle;
    lc->sampleRate = easConfig->sampleRate;
    lc->bufferSize = easConfig->mixBufferSize;
    lc->channels = easConfig->numChannels;
    //LOGI("EAS bufferSize=%d, sampleRate=%d, channels=%d", lc->bufferSize, lc->sampleRate, lc->channels);
  }
  return (*env)->NewDirectByteBuffer(env, (void *) lc, sizeof(struct LibraryContext));
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_close(JNIEnv *env, jclass clazz, jobject ctx)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  EAS_RESULT eas_res;

  if (lc->easData != NULL && lc->easHandle != NULL) {
	eas_res = EAS_CloseMIDIStream(lc->easData, lc->easHandle);
	if (eas_res != EAS_SUCCESS) {
		//LOGW("EAS_CloseMIDIStream error: %ld", eas_res);
	}
	eas_res = EAS_Shutdown(lc->easData);
	if (eas_res != EAS_SUCCESS) {
		//LOGW("EAS_Shutdown error: %ld", eas_res);
	}
  }

  opensl_close(lc->os);
  free(lc);
}

JNIEXPORT jint JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_start(JNIEnv *env, jclass clazz, jobject ctx)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  return opensl_start(lc->os);
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_stop(JNIEnv *env, jclass clazz, jobject ctx)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  opensl_pause(lc->os);
}

JNIEXPORT jboolean JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_isRunning(JNIEnv *env, jclass clazz, jobject ctx)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  return opensl_is_running(lc->os);
}

JNIEXPORT jboolean JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_write(JNIEnv *env, jclass clazz, jobject ctx, jbyteArray ba)
{
    EAS_RESULT eas_res = EAS_ERROR_ALREADY_STOPPED;
    EAS_I32 count;
    EAS_U8 *buffer;
    jboolean bc;

    struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
    if (lc != NULL &&
    	lc->easData != NULL &&
    	lc->easHandle != NULL &&
    	lc->os != NULL &&
    	opensl_is_running(lc->os) != 0)
    {
		buffer = (EAS_U8 *)(*env)->GetByteArrayElements(env, ba, &bc);
		count = (*env)->GetArrayLength(env, ba);
		eas_res = EAS_WriteMIDIStream(lc->easData, lc->easHandle, buffer, count);
		(*env)->ReleaseByteArrayElements(env, ba, (jbyte  *)buffer, 0);
    }
	if (eas_res != EAS_SUCCESS) {
		//LOGW("EAS_WriteMIDIStream error: %ld", eas_res);
	}
	return (eas_res == EAS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_initReverb(JNIEnv *env, jclass clazz, jobject ctx, jint reverb_type)
{
  EAS_RESULT eas_res;
  EAS_BOOL sw = EAS_TRUE;
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  if ( reverb_type >= EAS_PARAM_REVERB_LARGE_HALL && reverb_type <= EAS_PARAM_REVERB_ROOM ) {
	  sw = EAS_FALSE;
	  eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_PRESET, (EAS_I32) reverb_type);
	  if (eas_res != EAS_SUCCESS) {
		//LOGW("EAS_SetParameter error: %ld", eas_res);
	  }
  }
  eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_BYPASS, sw);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_SetParameter error: %ld", eas_res);
  }
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_initChorus(JNIEnv *env, jclass clazz, jobject ctx, jint chorus_type)
{
  EAS_RESULT eas_res;
  EAS_BOOL sw = EAS_TRUE;
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);
  if (chorus_type >= EAS_PARAM_CHORUS_PRESET1 && chorus_type <= EAS_PARAM_CHORUS_PRESET4 ) {
	  sw = EAS_FALSE;
	  eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_CHORUS, EAS_PARAM_CHORUS_PRESET, (EAS_I32) chorus_type);
	  if (eas_res != EAS_SUCCESS) {
		//LOGW("EAS_SetParameter error: %ld", eas_res);
	  }
  }
  eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_CHORUS, EAS_PARAM_CHORUS_BYPASS, sw);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_SetParameter error: %ld", eas_res);
  }
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_setReverbWet(JNIEnv *env, jclass clazz, jobject ctx, jint amount)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);

  EAS_RESULT eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_WET, (EAS_I32) amount);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_SetParameter error: %ld", eas_res);
  }
}

JNIEXPORT void JNICALL Java_io_github_pedrolcl_vmpk_MIDISynth_setChorusLevel(JNIEnv *env, jclass clazz, jobject ctx, jint amount)
{
  struct LibraryContext *lc = (struct LibraryContext *) (*env)->GetDirectBufferAddress(env, ctx);

  EAS_RESULT eas_res = EAS_SetParameter(lc->easData, EAS_MODULE_CHORUS, EAS_PARAM_CHORUS_LEVEL, (EAS_I32) amount);
  if (eas_res != EAS_SUCCESS) {
	//LOGW("EAS_SetParameter error: %ld", eas_res);
  }
}

