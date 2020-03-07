package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyApplicationInterface;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;
import com.caddish_hedgehog.hedgecam2.UI.IconView;
import com.caddish_hedgehog.hedgecam2.StringUtils;
import com.caddish_hedgehog.hedgecam2.Utils;

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
import android.widget.RelativeLayout;
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
	private final int camera_id;

	private final int total_width;
	private final int button_min_width;
	private final int padding;
	
	private final int max_buttons_count;
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
		camera_id = Prefs.getCameraIdPref();
		
		icon_font = IconView.getTypeface(main_activity);
		
		padding = resources.getDimensionPixelSize(R.dimen.popup_padding);
		button_min_width = resources.getDimensionPixelSize(R.dimen.popup_button_min_width);
		elements_gap = resources.getDimensionPixelSize(R.dimen.popup_elements_gap);
		arrow_width = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
		arrow_height = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
		
		switch( Prefs.getString(Prefs.POPUP_SIZE, "normal") ) {
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
			default:
				max_buttons_count = 6;
		}
		total_width = button_min_width*max_buttons_count;

		this.setMinimumWidth((int)((float)total_width*0.875f+padding*2));
		this.setPadding(padding, padding-elements_gap, padding, padding);

		
		switch( Prefs.getString(Prefs.POPUP_FONT_SIZE, "normal") ) {
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
		
		
		switch (Prefs.getString(Prefs.POPUP_COLOR, "black")) {
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

		expand_lists = Prefs.getBoolean(Prefs.POPUP_EXPANDED_LISTS, false);
		
		if (popup_type == PopupType.Flash) {
			List<String> supported_flash_values = preview.getSupportedFlashValues();
			addTextButtonOptionsToPopup(supported_flash_values, getResources().getString(R.string.flash_mode),
					R.array.flash_icons, R.array.flash_values, R.array.flash_entries, R.array.flash_keys,
					preview.getCurrentFlashValue(), new ButtonOptionsPopupListener() {
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
					preview.getCurrentFocusValue(), new ButtonOptionsPopupListener() {
				@Override
				public void onClick(String option) {
					if( MyDebug.LOG )
						Log.d(TAG, "clicked focus: " + option);
					final String old_value = preview.getCurrentFocusValue();
					preview.updateFocus(option, false, true);
					if (!preview.isTakingPhotoOrOnTimer() && 
							!(preview.isVideo() &&
							Prefs.getBoolean(Prefs.UPDATE_FOCUS_FOR_VIDEO, false))) {
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
					Prefs.getPhotoModePref(), new ButtonOptionsPopupListener() {
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
						Prefs.WHITE_BALANCE, Prefs.getString(Prefs.WHITE_BALANCE, preview.getCameraController().getDefaultWhiteBalance()),
						new RadioOptionsListener() {
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
						Prefs.SCENE_MODE, Prefs.getString(Prefs.SCENE_MODE, preview.getCameraController().getDefaultSceneMode()),
						new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						if( preview.getCameraController() != null ) {
							if( preview.getCameraController().sceneModeAffectsFunctionality() ) {
								// need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
								main_activity.updateForSettings(getResources().getString(R.string.scene_mode) + ": " + Utils.getStringResourceByName("sm_", selected_value));
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
						Prefs.COLOR_EFFECT, Prefs.getString(Prefs.COLOR_EFFECT, preview.getCameraController().getDefaultColorEffect()),
						new RadioOptionsListener() {
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
						new RadioOptionsListener() {
					@Override
					public void onClick(String selected_value) {
						Prefs.setISOPref(selected_value);
						
						if (current_iso.equals("manual") || selected_value.equals("manual")) {
							main_activity.getMainUI().setManualIsoSeekbars();
							main_activity.getMainUI().updateSeekbars();
							main_activity.getMainUI().setExposureIcon();
						}

						main_activity.updateForSettings(StringUtils.getISOString(selected_value));
						main_activity.getMainUI().setISOIcon();
						main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
					}
				});
			}
		} else {
			if (!main_activity.getMainUI().isVisible(R.id.flash_mode)) {
				List<String> supported_flash_values = preview.getSupportedFlashValues();
				if (supported_flash_values != null) {
					int keys_id = R.array.flash_keys;
					if (preview.isVideo() && supported_flash_values.contains("flash_off") && supported_flash_values.contains("flash_torch")) {
						keys_id = 0;
						supported_flash_values = new ArrayList<>();
						supported_flash_values.add("flash_off");
						supported_flash_values.add("flash_torch");
					}
					addButtonOptionsToPopup(supported_flash_values, R.array.flash_icons, R.array.flash_values, keys_id,
							getResources().getString(R.string.flash_mode), preview.getCurrentFlashValue(),
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
			}

			if (!main_activity.getMainUI().isVisible(R.id.focus_mode) && (preview.isVideo() || Prefs.getPhotoMode() != Prefs.PhotoMode.FocusBracketing)) {
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
					addButtonOptionsToPopup(supported_focus_values, R.array.focus_mode_icons, R.array.focus_mode_values, R.array.focus_mode_keys,
							getResources().getString(R.string.focus_mode), preview.getCurrentFocusValue(),
							new ButtonOptionsPopupListener() {
						@Override
						public void onClick(String option) {
							if( MyDebug.LOG )
								Log.d(TAG, "clicked focus: " + option);
							final String old_value = preview.getCurrentFocusValue();
							preview.updateFocus(option, false, true);
							if (!preview.isTakingPhotoOrOnTimer() && 
									!(preview.isVideo() &&
									Prefs.getBoolean(Prefs.UPDATE_FOCUS_FOR_VIDEO, false))) {
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
			}

			if (!main_activity.getPreview().isVideoRecording()) {
				if (!main_activity.getMainUI().isVisible(R.id.iso) && Prefs.getBoolean(Prefs.POPUP_ISO, true)) {
					List<String> supported_isos = getSupportedISOs(main_activity);
					final String current_iso = Prefs.getISOPref();
					// n.b., we hardcode the string "ISO" as we don't want it translated - firstly more consistent with the ISO values returned by the driver, secondly need to worry about the size of the buttons, so don't want risk of a translated string being too long
					addButtonOptionsToPopup(supported_isos, 0, 0, 0, "ISO", current_iso, new ButtonOptionsPopupListener() {
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

							main_activity.updateForSettings(StringUtils.getISOString(option));
							main_activity.getMainUI().destroyPopup(); // need to recreate popup for new selection
						}
					});
				}

				if (!preview.isVideo() && !main_activity.getMainUI().isVisible(R.id.photo_mode) && Prefs.getBoolean(Prefs.POPUP_PHOTO_MODE, true)) {
					List<String> supported_values = getSupportedPhotoModes(main_activity);
					if( supported_values.size() > 1 ) {
						addButtonOptionsToPopup(supported_values, R.array.photo_mode_icons, R.array.photo_mode_values, R.array.photo_mode_keys,
								getResources().getString(R.string.photo_mode), Prefs.getPhotoModePref(),
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

				if( Prefs.getBoolean(Prefs.POPUP_AUTO_STABILISE, true) && (preview.isVideo() ? preview.supportsVideoStabilization() : main_activity.supportsAutoStabilise()) ) {
					addCheckBoxToPopup(
						getResources().getString(preview.isVideo() ? R.string.preference_video_stabilization : R.string.preference_auto_stabilise),
						preview.isVideo() ? Prefs.VIDEO_STABILIZATION : Prefs.AUTO_STABILISE, false,
						new CheckBoxPopupListener() {
							@Override
							public void onCheckedChanged(boolean isChecked) {
								boolean done_dialog = false;
								if( isChecked ) {
									boolean done_auto_stabilise_info = Prefs.contains(preview.isVideo() ? Prefs.DONE_VIDEO_STABILIZATION_INFO : Prefs.DONE_AUTO_STABILISE_INFO);
									if( !done_auto_stabilise_info ) {
										if (preview.isVideo()) showInfoDialog(R.string.preference_video_stabilization, R.string.preference_video_stabilization_summary, Prefs.DONE_VIDEO_STABILIZATION_INFO);
										else showInfoDialog(R.string.preference_auto_stabilise, R.string.auto_stabilise_info, Prefs.DONE_AUTO_STABILISE_INFO);
										done_dialog = true;
									}
								}

								if( !done_dialog ) {
									String message = getResources().getString(preview.isVideo() ? R.string.preference_video_stabilization : R.string.preference_auto_stabilise) + ": " + getResources().getString(isChecked ? R.string.on : R.string.off);
									Utils.showToast(main_activity.getChangedAutoStabiliseToastBoxer(), message);
								}
							}
						}
					);
				}
				
				if( Prefs.getBoolean(Prefs.POPUP_OPTICAL_STABILIZATION, false) &&  preview.getCameraController() != null) {
					final List<String> modes = preview.getCameraController().getAvailableOpticalStabilizationModes();
					if (modes.size() == 3) {
						final String mode = preview.getCameraController().getOpticalStabilizationMode();
						if (mode != null) {
							addCheckBoxToPopup(getResources().getString(R.string.preference_optical_stabilization), mode.equals("on"), new CompoundButton.OnCheckedChangeListener() {
								public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
									String value = isChecked ? "on" : "off";
									
									if (preview.getCameraController() != null && preview.getCameraController().setOpticalStabilizationMode(value)) {
										Prefs.setString(Prefs.OPTICAL_STABILIZATION + "_" + camera_id, value);
									}
									
									main_activity.getMainUI().closePopup(); // don't need to destroy popup
								}
							});
						}
					}
				}

				if (Prefs.getBoolean(Prefs.POPUP_RESOLUTION, true)) {
					if (!preview.isVideo()) {
						addTitleToPopup(getResources().getString(R.string.preference_resolution));
						
						CameraController.Size current_picture_size = preview.getCurrentPictureSize();
						
						List<CameraController.Size> filtered_picture_sizes = new ArrayList<>();
						for (CameraController.Size size : preview.getSupportedPictureSizes()) {
							if (size.equals(current_picture_size) || Prefs.getBoolean("show_resolution_" + camera_id + "_" + size.width + "_" + size.height, true))
								filtered_picture_sizes.add(size);
						}
						this.addView(new ArrayOptions(
							filtered_picture_sizes,
							Prefs.getResolutionPreferenceKey(),
							current_picture_size,
							new ArrayOptionsPopupListener() {
								@Override
								public void onChanged() {
									updateForSettingsDelayed();
								}
							}
						));
					} else {
						addTitleToPopup(getResources().getString(R.string.video_quality));

						List<String> video_sizes = preview.getSupportedVideoQuality(Prefs.getVideoFPSPref());
						if( video_sizes.size() == 0 ) {
							Log.e(TAG, "can't find any supported video sizes for current fps!");
							// fall back to unfiltered list
							video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality();
						}
						String current_video_size = preview.getVideoQualityHander().getCurrentVideoQuality();
						
						List<String> filtered_video_sizes = new ArrayList<>();
						for (String size : video_sizes) {
							if (size.equals(current_video_size) || Prefs.getBoolean("show_quality_" + camera_id + "_" + size, true))
								filtered_video_sizes.add(size);
						}
						this.addView(new ArrayOptions(
							filtered_video_sizes,
							Prefs.getVideoQualityPreferenceKey(),
							current_video_size,
							new ArrayOptionsPopupListener() {
								@Override
								public void onChanged() {
									updateForSettingsDelayed();
								}
							}
						));
					}
				}

				if (preview.isVideo()) {
					if (Prefs.getBoolean(Prefs.POPUP_VIDEO_BITRATE, false)) {
						addArrayOptionsToPopup(
							getResources().getString(R.string.preference_video_bitrate),
							false,
							Arrays.asList(getResources().getStringArray(R.array.preference_video_bitrate_entries)),
							Arrays.asList(getResources().getStringArray(R.array.preference_video_bitrate_values)),
							Prefs.VIDEO_BITRATE,
							"default",
							false, 
							null
						);
					}

					if (Prefs.getBoolean(Prefs.POPUP_VIDEO_FPS, false)) {
						final List<String> entries = Arrays.asList(getResources().getStringArray(R.array.preference_video_fps_entries));
						final List<String> values = Arrays.asList(getResources().getStringArray(R.array.preference_video_fps_values));
						addArrayOptionsToPopup(
							getResources().getString(R.string.preference_video_fps),
							false,
							entries,
							values,
							Prefs.VIDEO_FPS,
							"default",
							false, 
							new ArrayOptionsPopupListener() {
								@Override
								public void onValueChanged(String value) {
									main_activity.updateForSettings(getResources().getString(R.string.preference_video_fps) + ": "+ entries.get(values.indexOf(value)), true);
								}
							}
						);
					}

					if (Prefs.getBoolean(Prefs.POPUP_CAPTURE_RATE, true)) {
						final List<Float> capture_rate_values = preview.getSupportedVideoCaptureRates();
						if( capture_rate_values.size() > 1 ) {
							if( MyDebug.LOG )
								Log.d(TAG, "add slow motion / timelapse video options");
								//fixme
							final List<String> entries = new ArrayList<>();
							final List<String> values = new ArrayList<>();
							for(Float this_capture_rate : capture_rate_values) {
								if( Math.abs(1.0f - this_capture_rate) < 1.0e-5 ) {
									entries.add(getResources().getString(R.string.default_str));
								}
								else {
									entries.add(Float.toString(this_capture_rate) + "x");
								}
								values.add(Float.toString(this_capture_rate));
							}
							
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_video_capture_rate),
								false,
								entries,
								values,
								Prefs.CAPTURE_RATE,
								Float.toString(1.0f),
								false, 
								new ArrayOptionsPopupListener() {
									private boolean was_slow_motion = Prefs.getVideoCaptureRateFactor() < 1.0f-1.0e-5f;
									
									@Override
									public void onValueChanged(String value) {
										float capture_rate = 1.0f;
										try {
											capture_rate = Float.parseFloat(value);
										} catch (NumberFormatException e) {}
										boolean slow_motion = (capture_rate < 1.0f-1.0e-5f);
										boolean keep_popup = was_slow_motion == slow_motion;
										was_slow_motion = slow_motion;
										
										main_activity.updateForSettings(getResources().getString(R.string.preference_video_capture_rate) + ": " + entries.get(values.indexOf(value)), keep_popup);
									}
								}
							);
						}
					}
				}

				if (main_activity.selfie_mode && Prefs.getBoolean(Prefs.POPUP_TIMER, true)) {
					addArrayOptionsToPopup(
						getResources().getString(R.string.preference_timer),
						false,
						Arrays.asList(getResources().getStringArray(R.array.preference_timer_entries)),
						Arrays.asList(getResources().getStringArray(R.array.preference_timer_values)),
						Prefs.TIMER,
						"5",
						false, 
						new ArrayOptionsPopupListener() {
							@Override
							public void onValueChanged(String value) {
								main_activity.getMainUI().setTakePhotoIcon();
							}
						}
					);
				}

				if (!preview.isVideo()) {
					final Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
					if (photo_mode == Prefs.PhotoMode.FocusBracketing || photo_mode == Prefs.PhotoMode.FastBurst || photo_mode == Prefs.PhotoMode.NoiseReduction) {
						if (Prefs.getBoolean(Prefs.POPUP_PHOTOS_COUNT, true)) {
							final List<String> values = Arrays.asList(getResources().getStringArray(R.array.preference_photos_count_values));
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

							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_photos_count),
								false,
								values,
								values,
								pref_key,
								"3",
								false, 
								new ArrayOptionsPopupListener() {
									@Override
									public void onValueChanged(String value) {
										if ((photo_mode == Prefs.PhotoMode.FastBurst || photo_mode == Prefs.PhotoMode.NoiseReduction) && preview.getCameraController() != null ) {
											int count;
											try {count = Integer.parseInt(value);}
											catch (NumberFormatException e) {count = 3;}
											preview.getCameraController().setWantBurstCount(count);
										}
									}
								}
							);
						}
					} else if (main_activity.selfie_mode) {
						if (Prefs.getBoolean(Prefs.POPUP_BURST_MODE, true)) {
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_burst_mode),
								false,
								Arrays.asList(getResources().getStringArray(R.array.preference_burst_mode_entries)),
								Arrays.asList(getResources().getStringArray(R.array.preference_burst_mode_values)),
								Prefs.BURST_MODE,
								"1",
								false, 
								new ArrayOptionsPopupListener() {
									@Override
									public void onValueChanged(String value) {
										main_activity.getMainUI().setTakePhotoIcon();
									}
								}
							);
						}
						
						if (Prefs.getBoolean(Prefs.POPUP_BURST_INTERVAL, true)) {
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_burst_interval),
								false,
								Arrays.asList(getResources().getStringArray(R.array.preference_burst_interval_entries)),
								Arrays.asList(getResources().getStringArray(R.array.preference_burst_interval_values)),
								Prefs.BURST_INTERVAL,
								"2",
								false, 
								null
							);
						}
					}
				}

				if (Prefs.getBoolean(Prefs.POPUP_GRID, true)) {
					addArrayOptionsToPopup(
						getResources().getString(R.string.grid),
						false,
						Arrays.asList(getResources().getStringArray(R.array.preference_grid_entries)),
						Arrays.asList(getResources().getStringArray(R.array.preference_grid_values)),
						Prefs.GRID,
						"preference_grid_none",
						true,
						null
					);
					
				}

				if( Prefs.getBoolean(Prefs.POPUP_HISTOGRAM, false)) {
					addCheckBoxToPopup(
						getResources().getString(R.string.preference_show_histogram),
						Prefs.SHOW_HISTOGRAM, false,
						new CheckBoxPopupListener() {
							@Override
							public void onCheckedChanged(boolean isChecked) {
								Prefs.setBoolean(Prefs.SHOW_HISTOGRAM, isChecked);
							}
						}
					);
				}

				if( Prefs.getBoolean(Prefs.POPUP_GHOST_IMAGE, false)) {
					addCheckBoxToPopup(
						getResources().getString(R.string.preference_ghost_image),
						Prefs.GHOST_IMAGE, false,
						new CheckBoxPopupListener() {
							@Override
							public void onCheckedChanged(boolean isChecked) {
								main_activity.getMainUI().setOverlayImage();
							}
						}
					);
				}

				if (!preview.isVideo()) {
					final Prefs.PhotoMode photo_mode = Prefs.getPhotoMode();
					if ((photo_mode == Prefs.PhotoMode.ExpoBracketing || photo_mode == Prefs.PhotoMode.HDR) && Prefs.getBoolean(Prefs.POPUP_EXPO_BRACKETING_STOPS, true)) {
						final List<String> values = Arrays.asList(getResources().getStringArray(R.array.preference_expo_bracketing_stops_values));

						addArrayOptionsToPopup(
							getResources().getString(R.string.preference_expo_bracketing_stops_up),
							false,
							values,
							values,
							photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_STOPS_UP : Prefs.EXPO_BRACKETING_STOPS_UP,
							"2",
							false, 
							new ArrayOptionsPopupListener() {
								@Override
								public void onValueChanged(String value) {
									preview.setupExpoBracketing(preview.getCameraController());
								}
							}
						);

						addArrayOptionsToPopup(
							getResources().getString(R.string.preference_expo_bracketing_stops_down),
							false,
							values,
							values,
							photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_STOPS_DOWN : Prefs.EXPO_BRACKETING_STOPS_DOWN,
							"2",
							false, 
							new ArrayOptionsPopupListener() {
								@Override
								public void onValueChanged(String value) {
									preview.setupExpoBracketing(preview.getCameraController());
								}
							}
						);
					}
					if(photo_mode == Prefs.PhotoMode.HDR) {
						if(Prefs.getBoolean(Prefs.POPUP_HDR_DEGHOST, true)) {
							addCheckBoxToPopup(
								getResources().getString(R.string.preference_hdr_deghost),
								Prefs.HDR_DEGHOST, true,
								null
/*								new CheckBoxPopupListener() {
									@Override
									public void onCheckedChanged(boolean isChecked) {
										main_activity.getMainUI().closePopup(); // don't need to destroy popup
									}
								}*/
							);
						}
						if (Prefs.getBoolean(Prefs.POPUP_HDR_TONEMAPPING, true)) {
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_hdr_tonemapping),
								false,
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_tonemapping_entries)),
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_tonemapping_values)),
								Prefs.HDR_TONEMAPPING,
								"reinhard",
								true, 
								null
							);
						}
					}

					if (photo_mode == Prefs.PhotoMode.HDR || photo_mode == Prefs.PhotoMode.DRO) { 
						if (Prefs.getBoolean(Prefs.POPUP_HDR_UNSHARP_MASK, true)) {
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_hdr_unsharp_mask),
								false,
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_local_contrast_entries)),
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_local_contrast_values)),
								photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_UNSHARP_MASK : Prefs.DRO_UNSHARP_MASK,
								"1",
								false, 
								null
							);
						}

						if (Prefs.getBoolean(Prefs.POPUP_HDR_UNSHARP_MASK_RADIUS, true)) {
							List<String> values = Arrays.asList(getResources().getStringArray(R.array.preference_radius_values));
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_hdr_unsharp_mask_radius),
								false,
								values,
								values,
								photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_UNSHARP_MASK_RADIUS : Prefs.DRO_UNSHARP_MASK_RADIUS,
								"5",
								false, 
								null
							);
						}

						if (Prefs.getBoolean(Prefs.POPUP_HDR_LOCAL_CONTRAST, true)) {
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_hdr_local_contrast),
								false,
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_local_contrast_entries)),
								Arrays.asList(getResources().getStringArray(R.array.preference_hdr_local_contrast_values)),
								photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_LOCAL_CONTRAST : Prefs.DRO_LOCAL_CONTRAST,
								"5",
								false, 
								null
							);
						}

						if (Prefs.getBoolean(Prefs.POPUP_HDR_N_TILES, true)) {
							List<String> values = Arrays.asList(getResources().getStringArray(R.array.preference_hdr_n_tiles_values));
							addArrayOptionsToPopup(
								getResources().getString(R.string.preference_hdr_n_tiles),
								false,
								values,
								values,
								photo_mode == Prefs.PhotoMode.HDR ? Prefs.HDR_N_TILES : Prefs.DRO_N_TILES,
								"4",
								false, 
								null
							);
						}
					}
				}

				// popup should only be opened if we have a camera controller, but check just to be safe
				if( preview.getCameraController() != null ) {
					if (!main_activity.getMainUI().isVisible(R.id.white_balance) && Prefs.getBoolean(Prefs.POPUP_WHITE_BALANCE, true)) {
						List<String> supported_white_balances = preview.getSupportedWhiteBalances();
						addExpandableRadioOptionsToPopup(supported_white_balances, getResources().getString(R.string.white_balance), "wb_",
								Prefs.WHITE_BALANCE, Prefs.getString(Prefs.WHITE_BALANCE, preview.getCameraController().getDefaultWhiteBalance()),
								new RadioOptionsListener() {
							@Override
							public void onClick(String selected_value) {
								switchToWhiteBalance(selected_value);
							}
						});
					}

					if (!main_activity.getMainUI().isVisible(R.id.scene_mode) && Prefs.getBoolean(Prefs.POPUP_SCENE_MODE, true)) {
						List<String> supported_scene_modes = preview.getSupportedSceneModes();
						addExpandableRadioOptionsToPopup(supported_scene_modes, getResources().getString(R.string.scene_mode), "sm_",
								Prefs.SCENE_MODE, Prefs.getString(Prefs.SCENE_MODE, preview.getCameraController().getDefaultSceneMode()),
								new RadioOptionsListener() {
							@Override
							public void onClick(String selected_value) {
								if( preview.getCameraController() != null ) {
									if( preview.getCameraController().sceneModeAffectsFunctionality() ) {
										// need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
										main_activity.updateForSettings(getResources().getString(R.string.scene_mode) + ": " + Utils.getStringResourceByName("sm_", selected_value));
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

					if (!main_activity.getMainUI().isVisible(R.id.color_effect) && Prefs.getBoolean(Prefs.POPUP_COLOR_EFFECT, true)) {
						List<String> supported_color_effects = preview.getSupportedColorEffects();
						addExpandableRadioOptionsToPopup(supported_color_effects, getResources().getString(R.string.color_effect), "ce_",
								Prefs.COLOR_EFFECT, Prefs.getString(Prefs.COLOR_EFFECT, preview.getCameraController().getDefaultColorEffect()),
								new RadioOptionsListener() {
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
	
	private void addButtonOptionsToPopup(
		List<String> options,
		int icons_id,
		int values_id,
		int keys_id,
		String prefix_string,
		String current_value,
		final ButtonOptionsPopupListener listener
	) {
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
							if (keys[i] == null || Prefs.getBoolean(keys[i] + "_" + camera_id, true))
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
					button_string = prefix_string + "\n" + StringUtils.fixISOString(supported_option);
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
					button.setTypeface(button.getTypeface(), Typeface.BOLD);
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
				if( MyDebug.LOG ) {
					Log.d(TAG, "addButtonOptionsToPopup time 2.4: " + (System.nanoTime() - debug_time));
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

	private void addTextButtonOptionsToPopup(
		List<String> supported_options,
		final String title,
		int icons_id,
		int values_id,
		int names_id,
		int keys_id,
		String current_value,
		final ButtonOptionsPopupListener listener
	) {

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
						if (keys != null && keys[index] != null && !Prefs.getBoolean(keys[index] + "_" + camera_id, true)) {
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
			}

			this.addView(ll2);
		}
	}
	
	private void addTitleToPopup(final String title) {
		TextView text_view = new TextView(this.getContext());
		text_view.setText(title + ":");
		text_view.setTextColor(negative ? Color.BLACK : Color.WHITE);
		text_view.setGravity(Gravity.CENTER);
		text_view.setTypeface(text_view.getTypeface(), Typeface.BOLD);

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

	private void addRadioOptionsToPopup(
		final List<String> supported_options,
		final String title,
		final String prefix,
		final String preference_key,
		final String current_option,
		final RadioOptionsListener listener
	) {
		if( MyDebug.LOG )
			Log.d(TAG, "addRadioOptionsToPopup: " + title);
		if( supported_options != null ) {
			addTitleToPopup(title);

			final RadioGroup rg = new RadioGroup(this.getContext());
			rg.setOrientation(RadioGroup.VERTICAL);
			addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, listener);
			this.addView(rg);
		}
	}

	private void addExpandableRadioOptionsToPopup(
		final List<String> supported_options,
		final String title,
		final String prefix,
		final String preference_key,
		final String current_option,
		final RadioOptionsListener listener
	) {
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
			button.setTypeface(button.getTypeface(), Typeface.BOLD);
			button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_title);
			this.addView(button);
			if( MyDebug.LOG )
				Log.d(TAG, "addRadioOptionsToPopup time 1: " + (System.nanoTime() - debug_time));

			final RadioGroup rg = new RadioGroup(this.getContext());
			rg.setOrientation(RadioGroup.VERTICAL);
			if (expand_lists) {
				addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, listener);
				rg.setVisibility(View.VISIBLE);
			} else {
				rg.setVisibility(View.GONE);
			}
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
							addRadioOptionsToGroup(rg, supported_options, title, prefix, preference_key,  current_option, listener);
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

	private void addRadioOptionsToGroup(
		final RadioGroup rg,
		List<String> supported_options,
		final String title,
		final String prefix,
		final String preference_key,
		final String current_option,
		final RadioOptionsListener listener
	) {
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
				else translated_text = StringUtils.fixISOString(supported_option);
			} else translated_text = Utils.getStringResourceByName(prefix, supported_option);
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
					Prefs.setString(preference_key, supported_option);

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
		}
		if( MyDebug.LOG )
			Log.d(TAG, "addRadioOptionsToGroup time total: " + (System.nanoTime() - debug_time));
	}
	
	private abstract class ArrayOptionsPopupListener {
		public void onValueChanged(String value) {};
		public void onChanged() {};
	}

	private void addArrayOptionsToPopup(
		final String title,
		final boolean title_in_options,
		final List<String> entries,
		final List<String> values,
		final String pref_key,
		final String default_value,
		final boolean cyclic,
		final ArrayOptionsPopupListener listener
	) {
		if( !title_in_options ) {
			addTitleToPopup(title);
		}
		
		this.addView(new ArrayOptions(entries, values, pref_key, default_value, cyclic, listener));
	}
	

	private class ArrayOptions extends LinearLayout {

		private TextView text_view;
		private Button prev_button;
		private Button next_button;
		
		private List<String> entries;
		private List<String> values;
		private List<CameraController.Size> picture_sizes;
		private List<String> video_qualities;
		private String pref_key;
		private int values_count;
		private int current_index;
		private boolean cyclic = false;
		private String title = null;

		private ArrayOptionsPopupListener listener;

		// Entry-value pair
		public ArrayOptions(
			final List<String> entries,
			final List<String> values,
			final String pref_key,
			final String default_value,
			final boolean cyclic,
			final ArrayOptionsPopupListener listener
		) {
			super(PopupView.this.getContext());

			this.entries = entries;
			this.values = values;
			this.pref_key = pref_key;
			String value = Prefs.getString(pref_key, default_value);
			current_index = values.indexOf(value);
			if( current_index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't find value " + value + " in values!");
				current_index = values.indexOf(default_value);
				if( current_index == -1 )
					current_index = 0;
			}
			this.values_count = values.size();

			this.cyclic = cyclic;
			this.listener = listener;

			init(false);

			if (!this.cyclic)
				updateButtonsVisibility();

			updateText();
		}

		// Picture size
		public ArrayOptions(
			List<CameraController.Size> picture_sizes,
			final String pref_key,
			final CameraController.Size value,
			final ArrayOptionsPopupListener listener
		) {
			super(PopupView.this.getContext());

			this.picture_sizes = picture_sizes;
			this.pref_key = pref_key;
			this.current_index = picture_sizes.indexOf(value);
			if( this.current_index == -1 ) {
				this.current_index = 0;
			}
			this.values_count = picture_sizes.size();

			this.cyclic = false;
			this.listener = listener;

			init(true);
			updateButtonsVisibility();
			updateText();
		}

		// Video quality
		public ArrayOptions(
			final List<String> video_qualities,
			final String pref_key,
			final String value,
			final ArrayOptionsPopupListener listener
		) {
			super(PopupView.this.getContext());

			this.video_qualities = video_qualities;
			this.pref_key = pref_key;
			current_index = video_qualities.indexOf(value);
			if( current_index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't find value " + value + " in values!");
				current_index = 0;
			}
			this.values_count = video_qualities.size();

			this.cyclic = false;
			this.listener = listener;

			init(true);
			updateButtonsVisibility();
			updateText();
		}

		private void init(boolean reverse_buttons) {
			setOrientation(LinearLayout.HORIZONTAL);
			setVerticalGravity(Gravity.CENTER_VERTICAL);
			text_view = new TextView(PopupView.this.getContext());
			text_view.setTextColor(negative ? Color.BLACK :Color.WHITE);
			text_view.setGravity(Gravity.CENTER);
			text_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
			params.setMargins(-arrow_width, 0, -arrow_width, 0);
			text_view.setLayoutParams(params);

			prev_button = new Button(PopupView.this.getContext());
			if (icon_font != null) {
				prev_button.setTypeface(icon_font);
			}
			prev_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			addView(prev_button);
			prev_button.setText("<");
			prev_button.setTextColor(negative ? Color.DKGRAY :Color.LTGRAY);
			prev_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_arrow);
			prev_button.setPadding(0,0,0,0);
			ViewGroup.LayoutParams vg_params = prev_button.getLayoutParams();
			vg_params.width = arrow_width;
			vg_params.height = arrow_height;
			prev_button.setLayoutParams(vg_params);


			next_button = new Button(PopupView.this.getContext());
			if (icon_font != null) {
				next_button.setTypeface(icon_font);
			}
			next_button.setBackgroundColor(Color.TRANSPARENT); // workaround for Android 6 crash!
			addView(text_view);
			addView(next_button);
			next_button.setText(">");
			next_button.setTextColor(negative ? Color.DKGRAY :Color.LTGRAY);
			next_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_arrow);
			next_button.setPadding(0,0,0,0);
			vg_params = next_button.getLayoutParams();
			vg_params.width = arrow_width;
			vg_params.height = arrow_height;
			next_button.setLayoutParams(vg_params);
			
			if (reverse_buttons) {
				Button button = prev_button;
				prev_button = next_button;
				next_button = button;
			}

			prev_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (cyclic || current_index > 0) {
						current_index--;
						if (current_index < 0)
							current_index = values_count-1;

						if (!cyclic)
							updateButtonsVisibility();
						updateText();
						valueChanged();
					}
				}
			});
			next_button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (cyclic || current_index < values_count-1) {
						current_index++;
						if (current_index == values_count)
							current_index = 0;

						if (!cyclic)
							updateButtonsVisibility();
						updateText();
						valueChanged();
					}
				}
			});
		}
		
		private void valueChanged() {
			String value = null;
			if (values != null) {
				value = values.get(current_index);
			} else if (picture_sizes != null) {
				CameraController.Size size = picture_sizes.get(current_index);
				value = size.width + " " + size.height;
			} else if (video_qualities != null) {
				value = video_qualities.get(current_index);
			}
			
			if (value != null) {
				Prefs.setString(pref_key, value);
			}
			
			if (listener == null)
				return;

			if (values != null)
				listener.onValueChanged(value);
			else
				listener.onChanged();

		}
		
		private void updateText() {
			if (entries != null) {
				if (this.title != null)
					text_view.setText(title + ": " + entries.get(current_index));
				else
					text_view.setText(entries.get(current_index));
			} else if (picture_sizes != null) {
				CameraController.Size size = picture_sizes.get(current_index);
				if (max_buttons_count >= 8)
					text_view.setText(size.width + " x " + size.height + " " + main_activity.getPreview().getAspectRatioMPString(size.width, size.height));
				else if (max_buttons_count >= 6)
					text_view.setText(size.width + " x " + size.height + " (" + main_activity.getPreview().getMPString(size.width, size.height) + ")");
				else
					text_view.setText(size.width + " x " + size.height);
			} else if (video_qualities != null) {
				if (max_buttons_count >= 8)
					text_view.setText(main_activity.getPreview().getCamcorderProfileDescriptionMedium(video_qualities.get(current_index)));
				else if (max_buttons_count >= 6)
					text_view.setText(main_activity.getPreview().getCamcorderProfileDescriptionAR(video_qualities.get(current_index)));
				else
					text_view.setText(main_activity.getPreview().getCamcorderProfileDescriptionShort(video_qualities.get(current_index)));
			}
		}

		private void updateButtonsVisibility() {
			prev_button.setVisibility(this.current_index > 0 ? View.VISIBLE : View.INVISIBLE);
			next_button.setVisibility(this.current_index < values_count-1 ? View.VISIBLE : View.INVISIBLE);
		}
	}

	private abstract class CheckBoxPopupListener {
		public void onCheckedChanged(boolean isChecked) {};
	}

	private void addCheckBoxToPopup(
		final String title,
		final String preference_key,
		final boolean default_value,
		final CheckBoxPopupListener listener
	) {
		addCheckBoxToPopup(title, Prefs.getBoolean(preference_key, default_value), new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Prefs.setBoolean(preference_key, isChecked);
				
				if (listener != null)
					listener.onCheckedChanged(isChecked);
				
				main_activity.getMainUI().closePopup(); // don't need to destroy popup
			}
		});
	}
	
	private void addCheckBoxToPopup(
		final String title,
		final boolean checked,
		final CompoundButton.OnCheckedChangeListener listener
	) {
		// Костыль
		View view = new LinearLayout(main_activity);
		view.setPadding(0, elements_gap, 0, 0);
		
		CheckBox checkBox = new CheckBox(main_activity);
		checkBox.setText(title);
		checkBox.setTextColor(negative ? Color.BLACK : Color.WHITE);
		checkBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size_main);
		
		checkBox.setChecked(checked);
		
		checkBox.setOnCheckedChangeListener(listener);

		this.addView(view);
		this.addView(checkBox);
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
				Prefs.setBoolean(info_preference_key, true);
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
		final String old_option = Prefs.getPhotoModePref();
		Prefs.setPhotoModePref(option);

		String toast_message = null;
		int i = java.util.Arrays.asList(getResources().getStringArray(R.array.photo_mode_values)).indexOf(option);
		if (i != -1) {
			toast_message = getResources().getString(R.string.photo_mode) + ": " + getResources().getStringArray(R.array.photo_mode_entries)[i];
		}

		boolean done_dialog = false;
		if( option.equals("hdr") ) {
			boolean done_hdr_info = Prefs.contains(Prefs.DONE_HDR_INFO);
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
	
	final Handler update_handler = new Handler();
	final Runnable update_runnable = new Runnable() {
		@Override
		public void run() {
			if( MyDebug.LOG )
				Log.d(TAG, "update settings due to video resolution change");
			main_activity.updateForSettings("", true); // keep the popupview open
			main_activity.getMainUI().setOverlayImage();
		}
	};

	private void updateForSettingsDelayed() {
		// make it easier to scroll through the list of resolutions without a pause each time
		update_handler.removeCallbacks(update_runnable);
		update_handler.postDelayed(update_runnable, 400);
	}

}
