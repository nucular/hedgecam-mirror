package com.caddish_hedgehog.hedgecam2.UI;

import com.caddish_hedgehog.hedgecam2.R;

import android.widget.TextView;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;

public class IconView extends TextView {
	public static final String ARROW_LEFT = "<";
	public static final String ARROW_RIGHT = ">";
	public static final String FLASH_AUTO = "F";
	public static final String FLASH_OFF = "N";
	public static final String FLASH_ON = "V";
	public static final String FLASH_TORCH = "T";
	public static final String FLASH_RED_EYE = "R";
	public static final String AUTO_LEVEL = "A";
	public static final String LOCATION = "L";
	public static final String LOCATION_UNKNOWN = "U";
	public static final String STAMP = "t";
	public static final String HDR = "H";
	public static final String EXPO_BRACKETING = "E";
	public static final String FOCUS_BRACKETING = "B";
	public static final String BURST = "b";
	public static final String DRO = "D";
	public static final String SDCARD = "M";
	public static final String SDCARD_FULL = "S";
	public static final String ISO = "I";
	public static final String FACE = "d";
	public static final String SELFIE = "e";
	public static final String FOCUS = "f";
	public static final String FOCUS_LANDSCAPE = "l";
	public static final String FOCUS_MACRO = "m";
	public static final String SHUTTER = "s";
	public static final String RAW = "r";
	public static final String NOISE_REDUCTION = "n";
	public static final String FAST_FORWARD = "»";
	public static final String FOLDER = "o";
	public static final String FILE = "p";
	public static final String UP = "u";
	
	public static Typeface mIconFont;
	
	private Context context;

	public IconView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	public IconView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public IconView(Context context) {
		super(context);
		init(context);
	}
	
	public static Typeface getTypeface(Context context) {
		if (mIconFont == null) {
			loadIconFont(context);
		}
		return mIconFont;
	}

	private void init(Context context) {
		this.context = context;

		if (mIconFont == null) {
			loadIconFont(context);
		}

		setGravity(Gravity.CENTER);
		setTextColor(Color.WHITE);
		setDrawShadow(true);
		setTypeface(mIconFont);
	}
	
	public void setDrawShadow(boolean draw) {
		if (draw) {
			setShadowLayer(context.getResources().getDimension(R.dimen.ctrl_button_shadow), 0, 0, context.getResources().getColor(R.color.ctrl_button_shadow));
			return;
		}
		setShadowLayer(0, 0, 0, 0);
	}
	
	private static void loadIconFont(Context context) {
		mIconFont = Typeface.createFromAsset(context.getAssets(), "icons.ttf");
	}
}
