package com.caddish_hedgehog.hedgecam2.Preview;

import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.TakePhoto;
import com.caddish_hedgehog.hedgecam2.TakeVideo;
import com.caddish_hedgehog.hedgecam2.ToastBoxer;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController1;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController2;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraControllerException;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraControllerManager;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraControllerManager1;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraControllerManager2;
import com.caddish_hedgehog.hedgecam2.Preview.ApplicationInterface.NoFreeStorageException;
import com.caddish_hedgehog.hedgecam2.Preview.CameraSurface.CameraSurface;
import com.caddish_hedgehog.hedgecam2.Preview.CameraSurface.MySurfaceView;
import com.caddish_hedgehog.hedgecam2.Preview.CameraSurface.MyTextureView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;

import android.widget.ImageView;
import android.graphics.BitmapFactory;

/** This class was originally named due to encapsulating the camera preview,
 *  but in practice it's grown to more than this, and includes most of the
 *  operation of the camera. It exists at a higher level than CameraController
 *  (i.e., this isn't merely a low level wrapper to the camera API, but
 *  supports much of the Open Camera logic and functionality). Communication to
 *  the rest of the application is available through ApplicationInterface.
 *  We could probably do with decoupling this class into separate components!
 */
public class Preview implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {
	private static final String TAG = "HedgeCam/Preview";

	private final boolean using_camera_2;
	private final boolean using_texture_view;

	private int tick_interval;
	private boolean tick_slow_if_busy;

	private final ApplicationInterface applicationInterface;
	private final SharedPreferences sharedPreferences;
	private final CameraSurface cameraSurface;
	private CanvasView canvasView;
	private boolean set_preview_size;
	private int preview_w, preview_h;
	private boolean set_textureview_size;
	private int textureview_w, textureview_h;

	private final Matrix camera_to_preview_matrix = new Matrix();
	private final Matrix preview_to_camera_matrix = new Matrix();
	//private RectF face_rect = new RectF();
	private double preview_targetRatio;

	//private boolean ui_placement_right = true;

	private boolean app_is_paused = true;
	private boolean has_surface;
	private boolean has_aspect_ratio;
	private double aspect_ratio;
	private final CameraControllerManager camera_controller_manager;
	private CameraController camera_controller;
	enum CameraOpenState {
		CAMERAOPENSTATE_CLOSED, // have yet to attempt to open the camera (either at all, or since the camera was closed)
		CAMERAOPENSTATE_OPENING, // the camera is currently being opened (on a background thread)
		CAMERAOPENSTATE_OPENED, // either the camera is open (if camera_controller!=null) or we failed to open the camera (if camera_controller==null)
		CAMERAOPENSTATE_CLOSING // the camera is currently being closed (on a background thread)
	}
	private CameraOpenState camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSED;
	private CloseCameraTask close_camera_task; // background task used for closing camera
	private AsyncTask<Void, Void, CameraController> open_camera_task; // background task used for opening camera
	private boolean has_permissions = true; // whether we have permissions necessary to operate the camera (camera, storage); assume true until we've been denied one of them
	private boolean is_video;
	private volatile MediaRecorder video_recorder; // must be volatile for test project reading the state
	private volatile boolean video_start_time_set; // must be volatile for test project reading the state
	private long video_start_time; // when the video recording was started, or last resumed if it's was paused
	private long video_accumulated_time; // this time should be added to (System.currentTimeMillis() - video_start_time) to find the true video duration, that takes into account pausing/resuming, as well as any auto-restarts from max filesize
	private boolean video_recorder_is_paused; // whether video_recorder is running but has paused
	private boolean video_restart_on_max_filesize;
	private static final long min_safe_restart_video_time = 1000; // if the remaining max time after restart is less than this, don't restart
	private class VideoFileInfo {
		// stores the file (or similar) to record a video
		private final int video_method;
		private final Uri video_uri; // for VIDEOMETHOD_SAF or VIDEOMETHOD_URI
		private final String video_filename; // for VIDEOMETHOD_FILE
		private final ParcelFileDescriptor video_pfd_saf; // for VIDEOMETHOD_SAF

		VideoFileInfo() {
			this.video_method = ApplicationInterface.VIDEOMETHOD_FILE;
			this.video_uri = null;
			this.video_filename = null;
			this.video_pfd_saf = null;
		}
		VideoFileInfo(int video_method, Uri video_uri, String video_filename, ParcelFileDescriptor video_pfd_saf) {
			this.video_method = video_method;
			this.video_uri = video_uri;
			this.video_filename = video_filename;
			this.video_pfd_saf = video_pfd_saf;
		}
	}
	private VideoFileInfo videoFileInfo = new VideoFileInfo();

	private static final int PHASE_NORMAL = 0;
	private static final int PHASE_TIMER = 1;
	private static final int PHASE_TAKING_PHOTO = 2;
	private static final int PHASE_PREVIEW_PAUSED = 3; // the paused state after taking a photo
	private volatile int phase = PHASE_NORMAL; // must be volatile for test project reading the state
	private final Timer takePictureTimer = new Timer();
	private TimerTask takePictureTimerTask;
	private final Timer beepTimer = new Timer();
	private TimerTask beepTimerTask;
	private final Timer flashVideoTimer = new Timer();
	private TimerTask flashVideoTimerTask;
	private final IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private final Timer batteryCheckVideoTimer = new Timer();
	private TimerTask batteryCheckVideoTimerTask;
	private long take_photo_time;
	private int remaining_burst_photos;
	private int burst_count;
	private int burst_captured;
	private int remaining_restart_video;

	private boolean is_preview_started;
	private boolean is_burst;
	private boolean show_timer = false;
	private volatile boolean wait_face;

	private int current_orientation; // orientation received by onOrientationChanged
	private int current_rotation; // orientation relative to camera's orientation (used for parameters.setRotation())
	private boolean has_level_angle;
	private double natural_level_angle; // "level" angle of device, before applying any calibration and without accounting for screen orientation
	private double level_angle; // "level" angle of device, including calibration
	private double orig_level_angle; // "level" angle of device, including calibration, but without accounting for screen orientation
	private boolean has_pitch_angle;
	private double pitch_angle;
	
	private boolean has_zoom;
	private int max_zoom_factor;
	private GestureDetector gestureDetector;
	private ScaleGestureDetector scaleGestureDetector;
	private List<Integer> zoom_ratios;
	private float minimum_focus_distance;
	public boolean multitouch_zoom;
	private boolean touch_was_multitouch;
	private float scale_zoom_factor = 1;
	private float touch_orig_x;
	private float touch_orig_y;

	private List<String> supported_flash_values; // our "values" format
	private int current_flash_index = -1; // this is an index into the supported_flash_values array, or -1 if no flash modes available

	private List<String> supported_focus_values; // our "values" format
	private int current_focus_index = -1; // this is an index into the supported_focus_values array, or -1 if no focus modes available
	private int max_num_focus_areas;
	private int max_num_metering_areas;
	private boolean continuous_focus_move_is_started;
	
	private boolean is_auto_adjustment_lock_supported;
	private boolean is_auto_adjustment_locked;

	private List<String> color_effects;
	private List<String> scene_modes;
	private List<String> white_balances;
	private List<String> isos;
	private boolean supports_white_balance_temperature;
	private int min_temperature;
	private int max_temperature;
	private boolean supports_iso_range;
	private int min_iso;
	private int max_iso;
	private boolean supports_exposure_time;
	private long min_exposure_time;
	private long max_exposure_time;
	private List<String> exposures;
	private int min_exposure;
	private int max_exposure;
	private float exposure_step;
	private boolean supports_expo_bracketing;
	private int max_expo_bracketing_n_images;
	private boolean supports_raw;
	private float view_angle_x;
	private float view_angle_y;
	private boolean supports_antibanding = false;

	private List<CameraController.Size> supported_preview_sizes;
	
	private List<CameraController.Size> sizes;
	private int current_size_index = -1; // this is an index into the sizes array, or -1 if sizes not yet set

	private boolean supports_video;
	private boolean has_capture_rate_factor; // whether we have a capture rate for faster (timelapse) or slow motion
	private float capture_rate_factor = 1.0f; // should be 1.0f if has_capture_rate_factor is false; set lower than 1 for slow motion, higher than 1 for timelapse
	private boolean video_high_speed; // whether the current video mode requires high speed frame rate (note this may still be true even if is_video==false, so potentially we could switch photo/video modes without setting up the flag)
	private boolean supports_video_high_speed;
	private final VideoQualityHandler video_quality_handler = new VideoQualityHandler();

	private Toast last_toast;
	private final ToastBoxer flash_toast = new ToastBoxer();
	private final ToastBoxer focus_toast = new ToastBoxer();
	private final ToastBoxer take_photo_toast = new ToastBoxer();
	private final ToastBoxer pause_video_toast = new ToastBoxer();
	private final ToastBoxer seekbar_toast = new ToastBoxer();

	private boolean supports_face_detection;
	private boolean using_face_detection;
	private CameraController.Face [] faces_detected;
	private boolean supports_video_stabilization;
	private boolean supports_photo_video_recording;
	private boolean can_disable_shutter_sound;
	private int tonemap_max_curve_points;
	private boolean supports_tonemap_curve;
	private boolean has_focus_area;
	private boolean has_metering_area;
	private int focus_screen_x;
	private int focus_screen_y;
	private int metering_screen_x = 0;
	private int metering_screen_y = 0;
	private boolean has_saved_focus_area;
	private boolean has_saved_metering_area;
	private int saved_focus_screen_x = 0;
	private int saved_focus_screen_y = 0;
	private int saved_metering_screen_x = 0;
	private int saved_metering_screen_y = 0;
	private boolean focus_zone_changed;
	private long focus_complete_time = -1;
	private long focus_started_time = -1;
	private int focus_success = FOCUS_DONE;
	private static final int FOCUS_WAITING = 0;
	private static final int FOCUS_SUCCESS = 1;
	private static final int FOCUS_FAILED = 2;
	private static final int FOCUS_DONE = 3;
	private String set_flash_value_after_autofocus = "";
	private boolean take_photo_after_autofocus; // set to take a photo when the in-progress autofocus has completed; if setting, remember to call camera_controller.setCaptureFollowAutofocusHint()
	private boolean successfully_focused;
	private long successfully_focused_time = -1;

	// accelerometer and geomagnetic sensor info
	private static final float sensor_alpha = 0.8f; // for filter
	private boolean has_gravity;
	private final float [] gravity = new float[3];
	private boolean has_geomagnetic;
	private final float [] geomagnetic = new float[3];
	private final float [] deviceRotation = new float[9];
	private final float [] cameraRotation = new float[9];
	private final float [] deviceInclination = new float[9];
	private boolean has_geo_direction;
	private final float [] geo_direction = new float[3];
	private final float [] new_geo_direction = new float[3];

	private final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
	private final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");

	/* If the user touches to focus in continuous mode, we switch the camera_controller to autofocus mode.
	 * autofocus_in_continuous_mode is set to true when this happens; the runnable reset_continuous_focus_runnable
	 * switches back to continuous mode.
	 */
	private final Handler reset_continuous_focus_handler = new Handler();
	private Runnable reset_continuous_focus_runnable;
	private boolean autofocus_in_continuous_mode;
	
	private final Handler clear_focus_handler = new Handler();
	private Runnable clear_focus_runnable;

	private String hardware_level;
	
	private float[] fb_stack;
	private Date fb_date = null;

	// for testing; must be volatile for test project reading the state
	private boolean is_test; // whether called from OpenCamera.test testing
	public volatile int count_cameraStartPreview;
	public volatile int count_cameraAutoFocus;
	public volatile int count_cameraTakePicture;
	public volatile int count_cameraContinuousFocusMoving;
	public volatile boolean test_fail_open_camera;
	public volatile boolean test_video_failure;
	public volatile boolean test_ticker_called; // set from MySurfaceView or CanvasView
	private boolean using_canvas_view;
	
	private int toast_padding;
	private int toast_radius;
	private float toast_font_size;
	private int toast_color;
	
	private final float scale;
	
	private int current_zoom = -1;

	private Handler auto_adjustment_unlock_handler;
	private Runnable auto_adjustment_unlock_runnable;

	public Preview(ApplicationInterface applicationInterface, ViewGroup parent) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "new Preview");
		}

		this.applicationInterface = applicationInterface;

		Activity activity = (Activity)this.getContext();
		if( activity.getIntent() != null && activity.getIntent().getExtras() != null ) {
			// whether called from testing
			is_test = activity.getIntent().getExtras().getBoolean("test_project");
			if( MyDebug.LOG )
				Log.d(TAG, "is_test: " + is_test);
		}

		this.scale = activity.getResources().getDisplayMetrics().density;
		if( MyDebug.LOG )
			Log.d(TAG, "scale: " + this.scale);
		
		this.sharedPreferences = ((MainActivity)activity).getSharedPrefs();
		updateTickInterval();
		updateToastConfig();
		
		this.using_camera_2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && applicationInterface.useCamera2();
		if( MyDebug.LOG ) {
			Log.d(TAG, "using_camera_2?: " + using_camera_2);
		}

		this.using_texture_view = using_camera_2 ? true : applicationInterface.useTextureView();

		this.using_canvas_view = using_texture_view || true;
		if( this.using_canvas_view ) {
			this.canvasView = new CanvasView(getContext(), this);
		}
		
		if( using_texture_view ) {
			this.cameraSurface = new MyTextureView(getContext(), this);
		}
		else {
			this.cameraSurface = new MySurfaceView(getContext(), this);
		}

		if( using_camera_2 ) {
			camera_controller_manager = new CameraControllerManager2(getContext());
		}
		else {
			camera_controller_manager = new CameraControllerManager1();
		}

		gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener());
//		gestureDetector.setOnDoubleTapListener(new DoubleTapListener());
		if (Prefs.getMultitouchZoomPref())
			scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

		parent.addView(cameraSurface.getView());
		if( canvasView != null ) {
			((FrameLayout)(activity.findViewById(R.id.overlay_container))).addView(canvasView);
		}
	}
	
	public boolean isUsingCanvasView() {
		return this.using_canvas_view;
	}

	private Resources getResources() {
		return cameraSurface.getView().getResources();
	}
	
	public View getView() {
		return cameraSurface.getView();
	}

	// If this code is changed, important to test that face detection and touch to focus still works as expected, for front and back
	// cameras, for old and new API, including with zoom. Also test with MainActivity.setWindowFlagsForCamera() setting orientation as SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
	// and/or set "Rotate preview" option to 180 degrees.
	private void calculateCameraToPreviewMatrix() {
		if( MyDebug.LOG )
			Log.d(TAG, "calculateCameraToPreviewMatrix");
		if( camera_controller == null )
			return;
		camera_to_preview_matrix.reset();
		if( !using_camera_2 ) {
			// from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
			// Need mirror for front camera
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
			// This is the value for android.hardware.Camera.setDisplayOrientation.
			int display_orientation = camera_controller.getDisplayOrientation();
			if( MyDebug.LOG ) {
				Log.d(TAG, "orientation of display relative to camera orientaton: " + display_orientation);
			}
			camera_to_preview_matrix.postRotate(display_orientation);
		}
		else {
			// Unfortunately the transformation for Android L API isn't documented, but this seems to work for Nexus 6.
			// This is the equivalent code for android.hardware.Camera.setDisplayOrientation, but we don't actually use setDisplayOrientation()
			// for CameraController2, so instead this is the equivalent code to https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int),
			// except testing on Nexus 6 shows that we shouldn't change "result" for front facing camera.
			boolean mirror = camera_controller.isFrontFacing();
			camera_to_preview_matrix.setScale(1, mirror ? -1 : 1);
			int degrees = getDisplayRotationDegrees();
			int result = (camera_controller.getCameraOrientation() - degrees + 360) % 360;
			if( MyDebug.LOG ) {
				Log.d(TAG, "orientation of display relative to natural orientaton: " + degrees);
				Log.d(TAG, "orientation of display relative to camera orientaton: " + result);
			}
			camera_to_preview_matrix.postRotate(result);
		}
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		camera_to_preview_matrix.postScale(cameraSurface.getView().getWidth() / 2000f, cameraSurface.getView().getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(cameraSurface.getView().getWidth() / 2f, cameraSurface.getView().getHeight() / 2f);
	}
	
	private void calculatePreviewToCameraMatrix() {
		if( camera_controller == null )
			return;
		calculateCameraToPreviewMatrix();
		if( !camera_to_preview_matrix.invert(preview_to_camera_matrix) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
		}
	}

	public Matrix getCameraToPreviewMatrix() {
		calculateCameraToPreviewMatrix();
		return camera_to_preview_matrix;
	}

	/*Matrix getPreviewToCameraMatrix() {
		calculatePreviewToCameraMatrix();
		return preview_to_camera_matrix;
	}*/

	private ArrayList<CameraController.Area> getAreas(float x, float y) {
		float [] coords = {x, y};
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];
		
		int focus_size = 50;
		if( MyDebug.LOG ) {
			Log.d(TAG, "x, y: " + x + ", " + y);
			Log.d(TAG, "focus x, y: " + focus_x + ", " + focus_y);
		}
		Rect rect = new Rect();
		rect.left = (int)focus_x - focus_size;
		rect.right = (int)focus_x + focus_size;
		rect.top = (int)focus_y - focus_size;
		rect.bottom = (int)focus_y + focus_size;
		if( rect.left < -1000 ) {
			rect.left = -1000;
			rect.right = rect.left + 2*focus_size;
		}
		else if( rect.right > 1000 ) {
			rect.right = 1000;
			rect.left = rect.right - 2*focus_size;
		}
		if( rect.top < -1000 ) {
			rect.top = -1000;
			rect.bottom = rect.top + 2*focus_size;
		}
		else if( rect.bottom > 1000 ) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2*focus_size;
		}

		ArrayList<CameraController.Area> areas = new ArrayList<>();
		areas.add(new CameraController.Area(rect, 1000));
		return areas;
	}

	public boolean touchEvent(MotionEvent event) {
		if( MyDebug.LOG )
			Log.d(TAG, "touch event at : " + event.getX() + " , " + event.getY() + " at time " + event.getEventTime());
		if (this.phase == PHASE_PREVIEW_PAUSED) {
			startCameraPreview();
			return true;
		}
		if( gestureDetector.onTouchEvent(event) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "touch event handled by gestureDetector");
			clearFocusReset();
			return true;
		}
		if (scaleGestureDetector != null)
			scaleGestureDetector.onTouchEvent(event);
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "received touch event, but camera not available");
			return true;
		}
		applicationInterface.touchEvent(event);
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "touch event: " + event.getAction());
		}*/
		if (scaleGestureDetector != null) {
			if (event.getPointerCount() != 1 && !this.is_burst && !this.isOnTimer()) {
				if (!multitouch_zoom) {
					multitouch_zoom = true;
					((MainActivity)this.getContext()).getMainUI().multitouchEventStart();
				}
			} else {
				if (multitouch_zoom) {
					((MainActivity)this.getContext()).getMainUI().multitouchEventStop();
					multitouch_zoom = false;
				}
				scale_zoom_factor = 1;
			}
		}
		if( event.getPointerCount() != 1 ) {
			//multitouch_time = System.currentTimeMillis();
			touch_was_multitouch = true;
			clearFocusReset();
			return true;
		}
		if( event.getAction() != MotionEvent.ACTION_UP ) {
			if( event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1 ) {
				touch_was_multitouch = false;
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					touch_orig_x = event.getX();
					touch_orig_y = event.getY();
					if( MyDebug.LOG )
						Log.d(TAG, "touch down at " + touch_orig_x + " , " + touch_orig_y);
				}
				if (has_focus_area || has_metering_area) {
					clear_focus_runnable = new Runnable() {
						@Override
						public void run() {
							clear_focus_runnable = null;
							touch_was_multitouch = true;
							clearFocusAreas();
						}
					};
					clear_focus_handler.postDelayed(clear_focus_runnable, 500);
				}
			} else if (clear_focus_runnable != null && (Math.abs(touch_orig_x-event.getX()) > 8*scale || Math.abs(touch_orig_y-event.getY()) > 8*scale)) {
				clearFocusReset();
			}
			return true;
		} else {
			clearFocusReset();
		}
		// now only have to handle MotionEvent.ACTION_UP from this point onwards

		if( touch_was_multitouch ) {
			return true;
		}

		if( !this.is_video && Prefs.getTouchCapturePref() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "touch to capture");
			// interpret as if user had clicked take photo/video button, except that we set the focus/metering areas
			this.takePicturePressed();
			return true;
		}

		if( !this.is_video && this.isTakingPhotoOrOnTimer() ) {
			// if video, okay to refocus when recording
			return true;
		}
		
		// ignore swipes
		{
			float x = event.getX();
			float y = event.getY();
			float diff_x = x - touch_orig_x;
			float diff_y = y - touch_orig_y;
			float dist2 = diff_x*diff_x + diff_y*diff_y;
			float tol = 31 * scale; // convert dps to pixels (about 0.5cm)
			if( MyDebug.LOG ) {
				Log.d(TAG, "touched from " + touch_orig_x + " , " + touch_orig_y + " to " + x + " , " + y);
				Log.d(TAG, "dist: " + Math.sqrt(dist2));
				Log.d(TAG, "tol: " + tol);
			}
			if( dist2 > tol*tol ) {
				if( MyDebug.LOG )
					Log.d(TAG, "touch was a swipe");
				return true;
			}
		}

		// note, we always try to force start the preview (in case is_preview_paused has become false)
		// except if recording video (firstly, the preview should be running; secondly, we don't want to reset the phase!)
		if( !this.is_video ) {
			startCameraPreview();
		}
		cancelAutoFocus();

		if( camera_controller != null && (!this.using_face_detection || this.getFacesDetected() == null) ) {
			ArrayList<CameraController.Area> areas = getAreas(event.getX(), event.getY());

			String focus_value = getCurrentFocusValue();
			if ((focus_value != null && (focus_value.equals("focus_mode_infinity") || focus_value.equals("focus_mode_manual2"))) || applicationInterface.isSetExpoMeteringArea()) {
				this.has_metering_area = false;
				if( camera_controller.setMeteringArea(areas) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "set metering area");
					this.has_metering_area = true;
					this.metering_screen_x = (int)event.getX();
					this.metering_screen_y = (int)event.getY();

					return true;
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "didn't set metering area");
				}
			} else {
				this.has_focus_area = false;
				this.has_metering_area = false;
				if( camera_controller.setFocusAndMeteringArea(areas) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "set focus (and metering?) area");
					this.has_focus_area = true;
					this.focus_screen_x = (int)event.getX();
					this.focus_screen_y = (int)event.getY();
					
					focus_zone_changed = true;

					tryAutoFocus(false, true);
					return true;
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "didn't set focus area in this mode, may have set metering");
					// don't set has_focus_area in this mode
				}
			}
		}

		return true;
	}
	
	public void clearFocusReset() {
		if( clear_focus_runnable != null ) {
			clear_focus_handler.removeCallbacks(clear_focus_runnable);
			clear_focus_runnable = null;
		}
	}
	
	/** Handle multitouch zoom.
	 */
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			if( Preview.this.camera_controller != null && Preview.this.has_zoom && !Preview.this.is_burst && !Preview.this.isOnTimer() ) {
				scale_zoom_factor *= detector.getScaleFactor();
				if (scale_zoom_factor >= 1.025f || scale_zoom_factor <= 0.9756f) {
					Preview.this.scaleZoom(scale_zoom_factor);
					scale_zoom_factor = 1;
				}
			}
			return true;
		}
	}
	
	public void clearFocusAreas() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearFocusAreas()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		if (has_focus_area || has_metering_area) {
			// don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
			camera_controller.clearFocusAndMetering();
			has_focus_area = false;
			has_metering_area = false;

		}
		focus_success = FOCUS_DONE;
		focus_zone_changed = false;
		successfully_focused = false;

	}
	
	public void setCenterFocus() {
		if( camera_controller != null && Prefs.getCenterFocusPref() ) {
			final int x = cameraSurface.getView().getWidth()/2;
			final int y = cameraSurface.getView().getHeight()/2;
			if ( camera_controller.setFocusAndMeteringArea(getAreas(x, y)) ) {
				this.focus_screen_x = x;
				this.focus_screen_y = y;
				this.has_focus_area = true;
			}
		}
	}

	public void getMeasureSpec(int [] spec, int widthSpec, int heightSpec) {
		if( !this.hasAspectRatio() ) {
			spec[0] = widthSpec;
			spec[1] = heightSpec;
			return;
		}
		double aspect_ratio = this.getAspectRatio();

		int previewWidth = MeasureSpec.getSize(widthSpec);
		int previewHeight = MeasureSpec.getSize(heightSpec);

		// Get the padding of the border background.
		int hPadding = cameraSurface.getView().getPaddingLeft() + cameraSurface.getView().getPaddingRight();
		int vPadding = cameraSurface.getView().getPaddingTop() + cameraSurface.getView().getPaddingBottom();

		// Resize the preview frame with correct aspect ratio.
		previewWidth -= hPadding;
		previewHeight -= vPadding;

		boolean widthLonger = previewWidth > previewHeight;
		int longSide = (widthLonger ? previewWidth : previewHeight);
		int shortSide = (widthLonger ? previewHeight : previewWidth);
		if (longSide > shortSide * aspect_ratio) {
			longSide = (int) ((double) shortSide * aspect_ratio);
		} else {
			shortSide = (int) ((double) longSide / aspect_ratio);
		}
		if (widthLonger) {
			previewWidth = longSide;
			previewHeight = shortSide;
		} else {
			previewWidth = shortSide;
			previewHeight = longSide;
		}

		// Add the padding of the border.
		previewWidth += hPadding;
		previewHeight += vPadding;

		spec[0] = MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY);
		spec[1] = MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY);
	}
	
	private void mySurfaceCreated() {
		this.has_surface = true;
		this.openCamera();
	}

	private void mySurfaceDestroyed() {
		this.has_surface = false;
		this.closeCamera(false, null);
	}
	
	private void mySurfaceChanged() {
		// surface size is now changed to match the aspect ratio of camera preview - so we shouldn't change the preview to match the surface size, so no need to restart preview here
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		
		// need to force a layoutUI update (e.g., so UI is oriented correctly when app goes idle, device is then rotated, and app is then resumed)
		applicationInterface.layoutUI();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		mySurfaceCreated();
		cameraSurface.getView().setWillNotDraw(false); // see http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		mySurfaceDestroyed();
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( MyDebug.LOG )
			Log.d(TAG, "surfaceChanged " + w + ", " + h);
		if( holder.getSurface() == null ) {
			// preview surface does not exist
			return;
		}
		mySurfaceChanged();
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureAvailable()");
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceCreated();
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureDestroyed()");
		this.set_textureview_size = false;
		this.textureview_w = 0;
		this.textureview_h = 0;
		mySurfaceDestroyed();
		return true;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width, int height) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSurfaceTextureSizeChanged " + width + ", " + height);
		this.set_textureview_size = true;
		this.textureview_w = width;
		this.textureview_h = height;
		mySurfaceChanged();
		if( this.using_camera_2 ) configureTransform();
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
	}

	private void configureTransform() {
		if( MyDebug.LOG )
			Log.d(TAG, "configureTransform");
		if( camera_controller == null || !this.set_preview_size || !this.set_textureview_size ) {
			if( MyDebug.LOG )
				Log.d(TAG, "nothing to do");
			return;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "textureview size: " + textureview_w + ", " + textureview_h);
		int rotation = getDisplayRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, this.textureview_w, this.textureview_h);
		RectF bufferRect = new RectF(0, 0, this.preview_h, this.preview_w);
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if( Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation ) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max(
					(float) textureview_h / preview_h,
					(float) textureview_w / preview_w);
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY);
		}
		cameraSurface.setTransform(matrix);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void stopVideo(boolean from_restart) {
		if( MyDebug.LOG )
			Log.d(TAG, "stopVideo()");
		if( video_recorder == null ) {
			// no need to do anything if not recording
			// (important to exit, otherwise we'll momentarily switch the take photo icon to video mode in MyApplicationInterface.stoppingVideo() when opening the settings in landscape mode
			if( MyDebug.LOG )
				Log.d(TAG, "video wasn't recording anyway");
			return;
		}
		applicationInterface.stoppingVideo();
		if( flashVideoTimerTask != null ) {
			flashVideoTimerTask.cancel();
			flashVideoTimerTask = null;
		}
		if( batteryCheckVideoTimerTask != null ) {
			batteryCheckVideoTimerTask.cancel();
			batteryCheckVideoTimerTask = null;
		}
		if( !from_restart ) {
			remaining_restart_video = 0;
		}
		if( video_recorder != null ) { // check again, just to be safe
			if( MyDebug.LOG )
				Log.d(TAG, "stop video recording");
			//this.phase = PHASE_NORMAL;
			video_recorder.setOnErrorListener(null);
			video_recorder.setOnInfoListener(null);

			try {
				if( MyDebug.LOG )
					Log.d(TAG, "about to call video_recorder.stop()");
				video_recorder.stop();
				if( MyDebug.LOG )
					Log.d(TAG, "done video_recorder.stop()");
			}
			catch(RuntimeException e) {
				// stop() can throw a RuntimeException if stop is called too soon after start - this indicates the video file is corrupt, and should be deleted
				if( MyDebug.LOG )
					Log.d(TAG, "runtime exception when stopping video");
				if( videoFileInfo.video_method == ApplicationInterface.VIDEOMETHOD_SAF ) {
					if( videoFileInfo.video_uri != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "delete corrupt video: " + videoFileInfo.video_uri);
						try {
							DocumentsContract.deleteDocument(getContext().getContentResolver(), videoFileInfo.video_uri);
						}
						catch(FileNotFoundException e2) {
							// note, Android Studio reports a warning that FileNotFoundException isn't thrown, but it can be
							// thrown by DocumentsContract.deleteDocument - and we get an error if we try to remove the catch!
							if( MyDebug.LOG )
								Log.e(TAG, "exception when deleting " + videoFileInfo.video_uri);
							e2.printStackTrace();
						}
					}
				}
				else if( videoFileInfo.video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
					if( videoFileInfo.video_filename != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "delete corrupt video: " + videoFileInfo.video_filename);
						File file = new File(videoFileInfo.video_filename);
						if( !file.delete() ) {
							if( MyDebug.LOG )
								Log.e(TAG, "failed to delete corrupt video: " + videoFileInfo.video_filename);
						}
					}
				}
				// else don't delete if a plain Uri

				videoFileInfo = new VideoFileInfo();
				// if video recording is stopped quickly after starting, it's normal that we might not have saved a valid file, so no need to display a message
				if( !video_start_time_set || System.currentTimeMillis() - video_start_time > 2000 ) {
					VideoProfile profile = getVideoProfile();
					applicationInterface.onVideoRecordStopError(profile);
				}
			}
			videoRecordingStopped();
		}
	}
	
	private void videoRecordingStopped() {
		if( MyDebug.LOG )
			Log.d(TAG, "reset video_recorder");
		video_recorder.reset();
		if( MyDebug.LOG )
			Log.d(TAG, "release video_recorder");
		video_recorder.release();
		video_recorder = null;
		video_recorder_is_paused = false;
		applicationInterface.cameraInOperation(false);
		reconnectCamera(false); // n.b., if something went wrong with video, then we reopen the camera - which may fail (or simply not reopen, e.g., if app is now paused)
		applicationInterface.stoppedVideo(videoFileInfo.video_method, videoFileInfo.video_uri, videoFileInfo.video_filename);
		videoFileInfo = new VideoFileInfo();
	}

	private Context getContext() {
		return applicationInterface.getContext();
	}

	/** Restart video - either due to hitting maximum filesize, or maximum duration.
	 */
	private void restartVideo(boolean due_to_max_filesize) {
		if( MyDebug.LOG )
			Log.d(TAG, "restartVideo()");
		if( video_recorder != null ) {
			if( due_to_max_filesize ) {
				long last_time = System.currentTimeMillis() - video_start_time;
				video_accumulated_time += last_time;
				if( MyDebug.LOG ) {
					Log.d(TAG, "last_time: " + last_time);
					Log.d(TAG, "video_accumulated_time is now: " + video_accumulated_time);
				}
			}
			else {
				video_accumulated_time = 0;
			}
			stopVideo(true); // this will also stop the timertask

			// handle restart
			if( MyDebug.LOG ) {
				if( due_to_max_filesize )
					Log.d(TAG, "restarting due to maximum filesize");
				else
					Log.d(TAG, "remaining_restart_video is: " + remaining_restart_video);
			}
			if( due_to_max_filesize ) {
				long video_max_duration = Prefs.getVideoMaxDurationPref();
				if( video_max_duration > 0 ) {
					video_max_duration -= video_accumulated_time;
					if( video_max_duration < min_safe_restart_video_time ) {
						// if there's less than 1s to go, ignore it - don't want to risk the resultant video being corrupt or throwing error, due to stopping too soon
						// so instead just pretend we hit the max duration instead
						if( MyDebug.LOG )
							Log.d(TAG, "hit max filesize, but max time duration is also set, with remaining time less than 1s: " + video_max_duration);
						due_to_max_filesize = false;
					}
				}
			}
			if( due_to_max_filesize || remaining_restart_video > 0 ) {
				if( is_video ) {
					String toast = null;
					if( !due_to_max_filesize )
						toast = remaining_restart_video + " " + getContext().getResources().getString(R.string.repeats_to_go);
					takePicture(due_to_max_filesize, false);
					if( !due_to_max_filesize ) {
						showToast(null, toast); // show the toast afterwards, as we're hogging the UI thread here, and media recorder takes time to start up
						// must decrement after calling takePicture(), so that takePicture() doesn't reset the value of remaining_restart_video
						remaining_restart_video--;
					}
				}
				else {
					remaining_restart_video = 0;
				}
			}
		}
	}
	
	private void reconnectCamera(boolean quiet) {
		if( MyDebug.LOG )
			Log.d(TAG, "reconnectCamera()");
		if( camera_controller != null ) { // just to be safe
			try {
				camera_controller.reconnect();
				this.setPreviewPaused(false);
			}
			catch(CameraControllerException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to reconnect to camera");
				e.printStackTrace();
				applicationInterface.onFailedReconnectError();
				closeCamera(false, null);
			}
			try {
				tryAutoFocus(false, false);
			}
			catch(RuntimeException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "tryAutoFocus() threw exception: " + e.getMessage());
				e.printStackTrace();
				// this happens on Nexus 7 if trying to record video at bitrate 50Mbits or higher - it's fair enough that it fails, but we need to recover without a crash!
				// not safe to call closeCamera, as any call to getParameters may cause a RuntimeException
				// update: can no longer reproduce failures on Nexus 7?!
				this.is_preview_started = false;
				if( !quiet ) {
					VideoProfile profile = getVideoProfile();
					applicationInterface.onVideoRecordStopError(profile);
				}
				camera_controller.release();
				camera_controller = null;
				camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSED;
				openCamera();
			}
		}
	}

	private interface CloseCameraCallback {
		void onClosed();
	}

	private class CloseCameraTask extends AsyncTask<Void, Void, Void> {
		private static final String TAG = "CloseCameraTask";

		boolean reopen; // if set to true, reopen the camera once closed

		final CameraController camera_controller_local;
		final CloseCameraCallback closeCameraCallback;

		CloseCameraTask(CameraController camera_controller_local, CloseCameraCallback closeCameraCallback) {
			this.camera_controller_local = camera_controller_local;
			this.closeCameraCallback = closeCameraCallback;
		}

		@Override
		protected Void doInBackground(Void... voids) {
			long debug_time = 0;
			if( MyDebug.LOG ) {
				Log.d(TAG, "doInBackground, async task: " + this);
				debug_time = System.currentTimeMillis();
			}
			camera_controller_local.stopPreview();
			if( MyDebug.LOG ) {
				Log.d(TAG, "time to stop preview: " + (System.currentTimeMillis() - debug_time));
			}
			camera_controller_local.release();
			if( MyDebug.LOG ) {
				Log.d(TAG, "time to release camera controller: " + (System.currentTimeMillis() - debug_time));
			}
			return null;
		}

		/** The system calls this to perform work in the UI thread and delivers
		 * the result from doInBackground() */
		protected void onPostExecute(Void result) {
			if( MyDebug.LOG )
				Log.d(TAG, "onPostExecute, async task: " + this);
			camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSED;
			close_camera_task = null; // just to be safe
			if( closeCameraCallback != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "onPostExecute, calling closeCameraCallback.onClosed");
				closeCameraCallback.onClosed();
			}
			if( reopen ) {
				if( MyDebug.LOG )
					Log.d(TAG, "onPostExecute, reopen camera");
				openCamera();
			}
			if( MyDebug.LOG )
				Log.d(TAG, "onPostExecute done, async task: " + this);
		}
	}

	public void closeCamera() {
		if (camera_controller != null && camera_open_state != CameraOpenState.CAMERAOPENSTATE_CLOSING)
			closeCamera(true, null);
	}

	private void closeCamera(boolean async, final CloseCameraCallback closeCameraCallback) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "closeCamera()");
			Log.d(TAG, "async: " + async);
			debug_time = System.currentTimeMillis();
		}
		removePendingContinuousFocusReset();
		has_focus_area = false;
		has_metering_area = false;
		focus_success = FOCUS_DONE;
		focus_started_time = -1;
		focus_zone_changed = false;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
			// no need to call camera_controller.setCaptureFollowAutofocusHint() as we're closing the camera
		}
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		if( continuous_focus_move_is_started ) {
			continuous_focus_move_is_started = false;
			applicationInterface.onContinuousFocusMove(false);
		}
		applicationInterface.cameraClosed();
		cancelTimer();
		if( camera_controller != null ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "close camera_controller");
			}
			if( video_recorder != null ) {
				stopVideo(false);
			}
			// make sure we're into continuous video mode for closing
			// workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
			// so to be safe, we always reset to continuous video mode
			this.updateFocusForVideo();
			// need to check for camera being non-null again - if an error occurred stopping the video, we will have closed the camera, and may not be able to reopen
			if( camera_controller != null ) {
				//camera.setPreviewCallback(null);
				if( MyDebug.LOG ) {
					Log.d(TAG, "closeCamera: about to pause preview: " + (System.currentTimeMillis() - debug_time));
				}
				pausePreview(false);
				// we set camera_controller to null before starting background thread, so that other callers won't try
				// to use it
				final CameraController camera_controller_local = camera_controller;
				camera_controller = null;
				if( async ) {
					if( MyDebug.LOG )
						Log.d(TAG, "close camera on background async");
					camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSING;
					close_camera_task = new CloseCameraTask(camera_controller_local, closeCameraCallback);
					close_camera_task.execute();
				}
				else {
					if( MyDebug.LOG ) {
						Log.d(TAG, "closeCamera: about to release camera controller: " + (System.currentTimeMillis() - debug_time));
					}
					camera_controller_local.stopPreview();
					if( MyDebug.LOG ) {
						Log.d(TAG, "time to stop preview: " + (System.currentTimeMillis() - debug_time));
					}
					camera_controller_local.release();
					camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSED;
				}
			}
		}
		else {
			if( MyDebug.LOG ) {
				Log.d(TAG, "camera_controller isn't open");
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "closeCamera: total time: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	public void cancelTimer() {
		if( MyDebug.LOG )
			Log.d(TAG, "cancelTimer()");
		if( this.isOnTimer() || this.is_burst ) {
			applicationInterface.stoppingTimer(this.is_burst);
			this.is_burst = false;
			remaining_burst_photos = 0;
			burst_captured = 0;
			if (!is_video && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) focusBracketingStopped();
		}
		else if (wait_face) {
			wait_face = false;
			applicationInterface.stoppingTimer(false);
		}
		if( this.isOnTimer() ) {
			takePictureTimerTask.cancel();
			takePictureTimerTask = null;
			if( beepTimerTask != null ) {
				beepTimerTask.cancel();
				beepTimerTask = null;
			}
			this.phase = PHASE_NORMAL;
			show_timer = false;
			if( MyDebug.LOG )
				Log.d(TAG, "cancelled camera timer");
		}
	}
	
	public void cancelWaitFace() {
	}

	public void pausePreview(boolean stop_preview) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview()");
			debug_time = System.currentTimeMillis();
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		// make sure we're into continuous video mode
		// workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
		// so to be safe, we always reset to continuous video mode
		// although I've now fixed this at the level where we close the settings, I've put this guard here, just in case the problem occurs from elsewhere
		this.updateFocusForVideo();
		this.setPreviewPaused(false);
		if( stop_preview ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "pausePreview: about to stop preview: " + (System.currentTimeMillis() - debug_time));
			}
			camera_controller.stopPreview();
			if( MyDebug.LOG ) {
				Log.d(TAG, "pausePreview: time to stop preview: " + (System.currentTimeMillis() - debug_time));
			}
		}
		this.phase = PHASE_NORMAL;
		this.is_preview_started = false;
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview: about to call cameraInOperation: " + (System.currentTimeMillis() - debug_time));
		}
		//applicationInterface.cameraInOperation(false);
		if( MyDebug.LOG ) {
			Log.d(TAG, "pausePreview: total time: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	//private int debug_count_opencamera = 0; // see usage below

	/** Try to open the camera. Should only be called if camera_controller==null.
	 *  The camera will be opened on a background thread, so won't be available upon
	 *  exit of this function.
	 *  If camera_open_state is already CAMERAOPENSTATE_OPENING, this method does nothing.
	 */
	private void openCamera() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera()");
			debug_time = System.currentTimeMillis();
		}
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENING ) {
			if( MyDebug.LOG )
				Log.d(TAG, "already opening camera in background thread");
			return;
		}
		else if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSING ) {
			if( MyDebug.LOG )
				Log.d(TAG, "tried to open camera while camera is still closing in background thread");
			return;
		}
		// need to init everything now, in case we don't open the camera (but these may already be initialised from an earlier call - e.g., if we are now switching to another camera)
		// n.b., don't reset has_set_location, as we can remember the location when switching camera
		is_preview_started = false; // theoretically should be false anyway, but I had one RuntimeException from surfaceCreated()->openCamera()->setupCamera()->setPreviewSize() because is_preview_started was true, even though the preview couldn't have been started
		set_preview_size = false;
		preview_w = 0;
		preview_h = 0;
		has_focus_area = false;
		has_metering_area = false;
		focus_success = FOCUS_DONE;
		focus_zone_changed = false;
		focus_started_time = -1;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
			// no need to call camera_controller.setCaptureFollowAutofocusHint() as we're opening the camera
		}
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		scene_modes = null;
		has_zoom = false;
		max_zoom_factor = 0;
		minimum_focus_distance = 0.0f;
		zoom_ratios = null;
		faces_detected = null;
		supports_face_detection = false;
		using_face_detection = false;
		supports_video_stabilization = false;
		supports_photo_video_recording = false;
		can_disable_shutter_sound = false;
		tonemap_max_curve_points = 0;
		supports_tonemap_curve = false;
		color_effects = null;
		white_balances = null;
		isos = null;
		supports_white_balance_temperature = false;
		min_temperature = 0;
		max_temperature = 0;
		supports_iso_range = false;
		min_iso = 0;
		max_iso = 0;
		supports_exposure_time = false;
		min_exposure_time = 0L;
		max_exposure_time = 0L;
		exposures = null;
		min_exposure = 0;
		max_exposure = 0;
		exposure_step = 0.0f;
		supports_expo_bracketing = false;
		max_expo_bracketing_n_images = 0;
		supports_raw = false;
		view_angle_x = 55.0f; // set a sensible default
		view_angle_y = 43.0f; // set a sensible default
		sizes = null;
		current_size_index = -1;
		has_capture_rate_factor = false;
		capture_rate_factor = 1.0f;
		video_high_speed = false;
		supports_video = true;
		supports_video_high_speed = false;
		video_quality_handler.resetCurrentQuality();
		supported_flash_values = null;
		current_flash_index = -1;
		supported_focus_values = null;
		current_focus_index = -1;
		max_num_focus_areas = 0;
		max_num_metering_areas = 0;
		applicationInterface.cameraInOperation(false);
		if( !this.has_surface ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "preview surface not yet available");
			}
			return;
		}
		if( this.app_is_paused ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "don't open camera as app is paused");
			}
			return;
		}
		
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
			// we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
			if( MyDebug.LOG )
				Log.d(TAG, "check for permissions");
			if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "camera permission not available");
				has_permissions = false;
				applicationInterface.requestCameraPermission();
				// return for now - the application should try to reopen the camera if permission is granted
				return;
			}
			if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ) {
				if( MyDebug.LOG )
					Log.d(TAG, "storage permission not available");
				has_permissions = false;
				applicationInterface.requestStoragePermission();
				// return for now - the application should try to reopen the camera if permission is granted
				return;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "permissions available");
		}
		// set in case this was previously set to false
		has_permissions = true;

		/*{
			// debug
			if( debug_count_opencamera++ == 0 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "debug: don't open camera yet");
				return;
			}
		}*/

		camera_open_state = CameraOpenState.CAMERAOPENSTATE_OPENING;
		int cameraId = Prefs.getCameraIdPref();
		if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "invalid cameraId: " + cameraId);
			cameraId = 0;
			Prefs.setCameraIdPref(cameraId);
		}
		
		Prefs.updatePhotoMode();

		boolean use_background_thread = false;
		//final boolean use_background_thread = true;
//		final boolean use_background_thread = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
		/* Opening camera on background thread is important so that we don't block the UI thread:
		 *   - For old Camera API, this is recommended behaviour by Google for Camera.open().
			 - For Camera2, the manager.openCamera() call is asynchronous, but CameraController2
			   waits for it to open, so it's still important that we run that in a background thread.
		 * In theory this works for all Android versions, but this caused problems of Galaxy Nexus
		 * with tests testTakePhotoAutoLevel(), testTakePhotoAutoLevelAngles() (various camera
		 * errors/exceptions, failing to taking photos). Since this is a significant change, this is
		 * for now limited to modern devices.
		 */
		if( use_background_thread ) {
			final int cameraId_f = cameraId;

			open_camera_task = new AsyncTask<Void, Void, CameraController>() {
				private static final String TAG = "HedgeCam/Preview/openCamera";

				@Override
				protected CameraController doInBackground(Void... voids) {
					if( MyDebug.LOG )
						Log.d(TAG, "doInBackground, async task: " + this);
					return openCameraCore(cameraId_f);
				}

				/** The system calls this to perform work in the UI thread and delivers
				 * the result from doInBackground() */
				protected void onPostExecute(CameraController camera_controller) {
					if( MyDebug.LOG )
						Log.d(TAG, "onPostExecute, async task: " + this);
					// see note in openCameraCore() for why we set camera_controller here
					Preview.this.camera_controller = camera_controller;
					cameraOpened();
					// set camera_open_state after cameraOpened, just in case a non-UI thread is listening for this - also
					// important for test code waitUntilCameraOpened(), as test code runs on a different thread
					camera_open_state = CameraOpenState.CAMERAOPENSTATE_OPENED;
					open_camera_task = null; // just to be safe
					if( MyDebug.LOG )
						Log.d(TAG, "onPostExecute done, async task: " + this);
				}

				protected void onCancelled(CameraController camera_controller) {
					if( MyDebug.LOG ) {
						Log.d(TAG, "onCancelled, async task: " + this);
						Log.d(TAG, "camera_controller: " + camera_controller);
					}
					// this typically means the application has paused whilst we were opening camera in background - so should just
					// dispose of the camera controller
					if( camera_controller != null ) {
						// this is the local camera_controller, not Preview.this.camera_controller!
						camera_controller.release();
					}
					camera_open_state = CameraOpenState.CAMERAOPENSTATE_OPENED; // n.b., still set OPENED state - important for test thread to know that this callback is complete
					open_camera_task = null; // just to be safe
					if( MyDebug.LOG )
						Log.d(TAG, "onCancelled done, async task: " + this);
				}
			}.execute();
		}
		else {
			this.camera_controller = openCameraCore(cameraId);
			if( MyDebug.LOG ) {
				Log.d(TAG, "openCamera: time after opening camera: " + (System.currentTimeMillis() - debug_time));
			}

			cameraOpened();
			camera_open_state = CameraOpenState.CAMERAOPENSTATE_OPENED;
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera: total time to open camera: " + (System.currentTimeMillis() - debug_time));
		}
	}

	/** Open the camera - this should be called from background thread, to avoid hogging the UI thread.
	 */
	private CameraController openCameraCore(int cameraId) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "openCameraCore()");
			debug_time = System.currentTimeMillis();
		}
		// We pass a camera controller back to the UI thread rather than assigning to camera_controller here, because:
		// * If we set camera_controller directly, we'd need to synchronize, otherwise risk of memory barrier issues
		// * Risk of race conditions if UI thread accesses camera_controller before we have called cameraOpened().
		CameraController camera_controller_local = null;
		try {
			if( MyDebug.LOG ) {
				Log.d(TAG, "try to open camera: " + cameraId);
				Log.d(TAG, "openCamera: time before opening camera: " + (System.currentTimeMillis() - debug_time));
			}
			if( test_fail_open_camera ) {
				if( MyDebug.LOG )
					Log.d(TAG, "test failing to open camera");
				throw new CameraControllerException();
			}
			CameraController.ErrorCallback cameraErrorCallback = new CameraController.ErrorCallback() {
				public void onError() {
					if( MyDebug.LOG )
						Log.e(TAG, "error from CameraController: camera device failed");
					if( camera_controller != null ) {
						camera_controller = null;
						camera_open_state = CameraOpenState.CAMERAOPENSTATE_CLOSED;
						applicationInterface.onCameraError();
					}
				}
			};
			if( using_camera_2 ) {
				CameraController.ErrorCallback previewErrorCallback = new CameraController.ErrorCallback() {
					public void onError() {
						if( MyDebug.LOG )
							Log.e(TAG, "error from CameraController: preview failed to start");
						applicationInterface.onFailedStartPreview();
					}
				};
				camera_controller_local = new CameraController2(Preview.this.getContext(), cameraId, previewErrorCallback, cameraErrorCallback);
				if( Prefs.useCamera2FakeFlash() ) {
					camera_controller_local.setUseCamera2FakeFlash(true);
				}
			}
			else
				camera_controller_local = new CameraController1(cameraId, cameraErrorCallback);
			//throw new CameraControllerException(); // uncomment to test camera not opening
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to open camera: " + e.getMessage());
			e.printStackTrace();
			camera_controller_local = null;
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera: total time for openCameraCore: " + (System.currentTimeMillis() - debug_time));
		}
		return camera_controller_local;
	}

	/** Called from UI thread after openCameraCore() completes on the background thread.
	 */
	private void cameraOpened() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "cameraOpened()");
			debug_time = System.currentTimeMillis();
		}
		boolean take_photo = false;
		boolean take_video = false;
		if( camera_controller != null ) {
			Activity activity = (Activity)Preview.this.getContext();
			if( MyDebug.LOG )
				Log.d(TAG, "intent: " + activity.getIntent());
			if( activity.getIntent() != null && activity.getIntent().getExtras() != null ) {
				take_photo = activity.getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
				activity.getIntent().removeExtra(TakePhoto.TAKE_PHOTO);
			}
			if( activity.getIntent() != null && activity.getIntent().getExtras() != null ) {
				take_video = activity.getIntent().getExtras().getBoolean(TakeVideo.TAKE_VIDEO);
				activity.getIntent().removeExtra(TakeVideo.TAKE_VIDEO);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "no intent data");
			}
			if( MyDebug.LOG )
				Log.d(TAG, "take_photo?: " + take_photo);

			setCameraDisplayOrientation();
			new OrientationEventListener(activity) {
				@Override
				public void onOrientationChanged(int orientation) {
					Preview.this.onOrientationChanged(orientation);
				}
			}.enable();
			if( MyDebug.LOG ) {
				Log.d(TAG, "openCamera: time after setting orientation: " + (System.currentTimeMillis() - debug_time));
			}

			if( MyDebug.LOG )
				Log.d(TAG, "call setPreviewDisplay");
			cameraSurface.setPreviewDisplay(camera_controller);
			if( MyDebug.LOG ) {
				Log.d(TAG, "openCamera: time after setting preview display: " + (System.currentTimeMillis() - debug_time));
			}

			Prefs.needUpdatePhotoMode();

			if (sharedPreferences.getBoolean(Prefs.RESET_MANUAL_MODE + "_" + camera_controller.getCameraId(), false) && Prefs.getISOPref().equals("manual"))
				Prefs.setISOPref("auto");

			setupCamera(take_photo, take_video);
			if( this.using_camera_2 ) {
				configureTransform();
			}
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "openCamera: total time for cameraOpened: " + (System.currentTimeMillis() - debug_time));
		}
	}


	/** Try to reopen the camera, if not currently open (e.g., permission wasn't granted, but now it is).
	 *  The camera will be opened on a background thread, so won't be available upon
	 *  exit of this function.
	 *  If camera_open_state is already CAMERAOPENSTATE_OPENING, or the camera is already open,
	 *  this method does nothing.
	 */
	public void retryOpenCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "retryOpenCamera()");
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSING ) {
			// when pausing, we close the camera on a background thread - so if this is still happening when we resume,
			// we won't be able to open the camera, so need to open camera when it's closed
			if( MyDebug.LOG )
				Log.d(TAG, "camera still closing");
			if( close_camera_task != null ) { // just to be safe
				close_camera_task.reopen = true;
			}
			else {
				Log.e(TAG, "onResume: state is CAMERAOPENSTATE_CLOSING, but close_camera_task is null");
			}
		} else if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "try to reopen camera");
			this.openCamera();
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "camera already open");
		}
	}

	/** Closes and reopens the camera.
	 *  The camera will be closed and opened on a background thread, so won't be available upon
	 *  exit of this function.
	 */
	public void reopenCamera() {
		if( MyDebug.LOG )
			Log.d(TAG, "reopenCamera()");
		//this.closeCamera(false, null);
		//this.openCamera();
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSING ) {
			// when pausing, we close the camera on a background thread - so if this is still happening when we resume,
			// we won't be able to open the camera, so need to open camera when it's closed
			if( MyDebug.LOG )
				Log.d(TAG, "camera still closing");
			if( close_camera_task != null ) { // just to be safe
				close_camera_task.reopen = true;
			}
			else {
				Log.e(TAG, "onResume: state is CAMERAOPENSTATE_CLOSING, but close_camera_task is null");
			}
		} else if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "try to open camera");
			openCamera();
		} else {
			closeCamera(true, new CloseCameraCallback() {
				@Override
				public void onClosed() {
					if( MyDebug.LOG )
						Log.d(TAG, "CloseCameraCallback.onClosed");
					openCamera();
				}
			});
		}
	}

	/** Returns false if we failed to open the camera because camera or storage permission wasn't available.
	 */
	public boolean hasPermissions() {
		return has_permissions;
	}

	/** Returns true iff the camera is currently being opened on background thread (openCamera() called, but
	 *  camera not yet available).
	 */
	public boolean isOpeningCamera() {
		return camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENING;
	}

	/** Returns true iff we've tried to open the camera (whether or not it was successful).
	 */
	public boolean openCameraAttempted() {
		return camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENED;
	}

	/** Returns true iff we've tried to open the camera, and were unable to do so.
	 */
	public boolean openCameraFailed() {
		return camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENED && camera_controller == null;
	}

	/* Should only be called after camera first opened, or after preview is paused.
	 * take_photo is true if we have been called from the TakePhoto widget (which means
	 * we'll take a photo immediately after startup).
	 */
	public void setupCamera(boolean take_photo, boolean take_video) {
		if( MyDebug.LOG )
			Log.d(TAG, "setupCamera()");
		long debug_time = 0;
		if( MyDebug.LOG ) {
			debug_time = System.currentTimeMillis();
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		boolean do_startup_focus = !take_photo && !take_video && Prefs.getStartupFocusPref();
		if( MyDebug.LOG ) {
			Log.d(TAG, "take_photo? " + take_photo);
			Log.d(TAG, "do_startup_focus? " + do_startup_focus);
		}
		// make sure we're into continuous video mode for reopening
		// workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
		// so to be safe, we always reset to continuous video mode
		// although I've now fixed this at the level where we close the settings, I've put this guard here, just in case the problem occurs from elsewhere
		// we'll switch to the user-requested focus by calling setFocusPref() from setupCameraParameters() below
		this.updateFocusForVideo();

		try {
			setupCameraParameters();
		}
		catch(CameraControllerException e) {
			e.printStackTrace();
			applicationInterface.onCameraError();
			closeCamera(false, null);
			return;
		}

		// now switch to video if saved
		boolean saved_is_video = Prefs.isVideoPref();
		if( MyDebug.LOG ) {
			Log.d(TAG, "saved_is_video: " + saved_is_video);
		}
		if( saved_is_video && !supports_video ) {
			if( MyDebug.LOG )
				Log.d(TAG, "but video not supported");
			saved_is_video = false;
		}
		// must switch video before starting preview
		if( take_photo && this.is_video ) {
			// FIXME
			this.switchVideo(false); // set during_startup to false, as we now need to reset the preview
		}
		else if( (take_video && !this.is_video) || saved_is_video != this.is_video ) {
			this.switchVideo(true);
		}

		// must be done after switching to video mode (so is_video is set correctly)
		if( MyDebug.LOG )
			Log.d(TAG, "is_video?: " + is_video);
		if (this.using_camera_2)
			setupLogProfile();

		// in theory it shouldn't matter if we call setVideoHighSpeed(true) if is_video==false, as it should only have an effect
		// in video mode; but don't set high speed mode in photo mode just to be safe
		// Setup for high speed - must be done after setupCameraParameters() and switching to video mode, but before setPreviewSize() and startCameraPreview()
		camera_controller.setVideoHighSpeed(is_video && video_high_speed);

		if( do_startup_focus && using_camera_2 && camera_controller.supportsAutoFocus() ) {
			// need to switch flash off for autofocus - and for Android L, need to do this before starting preview (otherwise it won't work in time); for old camera API, need to do this after starting preview!
			set_flash_value_after_autofocus = "";
			String old_flash_value = camera_controller.getFlashValue();
			// getFlashValue() may return "" if flash not supported!
			// also set flash_torch - otherwise we get bug where torch doesn't turn on when starting up in video mode (and it's not like we want to turn torch off for startup focus, anyway)
			if( old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
				set_flash_value_after_autofocus = old_flash_value;
				camera_controller.setFlashValue("flash_off");
			}
			if( MyDebug.LOG )
				Log.d(TAG, "set_flash_value_after_autofocus is now: " + set_flash_value_after_autofocus);
		}
		
		camera_controller.setRaw(this.supports_raw && applicationInterface.isRawPref());

		setupExpoBracketing(camera_controller);
		setupBurst(camera_controller);

		camera_controller.setOptimiseAEForDRO( Prefs.getOptimiseAEForDROPref() );
		
		// Must set preview size before starting camera preview
		// and must do it after setting photo vs video mode
		setPreviewSize(); // need to call this when we switch cameras, not just when we run for the first time
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCamera: time after setting preview size: " + (System.currentTimeMillis() - debug_time));
		}
		
		if (this.using_camera_2) {
			boolean uncompressed = sharedPreferences.getBoolean(Prefs.UNCOMPRESSED_PHOTO, false);
			camera_controller.setUncompressedPhoto(uncompressed);
			if (uncompressed) {
				camera_controller.setFullSizeCopy(sharedPreferences.getBoolean(Prefs.FULL_SIZE_COPY, false));
			}
		}
		
		// Must call startCameraPreview after checking if face detection is present - probably best to call it after setting all parameters that we want
		startCameraPreview();
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCamera: time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
		}
		
		// must be done after setting parameters, as this function may set parameters
		// also needs to be done after starting preview for some devices (e.g., Nexus 7)
/*		if( this.has_zoom ) {
			int zoom = Prefs.getZoomPref()
			if {zoom != 0 ) {
				zoomTo(zoom);
				if( MyDebug.LOG ) {
					Log.d(TAG, "setupCamera: total time after zoomTo: " + (System.currentTimeMillis() - debug_time));
				}
			}
		}*/
		
		/*if( take_photo ) {
			if( this.is_video ) {
				if( MyDebug.LOG )
					Log.d(TAG, "switch to video for take_photo widget");
				this.switchVideo(false); // set during_startup to false, as we now need to reset the preview
			}
		}*/

		applicationInterface.cameraSetup(); // must call this after the above take_photo code for calling switchVideo
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCamera: total time after cameraSetup: " + (System.currentTimeMillis() - debug_time));
		}

		if (Prefs.getCenterFocusPref()) {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "set center focus");
					setCenterFocus();
				}
			}, 200);
		}
		String focus_value = getCurrentFocusValue();

		if( take_photo || take_video ) {
			// take photo after a delay - otherwise we sometimes get a black image?!
			// also need a longer delay for continuous picture focus, to allow a chance to focus - 1000ms seems to work okay for Nexus 6, put 1500ms to be safe
			final int delay = ( focus_value != null && focus_value.equals("focus_mode_continuous_picture") ) ? 1500 : 500;
			if( MyDebug.LOG )
				Log.d(TAG, "delay for take photo: " + delay);
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "do automatic take picture");
					takePicture(false, false);
				}
			}, delay);
		}

		if( do_startup_focus && (focus_value != null && (focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro"))) ) {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "do startup autofocus");
					tryAutoFocus(true, false); // so we get the autofocus when starting up - we do this on a delay, as calling it immediately means the autofocus doesn't seem to work properly sometimes (at least on Galaxy Nexus)
				}
			}, 500);
		}
		
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCamera: total time after setupCamera: " + (System.currentTimeMillis() - debug_time));
		}
	}

	public void setupLogProfile() {
		if (camera_controller != null) {
			float video_log_profile_strength = (this.is_video && supports_tonemap_curve) ? Prefs.getVideoLogProfile() : 0.0f;
			if( MyDebug.LOG ) {
				Log.d(TAG, "video_log_profile_strength: " + video_log_profile_strength);
			}
			camera_controller.setLogProfile(video_log_profile_strength);
		}
	}

	public void setupExpoBracketing(final CameraController camera_controller) {
		if (camera_controller == null) return;

		if( this.supports_expo_bracketing && Prefs.isExpoBracketingPref() ) {
			camera_controller.setExpoBracketing(true);
			camera_controller.setExpoBracketingNImages( Prefs.getExpoBracketingNImagesPref() );
			camera_controller.setExpoBracketingStops( Prefs.getExpoBracketingStopsUpPref(), Prefs.getExpoBracketingStopsDownPref() );
			if (!this.supports_exposure_time) {
				camera_controller.setExposureCompensationDelay(Prefs.getExposureCompensationDelayPref());
			}
		}
		else {
			camera_controller.setExpoBracketing(false);
		}
	}

	public void setupBurst(final CameraController camera_controller) {
		if (camera_controller == null) return;

		boolean want_burst = Prefs.isCameraBurstPref();
		camera_controller.setWantBurst( want_burst );
		if (want_burst) camera_controller.setWantBurstCount( want_burst ? Prefs.getBurstCount() : 0 );
	}

	private void setupCameraParameters() throws CameraControllerException {
		if( MyDebug.LOG )
			Log.d(TAG, "setupCameraParameters()");
		long debug_time = 0;
		if( MyDebug.LOG ) {
			debug_time = System.currentTimeMillis();
		}
		{
			// get available scene modes
			// important, from old Camera API docs:
			// "Changing scene mode may override other parameters (such as flash mode, focus mode, white balance).
			// For example, suppose originally flash mode is on and supported flash modes are on/off. In night
			// scene mode, both flash mode and supported flash mode may be changed to off. After setting scene
			// mode, applications should call getParameters to know if some parameters are changed."
			// this doesn't appear to apply to Camera2 API, but we still might as well set scene mode first
			if( MyDebug.LOG )
				Log.d(TAG, "set up scene mode");
			String value = Prefs.getSceneModePref();
			if( MyDebug.LOG )
				Log.d(TAG, "saved scene mode: " + value);

			CameraController.SupportedValues supported_values = camera_controller.setSceneMode(value);
			if( supported_values != null ) {
				scene_modes = supported_values.values;
				// now save, so it's available for PreferenceActivity
				Prefs.setSceneModePref(supported_values.selected_value);
			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				Prefs.clearSceneModePref();
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after setting scene mode: " + (System.currentTimeMillis() - debug_time));
		}
		
		{
			// grab all read-only info from parameters
			if( MyDebug.LOG )
				Log.d(TAG, "grab info from parameters");
			CameraController.CameraFeatures camera_features = camera_controller.getCameraFeatures();
			this.hardware_level = camera_features.hardware_level;
			this.has_zoom = camera_features.is_zoom_supported;
			if( this.has_zoom ) {
				this.max_zoom_factor = camera_features.max_zoom;
				this.zoom_ratios = camera_features.zoom_ratios;
			}
			this.minimum_focus_distance = camera_features.minimum_focus_distance;
			if (minimum_focus_distance > 0.0f) {
				String pref_value = sharedPreferences.getString(Prefs.MIN_FOCUS_DISTANCE + "_" + camera_controller.getCameraId(), "default");
				if (!pref_value.equals("default")) {
					int focus;
					try {focus = Integer.parseInt(pref_value);}
					catch (NumberFormatException e) {focus = 0;}
					
					if (focus != 0) {
						minimum_focus_distance = 100.0f/focus;
					}
				}
				
				String pref_key = Prefs.FOCUS_DISTANCE_CALIBRATION + "_" + camera_controller.getCameraId();
				if (sharedPreferences.contains(pref_key)) {
					boolean remove = false;
					float value = 0.0f;
					pref_value = sharedPreferences.getString(pref_key, "0");
					if (pref_value.equals("") || pref_value.equals("0")) {
						remove = true;
					} else {
						try {
							value = Float.parseFloat(sharedPreferences.getString(pref_key, "0"));
						}
						catch(NumberFormatException exception) {
							remove = true;
						}
					}
					
					if (remove) {
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.remove(pref_key);
						editor.apply();
						
						camera_controller.setFocusDistanceCalibration(0.0f);
					} else {
						camera_controller.setFocusDistanceCalibration(value);
					}
				}
			}
			this.supports_face_detection = camera_features.supports_face_detection;
			this.sizes = camera_features.picture_sizes;
			supported_flash_values = camera_features.supported_flash_values;
			supported_focus_values = camera_features.supported_focus_values;
			this.max_num_focus_areas = camera_features.max_num_focus_areas;
			this.max_num_metering_areas = camera_features.max_num_metering_areas;
			this.is_auto_adjustment_lock_supported = camera_features.is_auto_adjustment_lock_supported;
			this.supports_video_stabilization = camera_features.is_video_stabilization_supported;
			this.supports_photo_video_recording = camera_features.is_photo_video_recording_supported;
			this.can_disable_shutter_sound = camera_features.can_disable_shutter_sound;
			this.tonemap_max_curve_points = camera_features.tonemap_max_curve_points;
			this.supports_tonemap_curve = camera_features.supports_tonemap_curve;
			this.supports_white_balance_temperature = camera_features.supports_white_balance_temperature;
			this.min_temperature = camera_features.min_temperature;
			this.max_temperature = camera_features.max_temperature;
			this.supports_iso_range = camera_features.supports_iso_range;
			this.min_iso = camera_features.min_iso;
			this.max_iso = camera_features.max_iso;
			this.supports_exposure_time = camera_features.supports_exposure_time;
			this.min_exposure_time = camera_features.min_exposure_time;
			this.max_exposure_time = camera_features.max_exposure_time;
			this.min_exposure = camera_features.min_exposure;
			this.max_exposure = camera_features.max_exposure;
			this.exposure_step = camera_features.exposure_step;
			this.supports_expo_bracketing = camera_features.supports_expo_bracketing;
			this.max_expo_bracketing_n_images = camera_features.max_expo_bracketing_n_images;
			this.supports_raw = camera_features.supports_raw;
			this.view_angle_x = camera_features.view_angle_x;
			this.view_angle_y = camera_features.view_angle_y;
			if (sharedPreferences.getBoolean(Prefs.USE_1920X1088, false)) {
				for(int i=0;i<camera_features.video_sizes.size();i++) {
					CameraController.Size size = camera_features.video_sizes.get(i);
					if (size.width == 1920 && size.height == 1080) {
						camera_features.video_sizes.remove(i);
						camera_features.video_sizes.add(new CameraController.Size(1920, 1088));
						break;
					}
				}
			}
			this.supports_video_high_speed = camera_features.video_sizes_high_speed != null && camera_features.video_sizes_high_speed.size() > 0;
			this.video_quality_handler.setVideoSizes(camera_features.video_sizes);
			this.video_quality_handler.setVideoSizesHighSpeed(camera_features.video_sizes_high_speed);
			this.supported_preview_sizes = camera_features.preview_sizes;
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after getting read only info: " + (System.currentTimeMillis() - debug_time));
		}
		
		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up face detection");
			// get face detection supported
			this.faces_detected = null;
			if( this.supports_face_detection ) {
				this.using_face_detection = Prefs.getFaceDetectionPref();
			}
			else {
				this.using_face_detection = false;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "supports_face_detection?: " + supports_face_detection);
				Log.d(TAG, "using_face_detection?: " + using_face_detection);
			}
			if( this.using_face_detection ) {
				class MyFaceDetectionListener implements CameraController.FaceDetectionListener {
					private boolean face_detected = false;
					
					@Override
					public void onFaceDetection(CameraController.Face[] faces) {
						if( MyDebug.LOG )
							Log.d(TAG, "Faces detected: "+faces.length);

						faces_detected = new CameraController.Face[faces.length];
						System.arraycopy(faces, 0, faces_detected, 0, faces.length);
						
						if (face_detected != (faces.length > 0)) {
							face_detected = !face_detected;
							
							onFaceDetected(face_detected);
						}
					}
				}
				camera_controller.setFaceDetectionListener(new MyFaceDetectionListener());
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after setting face detection: " + (System.currentTimeMillis() - debug_time));
		}
		
		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up video stabilization");
			if( this.supports_video_stabilization ) {
				boolean using_video_stabilization = Prefs.getVideoStabilizationPref();
				if( MyDebug.LOG )
					Log.d(TAG, "using_video_stabilization?: " + using_video_stabilization);
				camera_controller.setVideoStabilization(using_video_stabilization);
			}
			if( MyDebug.LOG )
				Log.d(TAG, "supports_video_stabilization?: " + supports_video_stabilization);
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after video stabilization: " + (System.currentTimeMillis() - debug_time));
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up color effect");
			String value = Prefs.getColorEffectPref();
			if( MyDebug.LOG )
				Log.d(TAG, "saved color effect: " + value);

			CameraController.SupportedValues supported_values = camera_controller.setColorEffect(value);
			if( supported_values != null ) {
				color_effects = supported_values.values;
				// now save, so it's available for PreferenceActivity
				Prefs.setColorEffectPref(supported_values.selected_value);
			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				Prefs.clearColorEffectPref();
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after color effect: " + (System.currentTimeMillis() - debug_time));
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up white balance");
			String value = Prefs.getWhiteBalancePref();
			if( MyDebug.LOG )
				Log.d(TAG, "saved white balance: " + value);

			CameraController.SupportedValues supported_values = camera_controller.setWhiteBalance(value);
			if( supported_values != null ) {
				white_balances = supported_values.values;
				// now save, so it's available for PreferenceActivity
				Prefs.setWhiteBalancePref(supported_values.selected_value);
			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				Prefs.clearWhiteBalancePref();
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after white balance: " + (System.currentTimeMillis() - debug_time));
		}
		
		// must be done before setting flash modes, as we may remove flash modes if in manual mode
		if( MyDebug.LOG )
			Log.d(TAG, "set up iso");
		String value = Prefs.getISOPref();
		if( MyDebug.LOG )
			Log.d(TAG, "saved iso: " + value);
		boolean is_manual_mode = false;
		if( supports_iso_range ) {
			// in this mode, we can set any ISO value from min to max
			this.isos = null; // if supports_iso_range==true, caller shouldn't be using getSupportedISOs()

			// now set the desired ISO mode/value
			if( value.equals("auto") ) {
				if( MyDebug.LOG )
					Log.d(TAG, "setting auto iso");
				camera_controller.setManualISO(false, 0);
			}
			else if( value.equals("manual") ) {
				camera_controller.setManualMode(true);
				is_manual_mode = true;
			} else {
				int iso = parseManualISOValue(value);
				if( iso >= 0 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "iso: " + iso);
					camera_controller.setManualISO(true, iso);
				}
				else {
					// failed to parse
					camera_controller.setManualISO(false, 0);
					value = "auto"; // so we switch the preferences back to auto mode, rather than the invalid value
				}
			}
		}
		else {
			// in this mode, any support for ISO is only the specific ISOs offered by the CameraController
			CameraController.SupportedValues supported_values = camera_controller.setISO(value);
			if( supported_values != null ) {
				isos = supported_values.values;
				// now save, so it's available for PreferenceActivity
				Prefs.setISOPref(supported_values.selected_value);

			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				Prefs.clearISOPref();
			}
		}

		if( is_manual_mode ) {
			if( this.using_camera_2 && supported_flash_values != null ) {
				// flash modes not supported when using Camera2 and manual ISO
				// (it's unclear flash is useful - ideally we'd at least offer torch, but ISO seems to reset to 100 when flash/torch is on!)
				supported_flash_values = null;
				if( MyDebug.LOG )
					Log.d(TAG, "flash not supported in Camera2 manual mode");
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after manual iso: " + (System.currentTimeMillis() - debug_time));
		}

		{
			if( MyDebug.LOG ) {
				Log.d(TAG, "set up exposure compensation");
				Log.d(TAG, "min_exposure: " + min_exposure);
				Log.d(TAG, "max_exposure: " + max_exposure);
			}
			// get min/max exposure
			exposures = null;
			if( min_exposure != 0 || max_exposure != 0 ) {
				exposures = new ArrayList<>();
				for(int i=min_exposure;i<=max_exposure;i++) {
					exposures.add("" + i);
				}
				// if in manual ISO mode, we still want to get the valid exposure compensations, but shouldn't set exposure compensation
				if( !is_manual_mode ) {
					int exposure = Prefs.getExposureCompensationPref();
					if( exposure < min_exposure || exposure > max_exposure ) {
						exposure = 0;
						if( MyDebug.LOG )
							Log.d(TAG, "saved exposure not supported, reset to 0");
						if( exposure < min_exposure || exposure > max_exposure ) {
							if( MyDebug.LOG )
								Log.d(TAG, "zero isn't an allowed exposure?! reset to min " + min_exposure);
							exposure = min_exposure;
						}
					}
					camera_controller.setExposureCompensation(exposure);
					// now save, so it's available for PreferenceActivity
					Prefs.setExposureCompensationPref(exposure);
				}
			}
			else {
				// delete key in case it's present (e.g., if feature no longer available due to change in OS, or switching APIs)
				Prefs.clearExposureCompensationPref();
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after exposures: " + (System.currentTimeMillis() - debug_time));
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up picture sizes");
			if( MyDebug.LOG ) {
				for(int i=0;i<sizes.size();i++) {
					CameraController.Size size = sizes.get(i);
					Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
				}
			}
			current_size_index = -1;
			Pair<Integer, Integer> resolution = Prefs.getCameraResolutionPref();
			if( resolution != null ) {
				int resolution_w = resolution.first;
				int resolution_h = resolution.second;
				// now find size in valid list
				for(int i=0;i<sizes.size() && current_size_index==-1;i++) {
					CameraController.Size size = sizes.get(i);
					if( size.width == resolution_w && size.height == resolution_h ) {
						current_size_index = i;
						if( MyDebug.LOG )
							Log.d(TAG, "set current_size_index to: " + current_size_index);
					}
				}
				if( current_size_index == -1 ) {
					if( MyDebug.LOG )
						Log.e(TAG, "failed to find valid size");
				}
			}

			if( current_size_index == -1 ) {
				// set to largest
				CameraController.Size current_size = null;
				for(int i=0;i<sizes.size();i++) {
					CameraController.Size size = sizes.get(i);
					if( current_size == null || size.width*size.height > current_size.width*current_size.height ) {
						current_size_index = i;
						current_size = size;
					}
				}
			}
			if( current_size_index != -1 ) {
				CameraController.Size current_size = sizes.get(current_size_index);
				if( MyDebug.LOG )
					Log.d(TAG, "Current size index " + current_size_index + ": " + current_size.width + ", " + current_size.height);

				// now save, so it's available for PreferenceActivity
				Prefs.setCameraResolutionPref(current_size.width, current_size.height);
			}
			// size set later in setPreviewSize()
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after picture sizes: " + (System.currentTimeMillis() - debug_time));
		}

		{
			int image_quality = Prefs.getImageQualityPref();
			if( MyDebug.LOG )
				Log.d(TAG, "set up jpeg quality: " + image_quality);
			camera_controller.setJpegQuality(image_quality);
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after jpeg quality: " + (System.currentTimeMillis() - debug_time));
		}

		// get available sizes
		initialiseVideoSizes();
		initialiseVideoQuality();
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after video sizes: " + (System.currentTimeMillis() - debug_time));
		}

		String video_quality_value_s = Prefs.getVideoQualityPref();
		if( MyDebug.LOG )
			Log.d(TAG, "video_quality_value: " + video_quality_value_s);
		video_quality_handler.setCurrentVideoQualityIndex(-1);
		if( video_quality_value_s.length() > 0 ) {
			// parse the saved video quality, and make sure it is still valid
			// now find value in valid list
			for(int i=0;i<video_quality_handler.getSupportedVideoQuality().size() && video_quality_handler.getCurrentVideoQualityIndex()==-1;i++) {
				if( video_quality_handler.getSupportedVideoQuality().get(i).equals(video_quality_value_s) ) {
					video_quality_handler.setCurrentVideoQualityIndex(i);
					if( MyDebug.LOG )
						Log.d(TAG, "set current_video_quality to: " + video_quality_handler.getCurrentVideoQualityIndex());
				}
			}
			if( video_quality_handler.getCurrentVideoQualityIndex() == -1 ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to find valid video_quality");
			}
		}
		if( video_quality_handler.getCurrentVideoQualityIndex() == -1 && video_quality_handler.getSupportedVideoQuality().size() > 0 ) {
			// default to FullHD if available, else pick highest quality
			// (FullHD will give smaller file sizes and generally give better performance than 4K so probably better for most users; also seems to suffer from less problems when using manual ISO in Camera2 API)
			video_quality_handler.setCurrentVideoQualityIndex(0); // start with highest quality
			for(int i=0;i<video_quality_handler.getSupportedVideoQuality().size();i++) {
				if( MyDebug.LOG )
					Log.d(TAG, "check video quality: " + video_quality_handler.getSupportedVideoQuality().get(i));
				CamcorderProfile profile = getCamcorderProfile(video_quality_handler.getSupportedVideoQuality().get(i));
				if( profile.videoFrameWidth == 1920 && profile.videoFrameHeight == 1080 ) {
					video_quality_handler.setCurrentVideoQualityIndex(i);
					break;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "set video_quality value to " + video_quality_handler.getCurrentVideoQuality());
		}

		if( video_quality_handler.getCurrentVideoQualityIndex() != -1 ) {
			// now save, so it's available for PreferenceActivity
			Prefs.setVideoQualityPref(video_quality_handler.getCurrentVideoQuality());
		}
		else {
			// This means video_quality_handler.getSupportedVideoQuality().size() is 0 - this could happen if the camera driver
			// supports no camcorderprofiles? In this case, we shouldn't support video.
			Log.e(TAG, "no video qualities found");
			supports_video = false;
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after handling video quality: " + (System.currentTimeMillis() - debug_time));
		}

		if( supports_video ) {
			capture_rate_factor = Prefs.getVideoCaptureRateFactor();
			has_capture_rate_factor = Math.abs(capture_rate_factor - 1.0f) > 1.0e-5f;
			if( MyDebug.LOG ) {
				Log.d(TAG, "has_capture_rate_factor: " + has_capture_rate_factor);
				Log.d(TAG, "capture_rate_factor: " + capture_rate_factor);
			}

			// set up high speed frame rates
			// should be done after checking the requested video size is available, and after reading the requested capture rate
			video_high_speed = false;
			if( this.supports_video_high_speed ) {
				VideoProfile profile = getVideoProfile();
				if( MyDebug.LOG )
					Log.d(TAG, "check if we need high speed video for " + profile.videoFrameWidth + " x " + profile.videoFrameHeight + " at fps " + profile.videoCaptureRate);
				CameraController.Size best_video_size = video_quality_handler.findVideoSizeForFrameRate(profile.videoFrameWidth, profile.videoFrameHeight, profile.videoCaptureRate);

				if( best_video_size == null && video_quality_handler.getSupportedVideoSizesHighSpeed() != null ) {
					Log.e(TAG, "can't find match for capture rate: " + profile.videoCaptureRate + " and video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight + " at fps " + profile.videoCaptureRate);
					// try falling back to one of the supported high speed resolutions
					CameraController.Size requested_size = video_quality_handler.getMaxSupportedVideoSizeHighSpeed();
					profile.videoFrameWidth = requested_size.width;
					profile.videoFrameHeight = requested_size.height;
					// now try again
					best_video_size = CameraController.CameraFeatures.findSize(video_quality_handler.getSupportedVideoSizesHighSpeed(), requested_size, profile.videoCaptureRate, false);
					if( best_video_size != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "fall back to a supported video size for high speed fps");
						// need to write back to the application
						// so find the corresponding quality value
						video_quality_handler.setCurrentVideoQualityIndex(-1);
						for(int i=0;i<video_quality_handler.getSupportedVideoQuality().size();i++) {
							if( MyDebug.LOG )
								Log.d(TAG, "check video quality: " + video_quality_handler.getSupportedVideoQuality().get(i));
							CamcorderProfile camcorder_profile = getCamcorderProfile(video_quality_handler.getSupportedVideoQuality().get(i));
							if( camcorder_profile.videoFrameWidth == profile.videoFrameWidth && camcorder_profile.videoFrameHeight == profile.videoFrameHeight ) {
								video_quality_handler.setCurrentVideoQualityIndex(i);
								break;
							}
						}
						if( video_quality_handler.getCurrentVideoQualityIndex() != -1 ) {
							if( MyDebug.LOG )
								Log.d(TAG, "reset to video quality: " + video_quality_handler.getCurrentVideoQuality());
							Prefs.setVideoQualityPref(video_quality_handler.getCurrentVideoQuality());
						}
						else {
							if( MyDebug.LOG )
								Log.d(TAG, "but couldn't find a corresponding video quality");
							best_video_size = null;
						}
					}
				}

				if( best_video_size == null ) {
					Log.e(TAG, "fps not supported for this video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight + " at fps " + profile.videoCaptureRate);
					// we'll end up trying to record at the requested resolution and fps even though these seem incompatible;
					// the camera driver will either ignore the requested fps, or fail
				}
				else if( best_video_size.high_speed ) {
					video_high_speed = true;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "video_high_speed?: " + video_high_speed);
		}

		if( is_video && video_high_speed && supports_iso_range && is_manual_mode ) {
			if( MyDebug.LOG )
				Log.d(TAG, "manual mode not supported for video_high_speed");
			camera_controller.setManualISO(false, 0);
			is_manual_mode = false;
		}

		{
			if( MyDebug.LOG ) {
				Log.d(TAG, "set up flash");
				Log.d(TAG, "flash values: " + supported_flash_values);
			}
			current_flash_index = -1;
			if( supported_flash_values != null && supported_flash_values.size() > 1 ) {
				setupFlash();
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "flash not supported");
				supported_flash_values = null;
			}
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: time after setting up flash: " + (System.currentTimeMillis() - debug_time));
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up focus");
			current_focus_index = -1;
			if( supported_focus_values != null && supported_focus_values.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "focus values: " + supported_focus_values);

				setupFocus(true);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "focus not supported");
				supported_focus_values = null;
			}
		}

		{
			if( MyDebug.LOG )
				Log.d(TAG, "set up exposure lock");
			// exposure lock should always default to false, as doesn't make sense to save it - we can't really preserve a "lock" after the camera is reopened
			// also note that it isn't safe to lock the exposure before starting the preview
			is_auto_adjustment_locked = false;
		}
		
		if (this.using_camera_2 && this.supports_exposure_time) {
			int max_expo;
			try {
				max_expo = Integer.parseInt(sharedPreferences.getString(Prefs.PREVIEW_MAX_EXPO, "12"));
			} catch(NumberFormatException e) {
				max_expo = 12;
			}
			camera_controller.setPreviewMaxExposure(max_expo);
		}
		
		if (this.using_camera_2 && this.supports_expo_bracketing) {
			camera_controller.useIsoForExpoBracketing(sharedPreferences.getBoolean(Prefs.EXPO_BRACKETING_USE_ISO + "_" + camera_controller.getCameraId(), true));
		}
		
		{
			String ab_mode = camera_controller.getAntibanding();
			if (ab_mode == null) {
				if( MyDebug.LOG )
					Log.d(TAG, "antibanding not supported");
				this.supports_antibanding = false;
			} else {
				this.supports_antibanding = true;
				String ab_pref = Prefs.getAntibandingPref();
				if (!ab_mode.equals(ab_pref)) {
					final boolean result = camera_controller.setAntibanding(ab_pref);
					if( MyDebug.LOG )
						Log.d(TAG, "change antibanding from \"" + ab_mode + "\" to \"" + ab_pref + "\": " + (result ? "success" : "failed"));
				}
			}
			
			String nr_key = Prefs.NOISE_REDUCTION + (using_camera_2 ? "_2" : "_1") + "_" + camera_controller.getCameraId();
			if (sharedPreferences.contains(nr_key)) {
				final String current_value = camera_controller.getNoiseReductionMode();
				final String pref_value = sharedPreferences.getString(nr_key, "");
				if (current_value != null && !current_value.equals(pref_value)) {
					final boolean result = camera_controller.setNoiseReductionMode(pref_value);
					if( MyDebug.LOG )
						Log.d(TAG, "change noise reduction from \"" + current_value + "\" to \"" + pref_value + "\": " + (result ? "success" : "failed"));
				}
			}

			String e_key = Prefs.EDGE + (using_camera_2 ? "_2" : "_1") + "_" + camera_controller.getCameraId();
			if (sharedPreferences.contains(e_key)) {
				final String current_value = camera_controller.getEdgeMode();
				final String pref_value = sharedPreferences.getString(e_key, "");
				if (current_value != null && !current_value.equals(pref_value)) {
					final boolean result = camera_controller.setEdgeMode(pref_value);
					if( MyDebug.LOG )
						Log.d(TAG, "change edge mode from \"" + current_value + "\" to \"" + pref_value + "\": " + (result ? "success" : "failed"));
				}
			}

			if (using_camera_2) {
				setupSmartFilter();
				
				if (Prefs.getPhotoMode() == Prefs.PhotoMode.FastBurst || Prefs.getPhotoMode() == Prefs.PhotoMode.NoiseReduction) {
					camera_controller.setDisableBurstFilters(sharedPreferences.getBoolean(Prefs.getPhotoMode() == Prefs.PhotoMode.NoiseReduction ? Prefs.NR_DISABLE_FILTERS : Prefs.FAST_BURST_DISABLE_FILTERS, true));
				}

				String ois_key = Prefs.OPTICAL_STABILIZATION + "_" + camera_controller.getCameraId();
				if (sharedPreferences.contains(ois_key)) {
					final String current_value = camera_controller.getOpticalStabilizationMode();
					final String pref_value = sharedPreferences.getString(ois_key, "");
					if (current_value != null && !current_value.equals(pref_value)) {
						final boolean result = camera_controller.setOpticalStabilizationMode(pref_value);
						if( MyDebug.LOG )
							Log.d(TAG, "change optical stabilization mode from \"" + current_value + "\" to \"" + pref_value + "\": " + (result ? "success" : "failed"));
					}
				}

				String hp_key = Prefs.HOT_PIXEL_CORRECTION + "_" + camera_controller.getCameraId();
				if (sharedPreferences.contains(hp_key)) {
					final String current_value = camera_controller.getHotPixelCorrectionMode();
					final String pref_value = sharedPreferences.getString(hp_key, "");
					if (current_value != null && !current_value.equals(pref_value)) {
						final boolean result = camera_controller.setHotPixelCorrectionMode(pref_value);
						if( MyDebug.LOG )
							Log.d(TAG, "change hot pixel correction mode from \"" + current_value + "\" to \"" + pref_value + "\": " + (result ? "success" : "failed"));
					}
				}
			} else {
				String zsd_key = Prefs.ZERO_SHUTTER_DELAY + "_" + camera_controller.getCameraId();
				if (sharedPreferences.contains(zsd_key)) {
					final String current_value = camera_controller.getZeroShutterDelayMode();
					final String pref_value = sharedPreferences.getString(zsd_key, "");
					if (current_value != null && !current_value.equals(pref_value)) {
						final boolean result = camera_controller.setZeroShutterDelayMode(pref_value);
						if( MyDebug.LOG )
							Log.d(TAG, "change zero shutter delay mode from \"" + current_value + "\" to \"" + pref_value + "\": " + (result ? "success" : "failed"));
					}
				}
			}
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "setupCameraParameters: total time for setting up camera parameters: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	private void setupFlash() {
		if( MyDebug.LOG )
			Log.d(TAG, "setupFlash()");
		String flash_value = Prefs.getFlashPref();
		if( flash_value.length() > 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "found existing flash_value: " + flash_value);
			if( !updateFlash(flash_value, false) ) { // don't need to save, as this is the value that's already saved
				if( MyDebug.LOG )
					Log.d(TAG, "flash value no longer supported!");
				updateFlash(0, true);
			}
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "found no existing flash_value");
			// whilst devices with flash should support flash_auto, we'll also be in this codepath for front cameras with
			// no flash, as instead the available options will be flash_off, flash_frontscreen_auto, flash_frontscreen_on
			// see testTakePhotoFrontCameraScreenFlash
			if( supported_flash_values.contains("flash_auto") )
				updateFlash("flash_auto", true);
			else
				updateFlash("flash_off", true);
		}
	}
	
	public void setupSmartFilter() {
		if (camera_controller != null) {
			String sf_key = Prefs.SMART_FILTER + "_" + camera_controller.getCameraId();
			if (sharedPreferences.contains(sf_key)) {
				if (Prefs.getPhotoMode() == Prefs.PhotoMode.HDR && sharedPreferences.getBoolean(Prefs.HDR_IGNORE_SMART_FILTER, false)) {
					camera_controller.setSmartFilterISO(0);
				} else {
					int sf_value;
					try {
						sf_value = Integer.parseInt(sharedPreferences.getString(sf_key, "0"));
					}
					catch(NumberFormatException e) {
						sf_value = 0;
					}
					camera_controller.setSmartFilterISO(sf_value);
					if( MyDebug.LOG )
						Log.d(TAG, "set smart filtering iso: " + sf_value);

					if (sf_value == 0) {
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.remove(sf_key);
						editor.apply();
					}
				}
			}
		}
	}

	private void setPreviewSize() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewSize()");
		// also now sets picture size
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		if( is_preview_started ) {
			Log.e(TAG, "setPreviewSize() shouldn't be called when preview is running");
			//throw new RuntimeException(); // throw as RuntimeException, as this is a programming error
			// Bizarrely I have seen the above crash reported from Google Play devices, but inspection of the code leaves it unclear
			// why this can happen. So have disabled the exception since this evidently can happen.
			return;
		}
		if( !using_camera_2 ) {
			// don't do for Android L, else this means we get flash on startup autofocus if flash is on
			this.cancelAutoFocus();
		}
		// first set picture size (for photo mode, must be done now so we can set the picture size from this; for video, doesn't really matter when we set it)
		CameraController.Size new_size = null;
		if( this.is_video ) {
			// see comments for getOptimalVideoPictureSize()
			VideoProfile profile = getVideoProfile();
			if( MyDebug.LOG )
				Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
			if( video_high_speed ) {
				// It's unclear it matters what size we set here given that high speed is only for Camera2 API, and that
				// take photo whilst recording video isn't supported for high speed video - so for Camera2 API, setting
				// picture size should have no effect. But set to a sensible value just in case.
				new_size = new CameraController.Size(profile.videoFrameWidth, profile.videoFrameHeight);
			}
			else {
				double targetRatio = ((double) profile.videoFrameWidth) / (double) profile.videoFrameHeight;
				new_size = getOptimalVideoPictureSize(sizes, targetRatio);
			}
		}
		else {
			if( current_size_index != -1 ) {
				new_size = sizes.get(current_size_index);
			}
		}
		if( new_size != null ) {
			camera_controller.setPictureSize(new_size.width, new_size.height);
		}
		// set optimal preview size
		if( supported_preview_sizes != null && supported_preview_sizes.size() > 0 ) {
			CameraController.Size best_size = getOptimalPreviewSize(supported_preview_sizes);
			camera_controller.setPreviewSize(best_size.width, best_size.height);
			this.set_preview_size = true;
			this.preview_w = best_size.width;
			this.preview_h = best_size.height;
			this.setAspectRatio( ((double)best_size.width) / (double)best_size.height );
		}
	}

	private void initialiseVideoSizes() {
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		this.video_quality_handler.sortVideoSizes();
	}

	private void initialiseVideoQuality() {
		int cameraId = camera_controller.getCameraId();
		List<Integer> profiles = new ArrayList<>();
		List<VideoQualityHandler.Dimension2D> dimensions = new ArrayList<>();
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
			profiles.add(CamcorderProfile.QUALITY_HIGH);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P) ) {
				CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
				profiles.add(CamcorderProfile.QUALITY_2160P);
				dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
			}
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
			profiles.add(CamcorderProfile.QUALITY_1080P);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
			profiles.add(CamcorderProfile.QUALITY_720P);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
			profiles.add(CamcorderProfile.QUALITY_480P);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_CIF);
			profiles.add(CamcorderProfile.QUALITY_CIF);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QVGA);
			profiles.add(CamcorderProfile.QUALITY_QVGA);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_QCIF);
			profiles.add(CamcorderProfile.QUALITY_QCIF);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		if( CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW) ) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
			profiles.add(CamcorderProfile.QUALITY_LOW);
			dimensions.add(new VideoQualityHandler.Dimension2D(profile.videoFrameWidth, profile.videoFrameHeight));
		}
		this.video_quality_handler.initialiseVideoQualityFromProfiles(profiles, dimensions);
	}

	/** Gets a CamcorderProfile associated with the supplied quality, for non-slow motion modes. Note
	 *  that the supplied quality doesn't have to match whatever the current video mode is (or indeed,
	 *  this might be called even in slow motion mode), since we use this for things like setting up
	 *  available preferences.
	 */
	private CamcorderProfile getCamcorderProfile(String quality) {
		if( MyDebug.LOG )
			Log.d(TAG, "getCamcorderProfile(): " + quality);
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return CamcorderProfile.get(0, CamcorderProfile.QUALITY_HIGH);
		}
		int cameraId = camera_controller.getCameraId();
		CamcorderProfile camcorder_profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH); // default
		try {
			String profile_string = quality;
			int index = profile_string.indexOf('_');
			if( index != -1 ) {
				profile_string = quality.substring(0, index);
				if( MyDebug.LOG )
					Log.d(TAG, "	profile_string: " + profile_string);
			}
			int profile = Integer.parseInt(profile_string);
			camcorder_profile = CamcorderProfile.get(cameraId, profile);
			if( index != -1 && index+1 < quality.length() ) {
				String override_string = quality.substring(index+1);
				if( MyDebug.LOG )
					Log.d(TAG, "	override_string: " + override_string);
				if( override_string.charAt(0) == 'r' && override_string.length() >= 4 ) {
					index = override_string.indexOf('x');
					if( index == -1 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "override_string invalid format, can't find x");
					}
					else {
						String resolution_w_s = override_string.substring(1, index); // skip first 'r'
						String resolution_h_s = override_string.substring(index+1);
						if( MyDebug.LOG ) {
							Log.d(TAG, "resolution_w_s: " + resolution_w_s);
							Log.d(TAG, "resolution_h_s: " + resolution_h_s);
						}
						// copy to local variable first, so that if we fail to parse height, we don't set the width either
						int resolution_w = Integer.parseInt(resolution_w_s);
						int resolution_h = Integer.parseInt(resolution_h_s);
						camcorder_profile.videoFrameWidth = resolution_w;
						camcorder_profile.videoFrameHeight = resolution_h;
					}
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "unknown override_string initial code, or otherwise invalid format");
				}
			}
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse video quality: " + quality);
			e.printStackTrace();
		}
		return camcorder_profile;
	}
	
	/** Returns a profile describing the currently selected video quality. The returned VideoProfile
	 *  will usually encapsulate a CamcorderProfile (VideoProfile.getCamcorderProfile() will return
	 *  non-null), but not always (e.g., for slow motion mode).
	 */
	public VideoProfile getVideoProfile() {
		VideoProfile video_profile;

		// 4K UHD video is not yet supported by Android API (at least testing on Samsung S5 and Note 3, they do not return it via getSupportedVideoSizes(), nor via a CamcorderProfile (either QUALITY_HIGH, or anything else)
		// but it does work if we explicitly set the resolution (at least tested on an S5)
		if( camera_controller == null ) {
			video_profile = new VideoProfile();
			Log.e(TAG, "camera not opened! returning default video profile for QUALITY_HIGH");
			return video_profile;
		}
		/*if( video_high_speed ) {
			// return a video profile for a high speed frame rate - note that if we have a capture rate factor of say 0.25x,
			// the actual fps and bitrate of the resultant video would also be scaled by a factor of 0.25x
			//return new VideoProfile(MediaRecorder.AudioEncoder.AAC, MediaRecorder.OutputFormat.WEBM, 20000000,
			//		MediaRecorder.VideoEncoder.VP8, this.video_high_speed_size.height, 120,
			//		this.video_high_speed_size.width);
			return new VideoProfile(MediaRecorder.AudioEncoder.AAC, MediaRecorder.OutputFormat.MPEG_4, 4*14000000,
					MediaRecorder.VideoEncoder.H264, this.video_high_speed_size.height, 120,
					this.video_high_speed_size.width);
		}*/

		// Get user settings
		boolean record_audio = Prefs.getRecordAudioPref();
		String channels_value = Prefs.getRecordAudioChannelsPref();
		String fps_value = Prefs.getVideoFPSPref();
		String bitrate_value = Prefs.getVideoBitratePref();
		boolean force4k = Prefs.getForce4KPref();
		// Use CamcorderProfile just to get the current sizes and defaults.
		{
			CamcorderProfile cam_profile;
			int cameraId = camera_controller.getCameraId();

			// video_high_speed should only be for Camera2, where we don't support force4k option, but
			// put the check here just in case - don't want to be forcing 4K resolution if high speed
			// frame rate!
			if( force4k && !video_high_speed ) {
				if( MyDebug.LOG )
					Log.d(TAG, "force 4K UHD video");
				cam_profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
				cam_profile.videoFrameWidth = 3840;
				cam_profile.videoFrameHeight = 2160;
				cam_profile.videoBitRate = (int)(cam_profile.videoBitRate*2.8); // need a higher bitrate for the better quality - this is roughly based on the bitrate used by an S5's native camera app at 4K (47.6 Mbps, compared to 16.9 Mbps which is what's returned by the QUALITY_HIGH profile)
			}
			else if( this.video_quality_handler.getCurrentVideoQualityIndex() != -1 ) {
				cam_profile = getCamcorderProfile(this.video_quality_handler.getCurrentVideoQuality());
			}
			else {
				cam_profile = null;
			}
			video_profile = cam_profile != null ? new VideoProfile(cam_profile) : new VideoProfile();
		}

		//video_profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
		//video_profile.videoCodec = MediaRecorder.VideoEncoder.H264;

		if( !fps_value.equals("default") ) {
			try {
				int fps = Integer.parseInt(fps_value);
				if( MyDebug.LOG )
					Log.d(TAG, "fps: " + fps);
				video_profile.videoFrameRate = fps;
				video_profile.videoCaptureRate = fps;
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.d(TAG, "fps invalid format, can't parse to int: " + fps_value);
			}
		}

		if( !bitrate_value.equals("default") ) {
			try {
				int bitrate = Integer.parseInt(bitrate_value);
				if( MyDebug.LOG )
					Log.d(TAG, "bitrate: " + bitrate);
				video_profile.videoBitRate = bitrate;
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.d(TAG, "bitrate invalid format, can't parse to int: " + bitrate_value);
			}
		}
		final int min_high_speed_bitrate_c = 4*14000000;
		if( video_high_speed && video_profile.videoBitRate < min_high_speed_bitrate_c ) {
			video_profile.videoBitRate = min_high_speed_bitrate_c;
			if( MyDebug.LOG )
				Log.d(TAG, "set minimum bitrate for high speed: " + video_profile.videoBitRate);
		}

		if( has_capture_rate_factor ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set video profile frame rate for slow motion or timelapse, capture rate: " + capture_rate_factor);
			if( capture_rate_factor < 1.0 ) {
				// capture rate remains the same, and we adjust the frame rate of video
				video_profile.videoFrameRate = (int)(video_profile.videoFrameRate * capture_rate_factor + 0.5f);
				video_profile.videoBitRate = (int)(video_profile.videoBitRate * capture_rate_factor + 0.5f);
				if( MyDebug.LOG )
					Log.d(TAG, "scaled frame rate to: " + video_profile.videoFrameRate);
				if( Math.abs(capture_rate_factor - 0.5f) < 1.0e-5f ) {
					// hack - on Nokia 8 at least, capture_rate_factor of 0.5x still gives a normal speed video, but a
					// workaround is to increase the capture rate - even increasing by just 1.0e-5 works
					// unclear if this is needed in general, or is a Nokia specific bug
					video_profile.videoCaptureRate += 1.0e-3;
					if( MyDebug.LOG )
						Log.d(TAG, "fudged videoCaptureRate to: " + video_profile.videoCaptureRate);
				}
			}
			else if( capture_rate_factor > 1.0 ) {
				// resultant framerate remains the same, instead adjst the capture rate
				video_profile.videoCaptureRate = video_profile.videoCaptureRate / (double)capture_rate_factor;
				if( MyDebug.LOG )
					Log.d(TAG, "scaled capture rate to: " + video_profile.videoCaptureRate);
				if( Math.abs(capture_rate_factor - 2.0f) < 1.0e-5f ) {
					// hack - similar idea to the hack above for 2x slow motion
					// again, even decreasing by 1.0e-5 works
					// again, unclear if this is needed in general, or is a Nokia specific bug
					video_profile.videoCaptureRate -= 1.0e-3f;
					if( MyDebug.LOG )
						Log.d(TAG, "fudged videoCaptureRate to: " + video_profile.videoCaptureRate);
				}
			}
			// audio not recorded with slow motion or timelapse video
			record_audio = false;
		}

		// we repeat the Build.VERSION check to avoid Android Lint warning; also needs to be an "if" statement rather than using the
		// "?" operator, otherwise we still get the Android Lint warning
		if( using_camera_2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			video_profile.videoSource = MediaRecorder.VideoSource.SURFACE;
		}
		else {
			video_profile.videoSource = MediaRecorder.VideoSource.CAMERA;
		}

		// Done with video

		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
				&& record_audio
				&& ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
			// needed for Android 6, in case users deny storage permission, otherwise we'll crash
			// see https://developer.android.com/training/permissions/requesting.html
			// we request permission when switching to video mode - if it wasn't granted, here we just switch it off
			// we restrict check to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
			if( MyDebug.LOG )
				Log.e(TAG, "don't have RECORD_AUDIO permission");
			// don't show a toast here, otherwise we'll keep showing toasts whenever getVideoProfile() is called; we only
			// should show a toast when user starts recording video; so we indicate this via the no_audio_permission flag
			record_audio = false;
			video_profile.no_audio_permission = true;
		}

		video_profile.record_audio = record_audio;
		if( record_audio ) {
			String pref_audio_src = Prefs.getRecordAudioSourcePref();
			if( MyDebug.LOG )
				Log.d(TAG, "pref_audio_src: " + pref_audio_src);
			switch(pref_audio_src) {
				case "audio_src_mic":
					video_profile.audioSource = MediaRecorder.AudioSource.MIC;
					break;
				case "audio_src_default":
					video_profile.audioSource = MediaRecorder.AudioSource.DEFAULT;
					break;
				case "audio_src_voice_communication":
					video_profile.audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
					break;
				case "audio_src_voice_recognition":
					video_profile.audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
					break;
				case "audio_src_unprocessed":
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
						video_profile.audioSource = MediaRecorder.AudioSource.UNPROCESSED;
					}
					else {
						Log.e(TAG, "audio_src_voice_unprocessed requires Android 7");
						video_profile.audioSource = MediaRecorder.AudioSource.CAMCORDER;
					}
					break;
				case "audio_src_camcorder":
				default:
					video_profile.audioSource = MediaRecorder.AudioSource.CAMCORDER;
					break;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "audio_source: " + video_profile.audioSource);

			if( MyDebug.LOG )
				Log.d(TAG, "pref_audio_channels: " + channels_value);
			if( channels_value.equals("audio_mono") ) {
				video_profile.audioChannels = 1;
			}
			else if( channels_value.equals("audio_stereo") ) {
				video_profile.audioChannels = 2;
			}
			// else keep with the value already stored in VideoProfile (set from the CamcorderProfile)
		}

		/*
		String pref_video_output_format = applicationInterface.getRecordVideoOutputFormatPref();
		if( MyDebug.LOG )
			Log.d(TAG, "pref_video_output_format: " + pref_video_output_format);
		if( pref_video_output_format.equals("output_format_default") ) {
			// n.b., although there is MediaRecorder.OutputFormat.DEFAULT, we don't explicitly set that - rather stick with what is default in the CamcorderProfile
		}
		else if( pref_video_output_format.equals("output_format_aac_adts") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.AAC_ADTS;
		}
		else if( pref_video_output_format.equals("output_format_amr_nb") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.AMR_NB;
		}
		else if( pref_video_output_format.equals("output_format_amr_wb") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.AMR_WB;
		}
		else if( pref_video_output_format.equals("output_format_mpeg4") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.MPEG_4;
			//video_recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264 );
			//video_recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC );
		}
		else if( pref_video_output_format.equals("output_format_3gpp") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.THREE_GPP;
		}
		else if( pref_video_output_format.equals("output_format_webm") ) {
			profile.fileFormat = MediaRecorder.OutputFormat.WEBM;
			profile.videoCodec = MediaRecorder.VideoEncoder.VP8;
			profile.audioCodec = MediaRecorder.AudioEncoder.VORBIS;
		}*/

		if( MyDebug.LOG )
			Log.d(TAG, "returning video_profile: " + video_profile);
		return video_profile;
	}
	
	private static String formatFloatToString(final float f) {
		final int i=(int)f;
		if( f == i )
			return Integer.toString(i);
		return String.format(Locale.getDefault(), "%.2f", f);
	}

	private static int greatestCommonFactor(int a, int b) {
		while( b > 0 ) {
			int temp = b;
			b = a % b;
			a = temp;
		}
		return a;
	}
	
	private static String getAspectRatio(int width, int height) {
		int gcf = greatestCommonFactor(width, height);
		if( gcf > 0 ) {
			// had a Google Play crash due to gcf being 0!? Implies width must be zero
			width /= gcf;
			height /= gcf;
		}
		return width + ":" + height;
	}
	
	private static String getMPString(int width, int height) {
		float mp = (width*height)/1000000.0f;
		return formatFloatToString(mp) + "MP";
	}
	
	public static String getAspectRatioMPString(int width, int height) {
		return "(" + getAspectRatio(width, height) + ", " + getMPString(width, height) + ")";
	}
	
	public String getCamcorderProfileDescriptionShort(String quality) {
		if( camera_controller == null )
			return "";
		CamcorderProfile profile = getCamcorderProfile(quality);
		// don't display MP here, as call to Preview.getMPString() here would contribute to poor performance (in PopupView)!
		// this is meant to be a quick simple string
		return profile.videoFrameWidth + "x" + profile.videoFrameHeight;
	}

	public String getCamcorderProfileDescription(String quality) {
		if( camera_controller == null )
			return "";
		CamcorderProfile profile = getCamcorderProfile(quality);
		String highest = "";
		if( profile.quality == CamcorderProfile.QUALITY_HIGH ) {
			highest = "Highest: ";
		}
		String type = "";
		if( profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160 ) {
			type = "4K Ultra HD ";
		}
		else if( profile.videoFrameWidth == 1920 && profile.videoFrameHeight == 1080 ) {
			type = "Full HD ";
		}
		else if( profile.videoFrameWidth == 1280 && profile.videoFrameHeight == 720 ) {
			type = "HD ";
		}
		else if( profile.videoFrameWidth == 720 && profile.videoFrameHeight == 480 ) {
			type = "SD ";
		}
		else if( profile.videoFrameWidth == 640 && profile.videoFrameHeight == 480 ) {
			type = "VGA ";
		}
		else if( profile.videoFrameWidth == 352 && profile.videoFrameHeight == 288 ) {
			type = "CIF ";
		}
		else if( profile.videoFrameWidth == 320 && profile.videoFrameHeight == 240 ) {
			type = "QVGA ";
		}
		else if( profile.videoFrameWidth == 176 && profile.videoFrameHeight == 144 ) {
			type = "QCIF ";
		}
		return highest + type + profile.videoFrameWidth + "x" + profile.videoFrameHeight + " " + getAspectRatioMPString(profile.videoFrameWidth, profile.videoFrameHeight);
	}

	public double getTargetRatio() {
		return preview_targetRatio;
	}

	private double calculateTargetRatioForPreview(Point display_size) {
		double targetRatio;
		// should always use wysiwig for video mode, otherwise we get incorrect aspect ratio shown when recording video (at least on Galaxy Nexus, e.g., at 640x480)
		// also not using wysiwyg mode with video caused corruption on Samsung cameras (tested with Samsung S3, Android 4.3, front camera, infinity focus)
		if( !Prefs.getPreviewMaxSizePref() /*|| this.is_video*/ ) {
			if( this.is_video ) {
				if( MyDebug.LOG )
					Log.d(TAG, "set preview aspect ratio from video size (wysiwyg)");
				VideoProfile profile = getVideoProfile();
				if( MyDebug.LOG )
					Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
				targetRatio = ((double)profile.videoFrameWidth) / (double)profile.videoFrameHeight;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "set preview aspect ratio from photo size (wysiwyg)");
				CameraController.Size picture_size = camera_controller.getPictureSize();
				if( MyDebug.LOG )
					Log.d(TAG, "picture_size: " + picture_size.width + " x " + picture_size.height);
				targetRatio = ((double)picture_size.width) / (double)picture_size.height;
			}
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "set preview aspect ratio from display size");
			// base target ratio from display size - means preview will fill the device's display as much as possible
			// but if the preview's aspect ratio differs from the actual photo/video size, the preview will show a cropped version of what is actually taken
			targetRatio = ((double)display_size.x) / (double)display_size.y;
		}
		this.preview_targetRatio = targetRatio;
		if( MyDebug.LOG )
			Log.d(TAG, "targetRatio: " + targetRatio);
		return targetRatio;
	}

	/** Returns the size in sizes that is the closest aspect ratio match to targetRatio, but (if max_size is non-null) is not
	 *  larger than max_size (in either width or height).
	 */
	private static CameraController.Size getClosestSize(List<CameraController.Size> sizes, double targetRatio, CameraController.Size max_size) {
		if( MyDebug.LOG )
			Log.d(TAG, "getClosestSize()");
		CameraController.Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		for(CameraController.Size size : sizes) {
			double ratio = (double)size.width / size.height;
			if( max_size != null ) {
				if( size.width > max_size.width || size.height > max_size.height )
					continue;
			}
			if( Math.abs(ratio - targetRatio) < minDiff ) {
				optimalSize = size;
				minDiff = Math.abs(ratio - targetRatio);
			}
		}
		return optimalSize;
	}

	public CameraController.Size getOptimalPreviewSize(List<CameraController.Size> sizes) {
		if( MyDebug.LOG )
			Log.d(TAG, "getOptimalPreviewSize()");
		final double ASPECT_TOLERANCE = 0.05;
		if( sizes == null )
			return null;
		if( is_video && video_high_speed ) {
			VideoProfile profile = getVideoProfile();
			if( MyDebug.LOG )
				Log.d(TAG, "video size: " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
			// preview size must match video resolution for high speed, see doc for CameraDevice.createConstrainedHighSpeedCaptureSession()
			return new CameraController.Size(profile.videoFrameWidth, profile.videoFrameHeight);
		}
		CameraController.Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		Point display_size = new Point();
		Activity activity = (Activity)this.getContext();
		{
			Window window = activity.getWindow();
			Display display = activity.getWindowManager().getDefaultDisplay();
			if (
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				(
					(window.getDecorView().getSystemUiVisibility() & (View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)) != 0 ||
					(window.getAttributes().flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0
				)
			)
				display.getRealSize(display_size);
			else
				display.getSize(display_size);
				
			if (display_size.x < display_size.y)
				display_size.set(display_size.y, display_size.x);
			if( MyDebug.LOG )
				Log.d(TAG, "display_size: " + display_size.x + " x " + display_size.y);
		}
		double targetRatio = calculateTargetRatioForPreview(display_size);
		int targetHeight = Math.min(display_size.y, display_size.x);
		if( targetHeight <= 0 ) {
			targetHeight = display_size.y;
		}
		// Try to find the size which matches the aspect ratio, and is closest match to display height
		for(CameraController.Size size : sizes) {
			if( MyDebug.LOG )
				Log.d(TAG, "	supported preview size: " + size.width + ", " + size.height);
			double ratio = (double)size.width / size.height;
			if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
				continue;
			if( Math.abs(size.height - targetHeight) < minDiff ) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		if( optimalSize == null ) {
			// can't find match for aspect ratio, so find closest one
			if( MyDebug.LOG )
				Log.d(TAG, "no preview size matches the aspect ratio");
			optimalSize = getClosestSize(sizes, targetRatio, null);
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
			Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
		}
		return optimalSize;
	}

	public CameraController.Size getOptimalVideoPictureSize(List<CameraController.Size> sizes, double targetRatio) {
		if( MyDebug.LOG )
			Log.d(TAG, "getOptimalVideoPictureSize()");
		CameraController.Size max_video_size = video_quality_handler.getMaxSupportedVideoSize();
		return getOptimalVideoPictureSize(sizes, targetRatio, max_video_size);
	}

	/** Returns a picture size to set during video mode.
	 *  In theory, the picture size shouldn't matter in video mode, but the stock Android camera sets a picture size
	 *  which is the largest that matches the video's aspect ratio.
	 *  This seems necessary to work around an aspect ratio bug introduced in Android 4.4.3 (on Nexus 7 at least): http://code.google.com/p/android/issues/detail?id=70830
	 *  which results in distorted aspect ratio on preview and recorded video!
	 *  Setting the picture size in video mode is also needed for taking photos when recording video. We need to make sure we
	 *  set photo resolutions that are supported by Android when recording video. For old camera API, this doesn't matter so much
	 *  (if we request too high, it'll automatically reduce the photo resolution), but still good to match the aspect ratio. For
	 *  Camera2 API, see notes at "https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession.StateCallback, android.os.Handler)" .
	 */
	public static CameraController.Size getOptimalVideoPictureSize(List<CameraController.Size> sizes, double targetRatio, CameraController.Size max_video_size) {
		if( MyDebug.LOG )
			Log.d(TAG, "getOptimalVideoPictureSize()");
		final double ASPECT_TOLERANCE = 0.05;
		if( sizes == null )
			return null;
		if( MyDebug.LOG )
			Log.d(TAG, "max_video_size: " + max_video_size.width + ", " + max_video_size.height);
		CameraController.Size optimalSize = null;
		// Try to find largest size that matches aspect ratio.
		// But don't choose a size that's larger than the max video size (as this isn't supported for taking photos when
		// recording video for devices with LIMITED support in Camera2 mode).
		// In theory, for devices FULL Camera2 support, if the current video resolution is smaller than the max preview resolution,
		// we should be able to support larger photo resolutions, but this is left to future.
		for(CameraController.Size size : sizes) {
			if( MyDebug.LOG )
				Log.d(TAG, "	supported preview size: " + size.width + ", " + size.height);
			double ratio = (double)size.width / size.height;
			if( Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE )
				continue;
			if( size.width > max_video_size.width || size.height > max_video_size.height )
				continue;
			if( optimalSize == null || size.width > optimalSize.width ) {
				optimalSize = size;
			}
		}
		if( optimalSize == null ) {
			// can't find match for aspect ratio, so find closest one
			if( MyDebug.LOG )
				Log.d(TAG, "no picture size matches the aspect ratio");
			optimalSize = getClosestSize(sizes, targetRatio, max_video_size);
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x " + optimalSize.height);
			Log.d(TAG, "optimalSize ratio: " + ((double)optimalSize.width / optimalSize.height));
		}
		return optimalSize;
	}

	private void setAspectRatio(double ratio) {
		if( ratio <= 0.0 )
			throw new IllegalArgumentException();

		has_aspect_ratio = true;
		if( aspect_ratio != ratio ) {
			aspect_ratio = ratio;
			if( MyDebug.LOG )
				Log.d(TAG, "new aspect ratio: " + aspect_ratio);
			cameraSurface.getView().requestLayout();
			if( canvasView != null ) {
				canvasView.requestLayout();
			}
		}
	}
	
	public boolean hasAspectRatio() {
		return has_aspect_ratio;
	}

	public double getAspectRatio() {
		return aspect_ratio;
	}

	/** Returns the ROTATION_* enum of the display relative to the natural device orientation.
	 */
	public int getDisplayRotation() {
		// gets the display rotation (as a Surface.ROTATION_* constant), taking into account the getRotatePreviewPreferenceKey() setting
		Activity activity = (Activity)this.getContext();
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

		String rotate_preview = Prefs.getPreviewRotationPref();
		if( MyDebug.LOG )
			Log.d(TAG, "	rotate_preview = " + rotate_preview);
		if( rotate_preview.equals("180") ) {
			switch (rotation) {
				case Surface.ROTATION_0: rotation = Surface.ROTATION_180; break;
				case Surface.ROTATION_90: rotation = Surface.ROTATION_270; break;
				case Surface.ROTATION_180: rotation = Surface.ROTATION_0; break;
				case Surface.ROTATION_270: rotation = Surface.ROTATION_90; break;
				default:
					break;
			}
		}

		return rotation;
	}
	
	/** Returns the rotation in degrees of the display relative to the natural device orientation.
	 */
	private int getDisplayRotationDegrees() {
		if( MyDebug.LOG )
			Log.d(TAG, "getDisplayRotationDegrees");
		int rotation = getDisplayRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0: degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
			default:
				break;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "	degrees = " + degrees);
		return degrees;
	}
	
	// for the Preview - from http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	// note, if orientation is locked to landscape this is only called when setting up the activity, and will always have the same orientation
	public void setCameraDisplayOrientation() {
		if( MyDebug.LOG )
			Log.d(TAG, "setCameraDisplayOrientation()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		if( using_camera_2 ) {
			// need to configure the textureview
			configureTransform();
		}
		else {
			int degrees = getDisplayRotationDegrees();
			if( MyDebug.LOG )
				Log.d(TAG, "	degrees = " + degrees);
			// note the code to make the rotation relative to the camera sensor is done in camera_controller.setDisplayOrientation()
			camera_controller.setDisplayOrientation(degrees);
		}
	}
	
	// for taking photos - from http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
	private void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
		}*/
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;
		if( camera_controller == null ) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");*/
			return;
		}
		orientation = (orientation + 45) / 90 * 90;
		this.current_orientation = orientation % 360;
		int new_rotation;
		int camera_orientation = camera_controller.getCameraOrientation();
		if( camera_controller.isFrontFacing() ) {
			new_rotation = (camera_orientation - orientation + 360) % 360;
		}
		else {
			new_rotation = (camera_orientation + orientation) % 360;
		}
		if( new_rotation != current_rotation ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "	current_orientation is " + current_orientation);
				Log.d(TAG, "	info orientation is " + camera_orientation);
				Log.d(TAG, "	set Camera rotation from " + current_rotation + " to " + new_rotation);
			}*/
			this.current_rotation = new_rotation;
		}
	}

	private int getDeviceDefaultOrientation() {
		WindowManager windowManager = (WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE);
		Configuration config = getResources().getConfiguration();
		int rotation = windowManager.getDefaultDisplay().getRotation();
		if( ( (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
				config.orientation == Configuration.ORIENTATION_LANDSCAPE )
				|| ( (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
				config.orientation == Configuration.ORIENTATION_PORTRAIT ) ) {
			return Configuration.ORIENTATION_LANDSCAPE;
		}
		else {
			return Configuration.ORIENTATION_PORTRAIT;
		}
	}

	/* Returns the rotation (in degrees) to use for images/videos, taking the preference_lock_orientation into account.
	 */
	public int getImageVideoRotation() {
		if( MyDebug.LOG )
			Log.d(TAG, "getImageVideoRotation() from current_rotation " + current_rotation);
		String lock_orientation = Prefs.getLockOrientationPref();
		if( lock_orientation.equals("landscape") ) {
			int camera_orientation = camera_controller.getCameraOrientation();
			int device_orientation = getDeviceDefaultOrientation();
			int result;
			if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
				// should be equivalent to onOrientationChanged(270)
				if( camera_controller.isFrontFacing() ) {
					result = (camera_orientation + 90) % 360;
				}
				else {
					result = (camera_orientation + 270) % 360;
				}
			}
			else {
				// should be equivalent to onOrientationChanged(0)
				result = camera_orientation;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "getImageVideoRotation() lock to landscape, returns " + result);
			return result;
		}
		else if( lock_orientation.equals("portrait") ) {
			int camera_orientation = camera_controller.getCameraOrientation();
			int result;
			int device_orientation = getDeviceDefaultOrientation();
			if( device_orientation == Configuration.ORIENTATION_PORTRAIT ) {
				// should be equivalent to onOrientationChanged(0)
				result = camera_orientation;
			}
			else {
				// should be equivalent to onOrientationChanged(90)
				if( camera_controller.isFrontFacing() ) {
					result = (camera_orientation + 270) % 360;
				}
				else {
					result = (camera_orientation + 90) % 360;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "getImageVideoRotation() lock to portrait, returns " + result);
			return result;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "getImageVideoRotation() returns current_rotation " + current_rotation);
		return this.current_rotation;
	}

	public void draw(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "draw()");*/
		if( this.app_is_paused ) {
			/*if( MyDebug.LOG )
				Log.d(TAG, "draw(): app is paused");*/
			return;
		}

		if( this.focus_success != FOCUS_DONE ) {
			if( focus_complete_time != -1 && System.currentTimeMillis() > focus_complete_time + 1000 ) {
				focus_success = FOCUS_DONE;
				if (focus_zone_changed) focus_zone_changed = false;
			}
		}
		applicationInterface.onDrawPreview(canvas);
	}

	public void scaleZoom(float scale_factor) {
		if( MyDebug.LOG )
			Log.d(TAG, "scaleZoom() " + scale_factor);
		if( this.camera_controller != null && this.has_zoom ) {
			int zoom_factor = camera_controller.getZoom();
			float zoom_ratio = this.zoom_ratios.get(zoom_factor)/100.0f;
			zoom_ratio *= scale_factor;

			int new_zoom_factor = zoom_factor;
			if( zoom_ratio <= 1.0f ) {
				new_zoom_factor = 0;
			}
			else if( zoom_ratio >= zoom_ratios.get(max_zoom_factor)/100.0f ) {
				new_zoom_factor = max_zoom_factor;
			}
			else {
				// find the closest zoom level
				if( scale_factor > 1.0f ) {
					// zooming in
					for(int i=zoom_factor;i<zoom_ratios.size();i++) {
						if( zoom_ratios.get(i)/100.0f >= zoom_ratio ) {
							if( MyDebug.LOG )
								Log.d(TAG, "zoom int, found new zoom by comparing " + zoom_ratios.get(i)/100.0f + " >= " + zoom_ratio);
							new_zoom_factor = i;
							break;
						}
					}
				}
				else {
					// zooming out
					for(int i=zoom_factor;i>=0;i--) {
						if( zoom_ratios.get(i)/100.0f <= zoom_ratio ) {
							if( MyDebug.LOG )
								Log.d(TAG, "zoom out, found new zoom by comparing " + zoom_ratios.get(i)/100.0f + " <= " + zoom_ratio);
							new_zoom_factor = i;
							break;
						}
					}
				}
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "ScaleListener.onScale zoom_ratio is now " + zoom_ratio);
				Log.d(TAG, "	old zoom_factor " + zoom_factor + " ratio " + zoom_ratios.get(zoom_factor)/100.0f);
				Log.d(TAG, "	chosen new zoom_factor " + new_zoom_factor + " ratio " + zoom_ratios.get(new_zoom_factor)/100.0f);
			}
			// n.b., don't call zoomTo; this should be called indirectly by applicationInterface.multitouchZoom()
			applicationInterface.multitouchZoom(new_zoom_factor);
		}
	}
	
	public void zoomTo(int new_zoom_factor) {
		if( MyDebug.LOG )
			Log.d(TAG, "ZoomTo(): " + new_zoom_factor);
		if( new_zoom_factor < 0 )
			new_zoom_factor = 0;
		else if( new_zoom_factor > max_zoom_factor )
			new_zoom_factor = max_zoom_factor;
		// problem where we crashed due to calling this function with null camera should be fixed now, but check again just to be safe
		if( camera_controller != null ) {
			if( this.has_zoom ) {
				// don't cancelAutoFocus() here, otherwise we get sluggish zoom behaviour on Camera2 API
				camera_controller.setZoom(new_zoom_factor);
//				Prefs.setZoomPref(new_zoom_factor);
				
				boolean need_clear_focus = false;
				if (this.has_metering_area) need_clear_focus = true;
				else if (this.has_focus_area) {
					if (this.focus_screen_x != cameraSurface.getView().getWidth() / 2 || this.focus_screen_y != cameraSurface.getView().getHeight() / 2)
						need_clear_focus = true;
				}
				
				if (need_clear_focus) {
					clearFocusAreas();
					setCenterFocus();
				}
			}
		}
	}

	public void setFocusDistance(float new_focus_distance) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFocusDistance: " + new_focus_distance);
		if( camera_controller != null ) {
			if( new_focus_distance < 0.0f )
				new_focus_distance = 0.0f;
			else if( new_focus_distance > minimum_focus_distance )
				new_focus_distance = minimum_focus_distance;
			camera_controller.setFocusDistance(new_focus_distance);
		}
	}
	
	public void setExposure(int new_exposure) {
		if( MyDebug.LOG )
			Log.d(TAG, "setExposure(): " + new_exposure);
		if( camera_controller != null && ( min_exposure != 0 || max_exposure != 0 ) ) {
			cancelAutoFocus();
			if( new_exposure < min_exposure )
				new_exposure = min_exposure;
			else if( new_exposure > max_exposure )
				new_exposure = max_exposure;
			if( camera_controller.setExposureCompensation(new_exposure) ) {
				// now save
				Prefs.setExposureCompensationPref(new_exposure);
			}
		}
	}

	/** Set a manual white balance temperature. The white balance mode must be set to "manual" for
	 *  this to have an effect.
	 */
	public void setWhiteBalanceTemperature(int new_temperature) {
		if( MyDebug.LOG )
			Log.d(TAG, "seWhiteBalanceTemperature(): " + new_temperature);
		if( camera_controller != null ) {
			camera_controller.setWhiteBalanceTemperature(new_temperature);
		}
	}

	/** Try to parse the supplied manual ISO value
	 * @return The manual ISO value, or -1 if not recognised as a number.
	 */
	public int parseManualISOValue(String value) {
		int iso;
		try {
			if( MyDebug.LOG )
				Log.d(TAG, "setting manual iso");
			iso = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "iso: " + iso);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "iso invalid format, can't parse to int");
			iso = -1;
		}
		return iso;
	}

	public void setISO(int new_iso) {
		if( MyDebug.LOG )
			Log.d(TAG, "setISO(): " + new_iso);
		if( camera_controller != null && supports_iso_range ) {
			if( new_iso < min_iso )
				new_iso = min_iso;
			else if( new_iso > max_iso )
				new_iso = max_iso;
			camera_controller.setISO(new_iso);
		}
	}
	
	public void setExposureTime(long new_exposure_time) {
		if( MyDebug.LOG )
			Log.d(TAG, "setExposureTime(): " + new_exposure_time);
		if( camera_controller != null && supports_exposure_time ) {
			if( new_exposure_time < getMinimumExposureTime() )
				new_exposure_time = getMinimumExposureTime();
			else if( new_exposure_time > getMaximumExposureTime() )
				new_exposure_time = getMaximumExposureTime();
			camera_controller.setExposureTime(new_exposure_time);
		}
	}
	
	public String getExposureCompensationString(int exposure) {
		float exposure_ev = exposure * exposure_step;
		return  (exposure > 0 ? "+" : "") + decimal_format_2dp.format(exposure_ev) + " EV";
	}

	public String getISOString(int iso) {
		return getResources().getString(R.string.iso) + " " + iso;
	}
	
	public String getISOString(String iso) {
		switch (iso) {
			case "auto":
				return getResources().getString(R.string.iso) + ": " + getResources().getString(R.string.auto);
			case "manual":
				return getResources().getString(R.string.iso_manual);
			default:
				return getResources().getString(R.string.iso) + ": " + ((MainActivity)this.getContext()).getMainUI().fixISOString(iso);
		}
	}

	public String getExposureTimeString(long exposure_time) {
		double exposure_time_s = exposure_time/1000000000.0;
		String string;
		if( exposure_time >= 500000000 ) {
			// show exposure times of more than 0.5s directly
			string = decimal_format_1dp.format(exposure_time_s) + getResources().getString(R.string.seconds_abbreviation);
		}
		else {
			double exposure_time_r = 1.0/exposure_time_s;
			string = " 1/" + decimal_format_1dp.format(exposure_time_r) + " " + getResources().getString(R.string.seconds_abbreviation);
		}
		return string;
	}

	public String getFocusDistanceString(float focus_distance) {
		String focus_distance_s;
		if( focus_distance > 0.0f ) {
			float real_focus_distance = 1.0f / focus_distance;
			if (real_focus_distance > 0.2f ) {
				focus_distance_s = decimal_format_2dp.format(real_focus_distance) + " " + getResources().getString(R.string.metres_abbreviation);
			} else {
				focus_distance_s = decimal_format_2dp.format(real_focus_distance*100) + " " + getResources().getString(R.string.centimetres_abbreviation);
			}
		}
		else {
			focus_distance_s = getResources().getString(R.string.infinite);
		}
		
		return focus_distance_s;
	}

	/*public String getFrameDurationString(long frame_duration) {
		double frame_duration_s = frame_duration/1000000000.0;
		double frame_duration_r = 1.0/frame_duration_s;
		return getResources().getString(R.string.fps) + " " + decimal_format_1dp.format(frame_duration_r);
	}*/

	public boolean canSwitchCamera() {
		if( this.phase == PHASE_TAKING_PHOTO || isVideoRecording() ) {
			// just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return false;
		}
		int n_cameras = camera_controller_manager.getNumberOfCameras();
		if( MyDebug.LOG )
			Log.d(TAG, "found " + n_cameras + " cameras");
		if( n_cameras == 0 )
			return false;
		return true;
	}
	
	public void setCamera(int cameraId) {
		if( MyDebug.LOG )
			Log.d(TAG, "setCamera(): " + cameraId);
		if( cameraId < 0 || cameraId >= camera_controller_manager.getNumberOfCameras() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "invalid cameraId: " + cameraId);
			cameraId = 0;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "camera_open_state: " + camera_open_state);
		
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENING ) {
			if( MyDebug.LOG )
				Log.d(TAG, "already opening camera in background thread");
			return;
		}
		if( canSwitchCamera() ) {
			if( camera_controller == null || camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSED ) {
				Prefs.updatePhotoMode();
				openCamera();
			} else {
				closeCamera(true, new CloseCameraCallback() {
					@Override
					public void onClosed() {
						if( MyDebug.LOG )
							Log.d(TAG, "CloseCameraCallback.onClosed");
						Prefs.updatePhotoMode();
						openCamera();
					}
				});
			}
		}
	}
	
	public static int [] matchPreviewFpsToVideo(List<int []> fps_ranges, int video_frame_rate) {
		if( MyDebug.LOG )
			Log.d(TAG, "matchPreviewFpsToVideo()");
		int selected_min_fps = -1, selected_max_fps = -1, selected_diff = -1;
		for(int [] fps_range : fps_ranges) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "	supported fps range: " + fps_range[0] + " to " + fps_range[1]);
			}
			int min_fps = fps_range[0];
			int max_fps = fps_range[1];
			if( min_fps <= video_frame_rate && max_fps >= video_frame_rate ) {
				int diff = max_fps - min_fps;
				if( selected_diff == -1 || diff < selected_diff ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_diff = diff;
				}
			}
		}
		if( selected_min_fps != -1 ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "	chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
			}
		}
		else {
			selected_diff = -1;
			int selected_dist = -1;
			for(int [] fps_range : fps_ranges) {
				int min_fps = fps_range[0];
				int max_fps = fps_range[1];
				int diff = max_fps - min_fps;
				int dist;
				if( max_fps < video_frame_rate )
					dist = video_frame_rate - max_fps;
				else
					dist = min_fps - video_frame_rate;
				if( MyDebug.LOG ) {
					Log.d(TAG, "	supported fps range: " + min_fps + " to " + max_fps + " has dist " + dist + " and diff " + diff);
				}
				if( selected_dist == -1 || dist < selected_dist || ( dist == selected_dist && diff < selected_diff ) ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_dist = dist;
					selected_diff = diff;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "	can't find match for fps range, so choose closest: " + selected_min_fps + " to " + selected_max_fps);
		}
		return new int[]{selected_min_fps, selected_max_fps};
	}

	public static int [] chooseBestPreviewFps(List<int []> fps_ranges) {
		if( MyDebug.LOG )
			Log.d(TAG, "chooseBestPreviewFps()");

		// find value with lowest min that has max >= 30; if more than one of these, pick the one with highest max
		int selected_min_fps = -1, selected_max_fps = -1;
		for(int [] fps_range : fps_ranges) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "	supported fps range: " + fps_range[0] + " to " + fps_range[1]);
			}
			int min_fps = fps_range[0];
			int max_fps = fps_range[1];
			if( max_fps >= 30000 ) {
				if( selected_min_fps == -1 || min_fps < selected_min_fps ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
				}
				else if( min_fps == selected_min_fps && max_fps > selected_max_fps ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
				}
			}
		}

		if( selected_min_fps != -1 ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "	chosen fps range: " + selected_min_fps + " to " + selected_max_fps);
			}
		}
		else {
			// just pick the widest range; if more than one, pick the one with highest max
			int selected_diff = -1;
			for(int [] fps_range : fps_ranges) {
				int min_fps = fps_range[0];
				int max_fps = fps_range[1];
				int diff = max_fps - min_fps;
				if( selected_diff == -1 || diff > selected_diff ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_diff = diff;
				}
				else if( diff == selected_diff && max_fps > selected_max_fps ) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_diff = diff;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "	can't find fps range 30fps or better, so picked widest range: " + selected_min_fps + " to " + selected_max_fps);
		}
		return new int[]{selected_min_fps, selected_max_fps};
	}

	/* It's important to set a preview FPS using chooseBestPreviewFps() rather than just leaving it to the default, as some devices
	 * have a poor choice of default - e.g., Nexus 5 and Nexus 6 on original Camera API default to (15000, 15000), which means very dark
	 * preview and photos in low light, as well as a less smooth framerate in good light.
	 * See http://stackoverflow.com/questions/18882461/why-is-the-default-android-camera-preview-smoother-than-my-own-camera-preview .
	 */
	public void setPreviewFps() {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewFps()");
		VideoProfile profile = getVideoProfile();
		List<int []> fps_ranges = camera_controller.getSupportedPreviewFpsRange();
		if( fps_ranges == null || fps_ranges.size() == 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "fps_ranges not available");
			return;
		}
		int [] selected_fps = null;
		if( this.is_video ) {
			// For Nexus 5 and Nexus 6, we need to set the preview fps using matchPreviewFpsToVideo to avoid problem of dark preview in low light, as described above.
			// When the video recording starts, the preview automatically adjusts, but still good to avoid too-dark preview before the user starts recording.
			// However I'm wary of changing the behaviour for all devices at the moment, since some devices can be
			// very picky about what works when it comes to recording video - e.g., corruption in preview or resultant video.
			// So for now, I'm just fixing the Nexus 5/6 behaviour without changing behaviour for other devices. Later we can test on other devices, to see if we can
			// use chooseBestPreviewFps() more widely.
			// Update for v1.31: we no longer seem to need this - I no longer get a dark preview in photo or video mode if we don't set the fps range;
			// but leaving the code as it is, to be safe.
			// Update for v1.43: implementing setPreviewFpsRange() for CameraController2 caused the dark preview problem on
			// OnePlus 3T. So enable the preview_too_dark for all devices on Camera2.
			// Update for v1.43.3: had reports of problems (e.g., setting manual mode with video on camera2) since 1.43. It's unclear
			// if there is any benefit to setting the preview fps when we aren't requesting a specific fps value, so seems safest to
			// revert to the old behaviour (where CameraController2.setPreviewFpsRange() did nothing).
			String fps_value = Prefs.getVideoFPSPref();
			
			if (using_camera_2 && sharedPreferences.getBoolean(Prefs.LOCK_PREVIEW_FPS_TO_VIDEO_FPS, false) && !fps_value.equals("default")) {
				try {
					int fps = Integer.parseInt(fps_value);
					float rate = Prefs.getVideoCaptureRateFactor();
					selected_fps = new int[] {(int)(fps*1000/rate+0.5f), (int)(fps*1000/rate+0.5f)};
				}
				catch(NumberFormatException e) {}
			} else {
				boolean preview_too_dark = using_camera_2 || Build.MODEL.equals("Nexus 5") || Build.MODEL.equals("Nexus 6");
				if( MyDebug.LOG ) {
					Log.d(TAG, "preview_too_dark? " + preview_too_dark);
					Log.d(TAG, "fps_value: " + fps_value);
				}
				if( fps_value.equals("default") && using_camera_2 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "don't set preview fps for camera2 and default fps video");
				}
				else if( fps_value.equals("default") && preview_too_dark ) {
					selected_fps = chooseBestPreviewFps(fps_ranges);
				}
				else {
					selected_fps = matchPreviewFpsToVideo(fps_ranges, (int)(profile.videoCaptureRate*1000));
				}
			}
		}
		else {
			// note that setting an fps here in continuous video focus mode causes preview to not restart after taking a photo on Galaxy Nexus
			// but we need to do this, to get good light for Nexus 5 or 6
			// we could hardcode behaviour like we do for video, but this is the same way that Google Camera chooses preview fps for photos
			// or I could hardcode behaviour for Galaxy Nexus, but since it's an old device (and an obscure bug anyway - most users don't really need continuous focus in photo mode), better to live with the bug rather than complicating the code
			// Update for v1.29: this doesn't seem to happen on Galaxy Nexus with continuous picture focus mode, which is what we now use
			// Update for v1.31: we no longer seem to need this for old API - I no longer get a dark preview in photo or video mode if we don't set the fps range;
			// but leaving the code as it is, to be safe.
			// Update for v1.43.3: as noted above, setPreviewFpsRange() was implemented for CameraController2 in v1.43, but no evidence this
			// is needed for anything, so thinking about it, best to keep things as they were before for Camera2
			if( using_camera_2 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "don't set preview fps for camera2 and photo");
			}
			else {
				selected_fps = chooseBestPreviewFps(fps_ranges);
			}
		}
		if( selected_fps != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set preview fps range: " + Arrays.toString(selected_fps));
			camera_controller.setPreviewFpsRange(selected_fps[0], selected_fps[1]);
		}
		else if( using_camera_2 ) {
			camera_controller.clearPreviewFpsRange();
		}
	}
	
	public void switchVideo(boolean during_startup) {
		if( MyDebug.LOG )
			Log.d(TAG, "switchVideo()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		if( !is_video && !supports_video ) {
			if( MyDebug.LOG )
				Log.d(TAG, "video not supported");
			return;
		}
		boolean old_is_video = is_video;
		if( this.is_video ) {
			if( video_recorder != null ) {
				stopVideo(false);
			}
			this.is_video = false;
		}
		else {
			if( this.isOnTimer() || this.is_burst || this.wait_face ) {
				cancelTimer();
				this.is_video = true;
			}
			else if( this.phase == PHASE_TAKING_PHOTO ) {
				// wait until photo taken
				if( MyDebug.LOG )
					Log.d(TAG, "wait until photo taken");
			}
			else {
				this.is_video = true;
			}
		}
		
		if( is_video != old_is_video ) {
			Prefs.setVideoPref(is_video);

			if( !during_startup ) {
				if (supported_flash_values != null) setupFlash();
				if (supported_focus_values != null) setupFocus(false); // first restore the saved focus for the new photo/video mode; don't do autofocus, as it'll be cancelled when restarting preview

				String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
				if( MyDebug.LOG )
					Log.d(TAG, "focus_value is " + focus_value);
				if( (!is_video && focus_value != null && focus_value.equals("focus_mode_continuous_picture")) ||
					!sharedPreferences.getString(Prefs.ISO + "_" + camera_controller.getCameraId(), "auto").equals(sharedPreferences.getString(Prefs.ISO + "_" + camera_controller.getCameraId() + "_video", "auto")) ||
					!Prefs.getVideoFPSPref().equals("auto")) {
					if( MyDebug.LOG )
						Log.d(TAG, "restart camera due to returning to continuous picture mode from video mode");
					// workaround for bug on Nexus 6 at least where switching to video and back to photo mode causes continuous picture mode to stop
					this.reopenCamera();
				}
				else {
					if( this.is_preview_started ) {
						camera_controller.stopPreview();
						this.is_preview_started = false;
					}
					setPreviewSize();
					// always start the camera preview, even if it was previously paused (also needed to update preview fps)
					this.startCameraPreview();
					this.clearFocusAreas();
					if (Prefs.getCenterFocusPref()) {
						final Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								if( MyDebug.LOG )
									Log.d(TAG, "set center focus");
								setCenterFocus();
							}
						}, 200);
					}
				}
			}
			
			if (this.using_camera_2 && Prefs.getVideoLogProfile() != 0.0f) {
				setupLogProfile();
			}

			if( is_video ) {
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Prefs.getRecordAudioPref() ) {
					// check for audio permission now, rather than when user starts video recording
					// we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
					// only request permission if record audio preference is enabled
					if( MyDebug.LOG )
						Log.d(TAG, "check for record audio permission");
					if( ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
						if( MyDebug.LOG )
							Log.d(TAG, "record audio permission not available");
						applicationInterface.requestRecordAudioPermission();
						// we can now carry on - if the user starts recording video, we'll check then if the permission was granted
					}
				}
			}
		}
	}
	
	private boolean focusIsVideo() {
		if( camera_controller != null ) {
			return camera_controller.focusIsVideo();
		}
		return false;
	}
	
	public void setupFocus(boolean auto_focus) {
		if( MyDebug.LOG )
			Log.d(TAG, "setupFocus()");
		if (!is_video && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) {
			updateFocus("focus_mode_manual2", true, false, false);
			return;
		}
		String focus_value = Prefs.getFocusPref(is_video);
		if( focus_value.length() > 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "found existing focus_value: " + focus_value);
			if( !updateFocus(focus_value, true, false, auto_focus) ) { // don't need to save, as this is the value that's already saved
				if( MyDebug.LOG )
					Log.d(TAG, "focus value no longer supported!");
				updateFocus(0, true, true, auto_focus);
			}
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "found no existing focus_value");
			// here we set the default values for focus mode
			// note if updating default focus value for photo mode, also update MainActivityTest.setToDefault()
			updateFocus(is_video ? "focus_mode_continuous_video" : "focus_mode_auto", true, true, auto_focus);
		}
	}

	/** If in video mode, update the focus mode if necessary to be continuous video focus mode (if that mode is available).
	 *  Normally we remember the user-specified focus value. And even setting the default is done in setFocusPref().
	 *  This method is used as a workaround for a bug on Samsung Galaxy S5 with UHD, where if the user switches to another
	 *  (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the
	 *  video is corrupted.
	 * @return If the focus mode is changed, this returns the previous focus mode; else it returns null.
	 */
	public void updateFocusForVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocusForVideo()");
		if (!Prefs.getUpdateFocusForVideoPref())
			return;

		if( this.supported_focus_values != null && camera_controller != null && is_video ) {
			boolean focus_is_video = focusIsVideo();
			if( MyDebug.LOG ) {
				Log.d(TAG, "focus_is_video: " + focus_is_video + " , is_video: " + is_video);
			}
			if( focus_is_video != is_video ) {
				if( MyDebug.LOG )
					Log.d(TAG, "need to change focus mode");
				updateFocus("focus_mode_continuous_video", true, false, false); // don't save, as we're just changing focus mode temporarily for the Samsung S5 video hack
			}
		}
	}

	/** Whether the flash mode is supported in video mode. This only returns true or flash_off or
	 * flash_torch.
	 */
	public static boolean isFlashSupportedForVideo(String flash_mode) {
		return flash_mode != null && ( flash_mode.equals("flash_off") || flash_mode.equals("flash_torch") );
	}
	
	public String getErrorFeatures(VideoProfile profile) {
		boolean was_4k = false, was_bitrate = false, was_fps = false, was_slow_motion = false;
		if( profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160 && Prefs.getForce4KPref() ) {
			was_4k = true;
		}
		String bitrate_value = Prefs.getVideoBitratePref();
		if( !bitrate_value.equals("default") ) {
			was_bitrate = true;
		}
		String fps_value = Prefs.getVideoFPSPref();
		if( Prefs.getVideoCaptureRateFactor() < 1.0f-1.0e-5f ) {
			was_slow_motion = true;
		}
		if( !fps_value.equals("default") ) {
			was_fps = true;
		}
		String features = "";
		if( was_4k || was_bitrate || was_fps || was_slow_motion ) {
			if( was_4k ) {
				features = "4K UHD";
			}
			if( was_bitrate ) {
				if( features.length() == 0 )
					features = "Bitrate";
				else
					features += "/Bitrate";
			}
			if( was_fps ) {
				if( features.length() == 0 )
					features = "Frame rate";
				else
					features += "/Frame rate";
			}
			if( was_slow_motion ) {
				if( features.length() == 0 )
					features = "Slow motion";
				else
					features += "/Slow motion";
			}
		}
		return features;
	}

	public void updateFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + flash_value);
		if( this.phase == PHASE_TAKING_PHOTO && !is_video ) {
			// just to be safe - risk of cancelling the autofocus before taking a photo, or otherwise messing things up
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		updateFlash(flash_value, true);
	}

	private boolean updateFlash(String flash_value, boolean save) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + flash_value);
		if( supported_flash_values != null ) {
			int new_flash_index = supported_flash_values.indexOf(flash_value);
			if( MyDebug.LOG )
				Log.d(TAG, "new_flash_index: " + new_flash_index);
			if( new_flash_index != -1 ) {
				updateFlash(new_flash_index, save);
				return true;
			}
		}
		return false;
	}
	
	private void updateFlash(int new_flash_index, boolean save) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFlash(): " + new_flash_index);
		// updates the Flash button, and Flash camera mode
		if( supported_flash_values != null && new_flash_index != current_flash_index ) {
			boolean initial = current_flash_index==-1;
			current_flash_index = new_flash_index;
			if( MyDebug.LOG )
				Log.d(TAG, "	current_flash_index is now " + current_flash_index + " (initial " + initial + ")");

			String flash_value = supported_flash_values.get(current_flash_index);
			if( MyDebug.LOG )
				Log.d(TAG, "	flash_value: " + flash_value);
				
			if (save) {
				String [] flash_entries = getResources().getStringArray(R.array.flash_entries);
				String [] flash_values = getResources().getStringArray(R.array.flash_values);
				for(int i=0;i<flash_values.length;i++) {
					if( flash_value.equals(flash_values[i]) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "	found entry: " + i);
						if( !initial ) {
							showToast(flash_toast, getResources().getString(R.string.flash_mode)+": "+flash_entries[i]);
						}
						break;
					}
				}
			}
			this.setFlash(flash_value);
			if( save ) {
				// now save
				Prefs.setFlashPref(flash_value);
			}
		}
	}

	private void setFlash(String flash_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFlash() " + flash_value);
		set_flash_value_after_autofocus = ""; // this overrides any previously saved setting, for during the startup autofocus
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		cancelAutoFocus();
		camera_controller.setFlashValue(flash_value);
	}

	// this returns the flash value indicated by the UI, rather than from the camera parameters (may be different, e.g., in startup autofocus!)
	public String getCurrentFlashValue() {
		if( this.current_flash_index == -1 )
			return null;
		return this.supported_flash_values.get(current_flash_index);
	}
	
	// this returns the flash mode indicated by the UI, rather than from the camera parameters (may be different, e.g., in startup autofocus!)
	/*public String getCurrentFlashMode() {
		if( current_flash_index == -1 )
			return null;
		String flash_value = supported_flash_values.get(current_flash_index);
		String flash_mode = convertFlashValueToMode(flash_value);
		return flash_mode;
	}*/

	public void updateFocus(String focus_value, boolean quiet, boolean auto_focus) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + focus_value);
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe - otherwise problem that changing the focus mode will cancel the autofocus before taking a photo, so we never take a photo, but is_taking_photo remains true!
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		updateFocus(focus_value, quiet, true, auto_focus);
	}

	private boolean supportedFocusValue(String focus_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "supportedFocusValue(): " + focus_value);
		if( this.supported_focus_values != null ) {
			int new_focus_index = supported_focus_values.indexOf(focus_value);
			if( MyDebug.LOG )
				Log.d(TAG, "new_focus_index: " + new_focus_index);
			return new_focus_index != -1;
		}
		return false;
	}

	private boolean updateFocus(String focus_value, boolean quiet, boolean save, boolean auto_focus) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + focus_value);
		if( this.supported_focus_values != null ) {
			int new_focus_index = supported_focus_values.indexOf(focus_value);
			if( MyDebug.LOG )
				Log.d(TAG, "new_focus_index: " + new_focus_index);
			if( new_focus_index != -1 ) {
				updateFocus(new_focus_index, quiet, save, auto_focus);
				return true;
			}
		}
		return false;
	}

	private String findEntryForValue(String value, int entries_id, int values_id) {
		String [] entries = getResources().getStringArray(entries_id);
		String [] values = getResources().getStringArray(values_id);
		for(int i=0;i<values.length;i++) {
			if( MyDebug.LOG )
				Log.d(TAG, "	compare to value: " + values[i]);
			if( value.equals(values[i]) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "	found entry: " + i);
				return entries[i];
			}
		}
		return null;
	}
	
	public String findFocusEntryForValue(String focus_value) {
		return findEntryForValue(focus_value, R.array.focus_mode_entries, R.array.focus_mode_values);
	}
	
	private void updateFocus(int new_focus_index, boolean quiet, boolean save, boolean auto_focus) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateFocus(): " + new_focus_index + " current_focus_index: " + current_focus_index);
		// updates the Focus button, and Focus camera mode
		if( this.supported_focus_values != null && new_focus_index != current_focus_index ) {
			current_focus_index = new_focus_index;
			if( MyDebug.LOG )
				Log.d(TAG, "	current_focus_index is now " + current_focus_index);

			String focus_value = supported_focus_values.get(current_focus_index);
			if( MyDebug.LOG )
				Log.d(TAG, "	focus_value: " + focus_value);
			if( !quiet ) {
				String focus_entry = findFocusEntryForValue(focus_value);
				if( focus_entry != null ) {
					showToast(focus_toast, getResources().getString(R.string.focus_mode)+": "+focus_entry);
				}
			}
			this.setFocusValue(focus_value, auto_focus);

			if( save ) {
				// now save
				Prefs.setFocusPref(focus_value, is_video);
			}
		}
	}
	
	/** This returns the flash mode indicated by the UI, rather than from the camera parameters.
	 */
	public String getCurrentFocusValue() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCurrentFocusValue()");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return null;
		}
		if( this.supported_focus_values != null && this.current_focus_index != -1 )
			return this.supported_focus_values.get(current_focus_index);
		return null;
	}

	private void setFocusValue(String focus_value, boolean auto_focus) {
		if( MyDebug.LOG )
			Log.d(TAG, "setFocusValue() " + focus_value);
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}
		cancelAutoFocus();
		removePendingContinuousFocusReset(); // this isn't strictly needed as the reset_continuous_focus_runnable will check the ui focus mode when it runs, but good to remove it anyway
		autofocus_in_continuous_mode = false;
		camera_controller.setFocusValue(focus_value);
		setupContinuousFocusMove();
//		clearFocusAreas();
		if( auto_focus && !focus_value.equals("focus_mode_locked") && Prefs.getStartupFocusPref() ) {
			tryAutoFocus(false, false);
		}
	}
	
	private void setupContinuousFocusMove() {
		if( MyDebug.LOG )
			Log.d(TAG, "setupContinuousFocusMove()" );
		if( continuous_focus_move_is_started ) {
			continuous_focus_move_is_started = false;
			applicationInterface.onContinuousFocusMove(false);
		}
		String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
		if( MyDebug.LOG )
			Log.d(TAG, "focus_value is " + focus_value);
		if( camera_controller != null && focus_value != null && focus_value.equals("focus_mode_continuous_picture") && !this.is_video ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set continuous picture focus move callback");
			camera_controller.setContinuousFocusMoveCallback(new CameraController.ContinuousFocusMoveCallback() {
				@Override
				public void onContinuousFocusMove(boolean start) {
					if( start != continuous_focus_move_is_started ) { // filter out repeated calls with same start value
						continuous_focus_move_is_started = start;
						count_cameraContinuousFocusMoving++;
						applicationInterface.onContinuousFocusMove(start);
					}
				}
			});
		}
		else if( camera_controller != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "remove continuous picture focus move callback");
			camera_controller.setContinuousFocusMoveCallback(null);
		}
	}

	public void toggleAutoAdjustmentLock() {
		if( MyDebug.LOG )
			Log.d(TAG, "toggleAutoAdjustmentLock()");
		if( this.phase == PHASE_TAKING_PHOTO ) {
			// just to be safe
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
			return;
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		is_auto_adjustment_locked = !is_auto_adjustment_locked;
		cancelAutoFocus();
		if( is_auto_adjustment_lock_supported ) {
			resetAutoAdjustmentUnlockTimer();
			camera_controller.setAutoAdjustmentLock(is_auto_adjustment_locked);
		}
	}
	
	public void takeVideoSnapshot() {
		if( MyDebug.LOG )
			Log.d(TAG, "takeVideoSnapshot");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			this.phase = PHASE_NORMAL;
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			this.phase = PHASE_NORMAL;
			return;
		}
		takePhotoWhenFocused();
	}
	/** User has clicked the "take picture" button (or equivalent GUI operation).
	 */
	public void takePicturePressed() {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicturePressed");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			this.phase = PHASE_NORMAL;
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			this.phase = PHASE_NORMAL;
			return;
		}
		
		if (this.phase == PHASE_PREVIEW_PAUSED) {
			startCameraPreview();
			return;
		}

		if( this.isOnTimer() ) {
			showToast(take_photo_toast, this.is_burst ? R.string.cancelled_burst_mode : R.string.cancelled_timer);
			remaining_burst_photos = 0;
			burst_captured = 0;
			cancelTimer();
			return;
		}

		if( is_video && isVideoRecording() ) {
			if( !video_start_time_set || System.currentTimeMillis() - video_start_time < 500 ) {
				// if user presses to stop too quickly, we ignore
				// firstly to reduce risk of corrupt video files when stopping too quickly (see RuntimeException we have to catch in stopVideo),
				// secondly, to reduce a backlog of events which slows things down, if user presses start/stop repeatedly too quickly
				if( MyDebug.LOG )
					Log.d(TAG, "ignore pressing stop video too quickly after start");
			}
			else {
				stopVideo(false);
			}
			return;
		}
		else if ( !is_video && this.phase == PHASE_TAKING_PHOTO) {
			if( MyDebug.LOG )
				Log.d(TAG, "already taking a photo");
			if( this.is_burst ) {
				remaining_burst_photos = 0;
				burst_captured = 0;
				applicationInterface.stoppingTimer(true);
				this.is_burst = false;
				if (Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) focusBracketingStopped();
				showToast(take_photo_toast, R.string.cancelled_burst_mode);
			}
			return;
		}

		// make sure that preview running (also needed to hide trash/share icons)
		this.startCameraPreview();

		long timer_delay = 0;
		String burst_mode_value = "1";
		boolean selfie_mode = Prefs.getSelfieModePref();
		if (selfie_mode) {
			if (using_face_detection) {
				if (wait_face) {
					wait_face = false;
					applicationInterface.stoppingTimer(false);
					return;
				}

				if (Prefs.getWaitFacePref() && getFacesDetected() == null) {
					wait_face = true;
					applicationInterface.startingTimer(false);
					return;
				}
			}

			timer_delay = Prefs.getTimerPref();
			burst_mode_value = Prefs.getRepeatPref();
		}
		
		remaining_burst_photos = 0;
		burst_captured = 0;
		boolean set_recording_state = false;
		if (!is_video) {
			if (Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) {
				fb_stack = ((MainActivity)this.getContext()).getMainUI().getFBStack();
				if (fb_stack.length < 2) {
					showToast(null, R.string.focus_bracketing_range_not_defined);
					return;
				}
				burst_count = fb_stack.length;
				remaining_burst_photos = fb_stack.length-1;
				this.is_burst = true;
				set_recording_state = true;

				if( is_auto_adjustment_lock_supported ) {
					resetAutoAdjustmentUnlockTimer();
					camera_controller.setAutoAdjustmentLock(true);
				}
			} else if (selfie_mode && !burst_mode_value.equals("1")) {
				burst_count = 1;
				if( burst_mode_value.equals("unlimited") ) {
					if( MyDebug.LOG )
						Log.d(TAG, "unlimited burst");
					burst_count = -1;
					remaining_burst_photos = -1;
					this.is_burst = true;
					set_recording_state = true;
				}
				else {
					try {
						burst_count = Integer.parseInt(burst_mode_value);
						if( MyDebug.LOG )
							Log.d(TAG, "burst_count: " + burst_count);
					}
					catch(NumberFormatException e) {
						if( MyDebug.LOG )
							Log.e(TAG, "failed to parse preference_burst_mode value: " + burst_mode_value);
						e.printStackTrace();
						burst_count = 1;
					}
					remaining_burst_photos = burst_count-1;
					this.is_burst = true;
					set_recording_state = true;
				}
			}
		}

		if( timer_delay == 0 ) {
			takePicture(false, false);
		}
		else {
			takePictureOnTimer(timer_delay, !this.is_burst);
			set_recording_state = true;
		}
		if(set_recording_state){
			applicationInterface.startingTimer(this.is_burst);
		}
		if( MyDebug.LOG )
			Log.d(TAG, "takePicturePressed exit");
	}
	
	private void takePictureOnTimer(final long timer_delay, final boolean last_photo) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "takePictureOnTimer");
			Log.d(TAG, "timer_delay: " + timer_delay);
		}
		this.phase = PHASE_TIMER;
		class TakePictureTimerTask extends TimerTask {
			public void run() {
				if( beepTimerTask != null ) {
					beepTimerTask.cancel();
					beepTimerTask = null;
				}
				Activity activity = (Activity)Preview.this.getContext();
				activity.runOnUiThread(new Runnable() {
					public void run() {
						// we run on main thread to avoid problem of camera closing at the same time
						// but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
						if( camera_controller != null && takePictureTimerTask != null ){
							if( last_photo ){
								applicationInterface.stoppingTimer(is_burst, is_video);
								is_burst = false;
							}

							takePicture(false, false);
						} else {
							if( MyDebug.LOG )
								Log.d(TAG, "takePictureTimerTask: don't take picture, as already cancelled");
						}
					}
				});
			}
		}
		take_photo_time = System.currentTimeMillis() + timer_delay;
		if( MyDebug.LOG )
			Log.d(TAG, "take photo at: " + take_photo_time);
		takePictureTimer.schedule(takePictureTimerTask = new TakePictureTimerTask(), timer_delay);

		class BeepTimerTask extends TimerTask {
			long remaining_time = timer_delay;
			public void run() {
				if( remaining_time > 0 ) { // check in case this isn't cancelled by time we take the photo
					applicationInterface.timerBeep(remaining_time);
				}
				remaining_time -= 1000;
			}
		}
		if (is_video || Prefs.getPhotoMode() != Prefs.PhotoMode.FocusBracketing || fb_stack.length == remaining_burst_photos+1) {
			beepTimer.schedule(beepTimerTask = new BeepTimerTask(), 0, 1000);
			show_timer = true;
		}
	}
	
	private void flashVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "flashVideo");
		// getFlashValue() may return "" if flash not supported!
		String flash_value = camera_controller.getFlashValue();
		if( flash_value.length() == 0 )
			return;
		String flash_value_ui = getCurrentFlashValue();
		if( flash_value_ui == null )
			return;
		if( flash_value_ui.equals("flash_torch") )
			return;
		if( flash_value.equals("flash_torch") ) {
			// shouldn't happen? but set to what the UI is
			cancelAutoFocus();
			camera_controller.setFlashValue(flash_value_ui);
			return;
		}
		// turn on torch
		cancelAutoFocus();
		camera_controller.setFlashValue("flash_torch");
		try {
			Thread.sleep(100);
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
		// turn off torch
		cancelAutoFocus();
		camera_controller.setFlashValue(flash_value_ui);
	}

	private void onVideoInfo(int what, int extra) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoInfo: " + what + " extra: " + extra);
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED && video_restart_on_max_filesize ) {
			if( MyDebug.LOG )
				Log.d(TAG, "restart due to max filesize reached");
			Activity activity = (Activity)Preview.this.getContext();
			activity.runOnUiThread(new Runnable() {
				public void run() {
					// we run on main thread to avoid problem of camera closing at the same time
					// but still need to check that the camera hasn't closed
					if( camera_controller != null )
						restartVideo(true);
					else {
						if( MyDebug.LOG )
							Log.d(TAG, "don't restart video, as already cancelled");
					}
				}
			});
		}
		else if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ) {
			if( MyDebug.LOG )
				Log.d(TAG, "reached max duration - see if we need to restart?");
			Activity activity = (Activity)Preview.this.getContext();
			activity.runOnUiThread(new Runnable() {
				public void run() {
					// we run on main thread to avoid problem of camera closing at the same time
					// but still need to check that the camera hasn't closed
					if( camera_controller != null )
						restartVideo(false); // n.b., this will only restart if remaining_restart_video > 0
					else {
						if( MyDebug.LOG )
							Log.d(TAG, "don't restart video, as already cancelled");
					}
				}
			});
		}
		else if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			stopVideo(false);
		}
		applicationInterface.onVideoInfo(what, extra); // call this last, so that toasts show up properly (as we're hogging the UI thread here, and mediarecorder takes time to stop)
	}
	
	private void onVideoError(int what, int extra) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		stopVideo(false);
		applicationInterface.onVideoError(what, extra); // call this last, so that toasts show up properly (as we're hogging the UI thread here, and mediarecorder takes time to stop)
	}
	
	/** Initiate "take picture" command. In video mode this means starting video command. In photo mode this may involve first
	 * autofocusing.
	 */
	private void takePicture(boolean max_filesize_restart, boolean photo_snapshot) {
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture");
		if( !is_video || photo_snapshot )
			this.phase = PHASE_TAKING_PHOTO;
		else {
			if( phase == PHASE_TIMER )
				this.phase = PHASE_NORMAL; // in case we were previously on timer for starting the video
		}
		show_timer = false;
		synchronized( this ) {
			// synchronise for consistency (keep FindBugs happy)
			take_photo_after_autofocus = false;
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}

		boolean store_location = Prefs.getGeotaggingPref();
		if( store_location ) {
			boolean require_location = Prefs.getRequireLocationPref();
			if( require_location ) {
				if( applicationInterface.getLocation() != null ) {
					// fine, we have location
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "location data required, but not available");
					showToast(null, R.string.location_not_available);
					if( !is_video || photo_snapshot )
						this.phase = PHASE_NORMAL;
					applicationInterface.cameraInOperation(false);
					return;
				}
			}
		}

		if( is_video && !photo_snapshot ) {
			if( MyDebug.LOG )
				Log.d(TAG, "start video recording");
			startVideoRecording(max_filesize_restart);
			return;
		}

		takePhoto(false);
		if( MyDebug.LOG )
			Log.d(TAG, "takePicture exit");
	}
	
	private VideoFileInfo createVideoFile() {
		if( MyDebug.LOG )
			Log.d(TAG, "createVideoFile");
		try {
			int method = applicationInterface.createOutputVideoMethod();
			String prefix = sharedPreferences.getString(Prefs.SAVE_VIDEO_PREFIX, "VID_");
			Uri video_uri = null;
			String video_filename = null;
			ParcelFileDescriptor video_pfd_saf = null;
			if( MyDebug.LOG )
				Log.d(TAG, "method? " + method);
			if( method == ApplicationInterface.VIDEOMETHOD_FILE ) {
				/*if( true )
					throw new IOException(); // test*/
				File videoFile = applicationInterface.createOutputVideoFile(prefix);
				video_filename = videoFile.getAbsolutePath();
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + video_filename);
			}
			else {
				Uri uri;
				if( method == ApplicationInterface.VIDEOMETHOD_SAF ) {
					uri = applicationInterface.createOutputVideoSAF(prefix);
				}
				else {
					uri = applicationInterface.createOutputVideoUri();
				}
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + uri);
				video_pfd_saf = getContext().getContentResolver().openFileDescriptor(uri, "rw");
				video_uri = uri;
			}

			return new VideoFileInfo(method, video_uri, video_filename, video_pfd_saf);
		}
		catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Couldn't create media video file; check storage permissions?");
			e.printStackTrace();
		}
		return null;
	}

	/** Start video recording.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void startVideoRecording(final boolean max_filesize_restart) {
		if( MyDebug.LOG )
			Log.d(TAG, "startVideoRecording");
		focus_success = FOCUS_DONE; // clear focus rectangle (don't do for taking photos yet)
		VideoFileInfo info = createVideoFile();
		if( info == null ) {
			videoFileInfo = new VideoFileInfo();
			applicationInterface.onFailedCreateVideoFileError();
			applicationInterface.cameraInOperation(false);
		}
		else {
			videoFileInfo = info;
			final VideoProfile profile = getVideoProfile();
			if( MyDebug.LOG ) {
				Log.d(TAG, "current_video_quality: " + this.video_quality_handler.getCurrentVideoQualityIndex());
				if (this.video_quality_handler.getCurrentVideoQualityIndex() != -1)
					Log.d(TAG, "current_video_quality value: " + this.video_quality_handler.getCurrentVideoQuality());
				Log.d(TAG, "resolution " + profile.videoFrameWidth + " x " + profile.videoFrameHeight);
				Log.d(TAG, "bit rate " + profile.videoBitRate);
			}

			boolean enable_sound = Prefs.getShutterSoundPref();
			if( MyDebug.LOG )
				Log.d(TAG, "enable_sound? " + enable_sound);
			camera_controller.enableShutterSound(enable_sound); // Camera2 API can disable video sound too

			MediaRecorder local_video_recorder = new MediaRecorder();
			this.camera_controller.unlock();
			if( MyDebug.LOG )
				Log.d(TAG, "set video listeners");

			local_video_recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
				@Override
				public void onInfo(MediaRecorder mr, int what, int extra) {
					if( MyDebug.LOG )
						Log.d(TAG, "MediaRecorder info: " + what + " extra: " + extra);
					final int final_what = what;
					final int final_extra = extra;
					Activity activity = (Activity)Preview.this.getContext();
					activity.runOnUiThread(new Runnable() {
						public void run() {
							// we run on main thread to avoid problem of camera closing at the same time
							onVideoInfo(final_what, final_extra);
						}
					});
				}
			});
			local_video_recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
				public void onError(MediaRecorder mr, int what, int extra) {
					final int final_what = what;
					final int final_extra = extra;
					Activity activity = (Activity)Preview.this.getContext();
					activity.runOnUiThread(new Runnable() {
						public void run() {
							// we run on main thread to avoid problem of camera closing at the same time
							onVideoError(final_what, final_extra);
						}
					});
				}
			});

			camera_controller.initVideoRecorderPrePrepare(local_video_recorder);
			if( profile.no_audio_permission ) {
				showToast(null, R.string.permission_record_audio_not_available);
			}

			boolean store_location = Prefs.getGeotaggingPref();
			if( store_location && applicationInterface.getLocation() != null ) {
				Location location = applicationInterface.getLocation();
				if( MyDebug.LOG ) {
					Log.d(TAG, "set video location: lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy());
				}
				local_video_recorder.setLocation((float)location.getLatitude(), (float)location.getLongitude());
			}

			if( MyDebug.LOG )
	   			Log.d(TAG, "copy video profile to media recorder");

			profile.copyToMediaRecorder(local_video_recorder);

			boolean told_app_starting = false; // true if we called applicationInterface.startingVideo()
			try {
				ApplicationInterface.VideoMaxFileSize video_max_filesize = applicationInterface.getVideoMaxFileSizePref();
				long max_filesize = video_max_filesize.max_filesize;
				//max_filesize = 15*1024*1024; // test
				if( max_filesize > 0 ) {
					if( MyDebug.LOG )
						Log.d(TAG, "set max file size of: " + max_filesize);
					try {
						local_video_recorder.setMaxFileSize(max_filesize);
					}
					catch(RuntimeException e) {
						// Google Camera warns this can happen - for example, if 64-bit filesizes not supported
						if( MyDebug.LOG )
							Log.e(TAG, "failed to set max filesize of: " + max_filesize);
						e.printStackTrace();
					}
				}
				video_restart_on_max_filesize = video_max_filesize.auto_restart; // note, we set this even if max_filesize==0, as it will still apply when hitting device max filesize limit

				// handle restart timer
				long video_max_duration = Prefs.getVideoMaxDurationPref();
				if( MyDebug.LOG )
					Log.d(TAG, "user preference video_max_duration: " + video_max_duration);
				if( max_filesize_restart ) {
					if( video_max_duration > 0 ) {
						video_max_duration -= video_accumulated_time;
						// this should be greater or equal to min_safe_restart_video_time, as too short remaining time should have been caught in restartVideo()
						if( video_max_duration < min_safe_restart_video_time ) {
							if( MyDebug.LOG )
								Log.e(TAG, "trying to restart video with too short a time: " + video_max_duration);
							video_max_duration = min_safe_restart_video_time;
						}
					}
				}
				else {
					video_accumulated_time = 0;
				}
				if( MyDebug.LOG )
					Log.d(TAG, "actual video_max_duration: " + video_max_duration);
				local_video_recorder.setMaxDuration((int)video_max_duration);

				if( videoFileInfo.video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
					local_video_recorder.setOutputFile(videoFileInfo.video_filename);
				}
				else {
					local_video_recorder.setOutputFile(videoFileInfo.video_pfd_saf.getFileDescriptor());
				}
				applicationInterface.cameraInOperation(true);
				told_app_starting = true;
				applicationInterface.startingVideo();
				/*if( true ) // test
					throw new IOException();*/
				cameraSurface.setVideoRecorder(local_video_recorder);

				local_video_recorder.setOrientationHint(getImageVideoRotation());
				if( MyDebug.LOG )
					Log.d(TAG, "about to prepare video recorder");
				local_video_recorder.prepare();
				boolean want_photo_video_recording = supportsPhotoVideoRecording();
				camera_controller.initVideoRecorderPostPrepare(local_video_recorder, want_photo_video_recording);
				if( MyDebug.LOG )
					Log.d(TAG, "about to start video recorder");

				try {
					local_video_recorder.start();
					this.video_recorder = local_video_recorder;
					videoRecordingStarted(max_filesize_restart);
				}
				catch(RuntimeException e) {
					// needed for emulator at least - although MediaRecorder not meant to work with emulator, it's good to fail gracefully
					Log.e(TAG, "runtime exception starting video recorder");
					e.printStackTrace();
					this.video_recorder = local_video_recorder; // still assign, so failedToStartVideoRecorder() will release the video_recorder
					// told_app_starting must be true if we're here
					applicationInterface.stoppingVideo();
					failedToStartVideoRecorder(profile);
				}

				/*final MediaRecorder local_video_recorder_f = local_video_recorder;
				new AsyncTask<Void, Void, Boolean>() {
					private static final String TAG = "video_recorder.start";

					@Override
					protected Boolean doInBackground(Void... voids) {
						if( MyDebug.LOG )
							Log.d(TAG, "doInBackground, async task: " + this);
						try {
							local_video_recorder_f.start();
						}
						catch(RuntimeException e) {
							// needed for emulator at least - although MediaRecorder not meant to work with emulator, it's good to fail gracefully
							Log.e(TAG, "runtime exception starting video recorder");
							e.printStackTrace();
							return false;
						}
						return true;
					}

					@Override
					protected void onPostExecute(Boolean success) {
						if( MyDebug.LOG ) {
							Log.d(TAG, "onPostExecute, async task: " + this);
							Log.d(TAG, "success: " + success);
						}
						 // still assign even if success==false, so failedToStartVideoRecorder() will release the video_recorder
						Preview.this.video_recorder = local_video_recorder_f;
						if( success ) {
							videoRecordingStarted(max_filesize_restart);
						}
						else {
							// told_app_starting must be true if we're here
							applicationInterface.stoppingVideo();
							failedToStartVideoRecorder(profile);
						}
					}
				}.execute();*/
			}
			catch(IOException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to save video");
				e.printStackTrace();
				this.video_recorder = local_video_recorder;
				if( told_app_starting ) {
					applicationInterface.stoppingVideo();
				}
				applicationInterface.onFailedCreateVideoFileError();
				video_recorder.reset();
				video_recorder.release();
				video_recorder = null;
				video_recorder_is_paused = false;
				applicationInterface.cameraInOperation(false);
				this.reconnectCamera(true);
			}
			catch(CameraControllerException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "camera exception starting video recorder");
				e.printStackTrace();
				this.video_recorder = local_video_recorder; // still assign, so failedToStartVideoRecorder() will release the video_recorder
				if( told_app_starting ) {
					applicationInterface.stoppingVideo();
				}
				failedToStartVideoRecorder(profile);
			}
			catch(NoFreeStorageException e) {
				if( MyDebug.LOG )
					Log.e(TAG, "nofreestorageexception starting video recorder");
				e.printStackTrace();
				this.video_recorder = local_video_recorder;
				if( told_app_starting ) {
					applicationInterface.stoppingVideo();
				}
				video_recorder.reset();
				video_recorder.release();
				video_recorder = null;
				video_recorder_is_paused = false;
				applicationInterface.cameraInOperation(false);
				this.reconnectCamera(true);
				this.showToast(null, R.string.video_no_free_space);
			}
		}
	}

	private void videoRecordingStarted(boolean max_filesize_restart) {
		if( MyDebug.LOG )
			Log.d(TAG, "video recorder started");
		video_recorder_is_paused = false;
		if( test_video_failure ) {
			if( MyDebug.LOG )
				Log.d(TAG, "test_video_failure is true");
			throw new RuntimeException();
		}
		video_start_time = System.currentTimeMillis();
		video_start_time_set = true;
		applicationInterface.startedVideo();
		// Don't send intent for ACTION_MEDIA_SCANNER_SCAN_FILE yet - wait until finished, so we get completed file.
		// Don't do any further calls after applicationInterface.startedVideo() that might throw an error - instead video error
		// should be handled by including a call to stopVideo() (since the video_recorder has started).

		// handle restarts
		if( remaining_restart_video == 0 && !max_filesize_restart ) {
			remaining_restart_video = Prefs.getVideoRestartTimesPref();
			if( MyDebug.LOG )
				Log.d(TAG, "initialised remaining_restart_video to: " + remaining_restart_video);
		}

		if( Prefs.getVideoFlashPref() && supportsFlash() ) {
			class FlashVideoTimerTask extends TimerTask {
				public void run() {
					if( MyDebug.LOG )
						Log.e(TAG, "FlashVideoTimerTask");
					Activity activity = (Activity)Preview.this.getContext();
					activity.runOnUiThread(new Runnable() {
						public void run() {
							// we run on main thread to avoid problem of camera closing at the same time
							// but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
							if( camera_controller != null && flashVideoTimerTask != null )
								flashVideo();
							else {
								if( MyDebug.LOG )
									Log.d(TAG, "flashVideoTimerTask: don't flash video, as already cancelled");
							}
						}
					});
				}
			}
			flashVideoTimer.schedule(flashVideoTimerTask = new FlashVideoTimerTask(), 0, 1000);
		}

		if( Prefs.getVideoLowPowerCheckPref() ) {
			/* When a device shuts down due to power off, the application will receive shutdown signals, and normally the video
			 * should stop and be valid. However it can happen that the video ends up corrupted (I've had people telling me this
			 * can happen; Googling finds plenty of stories of this happening on Android devices). I think the issue is that for
			 * very large videos, a lot of time is spent processing during the MediaRecorder.stop() call - if that doesn't complete
			 * by the time the device switches off, the video may be corrupt.
			 * So we add an extra safety net - devices typically turn off abou 1%, but we stop video at 3% to be safe. The user
			 * can try recording more videos after that if the want, but this reduces the risk that really long videos are entirely
			 * lost.
			 */
			class BatteryCheckVideoTimerTask extends TimerTask {
				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "BatteryCheckVideoTimerTask");

					// only check periodically - unclear if checking is costly in any way
					// note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
					Intent batteryStatus = getContext().registerReceiver(null, battery_ifilter);
					int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
					int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
					double battery_frac = battery_level/(double)battery_scale;
					if( MyDebug.LOG )
						Log.d(TAG, "batteryCheckVideoTimerTask: battery level at: " + battery_frac);

					if( battery_frac <= 0.03 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "batteryCheckVideoTimerTask: battery at critical level, switching off video");
						Activity activity = (Activity)Preview.this.getContext();
						activity.runOnUiThread(new Runnable() {
							public void run() {
								// we run on main thread to avoid problem of camera closing at the same time
								// but still need to check that the camera hasn't closed or the task halted, since TimerTask.run() started
								if( camera_controller != null && batteryCheckVideoTimerTask != null ) {
									stopVideo(false);
									String toast = getContext().getResources().getString(R.string.video_power_critical);
									showToast(null, toast); // show the toast afterwards, as we're hogging the UI thread here, and media recorder takes time to stop
								}
								else {
									if( MyDebug.LOG )
										Log.d(TAG, "batteryCheckVideoTimerTask: don't stop video, as already cancelled");
								}
							}
						});
					}
				}
			}
			final long battery_check_interval_ms = 60 * 1000;
			// Since we only first check after battery_check_interval_ms, this means users will get some video recorded even if the battery is already too low.
			// But this is fine, as typically short videos won't be corrupted if the device shuts off, and good to allow users to try to record a bit more if they want.
			batteryCheckVideoTimer.schedule(batteryCheckVideoTimerTask = new BatteryCheckVideoTimerTask(), battery_check_interval_ms, battery_check_interval_ms);
		}
	}
	
	private void failedToStartVideoRecorder(VideoProfile profile) {
		applicationInterface.onVideoRecordStartError(profile);
		video_recorder.reset();
		video_recorder.release();
		video_recorder = null;
		video_recorder_is_paused = false;
		applicationInterface.cameraInOperation(false);
		this.reconnectCamera(true);
	}

	/** Pauses the video recording - or unpauses if already paused.
	 *  This does nothing if isVideoRecording() returns false, or not on Android 7 or higher.
	 */
	public void pauseVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "pauseVideo");
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
			Log.e(TAG, "pauseVideo called but requires Android N");
		}
		else if( this.isVideoRecording() ) {
			if( video_recorder_is_paused ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resuming...");
				video_recorder.resume();
				video_recorder_is_paused = false;
				video_start_time = System.currentTimeMillis();
//				this.showToast(pause_video_toast, R.string.video_resume);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "pausing...");
				video_recorder.pause();
				video_recorder_is_paused = true;
				long last_time = System.currentTimeMillis() - video_start_time;
				video_accumulated_time += last_time;
				if( MyDebug.LOG ) {
					Log.d(TAG, "last_time: " + last_time);
					Log.d(TAG, "video_accumulated_time is now: " + video_accumulated_time);
				}
//				this.showToast(pause_video_toast, R.string.video_pause);
			}
		}
		else {
			Log.e(TAG, "pauseVideo called but not video recording");
		}
	}

	/** Take photo. The caller should aready have set the phase to PHASE_TAKING_PHOTO.
	 */
	private void takePhoto(boolean skip_autofocus) {
		if( MyDebug.LOG )
			Log.d(TAG, "takePhoto");
		if( camera_controller == null ) {
			Log.e(TAG, "camera not opened in takePhoto!");
			return;
		}
		if (!is_video) applicationInterface.cameraInOperation(true);
		String current_ui_focus_value = getCurrentFocusValue();
		if( MyDebug.LOG )
			Log.d(TAG, "current_ui_focus_value is " + current_ui_focus_value);

		if( autofocus_in_continuous_mode ) {
			if( MyDebug.LOG )
				Log.d(TAG, "continuous mode where user touched to focus");
			synchronized(this) {
				// as below, if an autofocus is in progress, then take photo when it's completed
				if( focus_success == FOCUS_WAITING ) {
					if( MyDebug.LOG )
						Log.d(TAG, "autofocus_in_continuous_mode: take photo after current focus");
					take_photo_after_autofocus = true;
					camera_controller.setCaptureFollowAutofocusHint(true);
				}
				else {
					// when autofocus_in_continuous_mode==true, it means the user recently touched to focus in continuous focus mode, so don't do another focus
					if( MyDebug.LOG )
						Log.d(TAG, "autofocus_in_continuous_mode: no need to refocus");
					takePhotoWhenFocused();
				}
			}
		}
		else if( camera_controller.focusIsContinuous() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "call autofocus for continuous focus mode");
			// we call via autoFocus(), to avoid risk of taking photo while the continuous focus is focusing - risk of blurred photo, also sometimes get bug in such situations where we end of repeatedly focusing
			// this is the case even if skip_autofocus is true (as we still can't guarantee that continuous focusing might be occurring)
			// note: if the user touches to focus in continuous mode, we camera controller may be in auto focus mode, so we should only enter this codepath if the camera_controller is in continuous focus mode
			CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success) {
					if( MyDebug.LOG )
						Log.d(TAG, "continuous mode autofocus complete: " + success);
					focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
					focus_complete_time = System.currentTimeMillis();

					takePhotoWhenFocused();
				}
			};
			this.focus_success = FOCUS_WAITING;
			this.focus_complete_time = -1;
			camera_controller.autoFocus(autoFocusCallback, true);
			count_cameraAutoFocus++;
			this.focus_started_time = System.currentTimeMillis();
		}
		else if( skip_autofocus || this.recentlyFocused() || Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing ) {
			if( MyDebug.LOG ) {
				if( skip_autofocus ) {
					Log.d(TAG, "skip_autofocus flag set");
				}
				else {
					Log.d(TAG, "recently focused successfully, so no need to refocus");
				}
			}
			takePhotoWhenFocused();
		}
		else if( current_ui_focus_value != null && !is_auto_adjustment_locked && ( current_ui_focus_value.equals("focus_mode_auto") || current_ui_focus_value.equals("focus_mode_macro") ) ) {
			// n.b., we check focus_value rather than camera_controller.supportsAutoFocus(), as we want to discount focus_mode_locked
			synchronized(this) {
				if( focus_success == FOCUS_WAITING ) {
					// Needed to fix bug (on Nexus 6, old camera API): if flash was on, pointing at a dark scene, and we take photo when already autofocusing, the autofocus never returned so we got stuck!
					// In general, probably a good idea to not redo a focus - just use the one that's already in progress
					if( MyDebug.LOG )
						Log.d(TAG, "take photo after current focus");
					take_photo_after_autofocus = true;
					camera_controller.setCaptureFollowAutofocusHint(true);
				}
				else {
					focus_success = FOCUS_DONE; // clear focus rectangle for new refocus
					focus_zone_changed = false;
					if (this.using_face_detection && Prefs.getForceFaceFocusPref() && current_ui_focus_value.equals("focus_mode_auto")) {
						CameraController.Face [] faces = getFacesDetected();
						if (faces != null) {
							RectF face_rect = new RectF();
							face_rect.set(faces[0].rect);
							getCameraToPreviewMatrix().mapRect(face_rect);
							int x = (int)(face_rect.left+face_rect.right)/2;
							int y = (int)(face_rect.top+face_rect.bottom)/2;

							if( camera_controller.setFocusAndMeteringArea(getAreas(x, y)) ) {
								this.focus_screen_x = x;
								this.focus_screen_y = y;
								this.has_focus_area = true;
							}
						}
					}
					CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
						@Override
						public void onAutoFocus(boolean success) {
							if( MyDebug.LOG )
								Log.d(TAG, "autofocus complete: " + success);
							focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
							focus_complete_time = System.currentTimeMillis();

							ensureFlashCorrect(); // need to call this in case user takes picture before startup focus completes!
							prepareAutoFocusPhoto();
							takePhotoWhenFocused();
						}
					};
					this.focus_success = FOCUS_WAITING;
					this.focus_complete_time = -1;
					if( MyDebug.LOG )
						Log.d(TAG, "start autofocus to take picture");
					camera_controller.autoFocus(autoFocusCallback, true);
					count_cameraAutoFocus++;
					this.focus_started_time = System.currentTimeMillis();
				}
			}
		}
		else {
			takePhotoWhenFocused();
		}
	}
	
	/** Should be called when taking a photo immediately after an autofocus.
	 *  This is needed for a workaround for Camera2 bug (at least on Nexus 6) where photos sometimes come out dark when using flash
	 *  auto, when the flash fires. This happens when taking a photo in autofocus mode (including when continuous mode has
	 *  transitioned to autofocus mode due to touching to focus). Seems to happen with scenes that have bright and dark regions,
	 *  i.e., on verge of flash firing.
	 *  Seems to be fixed if we have a short delay...
	 */
	private void prepareAutoFocusPhoto() {
		if( MyDebug.LOG )
			Log.d(TAG, "prepareAutoFocusPhoto");
		if( using_camera_2 ) {
			String flash_value = camera_controller.getFlashValue();
			// getFlashValue() may return "" if flash not supported!
			if( flash_value.length() > 0 && ( flash_value.equals("flash_auto") || flash_value.equals("flash_red_eye") ) ) {
				if( MyDebug.LOG )
					Log.d(TAG, "wait for a bit...");
				try {
					Thread.sleep(100);
				}
				catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/** Take photo, assumes any autofocus has already been taken care of, and that applicationInterface.cameraInOperation(true) has
	 *  already been called.
	 *  Note that even if a caller wants to take a photo without focusing, you probably want to call takePhoto() with skip_autofocus
	 *  set to true (so that things work okay in continuous picture focus mode).
	 */
	private void takePhotoWhenFocused() {
		// should be called when auto-focused
		if( MyDebug.LOG )
			Log.d(TAG, "takePhotoWhenFocused");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}
		if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false);
			return;
		}
		
		if (current_zoom != -1)
			resetZoom();

		final String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
		if( MyDebug.LOG ) {
			Log.d(TAG, "focus_value is " + focus_value);
			Log.d(TAG, "focus_success is " + focus_success);
		}

		if( focus_value != null && (focus_value.equals("focus_mode_locked") || this.is_auto_adjustment_locked) && focus_success == FOCUS_WAITING ) {
			// make sure there isn't an autofocus in progress - can happen if in locked mode we take a photo while autofocusing - see testTakePhotoLockedFocus() (although that test doesn't always properly test the bug...)
			// we only cancel when in locked mode and if still focusing, as I had 2 bug reports for v1.16 that the photo was being taken out of focus; both reports said it worked fine in 1.15, and one confirmed that it was due to the cancelAutoFocus() line, and that it's now fixed with this fix
			// they said this happened in every focus mode, including locked - so possible that on some devices, cancelAutoFocus() actually pulls the camera out of focus, or reverts to preview focus?
			cancelAutoFocus();
		}
		removePendingContinuousFocusReset(); // to avoid switching back to continuous focus mode while taking a photo - instead we'll always make sure we switch back after taking a photo
		updateParametersFromLocation(); // do this now, not before, so we don't set location parameters during focus (sometimes get RuntimeException)

		focus_success = FOCUS_DONE; // clear focus rectangle if not already done
		focus_zone_changed = false;
		successfully_focused = false; // so next photo taken will require an autofocus
		if( MyDebug.LOG )
			Log.d(TAG, "remaining_burst_photos: " + remaining_burst_photos);
			
		if (Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing && fb_date == null) {
			fb_date = new Date();
		}

		CameraController.ShutterCallback shutterCallback = new CameraController.ShutterCallback() {
			public void onShutter() {
				if( MyDebug.LOG )
					Log.d(TAG, "onShutter");
				applicationInterface.shutterSound();
			}
		};

		CameraController.PictureCallback pictureCallback = new CameraController.PictureCallback() {
			private boolean success = false; // whether jpeg callback succeeded
			private boolean has_date = false;
			private Date current_date = null;

			public void onStarted() {
				if( MyDebug.LOG )
					Log.d(TAG, "onStarted");
				applicationInterface.onCaptureStarted();
			}

			public void onCompleted() {
				if( MyDebug.LOG )
					Log.d(TAG, "onCompleted");
				applicationInterface.onPictureCompleted();
				if( !using_camera_2 && !is_video ) {
					is_preview_started = false; // preview automatically stopped due to taking photo on original Camera API
				}
				phase = PHASE_NORMAL; // need to set this even if remaining burst photos, so we can restart the preview
				if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
					if( !is_preview_started ) {
						// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
						// (otherwise this can fail, at least on Nexus 7)
						if( MyDebug.LOG )
							Log.d(TAG, "burst mode photos remaining: onPictureTaken about to start preview: " + remaining_burst_photos);
						startCameraPreview();
						if( MyDebug.LOG )
							Log.d(TAG, "burst mode photos remaining: onPictureTaken started preview: " + remaining_burst_photos);
					}
				}
				else {
					phase = PHASE_NORMAL;
					boolean pause_preview = Prefs.getPausePreviewPref();
					if( MyDebug.LOG )
						Log.d(TAG, "pause_preview? " + pause_preview);
					if( pause_preview && success ) {
						if( is_preview_started ) {
							// need to manually stop preview on Android L Camera2
							if( camera_controller != null ) {
								camera_controller.stopPreview();
							}
							is_preview_started = false;
						}
						setPreviewPaused(true);
					}
					else {
						if( !is_preview_started ) {
							// we need to restart the preview; and we do this in the callback, as we need to restart after saving the image
							// (otherwise this can fail, at least on Nexus 7)
							startCameraPreview();
						}
						if (!is_video) applicationInterface.cameraInOperation(false);
						if( MyDebug.LOG )
							Log.d(TAG, "onPictureTaken started preview");
					}
				}
				continuousFocusReset(); // in case we took a photo after user had touched to focus (causing us to switch from continuous to autofocus mode)
				if( camera_controller != null && focus_value != null && ( focus_value.equals("focus_mode_continuous_picture") || focus_value.equals("focus_mode_continuous_video") ) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "cancelAutoFocus to restart continuous focusing");
					camera_controller.cancelAutoFocus(); // needed to restart continuous focusing
				}

				if (!is_video && (remaining_burst_photos == 0 && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing)) {
					focusBracketingStopped();
				}

				if( MyDebug.LOG )
					Log.d(TAG, "do we need to take another photo? remaining_burst_photos: " + remaining_burst_photos);
				if( remaining_burst_photos == -1 || remaining_burst_photos > 0 ) {
					if( camera_controller == null ) {
						Log.e(TAG, "remaining_burst_photos still set, but camera is closed!: " + remaining_burst_photos);
						remaining_burst_photos = 0;
						burst_captured = 0;
					}
					else {
						burst_captured++;
						if( remaining_burst_photos > 0 )
							remaining_burst_photos--;

						long timer_delay = 0;
						if (!is_video && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) {
							timer_delay = Prefs.getFBFocusTimePref();
							setFocusDistance(fb_stack[fb_stack.length-remaining_burst_photos-1]);
						} else timer_delay = Prefs.getRepeatIntervalPref();
						if( timer_delay == 0 ) {
							// we set skip_autofocus to go straight to taking a photo rather than refocusing, for speed
							// need to manually set the phase
							if( remaining_burst_photos == 0 ){
								applicationInterface.stoppingTimer(true);
								is_burst = false;
							}
							phase = PHASE_TAKING_PHOTO;
							takePhoto(true);
						}
						else {
							takePictureOnTimer(timer_delay, remaining_burst_photos == 0);
						}
					}
				}
			}

			/** Ensures we get the same date for both JPEG and RAW; and that we set the date ASAP so that it corresponds to actual
			 *  photo time.
			 */
			private void initDate() {
				if( !has_date ) {
					has_date = true;
					current_date = (!is_video && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing) ? fb_date : new Date();
					if( MyDebug.LOG )
						Log.d(TAG, "picture taken on date: " + current_date);
				}
			}
			
			public void onPictureTaken(CameraController.Photo photo) {
				if( MyDebug.LOG )
					Log.d(TAG, "onPictureTaken");
				// n.b., this is automatically run in a different thread
				initDate();
				if( current_date != null && !applicationInterface.onPictureTaken(photo, current_date) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "applicationInterface.onPictureTaken failed");
					success = false;
				}
				else {
					success = true;
				}
				if ((android.os.Build.VERSION.SDK_INT<android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !can_disable_shutter_sound) && !Prefs.getShutterSoundPref()){
					applicationInterface.restoreSound();
				}
			}

			public void onRawPictureTaken(DngCreator dngCreator, Image image) {
				if( MyDebug.LOG )
					Log.d(TAG, "onRawPictureTaken");
				initDate();
				if( current_date != null && !applicationInterface.onRawPictureTaken(dngCreator, image, current_date) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "applicationInterface.onRawPictureTaken failed");
				}
			}

			public void onBurstPictureTaken(List<CameraController.Photo> images) {
				if( MyDebug.LOG )
					Log.d(TAG, "onBurstPictureTaken");
				// n.b., this is automatically run in a different thread
				initDate();

				success = true;
				if( !applicationInterface.onBurstPictureTaken(images, current_date) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "applicationInterface.onBurstPictureTaken failed");
					success = false;
				}
			}

			public void onFrontScreenTurnOn() {
				if( MyDebug.LOG )
					Log.d(TAG, "onFrontScreenTurnOn");
				applicationInterface.turnFrontScreenFlashOn();
			}
		};
		CameraController.ErrorCallback errorCallback = new CameraController.ErrorCallback() {
			public void onError() {
				if( MyDebug.LOG )
					Log.e(TAG, "error from takePicture");
				count_cameraTakePicture--; // cancel out the increment from after the takePicture() call
				applicationInterface.onPhotoError();
				phase = PHASE_NORMAL;
				startCameraPreview();
				if (!is_video) applicationInterface.cameraInOperation(false);
			}
		};
		{
			camera_controller.setRotation(getImageVideoRotation());

			boolean enable_sound = Prefs.getShutterSoundPref();
			if( is_video && isVideoRecording() )
				enable_sound = false; // always disable shutter sound if we're taking a photo while recording video
			if( MyDebug.LOG )
				Log.d(TAG, "enable_sound? " + enable_sound);
			if (android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && can_disable_shutter_sound){
				camera_controller.enableShutterSound(enable_sound);
			} else if (!enable_sound) {
				applicationInterface.disableSound();
			}
			if( using_camera_2 ) {
				boolean use_camera2_fast_burst = Prefs.useCamera2FastBurst();
				if( MyDebug.LOG )
					Log.d(TAG, "use_camera2_fast_burst? " + use_camera2_fast_burst);
				camera_controller.setUseExpoFastBurst( use_camera2_fast_burst );
			}
			if( MyDebug.LOG )
				Log.d(TAG, "about to call takePicture");
			camera_controller.takePicture(shutterCallback, pictureCallback, errorCallback);
			count_cameraTakePicture++;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "takePhotoWhenFocused exit");
	}
	
	public void requestAutoFocus() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestAutoFocus");
		cancelAutoFocus();
		tryAutoFocus(false, true);
	}

	private void tryAutoFocus(final boolean startup, final boolean manual) {
		// manual: whether user has requested autofocus (e.g., by touching screen, or volume focus, or hardware focus button)
		// consider whether you want to call requestAutoFocus() instead (which properly cancels any in-progress auto-focus first)
		if( MyDebug.LOG ) {
			Log.d(TAG, "tryAutoFocus");
			Log.d(TAG, "startup? " + startup);
			Log.d(TAG, "manual? " + manual);
		}
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
		}
		else if( !this.has_surface ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview surface not yet available");
		}
		else if( !this.is_preview_started ) {
			if( MyDebug.LOG )
				Log.d(TAG, "preview not yet started");
		}
		else if( !(manual && this.is_video) && (this.isVideoRecording() || this.isTakingPhotoOrOnTimer()) ) {
			// if taking a video, we allow manual autofocuses
			// autofocus may cause problem if there is a video corruption problem, see testTakeVideoBitrate() on Nexus 7 at 30Mbs or 50Mbs, where the startup autofocus would cause a problem here
			if( MyDebug.LOG )
				Log.d(TAG, "currently taking a photo");
		}
		else {
			if( manual ) {
				// remove any previous request to switch back to continuous
				removePendingContinuousFocusReset();
			}
			if( manual && !is_video && camera_controller.focusIsContinuous() && supportedFocusValue("focus_mode_auto") ) {
				if( MyDebug.LOG )
					Log.d(TAG, "switch from continuous to autofocus mode for touch focus");
				camera_controller.setFocusValue("focus_mode_auto"); // switch to autofocus
				autofocus_in_continuous_mode = true;
				// we switch back to continuous via a new reset_continuous_focus_runnable in autoFocusCompleted()
			}
			// it's only worth doing autofocus when autofocus has an effect (i.e., auto or macro mode)
			// but also for continuous focus mode, triggering an autofocus is still important to fire flash when touching the screen
			if( camera_controller.supportsAutoFocus() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "try to start autofocus");
				if( !using_camera_2 ) {
					set_flash_value_after_autofocus = "";
					String old_flash_value = camera_controller.getFlashValue();
					// getFlashValue() may return "" if flash not supported!
					if( startup && old_flash_value.length() > 0 && !old_flash_value.equals("flash_off") && !old_flash_value.equals("flash_torch") ) {
						set_flash_value_after_autofocus = old_flash_value;
						camera_controller.setFlashValue("flash_off");
					}
					if( MyDebug.LOG )
						Log.d(TAG, "set_flash_value_after_autofocus is now: " + set_flash_value_after_autofocus);
				}
				CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success) {
						if( MyDebug.LOG )
							Log.d(TAG, "autofocus complete: " + success);
						autoFocusCompleted(manual, success, false);
					}
				};
	
				this.focus_success = FOCUS_WAITING;
				if( MyDebug.LOG )
					Log.d(TAG, "set focus_success to " + focus_success);
				this.focus_complete_time = -1;
				this.successfully_focused = false;
				camera_controller.autoFocus(autoFocusCallback, false);
				count_cameraAutoFocus++;
				this.focus_started_time = System.currentTimeMillis();
				if( MyDebug.LOG )
					Log.d(TAG, "autofocus started, count now: " + count_cameraAutoFocus);
			}
			else if( has_focus_area ) {
				// do this so we get the focus box, for focus modes that support focus area, but don't support autofocus
//				focus_success = FOCUS_SUCCESS;
//				focus_complete_time = System.currentTimeMillis();
				// n.b., don't set focus_started_time as that may be used for application to show autofocus animation
			}
		}
	}
	
	/** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
	 *  After the autofocus completes, we set a reset_continuous_focus_runnable to switch back to the camera_controller
	 *  back to continuous focus after a short delay.
	 *  This function removes any pending reset_continuous_focus_runnable.
	 */
	private void removePendingContinuousFocusReset() {
		if( MyDebug.LOG )
			Log.d(TAG, "removePendingContinuousFocusReset");
		if( reset_continuous_focus_runnable != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "remove pending reset_continuous_focus_runnable");
			reset_continuous_focus_handler.removeCallbacks(reset_continuous_focus_runnable);
			reset_continuous_focus_runnable = null;
		}
	}

	/** If the user touches the screen in continuous focus mode, we switch the camera_controller to autofocus mode.
	 *  This function is called to see if we should switch from autofocus mode back to continuous focus mode.
	 *  If this isn't required, calling this function does nothing.
	 */
	private void continuousFocusReset() {
		if( MyDebug.LOG )
			Log.d(TAG, "switch back to continuous focus after autofocus?");
		if( camera_controller != null && autofocus_in_continuous_mode ) {
			autofocus_in_continuous_mode = false;
			// check again
			String current_ui_focus_value = getCurrentFocusValue();
			if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
				camera_controller.cancelAutoFocus();
				camera_controller.setFocusValue(current_ui_focus_value);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "no need to switch back to continuous focus after autofocus, mode already changed");
			}
		}
	}
	
	private void cancelAutoFocus() {
		if( MyDebug.LOG )
			Log.d(TAG, "cancelAutoFocus");
		if( camera_controller != null ) {
			camera_controller.cancelAutoFocus();
			autoFocusCompleted(false, false, true);
		}
	}
	
	private void ensureFlashCorrect() {
		// ensures flash is in correct mode, in case where we had to turn flash temporarily off for startup autofocus
		if( set_flash_value_after_autofocus.length() > 0 && camera_controller != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set flash back to: " + set_flash_value_after_autofocus);
			camera_controller.setFlashValue(set_flash_value_after_autofocus);
			set_flash_value_after_autofocus = "";
		}
	}
	
	private void autoFocusCompleted(boolean manual, boolean success, boolean cancelled) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "autoFocusCompleted");
			Log.d(TAG, "	manual? " + manual);
			Log.d(TAG, "	success? " + success);
			Log.d(TAG, "	cancelled? " + cancelled);
		}
		if( cancelled ) {
			focus_success = FOCUS_DONE;
			focus_zone_changed = false;
		}
		else {
			focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
			focus_complete_time = System.currentTimeMillis();
		}
		if( manual && !cancelled && ( success || applicationInterface.isTestAlwaysFocus() ) ) {
			successfully_focused = true;
			successfully_focused_time = focus_complete_time;
		}
		if( manual && camera_controller != null && autofocus_in_continuous_mode ) {
			String current_ui_focus_value = getCurrentFocusValue();
			if( MyDebug.LOG )
				Log.d(TAG, "current_ui_focus_value: " + current_ui_focus_value);
			if( current_ui_focus_value != null && !camera_controller.getFocusValue().equals(current_ui_focus_value) && camera_controller.getFocusValue().equals("focus_mode_auto") ) {
				reset_continuous_focus_runnable = new Runnable() {
					@Override
					public void run() {
						if( MyDebug.LOG )
							Log.d(TAG, "reset_continuous_focus_runnable running...");
						reset_continuous_focus_runnable = null;
						continuousFocusReset();
					}
				};
				reset_continuous_focus_handler.postDelayed(reset_continuous_focus_runnable, 3000);
			}
		}
		ensureFlashCorrect();
		if( this.using_face_detection && !cancelled ) {
			// On some devices such as mtk6589, face detection does not resume as written in documentation so we have
			// to cancelfocus when focus is finished
			if( camera_controller != null ) {
				camera_controller.cancelAutoFocus();
			}
		}
		synchronized(this) {
			if( take_photo_after_autofocus ) {
				if( MyDebug.LOG )
					Log.d(TAG, "take_photo_after_autofocus is set");
				take_photo_after_autofocus = false;
				prepareAutoFocusPhoto();
				takePhotoWhenFocused();
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "autoFocusCompleted exit");
	}

	private void focusBracketingStopped() {
		fb_date = null;

		setFocusDistance(fb_stack[0]);

		if( is_auto_adjustment_lock_supported ) {
			resetAutoAdjustmentUnlockTimer();
			camera_controller.setAutoAdjustmentLock(is_auto_adjustment_locked);
		}
	}
	
	public void startCameraPreview() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "startCameraPreview");
			debug_time = System.currentTimeMillis();
		}
		if( camera_controller != null && !this.isTakingPhotoOrOnTimer() && !is_preview_started ) {
			if( MyDebug.LOG )
				Log.d(TAG, "starting the camera preview");
			{
				if( MyDebug.LOG )
					Log.d(TAG, "setRecordingHint: " + is_video);
				camera_controller.setRecordingHint(this.is_video);
			}
			setPreviewFps();
			try {
				camera_controller.startPreview();
				count_cameraStartPreview++;
			}
			catch(CameraControllerException e) {
				if( MyDebug.LOG )
					Log.d(TAG, "CameraControllerException trying to startPreview");
				e.printStackTrace();
				applicationInterface.onFailedStartPreview();
				return;
			}
			this.is_preview_started = true;
			if( MyDebug.LOG ) {
				Log.d(TAG, "startCameraPreview: time after starting camera preview: " + (System.currentTimeMillis() - debug_time));
			}
			if( this.using_face_detection ) {
				if( MyDebug.LOG )
					Log.d(TAG, "start face detection");
				camera_controller.startFaceDetection();
				faces_detected = null;
			}
		}
		if (this.phase == PHASE_PREVIEW_PAUSED)
			this.setPreviewPaused(false);
		this.setupContinuousFocusMove();
		if( MyDebug.LOG ) {
			Log.d(TAG, "startCameraPreview: total time for startCameraPreview: " + (System.currentTimeMillis() - debug_time));
		}
	}

	private void setPreviewPaused(boolean paused) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewPaused: " + paused);
		applicationInterface.hasPausedPreview(paused);
		if( paused ) {
			this.phase = PHASE_PREVIEW_PAUSED;
			// shouldn't call applicationInterface.cameraInOperation(true), as should already have done when we started to take a photo (or above when exiting immersive mode)
		}
		else {
			this.phase = PHASE_NORMAL;
			applicationInterface.cameraInOperation(false); // needed for when taking photo with pause preview option
		}
	}

	public void onAccelerometerSensorChanged(SensorEvent event) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onAccelerometerSensorChanged: " + event.values[0] + ", " + event.values[1] + ", " + event.values[2]);*/

		this.has_gravity = true;
		for(int i=0;i<3;i++) {
			//this.gravity[i] = event.values[i];
			this.gravity[i] = sensor_alpha * this.gravity[i] + (1.0f-sensor_alpha) * event.values[i];
		}
		calculateGeoDirection();
		
		double x = gravity[0];
		double y = gravity[1];
		double z = gravity[2];
		double mag = Math.sqrt(x*x + y*y + z*z);
		/*if( MyDebug.LOG )
			Log.d(TAG, "xyz: " + x + ", " + y + ", " + z);*/

		this.has_pitch_angle = false;
		if( mag > 1.0e-8 ) {
			this.has_pitch_angle = true;
			this.pitch_angle = Math.asin(- z / mag) * 180.0 / Math.PI;
			/*if( MyDebug.LOG )
				Log.d(TAG, "pitch: " + pitch_angle);*/

			if( !is_test && Math.abs(pitch_angle) > 50.0 ) {
				// level angle becomes unstable when device is near vertical
				// note that if is_test, we always set the level angle - since the device typically lies face down when running tests...
				this.has_level_angle = false;
			}
			else {
				this.has_level_angle = true;
				this.natural_level_angle = Math.atan2(-x, y) * 180.0 / Math.PI;
				if( this.natural_level_angle < -0.0 ) {
					this.natural_level_angle += 360.0;
				}

				updateLevelAngles();
			}
		}
		else {
			Log.e(TAG, "accel sensor has zero mag: " + mag);
			this.has_level_angle = false;
		}

	}

	/** This method should be called when the natural level angle, or the calibration angle, has been updated, to update the other level angle variables.
	 *
	 */
	public void updateLevelAngles() {
		if( has_level_angle ) {
			this.level_angle = this.natural_level_angle;
			double calibrated_level_angle = Prefs.getCalibratedLevelAngle();
			this.level_angle -= calibrated_level_angle;
			this.orig_level_angle = this.level_angle;
			this.level_angle -= (float) this.current_orientation;
			if( this.level_angle < -180.0 ) {
				this.level_angle += 360.0;
			}
			else if( this.level_angle > 180.0 ) {
				this.level_angle -= 360.0;
			}
			/*if( MyDebug.LOG )
				Log.d(TAG, "level_angle is now: " + level_angle);*/
		}
	}
	
	public boolean hasLevelAngle() {
		return this.has_level_angle;
	}

	public double getLevelAngleUncalibrated() {
		return this.natural_level_angle - this.current_orientation;
	}

	public double getLevelAngle() {
		return this.level_angle;
	}
	
	public double getOrigLevelAngle() {
		return this.orig_level_angle;
	}

	public boolean hasPitchAngle() {
		return this.has_pitch_angle;
	}

	public double getPitchAngle() {
		return this.pitch_angle;
	}

	public void onMagneticSensorChanged(SensorEvent event) {
		this.has_geomagnetic = true;
		for(int i=0;i<3;i++) {
			//this.geomagnetic[i] = event.values[i];
			this.geomagnetic[i] = sensor_alpha * this.geomagnetic[i] + (1.0f-sensor_alpha) * event.values[i];
		}
		calculateGeoDirection();
	}
	
	private void calculateGeoDirection() {
		if( !this.has_gravity || !this.has_geomagnetic ) {
			return;
		}
		if( !SensorManager.getRotationMatrix(this.deviceRotation, this.deviceInclination, this.gravity, this.geomagnetic) ) {
			return;
		}
		SensorManager.remapCoordinateSystem(this.deviceRotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, this.cameraRotation);
		boolean has_old_geo_direction = has_geo_direction;
		this.has_geo_direction = true;
		//SensorManager.getOrientation(cameraRotation, geo_direction);
		SensorManager.getOrientation(cameraRotation, new_geo_direction);
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "###");
			Log.d(TAG, "old geo_direction: " + (geo_direction[0]*180/Math.PI) + ", " + (geo_direction[1]*180/Math.PI) + ", " + (geo_direction[2]*180/Math.PI));
		}*/
		for(int i=0;i<3;i++) {
			float old_compass = (float)Math.toDegrees(geo_direction[i]);
			float new_compass = (float)Math.toDegrees(new_geo_direction[i]);
			if( has_old_geo_direction ) {
				float smoothFactorCompass = 0.1f;
				float smoothThresholdCompass = 10.0f;
				old_compass = lowPassFilter(old_compass, new_compass, smoothFactorCompass, smoothThresholdCompass);
			}
			else {
				old_compass = new_compass;
			}
			geo_direction[i] = (float)Math.toRadians(old_compass);
		}
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "new_geo_direction: " + (new_geo_direction[0]*180/Math.PI) + ", " + (new_geo_direction[1]*180/Math.PI) + ", " + (new_geo_direction[2]*180/Math.PI));
			Log.d(TAG, "geo_direction: " + (geo_direction[0]*180/Math.PI) + ", " + (geo_direction[1]*180/Math.PI) + ", " + (geo_direction[2]*180/Math.PI));
		}*/
	}

	/** Low pass filter, for angles.
	 * @param old_value Old value in degrees.
	 * @param new_value New value in degrees.
	 */
	private float lowPassFilter(float old_value, float new_value, float smoothFactorCompass, float smoothThresholdCompass) {
		// see http://stackoverflow.com/questions/4699417/android-compass-orientation-on-unreliable-low-pass-filter
		// https://www.built.io/blog/applying-low-pass-filter-to-android-sensor-s-readings
		// http://stackoverflow.com/questions/27846604/how-to-get-smooth-orientation-data-in-android
		float diff = Math.abs(new_value - old_value);
		/*if( MyDebug.LOG )
			Log.d(TAG, "diff: " + diff);*/
		if( diff < 180 ) {
			if( diff > smoothThresholdCompass ) {
				/*if( MyDebug.LOG )
					Log.d(TAG, "jump to new compass");*/
				old_value = new_value;
			}
			else {
				old_value = old_value + smoothFactorCompass * (new_value - old_value);
			}
		}
		else {
			if( 360.0 - diff > smoothThresholdCompass ) {
				/*if( MyDebug.LOG )
					Log.d(TAG, "jump to new compass");*/
				old_value = new_value;
			}
			else {
				if( old_value > new_value ) {
					old_value = (old_value + smoothFactorCompass * ((360 + new_value - old_value) % 360) + 360) % 360;
				}
				else {
					old_value = (old_value - smoothFactorCompass * ((360 - new_value + old_value) % 360) + 360) % 360;
				}
			}
		}
		return old_value;
	}
	
	public boolean hasGeoDirection() {
		return has_geo_direction;
	}
	
	public double getGeoDirection() {
		return geo_direction[0];
	}

	public boolean supportsFaceDetection() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsFaceDetection");
		return supports_face_detection;
	}
	
	public boolean supportsVideoStabilization() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsVideoStabilization");
		return supports_video_stabilization;
	}

	public boolean supportsPhotoVideoRecording() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsPhotoVideoRecording");
		return supports_photo_video_recording && !video_high_speed;
	}

	/** Returns true iff we're in video mode, and a high speed fps video mode is selected.
	 */
	public boolean isVideoHighSpeed() {
		if( MyDebug.LOG )
			Log.d(TAG, "isVideoHighSpeed");
		return is_video && video_high_speed;
	}
	
	public boolean canDisableShutterSound() {
		if( MyDebug.LOG )
			Log.d(TAG, "canDisableShutterSound");
		return can_disable_shutter_sound;
	}

	public int getTonemapMaxCurvePoints() {
		if( MyDebug.LOG )
			Log.d(TAG, "getTonemapMaxCurvePoints");
		return tonemap_max_curve_points;
	}

	public boolean supportsTonemapCurve() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsTonemapCurve");
		return supports_tonemap_curve;
	}

	public List<String> getSupportedColorEffects() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedColorEffects");
		return this.color_effects;
	}

	public List<String> getSupportedSceneModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedSceneModes");
		return this.scene_modes;
	}

	public List<String> getSupportedWhiteBalances() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedWhiteBalances");
		return this.white_balances;
	}
	
	public String getISOKey() {
		if( MyDebug.LOG )
			Log.d(TAG, "getISOKey");
		return camera_controller == null ? "" : camera_controller.getISOKey();
	}

	/** Whether manual white balance temperatures can be specified via setWhiteBalanceTemperature().
	 */
	public boolean supportsWhiteBalanceTemperature() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsWhiteBalanceTemperature");
		return this.supports_white_balance_temperature;
	}

	/** Minimum allowed white balance temperature.
	 */
	public int getMinimumWhiteBalanceTemperature() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMinimumWhiteBalanceTemperature");
		return this.min_temperature;
	}

	/** Maximum allowed white balance temperature.
	 */
	public int getMaximumWhiteBalanceTemperature() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMaximumWhiteBalanceTemperature");
		return this.max_temperature;
	}

	public boolean supportsISO() {
		return this.supports_iso_range || this.isos != null;
	}
	/** Returns whether a range of manual ISO values can be set. If this returns true, use
	 *  getMinimumISO() and getMaximumISO() to return the valid range of values. If this returns
	 *  false, getSupportedISOs() to find allowed ISO values.
	 */
	public boolean supportsISORange() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsISORange");
		return this.supports_iso_range;
	}

	/** If supportsISORange() returns false, use this method to return a list of supported ISO values:
	 *	- If this is null, then manual ISO isn't supported.
	 *	- If non-null, this will include "auto" to indicate auto-ISO, and one or more numerical ISO
	 *	  values.
	 *  If supportsISORange() returns true, then this method should not be used (and it will return
	 *  null). Instead use getMinimumISO() and getMaximumISO().
	 */
	public List<String> getSupportedISOs() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedISOs");
		return this.isos;
	}
	
	public String getNoiseReductionMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "getNoiseReductionMode");
		if (camera_controller == null) return null;
		return camera_controller.getNoiseReductionMode();
	}

	public List<String> getSupportedNoiseReductionModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedNoiseReductionModes");
		if (camera_controller == null) return new ArrayList<>();
		return camera_controller.getAvailableNoiseReductionModes();
	}

	public String getEdgeMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "getEdgeMode");
		if (camera_controller == null) return null;
		return camera_controller.getEdgeMode();
	}

	public List<String> getSupportedEdgeModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedEdgeModes");
		if (camera_controller == null) return new ArrayList<>();
		return camera_controller.getAvailableEdgeModes();
	}

	public String getOpticalStabilizationMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "getOpticalStabilizationMode");
		if (camera_controller == null) return null;
		return camera_controller.getOpticalStabilizationMode();
	}

	public List<String> getSupportedOpticalStabilizationModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedOpticalStabilizationModes");
		if (camera_controller == null) return new ArrayList<>();
		return camera_controller.getAvailableOpticalStabilizationModes();
	}
	
	public String getHotPixelCorrectionMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "getHotPixelCorrectionMode");
		if (camera_controller == null) return null;
		return camera_controller.getHotPixelCorrectionMode();
	}

	public List<String> getSupportedHotPixelCorrectionModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedEdgeModes");
		if (camera_controller == null) return new ArrayList<>();
		return camera_controller.getAvailableHotPixelCorrectionModes();
	}

	public String getZeroShutterDelayMode() {
		if( MyDebug.LOG )
			Log.d(TAG, "getZeroShutterDelayMode");
		if (camera_controller == null) return null;
		return camera_controller.getZeroShutterDelayMode();
	}

	public List<String> getSupportedZeroShutterDelayModes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedZeroShutterDelayModes");
		if (camera_controller == null) return new ArrayList<>();
		return camera_controller.getAvailableZeroShutterDelayModes();
	}

	/** Returns minimum ISO value. Only relevant if supportsISORange() returns true.
	 */
	public int getMinimumISO() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMinimumISO");
		return this.min_iso;
	}

	/** Returns maximum ISO value. Only relevant if supportsISORange() returns true.
	 */
	public int getMaximumISO() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMaximumISO");
		return this.max_iso;
	}
	
	public float getMinimumFocusDistance() {
		return this.minimum_focus_distance;
	}
	
	public boolean supportsExposureTime() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsExposureTime");
		return this.supports_exposure_time;
	}
	
	public long getMinimumExposureTime() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMinimumExposureTime");
		return this.min_exposure_time;
	}
	
	public long getMaximumExposureTime() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMaximumExposureTime");
		long max = max_exposure_time;
		if( Prefs.isVideoPref() ) {
			max = Math.min(max_exposure_time, 1000000000L/2);
		}
		return max;
	}
	
	public boolean supportsExposures() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsExposures");
		return this.exposures != null;
	}
	
	public int getMinimumExposure() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMinimumExposure");
		return this.min_exposure;
	}
	
	public int getMaximumExposure() {
		if( MyDebug.LOG )
			Log.d(TAG, "getMaximumExposure");
		return this.max_exposure;
	}
	
	public int getCurrentExposure() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCurrentExposure");
		if( camera_controller == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return 0;
		}
		return camera_controller.getExposureCompensation();
	}
	
	/*List<String> getSupportedExposures() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedExposures");
		return this.exposures;
	}*/

	public boolean supportsExpoBracketing() {
		/*if( MyDebug.LOG )
			Log.d(TAG, "supportsExpoBracketing");*/
		return this.supports_expo_bracketing;
	}
	
	public int maxExpoBracketingNImages() {
		if( MyDebug.LOG )
			Log.d(TAG, "maxExpoBracketingNImages");
		return this.max_expo_bracketing_n_images;
	}

	public boolean supportsRaw() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsRaw");
		return this.supports_raw;
	}

	public boolean supportsAntibanding() {
		if( MyDebug.LOG )
			Log.d(TAG, "supportsAntibanding");
		return this.supports_antibanding;
	}

	/** Returns the horizontal angle of view in degrees (when unzoomed).
	 */
	public float getViewAngleX() {
		return this.view_angle_x;
	}

	/** Returns the vertical angle of view in degrees (when unzoomed).
	 */
	public float getViewAngleY() {
		return this.view_angle_y;
	}

	public List<CameraController.Size> getSupportedPreviewSizes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedPreviewSizes");
		return this.supported_preview_sizes;
	}
	
	public CameraController.Size getCurrentPreviewSize() {
		return new CameraController.Size(preview_w, preview_h);
	}

	public List<CameraController.Size> getSupportedPictureSizes() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedPictureSizes");
		return this.sizes;
	}
	
	public int getCurrentPictureSizeIndex() {
		if( MyDebug.LOG )
			Log.d(TAG, "getCurrentPictureSizeIndex");
		return this.current_size_index;
	}
	
	public CameraController.Size getCurrentPictureSize() {
		if( current_size_index == -1 || sizes == null )
			return null;
		return sizes.get(current_size_index);
	}

	public VideoQualityHandler getVideoQualityHander() {
		return this.video_quality_handler;
	}

	/** Returns the supported video "qualities", but unlike
	 *  getVideoQualityHander().getSupportedVideoQuality(), allows filtering to the supplied
	 *  fps_value.
	 * @param fps_value If not "default", the returned video qualities will be filtered to those that supported the requested
	 *				  frame rate.
	 */
	public List<String> getSupportedVideoQuality(String fps_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "getSupportedVideoQuality: " + fps_value);
		if( !fps_value.equals("default") && supports_video_high_speed ) {
			try {
				int fps = Integer.parseInt(fps_value);
				if( MyDebug.LOG )
					Log.d(TAG, "fps: " + fps);
				List<String> filtered_video_quality = new ArrayList<>();
				for(String quality : video_quality_handler.getSupportedVideoQuality()) {
					if( MyDebug.LOG )
						Log.d(TAG, "quality: " + quality);
					CamcorderProfile profile = getCamcorderProfile(quality);
					if( MyDebug.LOG ) {
						Log.d(TAG, "	width: " + profile.videoFrameWidth);
						Log.d(TAG, "	height: " + profile.videoFrameHeight);
					}
					CameraController.Size best_video_size = video_quality_handler.findVideoSizeForFrameRate(profile.videoFrameWidth, profile.videoFrameHeight, fps);
					if( best_video_size != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "	requested frame rate is supported");
						filtered_video_quality.add(quality);
					}
					else {
						if( MyDebug.LOG )
							Log.d(TAG, "	requested frame rate is NOT supported");
					}
			   }
			   return filtered_video_quality;
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.d(TAG, "fps invalid format, can't parse to int: " + fps_value);
			}
		}
		return video_quality_handler.getSupportedVideoQuality();
	}

	/** Returns whether the user's fps preference is both non-default, and is considered a
	 *  "high-speed" frame rate, but not a normal frame rate. (Note, we go by the supplied
	 *  fps_value, and not what the user's preference necessarily is; so this doesn't say whether
	 *  the Preview is currently set to normal or high speed video mode.)
	 */
	public boolean fpsIsHighSpeed(String fps_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "fpsIsHighSpeed: " + fps_value);
		if( !fps_value.equals("default") && supports_video_high_speed ) {
			try {
				int fps = Integer.parseInt(fps_value);
				if( MyDebug.LOG )
					Log.d(TAG, "fps: " + fps);
				// need to check both, e.g., 30fps on Nokia 8 is in fps ranges of both normal and high speed video sizes
				if( video_quality_handler.videoSupportsFrameRate(fps) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "fps is normal");
					return false;
				}
				else if( video_quality_handler.videoSupportsFrameRateHighSpeed(fps) ) {
					if( MyDebug.LOG )
						Log.d(TAG, "fps is high speed");
					return true;
				}
				else {
					// shouldn't be here?!
					Log.e(TAG, "fps is neither normal nor high speed");
					return false;
				}
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.d(TAG, "fps invalid format, can't parse to int: " + fps_value);
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "fps is not high speed");
		return false;
	}

	public boolean supportsVideoHighSpeed() {
		return this.supports_video_high_speed;
	}

	public List<String> getSupportedFlashValues() {
		return supported_flash_values;
	}

	public List<String> getSupportedFocusValues() {
		return supported_focus_values;
	}
	
	public int getCameraId() {
		if( camera_controller == null )
			return 0;
		return camera_controller.getCameraId();
	}

	public String getCameraAPI() {
		if( camera_controller == null )
			return "None";
		return camera_controller.getAPI();
	}

	public String getHardwareLevel() {
		if( camera_controller != null ) return this.hardware_level;
		return null;
	}

	public void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
		this.app_is_paused = false;
		cameraSurface.onResume();
		if( canvasView != null )
			canvasView.onResume();

		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSING ) {
			// when pausing, we close the camera on a background thread - so if this is still happening when we resume,
			// we won't be able to open the camera, so need to open camera when it's closed
			if( MyDebug.LOG )
				Log.d(TAG, "camera still closing");
			if( close_camera_task != null ) { // just to be safe
				close_camera_task.reopen = true;
			}
			else {
				Log.e(TAG, "onResume: state is CAMERAOPENSTATE_CLOSING, but close_camera_task is null");
			}
		}
		else if (this.camera_controller == null) {
			this.openCamera();
		}
	}

	public void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause");
		this.app_is_paused = true;
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_OPENING ) {
			if( MyDebug.LOG )
				Log.d(TAG, "cancel open_camera_task");
			if( open_camera_task != null ) { // just to be safe
				this.open_camera_task.cancel(true);
			}
			else {
				Log.e(TAG, "onPause: state is CAMERAOPENSTATE_OPENING, but open_camera_task is null");
			}
		}
		//final boolean use_background_thread = false;
		final boolean use_background_thread = true;
		this.closeCamera(use_background_thread, null);
		cameraSurface.onPause();
		if( canvasView != null )
			canvasView.onPause();
	}

	public void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( camera_open_state == CameraOpenState.CAMERAOPENSTATE_CLOSING ) {
			// If the camera is currently closing on a background thread, then wait until the camera has closed to be safe
			if( MyDebug.LOG ) {
				Log.d(TAG, "wait for close_camera_task");
			}
			if( close_camera_task != null ) { // just to be safe
				long time_s = System.currentTimeMillis();
				try {
					close_camera_task.get(3000, TimeUnit.MILLISECONDS); // set timeout to avoid ANR (camera resource should be freed by the OS when destroyed anyway)
				}
				catch(ExecutionException | InterruptedException | TimeoutException e) {
					Log.e(TAG, "exception while waiting for close_camera_task to finish");
					e.printStackTrace();
				}
				if( MyDebug.LOG ) {
					Log.d(TAG, "done waiting for close_camera_task");
					Log.d(TAG, "### time after waiting for close_camera_task: " + (System.currentTimeMillis() - time_s));
				}
			}
			else {
				Log.e(TAG, "onResume: state is CAMERAOPENSTATE_CLOSING, but close_camera_task is null");
			}
		}
	}

	public void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
	}

	public void showToast(final ToastBoxer clear_toast, final int message_id) {
		showToast(clear_toast, getResources().getString(message_id));
	}

	public void showToast(final ToastBoxer clear_toast, final String message) {
		showToast(clear_toast, message, 32);
	}

	public void showToast(final String message) {
		showToast(seekbar_toast, message, 32);
	}

	private void showToast(final ToastBoxer clear_toast, final String message, final int offset_y_dp) {
		if( !Prefs.getShowToastsPref() ) {
			return;
		}
		
		class RotatedTextView extends View {
			private String [] lines;
			private final Paint paint = new Paint();
			private final Rect bounds = new Rect();
			private final Rect sub_bounds = new Rect();
			private final RectF rect = new RectF();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.lines = text.split("\n");
			}
			
			void setText(String text) {
				this.lines = text.split("\n");
			}

			@Override
			protected void onDraw(Canvas canvas) {
				paint.setTextSize(toast_font_size);
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				paint.setAntiAlias(true);
				//paint.getTextBounds(text, 0, text.length(), bounds);
				boolean first_line = true;
				for(String line : lines) {
					paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "line: " + line + " sub_bounds: " + sub_bounds);
					}*/
					if( first_line ) {
						bounds.set(sub_bounds);
						first_line = false;
					}
					else {
						bounds.top = Math.min(sub_bounds.top, bounds.top);
						bounds.bottom = Math.max(sub_bounds.bottom, bounds.bottom);
						bounds.left = Math.min(sub_bounds.left, bounds.left);
						bounds.right = Math.max(sub_bounds.right, bounds.right);
					}
				}
				// above we've worked out the maximum bounds of each line - this is useful for left/right, but for the top/bottom
				// we would rather use a consistent height no matter what the text is (otherwise we have the problem of varying
				// gap between lines, depending on what the characters are).
				final String reference_text = "Ap";
				paint.getTextBounds(reference_text, 0, reference_text.length(), sub_bounds);
				bounds.top = sub_bounds.top;
				bounds.bottom = sub_bounds.bottom;
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "bounds: " + bounds);
				}*/
				int height = bounds.bottom - bounds.top; // height of each line
				bounds.bottom += ((lines.length-1) * height)/2;
				bounds.top -= ((lines.length-1) * height)/2;
				final int padding = (int) (14 * scale + 0.5f); // padding for the shaded rectangle; convert dps to pixels
				final int offset_y = (int) (offset_y_dp * scale + 0.5f); // convert dps to pixels
				canvas.save();
				canvas.rotate(((MainActivity)this.getContext()).getMainUI().getUIRotation(), canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

				rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
				rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
				rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
				rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(toast_color);
				final float radius = (toast_radius); // convert dps to pixels
				canvas.drawRoundRect(rect, radius, radius, paint);

				paint.setColor(Color.WHITE);
				int ypos = canvas.getHeight()/2 + offset_y - ((lines.length-1) * height)/2;
				for(String line : lines) {
					canvas.drawText(line, canvas.getWidth()/2 - bounds.width()/2, ypos, paint);
					ypos += height;
				}
				canvas.restore();
			}
		}

		if( MyDebug.LOG )
			Log.d(TAG, "showToast: " + message);
		final Activity activity = (Activity)this.getContext();
		// We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
		// Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		activity.runOnUiThread(new Runnable() {
			public void run() {
				/*if( clear_toast != null && clear_toast.toast != null )
					clear_toast.toast.cancel();

				Toast toast = new Toast(activity);
				if( clear_toast != null )
					clear_toast.toast = toast;*/
				// This method is better, as otherwise a previous toast (with different or no clear_toast) never seems to clear if we repeatedly issue new toasts - this doesn't happen if we reuse existing toasts if possible
				// However should only do this if the previous toast was the most recent toast (to avoid messing up ordering)
				Toast toast;
				if( clear_toast != null && clear_toast.toast != null && clear_toast.toast == last_toast ) {
					if( MyDebug.LOG )
						Log.d(TAG, "reuse last toast: " + last_toast);
					toast = clear_toast.toast;
					// for performance, important to reuse the same view, instead of creating a new one (otherwise we get jerky preview update e.g. for changing manual focus slider)
					RotatedTextView view = (RotatedTextView)toast.getView();
					view.setText(message);
					view.invalidate(); // make sure the toast is redrawn
					toast.setView(view);
				}
				else {
					if( clear_toast != null && clear_toast.toast != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "cancel last toast: " + clear_toast.toast);
						clear_toast.toast.cancel();
					}
					toast = new Toast(activity);
					if( MyDebug.LOG )
						Log.d(TAG, "created new toast: " + toast);
					if( clear_toast != null )
						clear_toast.toast = toast;
					View text = new RotatedTextView(message, activity);
					toast.setView(text);
				}
				toast.setDuration(Toast.LENGTH_SHORT);
				toast.show();
				last_toast = toast;
			}
		});
	}

	/** If geotagging is enabled, pass the location info to the camera controller (for photos).
	 */
	private void updateParametersFromLocation() {
		if( MyDebug.LOG )
			Log.d(TAG, "updateParametersFromLocation");
		if( camera_controller != null ) {
			boolean store_location = Prefs.getGeotaggingPref();
			if( store_location && applicationInterface.getLocation() != null ) {
				Location location = applicationInterface.getLocation();
				if( MyDebug.LOG ) {
					Log.d(TAG, "updating parameters from location...");
					Log.d(TAG, "lat " + location.getLatitude() + " long " + location.getLongitude() + " accuracy " + location.getAccuracy() + " timestamp " + location.getTime());
				}
				camera_controller.setLocationInfo(location);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "removing location data from parameters...");
				camera_controller.removeLocationInfo();
			}
		}
	}
	
	public boolean isVideo() {
		return is_video;
	}
	
	public boolean isVideoRecording() {
		return video_recorder != null && video_start_time_set;
	}

	public boolean isVideoRecordingPaused() {
		return isVideoRecording() && video_recorder_is_paused;
	}
	
	public long getVideoTime() {
		if( this.isVideoRecordingPaused() ) {
			return video_accumulated_time;
		}
		long time_now = System.currentTimeMillis();
		return time_now - video_start_time + video_accumulated_time;
	}
	
	public long getVideoAccumulatedTime() {
		return video_accumulated_time;
	}
	
	public int getMaxAmplitude() {
		return video_recorder != null ? video_recorder.getMaxAmplitude() : 0;
	}

	public boolean isTakingPhoto() {
		return this.phase == PHASE_TAKING_PHOTO;
	}
	
	public boolean usingCamera2API() {
		return this.using_camera_2;
	}

	public CameraController getCameraController() {
		return this.camera_controller;
	}
	
	public CameraControllerManager getCameraControllerManager() {
		return this.camera_controller_manager;
	}
	
	public boolean supportsFocus() {
		return this.supported_focus_values != null;
	}

	public boolean supportsFlash() {
		return this.supported_flash_values != null;
	}
	
	public boolean supportsAutoAdjustmentLock() {
		return (this.is_auto_adjustment_lock_supported || this.supported_focus_values != null);
	}

	public boolean isAutoAdjustmentLocked() {
		return this.is_auto_adjustment_locked;
	}
	
	public boolean supportsZoom() {
		return this.has_zoom;
	}
	
	public int getMaxZoom() {
		return this.max_zoom_factor;
	}
	
	public boolean hasFocusArea() {
		return this.has_focus_area;
	}
	
	public boolean hasMeteringArea() {
		return this.has_metering_area;
	}

	public Pair<Integer, Integer> getFocusPos() {
		return new Pair<>(focus_screen_x, focus_screen_y);
	}

	public Pair<Integer, Integer> getMeteringPos() {
		return new Pair<Integer, Integer>(metering_screen_x, metering_screen_y);
	}

	public int getMaxNumFocusAreas() {
		return this.max_num_focus_areas;
	}

	public int getMaxNumMeteringAreas() {
		return this.max_num_metering_areas;
	}

	public boolean isTakingPhotoOrOnTimer() {
		return this.phase == PHASE_TAKING_PHOTO || this.phase == PHASE_TIMER;
	}

	public boolean isOnTimer() {
		return this.phase == PHASE_TIMER;
	}

	public boolean showTimer() {
		return show_timer;
	}

	public long getTimerEndTime() {
		return take_photo_time;
	}
	
	public boolean isPreviewPaused() {
		return this.phase == PHASE_PREVIEW_PAUSED;
	}

	public boolean isPreviewStarted() {
		return this.is_preview_started;
	}
	
	public boolean isFocusWaiting() {
		return focus_success == FOCUS_WAITING;
	}
	
	public boolean isFocusRecentSuccess() {
		return focus_success == FOCUS_SUCCESS;
	}
	
	public long timeSinceStartedAutoFocus() {
		if( focus_started_time != -1 )
			return System.currentTimeMillis() - focus_started_time;
		return 0;
	}
	
	public boolean isFocusRecentFailure() {
		return focus_success == FOCUS_FAILED;
	}

	public boolean isFocusZoneChanged() {
		return focus_zone_changed;
	}

	/** Whether we can skip the autofocus before taking a photo.
	 */
	private boolean recentlyFocused() {
		return this.successfully_focused && System.currentTimeMillis() < this.successfully_focused_time + 5000;
	}

	public CameraController.Face [] getFacesDetected() {
		// FindBugs warns about returning the array directly, but in fact we need to return direct access rather than copying, so that the on-screen display of faces rectangles updates
		if (this.using_face_detection && faces_detected != null && faces_detected.length > 0)
			return this.faces_detected;
			
		return null;
	}

	/** Returns the current zoom factor of the camera. Always returns 1.0f if zoom isn't supported.
	 */
	public float getZoomRatio() {
		if( zoom_ratios == null || camera_controller == null )
			return 1.0f;
		int zoom_factor = camera_controller.getZoom();
		return this.zoom_ratios.get(zoom_factor)/100.0f;
	}

	public float getMaxZoomRatio() {
		return this.zoom_ratios.get(max_zoom_factor)/100.0f;
	}
	
	public boolean isBurst() {
		return is_burst;
	}
	
	public int getBurstCount() {
		return burst_count;
	}

	public int getBurstCaptured() {
		return burst_captured;
	}

	public boolean isWaitingFace() {
		return wait_face;
	}

	private void onFaceDetected(boolean detected) {
		if (camera_controller == null) return;
		if (detected) {
			if (this.has_focus_area) {
				this.saved_focus_screen_x = this.focus_screen_x;
				this.saved_focus_screen_y = this.focus_screen_y;

				this.has_saved_focus_area = true;
				this.has_focus_area = false;
			}
			if (this.has_metering_area) {
				this.saved_metering_screen_x = this.metering_screen_x;
				this.saved_metering_screen_y = this.metering_screen_y;

				this.has_saved_metering_area = true;
				this.has_metering_area = false;
			}
			
			camera_controller.clearFocusAndMetering();

			if (wait_face) {
				Activity activity = (Activity)Preview.this.getContext();
				activity.runOnUiThread(new Runnable() {
					public void run() {
						applicationInterface.stoppingTimer(false, true);
						takePicturePressed();
					}
				});
				wait_face = false;
			}
		} else {
			// Clear focus zone after force face focus
			if (this.has_focus_area) {
				this.has_focus_area = false;
				camera_controller.clearFocusAndMetering();
			}
			if (this.has_saved_focus_area) {
				if( camera_controller.setFocusAndMeteringArea(getAreas(saved_focus_screen_x, saved_focus_screen_y)) ) {
					this.focus_screen_x = this.saved_focus_screen_x;
					this.focus_screen_y = this.saved_focus_screen_y;
					this.has_focus_area = true;

//					tryAutoFocus(false, true);
				}

				this.has_saved_focus_area = false;
			}
			if (this.has_saved_metering_area) {
				if( camera_controller.setMeteringArea(getAreas(saved_metering_screen_x, saved_metering_screen_y)) ) {
					this.metering_screen_x = this.saved_metering_screen_x;
					this.metering_screen_y = this.saved_metering_screen_y;
					this.has_metering_area = true;
				}

				this.has_saved_metering_area = false;
			}
		}
		
		applicationInterface.faceDetected(detected);
	}
	
	public void updateToastConfig() {
		Resources resources = getContext().getResources();
		toast_padding = resources.getDimensionPixelSize(R.dimen.toast_padding);
		toast_radius = resources.getDimensionPixelSize(R.dimen.toast_radius);
		switch( sharedPreferences.getString(Prefs.POPUP_FONT_SIZE, "normal") ) {
			case "small":
				toast_font_size = resources.getDimension(R.dimen.popup_text_small_default);
				break;
			case "large":
				toast_font_size = resources.getDimension(R.dimen.popup_text_large_default);
				break;
			case "xlarge":
				toast_font_size = resources.getDimension(R.dimen.popup_text_xlarge_default);
				break;
			default:
				toast_font_size = resources.getDimension(R.dimen.popup_text_normal_default);
				break;
		}
		((TextView)((Activity)getContext()).findViewById(R.id.seekbar_hint)).setTextSize(TypedValue.COMPLEX_UNIT_PX, toast_font_size);
		
		toast_color = resources.getColor(R.color.toast_color);
	}
	
	public int getTickInterval() {
		// avoid overloading ui thread when taking photo
		if (tick_slow_if_busy && (isTakingPhotoOrOnTimer() || (is_video && isVideoRecording()))) return 500;
		return tick_interval;
	}
	
	public void updateTickInterval() {
		final String ind_update_freq = sharedPreferences.getString(Prefs.IND_FREQ, "normal");
		switch (ind_update_freq) {
			case "low":
				tick_interval = 100;
				break;
			case "high":
				tick_interval = 20;
				break;
			default:
				tick_interval = 40;
		}

		tick_slow_if_busy = sharedPreferences.getBoolean(Prefs.IND_SLOW_IF_BUSY, false);
	}
	
	public void focusZoom() {
		if (camera_controller != null && !this.is_video && this.has_zoom) {
			if( is_auto_adjustment_lock_supported ) {
				resetAutoAdjustmentUnlockTimer();
				camera_controller.setAutoAdjustmentLock(true);
			}
			if (current_zoom == -1)
				current_zoom = camera_controller.getZoom();
			camera_controller.setZoom(getMaxZoom());
		}
	}

	public void resetZoom() {
		if (camera_controller != null && !this.is_video && this.has_zoom && current_zoom != -1) {
			camera_controller.setZoom(current_zoom);
			current_zoom = -1;
			if( is_auto_adjustment_lock_supported && !is_auto_adjustment_locked ) {
				resetAutoAdjustmentUnlockTimer();
				auto_adjustment_unlock_handler = new Handler();
				auto_adjustment_unlock_runnable = new Runnable() {
					@Override
					public void run() {
						if (camera_controller != null)
							camera_controller.setAutoAdjustmentLock(is_auto_adjustment_locked);
						
						auto_adjustment_unlock_handler = null;
						auto_adjustment_unlock_runnable = null;
					}
				};
				
				auto_adjustment_unlock_handler.postDelayed(auto_adjustment_unlock_runnable, 500);
			}
		}
	}
	
	public void resetAutoAdjustmentUnlockTimer() {
		if (auto_adjustment_unlock_handler != null && auto_adjustment_unlock_runnable != null) {
			auto_adjustment_unlock_handler.removeCallbacks(auto_adjustment_unlock_runnable);
			auto_adjustment_unlock_handler = null;
			auto_adjustment_unlock_runnable = null;
		}
	}
	
	public ApplicationInterface getApplicationInterface() {
		return applicationInterface;
	}

	/** This will always return 1, even if slow motion isn't supported (i.e.,
	 *  slow motion should only be considered as supported if at least 2 entries
	 *  are returned. Entries are returned in increasing order.
	 */
	public List<Float> getSupportedVideoCaptureRates() {
		List<Float> rates = new ArrayList<>();
		if( supportsVideoHighSpeed() ) {
			// We consider a slow motion rate supported if we can get at least 30fps in slow motion.
			// If this code is updated, see if we also need to update how slow motion fps is chosen
			// in getVideoFPSPref().
			if( getVideoQualityHander().videoSupportsFrameRateHighSpeed(240) ||
					getVideoQualityHander().videoSupportsFrameRate(240) ) {
				rates.add(1.0f/8.0f);
				rates.add(1.0f/4.0f);
				rates.add(1.0f/2.0f);
			}
			else if( getVideoQualityHander().videoSupportsFrameRateHighSpeed(120) ||
					getVideoQualityHander().videoSupportsFrameRate(120) ) {
				rates.add(1.0f/4.0f);
				rates.add(1.0f/2.0f);
			}
			else if( getVideoQualityHander().videoSupportsFrameRateHighSpeed(60) ||
					getVideoQualityHander().videoSupportsFrameRate(60) ) {
				rates.add(1.0f/2.0f);
			}
		}
		rates.add(1.0f);
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			// add timelapse options
			// in theory this should work on any Android version, though video fails to record in timelapse mode on Galaxy Nexus...
			rates.add(2.0f);
			rates.add(3.0f);
			rates.add(4.0f);
			rates.add(5.0f);
			rates.add(10.0f);
			rates.add(20.0f);
			rates.add(30.0f);
			rates.add(60.0f);
		}
		return rates;
	}

}
