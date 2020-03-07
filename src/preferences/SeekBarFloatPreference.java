package com.caddish_hedgehog.hedgecam2.preferences;

import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.math.BigDecimal;

public class SeekBarFloatPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener, OnClickListener
{
	private static final String TAG = "HedgeCam/SeekBarFloatPreference";
	private static final String androidns="http://schemas.android.com/apk/res/android";

	private SeekBar seekbar;
	private TextView valueText;
	private Context context;

	private String suffix;
	private String summary;
	private float defValue;
	private float minValue;
	private float maxValue;
	private float step;
	private float value;

	public SeekBarFloatPreference(Context context, AttributeSet attrs) {
		super(context,attrs); 
		this.context = context;

		int id = attrs.getAttributeResourceValue(androidns, "text", 0);
		if (id == 0) suffix = attrs.getAttributeValue(androidns, "text");
		else suffix = context.getString(id);

		id = attrs.getAttributeResourceValue(androidns, "summary", 0);
		if (id == 0) summary = attrs.getAttributeValue(androidns, "summary");
		else summary = context.getString(id);

		defValue = attrs.getAttributeFloatValue(androidns, "defaultValue", 0.0f);
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

		valueText = new TextView(context);
		valueText.setGravity(Gravity.CENTER_HORIZONTAL);
		valueText.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.pref_seekbar_text_large));
		
		params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(valueText, params);

		seekbar = new SeekBar(context);
		seekbar.setOnSeekBarChangeListener(this);
		padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_large);
		seekbar.setPadding(seekbar.getPaddingLeft(), padding, seekbar.getPaddingRight(), padding);
		layout.addView(seekbar, params);

		if (shouldPersist()) {
			try {value = Float.parseFloat(getPersistedString(Float.toString(defValue)));}
			catch(NumberFormatException e) {value = defValue;}
		}

		seekbar.setMax((int)((maxValue-minValue)/step+0.5f));
		int progress = (int)((value-minValue)/step+0.5f);
		if (progress == 0)
			this.onProgressChanged(seekbar, 0, false);
		else
			seekbar.setProgress(progress);
		
		return layout;
	}

	@Override 
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		seekbar.setMax((int)((maxValue-minValue)/step+0.5f));
		int progress = (int)((value-minValue)/step+0.5f);
		if (progress == 0)
			this.onProgressChanged(seekbar, 0, false);
		else
			seekbar.setProgress(progress);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue)  
	{
		super.onSetInitialValue(restore, defaultValue);
		if (restore) {
			if (shouldPersist()) {
				try {value = Float.parseFloat(getPersistedString(Float.toString(defValue)));}
				catch(NumberFormatException e) {value = defValue;}
			}
		} else 
			value = defValue;
	}

	@Override
	public CharSequence getSummary() {
		String v = getPersistedString(Float.toString(defValue));
		return summary.replace("%s", suffix == null ? v : v + " " + suffix);
	}

	@Override
	public void onProgressChanged(SeekBar s, int value, boolean fromTouch)
	{
		String v = getResultString(value);
		valueText.setText(suffix == null ? v : v + " " + suffix);
	}

	@Override
	public void onStartTrackingTouch(SeekBar s) {}
	@Override
	public void onStopTrackingTouch(SeekBar s) {}

	public void setMin(float v) { minValue = v; }
	public float getMin() { return minValue; }

	public void setMax(float v) { maxValue = v; }
	public float getMax() { return maxValue; }

	public void setValue(float v) { 
		value = v;
		if (seekbar != null){
			int progress = (int)((value-minValue)/step+0.5f);
			if (progress == 0)
				this.onProgressChanged(seekbar, 0, false);
			else
				seekbar.setProgress(progress);
		}
	}
	public float getValue() { return value; }

	@Override
	public void showDialog(Bundle state) {

		super.showDialog(state);

		Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
		positiveButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (shouldPersist()) {
			String v = getResultString(seekbar.getProgress());
			persistString(v);
			callChangeListener(Integer.valueOf(seekbar.getProgress()));

			setSummary(summary.replace("%s", suffix == null ? v : v + " " + suffix));
		}

		((AlertDialog) getDialog()).dismiss();
	}
	
	private String getResultString(int seekBarValue) {
		// Don't ask me, why. It's fuckin magic... :D
		return Float.toString(new BigDecimal((float)seekBarValue*step+minValue).setScale(4, BigDecimal.ROUND_HALF_UP).floatValue());
	}
}