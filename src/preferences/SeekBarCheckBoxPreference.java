package com.caddish_hedgehog.hedgecam2.preferences;

import com.caddish_hedgehog.hedgecam2.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarCheckBoxPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener, OnClickListener
{
	private static final String androidns="http://schemas.android.com/apk/res/android";

	private Context context;

	private SeekBar seekbar;
	private TextView valueText;
	private CheckBox checkBox;

	private String suffix;
	private String summary;
	private String checkBoxTitle;
	private String checkBoxValue = null;
	private String defaultValue;
	private int minValue;
	private int maxValue;
	private int value;
	private int step;
	private int defaultSeekBarValue;

	private boolean valueIsCheckBox;

	public SeekBarCheckBoxPreference(Context context, AttributeSet attrs) {
		super(context,attrs); 
		this.context = context;

		int id = attrs.getAttributeResourceValue(androidns, "text", 0);
		if (id == 0) suffix = attrs.getAttributeValue(androidns, "text");
		else suffix = context.getString(id);

		id = attrs.getAttributeResourceValue(androidns, "summary", 0);
		if (id == 0) summary = attrs.getAttributeValue(androidns, "summary");
		else summary = context.getString(id);


		maxValue = attrs.getAttributeIntValue(androidns, "max", 100);
		// I don't need this long and ugly shit, i just need to read an attribute. Fuck you, google...
		TypedArray idontneedthisshit = context.getTheme().obtainStyledAttributes(attrs, R.styleable.idontneedthisshit, 0, 0);
		
		checkBoxTitle = idontneedthisshit.getString(R.styleable.idontneedthisshit_checkBoxTitle);

		minValue = idontneedthisshit.getInteger(R.styleable.idontneedthisshit_min, 0);
		step = idontneedthisshit.getInteger(R.styleable.idontneedthisshit_step, 1);

		checkBoxValue = idontneedthisshit.getString(R.styleable.idontneedthisshit_checkBoxValue);
		defaultValue = attrs.getAttributeValue(androidns, "defaultValue");
		if (defaultValue.equals(checkBoxValue)) {
			defaultSeekBarValue = minValue;
		} else {
			try {defaultSeekBarValue = Integer.parseInt(defaultValue);}
			catch(NumberFormatException e) {
				defaultSeekBarValue = minValue;
			}
		}
		
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
		
		CheckBox checkBox = new CheckBox(context);
		checkBox.setText(checkBoxTitle != null ? checkBoxTitle : "");
		checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				valueIsCheckBox = isChecked;
				checkBoxChecked(isChecked);
			}
		});
		layout.addView(checkBox);

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
			processValue(getPersistedString(defaultValue));
		}

		checkBox.setChecked(valueIsCheckBox);
		
		seekbar.setMax((maxValue-minValue)/step);
		int progress = (value-minValue)/step;
		if (progress == 0)
			this.onProgressChanged(seekbar, 0, false);
		else
			seekbar.setProgress(progress);

		return layout;
	}

	@Override 
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		seekbar.setMax((maxValue-minValue)/step);
		int progress = (value-minValue)/step;
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
				processValue(getPersistedString((String)defaultValue));
			}
		} else 
			value = defaultSeekBarValue;
	}
	
	@Override
	public CharSequence getSummary() {
		if (valueIsCheckBox) {
			return summary.replace("%s", checkBoxTitle);
		} else {
			String v = getPersistedString(Integer.toString(defaultSeekBarValue));
			return summary.replace("%s", suffix == null ? v : v + " " + suffix);
		}
	}

	@Override
	public void onProgressChanged(SeekBar s, int value, boolean fromTouch)
	{
		String v = String.valueOf(value*step+minValue);
		valueText.setText(suffix == null ? v : v + " " + suffix);
	}

	@Override
	public void onStartTrackingTouch(SeekBar s) {}
	@Override
	public void onStopTrackingTouch(SeekBar s) {}

	public void setMin(int v) { minValue = v; }
	public int getMin() { return minValue; }

	public void setMax(int v) { maxValue = v; }
	public int getMax() { return maxValue; }

	public void setValue(String v) { 
		if (seekbar == null)
			processValue(v);
	}
	public String getValue() { return valueIsCheckBox ? checkBoxValue : Integer.toString(value); }

	@Override
	public void showDialog(Bundle state) {

		super.showDialog(state);

		Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
		positiveButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (shouldPersist()) {
			if (valueIsCheckBox) {
				persistString(checkBoxValue);
				callChangeListener(defaultSeekBarValue);

				setSummary(summary.replace("%s", checkBoxTitle));
			} else {
				value = seekbar.getProgress()*step+minValue;
				persistString(Integer.toString(value));
				callChangeListener(Integer.valueOf(seekbar.getProgress()));

				String v = String.valueOf(value);
				setSummary(summary.replace("%s", suffix == null ? v : v + " " + suffix));
			}
		}

		((AlertDialog) getDialog()).dismiss();
	}
	
	private void processValue(String v) {
		if (v.equals(checkBoxValue)) {
			value = defaultSeekBarValue;
			valueIsCheckBox = true;
		} else {
			valueIsCheckBox = false;
			try {value = Integer.parseInt(v);}
			catch(NumberFormatException e) {
				value = defaultSeekBarValue;
			}
		}
	}
	
	private void checkBoxChecked(boolean value) {
		if (seekbar != null)
			seekbar.setEnabled(!value);
		if (valueText != null)
			valueText.setAlpha(value ? 0.1f : 1.0f);
	}
}