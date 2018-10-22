package com.caddish_hedgehog.hedgecam2;

import java.io.File;

import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;
import android.os.Environment;


public class Prefs {
	private static final String TAG = "HedgeCam/Prefs";
	private static SharedPreferences sharedPreferences;
	private static MainActivity main_activity;

	public static final String DONE_FIRST_TIME = "done_first_time";
	public static final String DONE_AUTO_STABILISE_INFO = "done_auto_stabilise_info";
	public static final String DONE_VIDEO_STABILIZATION_INFO = "done_video_stabilization_info";
	public static final String DONE_HDR_INFO = "done_hdr_info";
	public static final String DONE_RAW_INFO = "done_raw_info";
	public static final String CAMERA_ID = "camera_id";
	public static final String USE_CAMERA2 = "preference_use_camera2";
	public static final String PREVIEW_SURFACE = "preference_preview_surface";
	public static final String IS_VIDEO = "is_video";
	public static final String SHOW_SEEKBARS = "show_seekbars";
	public static final String EXPOSURE = "preference_exposure";
	public static final String COLOR_EFFECT = "preference_color_effect";
	public static final String SCENE_MODE = "preference_scene_mode";
	public static final String WHITE_BALANCE = "preference_white_balance";
	public static final String WHITE_BALANCE_TEMPERATURE = "preference_white_balance_temperature";
	public static final String ISO = "preference_iso";
	public static final String MANUAL_ISO = "preference_manual_iso";
	public static final String EXPOSURE_TIME = "preference_exposure_time";
	public static final String RAW = "preference_raw";
	public static final String EXPO_BRACKETING_N_IMAGES = "preference_expo_bracketing_n_images";
	public static final String EXPO_BRACKETING_STOPS_UP = "preference_expo_bracketing_stops_up";
	public static final String EXPO_BRACKETING_STOPS_DOWN = "preference_expo_bracketing_stops";
	public static final String EXPO_BRACKETING_USE_ISO = "preference_expo_bracketing_use_iso";
	public static final String EXPO_BRACKETING_DELAY = "preference_expo_bracketing_delay";
	public static final String VOLUME_KEYS = "preference_volume_keys";
	public static final String AUDIO_CONTROL = "preference_audio_control";
	public static final String AUDIO_NOISE_CONTROL_SENSITIVITY = "preference_audio_noise_control_sensitivity";
	public static final String QUALITY = "preference_quality";
	public static final String AUTO_STABILISE = "preference_auto_stabilise";
	public static final String PHOTO_MODE = "preference_photo_mode";
	public static final String HDR_SAVE_EXPO = "preference_hdr_save_expo";
	public static final String LOCATION = "preference_location";
	public static final String GPS_DIRECTION = "preference_gps_direction";
	public static final String REQUIRE_LOCATION = "preference_require_location";
	public static final String STAMP = "preference_stamp";
	public static final String STAMP_LOCATION_X = "preference_stamp_location_x";
	public static final String STAMP_LOCATION_Y = "preference_stamp_location_y";
	public static final String STAMP_DATEFORMAT = "preference_stamp_dateformat";
	public static final String STAMP_TIMEFORMAT = "preference_stamp_timeformat";
	public static final String STAMP_GPSFORMAT = "preference_stamp_gpsformat";
	public static final String TEXTSTAMP = "preference_textstamp";
	public static final String STAMP_FONTSIZE = "preference_stamp_fontsize";
	public static final String STAMP_FONT_COLOR = "preference_stamp_font_color";
	public static final String STAMP_BACKGROUND = "preference_stamp_background";
	public static final String VIDEO_SUBTITLE = "preference_video_subtitle";
	public static final String BACKGROUND_PHOTO_SAVING = "preference_background_photo_saving";
	public static final String CAMERA2_FAKE_FLASH = "preference_camera2_fake_flash";
	public static final String CAMERA2_FAST_BURST = "preference_camera2_fast_burst";
	public static final String UI_LEFT_HANDED = "preference_ui_left_handed";
	public static final String TOUCH_CAPTURE = "preference_touch_capture";
	public static final String PAUSE_PREVIEW = "preference_pause_preview";
	public static final String SHOW_TOASTS = "preference_show_toasts";
	public static final String THUMBNAIL_ANIMATION = "preference_thumbnail_animation";
	public static final String TAKE_PHOTO_BORDER = "preference_take_photo_border";
	public static final String SHOW_WHEN_LOCKED = "preference_show_when_locked";
	public static final String STARTUP_FOCUS = "preference_startup_focus";
	public static final String KEEP_DISPLAY_ON = "preference_keep_display_on";
	public static final String MAX_BRIGHTNESS = "preference_max_brightness";
	public static final String GUI_ORIENTATION = "preference_gui_orientation";
	public static final String USING_SAF = "preference_using_saf";
	public static final String SAVE_LOCATION = "preference_save_location";
	public static final String SAVE_LOCATION_SAF = "preference_save_location_saf";
	public static final String SAVE_PHOTO_PREFIX = "preference_save_photo_prefix";
	public static final String SAVE_VIDEO_PREFIX = "preference_save_video_prefix";
	public static final String SAVE_ZULU_TIME = "preference_save_zulu_time";
	public static final String OSD_FONT_SIZE = "preference_osd_font_size";
	public static final String SHOW_ZOOM_CONTROLS = "preference_show_zoom_controls";
	public static final String SHOW_ZOOM_SLIDER_CONTROLS = "preference_show_zoom_slider_controls";
	public static final String SHOW_TAKE_PHOTO = "preference_show_take_photo";
	public static final String SHOW_ZOOM = "preference_show_zoom";
	public static final String SHOW_ISO = "preference_show_iso";
	public static final String SHOW_WHITE_BALANCE = "preference_show_white_balance";
	public static final String SHOW_ANGLE = "preference_show_angle";
	public static final String SHOW_ANGLE_LINE = "preference_show_angle_line";
	public static final String SHOW_PITCH_LINES = "preference_show_pitch_lines";
	public static final String SHOW_GEO_DIRECTION_LINES = "preference_show_geo_direction_lines";
	public static final String ANGLE_HIGHLIGHT_COLOR = "preference_angle_highlight_color";
	public static final String CALIBRATED_LEVEL_ANGLE = "preference_calibrated_level_angle";
	public static final String SHOW_GEO_DIRECTION = "preference_show_geo_direction";
	public static final String FREE_MEMORY = "preference_free_memory";
	public static final String SHOW_TIME = "preference_show_time";
	public static final String SHOW_BATTERY = "preference_show_battery";
	public static final String GRID = "preference_grid";
	public static final String GRID_ALPHA = "preference_grid_alpha";
	public static final String CROP_GUIDE = "preference_crop_guide";
	public static final String FACE_DETECTION = "preference_face_detection";
	public static final String VIDEO_STABILIZATION = "preference_video_stabilization";
	public static final String FORCE_VIDEO_4K = "preference_force_video_4k";
	public static final String VIDEO_BITRATE = "preference_video_bitrate";
	public static final String VIDEO_FPS = "preference_video_fps";
	public static final String VIDEO_MAX_DURATION = "preference_video_max_duration";
	public static final String VIDEO_RESTART = "preference_video_restart";
	public static final String VIDEO_MAX_FILESIZE = "preference_video_max_filesize";
	public static final String VIDEO_RESTART_MAX_FILESIZE = "preference_video_restart_max_filesize";
	public static final String VIDEO_FLASH = "preference_video_flash";
	public static final String VIDEO_LOW_POWER_CHECK = "preference_video_low_power_check";
	public static final String LOCK_VIDEO = "preference_lock_video";
	public static final String RECORD_AUDIO = "preference_record_audio";
	public static final String RECORD_AUDIO_CHANNELS = "preference_record_audio_channels";
	public static final String RECORD_AUDIO_SRC = "preference_record_audio_src";
	public static final String PREVIEW_MAX_SIZE = "preference_preview_max_size";
	public static final String ROTATE_PREVIEW = "preference_rotate_preview";
	public static final String LOCK_ORIENTATION = "preference_lock_orientation";
	public static final String SELFIE_MODE = "selfie_mode";
	public static final String TIMER = "preference_timer";
	public static final String TIMER_BEEP = "preference_timer_beep";
	public static final String TIMER_SPEAK = "preference_timer_speak";
	public static final String TIMER_START_SOUND = "preference_timer_start_sound";
	public static final String BURST_MODE = "preference_burst_mode";
	public static final String BURST_INTERVAL = "preference_burst_interval";
	public static final String BURST_LOW_BRIGHTNESS = "preference_burst_low_brightness";
	public static final String BURST_LOCK = "preference_burst_lock";
	public static final String WAIT_FACE = "preference_wait_face";
	public static final String SHUTTER_SOUND = "preference_shutter_sound";
	public static final String SHUTTER_SOUND_SELECT = "preference_shutter_sound_select";
	public static final String FACE_DETECTION_SOUND = "preference_face_detection_sound";
	public static final String SOUND_VOLUME = "preference_sound_volume";
	public static final String AUDIO_STREAM = "preference_audio_stream";
	public static final String GUI_TYPE = "preference_gui_type";
	public static final String GUI_TYPE_PORTRAIT = "preference_gui_type_portrait";
	public static final String IMMERSIVE_MODE = "preference_immersive_mode";
	public static final String CTRL_PANEL_MARGIN = "preference_ctrl_panel_margin";
	public static final String SHUTTER_BUTTON_SIZE = "preference_shutter_button_size";
	public static final String SHUTTER_BUTTON_STYLE = "preference_shutter_button_style";
	
	public static final String HDR_TONEMAPPING = "preference_hdr_tonemapping";
	public static final String HDR_LOCAL_CONTRAST = "preference_hdr_local_contrast";
	public static final String HDR_N_TILES = "preference_hdr_n_tiles";
	public static final String HDR_IGNORE_SMART_FILTER = "preference_hdr_ignore_sf";
	public static final String HDR_STOPS_UP = "preference_hdr_stops_up";
	public static final String HDR_STOPS_DOWN = "preference_hdr_stops";

	public static final String PREVIEW_LOCATION = "preference_preview_location";
	public static final String PREVIEW_MAX_EXPO = "preference_preview_max_expo";
	public static final String CTRL_PANEL_SELFIE_MODE = "preference_ctrl_panel_selfie_mode";
	public static final String CTRL_PANEL_FACE_DETECTION = "preference_ctrl_panel_face_detection";
	public static final String CTRL_PANEL_PHOTO_MODE = "preference_ctrl_panel_photo_mode";
	public static final String CTRL_PANEL_SWITCH_CAMERA = "preference_ctrl_panel_switch_camera";
	public static final String CTRL_PANEL_EXPOSURE = "preference_ctrl_panel_exposure";
	public static final String CTRL_PANEL_LOCK = "preference_ctrl_panel_lock";
	public static final String CTRL_PANEL_EXPO_METERING_AREA = "preference_ctrl_panel_expo_metering_area";
	public static final String CTRL_PANEL_WHITE_BALANCE = "preference_ctrl_panel_white_balance";
	public static final String CTRL_PANEL_SCENE_MODE = "preference_ctrl_panel_scene_mode";
	public static final String CTRL_PANEL_COLOR_EFFECT = "preference_ctrl_panel_color_effect";
	public static final String CTRL_PANEL_ISO = "preference_ctrl_panel_iso";
	public static final String CTRL_PANEL_FLASH = "preference_ctrl_panel_flash";
	public static final String CTRL_PANEL_FOCUS = "preference_ctrl_panel_focus";
	public static final String CTRL_PANEL_POPUP = "preference_ctrl_panel_popup";
	
	public static final String SHOW_MODE_PANEL = "preference_show_mode_panel";
	public static final String MODE_PANEL_SELFIE_MODE = "preference_mode_panel_selfie_mode";
	public static final String MODE_PANEL_FACE_DETECTION = "preference_mode_panel_face_detection";
	public static final String MODE_PANEL_PHOTO_MODE = "preference_mode_panel_photo_mode";
	public static final String MODE_PANEL_SWITCH_CAMERA = "preference_mode_panel_switch_camera";
	public static final String MODE_PANEL_EXPOSURE = "preference_mode_panel_exposure";
	public static final String MODE_PANEL_LOCK = "preference_mode_panel_lock";
	public static final String MODE_PANEL_EXPO_METERING_AREA = "preference_mode_panel_expo_metering_area";
	public static final String MODE_PANEL_WHITE_BALANCE = "preference_mode_panel_white_balance";
	public static final String MODE_PANEL_SCENE_MODE = "preference_mode_panel_scene_mode";
	public static final String MODE_PANEL_COLOR_EFFECT = "preference_mode_panel_color_effect";
	public static final String MODE_PANEL_ISO = "preference_mode_panel_iso";
	public static final String MODE_PANEL_FLASH = "preference_mode_panel_flash";
	public static final String MODE_PANEL_FOCUS = "preference_mode_panel_focus";
	public static final String MODE_PANEL_POPUP = "preference_mode_panel_popup";
	public static final String MODE_PANEL_SETTINGS = "preference_mode_panel_settings";

	public static final String CTRL_PANEL_SETTINGS = "preference_ctrl_panel_settings";
	public static final String FLIP_FRONT_FACING = "preference_flip_front_facing";
	public static final String MULTITOUCH_ZOOM = "preference_multitouch_zoom";
	public static final String UPDATE_FOCUS_FOR_VIDEO = "preference_update_focus_for_video";
	public static final String ALT_INDICATION = "preference_alt_indication";
	public static final String POPUP_SIZE = "preference_popup_size";
	public static final String POPUP_FONT_SIZE = "preference_popup_font_size";
	public static final String POPUP_COLOR = "preference_popup_color";
	public static final String POPUP_EXPANDED_LISTS = "preference_popup_expanded_lists";
	public static final String POPUP_ISO = "preference_popup_iso";
	public static final String POPUP_PHOTO_MODE = "preference_popup_photo_mode";
	public static final String POPUP_WHITE_BALANCE = "preference_popup_white_balance";
	public static final String POPUP_SCENE_MODE = "preference_popup_scene_mode";
	public static final String POPUP_COLOR_EFFECT = "preference_popup_color_effect";
	public static final String POPUP_AUTO_STABILISE = "preference_popup_auto_stabilise";
	public static final String POPUP_RESOLUTION = "preference_popup_resolution";
	public static final String POPUP_TIMER = "preference_popup_timer";
	public static final String POPUP_BURST_MODE = "preference_popup_burst_mode";
	public static final String POPUP_BURST_INTERVAL = "preference_popup_burst_interval";
	public static final String POPUP_GRID = "preference_popup_grid";
	public static final String POPUP_EXPO_BRACKETING_STOPS = "preference_popup_stops";
	public static final String POPUP_HDR_TONEMAPPING = "preference_popup_hdr_tonemapping";
	public static final String POPUP_HDR_LOCAL_CONTRAST = "preference_popup_hdr_local_contrast";
	public static final String POPUP_HDR_N_TILES = "preference_popup_hdr_n_tiles";
	public static final String POPUP_PHOTOS_COUNT = "preference_popup_photos_count";
	public static final String FORCE_FACE_FOCUS = "preference_force_face_focus";
	public static final String CENTER_FOCUS = "preference_center_focus";
	public static final String USE_1920X1088 = "preference_use_1920x1088";
	public static final String MIN_FOCUS_DISTANCE = "preference_min_focus_distance";
	public static final String ANTIBANDING = "preference_antibanding";
	public static final String IND_FREQ = "preference_osd_frequency";
	public static final String IND_SLOW_IF_BUSY = "preference_osd_slow_if_busy";
	public static final String NR_SAVE = "preference_nr_save";
	public static final String NOISE_REDUCTION = "preference_noise_reduction";
	public static final String EDGE = "preference_edge";
	public static final String SMART_FILTER = "preference_smart_filter";
	public static final String OPTICAL_STABILIZATION = "preference_optical_stabilization";
	public static final String FOCUS_DISTANCE = "preference_focus_distance";
	public static final String FOCUS_BRACKETING_DISTANCE = "preference_focus_bracketing_distance";
	public static final String FOCUS_RANGE = "preference_focus_range";
	public static final String ISO_STEPS = "preference_iso_steps";
	public static final String SPEED_UP_SENSORS = "preference_speed_up_sensors";
	public static final String SLIDERS_LOCATION = "preference_sliders_location";
	public static final String SLIDERS_SIZE = "preference_sliders_size";
	public static final String SLIDERS_GAP = "preference_sliders_gap";
	public static final String SLIDERS_AUTO_SWITCH = "preference_sliders_auto_switch";
	public static final String SHOW_EXPOSURE_BUTTONS = "preference_show_exposure_buttons";
	public static final String FB_COUNT = "preference_fb_count";
	public static final String FB_FOCUS_TIME = "preference_fb_focus_time";
	public static final String FAST_BURST_COUNT = "preference_fast_burst_count";
	public static final String FAST_BURST_DISABLE_FILTERS = "preference_fast_burst_disable_filters";
	public static final String NR_COUNT = "preference_nr_count";
	public static final String NR_ADJUST_LEVELS = "preference_nr_adjust_levels";
	public static final String NR_DISABLE_FILTERS = "preference_nr_disable_filters";

	// note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
	public enum PhotoMode {
		Standard,
		DRO, // single image "fake" HDR
		HDR, // HDR created from multiple (expo bracketing) images
		ExpoBracketing, // take multiple expo bracketed images, without combining to a single image
		FocusBracketing,
		FastBurst,
		NoiseReduction
	}

	private static int pref_camera_id = -1;
	private static String pref_photo_mode = null;
	private static PhotoMode photo_mode = PhotoMode.Standard;
	
	public static void setSharedPreferences(SharedPreferences prefs) {
		sharedPreferences = prefs;
	}

	public static void setMainActivity(MainActivity activity) {
		main_activity = activity;
	}

	public static String getFlashPreferenceKey() {
		return "flash_value_" + pref_camera_id + (isVideoPref() ? "_video" : "");
	}

	public static String getFocusPreferenceKey(boolean is_video) {
		return "focus_value_" + pref_camera_id + "_" + is_video;
	}

	public static String getResolutionPreferenceKey() {
		return "camera_resolution_" + pref_camera_id;
	}

	public static String getVideoQualityPreferenceKey() {
		return "video_quality_" + pref_camera_id;
	}
	
	public static int getCameraIdPref() {
		if (pref_camera_id < 0) {
			pref_camera_id = sharedPreferences.getInt(CAMERA_ID, 0);
		}
		return pref_camera_id;
	}

	public static void setCameraIdPref(int camera_id) {
		pref_camera_id = camera_id;
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(CAMERA_ID, camera_id);
		editor.apply();
	}

	public static String getISOKey() {
		return ISO + "_" + pref_camera_id + (isVideoPref() ? "_video" : "");
	}
	
	public static String getISOPref() {
		return sharedPreferences.getString(getISOKey(), "auto");
	}

	public static void setISOPref(String iso) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getISOKey(), iso);
		editor.apply();
	}

	public static void clearISOPref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(getISOKey());
		editor.apply();
	}
	
	/*##########################################
		Photo modes
	##########################################*/
	
	public static PhotoMode getPhotoMode() {
		// Note, this always should return the true photo mode - if we're in video mode and taking a photo snapshot while
		// video recording, the caller should override. We don't override here, as this preference may be used to affect how
		// the CameraController is set up, and we don't always re-setup the camera when switching between photo and video modes.
		if (pref_photo_mode == null) updatePhotoMode();
		return photo_mode;
	}

	public static String getPhotoModePref() {
		if (pref_photo_mode == null) updatePhotoMode();
		return pref_photo_mode;
	}
	
	public static void setPhotoModePref(String option) {
		if( MyDebug.LOG ) Log.d(TAG, "setPhotoModePref");
		switch (option) {
			case "dro":
				photo_mode = PhotoMode.DRO;
				break;
			case "hdr":
				photo_mode = PhotoMode.HDR;
				break;
			case "ebr":
				photo_mode = PhotoMode.ExpoBracketing;
				break;
			case "fbr":
				photo_mode = PhotoMode.FocusBracketing;
				break;
			case "bur":
				photo_mode = PhotoMode.FastBurst;
				break;
			case "nr":
				photo_mode = PhotoMode.NoiseReduction;
				break;
			default:
				photo_mode = PhotoMode.Standard;
				break;
		}
		pref_photo_mode = option;

		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(Prefs.PHOTO_MODE, option);
		editor.apply();
	}

	private static void updatePhotoMode() {
		if( MyDebug.LOG ) Log.d(TAG, "updatePhotoMode");
		String photo_mode_string = sharedPreferences.getString(Prefs.PHOTO_MODE, "std");
		if( photo_mode_string.equals("dro") && main_activity.supportsDRO() ) {
			photo_mode = PhotoMode.DRO;
			pref_photo_mode = "dro";
		} else if( photo_mode_string.equals("hdr") && main_activity.supportsHDR() ) {
			photo_mode = PhotoMode.HDR;
			pref_photo_mode = "hdr";
		} else if( photo_mode_string.equals("ebr") && main_activity.supportsExpoBracketing() ) {
			photo_mode = PhotoMode.ExpoBracketing;
			pref_photo_mode = "ebr";
		} else if( photo_mode_string.equals("fbr") && main_activity.supportsFocusBracketing() ) {
			photo_mode = PhotoMode.FocusBracketing;
			pref_photo_mode = "fbr";
		} else if( photo_mode_string.equals("bur") && main_activity.supportsFastBurst() ) {
			photo_mode = PhotoMode.FastBurst;
			pref_photo_mode = "bur";
		} else if( photo_mode_string.equals("nr") && main_activity.supportsNoiseReduction() ) {
			photo_mode = PhotoMode.NoiseReduction;
			pref_photo_mode = "nr";
		} else {
			photo_mode = PhotoMode.Standard;
			pref_photo_mode = "std";
		}
	}
	
	public static void needUpdatePhotoMode() {
		pref_photo_mode = null;
	}

	public static boolean isExpoBracketingPref() {
		if( photo_mode == PhotoMode.HDR || photo_mode == PhotoMode.ExpoBracketing )
			return true;
		return false;
	}

	public static boolean isCameraBurstPref() {
		if( photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.FastBurst )
			return true;
		return false;
	}
	
	public static int getBurstCount() {
		if (photo_mode == PhotoMode.FastBurst || photo_mode == PhotoMode.NoiseReduction ) {
			int result = 3;
			String value = sharedPreferences.getString(photo_mode == PhotoMode.NoiseReduction ? Prefs.NR_COUNT : Prefs.FAST_BURST_COUNT, "3");
			try {
				result = Integer.parseInt(value);
			}
			catch(NumberFormatException exception) {
				result = 3;
			}
			return result;
		}
		return 0;
	}

	public static int getExpoBracketingNImagesPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getExpoBracketingNImagesPref");
		int n_images;
		if( photo_mode == PhotoMode.HDR ) {
			// always set 3 images for HDR
			n_images = 3;
		}
		else {
				String n_images_s = sharedPreferences.getString(Prefs.EXPO_BRACKETING_N_IMAGES, "3");
			try {
				n_images = Integer.parseInt(n_images_s);
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.e(TAG, "n_images_s invalid format: " + n_images_s);
				n_images = 3;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "n_images = " + n_images);
		return n_images;
	}

	public static double getExpoBracketingStopsUpPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getExpoBracketingStopsUpPref");
		double n_stops;

		String n_stops_s = sharedPreferences.getString(photo_mode == PhotoMode.HDR ? Prefs.HDR_STOPS_UP : Prefs.EXPO_BRACKETING_STOPS_UP, "2");
		try {
			n_stops = Double.parseDouble(n_stops_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "n_stops_s invalid format: " + n_stops_s);
			n_stops = 2.0;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "n_stops = " + n_stops);
		return n_stops;
	}

	public static double getExpoBracketingStopsDownPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getExpoBracketingStopsDownPref");
		double n_stops;

		String n_stops_s = sharedPreferences.getString(photo_mode == PhotoMode.HDR ? Prefs.HDR_STOPS_DOWN : Prefs.EXPO_BRACKETING_STOPS_DOWN, "2");
		try {
			n_stops = Double.parseDouble(n_stops_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "n_stops_s invalid format: " + n_stops_s);
			n_stops = 2.0;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "n_stops = " + n_stops);
		return n_stops;
	}

	public static boolean getOptimiseAEForDROPref() {
		return( photo_mode == PhotoMode.DRO );
	}

	/*##########################################
		
	##########################################*/

	public static String getFlashPref() {
		return sharedPreferences.getString(getFlashPreferenceKey(), "");
	}

	public static void setFlashPref(String flash_value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getFlashPreferenceKey(), flash_value);
		editor.apply();
	}

	public static String getFocusPref(boolean is_video) {
		return sharedPreferences.getString(getFocusPreferenceKey(is_video), "");
	}

	public static void setFocusPref(String focus_value, boolean is_video) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getFocusPreferenceKey(is_video), focus_value);
		editor.apply();
	}

	public static boolean isVideoPref() {
		return sharedPreferences.getBoolean(IS_VIDEO, false);
	}

	public static void setVideoPref(boolean is_video) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(IS_VIDEO, is_video);
		editor.apply();
	}

	public static String getSceneModePref() {
		return sharedPreferences.getString(SCENE_MODE, "auto");
	}

	public static void setSceneModePref(String scene_mode) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(SCENE_MODE, scene_mode);
		editor.apply();
	}

	public static void clearSceneModePref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(SCENE_MODE);
		editor.apply();
	}

	public static String getColorEffectPref() {
		return sharedPreferences.getString(COLOR_EFFECT, "none");
	}

	public static void setColorEffectPref(String color_effect) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(COLOR_EFFECT, color_effect);
		editor.apply();
	}

	public static void clearColorEffectPref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(COLOR_EFFECT);
		editor.apply();
	}

	public static String getWhiteBalancePref() {
		return sharedPreferences.getString(WHITE_BALANCE, "auto");
	}

	public static void setWhiteBalancePref(String white_balance) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(WHITE_BALANCE, white_balance);
		editor.apply();
	}

	public static void clearWhiteBalancePref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(WHITE_BALANCE);
		editor.apply();
	}

	public static int getWhiteBalanceTemperaturePref() {
		return sharedPreferences.getInt(WHITE_BALANCE_TEMPERATURE, 5000);
	}

	public static void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(WHITE_BALANCE_TEMPERATURE, white_balance_temperature);
		editor.apply();
	}

	public static boolean getMultitouchZoomPref() {
		return sharedPreferences.getBoolean(MULTITOUCH_ZOOM, true);
	}

	public static int getExposureCompensationPref() {
		String value = sharedPreferences.getString(EXPOSURE, "0");
		if( MyDebug.LOG )
			Log.d(TAG, "saved exposure value: " + value);
		int exposure = 0;
		try {
			exposure = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "exposure: " + exposure);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "exposure invalid format, can't parse to int");
		}
		return exposure;
	}

	public static void setExposureCompensationPref(int exposure) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(EXPOSURE, "" + exposure);
		editor.apply();
	}

	public static void clearExposureCompensationPref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(EXPOSURE);
		editor.apply();
	}

	public static boolean getForceFaceFocusPref() {
		return sharedPreferences.getBoolean(FORCE_FACE_FOCUS, false);
	}

	public static boolean getCenterFocusPref() {
		return sharedPreferences.getBoolean(CENTER_FOCUS, false);
	}

	public static Pair<Integer, Integer> getCameraResolutionPref() {
		String resolution_value = sharedPreferences.getString(getResolutionPreferenceKey(), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
	}

	public static void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getResolutionPreferenceKey(), resolution_value);
		editor.apply();
	}

	/** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
	 *  photo - in some cases, we may set that to a higher value, then perform processing on the
	 *  resultant JPEG before resaving. method returns the image quality setting to be used for
	 *  saving the final image (as specified by the user).
	 */
	public static int getSaveImageQualityPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSaveImageQualityPref");
		String image_quality_s = sharedPreferences.getString(QUALITY, "90");
		int image_quality;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
	}

	public static int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		// see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
		// at 100% quality for post-processing, the final image will then be saved at the user requested
		// setting
		if( photo_mode == PhotoMode.DRO )
			return 100;
		else if( photo_mode == PhotoMode.NoiseReduction )
			return 100;
		return getSaveImageQualityPref();
	}

	public static boolean getFaceDetectionPref() {
		return sharedPreferences.getBoolean(FACE_DETECTION, false);
	}

	public static String getVideoQualityPref() {
		return sharedPreferences.getString(getVideoQualityPreferenceKey(), "");
	}
	public static void setVideoQualityPref(String video_quality) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getVideoQualityPreferenceKey(), video_quality);
		editor.apply();
	}

	public static boolean getVideoStabilizationPref() {
		return sharedPreferences.getBoolean(VIDEO_STABILIZATION, false);
	}

	public static boolean getForce4KPref() {
		if( pref_camera_id == 0 && sharedPreferences.getBoolean(FORCE_VIDEO_4K, false) && main_activity.supportsForceVideo4K() ) {
			return true;
		}
		return false;
	}

	public static String getVideoBitratePref() {
		return sharedPreferences.getString(VIDEO_BITRATE, "default");
	}

	public static String getVideoFPSPref() {
		return sharedPreferences.getString(VIDEO_FPS, "default");
	}

	public static long getVideoMaxDurationPref() {
		String video_max_duration_value = sharedPreferences.getString(VIDEO_MAX_DURATION, "0");
		long video_max_duration;
		try {
			video_max_duration = (long)Integer.parseInt(video_max_duration_value) * 1000;
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_video_max_duration value: " + video_max_duration_value);
			e.printStackTrace();
			video_max_duration = 0;
		}
		return video_max_duration;
	}

	public static int getVideoRestartTimesPref() {
		String restart_value = sharedPreferences.getString(VIDEO_RESTART, "0");
		int remaining_restart_video;
		try {
			remaining_restart_video = Integer.parseInt(restart_value);
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_video_restart value: " + restart_value);
			e.printStackTrace();
			remaining_restart_video = 0;
		}
		return remaining_restart_video;
	}

	public static long getVideoMaxFileSizeUserPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizeUserPref");
		String video_max_filesize_value = sharedPreferences.getString(VIDEO_MAX_FILESIZE, "0");
		long video_max_filesize;
		try {
			video_max_filesize = Integer.parseInt(video_max_filesize_value);
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_video_max_filesize value: " + video_max_filesize_value);
			e.printStackTrace();
			video_max_filesize = 0;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "video_max_filesize: " + video_max_filesize);
		return video_max_filesize;
	}

	public static boolean getVideoRestartMaxFileSizeUserPref() {
		return sharedPreferences.getBoolean(VIDEO_RESTART_MAX_FILESIZE, true);
	}


	public static boolean getVideoFlashPref() {
		return sharedPreferences.getBoolean(VIDEO_FLASH, false);
	}

	public static boolean getVideoLowPowerCheckPref() {
		return sharedPreferences.getBoolean(VIDEO_LOW_POWER_CHECK, true);
	}

	public static boolean getPreviewMaxSizePref() {
		return (sharedPreferences.getBoolean(PREVIEW_MAX_SIZE, true) && 
			(!sharedPreferences.getBoolean(IS_VIDEO, false) || sharedPreferences.getString(CROP_GUIDE, "crop_guide_none").equals("crop_guide_none")));
	}

	public static String getPreviewRotationPref() {
		return sharedPreferences.getString(ROTATE_PREVIEW, "0");
	}

	public static String getLockOrientationPref() {
		return sharedPreferences.getString(LOCK_ORIENTATION, "none");
	}

	public static boolean getTouchCapturePref() {
		return sharedPreferences.getBoolean(TOUCH_CAPTURE, false);
	}

	public static boolean getPausePreviewPref() {
		if (!isVideoPref()) {
				return sharedPreferences.getBoolean(PAUSE_PREVIEW, false);
		}
		return false;
	}

	public static boolean getShowToastsPref() {
		return sharedPreferences.getBoolean(SHOW_TOASTS, true);
	}

	public static boolean getThumbnailAnimationPref() {
		return sharedPreferences.getBoolean(THUMBNAIL_ANIMATION, true);
	}

	public static boolean getShutterSoundPref() {
		return (sharedPreferences.getBoolean(SHUTTER_SOUND, true) &&
			sharedPreferences.getString(SHUTTER_SOUND_SELECT, "default").equals("default"));
	}

	public static boolean getStartupFocusPref() {
		return sharedPreferences.getBoolean(STARTUP_FOCUS, false);
	}

	public static long getTimerPref() {
		String timer_value = sharedPreferences.getString(TIMER, "0");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_timer value: " + timer_value);
			e.printStackTrace();
			timer_delay = 0;
		}
		return timer_delay;
	}

	public static String getRepeatPref() {
		return sharedPreferences.getString(BURST_MODE, "5");
	}

	public static boolean getSelfieModePref() {
		return main_activity.selfie_mode;
	}

	public static boolean getWaitFacePref() {
		return sharedPreferences.getBoolean(WAIT_FACE, false);
	}

	public static long getRepeatIntervalPref() {
		String timer_value = sharedPreferences.getString(BURST_INTERVAL, "2");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
			e.printStackTrace();
			timer_delay = 0;
		}
		return timer_delay;
	}

	public static boolean getGeotaggingPref() {
		return sharedPreferences.getBoolean(LOCATION, false);
	}

	public static boolean getRequireLocationPref() {
		return sharedPreferences.getBoolean(REQUIRE_LOCATION, false);
	}

	public static boolean getGeodirectionPref() {
		return sharedPreferences.getBoolean(GPS_DIRECTION, false);
	}

	public static boolean getRecordAudioPref() {
		return sharedPreferences.getBoolean(RECORD_AUDIO, true);
	}

	public static String getRecordAudioChannelsPref() {
		return sharedPreferences.getString(RECORD_AUDIO_CHANNELS, "audio_default");
	}

	public static String getRecordAudioSourcePref() {
		return sharedPreferences.getString(RECORD_AUDIO_SRC, "audio_src_camcorder");
	}

	public static boolean getAutoStabilisePref() {
		if( main_activity.supportsAutoStabilise() && sharedPreferences.getBoolean(AUTO_STABILISE, false))
			return true;
		return false;
	}

	public static double getCalibratedLevelAngle() {
		float angle = 0.0f;
		try {
			angle = Float.parseFloat(sharedPreferences.getString(CALIBRATED_LEVEL_ANGLE, "0"));
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "calibrated angle invalid format, can't parse to float");
		}
		return angle;
	}

	public static boolean useCamera2FakeFlash() {
		return sharedPreferences.getBoolean(CAMERA2_FAKE_FLASH, false);
	}

	public static boolean useCamera2FastBurst() {
		return sharedPreferences.getBoolean(CAMERA2_FAST_BURST, true);
	}

	public static boolean getUpdateFocusForVideoPref() {
		return sharedPreferences.getBoolean(UPDATE_FOCUS_FOR_VIDEO, false);
	}

	public static String getAntibandingPref() {
		return sharedPreferences.getString(ANTIBANDING, "auto");
	}
	
	public static long getFBFocusTimePref() {
		long value;
		try {
			value = (long)Integer.parseInt(sharedPreferences.getString(FB_FOCUS_TIME, "1000"));
		}
		catch(NumberFormatException e) {
			value = 1000;
		}
		return value;
	}

	public static int getExposureCompensationDelayPref() {
		int value;
		try {
			value = Integer.parseInt(sharedPreferences.getString(EXPO_BRACKETING_DELAY, "1000"));
		}
		catch(NumberFormatException e) {
			value = 1000;
		}
		return value;
	}

	public static void setShowSeekbarsPref(boolean value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(SHOW_SEEKBARS, value);
		editor.apply();
	}
}
