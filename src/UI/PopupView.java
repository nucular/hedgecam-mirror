package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.IconView;
import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyApplicationInterface;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

/** This defines the UI for the "popup" button, that provides quick access to a
 *  range of options.
 */
public class PopupView extends LinearLayout {
	public enum PopupType {
		Main,
		Flash,
		Focus,
		PhotoMode,
		WhiteBalance,
		SceneMode,
		ColorEffect,
		ISO
	}
	private static final String TAG = "HedgeCam/PopupView";
	public static final float ALPHA_BUTTON_SELECTED = 1.0f;
	public static final float ALPHA_BUTTON = 0.4f;
	
	private final MainActivity main_activity;
	private final Resources resources;
	private final SharedPreferences sharedPreferences;
	private final int camera_id;

	private final int total_width;
	private final int button_min_width;
	private final int padding;

	private int picture_size_index = -1;
	private int video_size_index = -1;
	private int timer_index = -1;
	private int burst_mode_index = -1;
	private int burst_interval_index = -1;
	private int grid_index = -1;
	private int stops_up_index = -1;
	private int stops_down_index = -1;
	private int hdr_tonemapping_index = -1;
	private int hdr_unsharp_mask_index = -1;
	private int hdr_unsharp_mask_radius_index = -1;
	private int hdr_local_contrast_index = -1;
	private int hdr_n_tiles_index = -1;
	private int photos_count_index = -1;
	
	private final int elements_gap;
	private int arrow_width;
	private final int arrow_height;

	private final float text_size_main;
	private final float text_size_title;
	private final float text_size_arrow;
	private final float text_size_button;
	private final float text_size_mode;
	
	private boolean expand_lists;
	private final boolean negative;
	private ColorMatrixColorFilter neg_filter = null;

	private final Map<String, View> popup_buttons = new Hashtable<>();
	
	private final Typeface icon_font;

	public PopupView(Context context, PopupType popup_type) {
		super(context);
		if( MyDebug.LOG )
			Log.d(TAG, "new PopupView: " + this);

		final long debug_time = System.nanoTime();
		this.setOrientation(LinearLayout.VERTICAL);

		main_activity = (MainActivity)this.getContext();
		resources = main_activity.getResources();
		final Preview preview = main_activity.getPreview();
		sharedPreferences = main_activity.getSharedPrefs();
		camera_id = Prefs.getCameraIdPref();
		
		icon_font = IconView.getTypeface(main_activity);
		
		padding = resources.getDimensionPixelSize(R.dimen.popup_padding);
		button_min_width = resources.getDimensionPixelSize(R.dimen.popup_button_min_width);
		elements_gap = resources.getDimensionPixelSize(R.dimen.popup_elements_gap);
		arrow_width = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
		arrow_height = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
		
		int max_buttons_count = 6;
		switch( sharedPreferences.getString(Prefs.POPUP_SIZE, "normal") ) {
			case "small":
				max_buttons_count = 5;
				arrow_width *= 0.7f;
				break;
			case "large":
				max_buttons_count = 7;
				break;
			case "xlarge":
				max_buttons_count = 8;
				break;
		}
		total_width = button_min_width*max_buttons_count;

		this.setMinimumWidth((int)((float)total_width*0.875f+padding*2));
		this.setPadding(padding, padding-elements_gap, padding, padding);

		
		switch( sharedPreferences.getString(Prefs.POPUP_FONT_SIZE, "normal") ) {
			case "small":
				text_size_main = resources.getDimension(R.dimen.popup_text_small_default);
				text_size_title = resources.getDimension(R.dimen.popup_text_small_title);
				text_size_arrow = resources.getDimension(R.dimen.popup_text_small_arrow);
				text_size_button = resources.getDimension(R.dimen.popup_text_small_button);
				text_size_mode = resources.getDimension(R.dimen.popup_text_small_mode);
				break;
			case "large":
				text_size_main = resources.getDimension(R.dimen.popup_text_large_default);
				text_size_title = resources.getDimension(R.dimen.popup_text_large_title);
				text_size_arrow = resources.getDimension(R.dimen.popup_text_large_arrow);
				text_size_button = resources.getDimension(R.dimen.popup_text_large_button);
				text_size_mode = resources.getDimension(R.dimen.popup_text_large_mode);
				break;
			case "xlarge":
				text_size_main = resources.getDimension(R.dimen.popup_text_xlarge_default);
				text_size_title = resources.getDimension(R.dimen.popup_text_xlarge_title);
				text_size_arrow = resources.getDimension(R.dimen.popup_text_xlarge_arrow);
				text_size_button = resources.getDimension(R.dimen.popup_text_xlarge_button);
				text_size_mode = resources.getDimension(R.dimen.popup_text_xlarge_mode);
				break;
			default:
				text_size_main = resources.getDimension(R.dimen.popup_text_normal_default);
				text_size_title = resources.getDimension(R.dimen.popup_text_normal_title);
				text_size_arrow = resources.getDimension(R.dimen.popup_text_normal_arrow);
				text_size_button = resources.getDimension(R.dimen.popup_text_normal_button);
				text_size_mode = resources.getDimension(R.dimen.popup_text_normal_mode);
				break;
		}
		
		
		switch (sharedPreferences.getString(Prefs.POPUP_COLOR, "black")) {
			case "light_gray":
			case "white":
				negative = true;
				break;
			default:
				negative = false;
		}
		
		if (negative) {
			neg_filter = new ColorMatrixColorFilter(new float[] {
			-1.0f,		0,		0,		0,	255, // red
				0,	-1.0f,		0,		0,	255, // green
				0,		0,	-1.0f,		0,	255, // blue
				0,		0,		0,	1.0f,	0	// alpha
			});
		}

		expand_lists = sharedPreferences.getBoolean(Prefs.POPUP_EXPANDED_LISTS, false);
		
		if (popup_type == PopupType.Flash) {
			List<String> supported_flash_values = preview.getSupportedFlashValues();
			addTextButtonOptionsToPopup(supported_flash_values, getResources().getString(R.string.flash_mode),
					R.array.flash_icons, R.array.flash_values, R.array.flash_entries, R.array.flash_keys,
					preview.getCurrentFlashValue(), "TEST_FLASH", new ButtonOptionsPopupListener() {
				@Override
				public void onClick(String option) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked flash: " + option);
					preview.updateFlash(option);
					main_activity.getMainUI().setFlashIcon();
					main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
				}
			});
		} else if (popup_type == PopupType.Focus) {
			// make a copy of getSupportedFocusValues() so we can modify it
			List<String> supported_focus_values = preview.getSupportedFocusValues();
			if( supported_focus_values != null ) {
				supported_focus_values = new ArrayList<String>(supported_focus_values);
				if( preview.isVideo() ) {
					supported_focus_values.remove("focus_mode_continuous_picture");
				}
				else {
					supported_focus_values.remove("focus_mode_continuous_video");
				}
			}
			addTextButtonOptionsToPopup(supported_focus_values, getResources().getString(R.string.focus_mode),
					R.array.focus_mode_icons, R.array.focus_mode_values, R.array.focus_mode_entries, R.array.focus_mode_keys,
					preview.getCurrentFocusValue(), "TEST_FOCUS", new ButtonOptionsPopupListener() {
				@Override
				public void onClick(String option) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked focus: " + option);
					final String old_value = preview.getCurrentFocusValue();
					preview.updateFocus(option, false, true);
					if (!preview.isTakingPhotoOrOnTimer() && 
							!(preview.isVideo() && main_activity.getSharedPrefs()
							.getBoolean(Prefs.UPDATE_FOCUS_FOR_VIDEO, false))) {
						if (!preview.usingCamera2API() && (option.equals("focus_mode_infinity") || option.equals("focus_mode_edof"))) {
							preview.reopenCamera();
						}

						if (option.equals("focus_mode_manual2")) {
							main_activity.getMainUI().setManualFocusSeekbars();
							main_activity.getMainUI().layoutSeekbars();
						} else {
							if (old_value != null && old_value.equals("focus_mode_manual2")) {
								main_activity.getMainUI().setManualFocusSeekbars();
								main_activity.getMainUI().layoutSeekbars();
							}
							if (old_value != null && (old_value.equals("focus_mode_infinity") || old_value.equals("focus_mode_edof")))
								preview.setCenterFocus();
						}
					}
					main_activity.getMainUI().setFocusIcon();
					main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
				}
			});
		} else if (popup_type == PopupType.PhotoMode) {
			List<String> supported_values = getSupportedPhotoModes(main_activity);
			addTextButtonOptionsToPopup(supported_values, getResources().getString(R.string.photo_mode),
					R.array.photo_mode_icons, R.array.photo_mode_values, R.array.photo_mode_entries, R.array.photo_mode_keys,
					Prefs.getPhotoModePref(), "TEST_PHOTO_MODE", new ButtonOptionsPopupListener() {
				@Override
				public void onClick(String option) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked photo mode: " + option);

					clickedPhotoMode(main_activity, option, true);
				}
			});
		} else if (popup_type == PopupType.WhiteBalance) {
			if (preview.getCameraController() != null) {
				List<String> supported_white_balances = preview.getSupportedWhiteBalances();
				addRadioOptionsToPopup(supported_white_balances, getResources().getString(R.string.white_balance), "wb_",
						Prefs.WHITE_BALANCE, sharedPreferences.getString(Prefs.WHITE_BALANCE, preview.getCameraController().getDefaultWhiteBalance()),
						"TEST_WHITE_BALANCE", new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						switchToWhiteBalance(selected_value);
						main_activity.getMainUI().setWhiteBalanceIcon();
					}
				});
			}
		} else if (popup_type == PopupType.SceneMode) {
			if (preview.getCameraController() != null) {
				List<String> supported_scene_modes = preview.getSupportedSceneModes();
				addRadioOptionsToPopup(supported_scene_modes, getResources().getString(R.string.scene_mode), "sm_",
						Prefs.SCENE_MODE, sharedPreferences.getString(Prefs.SCENE_MODE, preview.getCameraController().getDefaultSceneMode()),
						"TEST_SCENE_MODE", new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						if( preview.getCameraController() != null ) {
							if( preview.getCameraController().sceneModeAffectsFunctionality() ) {
								// need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
								main_activity.updateForSettings(getResources().getString(R.string.scene_mode) + ": " + main_activity.getStringResourceByName("sm_", selected_value));
								main_activity.getMainUI().closePopup();
							}
							else {
								preview.getCameraController().setSceneMode(selected_value);
								// keep popup open
							}
							main_activity.getMainUI().setSceneModeIcon();
						}
					}
				});
			}
		} else if (popup_type == PopupType.ColorEffect) {
			if (preview.getCameraController() != null) {
				List<String> supported_color_effects = preview.getSupportedColorEffects();
				addRadioOptionsToPopup(supported_color_effects, getResources().getString(R.string.color_effect), "ce_",
						Prefs.COLOR_EFFECT, sharedPreferences.getString(Prefs.COLOR_EFFECT, preview.getCameraController().getDefaultColorEffect()),
						"TEST_COLOR_EFFECT", new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						if( preview.getCameraController() != null ) {
							preview.getCameraController().setColorEffect(selected_value);
							main_activity.getMainUI().setColorEffectIcon();
						}
						// keep popup open
					}
				});
			}
		} else if (popup_type == PopupType.ISO) {
			if (preview.getCameraController() != null) {
				List<String> supported_isos = getSupportedISOs(main_activity);
				final String current_iso = Prefs.getISOPref();
				addRadioOptionsToPopup(supported_isos, getResources().getString(R.string.iso), "iso_",
						Prefs.getISOKey(), current_iso,
						"TEST_ISO", new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						Prefs.setISOPref(selected_value);
						
						if (current_iso.equals("manual") || selected_value.equals("manual")) {
							main_activity.getMainUI().setManualIsoSeekbars();
							main_activity.getMainUI().updateSeekbars();
							main_activity.getMainUI().setExposureIcon();
						}

						main_activity.updateForSettings(preview.getISOString(selected_value));
						main_activity.getMainUI().setISOIcon();
						main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
					}
				});
			}
		} else {
			if (!main_activity.getMainUI().isVisible(R.id.flash_mode)) {
				List<String> supported_flash_values = preview.getSupportedFlashValues();
				addButtonOptionsToPopup(supported_flash_values, R.array.flash_icons, R.array.flash_values, R.array.flash_keys,
						getResources().getString(R.string.flash_mode), preview.getCurrentFlashValue(), "TEST_FLASH",
						new ButtonOptionsPopupListener() {
					@Override
					public void onClick(String option) {
						if( MyDebug.LOG )
							Log.d(TAG, "clicked flash: " + option);
						preview.updateFlash(option);
						main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
					}
				});
			}

			if (!main_activity.getPreview().isVideoRecording()) {
				if (!main_activity.getMainUI().isVisible(R.id.focus_mode) && Prefs.getPhotoMode() != Prefs.PhotoMode.FocusBracketing) {
					// make a copy of getSupportedFocusValues() so we can modify it
					List<String> supported_focus_values = preview.getSupportedFocusValues();
					if( supported_focus_values != null ) {
						supported_focus_values = new ArrayList<>(supported_focus_values);
						// only show appropriate continuous focus mode
						if( preview.isVideo() ) {
							supported_focus_values.remove("focus_mode_continuous_picture");
						}
						else {
							supported_focus_values.remove("focus_mode_continuous_video");
						}
					}
					addButtonOptionsToPopup(supported_focus_values, R.array.focus_mode_icons, R.array.focus_mode_values, R.array.focus_mode_keys,
							getResources().getString(R.string.focus_mode), preview.getCurrentFocusValue(), "TEST_FOCUS",
							new ButtonOptionsPopupListener() {
						@Override
						public void onClick(String option) {
							if( MyDebug.LOG )
								Log.d(TAG, "clicked focus: " + option);
							final String old_value = preview.getCurrentFocusValue();
							preview.updateFocus(option, false, true);
							if (!preview.isTakingPhotoOrOnTimer() && 
									!(preview.isVideo() && main_activity.getSharedPrefs()
									.getBoolean(Prefs.UPDATE_FOCUS_FOR_VIDEO, false))) {
								if (!preview.usingCamera2API() && (option.equals("focus_mode_infinity") || option.equals("focus_mode_edof"))) {
									preview.reopenCamera();
								}

								if (option.equals("focus_mode_manual2")) {
									main_activity.getMainUI().setManualFocusSeekbars();
									main_activity.getMainUI().layoutSeekbars();
								} else {
									if (old_value != null && old_value.equals("focus_mode_manual2")) {
										main_activity.getMainUI().setManualFocusSeekbars();
										main_activity.getMainUI().layoutSeekbars();
									}
									if (old_value != null && (old_value.equals("focus_mode_infinity") || old_value.equals("focus_mode_edof")))
										preview.setCenterFocus();
								}
							}
							main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
						}
					});
				}

				if (!main_activity.getMainUI().isVisible(R.id.iso) && sharedPreferences.getBoolean(Prefs.POPUP_ISO, true)) {
					List<String> supported_isos = getSupportedISOs(main_activity);
					final String current_iso = Prefs.getISOPref();
					// n.b., we hardcode the string "ISO" as we don't want it translated - firstly more consistent with the ISO values returned by the driver, secondly need to worry about the size of the buttons, so don't want risk of a translated string being too long
					addButtonOptionsToPopup(supported_isos, 0, 0, 0, "ISO", current_iso, "TEST_ISO", new ButtonOptionsPopupListener() {
						@Override
						public void onClick(String option) {
							if( MyDebug.LOG )
								Log.d(TAG, "clicked iso: " + option);
							Prefs.setISOPref(option);
							
							if (current_iso.equals("manual") || option.equals("manual")) {
								main_activity.getMainUI().setManualIsoSeekbars();
								main_activity.getMainUI().updateSeekbars();
								main_activity.getMainUI().setExposureIcon();
							}

							main_activity.updateForSettings(preview.getISOString(option));
							main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
						}
					});
				}

				if (!preview.isVideo() && !main_activity.getMainUI().isVisible(R.id.photo_mode) && sharedPreferences.getBoolean(Prefs.POPUP_PHOTO_MODE, true)) {
					List<String> supported_values = getSupportedPhotoModes(main_activity);
					if( supported_values.size() > 1 ) {
						addButtonOptionsToPopup(supported_values, R.array.photo_mode_icons, R.array.photo_mode_values, R.array.photo_mode_keys,
								getResources().getString(R.string.photo_mode), Prefs.getPhotoModePref(), "TEST_PHOTO_MODE",
								new ButtonOptionsPopupListener() {
							@Override
							public void onClick(String option) {
								if( MyDebug.LOG )
									Log.d(TAG, "clicked photo mode: " + option);
								
								clickedPhotoMode(main_activity, option, false);
							}
						});
					}
				}

				if( sharedPreferences.getBoolean(Prefs.POPUP_AUTO_STABILISE, true) && (preview.isVideo() ? preview.supportsVideoStabilization() : main_activity.supportsAutoStabilise()) ) {
					// Костыль
					View view = new LinearLayout(main_activity);
					view.setPadding(0, elements_gap, 0, 0);
					
					CheckBox checkBox = new CheckBox(main_activity);
					checkBox.setText(getResources().getString(preview.isVideo() ? R.string.preference_video_stabilization : R.string.preference_auto_stabilise));
					checkBox.setTextColor(negative ? Color.BLACK : Color.WHITE);
					checkBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);

					boolean auto_stabilise = sharedPreferences.getBoolean(preview.isVideo() ? Prefs.VIDEO_STABILIZATION : Prefs.AUTO_STABILISE, false);
					if( auto_stabilise )
						checkBox.setChecked(auto_stabilise);
					checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							final SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
							SharedPreferences.Editor editor = sharedPreferences.edit();
							editor.putBoolean(preview.isVideo() ? Prefs.VIDEO_STABILIZATION : Prefs.AUTO_STABILISE, isChecked);
							editor.apply();

							boolean done_dialog = false;
							if( isChecked ) {
								boolean done_auto_stabilise_info = sharedPreferences.contains(preview.isVideo() ? Prefs.DONE_VIDEO_STABILIZATION_INFO : Prefs.DONE_AUTO_STABILISE_INFO);
								if( !done_auto_stabilise_info ) {
									if (preview.isVideo()) showInfoDialog(R.string.preference_video_stabilization, R.string.preference_video_stabilization_summary, Prefs.DONE_VIDEO_STABILIZATION_INFO);
									else showInfoDialog(R.string.preference_auto_stabilise, R.string.auto_stabilise_info, Prefs.DONE_AUTO_STABILISE_INFO);
									done_dialog = true;
								}
							}

							if( !done_dialog ) {
								String message = getResources().getString(preview.isVideo() ? R.string.preference_video_stabilization : R.string.preference_auto_stabilise) + ": " + getResources().getString(isChecked ? R.string.on : R.string.off);
								preview.showToast(main_activity.getChangedAutoStabiliseToastBoxer(), message);
							}
							main_activity.getMainUI().closePopup(); // don't need to destroy popup
						}
					});

					this.addView(view);
					this.addView(checkBox);
				}

				if (sharedPreferences.getBoolean(Prefs.POPUP_RESOLUTION, true)) {
					if (!preview.isVideo()) {
						final List<CameraController.Size> picture_sizes = preview.getSupportedPictureSizes();
						picture_size_index = preview.getCurrentPictureSizeIndex();
						final List<String> picture_size_strings = new ArrayList<>();
						for(CameraController.Size picture_size : picture_sizes) {
							// don't display MP here, as call to Preview.getMPString() here would contribute to poor performance!
							String size_string = picture_size.width + " x " + picture_size.height;
							picture_size_strings.add(size_string);
						}
						addArrayOptionsToPopup(picture_size_strings, getResources().getString(R.string.preference_resolution),
								false, picture_size_index, false, true, "PHOTO_RESOLUTIONS", new ArrayOptionsPopupListener() {
							final Handler handler = new Handler();
							final Runnable update_runnable = new Runnable() {
								@Override
								public void run() {
									if( MyDebug.LOG )
										Log.d(TAG, "update settings due to resolution change");
									main_activity.updateForSettings("", true); // keep the popupview open
								}
							};

							private void update() {
								if( picture_size_index == -1 )
									return;
								CameraController.Size new_size = picture_sizes.get(picture_size_index);
								String resolution_string = new_size.width + " " + new_size.height;
								SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
								SharedPreferences.Editor editor = sharedPreferences.edit();
								editor.putString(Prefs.getResolutionPreferenceKey(), resolution_string);
								editor.apply();

								// make it easier to scroll through the list of resolutions without a pause each time
								handler.removeCallbacks(update_runnable);
								handler.postDelayed(update_runnable, 400);
							}
							@Override
							public int onClickPrev() {
								if( picture_size_index != -1 && picture_size_index > 0 ) {
									picture_size_index--;
									update();
									return picture_size_index;
								}
								return -1;
							}
							@Override
							public int onClickNext() {
								if( picture_size_index != -1 && picture_size_index < picture_sizes.size()-1 ) {
									picture_size_index++;
									update();
									return picture_size_index;
								}
								return -1;
							}
						});
					} else {
						final List<String> video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality();
						video_size_index = preview.getVideoQualityHander().getCurrentVideoQualityIndex();
						final List<String> video_size_strings = new ArrayList<>();
						for(String video_size : video_sizes) {
							String quality_string = preview.getCamcorderProfileDescriptionShort(video_size);
							video_size_strings.add(quality_string);
						}
						addArrayOptionsToPopup(video_size_strings, getResources().getString(R.string.video_quality),
								false, video_size_index, false, true, "VIDEO_RESOLUTIONS", new ArrayOptionsPopupListener() {
							final Handler handler = new Handler();
							final Runnable update_runnable = new Runnable() {
								@Override
								public void run() {
									if( MyDebug.LOG )
										Log.d(TAG, "update settings due to video resolution change");
									main_activity.updateForSettings("", true); // keep the popupview open
								}
							};

							private void update() {
								if( video_size_index == -1 )
									return;
								String quality = video_sizes.get(video_size_index);
								SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
								SharedPreferences.Editor editor = sharedPreferences.edit();
								editor.putString(Prefs.getVideoQualityPreferenceKey(), quality);
								editor.apply();

								// make it easier to scroll through the list of resolutions without a pause each time
								handler.removeCallbacks(update_runnable);
								handler.postDelayed(update_runnable, 400);
							}
							@Override
							public int onClickPrev() {
								if( video_size_index != -1 && video_size_index > 0 ) {
									video_size_index--;
									update();
									return video_size_index;
								}
								return -1;
							}
							@Override
							public int onClickNext() {
								if( video_size_index != -1 && video_size_index < video_sizes.size()-1 ) {
									video_size_index++;
									update();
									return video_size_index;
								}
								return -1;
							}
						});
					}
				}

				if (main_activity.selfie_mode && sharedPreferences.getBoolean(Prefs.POPUP_TIMER, true)) {
					final String [] timer_values = getResources().getStringArray(R.array.preference_timer_values);
					String [] timer_entries = getResources().getStringArray(R.array.preference_timer_entries);
					String timer_value = sharedPreferences.getString(Prefs.TIMER, "0");
					timer_index = Arrays.asList(timer_values).indexOf(timer_value);
					if( timer_index == -1 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "can't find timer_value " + timer_value + " in timer_values!");
						timer_index = 0;
					}
					addArrayOptionsToPopup(Arrays.asList(timer_entries), getResources().getString(R.string.preference_timer),
							false, timer_index, false, false, "TIMER", new ArrayOptionsPopupListener() {
						private void update() {
							if( timer_index == -1 )
								return;
							String new_timer_value = timer_values[timer_index];
							SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
							SharedPreferences.Editor editor = sharedPreferences.edit();
							editor.putString(Prefs.TIMER, new_timer_value);
							editor.apply();
							
							main_activity.getMainUI().setTakePhotoIcon();
						}
						@Override
						public int onClickPrev() {
							if( timer_index != -1 && timer_index > 0 ) {
								timer_index--;
								update();
								return timer_index;
							}
							return -1;
						}
						@Override
						public int onClickNext() {
							if( timer_index != -1 && timer_index < timer_values.length-1 ) {
								timer_index++;
								update();
								return timer_index;
							}
							return -1;
						}
					});
				}

				if (!preview.isVideo()) {
					final Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
					if (photo_mode == Prefs.PhotoMode.FocusBracketing || photo_mode == Prefs.PhotoMode.FastBurst || photo_mode == Prefs.PhotoMode.NoiseReduction) {
						final String pref_key;
						switch (photo_mode) {
							case FastBurst:
								pref_key = Prefs.FAST_BURST_COUNT;
								break;
							case NoiseReduction:
								pref_key = Prefs.NR_COUNT;
								break;
							default:
								pref_key = Prefs.FB_COUNT;
						}

						if (sharedPreferences.getBoolean(Prefs.POPUP_PHOTOS_COUNT, true)) {
							final String [] photos_count_values = getResources().getStringArray(R.array.preference_photos_count_values);
							String photos_count_value = sharedPreferences.getString(pref_key, "3");
							photos_count_index = Arrays.asList(photos_count_values).indexOf(photos_count_value);
							if( photos_count_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find photos_count_value " + photos_count_value + " in photos_count_values!");
								photos_count_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(photos_count_values), getResources().getString(R.string.preference_photos_count),
									false, photos_count_index, false, false, "PHOTOS_COUNT", new ArrayOptionsPopupListener() {
								private void update() {
									if( photos_count_index == -1 )
										return;
									String new_photos_count_value = photos_count_values[photos_count_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString(pref_key, new_photos_count_value);
									editor.apply();

									if ((photo_mode == Prefs.PhotoMode.FastBurst || photo_mode == Prefs.PhotoMode.NoiseReduction) && preview.getCameraController() != null ) {
										int count;
										try {count = Integer.parseInt(new_photos_count_value);}
										catch (NumberFormatException e) {count = 3;}
										preview.getCameraController().setWantBurstCount(count);
									}
								}
								@Override
								public int onClickPrev() {
									if( photos_count_index != -1 && photos_count_index > 0 ) {
										photos_count_index--;
										update();
										return photos_count_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( photos_count_index != -1 && photos_count_index < photos_count_values.length-1 ) {
										photos_count_index++;
										update();
										return photos_count_index;
									}
									return -1;
								}
							});
						}
					} else if (main_activity.selfie_mode) {
						if (sharedPreferences.getBoolean(Prefs.POPUP_BURST_MODE, true)) {
							final String [] burst_mode_values = getResources().getStringArray(R.array.preference_burst_mode_values);
							String [] burst_mode_entries = getResources().getStringArray(R.array.preference_burst_mode_entries);
							String burst_mode_value = sharedPreferences.getString(Prefs.BURST_MODE, "1");
							burst_mode_index = Arrays.asList(burst_mode_values).indexOf(burst_mode_value);
							if( burst_mode_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!");
								burst_mode_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(burst_mode_entries), getResources().getString(R.string.preference_burst_mode),
									false, burst_mode_index, false, false, "BURST_MODE", new ArrayOptionsPopupListener() {
								private void update() {
									if( burst_mode_index == -1 )
										return;
									String new_burst_mode_value = burst_mode_values[burst_mode_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString(Prefs.BURST_MODE, new_burst_mode_value);
									editor.apply();
									
									main_activity.getMainUI().setTakePhotoIcon();
								}
								@Override
								public int onClickPrev() {
									if( burst_mode_index != -1 && burst_mode_index > 0 ) {
										burst_mode_index--;
										update();
										return burst_mode_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( burst_mode_index != -1 && burst_mode_index < burst_mode_values.length-1 ) {
										burst_mode_index++;
										update();
										return burst_mode_index;
									}
									return -1;
								}
							});
						}
						
						if (sharedPreferences.getBoolean(Prefs.POPUP_BURST_INTERVAL, true)) {
							final String [] burst_interval_values = getResources().getStringArray(R.array.preference_burst_interval_values);
							String [] burst_interval_entries = getResources().getStringArray(R.array.preference_burst_interval_entries);
							String burst_interval_value = sharedPreferences.getString(Prefs.BURST_INTERVAL, "2");
							burst_interval_index = Arrays.asList(burst_interval_values).indexOf(burst_interval_value);
							if( burst_interval_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find burst_interval_value " + burst_interval_value + " in burst_interval_values!");
								burst_interval_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(burst_interval_entries), getResources().getString(R.string.preference_burst_interval),
									false, burst_interval_index, false, false, "burst_interval", new ArrayOptionsPopupListener() {
								private void update() {
									if( burst_interval_index == -1 )
										return;
									String new_burst_interval_value = burst_interval_values[burst_interval_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString(Prefs.BURST_INTERVAL, new_burst_interval_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( burst_interval_index != -1 && burst_interval_index > 0 ) {
										burst_interval_index--;
										update();
										return burst_interval_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( burst_interval_index != -1 && burst_interval_index < burst_interval_values.length-1 ) {
										burst_interval_index++;
										update();
										return burst_interval_index;
									}
									return -1;
								}
							});
						}
					}
				}

				if (sharedPreferences.getBoolean(Prefs.POPUP_GRID, true)) {
					final String [] grid_values = getResources().getStringArray(R.array.preference_grid_values);
					String [] grid_entries = getResources().getStringArray(R.array.preference_grid_entries);
					String grid_value = sharedPreferences.getString(Prefs.GRID, "preference_grid_none");
					grid_index = Arrays.asList(grid_values).indexOf(grid_value);
					if( grid_index == -1 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "can't find grid_value " + grid_value + " in grid_values!");
						grid_index = 0;
					}
					addArrayOptionsToPopup(Arrays.asList(grid_entries), getResources().getString(R.string.grid),
							false, grid_index, true, false, "GRID", new ArrayOptionsPopupListener() {
						private void update() {
							if( grid_index == -1 )
								return;
							String new_grid_value = grid_values[grid_index];
							SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
							SharedPreferences.Editor editor = sharedPreferences.edit();
							editor.putString(Prefs.GRID, new_grid_value);
							editor.apply();
						}
						@Override
						public int onClickPrev() {
							if( grid_index != -1 ) {
								grid_index--;
								if( grid_index < 0 )
									grid_index += grid_values.length;
								update();
								return grid_index;
							}
							return -1;
						}
						@Override
						public int onClickNext() {
							if( grid_index != -1 ) {
								grid_index++;
								if( grid_index >= grid_values.length )
									grid_index -= grid_values.length;
								update();
								return grid_index;
							}
							return -1;
						}
					});
				}

				if (!preview.isVideo()) {
					final Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
					if ((photo_mode == Prefs.PhotoMode.ExpoBracketing || photo_mode == Prefs.PhotoMode.HDR) && sharedPreferences.getBoolean(Prefs.POPUP_EXPO_BRACKETING_STOPS, true)) {
						final String [] stops_values = getResources().getStringArray(R.array.preference_expo_bracketing_stops_values);
						{
							final String pref_key;
							switch (photo_mode) {
								case HDR:
									pref_key = Prefs.HDR_STOPS_UP;
									break;
								default:
									pref_key = Prefs.EXPO_BRACKETING_STOPS_UP;
							}

							String stops_value = sharedPreferences.getString(pref_key, "2");
							stops_up_index = Arrays.asList(stops_values).indexOf(stops_value);
							if( stops_up_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find stops_value " + stops_value + " in stops_values!");
								stops_up_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(stops_values), getResources().getString(R.string.preference_expo_bracketing_stops_up),
									false, stops_up_index, false, false, "STOPS", new ArrayOptionsPopupListener() {
								private void update() {
									if( stops_up_index == -1 )
										return;
									String new_stops_value = stops_values[stops_up_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString(pref_key, new_stops_value);
									editor.apply();

									preview.setupExpoBracketing(preview.getCameraController());
								}
								@Override
								public int onClickPrev() {
									if( stops_up_index != -1 && stops_up_index > 0 ) {
										stops_up_index--;
										update();
										return stops_up_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( stops_up_index != -1 && stops_up_index < stops_values.length-1 ) {
										stops_up_index++;
										update();
										return stops_up_index;
									}
									return -1;
								}
							});
						}
						{
							final String pref_key;
							switch (photo_mode) {
								case HDR:
									pref_key = Prefs.HDR_STOPS_DOWN;
									break;
								default:
									pref_key = Prefs.EXPO_BRACKETING_STOPS_DOWN;
							}

							String stops_value = sharedPreferences.getString(pref_key, "2");
							stops_down_index = Arrays.asList(stops_values).indexOf(stops_value);
							if( stops_down_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find stops_value " + stops_value + " in stops_values!");
								stops_down_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(stops_values), getResources().getString(R.string.preference_expo_bracketing_stops_down),
									false, stops_down_index, false, false, "STOPS", new ArrayOptionsPopupListener() {
								private void update() {
									if( stops_down_index == -1 )
										return;
									String new_stops_value = stops_values[stops_down_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString(pref_key, new_stops_value);
									editor.apply();

									preview.setupExpoBracketing(preview.getCameraController());
								}
								@Override
								public int onClickPrev() {
									if( stops_down_index != -1 && stops_down_index > 0 ) {
										stops_down_index--;
										update();
										return stops_down_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( stops_down_index != -1 && stops_down_index < stops_values.length-1 ) {
										stops_down_index++;
										update();
										return stops_down_index;
									}
									return -1;
								}
							});
						}
					}
					if(photo_mode == Prefs.PhotoMode.HDR) {
						if(sharedPreferences.getBoolean(Prefs.POPUP_HDR_DEGHOST, true)) {
							// Костыль
							View view = new LinearLayout(main_activity);
							view.setPadding(0, elements_gap, 0, 0);
							
							CheckBox checkBox = new CheckBox(main_activity);
							checkBox.setText(getResources().getString(R.string.preference_hdr_deghost));
							checkBox.setTextColor(negative ? Color.BLACK : Color.WHITE);
							checkBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
							checkBox.setChecked(sharedPreferences.getBoolean(Prefs.HDR_DEGHOST, true));

							checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
								public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
									final SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putBoolean(Prefs.HDR_DEGHOST, isChecked);
									editor.apply();

									main_activity.getMainUI().closePopup(); // don't need to destroy popup
								}
							});

							this.addView(view);
							this.addView(checkBox);
						}
						if (sharedPreferences.getBoolean(Prefs.POPUP_HDR_TONEMAPPING, true)) {
							final String [] hdr_tonemapping_values = getResources().getStringArray(R.array.preference_hdr_tonemapping_values);
							String [] hdr_tonemapping_entries = getResources().getStringArray(R.array.preference_hdr_tonemapping_entries);
							String hdr_tonemapping_value = sharedPreferences.getString(Prefs.HDR_TONEMAPPING, "reinhard");
							hdr_tonemapping_index = Arrays.asList(hdr_tonemapping_values).indexOf(hdr_tonemapping_value);
							if( hdr_tonemapping_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find value " + hdr_tonemapping_value + " in hdr_tonemapping_values!");
								hdr_tonemapping_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(hdr_tonemapping_entries), getResources().getString(R.string.preference_hdr_tonemapping),
									false, hdr_tonemapping_index, true, false, "HDR_TONEMAPPING", new ArrayOptionsPopupListener() {
								private void update() {
									if( hdr_tonemapping_index == -1 )
										return;
									String new_hdr_tonemapping_value = hdr_tonemapping_values[hdr_tonemapping_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString("preference_hdr_tonemapping", new_hdr_tonemapping_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( hdr_tonemapping_index != -1 ) {
										hdr_tonemapping_index--;
										if( hdr_tonemapping_index < 0 )
											hdr_tonemapping_index += hdr_tonemapping_values.length;
										update();
										return hdr_tonemapping_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( hdr_tonemapping_index != -1 ) {
										hdr_tonemapping_index++;
										if( hdr_tonemapping_index >= hdr_tonemapping_values.length )
											hdr_tonemapping_index -= hdr_tonemapping_values.length;
										update();
										return hdr_tonemapping_index;
									}
									return -1;
								}
							});
						}
					}

					if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.DRO) { 
						if (sharedPreferences.getBoolean(Prefs.POPUP_HDR_UNSHARP_MASK, true)) {
							final String [] hdr_unsharp_mask_values = getResources().getStringArray(R.array.preference_hdr_local_contrast_values);
							String [] hdr_unsharp_mask_entries = getResources().getStringArray(R.array.preference_hdr_local_contrast_entries);
							String hdr_unsharp_mask_value = sharedPreferences.getString(Prefs.HDR_UNSHARP_MASK, "1");
							hdr_unsharp_mask_index = Arrays.asList(hdr_unsharp_mask_values).indexOf(hdr_unsharp_mask_value);
							if( hdr_unsharp_mask_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find value " + hdr_unsharp_mask_value + " in hdr_unsharp_mask_values!");
								hdr_unsharp_mask_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(hdr_unsharp_mask_entries), getResources().getString(R.string.preference_hdr_unsharp_mask),
									false, hdr_unsharp_mask_index, false, false, "HDR_UNSHARP_MASK", new ArrayOptionsPopupListener() {
								private void update() {
									if( hdr_unsharp_mask_index == -1 )
										return;
									String new_hdr_unsharp_mask_value = hdr_unsharp_mask_values[hdr_unsharp_mask_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString("preference_hdr_unsharp_mask", new_hdr_unsharp_mask_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( hdr_unsharp_mask_index != -1 ) {
										hdr_unsharp_mask_index--;
										if( hdr_unsharp_mask_index < 0 )
											hdr_unsharp_mask_index += hdr_unsharp_mask_values.length;
										update();
										return hdr_unsharp_mask_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( hdr_unsharp_mask_index != -1 ) {
										hdr_unsharp_mask_index++;
										if( hdr_unsharp_mask_index >= hdr_unsharp_mask_values.length )
											hdr_unsharp_mask_index -= hdr_unsharp_mask_values.length;
										update();
										return hdr_unsharp_mask_index;
									}
									return -1;
								}
							});
						}
						if (sharedPreferences.getBoolean(Prefs.POPUP_HDR_UNSHARP_MASK_RADIUS, true)) {
							final String [] hdr_unsharp_mask_radius_values = getResources().getStringArray(R.array.preference_radius_values);
							String hdr_unsharp_mask_radius_value = sharedPreferences.getString(Prefs.HDR_UNSHARP_MASK_RADIUS, "5");
							hdr_unsharp_mask_radius_index = Arrays.asList(hdr_unsharp_mask_radius_values).indexOf(hdr_unsharp_mask_radius_value);
							if( hdr_unsharp_mask_radius_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find value " + hdr_unsharp_mask_radius_value + " in hdr_unsharp_mask_radius_values!");
								hdr_unsharp_mask_radius_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(hdr_unsharp_mask_radius_values), getResources().getString(R.string.preference_hdr_unsharp_mask_radius),
									false, hdr_unsharp_mask_radius_index, false, false, "HDR_UNSHARP_MASK_RADIUS", new ArrayOptionsPopupListener() {
								private void update() {
									if( hdr_unsharp_mask_radius_index == -1 )
										return;
									String new_hdr_unsharp_mask_radius_value = hdr_unsharp_mask_radius_values[hdr_unsharp_mask_radius_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString("preference_hdr_unsharp_mask_radius", new_hdr_unsharp_mask_radius_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( hdr_unsharp_mask_radius_index != -1 ) {
										hdr_unsharp_mask_radius_index--;
										if( hdr_unsharp_mask_radius_index < 0 )
											hdr_unsharp_mask_radius_index += hdr_unsharp_mask_radius_values.length;
										update();
										return hdr_unsharp_mask_radius_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( hdr_unsharp_mask_radius_index != -1 ) {
										hdr_unsharp_mask_radius_index++;
										if( hdr_unsharp_mask_radius_index >= hdr_unsharp_mask_radius_values.length )
											hdr_unsharp_mask_radius_index -= hdr_unsharp_mask_radius_values.length;
										update();
										return hdr_unsharp_mask_radius_index;
									}
									return -1;
								}
							});
						}
						if (sharedPreferences.getBoolean(Prefs.POPUP_HDR_LOCAL_CONTRAST, true)) {
							final String [] hdr_local_contrast_values = getResources().getStringArray(R.array.preference_hdr_local_contrast_values);
							String [] hdr_local_contrast_entries = getResources().getStringArray(R.array.preference_hdr_local_contrast_entries);
							String hdr_local_contrast_value = sharedPreferences.getString(Prefs.HDR_LOCAL_CONTRAST, "5");
							hdr_local_contrast_index = Arrays.asList(hdr_local_contrast_values).indexOf(hdr_local_contrast_value);
							if( hdr_local_contrast_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find value " + hdr_local_contrast_value + " in hdr_local_contrast_values!");
								hdr_local_contrast_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(hdr_local_contrast_entries), getResources().getString(R.string.preference_hdr_local_contrast),
									false, hdr_local_contrast_index, false, false, "HDR_LOCAL_CONTRAST", new ArrayOptionsPopupListener() {
								private void update() {
									if( hdr_local_contrast_index == -1 )
										return;
									String new_hdr_local_contrast_value = hdr_local_contrast_values[hdr_local_contrast_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString("preference_hdr_local_contrast", new_hdr_local_contrast_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( hdr_local_contrast_index != -1 ) {
										hdr_local_contrast_index--;
										if( hdr_local_contrast_index < 0 )
											hdr_local_contrast_index += hdr_local_contrast_values.length;
										update();
										return hdr_local_contrast_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( hdr_local_contrast_index != -1 ) {
										hdr_local_contrast_index++;
										if( hdr_local_contrast_index >= hdr_local_contrast_values.length )
											hdr_local_contrast_index -= hdr_local_contrast_values.length;
										update();
										return hdr_local_contrast_index;
									}
									return -1;
								}
							});
						}

						if (sharedPreferences.getBoolean(Prefs.POPUP_HDR_N_TILES, true)) {
							final String [] hdr_n_tiles_values = getResources().getStringArray(R.array.preference_hdr_n_tiles_values);
							String hdr_n_tiles_value = sharedPreferences.getString(Prefs.HDR_N_TILES, "4");
							hdr_n_tiles_index = Arrays.asList(hdr_n_tiles_values).indexOf(hdr_n_tiles_value);
							if( hdr_n_tiles_index == -1 ) {
								if( MyDebug.LOG )
									Log.d(TAG, "can't find value " + hdr_n_tiles_value + " in hdr_n_tiles_values!");
								hdr_n_tiles_index = 0;
							}
							addArrayOptionsToPopup(Arrays.asList(hdr_n_tiles_values), getResources().getString(R.string.preference_hdr_n_tiles),
									false, hdr_n_tiles_index, false, false, "HDR_TILES", new ArrayOptionsPopupListener() {
								private void update() {
									if( hdr_n_tiles_index == -1 )
										return;
									String new_hdr_n_tiles_value = hdr_n_tiles_values[hdr_n_tiles_index];
									SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
									SharedPreferences.Editor editor = sharedPreferences.edit();
									editor.putString("preference_hdr_n_tiles", new_hdr_n_tiles_value);
									editor.apply();
								}
								@Override
								public int onClickPrev() {
									if( hdr_n_tiles_index != -1 ) {
										hdr_n_tiles_index--;
										if( hdr_n_tiles_index < 0 )
											hdr_n_tiles_index += hdr_n_tiles_values.length;
										update();
										return hdr_n_tiles_index;
									}
									return -1;
								}
								@Override
								public int onClickNext() {
									if( hdr_n_tiles_index != -1 ) {
										hdr_n_tiles_index++;
										if( hdr_n_tiles_index >= hdr_n_tiles_values.length )
											hdr_n_tiles_index -= hdr_n_tiles_values.length;
										update();
										return hdr_n_tiles_index;
									}
									return -1;
								}
							});
						}
					}
				}

				// popup should only be opened if we have a camera controller, but check just to be safe
				if( preview.getCameraController() != null ) {
					if (!main_activity.getMainUI().isVisible(R.id.white_balance) && sharedPreferences.getBoolean(Prefs.POPUP_WHITE_BALANCE, true)) {
						List<String> supported_white_balances = preview.getSupportedWhiteBalances();
						addExpandableRadioOptionsToPopup(supported_white_balances, getResources().getString(R.string.white_balance), "wb_",
								Prefs.WHITE_BALANCE, sharedPreferences.getString(Prefs.WHITE_BALANCE, preview.getCameraController().getDefaultWhiteBalance()),
								"TEST_WHITE_BALANCE", new RadioOptionsListener() {
							@Override
							public void onClick(String selected_value) {
								switchToWhiteBalance(selected_value);
							}
						});
					}

					if (!main_activity.getMainUI().isVisible(R.id.scene_mode) && sharedPreferences.getBoolean(Prefs.POPUP_SCENE_MODE, true)) {
						List<String> supported_scene_modes = preview.getSupportedSceneModes();
						addExpandableRadioOptionsToPopup(supported_scene_modes, getResources().getString(R.string.scene_mode), "sm_",
								Prefs.SCENE_MODE, sharedPreferences.getString(Prefs.SCENE_MODE, preview.getCameraController().getDefaultSceneMode()),
								"TEST_SCENE_MODE", new RadioOptionsListener() {
							@Override
							public void onClick(String selected_value) {
								if( preview.getCameraController() != null ) {
									if( preview.getCameraController().sceneModeAffectsFunctionality() ) {
										// need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
										main_activity.updateForSettings(getResources().getString(R.string.scene_mode) + ": " + main_activity.getStringResourceByName("sm_", selected_value));
										main_activity.getMainUI().closePopup();
									}
									else {
										preview.getCameraController().setSceneMode(selected_value);
										// keep popup open
									}
								}
							}
						});
					}

					if (!main_activity.getMainUI().isVisible(R.id.color_effect) && sharedPreferences.getBoolean(Prefs.POPUP_COLOR_EFFECT, true)) {
						List<String> supported_color_effects = preview.getSupportedColorEffects();
						addExpandableRadioOptionsToPopup(supported_color_effects, getResources().getString(R.string.color_effect), "ce_",
								Prefs.COLOR_EFFECT, sharedPreferences.getString(Prefs.COLOR_EFFECT, preview.getCameraController().getDefaultColorEffect()),
								"TEST_COLOR_EFFECT", new RadioOptionsListener() {
							@Override
							public void onClick(String selected_value) {
								if( preview.getCameraController() != null ) {
									preview.getCameraController().setColorEffect(selected_value);
								}
								// keep popup open
							}
						});
					}
				}
			}
		}
	}

	public void switchToWhiteBalance(String selected_value) {
		if( MyDebug.LOG )
			Log.d(TAG, "switchToWhiteBalance: " + selected_value);
		final MainActivity main_activity = (MainActivity)this.getContext();
		final Preview preview = main_activity.getPreview();
		boolean close_popup = false;

		boolean update_seekbars = false;
		if( preview.getCameraController() != null ) {
			if (selected_value.equals("manual") || preview.getCameraController().getWhiteBalance().equals("manual")) {
				update_seekbars = true;
			}
			preview.getCameraController().setWhiteBalance(selected_value);
			if( selected_value.equals("manual") ) {
				main_activity.getMainUI().setManualWBSeekbar();
				close_popup = true;
			}
		}
		if (update_seekbars)
			main_activity.getMainUI().updateSeekbars();

		// keep popup open, unless switching to manual
		if( close_popup ) {
			main_activity.getMainUI().closePopup();
		}
		//main_activity.updateForSettings(getResources().getString(R.string.white_balance) + ": " + selected_value);
	}

	private abstract class ButtonOptionsPopupListener {
		public abstract void onClick(String option);
	}
	
	private void addButtonOptionsToPopup(List<String> options, int icons_id, int values_id, int keys_id, String prefix_string, String current_value, String test_key, final ButtonOptionsPopupListener listener) {
		if( MyDebug.LOG )
			Log.d(TAG, "addButtonOptionsToPopup");
		if( options != null ) {
			final long debug_time = System.nanoTime();
			LinearLayout ll2 = new LinearLayout(this.getContext());
			ll2.setOrientation(LinearLayout.HORIZONTAL);
			ll2.setPadding(0, elements_gap, 0, 0);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.popup_button_height));
			ll2.setLayoutParams(lp);
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 1: " + (System.nanoTime() - debug_time));
			String [] icons = icons_id > 0 ? getResources().getStringArray(icons_id) : null;
			String [] values = values_id > 0 ? getResources().getStringArray(values_id) : null;
			String [] keys = keys_id > 0 ? getResources().getStringArray(keys_id) : null;
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 2: " + (System.nanoTime() - debug_time));
				
			List<String> supported_options;
			if (keys == null) {
				supported_options = options;
			} else {
				supported_options = new ArrayList<String>();
				for(final String option : options) {
					for(int i = 0; i < values.length; i++) {
						if( values[i].equals(option) ) {
							if (keys[i] == null || sharedPreferences.getBoolean(keys[i] + "_" + camera_id, true))
								supported_options.add(option);
							break;
						}
							
					}
				}
			}

			int button_width = total_width/supported_options.size();
			boolean use_scrollview = false;
			if( button_width < button_min_width ) {
				button_width = button_min_width;
				use_scrollview = true;
			}
			final int final_button_width = button_width;

			View.OnClickListener on_click_listener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String supported_option = (String)v.getTag();
						if( MyDebug.LOG )
							Log.d(TAG, "clicked: " + supported_option);
						listener.onClick(supported_option);
					}
				};
			View current_view = null;
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 2.05: " + (System.nanoTime() - debug_time));

			for(final String supported_option : supported_options) {
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.06: " + (System.nanoTime() - debug_time));
				if( MyDebug.LOG )
					Log.d(TAG, "supported_option: " + supported_option);
				int resource = -1;
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.08: " + (System.nanoTime() - debug_time));
				if( icons != null && values != null ) {
					int index = -1;
					for(int i=0;i<values.length && index==-1;i++) {
						if( values[i].equals(supported_option) ) {
							index = i;
							break;
						}
					}
					if( MyDebug.LOG )
						Log.d(TAG, "index: " + index);
					if( index != -1 ) {
						resource = getResources().getIdentifier(icons[index], null, this.getContext().getApplicationContext().getPackageName());
					}
				}
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.1: " + (System.nanoTime() - debug_time));

				final boolean is_iso = prefix_string.equalsIgnoreCase("ISO");
				float text_size = text_size_button;
				String button_string;
				// hacks for ISO mode ISO_HJR (e.g., on Samsung S5)
				// also some devices report e.g. "ISO100" etc
				if( prefix_string.length() == 0 ) {
					button_string = supported_option;
				}
				else if( is_iso && supported_option.equalsIgnoreCase("auto")) {
					text_size = text_size_mode;
					button_string = "A";
				}
				else if( is_iso && supported_option.equalsIgnoreCase("manual")) {
					text_size = text_size_mode;
					button_string = "M";
				}
				else if( is_iso ) {
					button_string = prefix_string + "\n" + main_activity.getMainUI().fixISOString(supported_option);
				}
				else {
					button_string = prefix_string + "\n" + supported_option;
				}
				if( MyDebug.LOG )
					Log.d(TAG, "button_string: " + button_string);
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.105: " + (System.nanoTime() - debug_time));
				View view;
				if( resource != -1 ) {
					ImageButton image_button = new ImageButton(this.getContext(), null, android.R.attr.borderlessButtonStyle);
					if( MyDebug.LOG )
						Log.d(TAG, "addButtonOptionsToPopup time 2.11: " + (System.nanoTime() - debug_time));
					view = image_button;
					ll2.addView(view);
					if( MyDebug.LOG )
						Log.d(TAG, "addButtonOptionsToPopup time 2.12: " + (System.nanoTime() - debug_time));

					//image_button.setImageResource(resource);
					final MainActivity main_activity = (MainActivity)this.getContext();
					Bitmap bm = main_activity.getPreloadedBitmap(resource);
					if( bm != null )
						image_button.setImageBitmap(bm);
					else {
						if( MyDebug.LOG )
							Log.d(TAG, "failed to find bitmap for resource " + resource + "!");
					}
					if( MyDebug.LOG )
						Log.d(TAG, "addButtonOptionsToPopup time 2.13: " + (System.nanoTime() - debug_time));
					image_button.setScaleType(ScaleType.CENTER);
					if (negative) image_button.setColorFilter(neg_filter);
				}
				else {
					Button button = new Button(this.getContext());

					button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
					view = button;
					ll2.addView(view);

					button.setText(button_string);
					button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size);
					button.setTextColor(negative ? Color.BLACK : Color.WHITE);
					button.setTypeface(null, Typeface.BOLD);
					// need 0 padding so we have enough room to display text for ISO buttons, when there are 6 ISO settings
					view.setPadding(0,-50,0,-50);
				}
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.2: " + (System.nanoTime() - debug_time));

				ViewGroup.LayoutParams params = view.getLayoutParams();
				params.width = button_width;
				params.height = ViewGroup.LayoutParams.MATCH_PARENT;
				view.setLayoutParams(params);

				view.setContentDescription(button_string);
				if( supported_option.equals(current_value) ) {
					view.setAlpha(ALPHA_BUTTON_SELECTED);
					current_view = view;
				}
				else {
					view.setAlpha(ALPHA_BUTTON);
				}
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.3: " + (System.nanoTime() - debug_time));
				view.setTag(supported_option);
				view.setOnClickListener(on_click_listener);
				if( MyDebug.LOG )
					Log.d(TAG, "addButtonOptionsToPopup time 2.35: " + (System.nanoTime() - debug_time));
				this.popup_buttons.put(test_key + "_" + supported_option, view);
				if( MyDebug.LOG ) {
					Log.d(TAG, "addButtonOptionsToPopup time 2.4: " + (System.nanoTime() - debug_time));
					Log.d(TAG, "added to popup_buttons: " + test_key + "_" + supported_option + " view: " + view);
					Log.d(TAG, "popup_buttons is now: " + popup_buttons);
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 3: " + (System.nanoTime() - debug_time));
			if( use_scrollview ) {
				if( MyDebug.LOG )
					Log.d(TAG, "using scrollview");
				final HorizontalScrollView scroll = new HorizontalScrollView(this.getContext());
				scroll.addView(ll2);
				{
					ViewGroup.LayoutParams params = new LayoutParams(
							total_width,
							LayoutParams.WRAP_CONTENT);
					scroll.setLayoutParams(params);
				}
				this.addView(scroll);
				if( current_view != null ) {
					// scroll to the selected button
					final View final_current_view = current_view;
					this.getViewTreeObserver().addOnGlobalLayoutListener( 
						new OnGlobalLayoutListener() {
							@Override
							public void onGlobalLayout() {
								// scroll so selected button is centred
								int jump_x = final_current_view.getLeft() - (total_width-final_button_width)/2;
								// scrollTo should automatically clamp to the bounds of the view, but just in case
								jump_x = Math.min(jump_x, total_width-1);
								if( jump_x > 0 ) {
									/*if( MyDebug.LOG )
										Log.d(TAG, "jump to " + jump_X);*/
									scroll.scrollTo(jump_x, 0);
								}
							}
						}
					);
				}
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "not using scrollview");
				this.addView(ll2);
			}
			if( MyDebug.LOG )
				Log.d(TAG, "addButtonOptionsToPopup time 4: " + (System.nanoTime() - debug_time));
		}
	}

	private void addTextButtonOptionsToPopup(List<String> supported_options, final String title,
			int icons_id, int values_id, int names_id, int keys_id,
			String current_value, String test_key, final ButtonOptionsPopupListener listener) {

		if( MyDebug.LOG )
			Log.d(TAG, "addTextButtonOptionsToPopup");

		if( supported_options != null ) {
			addTitleToPopup(title);

			LinearLayout ll2 = new LinearLayout(this.getContext());
			ll2.setOrientation(LinearLayout.VERTICAL);
			String [] icons = icons_id > 0 ? getResources().getStringArray(icons_id) : null;
			String [] values = values_id > 0 ? getResources().getStringArray(values_id) : null;
			String [] names = names_id > 0 ? getResources().getStringArray(names_id) : null;
			String [] keys = keys_id > 0 ? getResources().getStringArray(keys_id) : null;
	
			for(final String supported_option : supported_options) {
				if( MyDebug.LOG )
					Log.d(TAG, "supported_option: " + supported_option);

				int resource = -1;
				int index = -1;
				if( icons != null && values != null ) {
					for(int i=0;i<values.length && index==-1;i++) {
						if( values[i].equals(supported_option) ) {
							index = i;
							break;
						}
					}
					if( index != -1 ) {
						if (keys != null && keys[index] != null && !sharedPreferences.getBoolean(keys[index] + "_" + camera_id, true)) {
							continue;
						}
						resource = getResources().getIdentifier(icons[index], null, this.getContext().getApplicationContext().getPackageName());
					}
				}
				
				String button_string = index == -1 ? supported_option : names[index];

				Button button = new Button(this.getContext());
				button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!

				button.setText(button_string);
				button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
				button.setTextColor(negative ? Color.BLACK : Color.WHITE);
				button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
				button.setAllCaps(false);
				if( resource != -1 ) {
					if (negative) {
						Drawable drawable = getResources().getDrawable(resource).mutate();
						drawable.setColorFilter(neg_filter);
						button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
					} else button.setCompoundDrawablesWithIntrinsicBounds(resource, 0, 0, 0);
				}
				
				button.setContentDescription(button_string);
				button.setPadding(0,0,0,0);
	
				ll2.addView(button);

				if( supported_option.equals(current_value) ) {
					button.setAlpha(ALPHA_BUTTON_SELECTED);
				} else {
					button.setAlpha(ALPHA_BUTTON);
				}
				button.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						if( MyDebug.LOG )
							Log.d(TAG, "clicked: " + supported_option);
						listener.onClick(supported_option);
					}
				});
				this.popup_buttons.put(test_key + "_" + supported_option, button);
			}

			this.addView(ll2);
		}
	}
	
	private void addTitleToPopup(final String title) {
		TextView text_view = new TextView(this.getContext());
		text_view.setText(title + ":");
		text_view.setTextColor(negative ? Color.BLACK : Color.WHITE);
		text_view.setGravity(Gravity.CENTER);
		text_view.setTypeface(null, Typeface.BOLD);

		text_view.setPadding(0, elements_gap, 0, 0);

		text_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_title);
		//text_view.setBackgroundColor(Color.GRAY); // debug
		this.addView(text_view);
	}

	private abstract class RadioOptionsListener {
		/** Called when a radio option is selected.
		 * @param selected_value The entry in the supplied supported_options_values list (received
		 *					   by addRadioOptionsToPopup) that corresponds to the selected radio
		 *					   option.
		 */
		public abstract void onClick(String selected_value);
	}

	private void addRadioOptionsToPopup(final List<String> supported_options, final String title, final String prefix, final String preference_key, final String current_option, final String test_key, final RadioOptionsListener listener) {
		if( MyDebug.LOG )
			Log.d(TAG, "addRadioOptionsToPopup: " + title);
		if( supported_options != null ) {
			addTitleToPopup(title);

			final RadioGroup rg = new RadioGroup(this.getContext());
			rg.setOrientation(RadioGroup.VERTICAL);
			addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, test_key, listener);
			this.addView(rg);
		}
	}

	private void addExpandableRadioOptionsToPopup(final List<String> supported_options, final String title, final String prefix, final String preference_key, final String current_option, final String test_key, final RadioOptionsListener listener) {
		if( MyDebug.LOG )
			Log.d(TAG, "addExpandableRadioOptionsToPopup: " + title);
		if( supported_options != null ) {
			final MainActivity main_activity = (MainActivity)this.getContext();
			final long debug_time = System.nanoTime();

			//addTitleToPopup(title);
			final Button button = new Button(this.getContext());
			button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			button.setAllCaps(false);
			button.setText(expand_lists ? title : title + "...");
			button.setTextColor(negative ? Color.BLACK : Color.WHITE);
			button.setTypeface(null, Typeface.BOLD);
			button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_title);
			this.addView(button);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToPopup time 1: " + (System.nanoTime() - debug_time));

			final RadioGroup rg = new RadioGroup(this.getContext());
			rg.setOrientation(RadioGroup.VERTICAL);
			if (expand_lists) {
				addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, test_key, listener);
				rg.setVisibility(View.VISIBLE);
			} else {
				rg.setVisibility(View.GONE);
			}
			this.popup_buttons.put(test_key, rg);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToPopup time 2: " + (System.nanoTime() - debug_time));

			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked to open radio buttons menu: " + title);
					if( rg.getVisibility() == View.VISIBLE ) {
						//rg.removeAllViews();
						rg.setVisibility(View.GONE);
						final ScrollView popup_container = (ScrollView) main_activity.findViewById(R.id.popup_container);
						// need to invalidate/requestLayout so that the scrollview's scroll positions update - otherwise scrollBy below doesn't work properly, when the user reopens the radio buttons
						popup_container.invalidate();
						popup_container.requestLayout();
						button.setText(title + "...");
					}
					else {
						if( rg.getChildCount() == 0 ) {
							addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, test_key, listener);
						}
						rg.setVisibility(View.VISIBLE);
						final ScrollView popup_container = (ScrollView) main_activity.findViewById(R.id.popup_container);
						popup_container.getViewTreeObserver().addOnGlobalLayoutListener(
								new OnGlobalLayoutListener() {
									@SuppressWarnings("deprecation")
									@Override
									public void onGlobalLayout() {
										//if( MyDebug.LOG )
										//	Log.d(TAG, "onGlobalLayout()");
										// stop listening - only want to call this once!
										if( Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 ) {
											popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
										}
										else {
											popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
										}

										// so that the user sees the options appear, if the button is at the bottom of the current scrollview position
										if( rg.getChildCount() > 0 ) {
											int id = rg.getCheckedRadioButtonId();
											if( id >= 0 && id < rg.getChildCount() ) {
												popup_container.smoothScrollBy(0, rg.getChildAt(id).getBottom());
											}
										}
									}
								}
						);
						button.setText(title);
					}
				}
			});

			this.addView(rg);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToPopup time 5: " + (System.nanoTime() - debug_time));
		}
	}

	private void addRadioOptionsToGroup(final RadioGroup rg, List<String> supported_options, final String title, final String prefix, final String preference_key, final String current_option, final String test_key, final RadioOptionsListener listener) {
		if( MyDebug.LOG )
			Log.d(TAG, "addRadioOptionsToGroup: " + title);
		final long debug_time = System.nanoTime();
		final MainActivity main_activity = (MainActivity)this.getContext();
		int count = 0;
		for(final String supported_option : supported_options) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "supported_option: " + supported_option);
			}
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 1: " + (System.nanoTime() - debug_time));
			RadioButton button = new RadioButton(this.getContext());
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 2: " + (System.nanoTime() - debug_time));

			button.setButtonDrawable(negative ? R.drawable.radio_selector_dark : R.drawable.radio_selector_light);
			button.setId(count);

			final String translated_text;
			if (supported_option.equals("auto")) translated_text = getResources().getString(R.string.auto);
			else if (prefix.equals("iso_")) {
				if (supported_option.equals("manual")) translated_text = getResources().getString(R.string.iso_manual);
				else translated_text = main_activity.getMainUI().fixISOString(supported_option);
			} else translated_text = main_activity.getStringResourceByName(prefix, supported_option);
			button.setText(translated_text);
			button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
			button.setTextColor(negative ? Color.BLACK : Color.WHITE);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 3: " + (System.nanoTime() - debug_time));
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 4: " + (System.nanoTime() - debug_time));
			rg.addView(button);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 5: " + (System.nanoTime() - debug_time));

			if( supported_option.equals(current_option) ) {
				//button.setChecked(true);
				rg.check(count);
			}
			count++;

			button.setContentDescription(translated_text);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 6: " + (System.nanoTime() - debug_time));
			button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if( MyDebug.LOG ) {
						Log.d(TAG, "clicked current_option entry: " + supported_option);
					}
					SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
					SharedPreferences.Editor editor = sharedPreferences.edit();
					editor.putString(preference_key, supported_option);
					editor.apply();

					if( listener != null ) {
						listener.onClick(supported_option);
					}
					else {
						main_activity.updateForSettings(title + ": " + translated_text);
						main_activity.getMainUI().closePopup();
					}
				}
			});
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 7: " + (System.nanoTime() - debug_time));
			this.popup_buttons.put(test_key + "_" + supported_option, button);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToGroup time 8: " + (System.nanoTime() - debug_time));
		}
		if( MyDebug.LOG )
			Log.d(TAG, "addRadioOptionsToGroup time total: " + (System.nanoTime() - debug_time));
	}
	
	private abstract class ArrayOptionsPopupListener {
		public abstract int onClickPrev();
		public abstract int onClickNext();
	}
	
	private void addArrayOptionsToPopup(final List<String> supported_options, final String title, final boolean title_in_options, final int current_index, final boolean cyclic, final boolean reverse, final String test_key, final ArrayOptionsPopupListener listener) {
		if( supported_options != null && current_index != -1 ) {
			if( !title_in_options ) {
				addTitleToPopup(title);
			}

			LinearLayout ll2 = new LinearLayout(this.getContext());
			ll2.setOrientation(LinearLayout.HORIZONTAL);
			
			final TextView resolution_text_view = new TextView(this.getContext());
			if( title_in_options )
				resolution_text_view.setText(title + ": " + supported_options.get(current_index));
			else
				resolution_text_view.setText(supported_options.get(current_index));
			resolution_text_view.setTextColor(negative ? Color.BLACK :Color.WHITE);
			resolution_text_view.setGravity(Gravity.CENTER);
			resolution_text_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
			resolution_text_view.setLayoutParams(params);

			final int padding = 0;
			final Button prev_button = new Button(this.getContext());
			if (icon_font != null) {
				prev_button.setTypeface(icon_font);
			}
			prev_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			ll2.addView(prev_button);
			prev_button.setText("<");
			prev_button.setTextColor(negative ? Color.DKGRAY :Color.LTGRAY);
			prev_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_arrow);
			prev_button.setPadding(padding, padding, padding, padding);
			ViewGroup.LayoutParams vg_params = prev_button.getLayoutParams();
			vg_params.width = arrow_width;
			vg_params.height = arrow_height;
			prev_button.setLayoutParams(vg_params);
			this.popup_buttons.put(test_key + "_PREV", prev_button);

			ll2.addView(resolution_text_view);
			this.popup_buttons.put(test_key, resolution_text_view);

			final Button next_button = new Button(this.getContext());
			if (icon_font != null) {
				next_button.setTypeface(icon_font);
			}
			next_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			ll2.addView(next_button);
			next_button.setText(">");
			next_button.setTextColor(negative ? Color.DKGRAY :Color.LTGRAY);
			next_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_arrow);
			next_button.setPadding(padding, padding, padding, padding);
			vg_params = next_button.getLayoutParams();
			vg_params.width = arrow_width;
			vg_params.height = arrow_height;
			next_button.setLayoutParams(vg_params);
			this.popup_buttons.put(test_key + "_NEXT", next_button);

			(reverse ? next_button : prev_button).setVisibility( (cyclic || current_index > 0) ? View.VISIBLE : View.INVISIBLE);
			(reverse ? prev_button : next_button).setVisibility( (cyclic || current_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);

			prev_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					int new_index = reverse ? listener.onClickNext() : listener.onClickPrev();
					if( new_index != -1 ) {
						if( title_in_options )
							resolution_text_view.setText(title + ": " + supported_options.get(new_index));
						else
							resolution_text_view.setText(supported_options.get(new_index));
						(reverse ? next_button : prev_button).setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
						(reverse ? prev_button : next_button).setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
					}
				}
			});
			next_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					int new_index = reverse ? listener.onClickPrev() : listener.onClickNext();
					if( new_index != -1 ) {
						if( title_in_options )
							resolution_text_view.setText(title + ": " + supported_options.get(new_index));
						else
							resolution_text_view.setText(supported_options.get(new_index));
						(reverse ? next_button : prev_button).setVisibility( (cyclic || new_index > 0) ? View.VISIBLE : View.INVISIBLE);
						(reverse ? prev_button : next_button).setVisibility( (cyclic || new_index < supported_options.size()-1) ? View.VISIBLE : View.INVISIBLE);
					}
				}
			});

			this.addView(ll2);
		}
	}
	
	private void showInfoDialog(int title_id, int info_id, final String info_preference_key) {
		final MainActivity main_activity = (MainActivity)this.getContext();
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(PopupView.this.getContext());
		alertDialog.setTitle(title_id);
		alertDialog.setMessage(info_id);
		alertDialog.setPositiveButton(android.R.string.ok, null);
		alertDialog.setNegativeButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if( MyDebug.LOG )
					Log.d(TAG, "user clicked dont_show_again for info dialog");
				final SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putBoolean(info_preference_key, true);
				editor.apply();
			}
		});

		main_activity.showPreview(false);
		main_activity.setWindowFlagsForSettings();

		AlertDialog alert = alertDialog.create();
		// AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
		alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface arg0) {
				if( MyDebug.LOG )
					Log.d(TAG, "info dialog dismissed");
				main_activity.setWindowFlagsForCamera();
				main_activity.showPreview(true);
			}
		});
		alert.show();
	}

	private List<String> getSupportedPhotoModes(final MainActivity main_activity) {
		List<String> modes = new ArrayList<>();
		modes.add( "std" );
		if( main_activity.supportsDRO() ) {
			modes.add( "dro" );
		}
		if( main_activity.supportsHDR() ) {
			modes.add( "hdr" );
		}
		if( main_activity.supportsExpoBracketing() ) {
			modes.add( "ebr" );
		}
		if( main_activity.supportsFocusBracketing() ) {
			modes.add( "fbr" );
		}
		if( main_activity.supportsFastBurst() ) {
			modes.add( "bur" );
		}
		if( main_activity.supportsNoiseReduction() ) {
			modes.add( "nr" );
		}
		return modes;
	}
	
	private List<String> getSupportedISOs(final MainActivity main_activity) {
		if( main_activity.getPreview().supportsISORange() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "supports ISO range");
			int min_iso = main_activity.getPreview().getMinimumISO();
			int max_iso = main_activity.getPreview().getMaximumISO();
			List<String> values = new ArrayList<>();
			values.add("auto");
			values.add("manual");
			if (!main_activity.getPreview().isVideo()) {
				values.add("" + min_iso);
				for(int iso_value : new int[]{50, 100, 200, 400, 800, 1600, 3200, 6400}) {
					if( iso_value > min_iso && iso_value < max_iso ) {
						values.add("" + iso_value);
					}
				}
				values.add("" + max_iso);
			}
			return values;
		}
		else {
			return main_activity.getPreview().getSupportedISOs();
		}
	}
	
	private void clickedPhotoMode(final MainActivity main_activity, final String option, final boolean update_icon) {
		final SharedPreferences sharedPreferences = main_activity.getSharedPrefs();
		final String old_option = Prefs.getPhotoModePref();
		Prefs.setPhotoModePref(option);

		String toast_message = null;
		int i = java.util.Arrays.asList(getResources().getStringArray(R.array.photo_mode_values)).indexOf(option);
		if (i != -1) {
			toast_message = getResources().getString(R.string.photo_mode) + ": " + getResources().getStringArray(R.array.photo_mode_entries)[i];
		}

		boolean done_dialog = false;
		if( option.equals("hdr") ) {
			boolean done_hdr_info = sharedPreferences.contains(Prefs.DONE_HDR_INFO);
			if( !done_hdr_info ) {
				showInfoDialog(R.string.photo_mode_hdr, R.string.hdr_info, Prefs.DONE_HDR_INFO);
				done_dialog = true;
			}
		}

		if( done_dialog ) {
			// no need to show toast
			toast_message = null;
		}
		
		if (option.equals("hdr") || old_option.equals("hdr")) {
			main_activity.getPreview().setupSmartFilter();
		}
		if (option.equals("fbr") || old_option.equals("fbr")) {
			main_activity.getPreview().setupFocus(true);
			main_activity.getMainUI().layoutSeekbars();
		}
		if (option.equals("bur") || old_option.equals("bur")) {
			main_activity.getPreview().reopenCamera();
//			main_activity.getPreview().setupBurst(main_activity.getPreview().getCameraController());
		}

		main_activity.updateForSettings(toast_message);
		if (update_icon) main_activity.getMainUI().setPhotoModeIcon();
		main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
	}

	// for testing
	public View getPopupButton(String key) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "getPopupButton(" + key + "): " + popup_buttons.get(key));
			Log.d(TAG, "this: " + this);
			Log.d(TAG, "popup_buttons: " + popup_buttons);
		}
		return popup_buttons.get(key);
	}
}
