package com.caddish_hedgehog.hedgecam2;

import com.caddish_hedgehog.hedgecam2.Preview.Preview;
import com.caddish_hedgehog.hedgecam2.UI.FolderChooserDialog;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.TwoStatePreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Fragment to handle the Settings UI. Note that originally this was a
 *  PreferenceActivity rather than a PreferenceFragment which required all
 *  communication to be via the bundle (since this replaced the MainActivity,
 *  meaning we couldn't access data from that class. This no longer applies due
 *  to now using a PreferenceFragment, but I've still kept with transferring
 *  information via the bundle (for the most part, at least).
 */
public class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	private static final String TAG = "HedgeCam/MyPreferenceFragment";

	private static final String[] mode_groups = {
		"preference_category_photo_modes",
		"preference_category_flash_modes",
		"preference_category_focus_modes",
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		final SharedPreferences sharedPreferences = ((MainActivity)this.getActivity()).getSharedPrefs();
		if (sharedPreferences == null) return;

		addPreferencesFromResource(R.xml.preferences);

		final Bundle bundle = getArguments();
		final int cameraId = bundle.getInt("cameraId");
		if( MyDebug.LOG )
			Log.d(TAG, "cameraId: " + cameraId);
		final int nCameras = bundle.getInt("nCameras");
		if( MyDebug.LOG )
			Log.d(TAG, "nCameras: " + nCameras);

		final String hardware_level = bundle.getString("hardware_level");
		
		PreferenceGroup buttonsGroup = (PreferenceGroup)this.findPreference("preference_screen_ctrl_panel_buttons");
		PreferenceGroup modeGroup = (PreferenceGroup)this.findPreference("preference_screen_mode_panel_buttons");
		PreferenceGroup popupGroup = (PreferenceGroup)this.findPreference("preference_category_popup_elements");
		PreferenceGroup bugfixGroup = (PreferenceGroup)this.findPreference("preference_screen_bug_fix");

		final String [] color_effects_values = bundle.getStringArray("color_effects");
		final boolean supports_color_effects = color_effects_values != null && color_effects_values.length > 0;
		final String [] scene_modes_values = bundle.getStringArray("scene_modes");
		final boolean supports_scene_modes = scene_modes_values != null && scene_modes_values.length > 0;
		final String [] white_balances_values = bundle.getStringArray("white_balances");
		final boolean supports_white_balances = white_balances_values != null && white_balances_values.length > 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "supports_color_effects: " + supports_color_effects);
			Log.d(TAG, "supports_scene_modes: " + supports_scene_modes);
			Log.d(TAG, "supports_white_balances: " + supports_white_balances);
		}
		
		if (!supports_color_effects) {
			removePref(popupGroup, Prefs.POPUP_COLOR_EFFECT);
		}
		if (!supports_scene_modes) {
			removePref(popupGroup, Prefs.POPUP_SCENE_MODE);
		}
		if (!supports_white_balances) {
			removePref(popupGroup, Prefs.POPUP_WHITE_BALANCE);
		}
		if (!supports_white_balances && !supports_scene_modes && !supports_color_effects) {
			removePref("preference_category_popup_elements", Prefs.POPUP_EXPANDED_LISTS);
		}

		final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

		if( !supports_auto_stabilise ) {
			removePref(popupGroup, Prefs.POPUP_AUTO_STABILISE);
			removePref("preference_screen_photo_settings", Prefs.AUTO_STABILISE);
		}

		final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_face_detection: " + supports_face_detection);

		if( !supports_face_detection ) {
			removePref("preference_category_camera_controls", Prefs.FACE_DETECTION);
			removePref(buttonsGroup, Prefs.CTRL_PANEL_FACE_DETECTION);
			removePref(modeGroup, Prefs.MODE_PANEL_FACE_DETECTION);
			removePref("preference_screen_sounds", Prefs.FACE_DETECTION_SOUND);
		}

		final boolean supports_flash = bundle.getBoolean("supports_flash");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_flash: " + supports_flash);

		if( !supports_flash ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_FLASH);
			removePref(modeGroup, Prefs.MODE_PANEL_FLASH);
			removePref("preference_category_modes", "preference_category_flash_modes");
		}

		final boolean supports_focus = bundle.getBoolean("supports_focus");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_focus: " + supports_focus);

		if( !supports_focus ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_FOCUS);
			removePref(modeGroup, Prefs.MODE_PANEL_FOCUS);
			removePref(bugfixGroup, Prefs.STARTUP_FOCUS);
			removePref(bugfixGroup, Prefs.FORCE_FACE_FOCUS);
			removePref(bugfixGroup, Prefs.CENTER_FOCUS);
			removePref(bugfixGroup, Prefs.UPDATE_FOCUS_FOR_VIDEO);
			removePref("preference_category_modes", "preference_category_focus_modes");
		}

		final boolean supports_metering_area = bundle.getBoolean("supports_metering_area");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_metering_area: " + supports_metering_area);

		if( !supports_metering_area ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_EXPO_METERING_AREA);
			removePref(modeGroup, Prefs.MODE_PANEL_EXPO_METERING_AREA);
		}
		
		if ( !supports_focus && !supports_metering_area ){
			removePref("preference_screen_osd", Prefs.ALT_INDICATION);
		}

		final int preview_width = bundle.getInt("preview_width");
		final int preview_height = bundle.getInt("preview_height");
		final int [] preview_widths = bundle.getIntArray("preview_widths");
		final int [] preview_heights = bundle.getIntArray("preview_heights");
		final int [] video_widths = bundle.getIntArray("video_widths");
		final int [] video_heights = bundle.getIntArray("video_heights");

		final int resolution_width = bundle.getInt("resolution_width");
		final int resolution_height = bundle.getInt("resolution_height");
		final int [] widths = bundle.getIntArray("resolution_widths");
		final int [] heights = bundle.getIntArray("resolution_heights");
		if( widths != null && heights != null ) {
			CharSequence [] entries = new CharSequence[widths.length];
			CharSequence [] values = new CharSequence[widths.length];
			for(int i=0;i<widths.length;i++) {
				entries[i] = widths[i] + " x " + heights[i] + " " + Preview.getAspectRatioMPString(widths[i], heights[i]);
				values[i] = widths[i] + " " + heights[i];
			}
			ListPreference lp = (ListPreference)findPreference("preference_resolution");
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String resolution_preference_key = Prefs.getResolutionPreferenceKey();
			String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
			if( MyDebug.LOG )
				Log.d(TAG, "resolution_value: " + resolution_value);
			lp.setValue(resolution_value);
			// now set the key, so we save for the correct cameraId
			lp.setKey(resolution_preference_key);
		}
		else {
			removePref("preference_screen_photo_settings", "preference_resolution");
		}

		{
			final int n_quality = 13;
			CharSequence [] entries = new CharSequence[n_quality];
			CharSequence [] values = new CharSequence[n_quality];
			for(int i=0;i<n_quality;i++) {
				entries[i] = "" + ((20-i)*5) + "%";
				values[i] = "" + ((20-i)*5);
			}
			ListPreference lp = (ListPreference)findPreference(Prefs.QUALITY);
			lp.setEntries(entries);
			lp.setEntryValues(values);
		}
		
		final boolean supports_raw = bundle.getBoolean("supports_raw");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_raw: " + supports_raw);

		if( !supports_raw ) {
			removePref("preference_screen_photo_settings", Prefs.RAW);
		}
		else {
			Preference pref = findPreference(Prefs.RAW);
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked raw: " + newValue);
					if( newValue.equals("preference_raw_yes") ) {
						// we check done_raw_info every time, so that this works if the user selects RAW again without leaving and returning to Settings
						boolean done_raw_info = sharedPreferences.contains(Prefs.DONE_RAW_INFO);
						if( !done_raw_info ) {
							AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
							alertDialog.setTitle(R.string.preference_raw);
							alertDialog.setMessage(R.string.raw_info);
							alertDialog.setPositiveButton(android.R.string.ok, null);
							alertDialog.setNegativeButton(R.string.dont_show_again, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									if( MyDebug.LOG )
										Log.d(TAG, "user clicked dont_show_again for raw info dialog");
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putBoolean(Prefs.DONE_RAW_INFO, true);
									editor.apply();
								}
							});
							alertDialog.show();
						}
					}
					return true;
				}
			});			
		}

		final boolean supports_dro = bundle.getBoolean("supports_dro");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_dro: " + supports_dro);

		final boolean supports_hdr = bundle.getBoolean("supports_hdr");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_hdr: " + supports_hdr);

		if( !supports_hdr && !supports_dro) {
			removePref("preference_screen_photo_settings", "preference_category_hdr");
			removePref(popupGroup, Prefs.POPUP_HDR_TONEMAPPING);
			removePref(popupGroup, Prefs.POPUP_HDR_LOCAL_CONTRAST);
			removePref(popupGroup, Prefs.POPUP_HDR_N_TILES);
			removePref("preference_category_photo_modes", "preference_photo_mode_dro");
			removePref("preference_category_photo_modes", "preference_photo_mode_hdr");
		} else if( !supports_hdr ) {
			removePref("preference_category_hdr", Prefs.HDR_SAVE_EXPO);
			removePref("preference_category_hdr", Prefs.HDR_TONEMAPPING);
			removePref(popupGroup, Prefs.POPUP_HDR_TONEMAPPING);
			removePref("preference_category_photo_modes", "preference_photo_mode_hdr");
		}

		final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

		final int max_expo_bracketing_n_images = bundle.getInt("max_expo_bracketing_n_images");
		if( MyDebug.LOG )
			Log.d(TAG, "max_expo_bracketing_n_images: " + max_expo_bracketing_n_images);
			
		final boolean supports_focus_bracketing = bundle.getBoolean("supports_focus_bracketing");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_focus_bracketing: " + supports_focus_bracketing);

		final boolean supports_fast_burst = bundle.getBoolean("supports_fast_burst");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_fast_burst: " + supports_fast_burst);

		final boolean supports_nr = bundle.getBoolean("supports_nr");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_nr: " + supports_nr);

		if( !supports_nr ) {
			removePref("preference_screen_photo_settings", "preference_category_nr");
			removePref("preference_category_photo_modes", "preference_photo_mode_nr");
		}

		final boolean supports_exposure_compensation = bundle.getBoolean("supports_exposure_compensation");
		final int exposure_compensation_min = bundle.getInt("exposure_compensation_min");
		final int exposure_compensation_max = bundle.getInt("exposure_compensation_max");
		if( MyDebug.LOG ) {
			Log.d(TAG, "supports_exposure_compensation: " + supports_exposure_compensation);
			Log.d(TAG, "exposure_compensation_min: " + exposure_compensation_min);
			Log.d(TAG, "exposure_compensation_max: " + exposure_compensation_max);
		}

		final String [] isos = bundle.getStringArray("isos");
		final boolean supports_iso = ( isos != null && isos.length > 0 );
		if( MyDebug.LOG )
			Log.d(TAG, "supports_iso: " + supports_iso);

		final boolean supports_iso_range = bundle.getBoolean("supports_iso_range");
		final int iso_range_min = bundle.getInt("iso_range_min");
		final int iso_range_max = bundle.getInt("iso_range_max");
		if( MyDebug.LOG ) {
			Log.d(TAG, "supports_iso_range: " + supports_iso_range);
			Log.d(TAG, "iso_range_min: " + iso_range_min);
			Log.d(TAG, "iso_range_max: " + iso_range_max);
		}
		
		if (!supports_iso_range ) {
			removePref("preference_screen_sliders", Prefs.ISO_STEPS);
		}

		if( !supports_iso && !supports_iso_range ) {
			removePref(popupGroup, Prefs.POPUP_ISO);
			removePref(buttonsGroup, Prefs.CTRL_PANEL_ISO);
			removePref(modeGroup, Prefs.MODE_PANEL_ISO);
		}

		final boolean supports_exposure_time = bundle.getBoolean("supports_exposure_time");
		final long exposure_time_min = bundle.getLong("exposure_time_min");
		final long exposure_time_max = bundle.getLong("exposure_time_max");
		if( MyDebug.LOG ) {
			Log.d(TAG, "supports_exposure_time: " + supports_exposure_time);
			Log.d(TAG, "exposure_time_min: " + exposure_time_min);
			Log.d(TAG, "exposure_time_max: " + exposure_time_max);
		}
		
		if (!supports_exposure_time) {
			removePref("preference_screen_preview", Prefs.PREVIEW_MAX_EXPO);
		}

		final boolean supports_white_balance_temperature = bundle.getBoolean("supports_white_balance_temperature");
		final int white_balance_temperature_min = bundle.getInt("white_balance_temperature_min");
		final int white_balance_temperature_max = bundle.getInt("white_balance_temperature_max");
		if( MyDebug.LOG ) {
			Log.d(TAG, "supports_white_balance_temperature: " + supports_white_balance_temperature);
			Log.d(TAG, "white_balance_temperature_min: " + white_balance_temperature_min);
			Log.d(TAG, "white_balance_temperature_max: " + white_balance_temperature_max);
		}

		if( !supports_expo_bracketing || max_expo_bracketing_n_images <= 3 ) {
			removePref("preference_category_expo_bracketing", Prefs.EXPO_BRACKETING_N_IMAGES);
		}

		if( !supports_expo_bracketing ) {
			removePref("preference_screen_photo_settings", "preference_category_expo_bracketing");
			removePref("preference_category_photo_modes", "preference_photo_mode_expo_bracketing");
			removePref(popupGroup, Prefs.POPUP_EXPO_BRACKETING_STOPS);
		} else {
			String preference_key = Prefs.EXPO_BRACKETING_USE_ISO + "_" + cameraId;
			TwoStatePreference p = (TwoStatePreference)findPreference(Prefs.EXPO_BRACKETING_USE_ISO);
			p.setKey(preference_key);
			p.setChecked(sharedPreferences.getBoolean(preference_key, true));
		}
		
		if (!supports_focus_bracketing) {
			removePref("preference_screen_photo_settings", "preference_category_focus_bracketing");
			removePref("preference_category_photo_modes", "preference_photo_mode_focus_bracketing");
		}

		if (!supports_fast_burst) {
			removePref("preference_screen_photo_settings", "preference_category_fast_burst");
			removePref("preference_category_photo_modes", "preference_photo_mode_fast_burst");
		}

		if (!supports_focus_bracketing && !supports_fast_burst) {
			removePref(popupGroup, Prefs.POPUP_PHOTOS_COUNT);
		}

		if (!supports_expo_bracketing && !supports_hdr && !supports_dro && !supports_focus_bracketing && !supports_fast_burst && !supports_nr) {
			removePref(popupGroup, Prefs.POPUP_PHOTO_MODE);
			removePref(buttonsGroup, Prefs.CTRL_PANEL_PHOTO_MODE);
			removePref(modeGroup, Prefs.MODE_PANEL_PHOTO_MODE);
			removePref("preference_category_modes", "preference_category_photo_modes");
		}

		final String [] video_quality = bundle.getStringArray("video_quality");
		final String [] video_quality_string = bundle.getStringArray("video_quality_string");
		if( video_quality != null && video_quality_string != null ) {
			CharSequence [] entries = new CharSequence[video_quality.length];
			CharSequence [] values = new CharSequence[video_quality.length];
			for(int i=0;i<video_quality.length;i++) {
				entries[i] = video_quality_string[i];
				values[i] = video_quality[i];
			}
			ListPreference lp = (ListPreference)findPreference("preference_video_quality");
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String video_quality_preference_key = Prefs.getVideoQualityPreferenceKey();
			String video_quality_value = sharedPreferences.getString(video_quality_preference_key, "");
			if( MyDebug.LOG )
				Log.d(TAG, "video_quality_value: " + video_quality_value);
			lp.setValue(video_quality_value);
			// now set the key, so we save for the correct cameraId
			lp.setKey(video_quality_preference_key);
		}
		else {
			removePref("preference_screen_video_settings", "preference_video_quality");
		}
		final String current_video_quality = bundle.getString("current_video_quality");
		final int video_frame_width = bundle.getInt("video_frame_width");
		final int video_frame_height = bundle.getInt("video_frame_height");
		final int video_bit_rate = bundle.getInt("video_bit_rate");
		final int video_frame_rate = bundle.getInt("video_frame_rate");

		final boolean supports_force_video_4k = bundle.getBoolean("supports_force_video_4k");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_force_video_4k: " + supports_force_video_4k);
		if( !supports_force_video_4k || video_quality == null || video_quality_string == null ) {
			removePref("preference_category_video_advanced", Prefs.FORCE_VIDEO_4K);
		}
		
		final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);
		if( !supports_video_stabilization ) {
			removePref("preference_screen_video_settings", Prefs.VIDEO_STABILIZATION);
		}

		final boolean using_camera_2 = bundle.getBoolean("using_camera_2");

		final String noise_reduction_mode = bundle.getString("noise_reduction_mode");
		final String [] noise_reduction_modes = bundle.getStringArray("noise_reduction_modes");
		if( noise_reduction_modes != null && noise_reduction_modes.length > 1 ) {
			List<String> arr_entries = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_noise_reduction_entries));
			List<String> arr_values = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_noise_reduction_values));
			CharSequence [] entries = new CharSequence[noise_reduction_modes.length];
			CharSequence [] values = new CharSequence[noise_reduction_modes.length];
			for(int i=0; i<noise_reduction_modes.length; i++) {
				int index = arr_values.indexOf(noise_reduction_modes[i]);
				entries[i] = index == -1 ? noise_reduction_modes[i] : arr_entries.get(index);
				values[i] = noise_reduction_modes[i];
			}
			ListPreference lp = (ListPreference)findPreference(Prefs.NOISE_REDUCTION);
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String preference_key = Prefs.NOISE_REDUCTION + (using_camera_2 ? "_2" : "_1") + "_" + cameraId;
			if (noise_reduction_mode != null) {
				lp.setValue(noise_reduction_mode);
			}
			lp.setKey(preference_key);
		} else {
			removePref("preference_screen_filtering", Prefs.NOISE_REDUCTION);
		}

		final String edge_mode = bundle.getString("edge_mode");
		final String [] edge_modes = bundle.getStringArray("edge_modes");
		if( edge_modes != null && edge_modes.length > 1 ) {
			List<String> arr_entries = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_edge_entries));
			List<String> arr_values = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_edge_values));
			CharSequence [] entries = new CharSequence[edge_modes.length];
			CharSequence [] values = new CharSequence[edge_modes.length];
			for(int i=0; i<edge_modes.length; i++) {
				int index = arr_values.indexOf(edge_modes[i]);
				entries[i] = index == -1 ? edge_modes[i] : arr_entries.get(index);
				values[i] = edge_modes[i];
			}
			ListPreference lp = (ListPreference)findPreference(Prefs.EDGE);
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String preference_key = Prefs.EDGE + (using_camera_2 ? "_2" : "_1") + "_" + cameraId;
			if (edge_mode != null) {
				lp.setValue(edge_mode);
			}
			lp.setKey(preference_key);
		} else {
			removePref("preference_screen_filtering", Prefs.EDGE);
		}
		
		if (
			hardware_level != null && !hardware_level.equals("legacy") &&
			noise_reduction_modes != null && noise_reduction_modes.length > 1 &&
			edge_modes != null && edge_modes.length > 1
		) {
			String preference_key = Prefs.SMART_FILTER + "_" + cameraId;
			ListPreference lp = (ListPreference)findPreference(Prefs.SMART_FILTER);
			lp.setKey(preference_key);
			lp.setValue(sharedPreferences.getString(preference_key, "0"));
		} else {
			removePref("preference_screen_filtering", Prefs.SMART_FILTER);
			removePref("preference_category_hdr", Prefs.HDR_IGNORE_SMART_FILTER);
		}

		final String optical_stabilization_mode = bundle.getString("optical_stabilization_mode");
		final String [] optical_stabilization_modes = bundle.getStringArray("optical_stabilization_modes");
		if( optical_stabilization_modes != null && optical_stabilization_modes.length > 1 ) {
			List<String> arr_entries = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_optical_stabilization_entries));
			List<String> arr_values = Arrays.asList(getActivity().getResources().getStringArray(R.array.preference_optical_stabilization_values));
			CharSequence [] entries = new CharSequence[optical_stabilization_modes.length];
			CharSequence [] values = new CharSequence[optical_stabilization_modes.length];
			for(int i=0; i<optical_stabilization_modes.length; i++) {
				int index = arr_values.indexOf(optical_stabilization_modes[i]);
				entries[i] = index == -1 ? optical_stabilization_modes[i] : arr_entries.get(index);
				values[i] = optical_stabilization_modes[i];
			}
			ListPreference lp = (ListPreference)findPreference(Prefs.OPTICAL_STABILIZATION);
			lp.setEntries(entries);
			lp.setEntryValues(values);
			String preference_key = Prefs.OPTICAL_STABILIZATION + "_" + cameraId;
			if (optical_stabilization_mode != null) {
				lp.setValue(optical_stabilization_mode);
			}
			lp.setKey(preference_key);
		} else {
			removePref("preference_screen_filtering", Prefs.OPTICAL_STABILIZATION);
		}

		final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
		if( MyDebug.LOG )
			Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 || !can_disable_shutter_sound){
			removePref("preference_screen_sounds", Prefs.SHUTTER_SOUND_SELECT);
		}

		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
			// Some immersive modes require KITKAT - simpler to require Kitkat for any of the menu options
			removePref("preference_screen_gui", Prefs.IMMERSIVE_MODE);
		}

		if( !bundle.getBoolean("supports_lock") ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_LOCK);
			removePref(modeGroup, Prefs.MODE_PANEL_LOCK);
		}
		if( !bundle.getBoolean("supports_switch_camera") ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_SWITCH_CAMERA);
			removePref(modeGroup, Prefs.MODE_PANEL_SWITCH_CAMERA);
		}
		if( !bundle.getBoolean("supports_exposure_button") ) {
			removePref(buttonsGroup, Prefs.CTRL_PANEL_EXPOSURE);
			removePref(modeGroup, Prefs.MODE_PANEL_EXPOSURE);
		}

		final String [] focus_values = bundle.getStringArray("focus_values");
		boolean supports_manual_focus = false;
		if( using_camera_2 ) {
			removePref("preference_screen_preview", "preference_category_preview_advanced");
			
			if( focus_values != null && focus_values.length > 0 ) {
				for(int i=0;i<focus_values.length;i++) {
					if (focus_values[i].equals("focus_mode_manual2")) {
						supports_manual_focus = true;
						break;
					}
				}
			}
		} else {
			if (!supports_iso) {
				removePref("preference_screen_osd", Prefs.SHOW_ISO);
			}

			removePref("preference_category_expo_bracketing", Prefs.CAMERA2_FAST_BURST);
			removePref("preference_screen_bug_fix", Prefs.CAMERA2_FAKE_FLASH);
		}
		
		if (supports_manual_focus) {
			String preference_key = Prefs.MIN_FOCUS_DISTANCE + "_" + cameraId;
			ListPreference lp = (ListPreference)findPreference(Prefs.MIN_FOCUS_DISTANCE);
			lp.setValue(sharedPreferences.getString(preference_key, "default"));
			lp.setKey(preference_key);
		} else {
			removePref("preference_screen_sliders", Prefs.FOCUS_RANGE);
			removePref("preference_screen_bug_fix", Prefs.MIN_FOCUS_DISTANCE);
		}
		
		boolean has_modes = false;
		for(String group_name : mode_groups) {
			PreferenceGroup group = (PreferenceGroup)this.findPreference(group_name);
			if (group != null) {
				for (int i = 0; i < group.getPreferenceCount(); i++) {
					has_modes = true;
					TwoStatePreference pref = (TwoStatePreference)group.getPreference(i);
					String pref_key = pref.getKey() + "_" + cameraId;
					pref.setKey(pref_key);
					pref.setChecked(sharedPreferences.getBoolean(pref_key, true));
				}
			}
		}
		if (!has_modes) {
			removePref("preference_screen_popup", "preference_category_modes");
		}

		final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
		if( MyDebug.LOG )
			Log.d(TAG, "supports_camera2: " + supports_camera2);
		if( supports_camera2 ) {
			final Preference pref = findPreference(Prefs.USE_CAMERA2);
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals(Prefs.USE_CAMERA2) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked camera2 API - need to restart");
						// see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
						Intent i = getActivity().getBaseContext().getPackageManager().getLaunchIntentForPackage( getActivity().getBaseContext().getPackageName() );
						i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						startActivity(i);
						return false;
					}
					return false;
				}
			});
		}
		else {
			removePref("preference_category_mics", Prefs.USE_CAMERA2);
		}
		
/*		{
			final Preference pref = findPreference("preference_online_help");
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals("preference_online_help") ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked online help");
						MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
						main_activity.launchOnlineHelp();
						return false;
					}
					return false;
				}
			});
		}*/

		/*{
			EditTextPreference edit = (EditTextPreference)findPreference(Prefs.SAVE_LOCATION);
			InputFilter filter = new InputFilter() { 
				// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
				String disallowed = "|\\?*<\":>";
				public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) { 
					for(int i=start;i<end;i++) { 
						if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
							return ""; 
						}
					} 
					return null; 
				}
			}; 
			edit.getEditText().setFilters(new InputFilter[]{filter});		 	
		}*/
		{
			Preference pref = findPreference(Prefs.SAVE_LOCATION);
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked save location");
					MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
					if( main_activity.getStorageUtils().isUsingSAF() ) {
						main_activity.openFolderChooserDialogSAF(true);
						return true;
					}
					else {
						FolderChooserDialog fragment = new SaveFolderChooserDialog();
						fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
						return true;
					}
				}
			});			
		}

		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
			removePref("preference_screen_files", Prefs.USING_SAF);
		}
		else {
			final Preference pref = findPreference(Prefs.USING_SAF);
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals(Prefs.USING_SAF) ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked saf");
						if( sharedPreferences.getBoolean(Prefs.USING_SAF, false) ) {
							if( MyDebug.LOG )
								Log.d(TAG, "saf is now enabled");
							// seems better to alway re-show the dialog when the user selects, to make it clear where files will be saved (as the SAF location in general will be different to the non-SAF one)
							//String uri = sharedPreferences.getString(Prefs.SAVE_LOCATION_SAF, "");
							//if( uri.length() == 0 )
							{
								MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
								Toast.makeText(main_activity, R.string.saf_select_save_location, Toast.LENGTH_SHORT).show();
								main_activity.openFolderChooserDialogSAF(true);
							}
						}
						else {
							if( MyDebug.LOG )
								Log.d(TAG, "saf is now disabled");
						}
					}
					return false;
				}
			});
		}

/*		{
			final Preference pref = findPreference("preference_donate");
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals("preference_donate") ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked to donate");
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.getDonateLink()));
						startActivity(browserIntent);
						return false;
					}
					return false;
				}
			});
		}*/

		{
			final Preference pref = findPreference("preference_about");
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals("preference_about") ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked about");
						AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
						alertDialog.setTitle(getActivity().getResources().getString(R.string.preference_about));
						final StringBuilder about_string = new StringBuilder();
						String version = "UNKNOWN_VERSION";
						int version_code = -1;
						try {
							PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
							version = pInfo.versionName;
							version_code = pInfo.versionCode;
						}
						catch(NameNotFoundException e) {
							if( MyDebug.LOG )
								Log.d(TAG, "NameNotFoundException exception trying to get version number");
							e.printStackTrace();
						}
						about_string.append("HedgeCam v");
						about_string.append(version);
						about_string.append("\n\n(c) 2016-2017 alex82 aka Caddish Hedgehog");
						about_string.append("\nBased on Open Camera by Mark Harman");
						about_string.append("\nReleased under the GPL v3 or later");
						final String translation = getActivity().getResources().getString(R.string.translation_author);
						if (translation.length() > 0) {
							about_string.append("\n\nTranslation: ");
							about_string.append(translation);
						}
						alertDialog.setMessage(about_string);
						alertDialog.setPositiveButton(android.R.string.ok, null);
						alertDialog.show();
						return false;
					}
					return false;
				}
			});
		}
		{
			final Preference pref = findPreference("preference_info");
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals("preference_info") ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked info");
						AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
						alertDialog.setTitle(getActivity().getResources().getString(R.string.preference_info));
						final StringBuilder about_string = new StringBuilder();
						String version = "UNKNOWN_VERSION";
						int version_code = -1;
						try {
							PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
							version = pInfo.versionName;
							version_code = pInfo.versionCode;
						}
						catch(NameNotFoundException e) {
							if( MyDebug.LOG )
								Log.d(TAG, "NameNotFoundException exception trying to get version number");
							e.printStackTrace();
						}
						about_string.append("HedgeCam v");
						about_string.append(version);
						about_string.append("\nPackage: ");
						about_string.append(MyPreferenceFragment.this.getActivity().getPackageName());
						about_string.append("\nVersion code: ");
						about_string.append(version_code);
						about_string.append("\nAndroid API version: ");
						about_string.append(Build.VERSION.SDK_INT);
						about_string.append("\nDevice manufacturer: ");
						about_string.append(Build.MANUFACTURER);
						about_string.append("\nDevice model: ");
						about_string.append(Build.MODEL);
						about_string.append("\nDevice code name: ");
						about_string.append(Build.DEVICE);
						about_string.append("\nDevice hardware: ");
						about_string.append(Build.HARDWARE);
						about_string.append("\nBoard name: ");
						about_string.append(Build.BOARD);
						about_string.append("\nLanguage: ");
						about_string.append(Locale.getDefault().getLanguage());
						{
							ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
							about_string.append("\nStandard max heap?: ");
							about_string.append(activityManager.getMemoryClass());
							about_string.append("\nLarge max heap?: ");
							about_string.append(activityManager.getLargeMemoryClass());
						}
						{
							Point display_size = new Point();
							Display display = MyPreferenceFragment.this.getActivity().getWindowManager().getDefaultDisplay();
							display.getSize(display_size);
							about_string.append("\nDisplay size: ");
							about_string.append(display_size.x);
							about_string.append("x");
							about_string.append(display_size.y);
						}
						about_string.append("\nCurrent camera ID: ");
						about_string.append(cameraId);
						about_string.append("\nNo. of cameras: ");
						about_string.append(nCameras);
						about_string.append("\nCamera API: ");
						about_string.append(using_camera_2 ? "2" : "1");
						if (hardware_level != null) {
							about_string.append("\nHardware level: ");
							about_string.append(hardware_level);
						}
						{
							String last_video_error = sharedPreferences.getString("last_video_error", "");
							if( last_video_error != null && last_video_error.length() > 0 ) {
								about_string.append("\nLast video error: ");
								about_string.append(last_video_error);
							}
						}
						if( preview_widths != null && preview_heights != null ) {
							about_string.append("\nPreview resolutions: ");
							for(int i=0;i<preview_widths.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(preview_widths[i]);
								about_string.append("x");
								about_string.append(preview_heights[i]);
							}
						}
						about_string.append("\nPreview resolution: ");
						about_string.append(preview_width);
						about_string.append("x");
						about_string.append(preview_height);
						if( widths != null && heights != null ) {
							about_string.append("\nPhoto resolutions: ");
							for(int i=0;i<widths.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(widths[i]);
								about_string.append("x");
								about_string.append(heights[i]);
							}
						}
						about_string.append("\nPhoto resolution: ");
						about_string.append(resolution_width);
						about_string.append("x");
						about_string.append(resolution_height);
						if( video_quality != null ) {
							about_string.append("\nVideo qualities: ");
							for(int i=0;i<video_quality.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(video_quality[i]);
							}
						}
						if( video_widths != null && video_heights != null ) {
							about_string.append("\nVideo resolutions: ");
							for(int i=0;i<video_widths.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(video_widths[i]);
								about_string.append("x");
								about_string.append(video_heights[i]);
							}
						}
						about_string.append("\nVideo quality: ");
						about_string.append(current_video_quality);
						about_string.append("\nVideo frame width: ");
						about_string.append(video_frame_width);
						about_string.append("\nVideo frame height: ");
						about_string.append(video_frame_height);
						about_string.append("\nVideo bit rate: ");
						about_string.append(video_bit_rate);
						about_string.append("\nVideo frame rate: ");
						about_string.append(video_frame_rate);
						about_string.append("\nAuto-stabilise?: ");
						about_string.append(getString(supports_auto_stabilise ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nAuto-stabilise enabled?: ");
						about_string.append(sharedPreferences.getBoolean(Prefs.AUTO_STABILISE, false));
						about_string.append("\nFace detection?: ");
						about_string.append(getString(supports_face_detection ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nRAW?: ");
						about_string.append(getString(supports_raw ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nHDR?: ");
						about_string.append(getString(supports_hdr ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nExpo?: ");
						about_string.append(getString(supports_expo_bracketing ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nExpo compensation?: ");
						about_string.append(getString(supports_exposure_compensation ? R.string.about_available : R.string.about_not_available));
						if( supports_exposure_compensation ) {
							about_string.append("\nExposure compensation range: ");
							about_string.append(exposure_compensation_min);
							about_string.append(" to ");
							about_string.append(exposure_compensation_max);
						}
						about_string.append("\nManual ISO?: ");
						about_string.append(getString(supports_iso_range ? R.string.about_available : R.string.about_not_available));
						if( supports_iso_range ) {
							about_string.append("\nISO range: ");
							about_string.append(iso_range_min);
							about_string.append(" to ");
							about_string.append(iso_range_max);
						}
						about_string.append("\nManual exposure?: ");
						about_string.append(getString(supports_exposure_time ? R.string.about_available : R.string.about_not_available));
						if( supports_exposure_time ) {
							about_string.append("\nExposure range: ");
							about_string.append(exposure_time_min);
							about_string.append(" to ");
							about_string.append(exposure_time_max);
						}
						about_string.append("\nManual WB?: ");
						about_string.append(getString(supports_white_balance_temperature ? R.string.about_available : R.string.about_not_available));
						if( supports_white_balance_temperature ) {
							about_string.append("\nWB temperature: ");
							about_string.append(white_balance_temperature_min);
							about_string.append(" to ");
							about_string.append(white_balance_temperature_max);
						}
						about_string.append("\nVideo stabilization?: ");
						about_string.append(getString(supports_video_stabilization ? R.string.about_available : R.string.about_not_available));
						about_string.append("\nCan disable shutter sound?: ");
						about_string.append(getString(can_disable_shutter_sound ? R.string.answer_yes : R.string.answer_no));
						about_string.append("\nFlash modes: ");
						String [] flash_values = bundle.getStringArray("flash_values");
						if( flash_values != null && flash_values.length > 0 ) {
							for(int i=0;i<flash_values.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(flash_values[i]);
							}
						}
						else {
							about_string.append("None");
						}
						about_string.append("\nFocus modes: ");
						if( focus_values != null && focus_values.length > 0 ) {
							for(int i=0;i<focus_values.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(focus_values[i]);
							}
						}
						else {
							about_string.append("None");
						}
						if( supports_color_effects ) {
							about_string.append("\nColor effects: ");
							for(int i=0;i<color_effects_values.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(color_effects_values[i]);
							}
						}
						else {
							about_string.append("None");
						}
						if( supports_scene_modes ) {
							about_string.append("\nScene modes: ");
							for(int i=0;i<scene_modes_values.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(scene_modes_values[i]);
							}
						}
						else {
							about_string.append("None");
						}
						if( supports_white_balances ) {
							about_string.append("\nWhite balances: ");
							for(int i=0;i<white_balances_values.length;i++) {
								if( i > 0 ) {
									about_string.append(", ");
								}
								about_string.append(white_balances_values[i]);
							}
						}
						else {
							about_string.append("None");
						}
						if( !using_camera_2 ) {
							about_string.append("\nISOs: ");
							String[] isos = bundle.getStringArray("isos");
							if (isos != null && isos.length > 0) {
								for (int i = 0; i < isos.length; i++) {
									if (i > 0) {
										about_string.append(", ");
									}
									about_string.append(isos[i]);
								}
							} else {
								about_string.append("None");
							}
							String iso_key = bundle.getString("iso_key");
							if (iso_key != null) {
								about_string.append("\nISO key: ");
								about_string.append(iso_key);
							}
						}

						if (noise_reduction_modes != null && noise_reduction_modes.length > 0) {
							about_string.append("\nNoise reduction modes: ");
							for (int i = 0; i < noise_reduction_modes.length; i++) {
								if (i > 0) {
									about_string.append(", ");
								}
								about_string.append(noise_reduction_modes[i]);
							}
							about_string.append("\nNoise reduction mode: ");
							if (noise_reduction_mode == null) about_string.append("None");
							else about_string.append(noise_reduction_mode);
						}
						if (edge_modes != null && edge_modes.length > 0) {
							about_string.append("\nEdge modes: ");
							for (int i = 0; i < edge_modes.length; i++) {
								if (i > 0) {
									about_string.append(", ");
								}
								about_string.append(edge_modes[i]);
							}
							about_string.append("\nEdge mode: ");
							if (edge_mode == null) about_string.append("None");
							else about_string.append(edge_mode);
						}

						if (optical_stabilization_modes != null && optical_stabilization_modes.length > 0) {
							about_string.append("\nOptical stabilization modes: ");
							for (int i = 0; i < optical_stabilization_modes.length; i++) {
								if (i > 0) {
									about_string.append(", ");
								}
								about_string.append(optical_stabilization_modes[i]);
							}
							about_string.append("\nOptical stabilization: ");
							if (optical_stabilization_mode == null) about_string.append("None");
							else about_string.append(optical_stabilization_mode);
						}

						about_string.append("\nUsing SAF?: ");
						about_string.append(sharedPreferences.getBoolean(Prefs.USING_SAF, false));
						String save_location = sharedPreferences.getString(Prefs.SAVE_LOCATION, "OpenCamera");
						about_string.append("\nSave Location: ");
						about_string.append(save_location);
						String save_location_saf = sharedPreferences.getString(Prefs.SAVE_LOCATION_SAF, "");
						about_string.append("\nSave Location SAF: ");
						about_string.append(save_location_saf);

						about_string.append("\nParameters: ");
						String parameters_string = bundle.getString("parameters_string");
						if( parameters_string != null ) {
							about_string.append(parameters_string);
						}
						else {
							about_string.append("None");
						}
						
						alertDialog.setMessage(about_string);
						alertDialog.setPositiveButton(android.R.string.ok, null);
						alertDialog.setNegativeButton(R.string.about_copy_to_clipboard, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								if( MyDebug.LOG )
									Log.d(TAG, "user clicked copy to clipboard");
							 	ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE); 
							 	ClipData clip = ClipData.newPlainText("About", about_string);
							 	clipboard.setPrimaryClip(clip);
							}
						});
						alertDialog.show();
						return false;
					}
					return false;
				}
			});
		}

		{
			final Preference pref = findPreference("preference_reset");
			pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference arg0) {
					if( pref.getKey().equals("preference_reset") ) {
						if( MyDebug.LOG )
							Log.d(TAG, "user clicked reset");
						new AlertDialog.Builder(MyPreferenceFragment.this.getActivity())
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.preference_reset)
						.setMessage(R.string.preference_reset_question)
						.setPositiveButton(R.string.answer_yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if( MyDebug.LOG )
									Log.d(TAG, "user confirmed reset");
								SharedPreferences.Editor editor = sharedPreferences.edit();
								editor.clear();
								editor.putBoolean(Prefs.DONE_FIRST_TIME, true);
								editor.apply();
								MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
								main_activity.setDeviceDefaults();
								if( MyDebug.LOG )
									Log.d(TAG, "user clicked reset - need to restart");
								// see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
								Intent i = getActivity().getBaseContext().getPackageManager().getLaunchIntentForPackage( getActivity().getBaseContext().getPackageName() );
								i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
								startActivity(i);
							}
						})
						.setNegativeButton(R.string.answer_no, null)
						.show();
					}
					return false;
				}
			});
		}
	}
	
	public static class SaveFolderChooserDialog extends FolderChooserDialog {
		@Override
		public void onDismiss(DialogInterface dialog) {
			if( MyDebug.LOG )
				Log.d(TAG, "FolderChooserDialog dismissed");
			// n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
			// so we access the MainActivity via the fragment's getActivity().
			MainActivity main_activity = (MainActivity)this.getActivity();
			String new_save_location = this.getChosenFolder();
			main_activity.updateSaveFolder(new_save_location);
			super.onDismiss(dialog);
		}
	}

	public void onResume() {
		super.onResume();
		// prevent fragment being transparent
		// note, setting color here only seems to affect the "main" preference fragment screen, and not sub-screens
		// note, on Galaxy Nexus Android 4.3 this sets to black rather than the dark grey that the background theme should be (and what the sub-screens use); works okay on Nexus 7 Android 5
		// we used to use a light theme for the PreferenceFragment, but mixing themes in same activity seems to cause problems (e.g., for EditTextPreference colors)
		TypedArray array = getActivity().getTheme().obtainStyledAttributes(new int[] {  
				android.R.attr.colorBackground
		});
		int backgroundColor = array.getColor(0, Color.BLACK);
		/*if( MyDebug.LOG ) {
			int r = (backgroundColor >> 16) & 0xFF;
			int g = (backgroundColor >> 8) & 0xFF;
			int b = (backgroundColor >> 0) & 0xFF;
			Log.d(TAG, "backgroundColor: " + r + " , " + g + " , " + b);
		}*/
		getView().setBackgroundColor(backgroundColor);
		array.recycle();

		SharedPreferences sharedPreferences = ((MainActivity)this.getActivity()).getSharedPrefs();
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	public void onPause() {
		super.onPause();
	}

	/* So that manual changes to the checkbox/switch preferences, while the preferences are showing, show up;
	 * in particular, needed for preference_using_saf, when the user cancels the SAF dialog (see
	 * MainActivity.onActivityResult).
	 */
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSharedPreferenceChanged");
		Preference pref = findPreference(key);
		if( pref instanceof TwoStatePreference ){
			TwoStatePreference twoStatePref = (TwoStatePreference)pref;
			twoStatePref.setChecked(prefs.getBoolean(key, true));
		}
	}
	
	private void removePref(final String pref_group_name, final String pref_name) {
		PreferenceGroup pg = (PreferenceGroup)this.findPreference(pref_group_name);
		removePref(pg, pref_name);
	}

	private void removePref(PreferenceGroup pg, final String pref_name) {
		if (pg != null) {
			Preference pref = findPreference(pref_name);
			if (pref != null) pg.removePreference(pref);
		}
	}
}
