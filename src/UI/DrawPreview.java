package com.caddish_hedgehog.hedgecam2.UI;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Locale;

import com.caddish_hedgehog.hedgecam2.GyroSensor;
import com.caddish_hedgehog.hedgecam2.IconView;
import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyApplicationInterface;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;

public class DrawPreview implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String TAG = "HedgeCam/DrawPreview";

	private final MainActivity main_activity;
	private final MyApplicationInterface applicationInterface;
	private final Resources resources;

	private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
	private Prefs.PhotoMode pref_photo_mode;
	private String pref_grid;
	private int pref_grid_alpha;
	private String pref_crop_guide;
	private boolean pref_is_video;
	private boolean pref_auto_stabilise;
	private boolean pref_take_photo_border;
	private boolean pref_thumbnail_animation;
	private boolean pref_angle;
	private boolean pref_angle_line;
	private boolean pref_pitch_lines;
	private boolean pref_direction;
	private boolean pref_direction_lines;
	private boolean pref_zoom;
	private boolean pref_alt_indication;
	private int pref_color_angle;
	private boolean pref_hide_indication;
	private boolean pref_battery;
	private boolean pref_time;
	private boolean pref_free_memory;
	private boolean pref_iso;
	private boolean pref_white_balance;
	private String pref_iso_value;
	private boolean pref_location;
	private boolean pref_stamp;
	private boolean pref_ctrl_panel_photo_mode;
	private boolean pref_ctrl_panel_flash;
	private boolean pref_mode_panel;
	private boolean pref_face_detection;
	private boolean pref_raw;
	
	private boolean update_prefs = true;

	// avoid doing things that allocate memory every frame!
	private final Paint p = new Paint();
	private final RectF face_rect = new RectF();
	private final RectF draw_rect = new RectF();
	private final int [] gui_location = new int[2];
	private final static DecimalFormat decimalFormat = new DecimalFormat("#0.0");
	private final float scale;
	private final float stroke_width;
	private Calendar calendar;
	private final DateFormat dateFormatTimeInstance = DateFormat.getTimeInstance();

	private final static double close_level_angle = 0.5f;
	private boolean is_level;

	private float free_memory_gb = -1.0f;
	private long last_free_memory_time;

	private final IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private boolean has_battery_frac;
	private float battery_frac;
	private long last_battery_time;

	private final Rect icon_dest = new Rect();

	private Bitmap last_thumbnail; // thumbnail of last picture taken
	private volatile boolean thumbnail_anim; // whether we are displaying the thumbnail animation; must be volatile for test project reading the state
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
	private final RectF thumbnail_anim_src_rect = new RectF();
	private final RectF thumbnail_anim_dst_rect = new RectF();
	private final Matrix thumbnail_anim_matrix = new Matrix();

	private boolean show_last_image;
	private final RectF last_image_src_rect = new RectF();
	private final RectF last_image_dst_rect = new RectF();
	private final Matrix last_image_matrix = new Matrix();

	private long ae_started_scanning_ms = -1; // time when ae started scanning

	private boolean taking_picture; // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
	private boolean capture_started; // true iff the camera is capturing
	
	private boolean continuous_focus_moving;
	private long continuous_focus_moving_ms;

	private int text_color_default;
	private int text_color_red;
	private int text_color_green;
	private int text_color_blue;
	private int text_color_yellow;
	
	private float text_size_default;
	private float text_size_video;
	private float text_size_timer;
	private float text_size_icon;
	private float line_height = 1.4f;
	private float line_height_small = 1.2f;
	private float half_line_div = 2.5f; /* WTF? F*ckin canvas, f*ckin android, f*ckin everything :'(  */
	
	private final Typeface default_font;
	private final Typeface icon_font;

	private boolean enable_gyro_target_spot;
	private final float [] gyro_direction = new float[3];
	private final float [] transformed_gyro_direction = new float[3];

	public DrawPreview(MainActivity main_activity, MyApplicationInterface applicationInterface) {
		if( MyDebug.LOG )
			Log.d(TAG, "DrawPreview");
		this.main_activity = main_activity;
		this.applicationInterface = applicationInterface;

		p.setAntiAlias(true);
		p.setStrokeCap(Paint.Cap.SQUARE);
		// may be, it will be used in future
		//p.setShadowLayer (10f, 0, 0, Color.argb(127, 0, 0, 0));
		this.resources = main_activity.getResources();
		this.scale = resources.getDisplayMetrics().density;
		this.stroke_width = resources.getDimension(R.dimen.ind_stroke_width);
		p.setStrokeWidth(stroke_width);

		text_color_default = resources.getColor(R.color.main_white);
		text_color_red = resources.getColor(R.color.main_red);
		text_color_green = resources.getColor(R.color.main_green);
		text_color_blue = resources.getColor(R.color.main_blue);
		text_color_yellow = resources.getColor(R.color.main_yellow);
		
		default_font = p.getTypeface();
		icon_font = IconView.getTypeface(main_activity);

		main_activity.getSharedPrefs().registerOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
		if( MyDebug.LOG ) Log.d(TAG, "onSharedPreferenceChanged");
		update_prefs = true;
	}

	public void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
	}

	private Context getContext() {
		return main_activity;
	}
	
	private void updatePrefs() {
		if( MyDebug.LOG ) Log.d(TAG, "updatePrefs");
		final SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
		switch( sharedPreferences.getString(Prefs.OSD_FONT_SIZE, "normal") ) {
			case "small":
				text_size_default = resources.getDimension(R.dimen.ind_text_small_default);
				text_size_video = resources.getDimension(R.dimen.ind_text_small_video);
				text_size_timer = resources.getDimension(R.dimen.ind_text_small_timer);
				text_size_icon = resources.getDimension(R.dimen.ind_text_small_icon);
				break;
			case "large":
				text_size_default = resources.getDimension(R.dimen.ind_text_large_default);
				text_size_video = resources.getDimension(R.dimen.ind_text_large_video);
				text_size_timer = resources.getDimension(R.dimen.ind_text_large_timer);
				text_size_icon = resources.getDimension(R.dimen.ind_text_large_icon);
				break;
			case "xlarge":
				text_size_default = resources.getDimension(R.dimen.ind_text_xlarge_default);
				text_size_video = resources.getDimension(R.dimen.ind_text_xlarge_video);
				text_size_timer = resources.getDimension(R.dimen.ind_text_xlarge_timer);
				text_size_icon = resources.getDimension(R.dimen.ind_text_xlarge_icon);
				break;
			default:
				text_size_default = resources.getDimension(R.dimen.ind_text_normal_default);
				text_size_video = resources.getDimension(R.dimen.ind_text_normal_video);
				text_size_timer = resources.getDimension(R.dimen.ind_text_normal_timer);
				text_size_icon = resources.getDimension(R.dimen.ind_text_normal_icon);
				break;
		}

		pref_grid = sharedPreferences.getString(Prefs.GRID, "preference_grid_none");
		if (pref_grid.equals("preference_grid_none")) {
			pref_grid = null;
		} else {
			pref_grid_alpha = 255;
			switch( sharedPreferences.getString(Prefs.GRID_ALPHA, "0") ) {
				case "1":
					pref_grid_alpha = 191;
					break;
				case "2":
					pref_grid_alpha = 127;
					break;
				case "3":
					pref_grid_alpha = 63;
					break;
			}
		}

		pref_crop_guide = sharedPreferences.getString(Prefs.CROP_GUIDE, "crop_guide_none");
		if (pref_crop_guide.equals("crop_guide_none")) pref_crop_guide = null;

		pref_is_video = Prefs.isVideoPref();
		pref_photo_mode = Prefs.getPhotoMode();
		pref_auto_stabilise = Prefs.getAutoStabilisePref();

		pref_take_photo_border = sharedPreferences.getBoolean(Prefs.TAKE_PHOTO_BORDER, true);
		pref_thumbnail_animation = sharedPreferences.getBoolean(Prefs.THUMBNAIL_ANIMATION, true)
			&& !sharedPreferences.getBoolean(Prefs.PAUSE_PREVIEW, false);

		pref_angle = sharedPreferences.getBoolean(Prefs.SHOW_ANGLE, true);
		pref_angle_line = sharedPreferences.getBoolean(Prefs.SHOW_ANGLE_LINE, false);
		pref_pitch_lines = sharedPreferences.getBoolean(Prefs.SHOW_PITCH_LINES, false);
		pref_direction = sharedPreferences.getBoolean(Prefs.SHOW_GEO_DIRECTION, false);
		pref_direction_lines = sharedPreferences.getBoolean(Prefs.SHOW_GEO_DIRECTION_LINES, false);
		pref_zoom = sharedPreferences.getBoolean(Prefs.SHOW_ZOOM, true);
		pref_alt_indication = sharedPreferences.getBoolean(Prefs.ALT_INDICATION, true);

		if (pref_angle || pref_angle_line) {
			switch (sharedPreferences.getString(Prefs.ANGLE_HIGHLIGHT_COLOR, "green")) {
				case "red":
					pref_color_angle = text_color_red;
					break;
				case "yellow":
					pref_color_angle = text_color_yellow;
					break;
				case "blue":
					pref_color_angle = text_color_blue;
					break;
				case "white":
					pref_color_angle = text_color_default;
					break;
				default:
					pref_color_angle = text_color_green;
			}
		}

		// exit, to ensure we don't display anything!
		// though note we still should do the front screen flash (since the user can take photos via volume keys when
		// in immersive_mode_everything mode)
		pref_hide_indication = sharedPreferences.getString(Prefs.IMMERSIVE_MODE, "immersive_mode_low_profile")
			.equals("immersive_mode_everything");
		
		pref_battery = sharedPreferences.getBoolean(Prefs.SHOW_BATTERY, true);
		pref_time = sharedPreferences.getBoolean(Prefs.SHOW_TIME, true);
		pref_free_memory = sharedPreferences.getBoolean(Prefs.FREE_MEMORY, true);
		pref_iso = sharedPreferences.getBoolean(Prefs.SHOW_ISO, true);
		pref_iso_value = Prefs.getISOPref();

//		pref_white_balance = sharedPreferences.getBoolean(Prefs.SHOW_WHITE_BALANCE, false);
	
		pref_location = sharedPreferences.getBoolean(Prefs.LOCATION, false);
		pref_stamp = sharedPreferences.getBoolean(Prefs.STAMP, false);
		
		pref_ctrl_panel_photo_mode = main_activity.getMainUI().isVisible(R.id.photo_mode);
		pref_ctrl_panel_flash = main_activity.getMainUI().isVisible(R.id.flash_mode);
		
		pref_mode_panel = sharedPreferences.getBoolean(Prefs.SHOW_MODE_PANEL, false);
		
		pref_face_detection = !main_activity.getMainUI().isVisible(R.id.face_detection) && Prefs.getFaceDetectionPref();
		pref_raw = !pref_is_video && applicationInterface.isRawPref() && main_activity.getPreview().supportsRaw() &&
				pref_photo_mode != Prefs.PhotoMode.HDR && pref_photo_mode != Prefs.PhotoMode.ExpoBracketing && pref_photo_mode != Prefs.PhotoMode.FastBurst;
	}

	public void updateThumbnail(Bitmap thumbnail) {
		if( MyDebug.LOG )
			Log.d(TAG, "updateThumbnail");
		if( pref_thumbnail_animation ) {
			if( MyDebug.LOG )
				Log.d(TAG, "thumbnail_anim started");
			thumbnail_anim = true;
			thumbnail_anim_start_ms = System.currentTimeMillis();
		}
		Bitmap old_thumbnail = this.last_thumbnail;
		this.last_thumbnail = thumbnail;
		if( old_thumbnail != null ) {
			// only recycle after we've set the new thumbnail
			old_thumbnail.recycle();
		}
	}
	
	public boolean hasThumbnailAnimation() {
		return this.thumbnail_anim;
	}
	
	/** Displays the thumbnail as a fullscreen image (used for pause preview option).
	 */
	public void showLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "showLastImage");
		this.show_last_image = true;
	}
	
	public void clearLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImage");
		this.show_last_image = false;
	}

	public void cameraInOperation(boolean in_operation) {
		if( in_operation && !main_activity.getPreview().isVideo() ) {
			taking_picture = true;
		}
		else {
			taking_picture = false;
			capture_started = false;
		}
	}

	public void onCaptureStarted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onCaptureStarted");
		capture_started = true;
	}

	public void onContinuousFocusMove(boolean start) {
		if( MyDebug.LOG )
			Log.d(TAG, "onContinuousFocusMove: " + start);
		if( start ) {
			if( !continuous_focus_moving ) { // don't restart the animation if already in motion
				continuous_focus_moving = true;
				continuous_focus_moving_ms = System.currentTimeMillis();
			}
		}
		// if we receive start==false, we don't stop the animation - let it continue
	}

	public void clearContinuousFocusMove() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearContinuousFocusMove");
		continuous_focus_moving = false;
		continuous_focus_moving_ms = 0;
	}

	public void setGyroDirectionMarker(float x, float y, float z) {
		enable_gyro_target_spot = true;
		gyro_direction[0] = x;
		gyro_direction[1] = y;
		gyro_direction[2] = z;
	}

	public void clearGyroDirectionMarker() {
		enable_gyro_target_spot = false;
	}

	private String getTimeStringFromSeconds(long time) {
		int secs = (int)(time % 60);
		time /= 60;
		int mins = (int)(time % 60);
		time /= 60;
		return (time > 0 ? time + ":" : "") + String.format(Locale.getDefault(), "%02d", mins) + ":" + String.format(Locale.getDefault(), "%02d", secs);
	}

	private void drawGrids(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		if( camera_controller == null || pref_grid == null ) {
			return;
		}
		
		final float canvas_width = (float)canvas.getWidth();
		final float canvas_height = (float)canvas.getHeight();

		p.setColor(Color.WHITE);
		p.setStrokeWidth(resources.getDimension(R.dimen.ind_grid_thickness));
		p.setAlpha(pref_grid_alpha);
		p.setStyle(Paint.Style.STROKE);
		switch( pref_grid ) {
			case "preference_grid_3x3":
				canvas.drawLine(canvas_width / 3.0f, 0.0f, canvas_width / 3.0f, canvas_height - 1.0f, p);
				canvas.drawLine(2.0f * canvas_width / 3.0f, 0.0f, 2.0f * canvas_width / 3.0f, canvas_height - 1.0f, p);
				canvas.drawLine(0.0f, canvas_height / 3.0f, canvas_width - 1.0f, canvas_height / 3.0f, p);
				canvas.drawLine(0.0f, 2.0f * canvas_height / 3.0f, canvas_width - 1.0f, 2.0f * canvas_height / 3.0f, p);
				break;
			case "preference_grid_phi_3x3":
				canvas.drawLine(canvas_width / 2.618f, 0.0f, canvas_width / 2.618f, canvas_height - 1.0f, p);
				canvas.drawLine(1.618f * canvas_width / 2.618f, 0.0f, 1.618f * canvas_width / 2.618f, canvas_height - 1.0f, p);
				canvas.drawLine(0.0f, canvas_height / 2.618f, canvas_width - 1.0f, canvas_height / 2.618f, p);
				canvas.drawLine(0.0f, 1.618f * canvas_height / 2.618f, canvas_width - 1.0f, 1.618f * canvas_height / 2.618f, p);
				break;
			case "preference_grid_4x2":
			case "preference_grid_4x4":
				p.setColor(Color.GRAY);
				p.setAlpha(pref_grid_alpha);
				canvas.drawLine(canvas_width / 4.0f, 0.0f, canvas_width / 4.0f, canvas_height - 1.0f, p);
				canvas.drawLine(canvas_width / 2.0f, 0.0f, canvas_width / 2.0f, canvas_height - 1.0f, p);
				canvas.drawLine(3.0f * canvas_width / 4.0f, 0.0f, 3.0f * canvas_width / 4.0f, canvas_height - 1.0f, p);
				canvas.drawLine(0.0f, canvas_height / 2.0f, canvas_width - 1.0f, canvas_height / 2.0f, p);
				if (pref_grid.equals("preference_grid_4x4")) {
					canvas.drawLine(0.0f, canvas_height / 4.0f, canvas_width - 1.0f, canvas_height / 4.0f, p);
					canvas.drawLine(0.0f, 3.0f * canvas_height / 4.0f, canvas_width - 1.0f, 3.0f * canvas_height / 4.0f, p);
				}
				p.setColor(Color.WHITE);
				p.setAlpha(pref_grid_alpha);
				int crosshairs_radius = resources.getDimensionPixelSize(R.dimen.ind_grid_crosshair);

				canvas.drawLine(canvas_width / 2.0f, canvas_height / 2.0f - crosshairs_radius, canvas_width / 2.0f, canvas_height / 2.0f + crosshairs_radius, p);
				canvas.drawLine(canvas_width / 2.0f - crosshairs_radius, canvas_height / 2.0f, canvas_width / 2.0f + crosshairs_radius, canvas_height / 2.0f, p);
				break;
			case "preference_grid_crosshair":
				canvas.drawLine(canvas_width / 2.0f, 0.0f, canvas_width / 2.0f, canvas_height - 1.0f, p);
				canvas.drawLine(0.0f, canvas_height / 2.0f, canvas_width - 1.0f, canvas_height / 2.0f, p);
				break;
			case "preference_grid_golden_spiral_right":
			case "preference_grid_golden_spiral_left":
			case "preference_grid_golden_spiral_upside_down_right":
			case "preference_grid_golden_spiral_upside_down_left":
				canvas.save();
				switch (pref_grid) {
					case "preference_grid_golden_spiral_left":
						canvas.scale(-1.0f, 1.0f, canvas_width * 0.5f, canvas_height * 0.5f);
						break;
					case "preference_grid_golden_spiral_right":
						// no transformation needed
						break;
					case "preference_grid_golden_spiral_upside_down_left":
						canvas.rotate(180.0f, canvas_width * 0.5f, canvas_height * 0.5f);
						break;
					case "preference_grid_golden_spiral_upside_down_right":
						canvas.scale(1.0f, -1.0f, canvas_width * 0.5f, canvas_height * 0.5f);
						break;
				}
				int fibb = 34;
				int fibb_n = 21;
				int left = 0, top = 0;
				int full_width = (int)canvas_width;
				int full_height = (int)canvas_height;
				int width = (int) (full_width * ((double) fibb_n) / (double) (fibb));
				int height = full_height;

				for (int count = 0; count < 2; count++) {
					canvas.save();
					draw_rect.set(left, top, left + width, top + height);
					canvas.clipRect(draw_rect);
					canvas.drawRect(draw_rect, p);
					draw_rect.set(left, top, left + 2 * width, top + 2 * height);
					canvas.drawOval(draw_rect, p);
					canvas.restore();

					int old_fibb = fibb;
					fibb = fibb_n;
					fibb_n = old_fibb - fibb;

					left += width;
					full_width = full_width - width;
					width = full_width;
					height = (int) (height * ((double) fibb_n) / (double) (fibb));

					canvas.save();
					draw_rect.set(left, top, left + width, top + height);
					canvas.clipRect(draw_rect);
					canvas.drawRect(draw_rect, p);
					draw_rect.set(left - width, top, left + width, top + 2 * height);
					canvas.drawOval(draw_rect, p);
					canvas.restore();

					old_fibb = fibb;
					fibb = fibb_n;
					fibb_n = old_fibb - fibb;

					top += height;
					full_height = full_height - height;
					height = full_height;
					width = (int) (width * ((double) fibb_n) / (double) (fibb));
					left += full_width - width;

					canvas.save();
					draw_rect.set(left, top, left + width, top + height);
					canvas.clipRect(draw_rect);
					canvas.drawRect(draw_rect, p);
					draw_rect.set(left - width, top - height, left + width, top + height);
					canvas.drawOval(draw_rect, p);
					canvas.restore();

					old_fibb = fibb;
					fibb = fibb_n;
					fibb_n = old_fibb - fibb;

					full_width = full_width - width;
					width = full_width;
					left -= width;
					height = (int) (height * ((double) fibb_n) / (double) (fibb));
					top += full_height - height;

					canvas.save();
					draw_rect.set(left, top, left + width, top + height);
					canvas.clipRect(draw_rect);
					canvas.drawRect(draw_rect, p);
					draw_rect.set(left, top - height, left + 2 * width, top + height);
					canvas.drawOval(draw_rect, p);
					canvas.restore();

					old_fibb = fibb;
					fibb = fibb_n;
					fibb_n = old_fibb - fibb;

					full_height = full_height - height;
					height = full_height;
					top -= height;
					width = (int) (width * ((double) fibb_n) / (double) (fibb));
				}

				canvas.restore();

				break;
			case "preference_grid_golden_triangle_1":
			case "preference_grid_golden_triangle_2":
				double theta = Math.atan2(canvas_width, canvas_height);
				double dist = canvas_height * Math.cos(theta);
				float dist_x = (float) (dist * Math.sin(theta));
				float dist_y = (float) (dist * Math.cos(theta));
				if( pref_grid.equals("preference_grid_golden_triangle_1") ) {
					canvas.drawLine(0.0f, canvas_height - 1.0f, canvas_width - 1.0f, 0.0f, p);
					canvas.drawLine(0.0f, 0.0f, dist_x, canvas_height - dist_y, p);
					canvas.drawLine(canvas_width - 1.0f - dist_x, dist_y - 1.0f, canvas_width - 1.0f, canvas_height - 1.0f, p);
				}
				else {
					canvas.drawLine(0.0f, 0.0f, canvas_width - 1.0f, canvas_height - 1.0f, p);
					canvas.drawLine(canvas_width - 1.0f, 0.0f, canvas_width - 1.0f - dist_x, canvas_height - dist_y, p);
					canvas.drawLine(dist_x, dist_y - 1.0f, 0.0f, canvas_height - 1.0f, p);
				}
				break;
			case "preference_grid_diagonals":
				canvas.drawLine(0.0f, 0.0f, canvas_height - 1.0f, canvas_height - 1.0f, p);
				canvas.drawLine(canvas_height - 1.0f, 0.0f, 0.0f, canvas_height - 1.0f, p);
				int diff = (int)canvas_width - (int)canvas_height;
				if (diff > 0) {
					canvas.drawLine(diff, 0.0f, diff + canvas_height - 1.0f, canvas_height - 1.0f, p);
					canvas.drawLine(diff + canvas_height - 1.0f, 0.0f, diff, canvas_height - 1.0f, p);
				}
				break;
		}
		p.setAlpha(255);
		p.setStyle(Paint.Style.FILL); // reset
	}

	private void drawCropGuides(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		if( preview.isVideo() ) {
			if( camera_controller != null && preview.getTargetRatio() > 0.0 && pref_crop_guide != null ) {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(resources.getDimension(R.dimen.ind_grid_thickness));
				p.setColor(text_color_yellow);
				double crop_ratio = -1.0;
				switch(pref_crop_guide) {
					case "crop_guide_1":
						crop_ratio = 1.0;
						break;
					case "crop_guide_1.25":
						crop_ratio = 1.25;
						break;
					case "crop_guide_1.33":
						crop_ratio = 1.33333333;
						break;
					case "crop_guide_1.4":
						crop_ratio = 1.4;
						break;
					case "crop_guide_1.5":
						crop_ratio = 1.5;
						break;
					case "crop_guide_1.78":
						crop_ratio = 1.77777778;
						break;
					case "crop_guide_1.85":
						crop_ratio = 1.85;
						break;
					case "crop_guide_2.33":
						crop_ratio = 2.33333333;
						break;
					case "crop_guide_2.35":
						crop_ratio = 2.35006120; // actually 1920:817
						break;
					case "crop_guide_2.4":
						crop_ratio = 2.4;
						break;
				}
				if( crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5 ) {
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "crop_ratio: " + crop_ratio);
						Log.d(TAG, "preview_targetRatio: " + preview_targetRatio);
						Log.d(TAG, "canvas width: " + canvas.getWidth());
						Log.d(TAG, "canvas height: " + canvas.getHeight());
					}*/
					int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
					if( crop_ratio > preview.getTargetRatio() ) {
						// crop ratio is wider, so we have to crop top/bottom
						double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
						top = (canvas.getHeight()/2 - (int)new_hheight);
						bottom = (canvas.getHeight()/2 + (int)new_hheight);
					}
					else {
						// crop ratio is taller, so we have to crop left/right
						double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
						left = (canvas.getWidth()/2 - (int)new_hwidth);
						right = (canvas.getWidth()/2 + (int)new_hwidth);
					}
					canvas.drawRect(left, top, right, bottom, p);
				}
				p.setStyle(Paint.Style.FILL); // reset
			}
		}
	}

	/** Formats the level_angle double into a string.
	 *  Beware of calling this too often - shouldn't be every frame due to performance of DecimalFormat
	 *  (see http://stackoverflow.com/questions/8553672/a-faster-alternative-to-decimalformat-format ).
	 */
	public static String formatLevelAngle(double level_angle) {
		String number_string = decimalFormat.format(level_angle);
		if( Math.abs(level_angle) < 0.1 ) {
			// avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
			// only do this when level_angle is small, to help performance
			number_string = number_string.replaceAll("^-(?=0(.0*)?$)", "");
		}
		return number_string;
	}

	private void drawAngleLines(Canvas canvas) {
		Preview preview  = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		boolean has_level_angle = preview.hasLevelAngle();
		if( camera_controller != null && !preview.isPreviewPaused() && has_level_angle && ( pref_angle_line || pref_pitch_lines || pref_direction_lines ) ) {
			int ui_rotation = preview.getUIRotation();
			double level_angle = preview.getLevelAngle();
			boolean has_pitch_angle = preview.hasPitchAngle();
			double pitch_angle = preview.getPitchAngle();
			boolean has_geo_direction = preview.hasGeoDirection();
			double geo_direction = preview.getGeoDirection();
			// n.b., must draw this without the standard canvas rotation
			int radius = resources.getDimensionPixelSize(R.dimen.ind_angle_line_size);
			double angle = - preview.getOrigLevelAngle();
			// see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
			int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
			switch (rotation) {
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				angle -= 90.0;
				break;
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
			default:
				break;
			}
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + preview.getOrigLevelAngle());
				Log.d(TAG, "angle: " + angle);
			}*/
			int cx = canvas.getWidth()/2;
			int cy = canvas.getHeight()/2;

			if( is_level ) {
				radius = (int)(radius * 1.2);
			}

			canvas.save();
			canvas.rotate((float)angle, cx, cy);

			final int line_alpha = 96;
			float hthickness = resources.getDimension(R.dimen.ind_angle_line_thickness);
			p.setStyle(Paint.Style.FILL);
			if( pref_angle_line ) {
				int color_angle = text_color_default;
				if( is_level ) color_angle = pref_color_angle;
				// draw outline
				p.setColor(Color.rgb(Color.red(color_angle)/4, Color.green(color_angle)/4, Color.blue(color_angle)/4));
				p.setAlpha(64);
				// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
				draw_rect.set(cx - radius - hthickness, cy - 2 * hthickness, cx + radius + hthickness, cy + 2 * hthickness);
				canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);
				// draw the vertical crossbar
				draw_rect.set(cx - 2 * hthickness, cy - radius - hthickness, cx + 2 * hthickness, cy + radius + hthickness);
				canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
				// draw inner portion
				p.setColor(color_angle);
				p.setAlpha(line_alpha);
				draw_rect.set(cx - radius, cy - hthickness, cx + radius, cy + hthickness);
				canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

				// draw the vertical crossbar
				draw_rect.set(cx - hthickness, cy - radius, cx + hthickness, cy + radius);
				canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
			}
			float camera_angle_x = preview.getViewAngleX();
			float camera_angle_y = preview.getViewAngleY();
			float angle_scale_x = (float)( canvas.getWidth() / (2.0 * Math.tan( Math.toRadians((camera_angle_x/2.0)) )) );
			float angle_scale_y = (float)( canvas.getHeight() / (2.0 * Math.tan( Math.toRadians((camera_angle_y/2.0)) )) );

			float angle_scale = (float)Math.sqrt( angle_scale_x*angle_scale_x + angle_scale_y*angle_scale_y );
			angle_scale *= preview.getZoomRatio();

			p.setTextSize(text_size_default);
			if( has_pitch_angle && pref_pitch_lines ) {
				int pitch_radius = resources.getDimensionPixelSize((ui_rotation == 90 || ui_rotation == 270) ? R.dimen.ind_line_size_narrow : R.dimen.ind_line_size_wide);
				int angle_step = 10;
				if( preview.getZoomRatio() >= 2.0f )
					angle_step = 5;
				for(int latitude_angle=-90;latitude_angle<=90;latitude_angle+=angle_step) {
					double this_angle = pitch_angle - latitude_angle;
					if( Math.abs(this_angle) < 90.0 ) {
						float pitch_distance = angle_scale * (float)Math.tan( Math.toRadians(this_angle) ); // angle_scale is already in pixels rather than dps
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "pitch_angle: " + pitch_angle);
							Log.d(TAG, "pitch_distance_dp: " + pitch_distance_dp);
						}*/
						// draw outline
						p.setColor(Color.rgb(Color.red(text_color_default)/4, Color.green(text_color_default)/4, Color.blue(text_color_default)/4));
						p.setAlpha(64);
						// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
						draw_rect.set(cx - pitch_radius - hthickness, cy + pitch_distance - 2*hthickness, cx + pitch_radius + hthickness, cy + pitch_distance + 2*hthickness);
						canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
						// draw inner portion
						p.setColor(text_color_default);
						p.setTextAlign(Paint.Align.LEFT);
						if( latitude_angle == 0 && Math.abs(pitch_angle) < 1.0 ) {
							p.setAlpha(255);
						}
						else {
							p.setAlpha(line_alpha);
						}
						draw_rect.set(cx - pitch_radius, cy + pitch_distance - hthickness, cx + pitch_radius, cy + pitch_distance + hthickness);
						canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
						// draw pitch angle indicator
						drawText(canvas, p, "" + latitude_angle + "\u00B0", text_color_default, (int)(cx + pitch_radius + 4*hthickness), (int)(cy + pitch_distance + text_size_default/2), false);
					}
				}
			}
			if( has_geo_direction && has_pitch_angle && pref_direction_lines ) {
				int geo_radius = resources.getDimensionPixelSize((ui_rotation == 90 || ui_rotation == 270) ? R.dimen.ind_line_size_wide : R.dimen.ind_line_size_narrow);
				float geo_angle = (float)Math.toDegrees(geo_direction);
				int angle_step = 10;
				if( preview.getZoomRatio() >= 2.0f )
					angle_step = 5;
				for(int longitude_angle=0;longitude_angle<360;longitude_angle+=angle_step) {
					double this_angle = longitude_angle - geo_angle;
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "longitude_angle: " + longitude_angle);
						Log.d(TAG, "geo_angle: " + geo_angle);
						Log.d(TAG, "this_angle: " + this_angle);
					}*/
					// normalise to be in interval [0, 360)
					while( this_angle >= 360.0 )
						this_angle -= 360.0;
					while( this_angle < -360.0 )
						this_angle += 360.0;
					// pick shortest angle
					if( this_angle > 180.0 )
						this_angle = - (360.0 - this_angle);
					if( Math.abs(this_angle) < 90.0 ) {
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "this_angle is now: " + this_angle);
						}*/
						float geo_distance = angle_scale * (float)Math.tan( Math.toRadians(this_angle) ); // angle_scale is already in pixels rather than dps
						// draw outline
						p.setColor(Color.rgb(Color.red(text_color_default)/4, Color.green(text_color_default)/4, Color.blue(text_color_default)/4));
						p.setAlpha(64);
						// can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
						draw_rect.set(cx + geo_distance - 2*hthickness, cy - geo_radius - hthickness, cx + geo_distance + 2*hthickness, cy + geo_radius + hthickness);
						canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
						// draw inner portion
						p.setColor(text_color_default);
						p.setTextAlign(Paint.Align.CENTER);
						p.setAlpha(line_alpha);
						draw_rect.set(cx + geo_distance - hthickness, cy - geo_radius, cx + geo_distance + hthickness, cy + geo_radius);
						canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
						// draw geo direction angle indicator
						drawText(canvas, p, "" + longitude_angle + "\u00B0", text_color_default, (int)(cx + geo_distance), (int)(cy - geo_radius - 4*hthickness), false);
					}
				}
			}

			p.setTextAlign(Paint.Align.CENTER);
			p.setAlpha(255);
			p.setStyle(Paint.Style.FILL); // reset

			canvas.restore();
		}
	}

	public void onDrawPreview(Canvas canvas) {
		Preview preview = main_activity.getPreview();
		int ui_rotation = preview.getUIRotation();

		final int canvas_width = canvas.getWidth();
		final int canvas_height = canvas.getHeight();

		if( show_last_image && last_thumbnail != null ) {
			// If changing this code, ensure that pause preview still works when:
			// - Taking a photo in portrait or landscape - and check rotating the device while preview paused
			// - Taking a photo with lock to portrait/landscape options still shows the thumbnail with aspect ratio preserved
			p.setColor(Color.rgb(0, 0, 0)); // in case image doesn't cover the canvas (due to different aspect ratios)
			canvas.drawRect(0.0f, 0.0f, canvas_width, canvas_height, p); // in case
			last_image_src_rect.left = 0;
			last_image_src_rect.top = 0;
			last_image_src_rect.right = last_thumbnail.getWidth();
			last_image_src_rect.bottom = last_thumbnail.getHeight();
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				last_image_src_rect.right = last_thumbnail.getHeight();
				last_image_src_rect.bottom = last_thumbnail.getWidth();
			}
			last_image_dst_rect.left = 0;
			last_image_dst_rect.top = 0;
			last_image_dst_rect.right = canvas_width;
			last_image_dst_rect.bottom = canvas_height;
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "thumbnail: " + last_thumbnail.getWidth() + " x " + last_thumbnail.getHeight());
				Log.d(TAG, "canvas: " + canvas_width + " x " + canvas_height);
			}*/
			last_image_matrix.setRectToRect(last_image_src_rect, last_image_dst_rect, Matrix.ScaleToFit.CENTER); // use CENTER to preserve aspect ratio
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				// the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
				float diff = last_thumbnail.getHeight() - last_thumbnail.getWidth();
				last_image_matrix.preTranslate(diff/2.0f, -diff/2.0f);
			}
			last_image_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
			canvas.drawBitmap(last_thumbnail, last_image_matrix, p);
		}

		if( preview.isPreviewPaused() || (main_activity.getMainUI().inImmersiveMode() && pref_hide_indication)) {
			return;
		}
		
		if (update_prefs) {
			updatePrefs();
			update_prefs = false;
		}

		CameraController camera_controller = preview.getCameraController();
		boolean ui_placement_right = main_activity.getMainUI().getUIPlacementRight();
		boolean has_level_angle = preview.hasLevelAngle();
		double level_angle = preview.getLevelAngle();
		boolean has_geo_direction = preview.hasGeoDirection();
		double geo_direction = preview.getGeoDirection();

		boolean draw_multitouch_zoom = preview.supportsZoom() && preview.multitouch_zoom;

		preview.getView().getLocationOnScreen(gui_location);

		is_level = false;
		int color_angle = text_color_default;
		if( camera_controller != null && !preview.isPreviewPaused() && has_level_angle &&
				(pref_angle || pref_angle_line) && Math.abs(level_angle) <= close_level_angle) {
			is_level = true;
			color_angle = pref_color_angle;
		}

		// see documentation for CameraController.shouldCoverPreview()
		if( preview.usingCamera2API() && ( camera_controller == null || camera_controller.shouldCoverPreview() ) ) {
			p.setColor(Color.BLACK);
			canvas.drawRect(0.0f, 0.0f, canvas_width, canvas_height, p);
		}

		if( camera_controller != null && taking_picture && pref_take_photo_border ) {
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			float this_stroke_width = resources.getDimension(R.dimen.ind_take_photo_border);
			p.setStrokeWidth(this_stroke_width);
			canvas.drawRect(0.0f, 0.0f, canvas_width, canvas_height, p);
			p.setStyle(Paint.Style.FILL); // reset
			p.setStrokeWidth(stroke_width); // reset
		}
		
		if (!draw_multitouch_zoom) {
			drawGrids(canvas);
			drawCropGuides(canvas);
		}
		
		// note, no need to check preferences here, as we do that when setting thumbnail_anim
		if( camera_controller != null && this.thumbnail_anim && !(pref_photo_mode != Prefs.PhotoMode.FocusBracketing && preview.isBurst()) && last_thumbnail != null ) {
			long time = System.currentTimeMillis() - this.thumbnail_anim_start_ms;
			// FIXME!!!!!!
			final long duration = 500;
			if( time > duration ) {
				if( MyDebug.LOG )
					Log.d(TAG, "thumbnail_anim finished");
				this.thumbnail_anim = false;
			}
			else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = last_thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = last_thumbnail.getHeight();
				View galleryButton = main_activity.findViewById(R.id.gallery);
				float alpha = (float)Math.pow(((float)time)/(float)duration, 2);

				int st_x = canvas_width/2;
				int st_y = canvas_height/2;
				int nd_x = galleryButton.getLeft() + galleryButton.getWidth()/2-gui_location[0];
				int nd_y = galleryButton.getTop() + galleryButton.getHeight()/2-gui_location[1];
				int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
				int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );

				float st_w = canvas_width;
				float st_h = canvas_height;
				float nd_w = galleryButton.getWidth()-resources.getDimension(R.dimen.button_gallery_padding)*2;
				float nd_h = galleryButton.getHeight()-resources.getDimension(R.dimen.button_gallery_padding)*2;
				//int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				//int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				float correction_w = st_w/nd_w - 1.0f;
				float correction_h = st_h/nd_h - 1.0f;
				int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
				int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
				thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2;
				thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2;
				thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2;
				thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2;
				//canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				//thumbnail_anim_matrix.reset();
				if( ui_rotation == 90 || ui_rotation == 270 ) {
					float ratio = ((float)last_thumbnail.getWidth())/(float)last_thumbnail.getHeight();
					thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
				canvas.drawBitmap(last_thumbnail, thumbnail_anim_matrix, p);
				if (pref_take_photo_border) {
					p.setColor(Color.WHITE);
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(resources.getDimension(R.dimen.ind_take_photo_border)*( ((thumbnail_w+thumbnail_h)/2) / ((st_w+st_h)/2) ));
					canvas.drawRect(thumbnail_anim_dst_rect, p);
					p.setStyle(Paint.Style.FILL); // reset
					p.setStrokeWidth(stroke_width); // reset
				}
			}
		}

		if (!draw_multitouch_zoom)
			drawAngleLines(canvas);

		boolean showFocus = preview.hasFocusArea() || preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure();
		if( camera_controller != null && continuous_focus_moving && !taking_picture ) {
			// we don't display the continuous focusing animation when taking a photo - and can also ive the impression of having
			// frozen if we pause because the image saver queue is full
			if( System.currentTimeMillis() - continuous_focus_moving_ms <= 1000 ) {
				showFocus = true;
			}
			else {
				continuous_focus_moving = false;
			}
		}

		if( showFocus ) {
			long time_since_focus_started;
			if (continuous_focus_moving && continuous_focus_moving_ms > 0)
				time_since_focus_started = System.currentTimeMillis() - continuous_focus_moving_ms;
			else time_since_focus_started = preview.timeSinceStartedAutoFocus();
			float min_radius = resources.getDimension(R.dimen.ind_focus_min_radius);
			float max_radius = resources.getDimension(R.dimen.ind_focus_max_radius);
			float radius = min_radius;
			if( time_since_focus_started > 0 ) {
				final long length = 500;
				float frac = ((float)time_since_focus_started) / (float)length;
				if( frac > 1.0f )
					frac = 1.0f;
				if( frac < 0.5f ) {
					float alpha = frac*2.0f;
					if (pref_alt_indication && preview.isFocusZoneChanged())
						min_radius /= 4;
					radius = (1.0f-alpha) * min_radius + alpha * max_radius;
				}
				else {
					float alpha = (frac-0.5f)*2.0f;
					radius = (1.0f-alpha) * max_radius + alpha * min_radius;
				}
			}

			if( preview.isFocusRecentSuccess() )
				p.setColor(text_color_green);
			else if( preview.isFocusRecentFailure() )
				p.setColor(text_color_red);
			else if (preview.isFocusWaiting() || continuous_focus_moving)
				p.setColor(Color.WHITE);
			else
				p.setColor(Color.argb(64, 255, 255, 255));

			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(stroke_width);
			int pos_x = 0;
			int pos_y = 0;
			if( preview.hasFocusArea() ) {
				Pair<Integer, Integer> focus_pos = preview.getFocusPos();
				pos_x = focus_pos.first;
				pos_y = focus_pos.second;
			}
			else {
				pos_x = canvas_width / 2;
				pos_y = canvas_height / 2;
			}

			int size = (int)radius;
			float frac = 0.5f;

			if (pref_alt_indication) {
				p.setStrokeWidth(resources.getDimension(R.dimen.ind_focus_thickness));
				RectF rect = new RectF(pos_x - size, pos_y - size, pos_x + size, pos_y + size);

				canvas.drawCircle(pos_x, pos_y, radius, p);
				p.setStrokeWidth(stroke_width);
			} else {
				p.setStrokeWidth(resources.getDimension(R.dimen.ind_focus_thickness_old));
				// horizontal strokes
				canvas.drawLine(pos_x - size, pos_y - size, pos_x - frac*size, pos_y - size, p);
				canvas.drawLine(pos_x + frac*size, pos_y - size, pos_x + size, pos_y - size, p);
				canvas.drawLine(pos_x - size, pos_y + size, pos_x - frac*size, pos_y + size, p);
				canvas.drawLine(pos_x + frac*size, pos_y + size, pos_x + size, pos_y + size, p);
				// vertical strokes
				canvas.drawLine(pos_x - size, pos_y - size + stroke_width, pos_x - size, pos_y - frac*size, p);
				canvas.drawLine(pos_x - size, pos_y + frac*size, pos_x - size, pos_y + size - stroke_width, p);
				canvas.drawLine(pos_x + size, pos_y - size + stroke_width, pos_x + size, pos_y - frac*size, p);
				canvas.drawLine(pos_x + size, pos_y + frac*size, pos_x + size, pos_y + size - stroke_width, p);
				p.setStrokeWidth(stroke_width);
			}

			p.setStyle(Paint.Style.FILL); // reset
		}

		if( preview.hasMeteringArea() ) {
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(stroke_width);
			p.setColor(Color.argb(64, 255, 255, 255));
			
			Pair<Integer, Integer> pos = preview.getMeteringPos();
			int pos_x = pos.first;
			int pos_y = pos.second;

			float mul = 1;
			if (pref_alt_indication) {
				p.setStrokeWidth(resources.getDimension(R.dimen.ind_focus_thickness));
				mul = 1.2f;
			} else {
				p.setStrokeWidth(resources.getDimension(R.dimen.ind_focus_thickness_old));
			}
			float size = resources.getDimension(R.dimen.ind_metering_ring);
			float ray_start = resources.getDimension(R.dimen.ind_metering_ray);
			float ray_size = ray_start + size;
			float diag_mul = (float)(Math.sin(Math.toRadians(45)));
			float diag_ray_start = ray_start * diag_mul;
			float diag_ray_size = ray_size * diag_mul;

			canvas.drawCircle(pos_x, pos_y, size, p);

			p.setStrokeCap(Paint.Cap.ROUND);

			canvas.drawLine(pos_x - ray_start, pos_y, pos_x - ray_size, pos_y, p);
			canvas.drawLine(pos_x + ray_start, pos_y, pos_x + ray_size, pos_y, p);
			canvas.drawLine(pos_x, pos_y - ray_start, pos_x, pos_y - ray_size, p);
			canvas.drawLine(pos_x, pos_y + ray_start, pos_x, pos_y + ray_size, p);

			canvas.drawLine(pos_x - diag_ray_start, pos_y - diag_ray_start, pos_x - diag_ray_size, pos_y - diag_ray_size, p);
			canvas.drawLine(pos_x - diag_ray_start, pos_y + diag_ray_start, pos_x - diag_ray_size, pos_y + diag_ray_size, p);
			canvas.drawLine(pos_x + diag_ray_start, pos_y - diag_ray_start, pos_x + diag_ray_size, pos_y - diag_ray_size, p);
			canvas.drawLine(pos_x + diag_ray_start, pos_y + diag_ray_start, pos_x + diag_ray_size, pos_y + diag_ray_size, p);

			p.setStrokeCap(Paint.Cap.SQUARE);

			p.setStrokeWidth(stroke_width);
			p.setStyle(Paint.Style.FILL); // reset
		}

		CameraController.Face [] faces_detected = preview.getFacesDetected();
		if( faces_detected != null ) {
			p.setColor(text_color_yellow);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(resources.getDimension(pref_alt_indication ? R.dimen.ind_focus_thickness : R.dimen.ind_focus_thickness_old));
			for(CameraController.Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
				// ... but not here
//				if( face.score >= 50 ) {
					face_rect.set(face.rect);
					preview.getCameraToPreviewMatrix().mapRect(face_rect);
					/*int eye_radius = (int) (5 * scale + 0.5f); // convert dps to pixels
					int mouth_radius = (int) (10 * scale + 0.5f); // convert dps to pixels
					float [] top_left = {face.rect.left, face.rect.top};
					float [] bottom_right = {face.rect.right, face.rect.bottom};
					canvas.drawRect(top_left[0], top_left[1], bottom_right[0], bottom_right[1], p);*/
					if (pref_alt_indication) {
						canvas.drawOval(face_rect, p);
//						canvas.drawCircle(face_rect.centerX(), face_rect.centerY(), (float)(face_rect.width()*0.6), p);
					} else {
						canvas.drawRect(face_rect, p);
					}
					/*if( face.leftEye != null ) {
						float [] left_point = {face.leftEye.x, face.leftEye.y};
						cameraToPreview(left_point);
						canvas.drawCircle(left_point[0], left_point[1], eye_radius, p);
					}
					if( face.rightEye != null ) {
						float [] right_point = {face.rightEye.x, face.rightEye.y};
						cameraToPreview(right_point);
						canvas.drawCircle(right_point[0], right_point[1], eye_radius, p);
					}
					if( face.mouth != null ) {
						float [] mouth_point = {face.mouth.x, face.mouth.y};
						cameraToPreview(mouth_point);
						canvas.drawCircle(mouth_point[0], mouth_point[1], mouth_radius, p);
					}*/
//				}
			}
			p.setStyle(Paint.Style.FILL); // reset
			p.setStrokeWidth(stroke_width);
		}

		canvas.save();
		canvas.rotate(ui_rotation, canvas_width/2.0f, canvas_height/2.0f);

		int margin = resources.getDimensionPixelSize(R.dimen.ind_padding);
		int [] ind_margins = main_activity.getMainUI().getIndicationMargins();


		final int shift_direction_x = ui_rotation == 180 ? -1 : 1;
		final int shift_direction_y = ui_rotation == 90 ? -1 : 1;

		int text_base_y = canvas_height - margin;
		if( ui_rotation == ( ui_placement_right ? 0 : 180 ) ) {
			text_base_y -= ind_margins[3]-gui_location[1];
		} else if ( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
			text_base_y -= ind_margins[1]-gui_location[1];
		} else if (ui_rotation == 90 || ui_rotation == 270) {
			int max_x = canvas_height/2+canvas_width/2;
			int min_x = (int)(max_x-canvas_width+text_size_default/(half_line_div/2));
			int this_left = gui_location[0];
			int this_right = main_activity.getMainUI().getRootWidth()-canvas_width-this_left;
			text_base_y = ui_rotation == 90 ? min_x+margin-this_right : max_x+this_right-margin;
			if (ind_margins[2] > 0) {
				text_base_y -= ind_margins[2]*shift_direction_y;
			}
			if (text_base_y > max_x) text_base_y = max_x;
			else if (text_base_y < min_x) text_base_y = min_x;
		}
		if (ui_rotation == 0 || ui_rotation == 180 ) {
			if (text_base_y > canvas_height) text_base_y = canvas_height;
		}

		int margin_left = Math.max(margin+ind_margins[0]-gui_location[0], 0);
		int margin_top = Math.max(margin-gui_location[1], 0);

		int line_count = 0;
		boolean gui_classic = main_activity.getMainUI().getGUIType() == MainUI.GUIType.Classic;

		int top_y = 0;
		switch(ui_rotation) {
			case 0:
			case 180:
				top_y = (int)(text_size_default/(half_line_div/2)*shift_direction_y + margin_top);
				break;
			case 270:
				top_y = (int)((canvas_height-canvas_width)/2 + text_size_default/(half_line_div/2)*shift_direction_y + margin_left);
				break;
			case 90:
				top_y = (canvas_width+canvas_height)/2 - margin_left;
				break;
		}
		
		int left_x = 0;
		switch(ui_rotation) {
			case 0:
				left_x = margin_left;
				break;
			case 180:
				left_x = canvas_width - margin_left;
				break;
			case 270:
			case 90:
				left_x = (canvas_width-canvas_height)/2 + margin_top;
				break;
		}
		
		int right_x = 0;
		if (!gui_classic) {
			switch(ui_rotation) {
				case 0:
					right_x = Math.min(main_activity.getMainUI().getRootWidth()-gui_location[0]-ind_margins[2], canvas_width-margin);
					break;
				case 90:
				case 270:
					right_x = canvas_width/2+canvas_height/2;
					break;
				case 180:
					right_x = Math.max(canvas_width-main_activity.getMainUI().getRootWidth()+gui_location[0]+ind_margins[2], margin);
					break;
			}
			right_x -= margin*shift_direction_x;
		}

		if( pref_battery ) {
			if( !this.has_battery_frac || System.currentTimeMillis() > this.last_battery_time + 10000 ) {
				// only check periodically - unclear if checking is costly in any way
				// note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
				Intent batteryStatus = main_activity.registerReceiver(null, battery_ifilter);
				int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				has_battery_frac = true;
				battery_frac = battery_level/(float)battery_scale;
				last_battery_time = System.currentTimeMillis();
				if( MyDebug.LOG )
					Log.d(TAG, "Battery status is " + battery_level + " / " + battery_scale + " : " + battery_frac);
			}
			//battery_frac = 0.0f; // test
			int battery_x = (int)left_x;
			int battery_y = (int)(top_y);
			int battery_height = (int)(text_size_default*2);
			int battery_width = (int)(battery_height/2.2f);
			int battery_border = (int) (scale + 0.5f); // convert dps to pixels
			
			if( ui_rotation == 90 ) battery_y -= battery_height;
			else battery_y -= text_size_default/(half_line_div/2);
			
			if( ui_rotation == 180 ) battery_x -= battery_width;

			// flash icon at this low level
			if( battery_frac > 0.05f || (((long)( System.currentTimeMillis() / 1000 )) % 2) == 0) {
				float this_stroke_width = scale + 0.5f;
				p.setColor(Color.rgb(63,63,63));
				p.setStyle(Paint.Style.FILL);
				p.setAlpha(127);
				canvas.drawRect(battery_x+battery_width/4-this_stroke_width, battery_y-this_stroke_width, battery_x+battery_width-battery_width/4+this_stroke_width, battery_y+battery_width/4-this_stroke_width, p);
				canvas.drawRect(battery_x-this_stroke_width, battery_y+battery_width/4-this_stroke_width, battery_x+battery_width+this_stroke_width, battery_y+battery_height+this_stroke_width, p);

				p.setColor(Color.WHITE);
				p.setAlpha(255);
				canvas.drawRect(battery_x+battery_width/4, battery_y, battery_x+battery_width-battery_width/4, battery_y+battery_width/4, p);
				canvas.drawRect(battery_x, battery_y+battery_width/4, battery_x+battery_width, battery_y+battery_height, p);

				p.setColor(Color.BLACK);
				canvas.drawRect(battery_x+battery_border, battery_y+battery_border+battery_width/4, battery_x+battery_width-battery_border, battery_y+battery_height-battery_border, p);
				p.setColor(battery_frac > 0.15f ? text_color_green : text_color_red);
				canvas.drawRect(battery_x+battery_border, (battery_y+battery_border+battery_width/4)+(1.0f-battery_frac)*(battery_height-battery_border*2-battery_width/4), battery_x+battery_width-battery_border, battery_y+battery_height-battery_border, p);
			}
			left_x += text_size_default*1.5f*shift_direction_x;
		}

		// set up text etc for the multiple lines of "info" (time, free mem, etc)
		p.setTextSize(text_size_default);
		p.setAlpha(255);
		if( ui_rotation == 180 ) p.setTextAlign(Paint.Align.RIGHT);
		else p.setTextAlign(Paint.Align.LEFT);
		int location_x = (int) left_x;
		int location_y = top_y;
		final int diff_y = (int) (text_size_default *line_height_small);

		if( pref_time ) {
			// avoid creating a new calendar object every time
			if( calendar == null )
				calendar = Calendar.getInstance();
			else
				calendar.setTimeInMillis(System.currentTimeMillis());
			// n.b., DateFormat.getTimeInstance() ignores user preferences such as 12/24 hour or date format, but this is an Android bug.
			// Whilst DateUtils.formatDateTime doesn't have that problem, it doesn't print out seconds! See:
			// http://stackoverflow.com/questions/15981516/simpledateformat-gettimeinstance-ignores-24-hour-format
			// http://daniel-codes.blogspot.co.uk/2013/06/how-to-correctly-format-datetime.html
			// http://code.google.com/p/android/issues/detail?id=42104
			// also possibly related https://code.google.com/p/android/issues/detail?id=181201
			String current_time = dateFormatTimeInstance.format(calendar.getTime());
			//String current_time = DateUtils.formatDateTime(getContext(), c.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
			drawText(canvas, p, current_time, text_color_default, location_x, location_y, true);

			location_y += diff_y*shift_direction_y;
			line_count++;
		}

		if( pref_free_memory ) {
			long time_now = System.currentTimeMillis();
			if( last_free_memory_time == 0 || time_now > last_free_memory_time + 5000 ) {
				// don't call this too often, for UI performance
				long free_mb = main_activity.freeMemory();
				if( free_mb >= 0 ) {
					free_memory_gb = free_mb/1024.0f;
				} else free_memory_gb = 0;
				last_free_memory_time = time_now; // always set this, so that in case of free memory not being available, we aren't calling freeMemory() every frame
			}
			if (icon_font != null) {
				boolean low_memory = free_memory_gb < (pref_is_video ? 200 : 20) / 1024.0f;
				p.setTypeface(icon_font);
				if (free_memory_gb >= (pref_is_video ? 50 : 5) / 1024.0f || ((int)(System.currentTimeMillis() / 500)) % 2 == 0)
					drawText(canvas, p, low_memory ? "S" : "M", low_memory ? text_color_red : text_color_default, location_x, (int)(location_y+text_size_default/7), true);
				p.setTypeface(default_font);
				drawText(canvas, p, decimalFormat.format(free_memory_gb) + resources.getString(R.string.gb_abbreviation),
					text_color_default, (int)(location_x+text_size_default*1.2f*shift_direction_x), location_y, true);
			} else {
				drawText(canvas, p, resources.getString(R.string.free_memory) + ": " + decimalFormat.format(free_memory_gb) + resources.getString(R.string.gb_abbreviation),
					text_color_default, location_x, location_y, true);
			}

			location_y += diff_y*shift_direction_y;
			line_count++;
			if (line_count == 2 && pref_battery) location_x -= text_size_default*1.5f*shift_direction_x;
		}
		
		long exposure_time = 0;
		if( camera_controller != null && pref_iso ) {
			String string = "";
			int iso = camera_controller.getIso();
			if( iso > 0 ) {
				if( string.length() > 0 )
					string += " ";
				string += preview.getISOString(iso);
			} else if (!pref_iso_value.equals("auto")) {
				string += resources.getString(R.string.iso) + ": " + pref_iso_value.replaceAll("ISO", "");
			}
			exposure_time = camera_controller.getExposureTime();
			if( exposure_time > 0 ) {
				if( string.length() > 0 )
					string += " ";
				string += preview.getExposureTimeString(exposure_time);
			}
			/*if( camera_controller.captureResultHasFrameDuration() ) {
				long frame_duration = camera_controller.captureResultFrameDuration();
				if( string.length() > 0 )
					string += " ";
				string += preview.getFrameDurationString(frame_duration);
			}*/
			if( string.length() > 0 ) {
				boolean is_scanning = false;
				if( camera_controller.captureResultIsAEScanning() && pref_iso_value.equals("auto") ) {
					// only show as scanning if in auto ISO mode (problem on Nexus 6 at least that if we're in manual ISO mode, after pausing and
					// resuming, the camera driver continually reports CONTROL_AE_STATE_SEARCHING)
					is_scanning = true;
				}

				int text_color = text_color_yellow;
				if( is_scanning ) {
					// we only change the color if ae scanning is at least a certain time, otherwise we get a lot of flickering of the color
					if( ae_started_scanning_ms == -1 ) {
						ae_started_scanning_ms = System.currentTimeMillis();
					}
					else if( System.currentTimeMillis() - ae_started_scanning_ms > 500 ) {
						text_color = text_color_red;
					}
				}
				else {
					ae_started_scanning_ms = -1;
				}
				if (exposure_time > 0 && !pref_iso_value.equals("auto") && camera_controller.isExposureOverRange())
					text_color = text_color_red;

				drawText(canvas, p, string, text_color, location_x, location_y, true);

				// only move location_y if we actually print something (because on old camera API, even if the ISO option has
				// been enabled, we'll never be able to display the on-screen ISO)
				location_y += diff_y*shift_direction_y;
				line_count++;
				if (line_count == 2 && pref_battery) location_x -= text_size_default*1.5f*shift_direction_x;
			}
		}
/*
		if( camera_controller != null && pref_white_balance && camera_controller.captureResultHasWhiteBalanceTemperature() ) {
			drawText(canvas, p, "WB " + camera_controller.captureResultWhiteBalanceTemperature()+"K", text_color_yellow, location_x, location_y, true);
			location_y += diff_y*shift_direction_y;
			line_count++;
			if (line_count == 2 && pref_battery) location_x -= text_size_default*1.5f*shift_direction_x;
		}
*/
		if(icon_font != null) {
			float icon_step = text_size_icon*1.2f;
			location_y += (text_size_icon-text_size_default)*2*shift_direction_y;
			p.setTextSize(text_size_icon);
			p.setTypeface(icon_font);
			if( pref_location ) {
				String icon = IconView.LOCATION_UNKNOWN;
				int color = text_color_default;
				if( applicationInterface.getLocation() != null ) {
					icon = IconView.LOCATION;
					color = applicationInterface.getLocation().getAccuracy() < 25.01f ? text_color_green : text_color_yellow;
				}
				drawText(canvas, p, icon, color, location_x, location_y, true);
				location_x += icon_step*shift_direction_x;
			}

			// RAW not enabled in HDR or ExpoBracketing modes (see note in CameraController.takePictureBurstExpoBracketing())
			if( pref_raw ) {
				drawText(canvas, p, IconView.RAW, text_color_default, location_x, location_y, true);
				location_x += icon_step*shift_direction_x;
			}

			if( pref_auto_stabilise && pref_photo_mode != Prefs.PhotoMode.FastBurst && !pref_is_video ) {
				drawText(canvas, p, IconView.AUTO_LEVEL, text_color_default, location_x, location_y, true);
				location_x += icon_step*shift_direction_x;
			}

			if( pref_face_detection ) {
				drawText(canvas, p, IconView.FACE, text_color_default, location_x, location_y, true);
				location_x += icon_step*shift_direction_x;
			}

			if( pref_photo_mode != Prefs.PhotoMode.Standard && !pref_ctrl_panel_photo_mode && !pref_is_video ) {
				String icon = null;
				if (pref_photo_mode == Prefs.PhotoMode.DRO) icon = IconView.DRO;
				else if (pref_photo_mode == Prefs.PhotoMode.HDR) icon = IconView.HDR;
				else if (pref_photo_mode == Prefs.PhotoMode.ExpoBracketing) icon = IconView.EXPO_BRACKETING;
				else if (pref_photo_mode == Prefs.PhotoMode.FocusBracketing) icon = IconView.FOCUS_BRACKETING;
				else if (pref_photo_mode == Prefs.PhotoMode.FastBurst) icon = IconView.BURST;
				else if (pref_photo_mode == Prefs.PhotoMode.NoiseReduction) icon = IconView.NOISE_REDUCTION;
				if (icon != null) {
					drawText(canvas, p, icon, text_color_default, location_x, location_y, true);
					location_x += icon_step*shift_direction_x;
				}
			}

			if( pref_stamp && !pref_is_video ) {
				drawText(canvas, p, IconView.STAMP, text_color_default, location_x, location_y, true);
				location_x += icon_step*shift_direction_x;
			}

			String flash_value = preview.getCurrentFlashValue();
			if (camera_controller != null && flash_value != null && (!pref_ctrl_panel_flash || flash_value.equals("auto"))) {
				// note, flash_frontscreen_auto not yet support for the flash symbol (as camera_controller.needsFlash() only returns info on the built-in actual flash, not frontscreen flash)
				String icon = null;
				switch(flash_value) {
					case "flash_on":
					case "flash_frontscreen_on":
						icon = IconView.FLASH_ON;
						break;
					case "flash_red_eye":
						icon = IconView.FLASH_RED_EYE;
						break;
					case "flash_auto":
					case "flash_frontscreen_auto":
						icon = IconView.FLASH_AUTO;
						if (camera_controller.canReportNeedsFlash() && !camera_controller.needsFlash()) {
							p.setAlpha(63);
						}
						break;
					case "flash_torch":
						icon = IconView.FLASH_TORCH;
						break;
				}
				if (icon != null) {
					drawText(canvas, p, icon, text_color_default, location_x, location_y, true);
					location_x += icon_step*shift_direction_x;
				}
			}
			p.setAlpha(255);
			p.setTypeface(default_font);
			p.setTextSize(text_size_default);
		}

		if( camera_controller != null ) {
			if( draw_multitouch_zoom ) {
				float outer_radius = Math.min(canvas_width, canvas_height)/2.75f;
				float pos_x = canvas_width/2.0f;
				float pos_y = canvas_height/2.0f;
				float this_stroke_width = resources.getDimension(R.dimen.ind_multitouch_zoom_thickness);
				float this_stroke_half_width = this_stroke_width/2;
				p.setColor(Color.WHITE);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(this_stroke_width);
				p.setAlpha(127);
				canvas.drawCircle(pos_x, pos_y, outer_radius-this_stroke_half_width, p);
				canvas.drawCircle(pos_x, pos_y, outer_radius/preview.getMaxZoomRatio()-this_stroke_half_width, p);
				p.setAlpha(255);
				canvas.drawCircle(pos_x, pos_y, outer_radius/(preview.getMaxZoomRatio()/preview.getZoomRatio())-this_stroke_half_width, p);
				p.setStrokeWidth(stroke_width);
				p.setStyle(Paint.Style.FILL); // reset

				float size = text_size_video/(preview.getMaxZoomRatio() >= 3 ? 1.5f : 1);
				p.setTextSize(size);
				p.setTextAlign(Paint.Align.CENTER);
				drawText(canvas, p, (float)(Math.round(preview.getZoomRatio()*10))/10 +"x", text_color_default, (int)pos_x, (int)(pos_y+size / half_line_div), false);
			}
			if (!preview.isPreviewPaused()) {
				/*canvas.drawText("PREVIEW", canvas_width / 2,
						canvas_height / 2, p);*/
				boolean draw_angle = has_level_angle && pref_angle;
				boolean draw_geo_direction = has_geo_direction && pref_direction;
				int x = right_x;
				int y;
				int shift_y;
				if (gui_classic) {
					y = text_base_y;
					shift_y = - (int)(text_size_default*line_height)*shift_direction_y;
				} else if (
					pref_mode_panel &&
					(ui_rotation == 0 || ui_rotation == 180) && 
					(main_activity.getMainUI().getGUIType() == MainUI.GUIType.Phone2 || main_activity.getMainUI().getGUIType() == MainUI.GUIType.Universal)
				) {
					p.setTextAlign(ui_rotation == 0 ? Paint.Align.LEFT : Paint.Align.RIGHT);
					x = ui_rotation == 0 ? margin_left : canvas_width - margin_left;
					y = canvas_height-margin_top;
					shift_y = -(diff_y*shift_direction_y);
				} else {
					p.setTextAlign(ui_rotation == 180 ? Paint.Align.LEFT : Paint.Align.RIGHT);
					y = top_y;
					shift_y = diff_y*shift_direction_y;
				}
				
				line_count = 0;

				if( MyDebug.LOG ) {
					p.setTextSize(text_size_default);
					p.setFakeBoldText(true);
					if (gui_classic) {
						p.setTextAlign(Paint.Align.CENTER);
						x = canvas_width / 2;
					}
					drawText(canvas, p, "DEBUG", text_color_red, x, y+shift_y*line_count, false);
					p.setFakeBoldText(false);
					line_count++;
				}

				if( draw_geo_direction ) {
					int color = Color.WHITE;
					p.setTextSize(text_size_default);
					if (gui_classic) {
						if( draw_angle ) {
							p.setTextAlign(Paint.Align.LEFT);
						}
						else {
							p.setTextAlign(Paint.Align.CENTER);
						}
						x = (int)(canvas_width / 2 - (20 * scale + 0.5f));
					}
					float geo_angle = (float)Math.toDegrees(geo_direction);
					if( geo_angle < 0.0f ) {
						geo_angle += 360.0f;
					}
					String string = resources.getString(R.string.direction) + ": " + Math.round(geo_angle) + (char)0x00B0;
					drawText(canvas, p, string, text_color_default, x, y+shift_y*line_count, false);
					
					if (!gui_classic) line_count++;
				}

				if( draw_angle ) {
					p.setTextSize(text_size_default);
					int pixels_offset_x = 0;
					if (gui_classic) {
						x = canvas_width / 2;
						if( draw_geo_direction ) {
							x -= (int)(text_size_default*7);
							p.setTextAlign(Paint.Align.LEFT);
						}
						else {
							p.setTextAlign(Paint.Align.CENTER);
						}
					}

					String angle_string = resources.getString(R.string.angle) + ": " + formatLevelAngle(level_angle) + (char)0x00B0;

					drawText(canvas, p, angle_string, color_angle, x, y+shift_y*line_count, false);

					if (!gui_classic) line_count++;
				}

				if (gui_classic && (draw_angle || draw_geo_direction)) line_count++;

				if( preview.supportsZoom() && pref_zoom && !draw_multitouch_zoom ) {
					float zoom_ratio = preview.getZoomRatio();
					// only show when actually zoomed in
					if( zoom_ratio > 1.0f + 1.0e-5f ) {
						// Convert the dps to pixels, based on density scale
						p.setTextSize(text_size_default);
						if (gui_classic) {
							p.setTextAlign(Paint.Align.CENTER);
							x = canvas_width / 2;
						}
						drawText(canvas, p, resources.getString(R.string.zoom) + ": " + zoom_ratio +"x", text_color_default, x, y+shift_y*line_count, false);
						if (!gui_classic) line_count++;
					}
				}
				if( preview.isWaitingFace() && ((int)(System.currentTimeMillis() / 500)) % 2 == 0 ) {
					p.setTextAlign(Paint.Align.CENTER);
					float text_size = (ui_rotation == 0 || ui_rotation == 180) ? (float)(text_size_default*1.2) : text_size_default;
					p.setTextSize(text_size);
					drawText(canvas, p, resources.getString(R.string.waiting_for_face), text_color_red, canvas_width / 2, (int)(canvas_height / 2 + text_size / half_line_div), false);
				}
				if( preview.showTimer() || preview.isBurst() ) {
					float half_line = text_size_timer / half_line_div;
					p.setTextAlign(Paint.Align.CENTER);
					if (preview.showTimer()) {
						long remaining_time = (preview.getTimerEndTime() - System.currentTimeMillis() + 999)/1000;
						if( MyDebug.LOG )
							Log.d(TAG, "remaining_time: " + remaining_time);
						p.setTextSize(text_size_timer);
						if( remaining_time > 0 ) {
							String time_s = "";
							if( remaining_time < 60 ) {
								// simpler to just show seconds when less than a minute
								time_s = "" + remaining_time;
							}
							else {
								time_s = getTimeStringFromSeconds(remaining_time);
							}
							drawText(canvas, p, time_s, text_color_red, canvas_width / 2, canvas_height / 2 + (int)half_line, false);
						}
					}
					if( main_activity.isScreenLocked() ) {
						p.setTextSize(text_size_default);
						drawText(canvas, p, resources.getString(R.string.screen_lock_message_1), text_color_red, canvas_width / 2, (int)(canvas_height / 2 + half_line + (text_size_default*line_height)), false);
						drawText(canvas, p, resources.getString(R.string.screen_lock_message_2), text_color_red, canvas_width / 2, (int)(canvas_height / 2 + half_line + (text_size_default*line_height*2)), false);
					}
				}
				else if( preview.isVideoRecording() ) {
					long video_time = preview.getVideoTime();
					String time_s = getTimeStringFromSeconds(video_time/1000);
					/*if( MyDebug.LOG )
						Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
					p.setTextSize(text_size_default);
					p.setTextAlign(Paint.Align.CENTER);
					int pixels_offset_y = gui_classic ? (int)(2*text_size_default*line_height)*shift_direction_y : 0; // avoid overwriting the zoom, and also allow a bit extra space
					if( ui_rotation == 90 ) pixels_offset_y -= (text_size_video-text_size_default)/(half_line_div/2);
					if( main_activity.isScreenLocked() ) {
						// writing in reverse order, bottom to top
						drawText(canvas, p, resources.getString(R.string.screen_lock_message_2), text_color_red, canvas_width / 2, text_base_y - pixels_offset_y, false);
						pixels_offset_y += text_size_default*line_height;
						drawText(canvas, p, resources.getString(R.string.screen_lock_message_1), text_color_red, canvas_width / 2, text_base_y - pixels_offset_y, false);
						pixels_offset_y += text_size_default*line_height;
					}
					if( !preview.isVideoRecordingPaused() || ((int)(System.currentTimeMillis() / 500)) % 2 == 0 ) { // if video is paused, then flash the video time
						p.setTextSize(text_size_video);
						drawText(canvas, p, time_s, text_color_red, canvas_width / 2, text_base_y - pixels_offset_y, false);
					}
				}
				else if( taking_picture && capture_started ) {
					if( camera_controller.isManualExposure() ) {
						// only show "capturing" text with time for manual exposure time >= 0.5s
						if (exposure_time == 0) exposure_time = camera_controller.getExposureTime();
						if( exposure_time >= 500000000L ) {
							if( ((int)(System.currentTimeMillis() / 500)) % 2 == 0 ) {
								p.setTextSize(text_size_video);
								p.setTextAlign(Paint.Align.CENTER);
								drawText(canvas, p, resources.getString(R.string.capturing), text_color_red, canvas_width / 2, (int)(canvas_height / 2 + text_size_video / half_line_div), false);
							}
						}
					}
				}
			}
		} else {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas_width + " height " + canvas_height);
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(text_size_default);
			p.setTextAlign(Paint.Align.CENTER);
			if( preview.hasPermissions() ) {
				if( preview.openCameraFailed() ) {
					drawText(canvas, p, resources.getString(R.string.failed_to_open_camera_1), text_color_default, canvas_width / 2, canvas_height / 2, false);
					drawText(canvas, p, resources.getString(R.string.failed_to_open_camera_2), text_color_default, canvas_width / 2, (int)(canvas_height / 2 + text_size_default*line_height), false);
					drawText(canvas, p, resources.getString(R.string.failed_to_open_camera_3), text_color_default, canvas_width / 2, (int)(canvas_height / 2 + 2*text_size_default*line_height), false);
				}
			}
			else {
				drawText(canvas, p, resources.getString(R.string.no_permission), text_color_default, canvas_width / 2, canvas_height / 2, false);
			}
		}

/*		if( enable_gyro_target_spot ) {
			GyroSensor gyroSensor = main_activity.getApplicationInterface().getGyroSensor();
			if( gyroSensor.isRecording() ) {
				gyroSensor.getRelativeInverseVector(transformed_gyro_direction, gyro_direction);
				// note that although X of gyro_direction represents left to right on the device, because we're in landscape mode,
				// this is y coordinates on the screen
				float angle_x = - (float)Math.asin(transformed_gyro_direction[1]);
				float angle_y = - (float)Math.asin(transformed_gyro_direction[0]);
				if( Math.abs(angle_x) < 0.5f*Math.PI && Math.abs(angle_y) < 0.5f*Math.PI ) {
					float camera_angle_x = preview.getViewAngleX();
					float camera_angle_y = preview.getViewAngleY();
					float angle_scale_x = (float) (canvas_width / (2.0 * Math.tan(Math.toRadians((camera_angle_x / 2.0)))));
					float angle_scale_y = (float) (canvas_height / (2.0 * Math.tan(Math.toRadians((camera_angle_y / 2.0)))));
					angle_scale_x *= preview.getZoomRatio();
					angle_scale_y *= preview.getZoomRatio();
					float distance_x = angle_scale_x * (float) Math.tan(angle_x); // angle_scale is already in pixels rather than dps
					float distance_y = angle_scale_y * (float) Math.tan(angle_y); // angle_scale is already in pixels rather than dps
					p.setColor(Color.WHITE);
					drawGyroSpot(canvas, 0.0f, 0.0f); // draw spot for the centre of the screen, to help the user orient the device
					p.setColor(Color.BLUE);
					drawGyroSpot(canvas, distance_x, distance_y);
				}
			}
		}*/
	}

/*	private void drawGyroSpot(Canvas canvas, float distance_x, float distance_y) {
		p.setAlpha(64);
		float radius = (45 * scale + 0.5f); // convert dps to pixels
		canvas.drawCircle(canvas.getWidth()/2.0f + distance_x, canvas.getHeight()/2.0f + distance_y, radius, p);
		p.setAlpha(255);
	}*/

	private void drawText(final Canvas canvas, final Paint paint, final String text, final int foreground, final int location_x, int location_y, boolean align_top) {
		final float scale = resources.getDisplayMetrics().density;
		final int alpha = paint.getAlpha();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.rgb(Color.red(foreground)/4, Color.green(foreground)/4, Color.blue(foreground)/4));
		paint.setAlpha(alpha/2);
		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setStrokeWidth(resources.getDimension(R.dimen.ind_text_stroke));
		canvas.drawText(text, location_x, location_y, paint);
		paint.setStyle(Paint.Style.FILL);
		paint.setStrokeWidth(0);
		paint.setColor(foreground);
		paint.setAlpha(alpha);
		canvas.drawText(text, location_x, location_y, paint);
	}
}
