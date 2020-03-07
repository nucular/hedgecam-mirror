package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.MainActivity;
import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;
import com.caddish_hedgehog.hedgecam2.R;
import com.caddish_hedgehog.hedgecam2.StorageUtils;
import com.caddish_hedgehog.hedgecam2.UI.IconView;
import com.caddish_hedgehog.hedgecam2.StringUtils;
import com.caddish_hedgehog.hedgecam2.Utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ZoomControls;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/** This contains functionality related to the main UI.
 */
public class MainUI {
	private static final String TAG = "HedgeCam/MainUI";

	private final MainActivity main_activity;
	private final Resources resources;
	private Preview preview;

	private int center_vertical = RelativeLayout.CENTER_VERTICAL;
	private int center_horizontal = RelativeLayout.CENTER_HORIZONTAL;
	private int align_left = RelativeLayout.ALIGN_LEFT;
	private int align_right = RelativeLayout.ALIGN_RIGHT;
	private int left_of = RelativeLayout.LEFT_OF;
	private int right_of = RelativeLayout.RIGHT_OF;
	private int above = RelativeLayout.ABOVE;
	private int below = RelativeLayout.BELOW;
	private int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
	private int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
	private int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
	private int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;

	private PopupView popup_view;
	private PopupView.PopupType current_popup = PopupView.PopupType.Main;

	private int root_width = 0;
	private int root_height = 0;

	private int current_orientation;
	public boolean shutter_icon_material;
	private int ui_rotation = 0;
	private int ui_rotation_relative = 0;
	private boolean ui_placement_right = true;
	private int popup_anchor = R.id.gallery;
	private int popup_from = R.id.popup;
	private int seekbars_container_anchor = R.id.gallery;
	private boolean orientation_changed;

	private boolean immersive_mode;
	public boolean show_gui = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
	private boolean show_seekbars = false;
	private int last_seekbar = 0;
	
	private int ind_margin_left = 0;
	private int ind_margin_top = 0;
	private int ind_margin_right = 0;
	private int ind_margin_bottom = 0;
	
	private int button_size = 0;
	
	private final int manual_n = 500; // the number of values on the seekbar used for manual focus distance, ISO or exposure speed

	private double focus_min_value;
	private double focus_max_value;
	
	private boolean overlay_initialized = false;
	private int overlay_rotation = 0;
	boolean overlay_is_portrait = false;
	
	public enum GUIType {
		Phone,
		Phone2,
		Tablet,
		Universal,
		Classic
	};
	private GUIType gui_type = GUIType.Phone;
	
	public enum Orientation {
		Auto,
		Landscape,
		Portrait
	}
	private Orientation ui_orientation;
	private final boolean system_ui_portrait;
	
	private final int BUTTON_SETTINGS = 0;
	private final int BUTTON_POPUP = 1;
	private final int BUTTON_FLASH_MODE = 2;
	private final int BUTTON_FOCUS_MODE = 3;
	private final int BUTTON_ISO = 4;
	private final int BUTTON_PHOTO_MODE = 5;
	private final int BUTTON_COLOR_EFFECT = 6;
	private final int BUTTON_SCENE_MODE = 7;
	private final int BUTTON_WHITE_BALANCE = 8;
	private final int BUTTON_EXPO_METERING_AREA = 9;
	private final int BUTTON_AUTO_ADJUSTMENT_LOCK = 10;
	private final int BUTTON_EXPOSURE = 11;
	private final int BUTTON_SWITCH_CAMERA = 12;
	private final int BUTTON_FACE_DETECTION = 13;
	private final int BUTTON_AUDIO_CONTROL = 14;
	private final int BUTTON_SELFIE_MODE = 15;

	public final int[] ctrl_panel_buttons = {
		R.id.settings,
		R.id.popup,
		R.id.flash_mode,
		R.id.focus_mode,
		R.id.iso,
		R.id.photo_mode,
		R.id.color_effect,
		R.id.scene_mode,
		R.id.white_balance,
		R.id.expo_metering_area,
		R.id.auto_adjustment_lock,
		R.id.exposure,
		R.id.switch_camera,
		R.id.face_detection,
		R.id.audio_control,
		R.id.selfie_mode,
	};
	
	private int[] buttons_location;
	
	public final int[] manual_control = {
		R.id.focus_seekbar,
		R.id.focus_bracketing_seekbar,
		R.id.zoom,
		R.id.zoom_seekbar,
		R.id.white_balance_seekbar,
		R.id.exposure_time_seekbar,
		R.id.iso_seekbar,
		R.id.exposure_seekbar_zoom,
		R.id.exposure_seekbar,
	};
	
	private final int[] rotatable_seekbars = {
		R.id.zoom_seekbar,
		R.id.focus_seekbar,
		R.id.focus_bracketing_seekbar,
		R.id.exposure_seekbar,
		R.id.iso_seekbar,
		R.id.exposure_time_seekbar,
		R.id.white_balance_seekbar
	};

	private final int[] seekbar_icons = {
		R.id.zoom_seekbar_icon,
		R.id.focus_seekbar_icon,
		R.id.focus_bracketing_seekbar_icon,
		R.id.exposure_seekbar_icon,
		R.id.iso_seekbar_icon,
		R.id.exposure_time_seekbar_icon,
		R.id.white_balance_seekbar_icon
	};
	
	private final int[] zoom_controls = {
		R.id.zoom,
		R.id.exposure_seekbar_zoom,
	};
	
	private ArrayList<Integer> hide_buttons = null;

	public MainUI(MainActivity main_activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "MainUI");
		this.main_activity = main_activity;
		this.resources = main_activity.getResources();
		
		shutter_icon_material = Prefs.getString(Prefs.SHUTTER_BUTTON_STYLE, "hedgecam").equals("material");
		main_activity.findViewById(R.id.gallery)
				.setBackgroundResource(shutter_icon_material ? R.drawable.rounded_gallery : R.drawable.gallery_bg);

		((TextView)main_activity.findViewById(R.id.queue_count))
				.setShadowLayer(resources.getDimension(R.dimen.ctrl_button_shadow), 0, 0, resources.getColor(R.color.ctrl_button_shadow));

		buttons_location = new int[ctrl_panel_buttons.length];
		for(int i = 0; i < ctrl_panel_buttons.length; i++) buttons_location[i] = 0;
		
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			ColorStateList progress_color = ColorStateList.valueOf( resources.getColor(R.color.main_white) );
			ColorStateList thumb_color = ColorStateList.valueOf( resources.getColor(R.color.main_blue) );
			
			for(int id : rotatable_seekbars) {
				SeekBar seekBar = (SeekBar)main_activity.findViewById(id);
				seekBar.setProgressTintList(progress_color);
				seekBar.setThumbTintList(thumb_color);
			}
		}

		for(int id : zoom_controls) {
			ViewGroup zoom_control = (ViewGroup)main_activity.findViewById(id);

			ImageButton button = (ImageButton)zoom_control.getChildAt(0);
			button.setImageResource(0);
			button.setBackgroundResource(R.drawable.zoom_minus);
	
			button = (ImageButton)zoom_control.getChildAt(1);
			button.setImageResource(0);
			button.setBackgroundResource(R.drawable.zoom_plus);
			
			zoom_control.setVisibility(View.GONE);
		}
		
		show_seekbars = Prefs.getBoolean(Prefs.SHOW_SEEKBARS, false);
		system_ui_portrait = Prefs.getString(Prefs.SYSTEM_UI_ORIENTATION, "landscape").equals("portrait");
	}
	
	public void setPreview(Preview preview) {
		this.preview = preview;
	}

	private void setViewRotation(View view, float ui_rotation) {
		setViewRotation(view, ui_rotation, false);
	}

	private void setViewRotation(View view, float ui_rotation, boolean view_position_changed) {
		if (orientation_changed && !view_position_changed) {
			float rotate_by = ui_rotation - view.getRotation();
			if( rotate_by > 181.0f )
				rotate_by -= 360.0f;
			else if( rotate_by < -181.0f )
				rotate_by += 360.0f;
			// view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
			// we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
			view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
		} else view.setRotation(ui_rotation);
	}

	public void layoutUI(boolean first_layout) {
		if (first_layout) {
			overlay_initialized = false;
		}
		layoutUI();
	}
	public void layoutUI() {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI");
			debug_time = System.currentTimeMillis();
		}
		
		View parent = (View)main_activity.findViewById(R.id.ctrl_panel_anchor).getParent();

		int margin_right = 0;
		Display display = main_activity.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
			String immersive_mode = Prefs.getString(Prefs.IMMERSIVE_MODE, "immersive_mode_off");
			if( immersive_mode.equals("immersive_mode_fullscreen") || immersive_mode.equals("immersive_mode_sticky") )
				display.getRealSize(size);
			else if (immersive_mode.equals("immersive_mode_overlay")) {
				display.getSize(size);
				int width = Math.max(size.x, size.y);
				display.getRealSize(size);
				margin_right = Math.max(size.x, size.y)-width;
			} else
				display.getSize(size);
		} else {
			display.getSize(size);
		}
		root_width = Math.max(size.x, size.y);
		root_height = Math.min(size.x, size.y);
		if( MyDebug.LOG ) {
			Log.d(TAG, "	root_width = " + root_width);
			Log.d(TAG, "	root_height = " + root_height);
		}

		final float scale = resources.getDisplayMetrics().density;
		if( MyDebug.LOG )
			Log.d(TAG, "	scale = " + scale);
		
		switch (ui_orientation) {
			case Landscape:
				ui_rotation = Prefs.getBoolean(Prefs.UI_LEFT_HANDED, false) ? (system_ui_portrait ? 270 : 180) : (system_ui_portrait ? 90 : 0);
				break;
			case Portrait:
				ui_rotation = system_ui_portrait ? 0 : 270;
				break;
			default:
				// new code for orientation fixed to landscape
				// the display orientation should be locked to landscape, but how many degrees is that?
				int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
				int degrees = 0;
				switch (rotation) {
					case Surface.ROTATION_0: degrees = 0; break;
					case Surface.ROTATION_90: degrees = 90; break;
					case Surface.ROTATION_180: degrees = 180; break;
					case Surface.ROTATION_270: degrees = 270; break;
					default:
						break;
				}
				// getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
				// relative_orientation is clockwise from landscape-left
				//int relative_orientation = (current_orientation + 360 - degrees) % 360;
				int relative_orientation = (current_orientation + degrees) % 360;
				ui_rotation = (360 - relative_orientation) % 360;
				if( MyDebug.LOG ) {
					Log.d(TAG, "	current_orientation = " + current_orientation);
					Log.d(TAG, "	degrees = " + degrees);
					Log.d(TAG, "	relative_orientation = " + relative_orientation);
				}
		}
		ui_rotation_relative = ui_rotation;
		if (system_ui_portrait) {
			ui_rotation_relative += 270;
			if (ui_rotation_relative >= 360)
				ui_rotation_relative -= 360;
		}


		boolean old_ui_placement_right = ui_placement_right;
		ui_placement_right = true;
		if (ui_rotation_relative == 180) ui_placement_right = false;
		else if( ui_rotation_relative == 90 || ui_rotation_relative == 270 ) ui_placement_right = !Prefs.getBoolean(Prefs.UI_LEFT_HANDED, false);

		GUIType old_gui_type = gui_type;
		String gui_type_string = "default";
		if( ui_rotation_relative == 90 || ui_rotation_relative == 270 ) gui_type_string = Prefs.getString(Prefs.GUI_TYPE_PORTRAIT, "default");
		if (gui_type_string.equals("default")) gui_type_string = Prefs.getString(Prefs.GUI_TYPE, "phone");
		switch (gui_type_string) {
			case ("phone2"):
				gui_type = GUIType.Phone2;
				break;
			case ("tablet"):
				gui_type = GUIType.Tablet;
				break;
			case ("universal"):
				gui_type = GUIType.Universal;
				break;
			case ("classic"):
				gui_type = GUIType.Classic;
				break;
			default:
				gui_type = GUIType.Phone;
				break;
		}

		boolean ctrl_panel_position_changed = old_gui_type != gui_type;
		boolean mode_panel_position_changed = ui_placement_right != old_ui_placement_right;

		boolean radius_auto = false;
		int buttons_margin = resources.getDimensionPixelSize(R.dimen.ctrl_buttons_gap_normal);
		if (gui_type != GUIType.Phone) {
			switch (Prefs.getString(Prefs.CTRL_PANEL_MARGIN, "auto")) {
				case ("small"):
					buttons_margin = resources.getDimensionPixelSize(R.dimen.ctrl_buttons_gap_small);
					break;
				case ("normal"):
					break;
				case ("large"):
					buttons_margin = resources.getDimensionPixelSize(R.dimen.ctrl_buttons_gap_large);
					break;
				case ("xlarge"):
					buttons_margin = resources.getDimensionPixelSize(R.dimen.ctrl_buttons_gap_xlarge);
					break;
				default:
					radius_auto = true;
			}
		}
		// Shadow size of HedgeCam shutter button
		if (!shutter_icon_material) buttons_margin -= resources.getDimensionPixelSize(R.dimen.default_shutter_shadow);

		if (system_ui_portrait) {
			center_vertical = RelativeLayout.CENTER_HORIZONTAL;
			center_horizontal = RelativeLayout.CENTER_VERTICAL;
			align_left = RelativeLayout.ALIGN_TOP;
			align_right = RelativeLayout.ALIGN_BOTTOM;
			left_of = RelativeLayout.ABOVE;
			right_of = RelativeLayout.BELOW;
			align_parent_left = RelativeLayout.ALIGN_PARENT_TOP;
			align_parent_right = RelativeLayout.ALIGN_PARENT_BOTTOM;
			if( ui_placement_right ) {
				above = RelativeLayout.RIGHT_OF;
				below = RelativeLayout.LEFT_OF;
				align_parent_top = RelativeLayout.ALIGN_PARENT_RIGHT;
				align_parent_bottom = RelativeLayout.ALIGN_PARENT_LEFT;
			} else {
				above = RelativeLayout.LEFT_OF;
				below = RelativeLayout.RIGHT_OF;
				align_parent_top = RelativeLayout.ALIGN_PARENT_LEFT;
				align_parent_bottom = RelativeLayout.ALIGN_PARENT_RIGHT;
			}
		} else {
			center_vertical = RelativeLayout.CENTER_VERTICAL;
			center_horizontal = RelativeLayout.CENTER_HORIZONTAL;
			align_left = RelativeLayout.ALIGN_LEFT;
			align_right = RelativeLayout.ALIGN_RIGHT;
			left_of = RelativeLayout.LEFT_OF;
			right_of = RelativeLayout.RIGHT_OF;
			align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
			align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
			if( ui_placement_right ) {
				above = RelativeLayout.ABOVE;
				below = RelativeLayout.BELOW;
				align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
				align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
			} else {
				above = RelativeLayout.BELOW;
				below = RelativeLayout.ABOVE;
				align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
				align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
			}
		}
		popup_anchor = R.id.gallery;
		seekbars_container_anchor = R.id.gallery;
		{
			ind_margin_left = 0;
			ind_margin_top = 0;
			ind_margin_bottom = 0;
			ind_margin_right = 0;
			
			hide_buttons = null;

			View view;
			RelativeLayout.LayoutParams layoutParams;

			view = main_activity.findViewById(R.id.take_photo);
			
			float shutter_size_mul = 1;
			switch (Prefs.getString(Prefs.SHUTTER_BUTTON_SIZE, "normal")) {
				case ("small"):
					shutter_size_mul = 0.888f;
					break;
				case ("large"):
					shutter_size_mul = 1.111f;
					break;
				case ("xlarge"):
					shutter_size_mul = 1.333f;
					break;
				default:
			}

			int shutter_width = (int)((shutter_icon_material
				? resources.getDimensionPixelSize(R.dimen.shutter_size)
				: resources.getDrawable(R.drawable.shutter_photo_selector).getIntrinsicWidth())
				* shutter_size_mul);

			int shutter_margin = (int)(shutter_icon_material ?
				-resources.getDimensionPixelSize(R.dimen.shutter_ring_margin)+scale :
				resources.getDimensionPixelSize(R.dimen.default_shutter_margin) * shutter_size_mul);
			
			if (view.getVisibility() == View.VISIBLE) {
				ind_margin_right = view.getWidth()+shutter_margin;
				popup_anchor = R.id.take_photo;
				seekbars_container_anchor = R.id.take_photo;
			}
//			if (Prefs.getBoolean(Prefs.SHOW_TAKE_PHOTO, true)) {
				setTakePhotoIcon();
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(center_horizontal, 0);
				layoutParams.addRule(center_vertical, RelativeLayout.TRUE);
				layoutParams.addRule(align_parent_left, 0);
				layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
				setMargins(layoutParams, 0, 0, shutter_margin+margin_right, 0);
				layoutParams.width = shutter_width;
				layoutParams.height = shutter_width;
				view.setLayoutParams(layoutParams);
				setViewRotation(view, ui_rotation);
//			}

			if( main_activity.supportsVideoPause() ) {
				int pause_width = (int)((shutter_icon_material
					? resources.getDimensionPixelSize(R.dimen.pause_size)
					: resources.getDrawable(R.drawable.pause_selector).getIntrinsicWidth())
					* shutter_size_mul);
				view = main_activity.findViewById(R.id.pause_video);
				((ImageButton)view).setImageResource(shutter_icon_material ? R.drawable.material_pause_selector : R.drawable.pause_selector);
				int pause_margin = 0;
				if (shutter_icon_material) pause_margin = (shutter_width-pause_width)/2;
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
				layoutParams.addRule(align_parent_left, 0);
				layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
				layoutParams.addRule(above, R.id.take_photo);
				layoutParams.addRule(below, 0);
				layoutParams.width = pause_width;
				layoutParams.height = pause_width;
				setMargins(layoutParams, pause_margin, pause_margin, pause_margin+margin_right, pause_margin);
				view.setLayoutParams(layoutParams);
				setViewRotation(view, ui_rotation);
			}

			view = main_activity.findViewById(R.id.gallery);
			int gallery_width = resources.getDimensionPixelSize(shutter_icon_material ? R.dimen.button_gallery_rounded_size : R.dimen.button_gallery_size);
			int gallery_margin = (shutter_width+shutter_margin*2-gallery_width)/2;
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.width = gallery_width;
			layoutParams.height = gallery_width;
			setMargins(layoutParams, 0, gallery_margin, gallery_margin+margin_right, gallery_margin);
			view.setLayoutParams(layoutParams);
			int padding = resources.getDimensionPixelSize(shutter_icon_material ? R.dimen.button_gallery_rounded_padding : R.dimen.button_gallery_padding);
			view.setPadding(padding, padding, padding, padding);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.queue_count);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.switch_video);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			layoutParams.width = gallery_width;
			layoutParams.height = gallery_width;
			setMargins(layoutParams, 0, gallery_margin, gallery_margin+margin_right, gallery_margin);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);

			view = main_activity.findViewById(R.id.trash);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(align_parent_left, 0);
			layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
			setMargins(layoutParams, 0, 0, margin_right, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
	
			view = main_activity.findViewById(R.id.share);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(left_of, R.id.trash);
			layoutParams.addRule(right_of, 0);
			view.setLayoutParams(layoutParams);
			setViewRotation(view, ui_rotation);
			
			if( MyDebug.LOG ) {
				Log.d(TAG, "	shutter_width = " + shutter_width);
				Log.d(TAG, "	shutter_margin = " + shutter_margin);
				Log.d(TAG, "	gallery_width = " + gallery_width);
				Log.d(TAG, "	gallery_margin = " + gallery_margin);
			}

			if (root_width == 0 || root_height == 0) return;

			int buttons_count = 0;
			int mode_buttons_count = 0;
			int margin = 0;

			for(int i = 0; i < ctrl_panel_buttons.length; i++) {
				view = main_activity.findViewById(ctrl_panel_buttons[i]);
				if (view.getVisibility() == View.VISIBLE){
					if (buttons_location[i] == 2)
						mode_buttons_count++;
					else
						buttons_count++;
				}
			}

			if( MyDebug.LOG ) Log.d(TAG, "buttons_count = " + buttons_count);

			if (buttons_count !=0) {
				button_size = resources.getDimensionPixelSize(R.dimen.ctrl_button_size);
				if(gui_type == GUIType.Phone || gui_type == GUIType.Phone2) {
					int buttons_height = buttons_count * button_size;
					if (buttons_height >= root_height) {
						button_size = root_height/buttons_count;
					} else {
						margin = (root_height-buttons_height)/buttons_count;
					}
				}
			}

			boolean preview_align_left = true;
			boolean preview_has_gap = gui_type == GUIType.Phone;
			switch (Prefs.getString(Prefs.PREVIEW_LOCATION, "auto")) {
				case ("center"):
					preview_align_left = false;
					preview_has_gap = false;
					break;
				case ("left"):
					preview_has_gap = false;
					break;
				case ("left_gap"):
					preview_has_gap = true;
					break;
				default:
					preview_align_left = gui_type != GUIType.Classic;
			}

			int preview_margin = 0;
			if (preview_has_gap && main_activity.getPreview().hasAspectRatio()) {
				double preview_ar = main_activity.getPreview().getAspectRatio();
				if (preview_ar < (double)root_width/(double)root_height && root_width-(int)((double)root_height*preview_ar) >= button_size*2) {
					preview_margin = button_size;
				}
			}

			view = main_activity.findViewById(R.id.preview);
			layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.addRule(center_horizontal, preview_align_left ? 0 : RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_left, preview_align_left ? RelativeLayout.TRUE : 0);
			setMargins(layoutParams, preview_margin, 0, 0, 0);
			view.setLayoutParams(layoutParams);

			if (buttons_count !=0 ) {
				view = main_activity.findViewById(R.id.ctrl_panel_anchor);
				layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();

				int previous = R.id.ctrl_panel_anchor;

				if (gui_type == GUIType.Phone || gui_type == GUIType.Phone2){
					if (gui_type == GUIType.Phone) {
						ind_margin_left = button_size;
						if( ui_rotation_relative == 0 || ui_rotation_relative == 180 ) ind_margin_right = gallery_width+gallery_margin;
					}
					else ind_margin_right += button_size+buttons_margin;

					setMargins(layoutParams, 0, 0, gui_type == GUIType.Phone2 ? buttons_margin : 0, 0);
					layoutParams.addRule(align_parent_top, 0);
					layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
					layoutParams.addRule(align_parent_left, gui_type == GUIType.Phone2 ? 0 : RelativeLayout.TRUE);
					layoutParams.addRule(align_parent_right, 0);
					layoutParams.addRule(left_of, gui_type == GUIType.Phone2 ? R.id.take_photo : 0);
					layoutParams.addRule(right_of, 0);
					view.setLayoutParams(layoutParams);

					boolean is_first = true;
					for(int i = 0; i < ctrl_panel_buttons.length; i++) {
						view = main_activity.findViewById(ctrl_panel_buttons[i]);
						if (view.getVisibility() == View.VISIBLE && buttons_location[i] != 2){
							layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
							setMargins(layoutParams,
								0,
								ui_placement_right ? 0 : (is_first ? margin/2 : margin),
								0,
								ui_placement_right ? (is_first ? margin/2 : margin) : 0
							);
							layoutParams.addRule(align_parent_top, 0);
							layoutParams.addRule(align_parent_bottom, 0);
							layoutParams.addRule(align_parent_left, gui_type == GUIType.Phone2 ? 0 : RelativeLayout.TRUE);
							layoutParams.addRule(align_parent_right, 0);
							layoutParams.addRule(above, previous);
							layoutParams.addRule(below, 0);
							layoutParams.addRule(left_of, gui_type == GUIType.Phone2 ? R.id.ctrl_panel_anchor : 0);
							layoutParams.addRule(right_of, 0);

							layoutParams.width = button_size;
							layoutParams.height = button_size;
							view.setLayoutParams(layoutParams);
							setViewRotation(view, ui_rotation, ctrl_panel_position_changed);

							previous = ctrl_panel_buttons[i];
							if (is_first && view.getVisibility() == View.VISIBLE) {
								is_first = false;
								popup_anchor = ctrl_panel_buttons[i];
								if (gui_type == GUIType.Phone2) seekbars_container_anchor = ctrl_panel_buttons[i];
							}
						}
					}

				} else if (gui_type == GUIType.Classic) {
					layoutParams.setMargins(0, 0, 0, 0);
					layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
					layoutParams.addRule(align_parent_bottom, 0);
					layoutParams.addRule(align_parent_left, 0);
					layoutParams.addRule(align_parent_right, 0);
					layoutParams.addRule(left_of, R.id.gallery);
					layoutParams.addRule(right_of, 0);
					view.setLayoutParams(layoutParams);

					// Fuckin Android makes an invisible gap between rotated button and screen's edge
					int gap_fix = 0;
					if (ui_rotation == 90 || ui_rotation == 270) {
						gap_fix = -(int)(scale/2 + 0.5f);
					}

					for(int i = 0; i < ctrl_panel_buttons.length; i++) {
						view = main_activity.findViewById(ctrl_panel_buttons[i]);
						if (view.getVisibility() == View.VISIBLE && buttons_location[i] != 2){
							layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
							setMargins(layoutParams, 0, gap_fix, buttons_margin, gap_fix);
							layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
							layoutParams.addRule(align_parent_bottom, 0);
							layoutParams.addRule(align_parent_left, 0);
							layoutParams.addRule(align_parent_right, 0);
							layoutParams.addRule(above, 0);
							layoutParams.addRule(below, 0);
							layoutParams.addRule(left_of, previous);
							layoutParams.addRule(right_of, 0);

							layoutParams.width = button_size;
							layoutParams.height = button_size;
							view.setLayoutParams(layoutParams);
							setViewRotation(view, ui_rotation, ctrl_panel_position_changed);

							previous = ctrl_panel_buttons[i];
							buttons_margin = 0;
						}
					}
					
					if (ui_rotation == (ui_placement_right ? 180 : 0))
						ind_margin_top = button_size;

				} else if (gui_type == GUIType.Tablet || gui_type == GUIType.Universal){
					layoutParams.setMargins(0, 0, 0, 0);
					layoutParams.addRule(align_parent_top, 0);
					layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
					layoutParams.addRule(align_parent_left, 0);
					layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
					layoutParams.addRule(left_of, 0);
					layoutParams.addRule(right_of, 0);
					view.setLayoutParams(layoutParams);
					
					int center_y = root_height/2;
					int center_x;
					int radius;
					float angle_start, angle_step;

					if (gui_type == GUIType.Tablet) {
						center_x = root_width-shutter_width/2-shutter_margin;
						radius = shutter_width/2+buttons_margin+button_size/2;
						if (buttons_count > 2) {
							angle_start = 0;
							angle_step = 180/(buttons_count-1);
							if (radius_auto) radius = Math.max(radius, (int)((buttons_count-1)*button_size/Math.PI-(shutter_icon_material ? 0 : resources.getDimensionPixelSize(R.dimen.ctrl_buttons_tablet_min_gap))));
						} else if (buttons_count == 2) {
							angle_start = 45;
							angle_step = 90;
						} else {
							angle_start = 90;
							angle_step = 0;
						}
						if (main_activity.supportsVideoPause() && main_activity.getPreview().isVideo()) hide_buttons = new ArrayList<>();
					} else {
						double y = Math.min(center_y-button_size/2, resources.getDimensionPixelSize(R.dimen.ctrl_panel_universal_y_max));
						angle_start = (float)(180-Math.toDegrees(Math.atan2(y, buttons_margin+resources.getDimensionPixelSize(R.dimen.ctrl_panel_universal_x_start)))*2);
						radius = (int)(y/Math.sin(Math.toRadians(angle_start)));
						center_x = (int)(root_width-shutter_width-shutter_margin-buttons_margin-button_size/2+radius);

						if (buttons_count > 1) {
							angle_step = angle_start*2/(buttons_count-1);
							float angle_max = (float)(Math.toDegrees(Math.asin(((double)button_size)/2/radius))*2.5);
							if (angle_step > angle_max) {
								angle_start = 90-angle_max*(buttons_count-1)/2;
								angle_step = angle_max;
							} else angle_start = 90-angle_start;
						} else {
							angle_start = 90;
							angle_step = 0;
						}
					}

					int button = 0;
					for(int i = 0; i < ctrl_panel_buttons.length; i++) {
						view = main_activity.findViewById(ctrl_panel_buttons[i]);
						if (view.getVisibility() == View.VISIBLE && buttons_location[i] != 2) {
							float angle = angle_start+angle_step*button;
							// Free space for pause button
							if (main_activity.supportsVideoPause() && gui_type == GUIType.Tablet && main_activity.getPreview().isVideo() && angle > 150) {
								hide_buttons.add(ctrl_panel_buttons[i]);
							}
							int direction_y = 1;
							if (angle > 90.0f) {
								angle = 180.0f-angle;
								direction_y = -1;
							}
							int margin_x = (int)(root_width-center_x+radius*Math.sin(Math.toRadians(angle))-button_size/2);
							int margin_y = (int)(center_y-radius*Math.cos(Math.toRadians(angle))*direction_y-button_size/2);
							
							if (margin_x+button_size > ind_margin_right) {
								ind_margin_right = margin_x+button_size;
								popup_anchor = ctrl_panel_buttons[i];
								seekbars_container_anchor = ctrl_panel_buttons[i];
							}
							
							layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
							setMargins(layoutParams, 0, ui_placement_right ? 0 : margin_y, margin_x, ui_placement_right ? margin_y : 0);
							layoutParams.addRule(align_parent_top, 0);
							layoutParams.addRule(align_parent_bottom, 0);
							layoutParams.addRule(align_parent_left, 0);
							layoutParams.addRule(align_parent_right, 0);
							layoutParams.addRule(above, R.id.ctrl_panel_anchor);
							layoutParams.addRule(below, 0);
							layoutParams.addRule(left_of, R.id.ctrl_panel_anchor);
							layoutParams.addRule(right_of, 0);

							layoutParams.width = button_size;
							layoutParams.height = button_size;
							view.setLayoutParams(layoutParams);
							setViewRotation(view, ui_rotation, ctrl_panel_position_changed);
							
							button++;
						}
					}
					if( gui_type == GUIType.Tablet && (ui_rotation == 0 || ui_rotation == 180) ) ind_margin_right = gallery_width+gallery_margin;
				}
			}
			if (mode_buttons_count != 0) {
				int previous = 0;
				// Fuckin Android makes an invisible gap between rotated button and screen's edge
				int gap_fix = 0;
				if (ui_rotation == 90 || ui_rotation == 270) {
					gap_fix = -(int)(scale/2 + 0.5f);
				}
				margin = (root_width-button_size*mode_buttons_count)/2;
				for(int i = 0; i < ctrl_panel_buttons.length; i++) {
					view = main_activity.findViewById(ctrl_panel_buttons[i]);
					if (view.getVisibility() == View.VISIBLE && buttons_location[i] == 2){
						layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
						setMargins(layoutParams, margin, gap_fix, 0, gap_fix);
						layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
						layoutParams.addRule(align_parent_bottom, 0);
						layoutParams.addRule(align_parent_left, previous == 0 ? RelativeLayout.TRUE : 0);
						layoutParams.addRule(align_parent_right, 0);
						layoutParams.addRule(above, 0);
						layoutParams.addRule(below, 0);
						layoutParams.addRule(left_of, 0);
						layoutParams.addRule(right_of, previous);

						layoutParams.width = button_size;
						layoutParams.height = button_size;
						view.setLayoutParams(layoutParams);
						setViewRotation(view, ui_rotation, mode_panel_position_changed);

						previous = ctrl_panel_buttons[i];
						margin = 0;
					}
				}
			}
			ind_margin_right += margin_right;
		}

		layoutSeekbars();
		layoutPopupView(false);

		setSelfieMode(main_activity.selfie_mode);
		setAudioControl(main_activity.audio_control);
		// no need to call setSwitchCameraContentDescription()
		
		if (overlay_initialized) {
			final ImageView overlay = (ImageView)main_activity.findViewById(R.id.overlay);
			if (overlay.getVisibility() == View.VISIBLE) {
				setOverlayImageRotation(overlay);
			}
		} else {
			setOverlayImage();
			overlay_initialized = true;
		}

		if( MyDebug.LOG ) {
			Log.d(TAG, "layoutUI: total time: " + (System.currentTimeMillis() - debug_time));
		}
	}
	
	private void setMargins(RelativeLayout.LayoutParams lp, int left, int top, int right, int bottom) {
		if (system_ui_portrait)
			lp.setMargins(bottom, left, top, right);
		else
			lp.setMargins(left, top, right, bottom);
	}
	
	public void layoutSeekbars() {
		View view;
		
		boolean has_seekbars = false;
		for(int id : manual_control) {
			view = main_activity.findViewById(id);
			if( view.getVisibility() == View.VISIBLE ) {
				has_seekbars = true;
				break;
			}
		}

		last_seekbar = 0;
		if (has_seekbars) {
			int current_rotation = 270;
			switch(Prefs.getString(Prefs.SLIDERS_LOCATION, "shutter")) {
				case "widest":
					current_rotation = ui_placement_right ? 0 : 180;
					break;
				case "auto":
					current_rotation = ui_rotation_relative;
					break;
			}

			int slider_padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_normal);
			switch(Prefs.getString(Prefs.SLIDERS_GAP, "normal")) {
				case "small":
					slider_padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_small);
					break;
				case "large":
					slider_padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_large);
					break;
				case "xlarge":
					slider_padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_xlarge);
					break;
			}

			boolean upside_down = current_rotation == 180 || (!ui_placement_right && current_rotation == 270);
			int rotation_diff = current_rotation - ui_rotation_relative;
			boolean seekbar_upside_down = rotation_diff == 90 || rotation_diff == 180;

			int seekbar_width_id = R.dimen.seekbar_width_large;
			switch(Prefs.getString(Prefs.SLIDERS_SIZE, "large")) {
				case "small":
					seekbar_width_id = R.dimen.seekbar_width_small;
					break;
				case "normal":
					seekbar_width_id = R.dimen.seekbar_width_normal;
					break;
				case "xlarge":
					seekbar_width_id = R.dimen.seekbar_width_xlarge;
					break;
			}
			int width_pixels = Math.min(resources.getDimensionPixelSize(seekbar_width_id),
				(( current_rotation == 0 || current_rotation == 180 ) ? root_width : root_height) - resources.getDimensionPixelSize(R.dimen.ctrl_button_size)*2);

			if (system_ui_portrait) {
				current_rotation -= 270;
				if (current_rotation < 0)
					current_rotation += 360;
			}

			for(int id : manual_control) {
				view = main_activity.findViewById(id);

				if( view.getVisibility() == View.VISIBLE ) {
					RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
					
					layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
					layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, last_seekbar == 0 ? RelativeLayout.TRUE : 0);
					layoutParams.addRule(RelativeLayout.ABOVE, last_seekbar);
					layoutParams.addRule(RelativeLayout.BELOW, 0);

					int margin_bottom = 0;
					if (last_seekbar == 0)
						margin_bottom = resources.getDimensionPixelSize(R.dimen.seekbar_margin_bottom);

					if (!(view instanceof SeekBar)) {
						layoutParams.setMargins(0, 0, 0, margin_bottom);
					}
					else
					view.setLayoutParams(layoutParams);

					if (view instanceof SeekBar) {
						view.setPadding(
							view.getPaddingLeft(),
							slider_padding+(seekbar_upside_down ? margin_bottom : 0),
							view.getPaddingRight(),
							slider_padding+(seekbar_upside_down ? 0 : margin_bottom)
						);
					}
					
					last_seekbar = id;
				}
			}

			int icons_rotation = 0;
			if (current_rotation != ui_rotation) {
				icons_rotation = ui_rotation - current_rotation;
				if (icons_rotation < 0) icons_rotation += 360;
			}

			view = main_activity.findViewById(R.id.seekbars_container);
			view.setVisibility(View.VISIBLE);
			int left = 0;
			if (current_rotation == (system_ui_portrait ? 0 : 270)) {
				left = seekbars_container_anchor;
			}
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			
			layoutParams.addRule(left_of, left);
			layoutParams.addRule(center_horizontal, left == 0 ? RelativeLayout.TRUE : 0);
			if (system_ui_portrait) {
				layoutParams.height = root_height;
				if (current_rotation == 270 || current_rotation == 90) {
					layoutParams.width = root_width;
					layoutParams.setMargins((root_height-root_width)/2, 0, (root_height-root_width)/2, 0);
				} else {
					layoutParams.width = root_height;
					layoutParams.setMargins(0, 0, 0, 0);
				}
			} else {
				int width = RelativeLayout.LayoutParams.MATCH_PARENT;
				if (current_rotation == 270 || current_rotation == 90) {
					layoutParams.width = root_height;
				} else {
					layoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
				}
			}
			view.setLayoutParams(layoutParams);
			view.setRotation(current_rotation);

			for(int i = 0; i < rotatable_seekbars.length; i++) {
				view = main_activity.findViewById(rotatable_seekbars[i]);
				if( view.getVisibility() == View.VISIBLE ) {
					RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
					lp.width = width_pixels;
					view.setLayoutParams(lp);
					view.setRotation(seekbar_upside_down ? 180 : 0);
					
					if (seekbar_icons[i] != 0) {
						int icon_margin = view.getPaddingBottom() - view.getPaddingTop();
						view = main_activity.findViewById(seekbar_icons[i]);
						lp = (RelativeLayout.LayoutParams)view.getLayoutParams();
						lp.setMargins(0, seekbar_upside_down ? icon_margin : 0, 0, seekbar_upside_down ? 0 : icon_margin);
						lp.addRule(RelativeLayout.LEFT_OF, upside_down ? 0 : rotatable_seekbars[i]);
						lp.addRule(RelativeLayout.RIGHT_OF, upside_down ? rotatable_seekbars[i] : 0);
						lp.addRule(RelativeLayout.ALIGN_TOP, rotatable_seekbars[i]);
						lp.addRule(RelativeLayout.ALIGN_BOTTOM, rotatable_seekbars[i]);
						view.setLayoutParams(lp);
						view.setVisibility(View.VISIBLE);
						setViewRotation(view, icons_rotation);
					}
				} else if (seekbar_icons[i] != 0) {
					main_activity.findViewById(seekbar_icons[i]).setVisibility(View.GONE);
				}
			}

			view = main_activity.findViewById(R.id.zoom);
			if( view.getVisibility() == View.VISIBLE ) {
				view.setRotation(seekbar_upside_down ? 180 : 0);
			}
			view = main_activity.findViewById(R.id.exposure_seekbar_zoom);
			if( view.getVisibility() == View.VISIBLE ) {
				view.setRotation(seekbar_upside_down ? 180 : 0);
			}

			cancelSeekbarAnimation();
			view = main_activity.findViewById(R.id.seekbar_hint);
			view.clearAnimation();
			view.setVisibility(View.GONE);
			view.setRotation(icons_rotation);

		} else {
			main_activity.findViewById(R.id.seekbars_container).setVisibility(View.GONE);
		}
	}

	private void layoutPopupView(boolean only_params) {
		boolean from_mode_panel = false;
		for(int i = 0; i < ctrl_panel_buttons.length; i++) {
			if (ctrl_panel_buttons[i] == popup_from) {
				if (buttons_location[i] == 2)
					from_mode_panel = true;
				break;
			}
		}
		final View view = main_activity.findViewById(R.id.popup_container);
		view.setAnimation(null);
		RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
		layoutParams.setMargins(0, 0, 0, 0);
		if (from_mode_panel) {
			layoutParams.addRule(center_horizontal, RelativeLayout.TRUE);
			layoutParams.addRule(center_vertical, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(below, popup_from);
			layoutParams.addRule(left_of, 0);
			layoutParams.addRule(right_of, 0);
			if (system_ui_portrait) {
				int diff = (root_width-root_height+resources.getDimensionPixelSize(R.dimen.ctrl_button_size))/2;
				layoutParams.setMargins(0, diff, 0, diff);
			}
		} else if (gui_type == GUIType.Classic) {
			layoutParams.addRule(center_horizontal, 0);
			layoutParams.addRule(center_vertical, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(below, R.id.popup);
			layoutParams.addRule(left_of, R.id.take_photo);
			layoutParams.addRule(right_of, 0);
			if (system_ui_portrait) {
				layoutParams.setMargins(0, main_activity.findViewById(R.id.take_photo).getTop()-root_height+resources.getDimensionPixelSize(R.dimen.ctrl_button_size), 0, 0);
			}
		} else if (gui_type == GUIType.Tablet || gui_type == GUIType.Universal) {
			layoutParams.addRule(center_horizontal, 0);
			layoutParams.addRule(center_vertical, RelativeLayout.TRUE);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, 0);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(below, 0);
			layoutParams.addRule(left_of, popup_anchor);
			layoutParams.addRule(right_of, 0);
			if (system_ui_portrait) {
				layoutParams.setMargins(0, main_activity.findViewById(popup_anchor).getTop()-root_height, 0, 0);
			}
		} else {
			layoutParams.addRule(center_horizontal, 0);
			layoutParams.addRule(center_vertical, 0);
			layoutParams.addRule(align_parent_top, 0);
			layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE);
			layoutParams.addRule(above, 0);
			layoutParams.addRule(below, 0);
			layoutParams.addRule(left_of, gui_type == GUIType.Phone2 ? popup_anchor : 0);
			layoutParams.addRule(right_of, gui_type == GUIType.Phone2 ? 0 : popup_anchor);
			if (system_ui_portrait) {
				if (gui_type == GUIType.Phone2)
					layoutParams.setMargins(0, main_activity.findViewById(popup_anchor).getTop()-root_height, 0, 0);
				else
					layoutParams.setMargins(0, 0, 0, root_width-root_height-main_activity.findViewById(popup_anchor).getBottom());
			}
		}
		view.setLayoutParams(layoutParams);
		
		if (only_params)
			return;
			
		if (system_ui_portrait && orientation_changed && view.getVisibility() == View.VISIBLE) {
			view.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@SuppressWarnings("deprecation")
					@Override
					public void onGlobalLayout() {
						if( MyDebug.LOG )
							Log.d(TAG, "onGlobalLayout()");
						layoutPopupView(true);
						// stop listening - only want to call this once!
						if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
							view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
						} else {
							view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
						}
					}
				}
			);
		}

		setViewRotation(view, ui_rotation);
		// reset:
		view.setTranslationX(0.0f);
		view.setTranslationY(0.0f);
		if( MyDebug.LOG ) {
			Log.d(TAG, "popup view width: " + view.getWidth());
			Log.d(TAG, "popup view height: " + view.getHeight());
		}
		boolean ui_rotation_portrait = ui_rotation == 90 || ui_rotation == 270;
		if (ui_rotation_portrait) {
			if (from_mode_panel) {
				setViewTranslationY(view, (view.getHeight()-view.getWidth())/2 * (ui_placement_right ? -1 : 1));
			} else if (gui_type == GUIType.Classic) {
				setViewTranslationX(view, (view.getWidth()-view.getHeight())/2 );
				setViewTranslationY(view, (view.getHeight()-view.getWidth())/2 * (ui_placement_right ? -1 : 1));
			} else if (gui_type == GUIType.Tablet || gui_type == GUIType.Universal) {
				setViewTranslationX(view, (view.getWidth()-view.getHeight())/2 );
			} else {
				if (gui_type == GUIType.Phone2) setViewTranslationX(view, (view.getWidth()-view.getHeight())/2 );
				else setViewTranslationX(view, (view.getHeight()-view.getWidth())/2 );
				setViewTranslationY(view, (view.getWidth()-view.getHeight())/2 * (ui_placement_right ? -1 : 1));
			}
		}
	}

	private void setViewTranslationX(View view, float translation) {
		if (system_ui_portrait)
			view.setTranslationY(-translation);
		else
			view.setTranslationX(translation);
	}

	private void setViewTranslationY(View view, float translation) {
		if (system_ui_portrait)
			view.setTranslationX(translation);
		else
			view.setTranslationY(translation);
	}

	/** Set icon for taking photos vs videos.
	 *  Also handles content descriptions for the take photo button and switch video button.
	 */
	public void setTakePhotoIcon() {
		if( MyDebug.LOG )
			Log.d(TAG, "setTakePhotoIcon()");
		if( main_activity.getPreview() != null ) {
			ImageButton view;
			final boolean is_video = main_activity.getPreview().isVideo();
			int resource = 0;
			int bg_resource = 0;
			int content_description = 0;
			
			if (
				main_activity.getPreview().isOnTimer()
				|| main_activity.getPreview().isBurst()
				|| main_activity.getPreview().isWaitingFace()
				|| main_activity.getPreview().isVideoRecording()
			) {
				view = (ImageButton)main_activity.findViewById(R.id.take_photo);
				if( is_video ) {
					if (shutter_icon_material) bg_resource = R.drawable.shutter_material_video_selector;
					else {
						resource = R.drawable.shutter_icon_stop;
						bg_resource = R.drawable.shutter_video_selector;
					}
					content_description = R.string.stop_video;
				}
				else {
					if (shutter_icon_material) {
						if (
							main_activity.selfie_mode &&
							(!Prefs.getString(Prefs.BURST_MODE, "1").equals("1") ||
							!Prefs.getString(Prefs.TIMER, "5").equals("0"))
						) bg_resource = R.drawable.shutter_material_selfie_selector;
						else bg_resource = R.drawable.shutter_material_photo_selector;
					} else {
						resource = R.drawable.shutter_icon_stop;
						bg_resource = R.drawable.shutter_photo_selector;
					}
					content_description = R.string.stop_timer;
				}

				view.setImageResource(resource);
				view.setBackgroundResource(bg_resource);
				view.setContentDescription( resources.getString(content_description) );
				view.setSelected(shutter_icon_material);
			} else {
				resetTakePhotoIcon();
			}

			view = (ImageButton)main_activity.findViewById(R.id.switch_video);
			view.setImageResource(
				is_video ? 
				(shutter_icon_material ? R.drawable.rounded_photo_camera : R.drawable.main_photo_camera) :
				(shutter_icon_material ? R.drawable.rounded_videocam : R.drawable.main_videocam)
			);
			view.setContentDescription( resources.getString(is_video ? R.string.switch_to_photo : R.string.switch_to_video) );
		}
	}
	
	public void resetTakePhotoIcon() {
		final ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		int resource = 0;
		int bg_resource = 0;
		int content_description = 0;

		if( main_activity.getPreview().isVideo() ) {
			if (shutter_icon_material) bg_resource = R.drawable.shutter_material_video_selector;
			else {
				resource = (main_activity.selfie_mode && !Prefs.getString(Prefs.TIMER, "5").equals("0"))
					? R.drawable.shutter_icon_timer
					: R.drawable.shutter_icon_video;
				bg_resource = R.drawable.shutter_video_selector;
			}
			content_description = R.string.start_video;
		}
		else {
			if (shutter_icon_material) {
				bg_resource = R.drawable.shutter_material_photo_selector;
				if (main_activity.selfie_mode) {
					if (!Prefs.getString(Prefs.BURST_MODE, "1").equals("1") || !Prefs.getString(Prefs.TIMER, "5").equals("0"))
						bg_resource = R.drawable.shutter_material_selfie_selector;
				}
			} else {
				resource = R.drawable.shutter_icon_photo;
				if (Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing || (main_activity.selfie_mode && !Prefs.getString(Prefs.BURST_MODE, "1").equals("1"))) resource = R.drawable.shutter_icon_burst;
				else if (main_activity.selfie_mode && !Prefs.getString(Prefs.TIMER, "5").equals("0")) resource = R.drawable.shutter_icon_timer;
				bg_resource = R.drawable.shutter_photo_selector;
			}
			content_description = R.string.take_photo;
		}

		view.setImageResource(resource);
		view.setBackgroundResource(bg_resource);
		view.setContentDescription( resources.getString(content_description) );
		view.setSelected(false);
	}
	
	public void startingVideo() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		if (shutter_icon_material) {
			view.setSelected(true);
		} else {
			view.setImageResource(R.drawable.shutter_icon_stop);
			view.setContentDescription( resources.getString(R.string.stop_video) );
			view.setTag(R.drawable.shutter_icon_stop); // for testing
		}
	}
	
	public void startingTimer() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		if (shutter_icon_material) {
			view.setSelected(true);
		} else {
			view.setImageResource(R.drawable.shutter_icon_stop);
			view.setContentDescription( resources.getString(R.string.stop_timer) );
		}
	}

	/** Set content description for switch camera button.
	 */
	public void setSwitchCameraContentDescription() {
		if( MyDebug.LOG )
			Log.d(TAG, "setSwitchCameraContentDescription()");
		if( main_activity.getPreview() != null && main_activity.getPreview().canSwitchCamera() ) {
			ImageButton view = (ImageButton)main_activity.findViewById(R.id.switch_camera);
			int image = 0;
			int content_description = 0;
			int cameraId = main_activity.getNextCameraId();
			if( main_activity.getPreview().getCameraControllerManager().isFrontFacing( cameraId ) ) {
				image = R.drawable.ctrl_camera_front;
				content_description = R.string.switch_to_front_camera;
			}
			else {
				image = R.drawable.ctrl_camera_rear;
				content_description = R.string.switch_to_back_camera;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "content_description: " + resources.getString(content_description));
			view.setImageResource(image);
			view.setContentDescription( resources.getString(content_description) );
		}
	}

	/** Set content description for pause video button.
	 */
	public void setPauseVideoContentDescription() {
		if (MyDebug.LOG)
			Log.d(TAG, "setPauseVideoContentDescription()");
		ImageButton pauseVideoButton =(ImageButton)main_activity.findViewById(R.id.pause_video);
		int content_description;
		if( main_activity.getPreview().isVideoRecordingPaused() ) {
			content_description = R.string.resume_video;
			pauseVideoButton.setSelected(true);
		}
		else {
			content_description = R.string.pause_video;
			pauseVideoButton.setSelected(false);
		}
		if( MyDebug.LOG )
			Log.d(TAG, "content_description: " + resources.getString(content_description));
		pauseVideoButton.setContentDescription(resources.getString(content_description));
	}

	public boolean getUIPlacementRight() {
		return this.ui_placement_right;
	}

	public void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
		
		if( orientation == OrientationEventListener.ORIENTATION_UNKNOWN )
			return;

		int diff = Math.abs(orientation - current_orientation);
		if( diff > 180 )
			diff = 360 - diff;
		// only change orientation when sufficiently changed
		if( diff > 60 ) {
			orientation = (orientation + 45) / 90 * 90;
			orientation = orientation % 360;
			if( orientation != current_orientation ) {
				this.current_orientation = orientation;
				if( MyDebug.LOG ) {
					Log.d(TAG, "current_orientation is now: " + current_orientation);
				}
				orientation_changed = true;
				layoutUI();
				orientation_changed = false;
			}
		}
	}

	public void setImmersiveMode(final boolean immersive_mode) {
		if( MyDebug.LOG )
			Log.d(TAG, "setImmersiveMode: " + immersive_mode);
		this.immersive_mode = immersive_mode;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				showGUI(!immersive_mode, Prefs.getString(Prefs.IMMERSIVE_MODE, "immersive_mode_off").equals("immersive_mode_everything"));
			}
		});
	}
	
	public boolean inImmersiveMode() {
		return immersive_mode;
	}

	public void showGUI(final boolean show) {
		showGUI(show, false);
	}

	public void showGUI(final boolean show, final boolean hide_all) {
		if( MyDebug.LOG )
			Log.d(TAG, "showGUI: " + show);
		this.show_gui = show;
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				final int visibility = show ? View.VISIBLE : View.GONE;
				
				main_activity.findViewById(R.id.gallery)
					.setVisibility(visibility);

				main_activity.findViewById(R.id.switch_video)
					.setVisibility(visibility);

				updateButtonsLocation();
				for(int i = 0; i < ctrl_panel_buttons.length; i++) {
					main_activity.findViewById(ctrl_panel_buttons[i])
						.setVisibility(buttons_location[i] !=0 ? visibility : View.GONE);
				}
				
				main_activity.findViewById(R.id.zoom)
					.setVisibility(( main_activity.getPreview().supportsZoom() && Prefs.getBoolean(Prefs.SHOW_ZOOM_CONTROLS, false) ) ? visibility : View.GONE);

				main_activity.findViewById(R.id.zoom_seekbar)
					.setVisibility(( main_activity.getPreview().supportsZoom() && Prefs.getBoolean(Prefs.SHOW_ZOOM_SLIDER_CONTROLS, false) ) ? visibility : View.GONE);

				main_activity.findViewById(R.id.take_photo)
					.setVisibility((Prefs.getBoolean(Prefs.SHOW_TAKE_PHOTO, true)) ? visibility : View.GONE);

				if (show) layoutUI();
			}
			
		});
	}
	
	private void updateButtonsLocation() {
		boolean m =
			Prefs.getBoolean(Prefs.SHOW_MODE_PANEL, false) &&
			!Prefs.getString(Prefs.GUI_TYPE_PORTRAIT, "default").equals("classic") &&
			!Prefs.getString(Prefs.GUI_TYPE, "phone").equals("classic");

		buttons_location[BUTTON_SETTINGS] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_SETTINGS, false)) buttons_location[BUTTON_SETTINGS] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_SETTINGS, true)) buttons_location[BUTTON_SETTINGS] = 1;

		buttons_location[BUTTON_POPUP] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_POPUP, false)) buttons_location[BUTTON_POPUP] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_POPUP, true)) buttons_location[BUTTON_POPUP] = 1;

		buttons_location[BUTTON_FOCUS_MODE] = 0;
		if (main_activity.getPreview().supportsFocus() && (main_activity.getPreview().isVideo() || Prefs.getPhotoMode() != Prefs.PhotoMode.FocusBracketing)) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_FOCUS, true)) buttons_location[BUTTON_FOCUS_MODE] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_FOCUS, false)) buttons_location[BUTTON_FOCUS_MODE] = 1;
		}

		buttons_location[BUTTON_FLASH_MODE] = 0;
		if (main_activity.getPreview().supportsFlash()) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_FLASH, true)) buttons_location[BUTTON_FLASH_MODE] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_FLASH, false)) buttons_location[BUTTON_FLASH_MODE] = 1;
		}

		buttons_location[BUTTON_ISO] = 0;
		if (main_activity.getPreview().supportsISO()) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_ISO, true)) buttons_location[BUTTON_ISO] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_ISO, false)) buttons_location[BUTTON_ISO] = 1;
		}

		buttons_location[BUTTON_PHOTO_MODE] = 0;
		if (!main_activity.getPreview().isVideo() && (main_activity.supportsDRO() || main_activity.supportsHDR() || main_activity.supportsExpoBracketing())) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_PHOTO_MODE, true)) buttons_location[BUTTON_PHOTO_MODE] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_PHOTO_MODE, false)) buttons_location[BUTTON_PHOTO_MODE] = 1;
		}

		buttons_location[BUTTON_COLOR_EFFECT] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_COLOR_EFFECT, false)) buttons_location[BUTTON_COLOR_EFFECT] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_COLOR_EFFECT, false)) buttons_location[BUTTON_COLOR_EFFECT] = 1;

		buttons_location[BUTTON_SCENE_MODE] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_SCENE_MODE, false)) buttons_location[BUTTON_SCENE_MODE] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_SCENE_MODE, false)) buttons_location[BUTTON_SCENE_MODE] = 1;

		buttons_location[BUTTON_WHITE_BALANCE] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_WHITE_BALANCE, false)) buttons_location[BUTTON_WHITE_BALANCE] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_WHITE_BALANCE, false)) buttons_location[BUTTON_WHITE_BALANCE] = 1;

		buttons_location[BUTTON_EXPO_METERING_AREA] = 0;
		if (main_activity.getPreview().getMaxNumMeteringAreas() > 0) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_EXPO_METERING_AREA, false)) buttons_location[BUTTON_EXPO_METERING_AREA] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_EXPO_METERING_AREA, false)) buttons_location[BUTTON_EXPO_METERING_AREA] = 1;
		}

		buttons_location[BUTTON_AUTO_ADJUSTMENT_LOCK] = 0;
		if (main_activity.getPreview().supportsAutoAdjustmentLock()) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_LOCK, false)) buttons_location[BUTTON_AUTO_ADJUSTMENT_LOCK] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_LOCK, true)) buttons_location[BUTTON_AUTO_ADJUSTMENT_LOCK] = 1;
		}

		buttons_location[BUTTON_EXPOSURE] = 0;
		if (main_activity.supportsExposureButton()) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_EXPOSURE, false)) buttons_location[BUTTON_EXPOSURE] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_EXPOSURE, true)) buttons_location[BUTTON_EXPOSURE] = 1;
		}

		buttons_location[BUTTON_SWITCH_CAMERA] = 0;
		if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1) {
			if (m && Prefs.getBoolean(Prefs.MODE_PANEL_SWITCH_CAMERA, false)) buttons_location[BUTTON_SWITCH_CAMERA] = 2;
			else if (Prefs.getBoolean(Prefs.CTRL_PANEL_SWITCH_CAMERA, true)) buttons_location[BUTTON_SWITCH_CAMERA] = 1;
		}
		
		buttons_location[BUTTON_FACE_DETECTION] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_FACE_DETECTION, false)) buttons_location[BUTTON_FACE_DETECTION] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_FACE_DETECTION, false)) buttons_location[BUTTON_FACE_DETECTION] = 1;

		buttons_location[BUTTON_AUDIO_CONTROL] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_AUDIO_CONTROL, false)) buttons_location[BUTTON_AUDIO_CONTROL] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_AUDIO_CONTROL, false)) buttons_location[BUTTON_AUDIO_CONTROL] = 1;

		buttons_location[BUTTON_SELFIE_MODE] = 0;
		if (m && Prefs.getBoolean(Prefs.MODE_PANEL_SELFIE_MODE, false)) buttons_location[BUTTON_SELFIE_MODE] = 2;
		else if (Prefs.getBoolean(Prefs.CTRL_PANEL_SELFIE_MODE, true)) buttons_location[BUTTON_SELFIE_MODE] = 1;
	}

	public void enableClickableControls(final boolean enable) {
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				final float alpha = enable ? 1.0f : 0.3f;
				View view = (View) main_activity.findViewById(R.id.switch_video);
				view.setEnabled(enable);
				view.setAlpha(alpha);
				view = (View) main_activity.findViewById(R.id.gallery);
				view.setEnabled(enable);
				view.setAlpha(alpha);
				view = (View) main_activity.findViewById(R.id.queue_count);
				view.setAlpha(alpha);
				boolean is_video = main_activity.getPreview().isVideo();
				for(int id : ctrl_panel_buttons) {
					view = (View) main_activity.findViewById(id);
					if (view.getVisibility() == View.VISIBLE && (enable || (!is_video || (id != R.id.exposure && id != R.id.auto_adjustment_lock)))){
						view.setEnabled(enable);
						
						boolean hide_totally = false;
						if (!enable && hide_buttons != null && hide_buttons.indexOf(id) != -1) hide_totally = true;

						view.setAlpha(hide_totally ? 0.0f : alpha);
					}
				}

				if( !is_video ) {
					for(int id : manual_control) {
						view = (View) main_activity.findViewById(id);
						if (view.getVisibility() == View.VISIBLE){
							view.setEnabled(enable);
							view.setAlpha(enable && view instanceof SeekBar ? 0.9f : alpha);
						}
					}
					for(int id : seekbar_icons) {
						view = (View) main_activity.findViewById(id);
						if (view.getVisibility() == View.VISIBLE){
							view.setEnabled(enable);
							view.setAlpha(alpha/2);
						}
					}
				} else {
					// still allow popup in order to change flash mode when recording video
					boolean enable_popup = false;
					if( main_activity.getPreview().supportsFlash() ) {
						if (main_activity.findViewById(R.id.flash_mode).getVisibility() == View.VISIBLE) {
							view = (View) main_activity.findViewById(R.id.flash_mode);
							view.setEnabled(true);
							view.setAlpha(1.0f);
						
						} else {
							enable_popup = true;
						}
					}
					if( main_activity.getPreview().supportsFocus() ) {
						if (main_activity.findViewById(R.id.focus_mode).getVisibility() == View.VISIBLE) {
							view = (View) main_activity.findViewById(R.id.focus_mode);
							view.setEnabled(true);
							view.setAlpha(1.0f);
						
						} else {
							enable_popup = true;
						}
					}
					if (enable_popup) {
						view = (View) main_activity.findViewById(R.id.popup);
						view.setEnabled(true);
						view.setAlpha(1.0f);
					}
					if (main_activity.getPreview().supportsPhotoVideoRecording()) {
						view = (View) main_activity.findViewById(R.id.switch_video);
						view.setEnabled(true);
						view.setAlpha(1.0f);
					}
				}

				if( !enable ) {
					closePopup(); // we still allow the popup when recording video, but need to update the UI (so it only shows flash options), so easiest to just close
				}
			}
		});
	}

	public void setSelfieMode(final boolean is_selfie) {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.selfie_mode);
		
		int res = 0;
		int descr = 0;
		if (is_selfie) {
			res = R.drawable.ctrl_selfie_red;
			descr = R.string.selfie_mode_stop;
		}
		else {
			res = R.drawable.ctrl_selfie;
			descr = R.string.selfie_mode_start;
		}
		
		view.setImageResource(res);
		view.setContentDescription(resources.getString(descr));
	}

	public void setAudioControl(final boolean state) {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.audio_control);
		
		final int res;
		final int descr;
		if (state) {
			res = R.drawable.ctrl_mic_red;
			descr = R.string.audio_control_stop;
		} else {
			res = R.drawable.ctrl_mic;
			descr = R.string.audio_control_start;
		}
		
		view.setImageResource(res);
		view.setContentDescription(resources.getString(descr));
	}

	public void setFaceDetection(final boolean is_enabled) {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.face_detection);
		
		int res = 0;
		int descr = 0;
		if (is_enabled) {
			res = R.drawable.ctrl_face_red;
			descr = R.string.disable_face_detection;
		}
		else {
			res = R.drawable.ctrl_face;
			descr = R.string.enable_face_detection;
		}
		
		view.setImageResource(res);
		view.setContentDescription(resources.getString(descr));
	}

	public void toggleSeekbars() {
		show_seekbars = !show_seekbars;
		Prefs.setBoolean(Prefs.SHOW_SEEKBARS, show_seekbars);
		showSeekbars(show_seekbars, true);
	}
	
	public void showSeekbars() {
		if (show_seekbars)
			showSeekbars(true, false);
	}

	public void showSeekbars(boolean show, boolean layout) {
		boolean seekbar_exposure = false;
		boolean seekbar_exposure_buttons = false;
		boolean seekbar_iso = false;
		boolean seekbar_exposure_time = false;
		boolean seekbar_wb = false;

		if (show) {
			if( main_activity.getPreview().getCameraController() != null ) {
				String iso_value = Prefs.getISOPref();
				// with Camera2 API, when using manual ISO we instead show sliders for ISO range and exposure time
				if( main_activity.getPreview().supportsISORange() && iso_value.equals("manual") ) {
					seekbar_iso = true;
					seekbar_exposure_time = main_activity.getPreview().supportsExposureTime();
				}
				else {
					seekbar_exposure = main_activity.getPreview().supportsExposures();
					seekbar_exposure_buttons = seekbar_exposure && Prefs.getBoolean(Prefs.SHOW_EXPOSURE_BUTTONS, true);
				}

				if( main_activity.getPreview().usingCamera2API() && main_activity.getPreview().supportsWhiteBalanceTemperature()) {
					// we also show slider for manual white balance, if in that mode
					seekbar_wb = Prefs.getString(Prefs.WHITE_BALANCE, "auto").equals("manual");
				}
			}
		}

		main_activity.findViewById(R.id.iso_seekbar)
			.setVisibility(seekbar_iso ? View.VISIBLE : View.GONE);

		main_activity.findViewById(R.id.exposure_time_seekbar)
			.setVisibility(seekbar_exposure_time ? View.VISIBLE : View.GONE);

		main_activity.findViewById(R.id.white_balance_seekbar)
			.setVisibility(seekbar_wb ? View.VISIBLE : View.GONE);

		main_activity.findViewById(R.id.exposure_seekbar)
			.setVisibility(seekbar_exposure ? View.VISIBLE : View.GONE);

		main_activity.findViewById(R.id.exposure_seekbar_zoom)
			.setVisibility(seekbar_exposure_buttons ? View.VISIBLE : View.GONE);

		if (layout) layoutSeekbars();
	}
	
	public void updateSeekbars() {
		updateSeekbars(true);
	}

	public void updateSeekbars(boolean layout) {
		if (!layout) {
			if (show_seekbars) showSeekbars(true, false);
		} else if (Prefs.getBoolean(Prefs.SLIDERS_AUTO_SWITCH, true)) {
			show_seekbars = Prefs.getISOPref().equals("manual") || Prefs.getString(Prefs.WHITE_BALANCE, "auto").equals("manual");
			Prefs.setBoolean(Prefs.SHOW_SEEKBARS, show_seekbars);
			showSeekbars(show_seekbars, true);
		} else if (show_seekbars) showSeekbars(true, true);
	}

	
	public void setFlashIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setFlashIcon");
		
		setPopupIcon(R.id.flash_mode, R.array.flash_values, R.array.flash_icons,
				main_activity.getPreview().getCurrentFlashValue(), R.drawable.ctrl_flash_on);
	}

	public void setFocusIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setFocusIcon");
		
		setPopupIcon(R.id.focus_mode, R.array.focus_mode_values, R.array.focus_mode_icons,
				main_activity.getPreview().getCurrentFocusValue(), R.drawable.ctrl_focus_mode);
	}
	
	public void setPhotoModeIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setPhotoModeIcon");
		
		setPopupIcon(R.id.photo_mode, R.array.photo_mode_values, R.array.photo_mode_icons,
				Prefs.getPhotoModePref(), R.drawable.ctrl_mode_standard);
	}
	
	public void setWhiteBalanceIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setWhiteBalanceIcon");
		
		final String def = "auto";
		((ImageButton)main_activity.findViewById(R.id.white_balance))
			.setImageResource(Prefs.getString(Prefs.WHITE_BALANCE, def).equals(def) ? R.drawable.ctrl_wb : R.drawable.ctrl_wb_red);
	}

	public void setSceneModeIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setSceneModeIcon");
		
		final String def = "auto";
		((ImageButton)main_activity.findViewById(R.id.scene_mode))
			.setImageResource(Prefs.getString(Prefs.SCENE_MODE, def).equals(def) ? R.drawable.ctrl_scene : R.drawable.ctrl_scene_red);
	}
	
	public void setColorEffectIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setColorEffectIcon");
		
		final String def = "none";
		((ImageButton)main_activity.findViewById(R.id.color_effect))
			.setImageResource(Prefs.getString(Prefs.COLOR_EFFECT, def).equals(def) ? R.drawable.ctrl_color_effect : R.drawable.ctrl_color_effect_red);
	}

	public void setPopupIcon(final int icon_id, final int values_id, final int icons_id, final String current_value, final int default_icon) {
		if( MyDebug.LOG ) Log.d(TAG, "setFocusIcon");

		ImageButton button = (ImageButton)main_activity.findViewById(icon_id);

		String [] icons = resources.getStringArray(icons_id);
		String [] values = resources.getStringArray(values_id);

		int resource = default_icon;
		if( icons != null && values != null ) {
			int index = -1;
			for(int i=0;i<values.length && index==-1;i++) {
				if( values[i].equals(current_value) ) {
					index = i;
					break;
				}
			}
			if( index != -1 ) {
				resource = resources.getIdentifier(icons[index], null, main_activity.getPackageName());
			}
		}
		
		button.setImageResource(resource);
	}

	public void setExposureIcon() {
		if( MyDebug.LOG ) Log.d(TAG, "setExposureIcon");

		ImageButton button = (ImageButton)main_activity.findViewById(R.id.exposure);
		int resource = R.drawable.ctrl_exposure;

		if (!Prefs.getISOPref().equals("manual")) {
			int value = main_activity.getPreview().getCurrentExposure();
			if (value > 0) {
				resource = R.drawable.ctrl_exposure_pos;
			} else if (value < 0) {
				resource = R.drawable.ctrl_exposure_neg;
			}
		}
		
		button.setImageResource(resource);
	}
	
	public void setISOIcon() {
		String value = Prefs.getISOPref();
		String text;
		if (value.equals("auto"))
			text = "A";
		else if (value.equals("manual"))
			text = "M";
		else
			text = resources.getString(R.string.iso) + "\n" + StringUtils.fixISOString(value);
		
		int text_size = resources.getDimensionPixelSize(text.length() == 1 ? R.dimen.ctrl_button_text_large : R.dimen.ctrl_button_text);

		Button button = (Button)main_activity.findViewById(R.id.iso);
		button.setLineSpacing(0f, 0.9f);
		button.setGravity(Gravity.CENTER);
		button.setPadding(0,(int)(text_size*0.14*-1),0,0);
		button.setText(text);
		button.setTextSize(TypedValue.COMPLEX_UNIT_PX, text_size);
		button.setTextColor(Color.WHITE);
		button.setTypeface(button.getTypeface(), Typeface.BOLD);
		button.setShadowLayer(resources.getDimension(R.dimen.ctrl_button_shadow), 0, 0, resources.getColor(R.color.ctrl_button_shadow));
	}

	public void setPopupIcons() {
		if (isVisible(R.id.flash_mode)) setFlashIcon();
		if (isVisible(R.id.focus_mode)) setFocusIcon();
		if (isVisible(R.id.photo_mode)) setPhotoModeIcon();
		if (isVisible(R.id.exposure)) setExposureIcon();
		if (isVisible(R.id.white_balance)) setWhiteBalanceIcon();
		if (isVisible(R.id.scene_mode)) setSceneModeIcon();
		if (isVisible(R.id.color_effect)) setColorEffectIcon();
		if (isVisible(R.id.iso)) setISOIcon();
		if (isVisible(R.id.auto_adjustment_lock)) {
			ImageButton button = (ImageButton)main_activity.findViewById(R.id.auto_adjustment_lock);
			button.setImageResource(main_activity.getPreview().isAutoAdjustmentLocked() ? R.drawable.ctrl_lock_red : R.drawable.ctrl_lock);
		}
	}

	public void closePopup() {
		if( MyDebug.LOG )
			Log.d(TAG, "close popup");
		if( popupIsOpen() ) {
			((ViewGroup)main_activity.findViewById(R.id.popup_container)).setVisibility(View.GONE);
			main_activity.initImmersiveMode(); // to reset the timer when closing the popup
		}
	}

	public boolean popupIsOpen() {
		return ((ViewGroup)main_activity.findViewById(R.id.popup_container)).getVisibility() == View.VISIBLE;
	}
	
	public void destroyPopup() {
		if( popupIsOpen() ) {
			closePopup();
		}
		((ViewGroup)main_activity.findViewById(R.id.popup_container)).removeAllViews();
		popup_view = null;
	}

	public void togglePopup(View view) {
		
		PopupView.PopupType popup_type = PopupView.PopupType.Main;
		switch (view.getId()) {
			case R.id.focus_mode:
				popup_type = PopupView.PopupType.Focus;
				break;
			case R.id.flash_mode:
				popup_type = PopupView.PopupType.Flash;
				break;
			case R.id.iso:
				popup_type = PopupView.PopupType.ISO;
				break;
			case R.id.photo_mode:
				popup_type = PopupView.PopupType.PhotoMode;
				break;
			case R.id.color_effect:
				popup_type = PopupView.PopupType.ColorEffect;
				break;
			case R.id.scene_mode:
				popup_type = PopupView.PopupType.SceneMode;
				break;
			case R.id.white_balance:
				popup_type = PopupView.PopupType.WhiteBalance;
				break;
		}
		
		if (popup_type == PopupView.PopupType.ISO && preview.supportsISORange() && preview.isVideo()) {
			String new_iso;
			if (Prefs.getISOPref().equals("manual"))
				new_iso = "auto";
			else
				new_iso = "manual";
			
			Prefs.setISOPref(new_iso);
			
			setManualIsoSeekbars();
			updateSeekbars();
			setExposureIcon();

			main_activity.updateForSettings(StringUtils.getISOString(new_iso));
			setISOIcon();

			return;
		} else if (popup_type == PopupView.PopupType.Flash && (preview.isVideo() || (preview.isManualMode() && !preview.isVideo()))) {
			List<String> values = preview.getSupportedFlashValues();
			String on_value = preview.isVideo() ? "flash_torch" : "flash_on";
			if (values.contains("flash_off") && values.contains(on_value)) {
				String value = "flash_off";
				if (preview.getCurrentFlashValue().equals("flash_off")) {
					value = on_value;
				}
				preview.updateFlash(value);
				setFlashIcon();
				return;
			}
		}

		popup_from = view.getId();

		final ViewGroup popup_container = (ViewGroup)main_activity.findViewById(R.id.popup_container);
		if( popupIsOpen() && current_popup == popup_type ) {
			closePopup();
			return;
		}
		if( preview.getCameraController() == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "camera not opened!");
			return;
		}

		if( MyDebug.LOG )
			Log.d(TAG, "open popup");

		preview.cancelTimer(); // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
		main_activity.stopAudioListeners(true);
		
		final long time_s = System.currentTimeMillis();

		{
			// prevent popup being transparent
			switch (Prefs.getString(Prefs.POPUP_COLOR, "black")) {
				case "dark_gray":
					popup_container.setBackgroundColor(resources.getColor(R.color.popup_bg_dkgray));
					break;
				case "dark_blue":
					popup_container.setBackgroundColor(resources.getColor(R.color.popup_bg_dkblue));
					break;
				case "light_gray":
					popup_container.setBackgroundColor(resources.getColor(R.color.popup_bg_ltgray));
					break;
				case "white":
					popup_container.setBackgroundColor(resources.getColor(R.color.popup_bg_white));
					break;
				default:
					popup_container.setBackgroundColor(resources.getColor(R.color.popup_bg_black));
			}
			popup_container.setAlpha(0.9f);
		}
		
		if( popup_view != null && current_popup != popup_type) {
			popup_container.removeAllViews();
			popup_view = null;
			popup_container.setVisibility(View.INVISIBLE);
		}

		current_popup = popup_type;

		if( popup_view == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "create new popup_view");
			popup_view = new PopupView(main_activity, popup_type);
			popup_container.addView(popup_view);
			layoutPopupView(false);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "use cached popup_view");
		}
		popup_container.setVisibility(View.VISIBLE);
		
		if (Prefs.getString(Prefs.IMMERSIVE_MODE, "immersive_mode_off").equals("immersive_mode_low_profile")) {
			main_activity.getWindow().getDecorView().setSystemUiVisibility(0);
		}
		
		// need to call layoutUI to make sure the new popup is oriented correctly
		// but need to do after the layout has been done, so we have a valid width/height to use
		// n.b., even though we only need the portion of layoutUI for the popup container, there
		// doesn't seem to be any performance benefit in only calling that part
		popup_container.getViewTreeObserver().addOnGlobalLayoutListener(
			new OnGlobalLayoutListener() {
				@SuppressWarnings("deprecation")
				@Override
				public void onGlobalLayout() {
					if( MyDebug.LOG )
						Log.d(TAG, "onGlobalLayout()");
					if( MyDebug.LOG )
						Log.d(TAG, "time after global layout: " + (System.currentTimeMillis() - time_s));
					layoutPopupView(false);
					if( MyDebug.LOG )
						Log.d(TAG, "time after layoutUI: " + (System.currentTimeMillis() - time_s));
					// stop listening - only want to call this once!
					if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
						popup_container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					} else {
						popup_container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					}
				}
			}
		);

		if( MyDebug.LOG )
			Log.d(TAG, "time to create popup: " + (System.currentTimeMillis() - time_s));
	}

	public void enableFrontScreenFlasn(final boolean state) {
		main_activity.findViewById(R.id.front_flash).setVisibility(state ? View.VISIBLE : View.GONE);
	}

	public int[] getIndicationMargins() {
		int[] margins = new int[4];
		margins[0] = ind_margin_left;
		margins[1] = ind_margin_top;
		margins[2] = ind_margin_right;
		margins[3] = ind_margin_bottom;
		if (last_seekbar != 0) {
			View view = main_activity.findViewById(R.id.seekbars_container);
			if (view.getVisibility() == View.VISIBLE) {
				int view_rotation = (int)view.getRotation();
				int add_to_margin = -1;
				if (ui_rotation == view_rotation) {
					if (ui_rotation_relative == 0 || ui_rotation_relative == 180)
						add_to_margin = 3;
					else if (ui_rotation_relative == 270)
						add_to_margin = 2;
				} else if (ui_rotation_relative == 90 && view_rotation == (system_ui_portrait ? 0 : 270))
					add_to_margin = 2;
					
				if (add_to_margin >= 0)
					margins[add_to_margin] += view.getHeight()-main_activity.findViewById(last_seekbar).getTop();
			}
		}

		return margins;
	}

	public int getRootWidth() {
		return root_width;
	}

	public int getRootHeight() {
		return root_height;
	}
	
	public GUIType getGUIType() {
		return gui_type;
	}
	
	public boolean isVisible(final int id) {
		return main_activity.findViewById(id).getVisibility() == View.VISIBLE;
	}
	
	public void updateOrientationPrefs() {
		switch (Prefs.getString(Prefs.GUI_ORIENTATION, "auto")) {
			case "landscape":
				this.ui_orientation = Orientation.Landscape;
				break;
			case "portrait":
				this.ui_orientation = Orientation.Portrait;
				break;
			default:
				this.ui_orientation = Orientation.Auto;
		}
	}

	public Orientation getOrientation() {
		return ui_orientation;
	}
	
	public int getUIRotation() {
		return ui_rotation;
	}

	public int getUIRotationRelative() {
		return ui_rotation_relative;
	}
	
	public PopupView getPopupView() {
		return popup_view;
	}
	
	private boolean seekbars_was_visible;
	public void multitouchEventStart() {
		if( MyDebug.LOG )
			Log.d(TAG, "multitouchEventStart");
		View view = main_activity.findViewById(R.id.seekbars_container);
		seekbars_was_visible = view.getVisibility() == View.VISIBLE;
		if (seekbars_was_visible) {
			view.setVisibility(View.GONE);
		}
	}

	public void multitouchEventStop() {
		if( MyDebug.LOG )
			Log.d(TAG, "multitouchEventStop");
		if (seekbars_was_visible) {
			main_activity.findViewById(R.id.seekbars_container).setVisibility(View.VISIBLE);
			seekbars_was_visible = false;
		}
		saveZoom();
	}

	public void changeSeekbar(int seekBarId, int change) {
		if( MyDebug.LOG )
			Log.d(TAG, "changeSeekbar: " + change);
		SeekBar seekBar = (SeekBar)main_activity.findViewById(seekBarId);
		int value = seekBar.getProgress();
		int new_value = value + change;
		if( new_value < 0 )
			new_value = 0;
		else if( new_value > seekBar.getMax() )
			new_value = seekBar.getMax();
		if( MyDebug.LOG ) {
			Log.d(TAG, "value: " + value);
			Log.d(TAG, "new_value: " + new_value);
			Log.d(TAG, "max: " + seekBar.getMax());
		}
		if( new_value != value ) {
			seekBar.setProgress(new_value);
		}
	}

	public void setZoomSeekbar() {
		if( MyDebug.LOG )
			Log.d(TAG, "set up zoom");
		if( MyDebug.LOG )
			Log.d(TAG, "has_zoom? " + preview.supportsZoom());
		ZoomControls zoomControls = (ZoomControls) main_activity.findViewById(R.id.zoom);
		SeekBar zoomSeekBar = (SeekBar) main_activity.findViewById(R.id.zoom_seekbar);

		if( preview.supportsZoom() ) {
			if( Prefs.getBoolean(Prefs.SHOW_ZOOM_CONTROLS, false) ) {
				zoomControls.setIsZoomInEnabled(true);
				zoomControls.setIsZoomOutEnabled(true);
				zoomControls.setZoomSpeed(20);

				zoomControls.setOnZoomInClickListener(new View.OnClickListener(){
					public void onClick(View v){
						zoomIn();
					}
				});
				zoomControls.setOnZoomOutClickListener(new View.OnClickListener(){
					public void onClick(View v){
						zoomOut();
					}
				});
				if( !inImmersiveMode() ) {
					zoomControls.setVisibility(View.VISIBLE);
				}
			}
			else {
				zoomControls.setVisibility(View.INVISIBLE); // must be INVISIBLE not GONE, so we can still position the zoomSeekBar relative to it
			}
			
			zoomSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
			zoomSeekBar.setMax(preview.getMaxZoom());
			zoomSeekBar.setProgress(preview.getCameraController().getZoom());
			zoomSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if( MyDebug.LOG )
						Log.d(TAG, "zoom onProgressChanged: " + progress);
					// note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom, -/+ zoomcontrol)
					// indirectly set zoom via this method, from setting the zoom slider
					preview.zoomTo(progress);
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					saveZoom();
				}
			});

			if( Prefs.getBoolean(Prefs.SHOW_ZOOM_SLIDER_CONTROLS, false) ) {
				if( !inImmersiveMode() ) {
					zoomSeekBar.setVisibility(View.VISIBLE);
				}
			}
			else {
				zoomSeekBar.setVisibility(View.INVISIBLE);
			}
		}
		else {
			zoomControls.setVisibility(View.GONE);
			zoomSeekBar.setVisibility(View.GONE);
		}
	}

	public void setSeekbarZoom(int new_zoom) {
		if( MyDebug.LOG )
			Log.d(TAG, "setSeekbarZoom: " + new_zoom);
		SeekBar zoomSeekBar = (SeekBar) main_activity.findViewById(R.id.zoom_seekbar);
		if( MyDebug.LOG )
			Log.d(TAG, "progress was: " + zoomSeekBar.getProgress());
		zoomSeekBar.setProgress(new_zoom);
		if( MyDebug.LOG )
			Log.d(TAG, "progress is now: " + zoomSeekBar.getProgress());
	}

	public void zoomIn() {
		changeSeekbar(R.id.zoom_seekbar, 1);
		saveZoom();
	}

	public void zoomOut() {
		changeSeekbar(R.id.zoom_seekbar, -1);
		saveZoom();
	}
	
	private void saveZoom() {
		if (preview.supportsZoom() && Prefs.getBoolean(Prefs.SAVE_ZOOM, false)) {
			final CameraController camera_controller = main_activity.getPreview().getCameraController();
			if (camera_controller != null) {
				String pref_key = Prefs.ZOOM + (main_activity.getPreview().usingCamera2API() ? "_2_" : "_1_") + camera_controller.getCameraId();
				int value = camera_controller.getZoom();
				if (value == 0)
					Prefs.clearPref(pref_key);
				else
					Prefs.setInt(pref_key, value);
			}
		}
	}

	public void setExposureSeekbar() {
		if( preview.supportsExposures() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set up exposure compensation");
			final int min_exposure = preview.getMinimumExposure();
			SeekBar exposure_seek_bar = ((SeekBar)main_activity.findViewById(R.id.exposure_seekbar));
			exposure_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
			exposure_seek_bar.setMax( preview.getMaximumExposure() - min_exposure );
			exposure_seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );
			exposure_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if( MyDebug.LOG )
						Log.d(TAG, "exposure seekbar onProgressChanged: " + progress);
					preview.setExposure(min_exposure + progress);
					setExposureIcon();
					if (fromUser)
						setSeekbarHint(seekBar, StringUtils.getExposureCompensationString(min_exposure + progress));
					else if (((View)seekBar).getVisibility() == View.VISIBLE)
						showSeekbarHint(seekBar, StringUtils.getExposureCompensationString(min_exposure + progress), false);
					else
						Utils.showToast(resources.getString(R.string.exposure_compensation) + " " + StringUtils.getExposureCompensationString(min_exposure + progress));
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
					showSeekbarHint(seekBar, StringUtils.getExposureCompensationString(min_exposure + seekBar.getProgress()), true);
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					hideSeekbarHint();
				}
			});

			ZoomControls seek_bar_zoom = (ZoomControls)main_activity.findViewById(R.id.exposure_seekbar_zoom);
			seek_bar_zoom.setOnZoomInClickListener(new View.OnClickListener(){
				public void onClick(View v){
					changeExposure(1);
				}
			});
			seek_bar_zoom.setOnZoomOutClickListener(new View.OnClickListener(){
				public void onClick(View v){
					changeExposure(-1);
				}
			});
		}
	}

	private final int[] std_exposures = {
		32000, 16000, 8000, 4000, 2000, 1000, 500, 250, 125, 100, 60, 50, 30, 25, 15, 8, 4, 2, 1,
		2, 5, 10, 15, 20, 30, 60
	};
	private class Exposure {
		public String text;
		public long exposure_time;

		public Exposure(String text, long exposure_time) {
			this.text = text;
			this.exposure_time = exposure_time;
		}
	};
	
	private final List<Exposure> exposures = new ArrayList<>();
	private boolean iso_steps;
	private boolean exposure_steps;

	public void setManualIsoSeekbars() {
		if( preview.supportsISORange()) {
			if( MyDebug.LOG )
				Log.d(TAG, "set up iso");
			
			final CameraController camera_controller = preview.getCameraController();
			if (camera_controller == null) return;
			
			final boolean is_manual = Prefs.getISOPref().equals("manual");

			SeekBar iso_seek_bar = ((SeekBar)main_activity.findViewById(R.id.iso_seekbar));
			iso_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
			if (is_manual) {
				final int iso_min = preview.getMinimumISO();
				final int iso_max = preview.getMaximumISO();
				final int iso_value = Math.min(Math.max(Prefs.getInt(Prefs.MANUAL_ISO, iso_max/2), iso_min), iso_max);
				iso_steps = Prefs.getBoolean(Prefs.ISO_STEPS, false);
				final int steps = iso_steps ? (31-Integer.numberOfLeadingZeros(iso_max/iso_min))*3 : manual_n;
				
				camera_controller.setISO(iso_value);
				
				setProgressSeekbarExponential(iso_seek_bar, iso_min, iso_max, iso_value, steps);
				iso_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					private int iso = 0;
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						if( MyDebug.LOG )
							Log.d(TAG, "iso seekbar onProgressChanged: " + progress);
						double frac = progress/(double)steps;
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time frac: " + frac);

						iso = (int)(MainActivity.exponentialScaling(frac, iso_min, iso_max) + 0.5d);
						preview.setISO(iso);
						if (fromUser)
							setSeekbarHint(seekBar, Integer.toString(iso));
						else if (((View)seekBar).getVisibility() == View.VISIBLE)
							showSeekbarHint(seekBar, Integer.toString(iso), false);
						else
							Utils.showToast(resources.getString(R.string.iso) + " " + iso);
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						showSeekbarHint(seekBar, Integer.toString(iso_value), true);
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						Prefs.setInt(Prefs.MANUAL_ISO, iso);

						hideSeekbarHint();
					}
				});
			}
			if( preview.supportsExposureTime() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "set up exposure time");
				SeekBar exposure_time_seek_bar = ((SeekBar)main_activity.findViewById(R.id.exposure_time_seekbar));
				exposure_time_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
				if (is_manual) {
					final long expo_min = preview.getMinimumExposureTime();
					final long expo_max = preview.getMaximumExposureTime();
					long expo_value = Prefs.getLong(Prefs.EXPOSURE_TIME, camera_controller.getExposureTime());
					expo_value = Math.min(Math.max(expo_value, expo_min), expo_max);
					
					camera_controller.setExposureTime(expo_value);
				
					exposure_steps = Prefs.getBoolean(Prefs.EXPOSURE_STEPS, false);
					exposures.clear();
					if (exposure_steps) {
						exposures.add(new Exposure(StringUtils.getExposureTimeString(expo_min), expo_min));
						int progress = 0;
						for(int i = 0; i < std_exposures.length; i++) {
							final boolean is_second = i > 0 && std_exposures[i-1] < std_exposures[i];
							long exposure = (long)(is_second ? 1000000000.0d*std_exposures[i]+0.5d : 1000000000.0d/std_exposures[i]+0.5d);
							if (exposure > expo_min && exposure < expo_max) {
								exposures.add(new Exposure(
									((is_second || std_exposures[i] == 1) ? Integer.toString(std_exposures[i]) : "1/" + std_exposures[i]) + " " + resources.getString(R.string.seconds_abbreviation),
									exposure
								));
								if (exposure <= expo_value)
									progress = exposures.size()-1;
							}
						}
						exposures.add(new Exposure(StringUtils.getExposureTimeString(expo_max), expo_max));
						if (expo_max == expo_value)
							progress = exposures.size()-1;

						exposure_time_seek_bar.setMax(exposures.size()-1);
						exposure_time_seek_bar.setProgress(progress);
					} else {
						setProgressSeekbarExponential(exposure_time_seek_bar, expo_min, expo_max, expo_value);
					}

					exposure_time_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
						private long exposure_time = 0;

						@Override
						public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time seekbar onProgressChanged: " + progress);
							double frac = progress/(double)manual_n;
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time frac: " + frac);

							String text;
							if (exposure_steps) {
								Exposure exposure = exposures.get(progress);
								exposure_time = exposure.exposure_time;
								text = exposure.text;
							} else {
								exposure_time = (long)(MainActivity.exponentialScaling(frac, expo_min, expo_max) + 0.5d);
								text = StringUtils.getExposureTimeString(exposure_time);
							}

							preview.setExposureTime(exposure_time);

							if (fromUser)
								setSeekbarHint(seekBar, text);
							else if (((View)seekBar).getVisibility() == View.VISIBLE)
								showSeekbarHint(seekBar, text, false);
							else
								Utils.showToast(resources.getString(R.string.exposure) + " " + text);
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
							showSeekbarHint(seekBar, exposure_steps ? exposures.get(seekBar.getProgress()).text : StringUtils.getExposureTimeString(exposure_time), true);
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
							Prefs.setLong(Prefs.EXPOSURE_TIME, exposure_time);
							
							hideSeekbarHint();
						}
					});
				}
			}
		}
	}

	public void changeExposure(int change) {
		if (Prefs.getISOPref().equals("manual")) {
			changeSeekbar(R.id.exposure_time_seekbar, exposure_steps ? change : change*10);
		} else {
			changeSeekbar(R.id.exposure_seekbar, change);
			setExposureIcon();
		}
	}

	public void changeISO(int change) {
		changeSeekbar(R.id.iso_seekbar, iso_steps ? change : change*10);
	}

	public void setManualFocusSeekbars() {
		if( MyDebug.LOG )
			Log.d(TAG, "setManualFocusSeekbars()");
		final boolean is_bracketing = !preview.isVideo() && Prefs.getPhotoMode() == Prefs.PhotoMode.FocusBracketing;
		final SeekBar focusSeekBar = (SeekBar)main_activity.findViewById(R.id.focus_seekbar);
		final SeekBar focusBracketingSeekBar = (SeekBar)main_activity.findViewById(R.id.focus_bracketing_seekbar);
		focusSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
		focusBracketingSeekBar.setOnSeekBarChangeListener(null);
		final boolean is_visible = is_bracketing || (preview.getCurrentFocusValue() != null && preview.getCurrentFocusValue().equals("focus_mode_manual2"));
		if (is_visible) {
			final boolean focus_range_macro;
			String icon = IconView.FOCUS;

			final double min_value;
			final double max_value;
			double focus_distance = (double)Prefs.getFloat(Prefs.FOCUS_DISTANCE, 0.0f);

			final String focus_range_pref = Prefs.getString(Prefs.FOCUS_RANGE, "default");
			switch (focus_range_pref) {
				case "macro":
					min_value = 4;
					max_value = preview.getMinimumFocusDistance();
					focus_range_macro = true;
					break;
				case "portrait":
					min_value = 0.5;
					max_value = 10;
					focus_range_macro = false;
					break;
				case "room":
					min_value = 0.2;
					max_value = 5;
					focus_range_macro = false;
					break;
				case "group":
					min_value = 0.2;
					max_value = 2;
					focus_range_macro = false;
					break;
				case "landscape":
					min_value = 0.0;
					max_value = 2.0;
					focus_range_macro = false;
					break;
				case "landscape_macro":
					if (!is_bracketing) {
						if (focus_distance < 2) {
							focus_range_macro = false;
						} else {
							focus_range_macro = true;
						}
						min_value = focus_range_macro ? 1.9 : 0.0;
						max_value = focus_range_macro ? preview.getMinimumFocusDistance() : 2.0;
						
						icon = focus_range_macro ? IconView.FOCUS_MACRO : IconView.FOCUS_LANDSCAPE;
						break;
					}
				default:
					min_value = 0.0;
					max_value = preview.getMinimumFocusDistance();
					focus_range_macro = false;
			}

			focus_distance = Math.min(Math.max(focus_distance, min_value), max_value);
			
			preview.setFocusDistance((float)focus_distance);

			((IconView)main_activity.findViewById(R.id.focus_seekbar_icon)).setText(icon);

			setProgressSeekbarExponential(focusSeekBar, min_value, max_value, focus_distance);
			focusSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				private float focus_distance = 0.0f;
				private boolean is_macro = focus_range_macro;
				double current_min_value = min_value;
				double current_max_value = max_value;

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					double frac = progress/(double)manual_n;
					focus_distance = (float)MainActivity.exponentialScaling(frac, current_min_value, current_max_value);
					preview.setFocusDistance(focus_distance);
					if (fromUser)
						setSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance));
					else if (((View)seekBar).getVisibility() == View.VISIBLE)
						showSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance), false);
					else
						Utils.showToast(resources.getString(R.string.focus_distance) + " " + StringUtils.getFocusDistanceString(focus_distance));
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
					if (Prefs.getBoolean(Prefs.ZOOM_WHEN_FOCUSING, false))
						preview.focusZoom();

					showSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance), true);
				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					if (Prefs.getBoolean(Prefs.ZOOM_WHEN_FOCUSING, false))
						preview.resetZoom();

					hideSeekbarHint();

					Prefs.setFloat(Prefs.FOCUS_DISTANCE, focus_distance);
					
					if (!is_bracketing && focus_range_pref.equals("landscape_macro")) {
						int progress = seekBar.getProgress();
						if (!is_macro && progress == manual_n) {
							is_macro = true;

							((IconView)main_activity.findViewById(R.id.focus_seekbar_icon)).setText(IconView.FOCUS_MACRO);
							current_min_value = 1.9;
							current_max_value = preview.getMinimumFocusDistance();
							setProgressSeekbarExponential(focusSeekBar, current_min_value, current_max_value, focus_distance);
						} else if (is_macro && progress == 0) {
							is_macro = false;

							((IconView)main_activity.findViewById(R.id.focus_seekbar_icon)).setText(IconView.FOCUS_LANDSCAPE);
							current_min_value = 0.0;
							current_max_value = 2.0;
							setProgressSeekbarExponential(focusSeekBar, current_min_value, current_max_value, focus_distance);
						}

					}
				}
			});
			
			if (is_bracketing) {
				focus_min_value = min_value;
				focus_max_value = max_value;

				focus_distance = (double)Prefs.getFloat(Prefs.FOCUS_BRACKETING_DISTANCE, 0.0f);
				focus_distance = Math.min(Math.max(focus_distance, min_value), max_value);
				
				setProgressSeekbarExponential(focusBracketingSeekBar, min_value, max_value, focus_distance);
				focusBracketingSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					private float focus_distance = 0.0f;

					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						double frac = progress/(double)manual_n;
						focus_distance = (float)MainActivity.exponentialScaling(frac, min_value, max_value);
						preview.setFocusDistance(focus_distance);
						if (fromUser)
							setSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance));
						else if (((View)seekBar).getVisibility() == View.VISIBLE)
							showSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance), false);
						else
							Utils.showToast(resources.getString(R.string.focus_distance) + " " + StringUtils.getFocusDistanceString(focus_distance));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						if (Prefs.getBoolean(Prefs.ZOOM_WHEN_FOCUSING, false))
							preview.focusZoom();

						showSeekbarHint(seekBar, StringUtils.getFocusDistanceString(focus_distance), true);
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						if (Prefs.getBoolean(Prefs.ZOOM_WHEN_FOCUSING, false))
							preview.resetZoom();

						hideSeekbarHint();

						Prefs.setFloat(Prefs.FOCUS_BRACKETING_DISTANCE, focus_distance);
						
						double frac = focusSeekBar.getProgress()/(double)manual_n;
						focus_distance = (float)MainActivity.exponentialScaling(frac, min_value, max_value);
						preview.setFocusDistance(focus_distance);
					}
				});
			}
		}
		focusSeekBar.setVisibility(is_visible ? View.VISIBLE : View.GONE);
		focusBracketingSeekBar.setVisibility(is_bracketing ? View.VISIBLE : View.GONE);
	}

	public void changeFocusDistance(int change) {
		changeSeekbar(R.id.focus_seekbar, change*10);
	}

	private boolean white_balance_steps;

	public void setManualWBSeekbar() {
		if( MyDebug.LOG )
			Log.d(TAG, "setManualWBSeekbar");
		if( preview.getSupportedWhiteBalances() != null && preview.supportsWhiteBalanceTemperature() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "set up manual white balance");
			if (Prefs.getString(Prefs.WHITE_BALANCE, "auto").equals("manual")) {
				SeekBar white_balance_seek_bar = ((SeekBar)main_activity.findViewById(R.id.white_balance_seekbar));
				white_balance_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
				final int minimum_temperature = preview.getMinimumWhiteBalanceTemperature();
				final int maximum_temperature = preview.getMaximumWhiteBalanceTemperature();
				
				int value = Prefs.getInt(Prefs.WHITE_BALANCE_TEMPERATURE, 5000);
				preview.setWhiteBalanceTemperature(value);
				
				white_balance_steps = Prefs.getBoolean(Prefs.WHITE_BALANCE_STEPS, false);
				final int step_divider = white_balance_steps ? 100 : 2;
				
				// white balance should use linear scaling
				white_balance_seek_bar.setMax((maximum_temperature - minimum_temperature)/step_divider);
				white_balance_seek_bar.setProgress((value - minimum_temperature)/step_divider);
				white_balance_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
						if( MyDebug.LOG )
							Log.d(TAG, "white balance seekbar onProgressChanged: " + progress);
						int temperature = minimum_temperature + progress*step_divider;
						preview.setWhiteBalanceTemperature(temperature);

						if (fromUser)
							setSeekbarHint(seekBar, Integer.toString(temperature));
						else if (((View)seekBar).getVisibility() == View.VISIBLE)
							showSeekbarHint(seekBar, Integer.toString(temperature), false);
						else
							Utils.showToast(resources.getString(R.string.white_balance) + " " + temperature);
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						showSeekbarHint(seekBar, Integer.toString(minimum_temperature + seekBar.getProgress()*step_divider), true);
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						Prefs.setInt(Prefs.WHITE_BALANCE_TEMPERATURE, minimum_temperature+seekBar.getProgress()*step_divider);
						hideSeekbarHint();
					}
				});
			}
		}
	}

	public void changeWhiteBalance(int change) {
		changeSeekbar(R.id.white_balance_seekbar, white_balance_steps ? change : change*10);
	}

	public void setProgressSeekbarExponential(SeekBar seekBar, double min_value, double max_value, double value) {
		setProgressSeekbarExponential(seekBar, min_value, max_value, value, manual_n);
	}

	public void setProgressSeekbarExponential(SeekBar seekBar, double min_value, double max_value, double value, int steps) {
		seekBar.setMax(steps);
		double frac = MainActivity.exponentialScalingInverse(value, min_value, max_value);
		int new_value = (int)(frac*steps + 0.5); // add 0.5 for rounding
		if( new_value < 0 )
			new_value = 0;
		else if( new_value > steps )
			new_value = steps;
		seekBar.setProgress(new_value);
	}

	private void setProgressSeekbarScaled(SeekBar seekBar, double min_value, double max_value, double value) {
		seekBar.setMax(manual_n);
		double scaling = (value - min_value)/(max_value - min_value);
		double frac = MainActivity.seekbarScalingInverse(scaling);
		int new_value = (int)(frac*manual_n + 0.5); // add 0.5 for rounding
		if( new_value < 0 )
			new_value = 0;
		else if( new_value > manual_n )
			new_value = manual_n;
		seekBar.setProgress(new_value);
	}

	public float[] getFBStack() {
		if( MyDebug.LOG )
			Log.d(TAG, "getFBStack");
		int start = ((SeekBar) main_activity.findViewById(R.id.focus_seekbar)).getProgress();
		int end = ((SeekBar) main_activity.findViewById(R.id.focus_bracketing_seekbar)).getProgress();
		
		if (start != end) {
			int count;
			try {
				count = Integer.parseInt(Prefs.getString(Prefs.FB_COUNT, "3"));
			} catch(NumberFormatException e) {
				count = 3;
			}
			
			if (count > 1) {
				float step = ((float)(end-start))/(count-1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "focus_min_value: " + focus_min_value);
					Log.d(TAG, "focus_max_value: " + focus_max_value);
					Log.d(TAG, "step: " + step);
				}

				float[] stack = new float[count];
				for(int i = 0; i < count; i++) {
					stack[i] = (float)MainActivity.exponentialScaling((double)(start+step*i)/(double)manual_n, focus_min_value, focus_max_value);
					if( MyDebug.LOG )
						Log.d(TAG, "stack[" + i + "]: " + stack[i]);
				}
				return stack;
			}
		}

		return new float[0];
	}

	private AlphaAnimation seekbarHintAnimation;

	public void showSeekbarHint(SeekBar seekBar, String text, boolean fromUser) {
		if( MyDebug.LOG )
			Log.d(TAG, "showSeekbarHint");
		cancelSeekbarAnimation();
		View view = main_activity.findViewById(R.id.seekbar_hint);
		int seekbar_id = seekBar.getId();
		RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
		layoutParams.addRule(RelativeLayout.ABOVE, seekbar_id);
		layoutParams.addRule(RelativeLayout.ALIGN_LEFT, seekbar_id);
		view.setLayoutParams(layoutParams);
		view.setVisibility(View.VISIBLE);
		setSeekbarHint(seekBar, text);
		if (!fromUser)
			hideSeekbarHint(2000);
	}

	public void setSeekbarHint(SeekBar seekBar, String text) {
		if( MyDebug.LOG )
			Log.d(TAG, "setSeekbarHint");
		View view = main_activity.findViewById(R.id.seekbar_hint);
		if (view.getVisibility() == View.VISIBLE) {
			((TextView)view).setText(" " + text + " ");

			double frac = seekBar.getProgress()/(double)seekBar.getMax();
			if (seekBar.getRotation() == 180) frac = 1-frac;
			
			int margin = (int)(frac*(seekBar.getMeasuredWidth()-seekBar.getPaddingLeft()-seekBar.getPaddingRight())+seekBar.getPaddingLeft());
			RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
			layoutParams.setMargins(margin, 0, 0, resources.getDimensionPixelSize(R.dimen.seekbar_hint_margin));
			view.setLayoutParams(layoutParams);
			
			int rotation = (int)view.getRotation();
			int padding = resources.getDimensionPixelSize(R.dimen.seekbar_hint_padding);
			view.setPadding(
				padding + (rotation == 270 ? resources.getDimensionPixelSize(R.dimen.seekbar_hint_pointer) : 0),
				padding + (rotation == 180 ? resources.getDimensionPixelSize(R.dimen.seekbar_hint_pointer) : 0),
				padding + (rotation == 90 ? resources.getDimensionPixelSize(R.dimen.seekbar_hint_pointer) : 0),
				padding + (rotation == 0 ? resources.getDimensionPixelSize(R.dimen.seekbar_hint_pointer) : 0)
			);
		}
	}
	
	private void cancelSeekbarAnimation() {
		if (seekbarHintAnimation != null) {
			seekbarHintAnimation.setAnimationListener(null);
			seekbarHintAnimation.cancel();
			seekbarHintAnimation = null;
		}
	}
	
	private void hideSeekbarHint() {
		hideSeekbarHint(0);
	}
		
	private void hideSeekbarHint(int timeout) {
		if( MyDebug.LOG )
			Log.d(TAG, "hideSeekbarHint");
		seekbarHintAnimation = new AlphaAnimation(1.0f, 0.0f);
		seekbarHintAnimation.setDuration(500);
		if (timeout > 0)
			seekbarHintAnimation.setStartOffset(timeout);

		seekbarHintAnimation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {}

			@Override
			public void onAnimationEnd(Animation animation) {
				if( MyDebug.LOG )
					Log.d(TAG, "onAnimationEnd");
				main_activity.findViewById(R.id.seekbar_hint).setVisibility(View.GONE);
				seekbarHintAnimation = null;
			}

			@Override
			public void onAnimationRepeat(Animation animation) {}
		});
		main_activity.findViewById(R.id.seekbar_hint).startAnimation(seekbarHintAnimation);
	}

	public boolean isSystemUIPortrait() {
		return system_ui_portrait;
	}
	
	public void setOverlayImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "setOverlayImage");
		final ImageView overlay = (ImageView)main_activity.findViewById(R.id.overlay);
		if (Prefs.getBoolean(Prefs.GHOST_IMAGE, false)) {
			final ContentResolver resolver = main_activity.getContentResolver();
			if (resolver != null) {
				int alpha;
				try {
					alpha = Integer.parseInt(Prefs.getString(Prefs.GHOST_IMAGE_ALPHA, "50"));
					alpha = (int)((float)alpha*2.55f+0.5f);
				} catch(NumberFormatException e) {
					alpha = 127;
				}
				overlay.setAlpha(alpha);
				new AsyncTask<Void, Void, Bitmap>() {
					private static final String TAG = "HedgeCam/MainUI/AsyncTask";

					protected Bitmap doInBackground(Void... params) {
						if( MyDebug.LOG )
							Log.d(TAG, "doInBackground");
						Uri uri = null;
						String file_path = null;
						Bitmap bitmap = null;

						String source = Prefs.getString(Prefs.GHOST_IMAGE_SOURCE, "last_photo");
						if (source.equals("last_photo")) {
							StorageUtils.Media media = main_activity.getStorageUtils().getLatestMedia();
							if (media != null)
								uri = media.uri;
						} else {
							if (main_activity.getStorageUtils().isUsingSAF()) {
								String file = Prefs.getString(Prefs.GHOST_IMAGE_FILE_SAF, "");
								if (file.length() > 0)
									uri = Uri.parse(file);
							} else {
								file_path = Prefs.getString(Prefs.GHOST_IMAGE_FILE, "");
								if (file_path.length() == 0)
									file_path = null;
							}
						}

						if (uri != null || file_path != null) {
							View parent = (View)(overlay.getParent());
							int parent_width = Math.max(parent.getMeasuredWidth(), parent.getMeasuredHeight());
							int parent_height = Math.min(parent.getMeasuredWidth(), parent.getMeasuredHeight());
							if( MyDebug.LOG ) {
								Log.d(TAG, "	parent_width: " + parent_width);
								Log.d(TAG, "	parent_height: " + parent_height);
							}

							InputStream is = null;
							ExifInterface exif = null;
							try {
								if (uri != null)
									is = resolver.openInputStream(uri);
								else
									is = new FileInputStream(file_path);

								if (is != null) {
									BitmapFactory.Options options = new BitmapFactory.Options();
									options.inJustDecodeBounds = true;
									BitmapFactory.decodeStream(is, null, options);
									int bitmap_width = Math.max(options.outWidth, options.outHeight);
									int bitmap_height = Math.min(options.outWidth, options.outHeight);
									if( MyDebug.LOG ) {
										Log.d(TAG, "	bitmap_width: " + bitmap_width);
										Log.d(TAG, "	bitmap_height: " + bitmap_height);
									}
									int scale = Math.min(bitmap_width/parent_width, bitmap_height/parent_height);
									if( MyDebug.LOG )
										Log.d(TAG, "	scale: " + scale);

									is.close();

									if (uri != null)
										is = resolver.openInputStream(uri);
									else
										is = new FileInputStream(file_path);

									if (scale > 1) {
										options.inJustDecodeBounds = false;
										options.inSampleSize = scale;
										bitmap = BitmapFactory.decodeStream(is, null, options);
									} else {
										bitmap = BitmapFactory.decodeStream(is);
									}
									exif = new ExifInterface(is);
									is.close();
								}
							}
							catch (FileNotFoundException e) {}
							catch (IOException e) {}
							finally {
								if (is != null) {
									try {
										is.close();
									} catch (Throwable e) {}
								}
							}

							if (bitmap != null) {
								overlay_rotation = 0;

								if( exif != null ) {
									switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
										case ExifInterface.ORIENTATION_ROTATE_180:
											overlay_rotation = 180;
											break;
										case ExifInterface.ORIENTATION_ROTATE_90:
											overlay_rotation = 90;
											break;
										case ExifInterface.ORIENTATION_ROTATE_270:
											overlay_rotation = 270;
											break;
									}

									if( MyDebug.LOG )
										Log.d(TAG, "exif rotation: " + overlay_rotation);
								}
								
								int width = bitmap.getWidth();
								int height = bitmap.getHeight();
								
								overlay_is_portrait = (overlay_rotation == 0 || overlay_rotation == 180) ? width < height : width > height;
								if( MyDebug.LOG ) {
									Log.d(TAG, "	width: "+ width);
									Log.d(TAG, "	height: "+ height);
									Log.d(TAG, "	overlay_is_portrait: " + overlay_is_portrait);
								}
								
								if (system_ui_portrait) {
									if (!overlay_is_portrait)
										overlay_rotation += 90;
								} else {
									if (overlay_is_portrait)
										overlay_rotation += 270;
								}
								
								if (overlay_rotation >= 360)
									overlay_rotation -= 360;
									
								/* This shit works too slow
								if( rotation != 0 ) {
									if( MyDebug.LOG )
										Log.d(TAG, "	need to rotate bitmap due to exif orientation tag");
									Matrix m = new Matrix();
									m.setRotate(rotation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
									Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, m, true);
									if( rotated_bitmap != bitmap ) {
										bitmap.recycle();
										bitmap = rotated_bitmap;
									}
								}*/
							}
						}

						return bitmap;
					}

					protected void onPostExecute(Bitmap bitmap) {
						if( MyDebug.LOG )
							Log.d(TAG, "onPostExecute");
						if (bitmap != null) {
							overlay.setImageBitmap(null);
							FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)overlay.getLayoutParams();
							if (overlay_rotation == 90 || overlay_rotation == 270) {
								View parent = (View)(overlay.getParent());
								int diff = (parent.getMeasuredWidth()-parent.getMeasuredHeight())/2;
								lp.setMargins(diff, -diff, diff, -diff);
							} else {
								lp.setMargins(0,0,0,0);
							}
							overlay.setLayoutParams(lp);
							setOverlayImageRotation(overlay);
							overlay.setImageBitmap(bitmap);
							overlay.setVisibility(View.VISIBLE);
						} else {
							overlay.setVisibility(View.GONE);
							overlay.setImageBitmap(null);
						}
					}
				}.execute();
				return;
			}
		}
	
		overlay.setVisibility(View.GONE);
		overlay.setImageBitmap(null);
	}
	
	public void setOverlayImageRotation(ImageView overlay) {
		int rotation = overlay_rotation;
		if (overlay_is_portrait) {
			if (ui_rotation_relative == 90)
				rotation += 180;
		} else {
			if (ui_placement_right) {
				if (ui_rotation_relative == 180)
					rotation += 180;
			} else {
				if (ui_rotation_relative != 0)
					rotation += 180;
			}
		}
		if (rotation >= 360)
			rotation -= 360;
		overlay.setRotation(rotation);
	}
}
