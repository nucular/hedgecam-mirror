package com.caddish_hedgehog.hedgecam2.preferences;

import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public class SeekBarColorsPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener, OnClickListener
{
	private static final String TAG = "HedgeCam/SeekBarColorsPreference";
	private static final String androidns="http://schemas.android.com/apk/res/android";

	private SeekBar[] seekbars;
	private TextView[] valueTexts;
	private Context context;

	private String suffix;
	private String summary;
	private float defValue;
	private String defValueString;
	private float minValue;
	private float maxValue;
	private float step;
	private float[] values;
	
	private static final int[] seekbarColors = {
		R.color.main_red,
		R.color.main_green,
		R.color.main_blue
	};
	
	private int i;

	public SeekBarColorsPreference(Context context, AttributeSet attrs) {
		super(context,attrs); 
		this.context = context;

		seekbars = new SeekBar[3];
		valueTexts = new TextView[3];
		values = new float[3];

		int id = attrs.getAttributeResourceValue(androidns, "text", 0);
		if (id == 0) suffix = attrs.getAttributeValue(androidns, "text");
		else suffix = context.getString(id);

		id = attrs.getAttributeResourceValue(androidns, "summary", 0);
		if (id == 0) summary = attrs.getAttributeValue(androidns, "summary");
		else summary = context.getString(id);

		defValue = attrs.getAttributeFloatValue(androidns, "defaultValue", 0.0f);
		StringBuilder sb = new StringBuilder();
		sb.append(defValue);
		sb.append("|");
		sb.append(defValue);
		sb.append("|");
		sb.append(defValue);
		defValueString = sb.toString();

		// I don't need this long and ugly shit, i just need to read an attribute. Fuck you, google...
		TypedArray idontneedthisshit = context.getTheme().obtainStyledAttributes(attrs, R.styleable.idontneedthisshit, 0, 0);
		minValue = idontneedthisshit.getFloat(R.styleable.idontneedthisshit_fmin, 0.0f);
		maxValue = idontneedthisshit.getFloat(R.styleable.idontneedthisshit_fmax, 100.0f);
		step = idontneedthisshit.getFloat(R.styleable.idontneedthisshit_fstep, 1.0f);
		idontneedthisshit.recycle();
	}

	@Override 
	protected View onCreateDialogView() {
		Resources resources = getContext().getResources();

		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.VERTICAL);
		int padding = resources.getDimensionPixelSize(R.dimen.pref_seekbar_padding);
		layout.setPadding(padding,padding,padding,padding);
		params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

		LinearLayout valueTextsLayout = new LinearLayout(context);
		valueTextsLayout.setOrientation(LinearLayout.HORIZONTAL);
		layout.addView(valueTextsLayout, params);

		String[] stringValues = null;
		if (shouldPersist()) {
			stringValues = getStringValues();
		}

		for (i = 0; i < 3; i++) {
			int color = resources.getColor(seekbarColors[i]);

			valueTexts[i] = new TextView(context);
			valueTexts[i].setGravity(Gravity.CENTER_HORIZONTAL);
			valueTexts[i].setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.pref_seekbar_text_medium));
			valueTexts[i].setTextColor(color);
			valueTextsLayout.addView(valueTexts[i], new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

			seekbars[i] = new SeekBar(context);
			seekbars[i].setOnSeekBarChangeListener(this);
			padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_large);
			seekbars[i].setPadding(seekbars[i].getPaddingLeft(), padding, seekbars[i].getPaddingRight(), padding);
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
				seekbars[i].setThumbTintList(ColorStateList.valueOf(color));
			}
			layout.addView(seekbars[i], params);

			if (shouldPersist()) {
				if (stringValues == null) {
					values[i] = defValue;
				} else {
					try {values[i] = Float.parseFloat(stringValues[i]);}
					catch(NumberFormatException e) {values[i] = defValue;}
				}
			}

			setSeekbar(i);
		}
		
		return layout;
	}

	@Override 
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		
		for (i = 0; i < 3; i++) {
			setSeekbar(i);
		}
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue)  
	{
		super.onSetInitialValue(restore, defaultValue);
		if (restore) {
			if (shouldPersist()) {
				String[] stringValues = getStringValues();
				for (i = 0; i < 3; i++) {
					if (stringValues == null) {
						values[i] = defValue;
					} else {
						try {values[i] = Float.parseFloat(stringValues[i]);}
						catch(NumberFormatException e) {values[i] = defValue;}
					}
				}
			}
		} else {
			values[0] = defValue;
			values[1] = defValue;
			values[2] = defValue;
		}
	}

	@Override
	public CharSequence getSummary() {
		String v = getPersistedString(defValueString).replace("|", "; ");
		return summary.replace("%s", suffix == null ? v : v + " " + suffix);
	}

	@Override
	public void onProgressChanged(SeekBar s, int value, boolean fromTouch)
	{
		for (i = 0; i < 3; i++) {
			if (seekbars[i] == s) {
				String v = getResultString(value);
				valueTexts[i].setText(suffix == null ? v : v + " " + suffix);
				break;
			}
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar s) {}
	@Override
	public void onStopTrackingTouch(SeekBar s) {}

	public void setMin(float v) { minValue = v; }
	public float getMin() { return minValue; }

	public void setMax(float v) { maxValue = v; }
	public float getMax() { return maxValue; }

	public void setValue(float v[]) {
		if (v.length == 3) {
			values = v;
			for (i = 0; i < 3; i++) {
				if (seekbars[i] != null)
					setSeekbar(i);
			}
		}
	}

	public void setValue(String v) {
		String[] stringValues = v.split(Pattern.quote("|"));
		if (stringValues.length == 3) {
			for (i = 0; i < 3; i++) {
				try {values[i] = Float.parseFloat(stringValues[i]);}
				catch(NumberFormatException e) {values[i] = defValue;}
				
				if (seekbars[i] != null)
					setSeekbar(i);
			}
		}
	}
//	public float getValue() { return value; }

	@Override
	public void showDialog(Bundle state) {

		super.showDialog(state);

		Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
		positiveButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (shouldPersist()) {
			StringBuilder sb = new StringBuilder();
			sb.append(getResultString(seekbars[0].getProgress()));
			sb.append("|");
			sb.append(getResultString(seekbars[1].getProgress()));
			sb.append("|");
			sb.append(getResultString(seekbars[2].getProgress()));
			String v = sb.toString();
			persistString(v);
			callChangeListener(v);

			v = v.replace("|", "; ");
			setSummary(summary.replace("%s", suffix == null ? v : v + " " + suffix));
		}

		((AlertDialog) getDialog()).dismiss();
	}
	
	private String[] getStringValues() {
		String[] stringValues = getPersistedString(defValueString).split(Pattern.quote("|"));
		if (stringValues.length != 3)
			stringValues = null;

		return stringValues;
	}
	
	private void setSeekbar(int i) {
		seekbars[i].setMax((int)((maxValue-minValue)/step+0.5f));
		int progress = (int)((values[i]-minValue)/step+0.5f);
		if (progress == 0)
			this.onProgressChanged(seekbars[i], 0, false);
		else
			seekbars[i].setProgress(progress);
	}
	
	private String getResultString(int seekBarValue) {
		// Don't ask me, why. It's fuckin magic... :D
		return Float.toString(new BigDecimal((float)seekBarValue*step+minValue).setScale(4, BigDecimal.ROUND_HALF_UP).floatValue());
	}
}