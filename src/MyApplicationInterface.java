package com.caddish_hedgehog.hedgecam2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.Preview.ApplicationInterface;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;
import com.caddish_hedgehog.hedgecam2.Preview.VideoProfile;
import com.caddish_hedgehog.hedgecam2.UI.DrawPreview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "HedgeCam/MyApplicationInterface";

	private final MainActivity main_activity;
	private final SharedPreferences sharedPreferences;
	private final LocationSupplier locationSupplier;
	private final GyroSensor gyroSensor;
	private final StorageUtils storageUtils;
	private final DrawPreview drawPreview;
	private final ImageSaver imageSaver;

	private final float panorama_pics_per_screen = 2.0f;

	private File last_video_file = null;
	private Uri last_video_file_saf = null;

	private final Timer subtitleVideoTimer = new Timer();
	private TimerTask subtitleVideoTimerTask;

	private final Rect text_bounds = new Rect();
	private boolean used_front_screen_flash ;
	
	private boolean last_images_saf; // whether the last images array are using SAF or not
	/** This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
	 */
	private static class LastImage {
		public final boolean share; // one of the images in the list should have share set to true, to indicate which image to share
		public final String name;
		final Uri uri;

		LastImage(Uri uri, boolean share) {
			this.name = null;
			this.uri = uri;
			this.share = share;
		}
		
		LastImage(String filename, boolean share) {
			this.name = filename;
			this.uri = Uri.parse("file://" + this.name);
			this.share = share;
		}
	}
	private final List<LastImage> last_images = new ArrayList<>();

	private boolean isVolumeChanged = false;
	private int currentVolume = 0;


	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		this.sharedPreferences = main_activity.getSharedPrefs();
		this.locationSupplier = new LocationSupplier(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
		this.gyroSensor = new GyroSensor(main_activity);
		this.storageUtils = new StorageUtils(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
		this.drawPreview = new DrawPreview(main_activity, this);
		
		this.imageSaver = new ImageSaver(main_activity);
		
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}
	
	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( drawPreview != null ) {
			drawPreview.onDestroy();
		}
		restoreSound();
	}

	LocationSupplier getLocationSupplier() {
		return locationSupplier;
	}

	public GyroSensor getGyroSensor() {
		return gyroSensor;
	}
	
	StorageUtils getStorageUtils() {
		return storageUtils;
	}
	
	ImageSaver getImageSaver() {
		return imageSaver;
	}

	public DrawPreview getDrawPreview() {
		return drawPreview;
	}

	@Override
	public Context getContext() {
		return main_activity;
	}
	
	@Override
	public boolean useCamera2() {
		if( main_activity.supportsCamera2() ) {
			return sharedPreferences.getBoolean(Prefs.USE_CAMERA2, false);
		}
		return false;
	}

	@Override
	public boolean useTextureView() {
		return sharedPreferences.getString(Prefs.PREVIEW_SURFACE, "auto").equals("texture");
	}

	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}

	@Override
	public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizePref");
		VideoMaxFileSize video_max_filesize = new VideoMaxFileSize();
		video_max_filesize.max_filesize = Prefs.getVideoMaxFileSizeUserPref();
		video_max_filesize.auto_restart = Prefs.getVideoRestartMaxFileSizeUserPref();
		
		/* Also if using internal memory without storage access framework, try to set the max filesize so we don't run out of space.
		   is the only way to avoid the problem where videos become corrupt when run out of space - MediaRecorder doesn't stop on
		   its own, and no error is given!
		   If using SD card, it's not reliable to get the free storage (see https://sourceforge.net/p/opencamera/tickets/153/ ).
		   If using storage access framework, in theory we could check if was on internal storage, but risk of getting it wrong...
		   so seems safest to leave (the main reason for using SAF is for SD cards, anyway).
		   */
		if( !storageUtils.isUsingSAF() ) {
			String folder_name = storageUtils.getSaveLocation();
			if( MyDebug.LOG )
				Log.d(TAG, "saving to: " + folder_name);
			boolean is_internal = false;
			if( !folder_name.startsWith("/") ) {
				is_internal = true;
			}
			else {
				// if save folder path is a full path, see if it matches the "external" storage (which actually means "primary", which typically isn't an SD card these days)
				File storage = Environment.getExternalStorageDirectory();
				if( MyDebug.LOG )
					Log.d(TAG, "compare to: " + storage.getAbsolutePath());
				if( folder_name.startsWith( storage.getAbsolutePath() ) )
					is_internal = true;
			}
			if( is_internal ) {
				if( MyDebug.LOG )
					Log.d(TAG, "using internal storage");
				long free_memory = main_activity.freeMemory() * 1024 * 1024;
				final long min_free_memory = 50000000; // how much free space to leave after video
				// min_free_filesize is the minimum value to set for max file size:
				//   - no point trying to create a really short video
				//   - too short videos can end up being corrupted
				//   - also with auto-restart, if is too small we'll end up repeatedly restarting and creating shorter and shorter videos
				final long min_free_filesize = 20000000;
				long available_memory = free_memory - min_free_memory;
				if( test_set_available_memory ) {
					available_memory = test_available_memory;
				}
				if( MyDebug.LOG ) {
					Log.d(TAG, "free_memory: " + free_memory);
					Log.d(TAG, "available_memory: " + available_memory);
				}
				if( available_memory > min_free_filesize ) {
					if( video_max_filesize.max_filesize == 0 || video_max_filesize.max_filesize > available_memory ) {
						video_max_filesize.max_filesize = available_memory;
						// still leave auto_restart set to true - because even if we set a max filesize for running out of storage, the video may still hit a maximum limit before hand, if there's a device max limit set (typically ~2GB)
						if( MyDebug.LOG )
							Log.d(TAG, "set video_max_filesize to avoid running out of space: " + video_max_filesize);
					}
				}
				else {
					if( MyDebug.LOG )
						Log.e(TAG, "not enough free storage to record video");
					throw new NoFreeStorageException();
				}
			}
		}
		
		return video_max_filesize;
	}

	@Override
	public int createOutputVideoMethod() {
		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
			Bundle myExtras = main_activity.getIntent().getExtras();
			if (myExtras != null) {
				Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
				if( intent_uri != null ) {
					if( MyDebug.LOG )
						Log.d(TAG, "save to: " + intent_uri);
					return VIDEOMETHOD_URI;
				}
			}
			// if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
			if( MyDebug.LOG )
				Log.d(TAG, "intent uri not specified");
			// note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
			return VIDEOMETHOD_FILE;
		}
		boolean using_saf = storageUtils.isUsingSAF();
		return using_saf ? VIDEOMETHOD_SAF : VIDEOMETHOD_FILE;
	}

	@Override
	public File createOutputVideoFile(String prefix) throws IOException {
		last_video_file = storageUtils.createOutputMediaFile(prefix, "", "mp4", new Date());
		return last_video_file;
	}

	@Override
	public Uri createOutputVideoSAF(String prefix) throws IOException {
		last_video_file_saf = storageUtils.createOutputMediaFileSAF(prefix, "", "mp4", new Date());
		return last_video_file_saf;
	}

	@Override
	public Uri createOutputVideoUri() {
		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
			Bundle myExtras = main_activity.getIntent().getExtras();
			if (myExtras != null) {
				Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
				if( intent_uri != null ) {
					if( MyDebug.LOG )
						Log.d(TAG, "save to: " + intent_uri);
					return intent_uri;
				}
			}
		}
		throw new RuntimeException(); // programming error if we arrived here
	}

	public boolean isRawPref() {
		if( isImageCaptureIntent() )
			return false;
		return sharedPreferences.getString(Prefs.RAW, "preference_raw_no").equals("preference_raw_yes");
	}

	@Override
	public boolean isTestAlwaysFocus() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test);
		}
		return main_activity.is_test;
	}

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
		drawPreview.clearContinuousFocusMove();
	}

	@Override
	public void onContinuousFocusMove(boolean start) {
		if( MyDebug.LOG )
			Log.d(TAG, "onContinuousFocusMove: " + start);
		drawPreview.onContinuousFocusMove(start);
	}

	private int n_panorama_pics = 0;

	void startPanorama() {
		if( MyDebug.LOG )
			Log.d(TAG, "startPanorama");
		gyroSensor.startRecording();
		n_panorama_pics = 0;
	}

	void stopPanorama() {
		if( MyDebug.LOG )
			Log.d(TAG, "stopPanorama");
		gyroSensor.stopRecording();
		clearPanoramaPoint();
	}

	void setNextPanoramaPoint() {
		if( MyDebug.LOG )
			Log.d(TAG, "setNextPanoramaPoint");
		float camera_angle_y = main_activity.getPreview().getViewAngleY();
		n_panorama_pics++;
		float angle = (float) Math.toRadians(camera_angle_y) * n_panorama_pics;
		setNextPanoramaPoint((float) Math.sin(angle / panorama_pics_per_screen), 0.0f, (float) -Math.cos(angle / panorama_pics_per_screen));
	}

	private void setNextPanoramaPoint(float x, float y, float z) {
		if( MyDebug.LOG )
			Log.d(TAG, "setNextPanoramaPoint : " + x + " , " + y + " , " + z);

		final float target_angle = 2.0f * 0.01745329252f;
		gyroSensor.setTarget(x, y, z, target_angle, new GyroSensor.TargetCallback() {
			@Override
			public void onAchieved() {
				if( MyDebug.LOG )
					Log.d(TAG, "TargetCallback.onAchieved");
				clearPanoramaPoint();
				main_activity.takePicturePressed();
			}
		});
		drawPreview.setGyroDirectionMarker(x, y, z);
	}

	void clearPanoramaPoint() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearPanoramaPoint");
		gyroSensor.clearTarget();
		drawPreview.clearGyroDirectionMarker();
	}

	@Override
	public void touchEvent(MotionEvent event) {
		main_activity.getMainUI().closePopup();
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}
	
	@Override
	public void startingVideo() {
		if( sharedPreferences.getBoolean(Prefs.LOCK_VIDEO, false) ) {
			main_activity.lockScreen();
		}
		main_activity.stopAudioListeners(true); // important otherwise MediaRecorder will fail to start() if we have an audiolistener! Also don't want to have the speech recognizer going off
		main_activity.getMainUI().startingVideo();
		main_activity.getMainUI().destroyPopup(); // as the available popup options change while recording video
		
		main_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	public void startedVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "startedVideo()");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
			if( !( main_activity.getMainUI().inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything() ) ) {
				main_activity.findViewById(R.id.pause_video).setVisibility(View.VISIBLE);
				main_activity.findViewById(R.id.gallery).setVisibility(View.INVISIBLE);
			}
			main_activity.getMainUI().setPauseVideoContentDescription();
		}
		final int video_method = this.createOutputVideoMethod();
		boolean dategeo_subtitles = sharedPreferences.getString(Prefs.VIDEO_SUBTITLE, "preference_video_subtitle_no").equals("preference_video_subtitle_yes");
		if( dategeo_subtitles && video_method != ApplicationInterface.VIDEOMETHOD_URI ) {
			final String preference_stamp_dateformat = sharedPreferences.getString(Prefs.STAMP_DATEFORMAT, "preference_stamp_dateformat_default");
			final String preference_stamp_timeformat = sharedPreferences.getString(Prefs.STAMP_TIMEFORMAT, "preference_stamp_timeformat_default");
			final String preference_stamp_gpsformat = sharedPreferences.getString(Prefs.STAMP_GPSFORMAT, "preference_stamp_gpsformat_default");
			final boolean store_location = Prefs.getGeotaggingPref();
			final boolean store_geo_direction = Prefs.getGeodirectionPref();
			class SubtitleVideoTimerTask extends TimerTask {
				OutputStreamWriter writer;
				private int count = 1;

				private String getSubtitleFilename(String video_filename) {
					if( MyDebug.LOG )
						Log.d(TAG, "getSubtitleFilename");
					int indx = video_filename.indexOf('.');
					if( indx != -1 ) {
						video_filename = video_filename.substring(0, indx);
					}
					video_filename = video_filename + ".srt";
					if( MyDebug.LOG )
						Log.d(TAG, "return filename: " + video_filename);
					return video_filename;
				}

				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask run");
					long video_time = main_activity.getPreview().getVideoTime();
					if( !main_activity.getPreview().isVideoRecording() ) {
						if( MyDebug.LOG )
							Log.d(TAG, "no longer video recording");
						return;
					}
					if( main_activity.getPreview().isVideoRecordingPaused() ) {
						if( MyDebug.LOG )
							Log.d(TAG, "video recording is paused");
						return;
					}
					Date current_date = new Date();
					Calendar current_calendar = Calendar.getInstance();
					int offset_ms = current_calendar.get(Calendar.MILLISECOND);
					if( MyDebug.LOG ) {
						Log.d(TAG, "count: " + count);
						Log.d(TAG, "offset_ms: " + offset_ms);
						Log.d(TAG, "video_time: " + video_time);
					}
					String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, current_date);
					String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, current_date);
					Location location = store_location ? getLocation() : null;
					double geo_direction = store_geo_direction && main_activity.getPreview().hasGeoDirection() ? main_activity.getPreview().getGeoDirection() : 0.0;
					String gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, store_location && location!=null, location, store_geo_direction && main_activity.getPreview().hasGeoDirection(), geo_direction);
					if( MyDebug.LOG ) {
						Log.d(TAG, "date_stamp: " + date_stamp);
						Log.d(TAG, "time_stamp: " + time_stamp);
						Log.d(TAG, "gps_stamp: " + gps_stamp);
					}
					String datetime_stamp = "";
					if( date_stamp.length() > 0 )
						datetime_stamp += date_stamp;
					if( time_stamp.length() > 0 ) {
						if( datetime_stamp.length() > 0 )
							datetime_stamp += " ";
						datetime_stamp += time_stamp;
					}
					String subtitles = "";
					if( datetime_stamp.length() > 0 )
						subtitles += datetime_stamp + "\n";
					if( gps_stamp.length() > 0 )
						subtitles += gps_stamp + "\n";
					if( subtitles.length() == 0 ) {
						return;
					}
					long video_time_from = video_time - offset_ms;
					long video_time_to = video_time_from + 999;
					if( video_time_from < 0 )
						video_time_from = 0;
					String subtitle_time_from = TextFormatter.formatTimeMS(video_time_from);
					String subtitle_time_to = TextFormatter.formatTimeMS(video_time_to);
					try {
						synchronized( this ) {
							if( writer == null ) {
								if( video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
									String subtitle_filename = last_video_file.getAbsolutePath();
									subtitle_filename = getSubtitleFilename(subtitle_filename);
									writer = new FileWriter(subtitle_filename);
								}
								else {
									if( MyDebug.LOG )
										Log.d(TAG, "last_video_file_saf: " + last_video_file_saf);
									File file = storageUtils.getFileFromDocumentUriSAF(last_video_file_saf, false);
									String subtitle_filename = file.getName();
									subtitle_filename = getSubtitleFilename(subtitle_filename);
									Uri subtitle_uri = storageUtils.createOutputFileSAF(subtitle_filename, ""); // don't set a mimetype, as we don't want it to append a new extension
									ParcelFileDescriptor pfd_saf = getContext().getContentResolver().openFileDescriptor(subtitle_uri, "w");
									writer = new FileWriter(pfd_saf.getFileDescriptor());
								}
							}
							if( writer != null ) {
								writer.append(Integer.toString(count));
								writer.append('\n');
								writer.append(subtitle_time_from);
								writer.append(" --> ");
								writer.append(subtitle_time_to);
								writer.append('\n');
								writer.append(subtitles); // subtitles should include the '\n' at the end
								writer.append('\n'); // additional newline to indicate end of this subtitle
								writer.flush();
								// n.b., we flush rather than closing/reopening the writer each time, as appending doesn't seem to work with storage access framework
							}
						}
						count++;
					}
					catch(IOException e) {
						if( MyDebug.LOG )
							Log.e(TAG, "SubtitleVideoTimerTask failed to create or write");
						e.printStackTrace();
					}
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask exit");
				}

				public boolean cancel() {
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask cancel");
					synchronized( this ) {
						if( writer != null ) {
							if( MyDebug.LOG )
								Log.d(TAG, "close writer");
							try {
								writer.close();
							}
							catch(IOException e) {
								e.printStackTrace();
							}
							writer = null;
						}
					}
					return super.cancel();
				}
			}
			subtitleVideoTimer.schedule(subtitleVideoTimerTask = new SubtitleVideoTimerTask(), 0, 1000);
		}
	}

	@Override
	public void stoppingVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "stoppingVideo()");
		main_activity.unlockScreen();
		main_activity.getMainUI().resetTakePhotoIcon();

		if( !sharedPreferences.getBoolean(Prefs.KEEP_DISPLAY_ON, false) )
			main_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	public void stoppedVideo(final int video_method, final Uri uri, final String filename) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stoppedVideo");
			Log.d(TAG, "video_method " + video_method);
			Log.d(TAG, "uri " + uri);
			Log.d(TAG, "filename " + filename);
		}
		View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
		if (pauseVideoButton.getVisibility() == View.VISIBLE) {
			pauseVideoButton.setVisibility(View.INVISIBLE);
			main_activity.getMainUI().setPauseVideoContentDescription(); // just to be safe
			main_activity.findViewById(R.id.gallery).setVisibility(View.VISIBLE);
		}
		main_activity.getMainUI().destroyPopup(); // as the available popup options change while recording video
		if( subtitleVideoTimerTask != null ) {
			subtitleVideoTimerTask.cancel();
			subtitleVideoTimerTask = null;
		}

		boolean done = false;
		if( video_method == VIDEOMETHOD_FILE ) {
			if( filename != null ) {
				File file = new File(filename);
				storageUtils.broadcastFile(file, false, true, true);
				done = true;
			}
		}
		else {
			if( uri != null ) {
				// see note in onPictureTaken() for where we call broadcastFile for SAF photos
				final File real_file = storageUtils.getFileFromDocumentUriSAF(uri, false);
				if( MyDebug.LOG )
					Log.d(TAG, "real_file: " + real_file);
				if( real_file != null ) {
					// Я таки извиняюсь шо я по-русски, но как жеж меня заебала эта гнилая, мерзкая, кривая ось, которая заставляет меня переступать через свое чувство прекрасного, клепая подобные костыли.
					if (real_file.length() == 0) {
						storageUtils.broadcastFile(real_file, false, true, true, 1024);
						final Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								storageUtils.broadcastFile(real_file, false, true, true);
							}
						}, 10000);
					} else {
						storageUtils.broadcastFile(real_file, false, true, true, real_file.length());
					}
					main_activity.test_last_saved_image = real_file.getAbsolutePath();
				}
				else {
					// announce the SAF Uri
					storageUtils.announceUri(uri, false, true);
				}
				done = true;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "done? " + done);

		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( done && video_method == VIDEOMETHOD_FILE ) {
				// do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "from video capture intent");
				Intent output = null;
				if( done ) {
					// may need to pass back the Uri we saved to, if the calling application didn't specify a Uri
					// set note above for VIDEOMETHOD_FILE
					// n.b., currently this code is not used, as we always switch to VIDEOMETHOD_FILE if the calling application didn't specify a Uri, but I've left this here for possible future behaviour
					if( video_method == VIDEOMETHOD_SAF ) {
						output = new Intent();
						output.setData(uri);
						if( MyDebug.LOG )
							Log.d(TAG, "pass back output uri [saf]: " + output.getData());
					}
				}
				main_activity.setResult(done ? Activity.RESULT_OK : Activity.RESULT_CANCELED, output);
				main_activity.finish();
			}
		}
		else if( done ) {
			// create thumbnail
			long debug_time = System.currentTimeMillis();
			Bitmap thumbnail = null;
			MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				if( video_method == VIDEOMETHOD_FILE ) {
					File file = new File(filename);
					retriever.setDataSource(file.getPath());
				}
				else {
					ParcelFileDescriptor pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "r");
					retriever.setDataSource(pfd_saf.getFileDescriptor());
				}
				thumbnail = retriever.getFrameAtTime(-1);
			}
			catch(FileNotFoundException | /*IllegalArgumentException |*/ RuntimeException e) {
				// video file wasn't saved or corrupt video file?
				Log.d(TAG, "failed to find thumbnail");
				e.printStackTrace();
			}
			finally {
				try {
					retriever.release();
				}
				catch(RuntimeException ex) {
					// ignore
				}
			}
			if( thumbnail != null ) {
				ImageButton galleryButton = (ImageButton) main_activity.findViewById(R.id.gallery);
				int width = thumbnail.getWidth();
				int height = thumbnail.getHeight();
				if( MyDebug.LOG )
					Log.d(TAG, "	video thumbnail size " + width + " x " + height);
				if( width > galleryButton.getWidth() ) {
					float scale = (float) galleryButton.getWidth() / width;
					int new_width = Math.round(scale * width);
					int new_height = Math.round(scale * height);
					if( MyDebug.LOG )
						Log.d(TAG, "	scale video thumbnail to " + new_width + " x " + new_height);
					Bitmap scaled_thumbnail = Bitmap.createScaledBitmap(thumbnail, new_width, new_height, true);
					// careful, as scaled_thumbnail is sometimes not a copy!
					if( scaled_thumbnail != thumbnail ) {
						thumbnail.recycle();
						thumbnail = scaled_thumbnail;
					}
				}
				final Bitmap thumbnail_f = thumbnail;
				main_activity.runOnUiThread(new Runnable() {
					public void run() {
						updateThumbnail(thumbnail_f, true);
					}
				});
			}
			if( MyDebug.LOG )
				Log.d(TAG, "	time to create thumbnail: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	public int getCapturedImagesCount() {
		return n_capture_images;
	}

	@Override
	public void startingTimer(final boolean is_burst) {
		if( MyDebug.LOG )
			Log.d(TAG, "startingTimer()");
		main_activity.getMainUI().startingTimer();

		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshop mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}

		if (photo_mode == Prefs.PhotoMode.FocusBracketing) {
			n_capture_images = 0;
		} else {
			if( sharedPreferences.getBoolean(Prefs.TIMER_START_SOUND, false) &&
					!sharedPreferences.getBoolean(Prefs.TIMER_BEEP, true) )
				main_activity.playSound(R.raw.beep);

			if( is_burst && sharedPreferences.getBoolean(Prefs.BURST_LOW_BRIGHTNESS, false) ) {
				main_activity.setMinBrightness();
			}

			if( sharedPreferences.getBoolean(Prefs.BURST_LOCK, false) )
				main_activity.lockScreen();

		}

		if (!main_activity.usingKitKatImmersiveMode()) {
			main_activity.getMainUI().enableClickableControls(false);
		}

		main_activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	@Override
	public void stoppingTimer(final boolean is_burst) {
		stoppingTimer(is_burst, false);
	}

	@Override
	public void stoppingTimer(final boolean is_burst, final boolean intermediate) {
		if( MyDebug.LOG )
			Log.d(TAG, "stoppingTimer()");

		if( !sharedPreferences.getBoolean(Prefs.KEEP_DISPLAY_ON, false) )
			main_activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (!intermediate && !main_activity.usingKitKatImmersiveMode()) {
			main_activity.getMainUI().enableClickableControls(true);
		}

		if (Prefs.getPhotoMode() != Prefs.PhotoMode.FocusBracketing) {
			if( is_burst && sharedPreferences.getBoolean(Prefs.BURST_LOW_BRIGHTNESS, false) ) {
				main_activity.setBrightnessForCamera(false);
			}

			if( main_activity.isScreenLocked() )
				main_activity.unlockScreen();

			if( !intermediate && sharedPreferences.getBoolean(Prefs.TIMER_START_SOUND, false) &&
					!sharedPreferences.getBoolean(Prefs.TIMER_BEEP, true) )
				main_activity.playSound(R.raw.beep_hi);
		}

		main_activity.getMainUI().resetTakePhotoIcon();
	}

	@Override
	public void onVideoInfo(int what, int extra) {
		// we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			if( MyDebug.LOG )
				Log.d(TAG, "max filesize reached");
			int message_id = R.string.video_max_filesize;
			main_activity.getPreview().showToast(null, message_id);
			// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
			// fixed in 1.25; also was correct for 1.23 and earlier
			String debug_value = "info_" + what + "_" + extra;
				SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("last_video_error", debug_value);
			editor.apply();
		}
	}

	@Override
	public void onFailedStartPreview() {
		main_activity.getPreview().showToast(null, R.string.failed_to_start_camera_preview);
	}

	@Override
	public void onCameraError() {
		main_activity.getPreview().showToast(null, R.string.camera_error);
	}

	@Override
	public void onPhotoError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_take_picture);
	}

	@Override
	public void onVideoError(int what, int extra) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		}
		int message_id = R.string.video_error_unknown;
		if( what == MediaRecorder.MEDIA_ERROR_SERVER_DIED  ) {
			if( MyDebug.LOG )
				Log.d(TAG, "error: server died");
			message_id = R.string.video_error_server_died;
		}
		main_activity.getPreview().showToast(null, message_id);
		// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
		// fixed in 1.25; also was correct for 1.23 and earlier
		String debug_value = "error_" + what + "_" + extra;
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("last_video_error", debug_value);
		editor.apply();
	}
	
	@Override
	public void onVideoRecordStartError(VideoProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStartError");
		String error_message;
		String features = main_activity.getPreview().getErrorFeatures(profile);
		if( features.length() > 0 ) {
			error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		else {
			error_message = getContext().getResources().getString(R.string.failed_to_record_video);
		}
		main_activity.getPreview().showToast(null, error_message);
		main_activity.getMainUI().setTakePhotoIcon();
	}

	@Override
	public void onVideoRecordStopError(VideoProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStopError");
		//main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
		String features = main_activity.getPreview().getErrorFeatures(profile);
		String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
		if( features.length() > 0 ) {
			error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		main_activity.getPreview().showToast(null, error_message);
	}
	
	@Override
	public void onFailedReconnectError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_reconnect_camera);
	}
	
	@Override
	public void onFailedCreateVideoFileError() {
		main_activity.getPreview().showToast(null, R.string.failed_to_save_video);
		main_activity.getMainUI().setTakePhotoIcon();
	}

	@Override
	public void hasPausedPreview(boolean paused) {
		View shareButton = main_activity.findViewById(R.id.share);
		View trashButton = main_activity.findViewById(R.id.trash);
		if( paused ) {
			if (!main_activity.getMainUI().inImmersiveMode())
				main_activity.getMainUI().showGUI(false, true);
			shareButton.setVisibility(View.VISIBLE);
			trashButton.setVisibility(View.VISIBLE);
		}
		else {
			shareButton.setVisibility(View.GONE);
			trashButton.setVisibility(View.GONE);
			if (!main_activity.getMainUI().inImmersiveMode())
				main_activity.getMainUI().showGUI(true);
			this.clearLastImages();
		}
	}
	
	@Override
	public void cameraInOperation(boolean in_operation) {
		if( MyDebug.LOG )
			Log.d(TAG, "cameraInOperation: " + in_operation);
		if( !in_operation && used_front_screen_flash ) {
			main_activity.runOnUiThread(new Runnable() {
				public void run() {
					main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
					main_activity.getMainUI().enableFrontScreenFlasn(false);
				}
			});
			used_front_screen_flash = false;
		}
		drawPreview.cameraInOperation(in_operation);
		if (!main_activity.getPreview().isBurst())
			main_activity.getMainUI().enableClickableControls(!in_operation);
	}
	
	@Override
	public void turnFrontScreenFlashOn() {
		if( MyDebug.LOG )
			Log.d(TAG, "turnFrontScreenFlashOn");
		used_front_screen_flash = true;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				main_activity.setBrightnessForCamera(true); // ensure we have max screen brightness, even if user preference not set for max brightness
				main_activity.getMainUI().enableFrontScreenFlasn(true);
			}
		});
	}

	private int n_capture_images = 0; // how many calls to onPictureTaken() since the last call to onCaptureStarted()

	@Override
	public void onCaptureStarted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onCaptureStarted");
		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshot mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}
		
		if (photo_mode != Prefs.PhotoMode.FocusBracketing)
			n_capture_images = 0;
		drawPreview.onCaptureStarted();
	}

	@Override
	public void onPictureCompleted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureCompleted");

		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshot mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}
/*		if( photo_mode == Prefs.PhotoMode.NoiseReduction ) {
			boolean image_capture_intent = isImageCaptureIntent();
			boolean do_in_background = saveInBackground(image_capture_intent);
			imageSaver.finishImageAverage(do_in_background);
		}*/

		// call this, so that if pause-preview-after-taking-photo option is set, we remove the "taking photo" border indicator straight away
		// also even for normal (not pausing) behaviour, good to remove the border asap
		drawPreview.cameraInOperation(false);
	}

	@Override
	public void cameraClosed() {
		main_activity.getMainUI().showSeekbars(false, true);
		main_activity.getMainUI().destroyPopup(); // need to close popup - and when camera reopened, it may have different settings
		drawPreview.clearContinuousFocusMove();
	}
	
	void updateThumbnail(Bitmap thumbnail, boolean is_video) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateThumbnail");
		main_activity.updateGalleryIcon(thumbnail);
		drawPreview.updateThumbnail(thumbnail);
		if( !is_video && Prefs.getPausePreviewPref() ) {
			drawPreview.showLastImage();
		}
	}
	
	@Override
	public void timerBeep(long remaining_time) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "timerBeep()");
			Log.d(TAG, "remaining_time: " + remaining_time);
		}
		if( sharedPreferences.getBoolean(Prefs.TIMER_BEEP, true) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "play beep!");
			boolean is_last = remaining_time <= 1000;
			main_activity.playSound(is_last ? R.raw.beep_hi : R.raw.beep);
		}
		if( sharedPreferences.getBoolean(Prefs.TIMER_SPEAK, false) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "speak countdown!");
			int remaining_time_s = (int)(remaining_time/1000);
			if( remaining_time_s <= 60 )
				main_activity.speak("" + remaining_time_s);
		}
	}

	@Override
	public void shutterSound() {
		if( MyDebug.LOG ) {
			Log.d(TAG, "shutterSound()");
		}
		if (!sharedPreferences.getBoolean(Prefs.SHUTTER_SOUND, true))
			return;
			
		main_activity.playShutterSound();
	}

	@Override
	public void layoutUI() {
		main_activity.getMainUI().layoutUI();
	}
	
	@Override
	public void multitouchZoom(int new_zoom) {
		main_activity.getMainUI().setSeekbarZoom(new_zoom);
	}
	
	@Override
	public void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		main_activity.requestCameraPermission();
	}
	
	@Override
	public void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		main_activity.requestStoragePermission();
	}
	
	@Override
	public void requestRecordAudioPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestRecordAudioPermission");
		main_activity.requestRecordAudioPermission();
	}


	@Override
	public void onDrawPreview(Canvas canvas) {
		drawPreview.onDrawPreview(canvas);
	}

	@Override
	public void onPrefsChanged() {
		drawPreview.onPrefsChanged();
	}

	@Override
	public void faceDetected(boolean detected) {
		if (sharedPreferences.getBoolean(Prefs.FACE_DETECTION_SOUND, false))
			main_activity.playSound(detected ? R.raw.double_beep : R.raw.double_beep_hi);
	}

	public void drawTextOnPhoto(final Canvas canvas, final Paint paint, final String text, final int width, final int height, final int line_count) {
		final int foreground;
		String color = sharedPreferences.getString(Prefs.STAMP_FONT_COLOR, "white");
		if (color.equals("red")) {
			foreground = Color.rgb(239,83,80); //Red 400
		} else if (color.equals("green")) {
			foreground = Color.rgb(102,187,106); //Green 400
		} else if (color.equals("blue")) {
			foreground = Color.rgb(66,165,245); //Blue 400
		} else if (color.equals("yellow")) {
			foreground = Color.rgb(255,238,88); //Yellow 400
		} else {
			foreground = Color.WHITE;
		}
		
		// we don't use the density of the screen, because we're stamping to the image, not drawing on the screen (we don't want the font height to depend on the device's resolution)
		// instead we go by 1 pt == 1/72 inch height, and scale for an image height (or width if in portrait) of 4" (this means the font height is also independent of the photo resolution)
		final float scale = ((float)Math.min(width, height)) / (72.0f*4.0f);
		int font_size = 12;
		String value = sharedPreferences.getString(Prefs.STAMP_FONTSIZE, "12");
		if( MyDebug.LOG )
			Log.d(TAG, "saved font size: " + value);
		try {
			font_size = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "font_size: " + font_size);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "font size invalid format, can't parse to int");
		}
		font_size = (int)(font_size * scale + 0.5f); // convert pt to pixels

		if( MyDebug.LOG ) {
			Log.d(TAG, "scale: " + scale);
			Log.d(TAG, "font_size: " + font_size);
		}
		int margin = (int)(8 * scale + 0.5f); // convert pt to pixels
		int diff_y = (int)(font_size+(4 * scale + 0.5f)); // convert pt to pixels

		int location_x;
		switch (sharedPreferences.getString(Prefs.STAMP_LOCATION_X, "right")) {
			case "center":
				location_x = width/2;
				paint.setTextAlign(Paint.Align.CENTER);
				break;
			case "right":
				location_x = width-margin;
				paint.setTextAlign(Paint.Align.RIGHT);
				break;
			default:
				location_x = margin;
				paint.setTextAlign(Paint.Align.LEFT);
				break;
		}

		int location_y;
		if (sharedPreferences.getString(Prefs.STAMP_LOCATION_Y, "bottom").equals("bottom")) {
			location_y = height-margin-diff_y*line_count;
		} else {
			location_y = font_size+margin+diff_y*line_count;
		}

		paint.setTextSize(font_size);
		if( sharedPreferences.getBoolean(Prefs.STAMP_BACKGROUND, false) ) {
			paint.getTextBounds(text, 0, text.length(), text_bounds);
			final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
			if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
				float text_width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
				if( paint.getTextAlign() == Paint.Align.CENTER )
					text_width /= 2.0f;
				text_bounds.left -= text_width;
				text_bounds.right -= text_width;
			}
			text_bounds.left += location_x - padding;
			text_bounds.right += location_x + padding;
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;

			paint.setColor(Color.BLACK);
			paint.setAlpha(64);
			canvas.drawRect(text_bounds, paint);
		} else {
			paint.setColor(Color.rgb(Color.red(foreground)/4, Color.green(foreground)/4, Color.blue(foreground)/4));
			paint.setAlpha(127);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(font_size/14);
			canvas.drawText(text, location_x, location_y, paint);
		}
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}
	
	private boolean saveInBackground(boolean image_capture_intent) {
		boolean do_in_background = true;
		if( !sharedPreferences.getBoolean(Prefs.BACKGROUND_PHOTO_SAVING, true) )
			do_in_background = false;
		else if( image_capture_intent )
			do_in_background = false;
		else if( Prefs.getPausePreviewPref() )
			do_in_background = false;
		return do_in_background;
	}
	
	private boolean isImageCaptureIntent() {
		boolean image_capture_intent = false;
		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			image_capture_intent = true;
		}
		return image_capture_intent;
	}
	
	private boolean saveImage(Prefs.PhotoMode photo_mode, boolean save_expo, List<CameraController.Photo> images, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveImage");

		System.gc();

		boolean image_capture_intent = isImageCaptureIntent();
		Uri image_capture_intent_uri = null;
		if( image_capture_intent ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			Bundle myExtras = main_activity.getIntent().getExtras();
			if (myExtras != null) {
				image_capture_intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + image_capture_intent_uri);
			}
		}

		boolean using_camera2 = main_activity.getPreview().usingCamera2API();
		int image_quality = Prefs.getSaveImageQualityPref();
		if( MyDebug.LOG )
			Log.d(TAG, "image_quality: " + image_quality);
		boolean do_auto_stabilise = photo_mode != Prefs.PhotoMode.FastBurst && Prefs.getAutoStabilisePref() && main_activity.getPreview().hasLevelAngle();
		double level_angle = do_auto_stabilise ? main_activity.getPreview().getLevelAngle() : 0.0;
		if( do_auto_stabilise && main_activity.test_have_angle )
			level_angle = main_activity.test_angle;
		if( do_auto_stabilise && main_activity.test_low_memory )
			level_angle = 45.0;
		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
		boolean is_front_facing = main_activity.getPreview().getCameraController() != null && main_activity.getPreview().getCameraController().isFrontFacing();
		boolean store_location = Prefs.getGeotaggingPref() && getLocation() != null;
		Location location = store_location ? getLocation() : null;
		boolean store_geo_direction = main_activity.getPreview().hasGeoDirection() && Prefs.getGeodirectionPref();
		double geo_direction = store_geo_direction ? main_activity.getPreview().getGeoDirection() : 0.0;
		boolean has_thumbnail_animation = Prefs.getThumbnailAnimationPref();
		
		boolean do_in_background = saveInBackground(image_capture_intent);
		
		int sample_factor = 1;
		if( !Prefs.getPausePreviewPref() ) {
			// if pausing the preview, we use the thumbnail also for the preview, so don't downsample
			// otherwise, we can downsample by 4 to increase performance, without noticeable loss in visual quality (even for the thumbnail animation)
			sample_factor *= 4;
			if( !has_thumbnail_animation ) {
				// can use even lower resolution if we don't have the thumbnail animation
				sample_factor *= 4;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "sample_factor: " + sample_factor);
		boolean success;

		if ((photo_mode == Prefs.PhotoMode.FastBurst || photo_mode == Prefs.PhotoMode.NoiseReduction) && n_capture_images != Prefs.getBurstCount())
			sample_factor = 0;

		ImageSaver.ProcessingSettings settings = new ImageSaver.ProcessingSettings();

		if (!main_activity.getPreview().isAutoAdjustmentLocked()) {
			String adjust_levels_key = null;
			if (photo_mode == Prefs.PhotoMode.NoiseReduction && n_capture_images == Prefs.getBurstCount()) {
				adjust_levels_key = Prefs.NR_ADJUST_LEVELS;
			} else if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.DRO) {
				adjust_levels_key = Prefs.HDR_ADJUST_LEVELS;
			} else if (photo_mode != Prefs.PhotoMode.NoiseReduction) {
				adjust_levels_key = Prefs.ADJUST_LEVELS;
			}
			if (adjust_levels_key != null) {
				try {
					settings.adjust_levels = Integer.parseInt(sharedPreferences.getString(adjust_levels_key, "0"));
				} catch(NumberFormatException e) {
					settings.adjust_levels = 0;
				}
			}
		}
	
		if (photo_mode == Prefs.PhotoMode.HDR) {
			settings.save_base = save_expo ? ImageSaver.ProcessingSettings.SaveBase.ALL : ImageSaver.ProcessingSettings.SaveBase.NONE;
			settings.hdr_tonemapping = sharedPreferences.getString(Prefs.HDR_TONEMAPPING, "reinhard");
			settings.hdr_deghost = sharedPreferences.getBoolean(Prefs.HDR_DEGHOST, true);
		}
		if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.DRO) {
			settings.hdr_local_contrast = sharedPreferences.getString(Prefs.HDR_LOCAL_CONTRAST, "5");
			settings.hdr_n_tiles = sharedPreferences.getString(Prefs.HDR_N_TILES, "4");
			settings.hdr_unsharp_mask = sharedPreferences.getString(Prefs.HDR_UNSHARP_MASK, "1");
			settings.hdr_unsharp_mask_radius = sharedPreferences.getString(Prefs.HDR_UNSHARP_MASK_RADIUS, "5");
		}

		settings.do_auto_stabilise = do_auto_stabilise;
		settings.level_angle = level_angle;
		settings.mirror = is_front_facing && sharedPreferences.getBoolean(Prefs.FLIP_FRONT_FACING, false);
		settings.stamp = sharedPreferences.getBoolean(Prefs.STAMP, false);
		settings.stamp_text = sharedPreferences.getString(Prefs.TEXTSTAMP, "");
		settings.stamp_dateformat = sharedPreferences.getString(Prefs.STAMP_DATEFORMAT, "preference_stamp_dateformat_default");
		settings.stamp_timeformat = sharedPreferences.getString(Prefs.STAMP_TIMEFORMAT, "preference_stamp_timeformat_default");
		settings.stamp_gpsformat = sharedPreferences.getString(Prefs.STAMP_GPSFORMAT, "preference_stamp_gpsformat_default");
		
		String yuv_conversion = "";
		if (images.get(0).image != null) {
			yuv_conversion = sharedPreferences.getString(Prefs.YUV_CONVERSION, "default");
		}
		
		ImageSaver.Metadata metadata = new ImageSaver.Metadata();
		metadata.author = sharedPreferences.getString(Prefs.METADATA_AUTHOR, "");
		metadata.comment = sharedPreferences.getString(Prefs.METADATA_COMMENT, "");
		metadata.comment_as_file = sharedPreferences.getBoolean(Prefs.METADATA_COMMENT_AS_FILE, false);
		
		String info = "";
		boolean position_info = sharedPreferences.getBoolean(Prefs.METADATA_POSITION_INFO, false);
		boolean mode_info = sharedPreferences.getBoolean(Prefs.METADATA_MODE_INFO, false);
		boolean sensor_info = sharedPreferences.getBoolean(Prefs.METADATA_MODE_INFO, false);
		boolean processing_info = sharedPreferences.getBoolean(Prefs.METADATA_MODE_INFO, false);
		if (position_info || mode_info || sensor_info || processing_info) {
			Resources resources = getContext().getResources();
			Preview preview = main_activity.getPreview();
			CameraController camera_controller = preview.getCameraController();
			if (camera_controller != null) {
				if (position_info) {
					info += "\n" + resources.getString(R.string.rotation) + ": " + main_activity.getPreview().getImageVideoRotation() + (char)0x00B0 +
						"\n" + resources.getString(R.string.angle) + ": " + DrawPreview.formatLevelAngle(main_activity.getPreview().getLevelAngle()) + (char)0x00B0;
				}
				if (mode_info) {
					if( preview.supportsFocus() && preview.getSupportedFocusValues().size() > 1 ) {
						String focus_entry = preview.findFocusEntryForValue(preview.getCurrentFocusValue());
						if( focus_entry != null ) {
							info += "\n" + resources.getString(R.string.focus_mode) + ": " + focus_entry;
						}
					}
					String iso_value = Prefs.getISOPref();
					if( !iso_value.equals(camera_controller.getDefaultISO()) ) {
						info += "\n" + preview.getISOString(iso_value);
					}
					int current_exposure = camera_controller.getExposureCompensation();
					if( current_exposure != 0 ) {
						info += "\n" + resources.getString(R.string.exposure_compensation) + ": " + preview.getExposureCompensationString(current_exposure);
					}
					String scene_mode = camera_controller.getSceneMode();
					if( scene_mode != null && !scene_mode.equals(camera_controller.getDefaultSceneMode()) ) {
						info += "\n" + resources.getString(R.string.scene_mode) + ": " + main_activity.getStringResourceByName("sm_", scene_mode);
					}
					String white_balance = camera_controller.getWhiteBalance();
					if( white_balance != null && !white_balance.equals(camera_controller.getDefaultWhiteBalance()) ) {
						info += "\n" + resources.getString(R.string.white_balance) + ": " + main_activity.getStringResourceByName("wb_", white_balance);
						if( white_balance.equals("manual") && preview.supportsWhiteBalanceTemperature() ) {
							info += " " + camera_controller.getWhiteBalanceTemperature();
						}
					}
					String color_effect = camera_controller.getColorEffect();
					if( color_effect != null && !color_effect.equals(camera_controller.getDefaultColorEffect()) ) {
						info += "\n" + resources.getString(R.string.color_effect) + ": " + main_activity.getStringResourceByName("ce_", color_effect);
					}
					if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.ExpoBracketing) {
						info += "\n" + resources.getString(R.string.preference_expo_bracketing_stops_up) + ": " + Prefs.getExpoBracketingStopsUpPref();
						info += "\n" + resources.getString(R.string.preference_expo_bracketing_stops_down) + ": " + Prefs.getExpoBracketingStopsDownPref();
					}
				}
				if (sensor_info) {
					String antibanding = camera_controller.getAntibanding();
					if (antibanding != null) {
						info += "\n" + resources.getString(R.string.preference_antibanding) + ": " + main_activity.getStringFromArrays(
							antibanding,
							R.array.preference_antibanding_entries,
							R.array.preference_antibanding_values
						);
					}

					String noise_reduction = camera_controller.getNoiseReductionMode();
					if (noise_reduction != null) {
						info += "\n" + resources.getString(R.string.preference_noise_reduction) + ": " + (camera_controller.isFilteringBlocked() ? resources.getString(R.string.off) :
						main_activity.getStringFromArrays(
							noise_reduction,
							R.array.preference_noise_reduction_entries,
							R.array.preference_noise_reduction_values
						));
					}

					String edge = camera_controller.getEdgeMode();
					if (edge != null) {
						info += "\n" + resources.getString(R.string.preference_edge) + ": " + (camera_controller.isFilteringBlocked() ? resources.getString(R.string.off) :
						main_activity.getStringFromArrays(
							edge,
							R.array.preference_edge_entries,
							R.array.preference_edge_values
						));
					}
				}
				if ((mode_info || processing_info) && photo_mode != Prefs.PhotoMode.Standard) {
					info += "\n" + resources.getString(R.string.photo_mode) + ": " + main_activity.getStringFromArrays(
						Prefs.getPhotoModeStringValue(photo_mode),
						R.array.photo_mode_entries,
						R.array.photo_mode_values
					);
				}
				if (processing_info) {
					if (photo_mode == Prefs.PhotoMode.HDR) {
						info += "\n" + resources.getString(R.string.preference_hdr_tonemapping) + ": " + main_activity.getStringFromArrays(
							settings.hdr_tonemapping,
							R.array.preference_hdr_tonemapping_entries,
							R.array.preference_hdr_tonemapping_values
						);
					}
					if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.DRO) {
						if (!settings.hdr_unsharp_mask.equals("0")) {
							info += "\n" + resources.getString(R.string.preference_hdr_unsharp_mask) + ": " + main_activity.getStringFromArrays(
								settings.hdr_unsharp_mask,
								R.array.preference_hdr_local_contrast_entries,
								R.array.preference_hdr_local_contrast_values
							);
							info += "\n" + resources.getString(R.string.preference_hdr_unsharp_mask_radius) + ": " + settings.hdr_unsharp_mask_radius;
						}
						if (!settings.hdr_local_contrast.equals("0")) {
							info += "\n" + resources.getString(R.string.preference_hdr_local_contrast) + ": " + main_activity.getStringFromArrays(
								settings.hdr_local_contrast,
								R.array.preference_hdr_local_contrast_entries,
								R.array.preference_hdr_local_contrast_values
							);
							info += "\n" + resources.getString(R.string.preference_hdr_n_tiles) + ": " + settings.hdr_n_tiles;
						}
					}
					if (do_auto_stabilise) {
						info += "\n" + resources.getString(R.string.preference_auto_stabilise) + ": " + level_angle;
					}
					if (settings.adjust_levels > 0) {
						info += "\n" + resources.getString(R.string.preference_adjust_levels) + ": " + resources.getStringArray(R.array.preference_adjust_levels_entries)[settings.adjust_levels];
					}
				}
			}
		}
		
		if (info.length() > 0) {
			if (metadata.comment.length() > 0) {
				metadata.comment += "\n" + info;
			} else {
				metadata.comment = info.substring(1);
			}
		}

		String prefix = sharedPreferences.getString(Prefs.SAVE_PHOTO_PREFIX, "IMG_");

		success = imageSaver.saveImageJpeg(do_in_background, photo_mode,
				images,
				yuv_conversion,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				settings,
				metadata,
				is_front_facing,
				prefix, current_date, n_capture_images,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);

		if( MyDebug.LOG )
			Log.d(TAG, "saveImage complete, success: " + success);
		
		return success;
	}

	@Override
	public boolean onPictureTaken(CameraController.Photo photo, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken");

		n_capture_images++;
		if( MyDebug.LOG )
			Log.d(TAG, "n_capture_images is now " + n_capture_images);

		List<CameraController.Photo> images = new ArrayList<>();
		images.add(photo);

		// note, multi-image HDR and expo is handled under onBurstPictureTaken; here we look for DRO, as that's the photo mode to set
		// single image HDR
		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshot mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}
		boolean success = saveImage(photo_mode, false, images, current_date);
		
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken complete, success: " + success);
		
		return success;
	}
	
	@Override
	public boolean onBurstPictureTaken(List<CameraController.Photo> images, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "onBurstPictureTaken: received " + images.size() + " images");

		boolean success;
		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshop mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}
		if( photo_mode == Prefs.PhotoMode.HDR ) {
			if( MyDebug.LOG )
				Log.d(TAG, "HDR mode");
				boolean save_expo =  sharedPreferences.getBoolean(Prefs.HDR_SAVE_EXPO, false);
			if( MyDebug.LOG )
				Log.d(TAG, "save_expo: " + save_expo);

			success = saveImage(Prefs.PhotoMode.HDR, save_expo, images, current_date);
		}
		else {
			if( MyDebug.LOG ) {
				Log.d(TAG, "exposure bracketing mode mode");
				if( photo_mode != Prefs.PhotoMode.ExpoBracketing )
					Log.e(TAG, "onBurstPictureTaken called with unexpected photo mode?!: " + photo_mode);
			}
			
			success = saveImage(photo_mode, true, images, current_date);
		}
		return success;
	}

	@Override
	public boolean onRawPictureTaken(DngCreator dngCreator, Image image, Date current_date) {
		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken");
		System.gc();

		boolean do_in_background = saveInBackground(false);
		Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
		if( main_activity.getPreview().isVideo() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "snapshop mode");
			// must be in photo snapshot while recording video mode, only support standard photo mode
			photo_mode = Prefs.PhotoMode.Standard;
		}

		String prefix = sharedPreferences.getString(Prefs.SAVE_PHOTO_PREFIX, "IMG_");
		boolean success = imageSaver.saveImageRaw(do_in_background, photo_mode, dngCreator, image, prefix, current_date, n_capture_images);
		
		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken complete");
		return success;
	}
	
	void addLastImage(File file, boolean share) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "addLastImage: " + file);
			Log.d(TAG, "share?: " + share);
		}
		last_images_saf = false;
		LastImage last_image = new LastImage(file.getAbsolutePath(), share);
		last_images.add(last_image);
	}
	
	void addLastImageSAF(Uri uri, boolean share) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "addLastImageSAF: " + uri);
			Log.d(TAG, "share?: " + share);
		}
		last_images_saf = true;
		LastImage last_image = new LastImage(uri, share);
		last_images.add(last_image);
	}

	void clearLastImages() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImages");
		last_images_saf = false;
		last_images.clear();
		drawPreview.clearLastImage();
	}

	void shareLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "shareLastImage");
		Preview preview  = main_activity.getPreview();
		if( preview.isPreviewPaused() ) {
			LastImage share_image = null;
			for(int i=0;i<last_images.size() && share_image == null;i++) {
				LastImage last_image = last_images.get(i);
				if( last_image.share ) {
					share_image = last_image;
				}
			}
			if( share_image != null ) {
				Uri last_image_uri = share_image.uri;
				if( MyDebug.LOG )
					Log.d(TAG, "Share: " + last_image_uri);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, last_image_uri);
				main_activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			clearLastImages();
			preview.startCameraPreview();
		}
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void trashImage(boolean image_saf, Uri image_uri, String image_name) {
		if( MyDebug.LOG )
			Log.d(TAG, "trashImage");
		Preview preview  = main_activity.getPreview();
		if( image_saf && image_uri != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "Delete: " + image_uri);
			File file = storageUtils.getFileFromDocumentUriSAF(image_uri, false); // need to get file before deleting it, as fileFromDocumentUriSAF may depend on the file still existing
			try {
				if( !DocumentsContract.deleteDocument(main_activity.getContentResolver(), image_uri) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to delete " + image_uri);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "successfully deleted " + image_uri);
					preview.showToast(null, R.string.photo_deleted);
					if( file != null ) {
						// SAF doesn't broadcast when deleting them
						storageUtils.broadcastFile(file, false, false, true);
					}
				}
			}
			catch(FileNotFoundException e) {
				// note, Android Studio reports a warning that FileNotFoundException isn't thrown, but it can be
				// thrown by DocumentsContract.deleteDocument - and we get an error if we try to remove the catch!
				if( MyDebug.LOG )
					Log.e(TAG, "exception when deleting " + image_uri);
				e.printStackTrace();
			}
		}
		else if( image_name != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "Delete: " + image_name);
			File file = new File(image_name);
			if( !file.delete() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to delete " + image_name);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "successfully deleted " + image_name);
				preview.showToast(null, R.string.photo_deleted);
				storageUtils.broadcastFile(file, false, false, true);
			}
		}
	}
	
	void trashLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "trashImage");
		Preview preview  = main_activity.getPreview();
		if( preview.isPreviewPaused() ) {
			for(int i=0;i<last_images.size();i++) {
				LastImage last_image = last_images.get(i);
				trashImage(last_images_saf, last_image.uri, last_image.name);
			}
			clearLastImages();
			preview.startCameraPreview();
		}
		// Calling updateGalleryIcon() immediately has problem that it still returns the latest image that we've just deleted!
		// But works okay if we call after a delay. 100ms works fine on Nexus 7 and Galaxy Nexus, but set to 500 just to be safe.
		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				main_activity.updateGalleryIcon();
			}
		}, 500);
	}

	boolean hasThumbnailAnimation() {
		return this.drawPreview.hasThumbnailAnimation();
	}
	
	public HDRProcessor getHDRProcessor() {
		return imageSaver.getHDRProcessor();
	}

	public void disableSound() {
		AudioManager audio = (AudioManager)this.getContext().getSystemService(Context.AUDIO_SERVICE);
		currentVolume = audio.getStreamVolume(AudioManager.STREAM_SYSTEM);
		audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0,   AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
		isVolumeChanged = true;
	}
	
	public void restoreSound() {
		if (isVolumeChanged){
			AudioManager audio = (AudioManager)this.getContext().getSystemService(Context.AUDIO_SERVICE);
			audio.setStreamVolume(AudioManager.STREAM_SYSTEM,currentVolume,AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
			isVolumeChanged = false;
		}
	}	

	public boolean isSetExpoMeteringArea() {
		return main_activity.set_expo_metering_area;
	}
	
	public boolean fpsIsHighSpeed() {
		return main_activity.getPreview().fpsIsHighSpeed(Prefs.getVideoFPSPref());
	}

	// for testing
	public boolean test_set_available_memory = false;
	public long test_available_memory = 0;
}
