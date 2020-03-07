package com.caddish_hedgehog.hedgecam2;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.SparseIntArray;

public class Sound {
	private static final String TAG = "HedgeCam/Sound";

	enum ShutterSound {
		DEFAULT,
		CUSTOM,
		OFF,
		FORCE_OFF
	}

	private static Context context;
	private static boolean using_camera_2;

	private static SoundPool sound_pool;
	private static SparseIntArray sound_ids;
	private static int sound_shutter = 0;
	
	private static volatile TextToSpeech textToSpeech;
	private static volatile boolean textToSpeechSuccess;

	private static MediaActionSound media_action_sound;
	
	private static ShutterSound soundShutter;
	private static boolean soundVideo;
	private static boolean soundTimer;
	private static boolean soundTimerStart;
	private static boolean soundTimerSpeak;
	private static boolean soundFaceDetection;
	private static float sound_volume;
	
	private static boolean shutterSoundAllowed;

	@SuppressWarnings("deprecation")
	public static void init(Context c, boolean camera_2) {
		if( MyDebug.LOG )
			Log.d(TAG, "init()");

		context = c;
		using_camera_2 = camera_2;

		String shutter_sound = Prefs.getString(Prefs.SHUTTER_SOUND, "default");
		switch (shutter_sound) {
			case "default":
				soundShutter = ShutterSound.DEFAULT;
				break;
			case "off":
				soundShutter = ShutterSound.OFF;
				break;
			case "force_off":
				if (camera_2)
					soundShutter = ShutterSound.OFF;
				else
					soundShutter = ShutterSound.FORCE_OFF;
				break;
			default:
				soundShutter = ShutterSound.CUSTOM;
		}
		
		soundVideo = Prefs.getBoolean(Prefs.VIDEO_SOUND, true);
		soundTimer = Prefs.getBoolean(Prefs.TIMER_BEEP, true);
		soundTimerStart = Prefs.getBoolean(Prefs.TIMER_START_SOUND, true);
		soundTimerSpeak = Prefs.getBoolean(Prefs.TIMER_SPEAK, false);
		soundFaceDetection = Prefs.getBoolean(Prefs.FACE_DETECTION_SOUND, false);
		
		if( sound_pool == null ) {
			int audio_stream = AudioManager.STREAM_SYSTEM;
			String stream = Prefs.getString(Prefs.AUDIO_STREAM, "system");
			if (stream.equals("notification"))
				audio_stream = AudioManager.STREAM_NOTIFICATION;
			else if (stream.equals("music"))
				audio_stream = AudioManager.STREAM_MUSIC;

			if( MyDebug.LOG )
				Log.d(TAG, "create new sound_pool");
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
				AudioAttributes audio_attributes = new AudioAttributes.Builder()
					.setLegacyStreamType(audio_stream)
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build();
				sound_pool = new SoundPool.Builder()
					.setMaxStreams(1)
					.setAudioAttributes(audio_attributes)
					.build();
			}
			else {
				sound_pool = new SoundPool(1, audio_stream, 0);
			}
			sound_ids = new SparseIntArray();
			if (soundTimer || soundTimerStart) {
				loadSound(R.raw.beep);
				loadSound(R.raw.beep_hi);
			}
			if (soundFaceDetection) {
				loadSound(R.raw.double_beep);
				loadSound(R.raw.double_beep_hi);
			}

			if (soundShutter == ShutterSound.CUSTOM) {
				int resource_id = context.getResources().getIdentifier(shutter_sound, "raw", context.getPackageName());
				
				if (resource_id != 0)
					sound_shutter = sound_pool.load(context, resource_id, 1);
			}

			switch(Prefs.getString(Prefs.SOUND_VOLUME, "max")) {
				case "high":
					sound_volume = 0.5f;
					break;
				case "medium":
					sound_volume = 0.25f;
					break;
				case "low":
					sound_volume = 0.125f;
					break;
				case "min":
					sound_volume = 0.063f;
					break;
				default:
					sound_volume = 1.0f;
			}
		}
		
		if (using_camera_2 && (soundVideo || soundShutter == ShutterSound.DEFAULT)) {
			// preload sounds to reduce latency - important so that START_VIDEO_RECORDING sound doesn't play after video has started (which means it'll be heard in the resultant video)
			media_action_sound = new MediaActionSound();
			if (soundVideo) {
				media_action_sound.load(MediaActionSound.START_VIDEO_RECORDING);
				media_action_sound.load(MediaActionSound.STOP_VIDEO_RECORDING);
			}
			if (soundShutter == ShutterSound.DEFAULT)
				media_action_sound.load(MediaActionSound.SHUTTER_CLICK);
		}

		// initialise text to speech engine
		if (soundTimerSpeak && textToSpeech == null) {
			textToSpeechSuccess = false;
			// run in separate thread so as to not delay startup time
			new Thread(new Runnable() {
				public void run() {
					textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
						@Override
						public void onInit(int status) {
							if( MyDebug.LOG )
								Log.d(TAG, "TextToSpeech initialised");
							if( status == TextToSpeech.SUCCESS ) {
								textToSpeechSuccess = true;
								if( MyDebug.LOG )
									Log.d(TAG, "TextToSpeech succeeded");
							}
							else {
								if( MyDebug.LOG )
									Log.d(TAG, "TextToSpeech failed");
							}
						}
					});
				}
			}).start();
		}
	}
	
	public static void release() {
		if( MyDebug.LOG )
			Log.d(TAG, "release()");

		if( sound_pool != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "release sound_pool");
			sound_pool.release();
			sound_pool = null;
			sound_ids = null;
			sound_shutter = 0;
		}
		if (media_action_sound != null) {
			if( MyDebug.LOG )
				Log.d(TAG, "release media_action_sound");
			media_action_sound.release();
			media_action_sound = null;
		}
		if( textToSpeech != null ) {
			// http://stackoverflow.com/questions/4242401/tts-error-leaked-serviceconnection-android-speech-tts-texttospeech-solved
			if( MyDebug.LOG )
				Log.d(TAG, "free textToSpeech");
			textToSpeech.stop();
			textToSpeech.shutdown();
			textToSpeech = null;
		}
		system_sound_handler.removeCallbacks(system_sound_runnable);
		restoreSystemSound();
	}
	
	public static void reload() {
		if( MyDebug.LOG )
			Log.d(TAG, "reload()");

		release();
		init(context, using_camera_2);
	}
	
	@SuppressWarnings("deprecation")
	private static void speak(String text) {
		if( MyDebug.LOG )
			Log.d(TAG, "speak()");

		if( textToSpeech != null && textToSpeechSuccess ) {
			textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
		}
	}

	// must be called before playSound (allowing enough time to load the sound)
	private static void loadSound(int resource_id) {
		if( MyDebug.LOG )
			Log.d(TAG, "loadSound()");

		if( sound_pool != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "loading sound resource: " + resource_id);
			int sound_id = sound_pool.load(context, resource_id, 1);
			if( MyDebug.LOG )
				Log.d(TAG, "	loaded sound: " + sound_id);
			sound_ids.put(resource_id, sound_id);
		}
	}
	
	// must call loadSound first (allowing enough time to load the sound)
	static void playSound(int resource_id) {
		if( MyDebug.LOG )
			Log.d(TAG, "playSound()");

		if( sound_pool != null ) {
			if( sound_ids.indexOfKey(resource_id) < 0 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resource not loaded: " + resource_id);
			}
			else {
				int sound_id = sound_ids.get(resource_id);
				if( MyDebug.LOG )
					Log.d(TAG, "play sound: " + sound_id);
				doPlaySound(sound_id);
			}
		}
	}
	
	public static void enableShutterSound(boolean enable, CameraController camera_controller) {
		if( MyDebug.LOG )
			Log.d(TAG, "enableShutterSound()");

		shutterSoundAllowed = false;
		if (enable && (soundShutter == ShutterSound.DEFAULT || soundShutter == ShutterSound.CUSTOM)) {
			shutterSoundAllowed = true;
		} 
		if (!using_camera_2) {
			camera_controller.enableShutterSound(enable && soundShutter == ShutterSound.DEFAULT);
		}
	}

	public static void playShutterSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "playShutterSound()");

		if (using_camera_2 && shutterSoundAllowed && soundShutter == ShutterSound.DEFAULT && media_action_sound != null)
			media_action_sound.play(MediaActionSound.SHUTTER_CLICK);
		else if (shutterSoundAllowed && soundShutter == ShutterSound.CUSTOM && sound_shutter != 0)
			doPlaySound(sound_shutter);
		else if (soundShutter == ShutterSound.FORCE_OFF)
			disableSystemSound();
	}
	
	public static void playVideoStartSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "playVideoStartSound()");

		if (!using_camera_2 || !soundVideo || media_action_sound == null)
			return;
		media_action_sound.play(MediaActionSound.START_VIDEO_RECORDING);
	}
	
	public static void playVideoStopSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "playVideoStopSound()");

		if (!using_camera_2 || !soundVideo || media_action_sound == null)
			return;
		media_action_sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
	}

	public static void timerBeep(long remaining_time) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "timerBeep()");
			Log.d(TAG, "remaining_time: " + remaining_time);
		}
		if( soundTimer ) {
			if( MyDebug.LOG )
				Log.d(TAG, "play beep!");
			boolean is_last = remaining_time <= 1000;
			playSound(is_last ? R.raw.beep_hi : R.raw.beep);
		}
		if( soundTimerSpeak ) {
			if( MyDebug.LOG )
				Log.d(TAG, "speak countdown!");
			int remaining_time_s = (int)(remaining_time/1000);
			if( remaining_time_s <= 60 )
				speak("" + remaining_time_s);
		}
	}

	private static void doPlaySound(final int sound_id) {
		if( MyDebug.LOG )
			Log.d(TAG, "doPlaySound()");

		sound_pool.play(sound_id, sound_volume, sound_volume, 0, 0, 1);
	}
	
	private static boolean isVolumeChanged = false;
	private static int currentVolume = 0;
	private static Handler system_sound_handler = new Handler();
	private static Runnable system_sound_runnable = new Runnable() {
		@Override
		public void run(){
			restoreSystemSound();
	   }
	};

	private static void disableSystemSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "disableSystemSound()");

		AudioManager audio = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		currentVolume = audio.getStreamVolume(AudioManager.STREAM_SYSTEM);
		audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
		isVolumeChanged = true;
		system_sound_handler.removeCallbacks(system_sound_runnable);
		system_sound_handler.postDelayed(system_sound_runnable, 500);
	}

	private static void restoreSystemSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "restoreSystemSound()");

		if (isVolumeChanged){
			AudioManager audio = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
			audio.setStreamVolume(AudioManager.STREAM_SYSTEM, currentVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
			isVolumeChanged = false;
		}
	}	

}