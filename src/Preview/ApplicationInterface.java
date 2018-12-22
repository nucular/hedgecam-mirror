package com.caddish_hedgehog.hedgecam2.Preview;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.net.Uri;
import android.util.Pair;
import android.view.MotionEvent;

/** Provides communication between the Preview and the rest of the application
 *  - so in theory one can drop the Preview/ (and CameraController/) classes
 *  into a new application, by providing an appropriate implementation of this
 *  ApplicationInterface.
 */
public interface ApplicationInterface {
	class NoFreeStorageException extends Exception {
		private static final long serialVersionUID = -2021932609486148748L;
	}
	class VideoMaxFileSize {
		public long max_filesize; // maximum file size in bytes for video (return 0 for device default - typically this is ~2GB)
		public boolean auto_restart; // whether to automatically restart on hitting max filesize (this setting is still relevant for max_filesize==0, as typically there will still be a device max filesize)
	}

	int VIDEOMETHOD_FILE = 0; // video will be saved to a file
	int VIDEOMETHOD_SAF = 1; // video will be saved using Android 5's Storage Access Framework
	int VIDEOMETHOD_URI = 2; // video will be written to the supplied Uri
	
	// methods that request information
	Context getContext(); // get the application context
	boolean useCamera2(); // should Android 5's Camera 2 API be used?
	boolean useTextureView();
	Location getLocation(); // get current location - null if not available (or you don't care about geotagging)
	int createOutputVideoMethod(); // return a VIDEOMETHOD_* value to specify how to create a video file
	File createOutputVideoFile(String prefix, String extension) throws IOException; // will be called if createOutputVideoUsingSAF() returns VIDEOMETHOD_FILE
	Uri createOutputVideoSAF(String prefix, String extension) throws IOException; // will be called if createOutputVideoUsingSAF() returns VIDEOMETHOD_SAF
	Uri createOutputVideoUri(); // will be called if createOutputVideoUsingSAF() returns VIDEOMETHOD_URI
	VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException; // see VideoMaxFileSize class for details
	boolean isRawPref(); // whether to enable RAW photos

	// for testing purposes:
	boolean isTestAlwaysFocus(); // if true, pretend autofocus always successful

	// methods that transmit information/events (up to the Application whether to do anything or not)
	void cameraSetup(); // called when the camera is (re-)set up - should update UI elements/parameters that depend on camera settings
	void touchEvent(MotionEvent event);
	void startingVideo(); // called just before video recording starts
	void startedVideo(); // called just after video recording starts
	void stoppingVideo(); // called just before video recording stops; note that if startingVideo() is called but then video recording fails to start, this method will still be called, but startedVideo() and stoppedVideo() won't be called
	void stoppedVideo(final int video_method, final Uri uri, final String filename); // called after video recording stopped (uri/filename will be null if video is corrupt or not created); will be called iff startedVideo() was called
	void startingTimer(final boolean is_burst);
	void stoppingTimer(final boolean is_burst);
	void stoppingTimer(final boolean is_burst, final boolean intermediate);
	void onFailedStartPreview(); // called if failed to start camera preview
	void onCameraError(); // called if the camera closes due to serious error.
	void onPhotoError(); // callback for failing to take a photo
	void onVideoInfo(int what, int extra); // callback for info when recording video (see MediaRecorder.OnInfoListener)
	void onVideoError(int what, int extra); // callback for errors when recording video (see MediaRecorder.OnErrorListener)
	void onVideoRecordStartError(VideoProfile profile); // callback for video recording failing to start
	void onVideoRecordStopError(VideoProfile profile); // callback for video recording being corrupted
	void onFailedReconnectError(); // failed to reconnect camera after stopping video recording
	void onFailedCreateVideoFileError(); // callback if unable to create file for recording video
	void hasPausedPreview(boolean paused); // called when the preview is paused or unpaused (due to getPausePreviewPref())
	void cameraInOperation(boolean in_operation); // called when the camera starts/stops being operation (taking photos or recording video, including if preview is paused after taking a photo), use to disable GUI elements during camera operation
	void turnFrontScreenFlashOn(); // called when front-screen "flash" required (for modes flash_frontscreen_auto, flash_frontscreen_on); the application should light up the screen, until cameraInOperation(false) is called
	void cameraClosed();

	// methods that request actions
	void layoutUI(); // application should layout UI that's on top of the preview
	void multitouchZoom(int new_zoom); // indicates that the zoom has changed due to multitouch gesture on preview
	// the set/clear*Pref() methods are called if Preview decides to override the requested pref (because Camera device doesn't support requested pref) (clear*Pref() is called if the feature isn't supported at all)
	// the application can use this information to update its preferences
	void requestCameraPermission(); // for Android 6+: called when trying to open camera, but CAMERA permission not available
	void requestStoragePermission(); // for Android 6+: called when trying to open camera, but WRITE_EXTERNAL_STORAGE permission not available
	void requestRecordAudioPermission(); // for Android 6+: called when switching to (or starting up in) video mode, but RECORD_AUDIO permission not available
	
	// callbacks
	void onDrawPreview(Canvas canvas);
	public void onPrefsChanged();
	boolean onPictureTaken(CameraController.Photo photo, Date current_date);
	boolean onBurstPictureTaken(List<CameraController.Photo> images, Date current_date);
	boolean onRawPictureTaken(DngCreator dngCreator, Image image, Date current_date);
	void onCaptureStarted(); // called immediately before we start capturing the picture
	void onPictureCompleted(); // called after all picture callbacks have been called and returned
	void onContinuousFocusMove(boolean start); // called when focusing starts/stop in continuous picture mode (in photo mode only)

	void faceDetected(boolean low);
	
	boolean isSetExpoMeteringArea();
}
