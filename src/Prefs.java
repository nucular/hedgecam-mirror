package com.caddish_hedgehog.hedgecam2;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.os.Build;
import android.os.Environment;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.xmlpull.v1.XmlPullParserException;

public class Prefs {
	private static final String TAG = "HedgeCam/Prefs";
	private static SharedPreferences sharedPreferences;
	private static MainActivity main_activity;
	private static SharedPreferences.Editor prefEditor;

	public static final String LAST_FOLDER = "last_folder";
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
	public static final String SAVE_ZOOM = "preference_save_zoom";
	public static final String ZOOM = "zoom";
	public static final String EXPOSURE = "preference_exposure";
	public static final String COLOR_EFFECT = "preference_color_effect";
	public static final String SCENE_MODE = "preference_scene_mode";
	public static final String WHITE_BALANCE = "preference_white_balance";
	public static final String WHITE_BALANCE_TEMPERATURE = "preference_white_balance_temperature";
	public static final String ISO = "preference_iso";
	public static final String MANUAL_ISO = "preference_manual_iso";
	public static final String EXPOSURE_TIME = "preference_exposure_time";
	public static final String IMAGE_FORMAT = "preference_image_format";
	public static final String EXPO_BRACKETING_N_IMAGES = "preference_expo_bracketing_n_images";
	public static final String EXPO_BRACKETING_STOPS_UP = "preference_expo_bracketing_stops_up";
	public static final String EXPO_BRACKETING_STOPS_DOWN = "preference_expo_bracketing_stops";
	public static final String EXPO_BRACKETING_USE_ISO = "preference_expo_bracketing_use_iso";
	public static final String EXPO_BRACKETING_DELAY = "preference_expo_bracketing_delay";
	public static final String VOLUME_KEYS = "preference_volume_keys";
	public static final String AUDIO_CONTROL_TYPE = "preference_audio_control_type";
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
	public static final String STAMP_STORE_ADDRESS = "preference_stamp_store_address";
	public static final String STAMP_STORE_ALTITUDE = "preference_stamp_store_altitude";
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
	public static final String SYSTEM_UI_ORIENTATION = "preference_system_ui_orientation";
	public static final String USING_SAF = "preference_using_saf";
	public static final String SAVE_LOCATION = "preference_save_location";
	public static final String SAVE_LOCATION_SAF = "preference_save_location_saf";
	public static final String SAVE_VIDEO_FOLDER = "preference_save_video_folder";
	public static final String SAVE_VIDEO_LOCATION = "preference_save_video_location";
	public static final String SAVE_VIDEO_LOCATION_SAF = "preference_save_video_location_saf";
	public static final String SAVE_PHOTO_PREFIX = "preference_save_photo_prefix";
	public static final String SAVE_VIDEO_PREFIX = "preference_save_video_prefix";
	public static final String SAVE_ZULU_TIME = "preference_save_zulu_time";
	public static final String OSD_FONT_SIZE = "preference_osd_font_size";
	public static final String SHOW_ZOOM_CONTROLS = "preference_show_zoom_controls";
	public static final String SHOW_ZOOM_SLIDER_CONTROLS = "preference_show_zoom_slider_controls";
	public static final String SHOW_TAKE_PHOTO = "preference_show_take_photo";
	public static final String SHOW_ZOOM = "preference_show_zoom";
	public static final String SHOW_ISO = "preference_show_iso";
	public static final String SHOW_FOCUS_DISTANCE = "preference_show_focus_distance";
	public static final String SHOW_FOCUS_RANGE = "preference_show_focus_range";
	public static final String SHOW_WHITE_BALANCE = "preference_show_white_balance";
	public static final String SHOW_WHITE_BALANCE_XY = "preference_show_white_balance_xy";
	public static final String SHOW_ANGLE = "preference_show_angle";
	public static final String SHOW_ANGLE_LINE = "preference_show_angle_line";
	public static final String SHOW_PITCH_LINES = "preference_show_pitch_lines";
	public static final String SHOW_GEO_DIRECTION_LINES = "preference_show_geo_direction_lines";
	public static final String SHOW_VIDEO_MAX_AMP = "preference_show_video_max_amp";
	public static final String ANGLE_HIGHLIGHT_COLOR = "preference_angle_highlight_color";
	public static final String CALIBRATED_LEVEL_ANGLE = "preference_calibrated_level_angle";
	public static final String SHOW_GEO_DIRECTION = "preference_show_geo_direction";
	public static final String FREE_MEMORY = "preference_free_memory";
	public static final String SHOW_TIME = "preference_show_time";
	public static final String SHOW_BATTERY = "preference_show_battery";
	public static final String GRID = "preference_grid";
	public static final String GRID_ALPHA = "preference_grid_alpha";
	public static final String CROP_GUIDE = "preference_crop_guide";
	public static final String SHOW_HISTOGRAM = "preference_show_histogram";
	public static final String HISTOGRAM_MODE = "preference_histogram_mode";
	public static final String HISTOGRAM_SIZE = "preference_histogram_size";
	public static final String HISTOGRAM_UPDATE = "preference_histogram_frequency";
	public static final String HISTOGRAM_ACCURACY = "preference_histogram_accuracy";
	public static final String SHOW_COLOR_PROBE = "preference_color_probe";
	public static final String COLOR_PROBE_SIZE = "preference_color_probe_size";
	public static final String FACE_DETECTION = "preference_face_detection";
	public static final String VIDEO_STABILIZATION = "preference_video_stabilization";
	public static final String FORCE_VIDEO_4K = "preference_force_video_4k";
	public static final String VIDEO_BITRATE = "preference_video_bitrate";
	public static final String VIDEO_FPS = "preference_video_fps";
	public static final String VIDEO_FORMAT = "preference_video_format";
	public static final String VIDEO_MAX_DURATION = "preference_video_max_duration";
	public static final String VIDEO_RESTART = "preference_video_restart";
	public static final String VIDEO_MAX_FILESIZE = "preference_video_max_filesize";
	public static final String VIDEO_RESTART_MAX_FILESIZE = "preference_video_restart_max_filesize";
	public static final String VIDEO_FLASH = "preference_video_flash";
	public static final String VIDEO_LOW_POWER_CHECK = "preference_video_low_power_check";
	public static final String VIDEO_LOG_PROFILE = "preference_video_log";
	public static final String CAPTURE_RATE = "preference_capture_rate_s";
	public static final String LOCK_VIDEO = "preference_lock_video";
	public static final String RECORD_AUDIO = "preference_record_audio";
	public static final String RECORD_AUDIO_CHANNELS = "preference_record_audio_channels";
	public static final String RECORD_AUDIO_SRC = "preference_record_audio_src";
	public static final String RECORD_AUDIO_BITRATE = "preference_record_audio_bitrate";
	public static final String RECORD_AUDIO_SAMPLE_RATE = "preference_record_audio_sample_rate";
	public static final String PREVIEW_MAX_SIZE = "preference_preview_max_size";
	public static final String ROTATE_PREVIEW = "preference_rotate_preview";
	public static final String LOCK_PHOTO_ORIENTATION = "preference_lock_orientation";
	public static final String LOCK_VIDEO_ORIENTATION = "preference_lock_video_orientation";
	public static final String SELFIE_MODE = "selfie_mode";
	public static final String AUDIO_CONTROL = "audio_control";
	public static final String TIMER = "preference_timer";
	public static final String TIMER_BEEP = "preference_timer_beep";
	public static final String TIMER_SPEAK = "preference_timer_speak";
	public static final String TIMER_START_SOUND = "preference_timer_start_sound";
	public static final String BURST_MODE = "preference_burst_mode";
	public static final String BURST_INTERVAL = "preference_burst_interval";
	public static final String BURST_LOW_BRIGHTNESS = "preference_burst_low_brightness";
	public static final String BURST_LOCK = "preference_burst_lock";
	public static final String WAIT_FACE = "preference_wait_face";
	public static final String SHUTTER_SOUND = "preference_shutter_sound_select";
	public static final String VIDEO_SOUND = "preference_video_sound";
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
	public static final String HDR_ALIGN = "preference_hdr_align";
	public static final String HDR_UNSHARP_MASK = "preference_hdr_unsharp_mask";
	public static final String HDR_UNSHARP_MASK_RADIUS = "preference_hdr_unsharp_mask_radius";
	public static final String HDR_LOCAL_CONTRAST = "preference_hdr_local_contrast";
	public static final String HDR_N_TILES = "preference_hdr_n_tiles";
	public static final String HDR_IGNORE_SMART_FILTER = "preference_hdr_ignore_sf";
	public static final String HDR_STOPS_UP = "preference_hdr_stops_up";
	public static final String HDR_STOPS_DOWN = "preference_hdr_stops";
	public static final String HDR_DEGHOST = "preference_hdr_deghost";
	public static final String HDR_ADJUST_LEVELS = "preference_hdr_adjust_levels";
	public static final String HDR_HISTOGRAM_LEVEL = "preference_hdr_histogram_level";

	public static final String DRO_UNSHARP_MASK = "preference_dro_unsharp_mask";
	public static final String DRO_UNSHARP_MASK_RADIUS = "preference_dro_unsharp_mask_radius";
	public static final String DRO_LOCAL_CONTRAST = "preference_dro_local_contrast";
	public static final String DRO_N_TILES = "preference_dro_n_tiles";
	public static final String DRO_ADJUST_LEVELS = "preference_dro_adjust_levels";
	public static final String DRO_HISTOGRAM_LEVEL = "preference_dro_histogram_level";

	public static final String PREVIEW_LOCATION = "preference_preview_location";
	public static final String PREVIEW_MAX_EXPO = "preference_preview_max_expo";
	public static final String GHOST_IMAGE = "preference_ghost_image";
	public static final String GHOST_IMAGE_SOURCE = "preference_ghost_image_source";
	public static final String GHOST_IMAGE_ALPHA = "preference_ghost_image_alpha";
	public static final String GHOST_IMAGE_FILE = "preference_ghost_image_file";
	public static final String GHOST_IMAGE_FILE_SAF = "preference_ghost_image_file_saf";
	public static final String CTRL_PANEL_SELFIE_MODE = "preference_ctrl_panel_selfie_mode";
	public static final String CTRL_PANEL_AUDIO_CONTROL = "preference_ctrl_panel_audio_control";
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
	public static final String MODE_PANEL_AUDIO_CONTROL = "preference_mode_panel_audio_control";
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
	public static final String TOUCH_FOCUS = "preference_touch_focus";
	public static final String UPDATE_FOCUS_FOR_VIDEO = "preference_update_focus_for_video";
	public static final String ALT_INDICATION = "preference_alt_indication";
	public static final String POPUP_SIZE = "preference_popup_size";
	public static final String POPUP_CAPTURE_RATE = "preference_popup_capture_rate";
	public static final String POPUP_FONT_SIZE = "preference_popup_font_size";
	public static final String POPUP_COLOR = "preference_popup_color";
	public static final String POPUP_EXPANDED_LISTS = "preference_popup_expanded_lists";
	public static final String POPUP_ISO = "preference_popup_iso";
	public static final String POPUP_PHOTO_MODE = "preference_popup_photo_mode";
	public static final String POPUP_WHITE_BALANCE = "preference_popup_white_balance";
	public static final String POPUP_SCENE_MODE = "preference_popup_scene_mode";
	public static final String POPUP_COLOR_EFFECT = "preference_popup_color_effect";
	public static final String POPUP_AUTO_STABILISE = "preference_popup_auto_stabilise";
	public static final String POPUP_OPTICAL_STABILIZATION = "preference_popup_optical_stabilization";
	public static final String POPUP_RESOLUTION = "preference_popup_resolution";
	public static final String POPUP_TIMER = "preference_popup_timer";
	public static final String POPUP_BURST_MODE = "preference_popup_burst_mode";
	public static final String POPUP_BURST_INTERVAL = "preference_popup_burst_interval";
	public static final String POPUP_GRID = "preference_popup_grid";
	public static final String POPUP_HISTOGRAM = "preference_popup_histogram";
	public static final String POPUP_GHOST_IMAGE = "preference_popup_ghost_image";
	public static final String POPUP_EXPO_BRACKETING_STOPS = "preference_popup_stops";
	public static final String POPUP_HDR_TONEMAPPING = "preference_popup_hdr_tonemapping";
	public static final String POPUP_HDR_UNSHARP_MASK = "preference_popup_hdr_unsharp_mask";
	public static final String POPUP_HDR_UNSHARP_MASK_RADIUS = "preference_popup_hdr_unsharp_mask_radius";
	public static final String POPUP_HDR_LOCAL_CONTRAST = "preference_popup_hdr_local_contrast";
	public static final String POPUP_HDR_N_TILES = "preference_popup_hdr_n_tiles";
	public static final String POPUP_HDR_DEGHOST = "preference_popup_hdr_deghost";
	public static final String POPUP_PHOTOS_COUNT = "preference_popup_photos_count";
	public static final String POPUP_VIDEO_BITRATE = "preference_popup_video_bitrate";
	public static final String POPUP_VIDEO_FPS = "preference_popup_video_fps";
	public static final String POPUP_VIDEO_LOG_PROFILE = "preference_popup_video_log";
	public static final String FORCE_FACE_FOCUS = "preference_force_face_focus";
	public static final String CENTER_FOCUS = "preference_center_focus";
	public static final String USE_1920X1088 = "preference_use_1920x1088";
	public static final String FORCE_ISO_EXPOSURE = "preference_force_iso_exposure";
	public static final String MIN_FOCUS_DISTANCE = "preference_min_focus_distance";
	public static final String FOCUS_DISTANCE_CALIBRATION = "preference_focus_distance_calibration";
	public static final String WHITE_BALANCE_CALIBRATION = "preference_white_balance_calibration";
	public static final String ANTIBANDING = "preference_antibanding";
	public static final String IND_FREQ = "preference_osd_frequency";
	public static final String IND_SLOW_IF_BUSY = "preference_osd_slow_if_busy";
	public static final String NR_SAVE = "preference_nr_save";
	public static final String NOISE_REDUCTION = "preference_noise_reduction";
	public static final String EDGE = "preference_edge";
	public static final String SMART_FILTER = "preference_smart_filter";
	public static final String OPTICAL_STABILIZATION = "preference_optical_stabilization";
	public static final String HOT_PIXEL_CORRECTION = "preference_hot_pixel_correction";
	public static final String ZERO_SHUTTER_DELAY = "preference_zero_shutter_delay";
	public static final String FOCUS_DISTANCE = "preference_focus_distance";
	public static final String FOCUS_BRACKETING_DISTANCE = "preference_focus_bracketing_distance";
	public static final String FOCUS_RANGE = "preference_focus_range";
	public static final String ISO_STEPS = "preference_iso_steps";
	public static final String WHITE_BALANCE_STEPS = "preference_wb_steps";
	public static final String EXPOSURE_STEPS = "preference_expo_steps";
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
	public static final String NR_ALIGN = "preference_nr_align";
	public static final String NR_ADJUST_LEVELS = "preference_nr_adjust_levels";
	public static final String NR_HISTOGRAM_LEVEL = "preference_nr_histogram_level";
	public static final String NR_SAVE_BASE = "preference_nr_save_base";
	public static final String NR_DISABLE_FILTERS = "preference_nr_disable_filters";
	public static final String NR_SLOW_BURST = "preference_nr_slow_burst";
	public static final String NR_BURST_DELAY = "preference_nr_burst_delay";

	public static final String UNCOMPRESSED_PHOTO = "preference_uncompressed_photo";
	public static final String YUV_CONVERSION = "preference_yuv_conversion";
	public static final String ROW_SPACE_Y = "preference_row_space_y";
	public static final String ROW_SPACE_UV = "preference_row_space_uv";
	public static final String FULL_SIZE_COPY = "preference_full_size_copy";

	public static final String METADATA_AUTHOR = "preference_metadata_author";
	public static final String METADATA_COMMENT = "preference_metadata_comment";
	public static final String METADATA_POSITION_INFO = "preference_metadata_position_info";
	public static final String METADATA_MODE_INFO = "preference_metadata_mode_info";
	public static final String METADATA_SENSOR_INFO = "preference_metadata_sensor_info";
	public static final String METADATA_PROCESSING_INFO = "preference_metadata_processing_info";
	public static final String METADATA_COMMENT_AS_FILE = "preference_metadata_comment_as_file";

	public static final String ADJUST_LEVELS = "preference_adjust_levels";
	public static final String HISTOGRAM_LEVEL = "preference_histogram_level";

	public static final String ZOOM_WHEN_FOCUSING = "preference_zoom_when_focusing";
	
	public static final String RESET_MANUAL_MODE = "preference_reset_manual_mode";

	public static final String LOCK_PREVIEW_FPS_TO_VIDEO_FPS = "preference_lock_preview_fps";
	public static final String PREVIEW_FPS_OVERRIDE_DEFAULT = "preference_preview_fps_override";
	public static final String PREVIEW_FPS_MIN = "preference_preview_fps_min";
	public static final String PREVIEW_FPS_MAX = "preference_preview_fps_max";

	
	public static final String DEFAULT_COLOR_CORRECTION = "preference_default_color_correction";
	
	public static final String DONT_ROTATE = "preference_dont_rotate";
	
	public static final int ADJUST_LEVELS_NONE = 0;
	public static final int ADJUST_LEVELS_LIGHTS = 1;
	public static final int ADJUST_LEVELS_LIGHTS_SHADOWS = 2;
	public static final int ADJUST_LEVELS_BOOST = 3;
	
	public static final int HISTOGRAM_MODE_BRIGHTNESS = 1;
	public static final int HISTOGRAM_MODE_MAXIMUM = 2;
	public static final int HISTOGRAM_MODE_COLORS = 3;
	
	public static class Category {
		public String id;
		public int name_resource;
		public int summary_resource;
		public String[] keys;
		
		Category(String id, int name_resource, int summary_resource, String[] keys) {
			this.id = id;
			this.name_resource = name_resource;
			this.summary_resource = summary_resource;
			this.keys = keys;
		}
	}
	
	public static Category[] PREF_CATEGORIES = {
		new Category("modes", R.string.preference_category_modes, R.string.preference_category_modes_summary, new String[] {
			"flash_value_0",
			"flash_value_1",
			"flash_value_2",
			"flash_value_0_video",
			"flash_value_1_video",
			"flash_value_2_video",
			"focus_value_0",
			"focus_value_1",
			"focus_value_2",
			"focus_value_0_video",
			"focus_value_1_video",
			"focus_value_2_video",
			ISO + "_0",
			ISO + "_1",
			ISO + "_2",
			ISO + "_0_video",
			ISO + "_1_video",
			ISO + "_2_video",
			SCENE_MODE,
			COLOR_EFFECT,
			WHITE_BALANCE,
			PHOTO_MODE,
			MANUAL_ISO,
			EXPOSURE,
			EXPOSURE_TIME,
			FOCUS_DISTANCE,
			FOCUS_BRACKETING_DISTANCE,
			WHITE_BALANCE,
			WHITE_BALANCE_TEMPERATURE
		}),
		new Category("model", R.string.preference_category_model, R.string.preference_category_model_summary, new String[] {
			MIN_FOCUS_DISTANCE+"_0",
			MIN_FOCUS_DISTANCE+"_1",
			MIN_FOCUS_DISTANCE+"_2",
			WHITE_BALANCE_CALIBRATION+"_0",
			WHITE_BALANCE_CALIBRATION+"_1",
			WHITE_BALANCE_CALIBRATION+"_2",
			ROW_SPACE_Y+"_0",
			ROW_SPACE_Y+"_1",
			ROW_SPACE_Y+"_2",
			ROW_SPACE_UV+"_0",
			ROW_SPACE_UV+"_1",
			ROW_SPACE_UV+"_2",

			EXPO_BRACKETING_USE_ISO+"_0",
			EXPO_BRACKETING_USE_ISO+"_1",
			EXPO_BRACKETING_USE_ISO+"_2",
			EXPO_BRACKETING_DELAY,
			FB_FOCUS_TIME,
			
			DONT_ROTATE,
			STARTUP_FOCUS,
			UPDATE_FOCUS_FOR_VIDEO,
			CAMERA2_FAKE_FLASH,
			USE_1920X1088,
			SPEED_UP_SENSORS,
			FULL_SIZE_COPY,
			RESET_MANUAL_MODE,
			LOCK_PREVIEW_FPS_TO_VIDEO_FPS,
			DEFAULT_COLOR_CORRECTION,
			FORCE_ISO_EXPOSURE
		}),
		new Category("device", R.string.preference_category_device, R.string.preference_category_device_summary, new String[] {
			CALIBRATED_LEVEL_ANGLE,
			FOCUS_DISTANCE_CALIBRATION+"_0",
			FOCUS_DISTANCE_CALIBRATION+"_1",
			FOCUS_DISTANCE_CALIBRATION+"_2",

			MIN_FOCUS_DISTANCE+"_0",
			MIN_FOCUS_DISTANCE+"_1",
			MIN_FOCUS_DISTANCE+"_2",
			WHITE_BALANCE_CALIBRATION+"_0",
			WHITE_BALANCE_CALIBRATION+"_1",
			WHITE_BALANCE_CALIBRATION+"_2",
			ROW_SPACE_Y+"_0",
			ROW_SPACE_Y+"_1",
			ROW_SPACE_Y+"_2",
			ROW_SPACE_UV+"_0",
			ROW_SPACE_UV+"_1",
			ROW_SPACE_UV+"_2",

			EXPO_BRACKETING_USE_ISO+"_0",
			EXPO_BRACKETING_USE_ISO+"_1",
			EXPO_BRACKETING_USE_ISO+"_2",
			EXPO_BRACKETING_DELAY,
			FB_FOCUS_TIME,
			
			DONT_ROTATE,
			STARTUP_FOCUS,
			UPDATE_FOCUS_FOR_VIDEO,
			CAMERA2_FAKE_FLASH,
			USE_1920X1088,
			SPEED_UP_SENSORS,
			FULL_SIZE_COPY,
			RESET_MANUAL_MODE,
			LOCK_PREVIEW_FPS_TO_VIDEO_FPS,
			DEFAULT_COLOR_CORRECTION,
			FORCE_ISO_EXPOSURE
		}),
		new Category("filtering", R.string.preference_screen_filtering, 0, new String[] {
			ANTIBANDING,
			NOISE_REDUCTION+"_2_0",
			NOISE_REDUCTION+"_2_1",
			NOISE_REDUCTION+"_2_2",
			EDGE+"_2_0",
			EDGE+"_2_1",
			EDGE+"_2_2",
			SMART_FILTER+"_0",
			SMART_FILTER+"_1",
			SMART_FILTER+"_2",
			OPTICAL_STABILIZATION+"_0",
			OPTICAL_STABILIZATION+"_1",
			OPTICAL_STABILIZATION+"_2",
			HOT_PIXEL_CORRECTION+"_2_0",
			HOT_PIXEL_CORRECTION+"_2_1",
			HOT_PIXEL_CORRECTION+"_2_2"
		})
	};

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
	
	private static boolean is_video = false;
	private static boolean is_video_cached = false;

	public static void init(MainActivity activity, SharedPreferences prefs) {
		main_activity = activity;
		sharedPreferences = prefs;
	}

	public static SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	public static String getFlashPreferenceKey() {
		return "flash_value_" + pref_camera_id + (isVideoPref() ? "_video" : "");
	}

	public static String getFocusPreferenceKey() {
		return "focus_value_" + pref_camera_id + (isVideoPref() ? "_video" : "");
	}

	public static String getResolutionPreferenceKey() {
		return "camera_resolution_" + pref_camera_id;
	}

	public static String getVideoQualityPreferenceKey() {
		return "video_quality_" + pref_camera_id;
	}
	
	public static String getPreviewResolutionPreferenceKey() {
		return "preview_resolution_" + pref_camera_id + (isVideoPref() ? "_video" : "");
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
		editor.putString(PHOTO_MODE, option);
		editor.apply();
	}

	public static void updatePhotoMode() {
		if( MyDebug.LOG ) Log.d(TAG, "updatePhotoMode");
		String photo_mode_string = sharedPreferences.getString(PHOTO_MODE, "std");
		if( photo_mode_string.equals("dro") && main_activity.supportsDRO() && sharedPreferences.getBoolean("preference_photo_mode_dro_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.DRO;
			pref_photo_mode = "dro";
		} else if( photo_mode_string.equals("hdr") && main_activity.supportsHDR() && sharedPreferences.getBoolean("preference_photo_mode_hdr_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.HDR;
			pref_photo_mode = "hdr";
		} else if( photo_mode_string.equals("ebr") && main_activity.supportsExpoBracketing() && sharedPreferences.getBoolean("preference_photo_mode_expo_bracketing_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.ExpoBracketing;
			pref_photo_mode = "ebr";
		} else if( photo_mode_string.equals("fbr") && main_activity.supportsFocusBracketing() && sharedPreferences.getBoolean("preference_photo_mode_focus_bracketing_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.FocusBracketing;
			pref_photo_mode = "fbr";
		} else if( photo_mode_string.equals("bur") && main_activity.supportsFastBurst() && sharedPreferences.getBoolean("preference_photo_mode_fast_burst_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.FastBurst;
			pref_photo_mode = "bur";
		} else if( photo_mode_string.equals("nr") && main_activity.supportsNoiseReduction() && sharedPreferences.getBoolean("preference_photo_mode_nr_" + pref_camera_id, true) ) {
			photo_mode = PhotoMode.NoiseReduction;
			pref_photo_mode = "nr";
		} else {
			photo_mode = PhotoMode.Standard;
			pref_photo_mode = "std";
		}
	}
	
	public static String getPhotoModeStringValue(PhotoMode option) {
		switch (option) {
			case DRO:
				return "dro";
			case HDR:
				return "hdr";
			case ExpoBracketing:
				return "ebr";
			case FocusBracketing:
				return "fbr";
			case FastBurst:
				return "bur";
			case NoiseReduction:
				return "nr";
			default:
				return "std";
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

	public static String getFocusPref() {
		return sharedPreferences.getString(getFocusPreferenceKey(), "");
	}

	public static void setFocusPref(String focus_value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getFocusPreferenceKey(), focus_value);
		editor.apply();
	}

	public static boolean isVideoPref() {
		if (!is_video_cached) {
			is_video = sharedPreferences.getBoolean(IS_VIDEO, false);
			is_video_cached = true;
		}
		return is_video;
	}

	public static void setVideoPref(boolean state) {
		is_video = state;
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(IS_VIDEO, state);
		editor.apply();
	}
	
	public static boolean isVideoFolder() {
		return isVideoPref() && sharedPreferences.getString(Prefs.SAVE_VIDEO_FOLDER, "same_as_photo").equals("folder");
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

	public static int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		// see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
		// at 100% quality for post-processing, the final image will then be saved at the user requested
		// setting
		if( photo_mode == PhotoMode.DRO || photo_mode == PhotoMode.NoiseReduction || sharedPreferences.getString(Prefs.IMAGE_FORMAT, "jpeg").equals("png") )
			return 100;
		return getStringAsInt(QUALITY, 90);
	}

	public static String getVideoQualityPref() {
		return sharedPreferences.getString(getVideoQualityPreferenceKey(), "");
	}
	public static void setVideoQualityPref(String video_quality) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(getVideoQualityPreferenceKey(), video_quality);
		editor.apply();
	}

	public static boolean getForce4KPref() {
		if( pref_camera_id == 0 && sharedPreferences.getBoolean(FORCE_VIDEO_4K, false) && main_activity.supportsForceVideo4K() ) {
			return true;
		}
		return false;
	}

	public static String getVideoFPSPref() {
		float capture_rate_factor = getVideoCaptureRateFactor();
		if( capture_rate_factor < 1.0f-1.0e-5f ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set fps for slow motion, capture rate: " + capture_rate_factor);
			int preferred_fps = (int)(30.0/capture_rate_factor+0.5);
			if( MyDebug.LOG )
				Log.d(TAG, "preferred_fps: " + preferred_fps);
			if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(preferred_fps) ||
					main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(preferred_fps) )
				return "" + preferred_fps;
			// just in case say we support 120fps but NOT 60fps, getSupportedSlowMotionRates() will have returned that 2x slow
			// motion is supported, but we need to set 120fps instead of 60fps
			while( preferred_fps < 240 ) {
				preferred_fps *= 2;
				if( MyDebug.LOG )
					Log.d(TAG, "preferred_fps not supported, try: " + preferred_fps);
				if( main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRateHighSpeed(preferred_fps) ||
						main_activity.getPreview().getVideoQualityHander().videoSupportsFrameRate(preferred_fps) )
					return "" + preferred_fps;
			}
			// shouln't happen based on getSupportedSlowMotionRates()
			Log.e(TAG, "can't find valid fps for slow motion");
			return "default";
		}
		return sharedPreferences.getString(VIDEO_FPS, "default");
	}

	public static float getVideoCaptureRateFactor() {
		// fixme
		float capture_rate_factor = 1.0f;
		try {
			capture_rate_factor = Float.parseFloat(sharedPreferences.getString(CAPTURE_RATE, "1.0"));
		} catch (NumberFormatException e) {}
		if( MyDebug.LOG )
			Log.d(TAG, "capture_rate_factor: " + capture_rate_factor);
		if( Math.abs(capture_rate_factor - 1.0f) > 1.0e-5 ) {
			// check stored capture rate is valid
			if( MyDebug.LOG )
				Log.d(TAG, "check stored capture rate is valid");
			List<Float> supported_capture_rates = main_activity.getPreview().getSupportedVideoCaptureRates();
			if( MyDebug.LOG )
				Log.d(TAG, "supported_capture_rates: " + supported_capture_rates);
			boolean found = false;
			for(float this_capture_rate : supported_capture_rates) {
				if( Math.abs(capture_rate_factor - this_capture_rate) < 1.0e-5 ) {
					found = true;
					break;
				}
			}
			if( !found ) {
				Log.e(TAG, "stored capture_rate_factor: " + capture_rate_factor + " not supported");
				capture_rate_factor = 1.0f;
			}
		}
		return capture_rate_factor;
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

	public static boolean getPausePreviewPref() {
		if (!isVideoPref()) {
				return sharedPreferences.getBoolean(PAUSE_PREVIEW, false);
		}
		return false;
	}

	public static long getTimerPref() {
		String timer_value = sharedPreferences.getString(TIMER, "5");
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

	public static boolean getSelfieModePref() {
		return main_activity.selfie_mode;
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

	public static int getRecordAudioBitRatePref() {
		String value = sharedPreferences.getString(RECORD_AUDIO_BITRATE, "default");
		if (value.equals("default"))
			return 0;
		else {
			try{
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
	}

	public static int getRecordAudioSampleRatePref() {
		String value = sharedPreferences.getString(RECORD_AUDIO_SAMPLE_RATE, "default");
		if (value.equals("default"))
			return 0;
		else {
			try{
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
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

	public static int getRowSpaceYPref() {
		String value = sharedPreferences.getString(ROW_SPACE_Y + "_" + pref_camera_id, "default");
		if (value.equals("default"))
			return -1;
		try {
			return Integer.parseInt(value);
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}

	public static int getRowSpaceUVPref() {
		String value = sharedPreferences.getString(ROW_SPACE_UV + "_" + pref_camera_id, "default");
		if (value.equals("default"))
			return -1;
		try {
			return Integer.parseInt(value);
		}
		catch(NumberFormatException e) {
			return -1;
		}
	}
	
	/*##########################################
		
	##########################################*/

	public static boolean contains(final String prefKey) {
		return sharedPreferences.contains(prefKey);
	}

	public static boolean getBoolean(final String prefKey, final boolean defaultValue) {
		return sharedPreferences.getBoolean(prefKey, defaultValue);
	}

	public static void setBoolean(final String prefKey, final boolean value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(prefKey, value);
		editor.apply();
	}

	public static void putBoolean(final String prefKey, final boolean value) {
		if (prefEditor == null)
			prefEditor = sharedPreferences.edit();
		prefEditor.putBoolean(prefKey, value);
	}

	public static int getInt(final String prefKey, final int defaultValue) {
		return sharedPreferences.getInt(prefKey, defaultValue);
	}

	public static int getStringAsInt(final String prefKey, final int defaultValue) {
		int value = defaultValue;
		try {
			value = Integer.parseInt(sharedPreferences.getString(prefKey, Integer.toString(defaultValue)));
		} catch(NumberFormatException e) {}
		return value;
	}

	public static void setInt(final String prefKey, final int value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(prefKey, value);
		editor.apply();
	}

	public static void putInt(final String prefKey, final int value) {
		if (prefEditor == null)
			prefEditor = sharedPreferences.edit();
		prefEditor.putInt(prefKey, value);
	}

	public static long getLong(final String prefKey, final long defaultValue) {
		return sharedPreferences.getLong(prefKey, defaultValue);
	}

	public static long getStringAsLong(final String prefKey, final long defaultValue) {
		long value = defaultValue;
		try {
			value = Long.parseLong(sharedPreferences.getString(prefKey, Long.toString(defaultValue)));
		} catch(NumberFormatException e) {}
		return value;
	}

	public static void setLong(final String prefKey, final long value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(prefKey, value);
		editor.apply();
	}

	public static float getFloat(final String prefKey, final float defaultValue) {
		return sharedPreferences.getFloat(prefKey, defaultValue);
	}

	public static float getStringAsFloat(final String prefKey, final float defaultValue) {
		float value = defaultValue;
		try {
			value = Float.parseFloat(sharedPreferences.getString(prefKey, Float.toString(defaultValue)));
		} catch(NumberFormatException e) {}
		return value;
	}

	public static void setFloat(final String prefKey, final float value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putFloat(prefKey, value);
		editor.apply();
	}

	public static String getString(final String prefKey, final String defaultValue) {
		return sharedPreferences.getString(prefKey, defaultValue);
	}
	
	public static void setString(final String prefKey, final String value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(prefKey, value);
		editor.apply();
	}
	
	public static void putString(final String prefKey, final String value) {
		if (prefEditor == null)
			prefEditor = sharedPreferences.edit();
		prefEditor.putString(prefKey, value);
	}

	public static void clearPref(final String prefKey) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(prefKey);
		editor.apply();
	}
	
	public static void commit() {
		if (prefEditor != null) {
			prefEditor.apply();
			prefEditor = null;
		}
	}

	public static void exportPrefs(OutputStream output, String category, String[] pref_names) throws XmlPullParserException, IOException {
		if( MyDebug.LOG )
			Log.e(TAG, "exportPrefs()");
		
		Map<String, ?> prefs;
		final Map<String, ?> all = sharedPreferences.getAll();
		if (pref_names == null) {
			prefs = new TreeMap<String, Object>(all);
		} else {
			prefs = new TreeMap<String, Object>();
			if (category != null)
				((TreeMap)prefs).put("category", category);
			for (String name : pref_names) {
				Object value = all.get(name);
				if (value != null)
					((TreeMap)prefs).put(name, value);
			}
		}
		String comment = "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + "), fingerprint: " + Build.FINGERPRINT;
		XmlUtils.writeMapXml(prefs, comment, output);
	}

	public static void importPrefs(InputStream input, boolean clear) throws XmlPullParserException, IOException {
		if( MyDebug.LOG )
			Log.e(TAG, "importPrefs()");
			
		SharedPreferences.Editor editor = sharedPreferences.edit();
		Map<String, ?> entries = XmlUtils.readMapXml(input);
		if (clear)
			editor.clear();
		else {
			String category = (String)(entries.get("category"));
			if (category != null) {
				for (Category cat : PREF_CATEGORIES) {
					if (category.equals(cat.id)) {
						for (String key : cat.keys) {
							editor.remove(key);
						}
						break;
					}
				}
			}
		}
		for (Map.Entry<String, ?> entry : entries.entrySet()) {
			String key = entry.getKey();
			if (key.equals("category"))
				continue;

			Object value = entry.getValue();
			if (value instanceof Boolean)
				editor.putBoolean(key, ((Boolean)value).booleanValue());
			else if (value instanceof Float)
				editor.putFloat(key, ((Float)value).floatValue());
			else if (value instanceof Integer)
				editor.putInt(key, ((Integer)value).intValue());
			else if (value instanceof Long)
				editor.putLong(key, ((Long)value).longValue());
			else if (value instanceof String)
				editor.putString(key, (String)value);
		}

		editor.apply();
	}
	
	public static void reset(String[] pref_keys) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		for (String key : pref_keys)
			editor.remove(key);
		editor.apply();
	}

	public static void reset() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.clear();
		editor.putBoolean(Prefs.DONE_FIRST_TIME, true);
		editor.apply();
	}
}
