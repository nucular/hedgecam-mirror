package com.caddish_hedgehog.hedgecam2.UI;

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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class SeekBarArrayPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener, OnClickListener {
	private static final String TAG = "HedgeCam/SeekBarArrayPreference";

	private static final String androidns="http://schemas.android.com/apk/res/android";

	private Context context;

	private SeekBar seekbar;
	private TextView valueText;
	private CheckBox checkBox;

	private String summary;
	private String checkBoxValue = null;
	private String defValue;
	private String[] arrayEntries;
	private String[] arrayValues;
	private List<String> entries;
	private List<String> values;
	private String value;
	private String defaultSeekBarValue;
	private boolean reverse = false;

	public SeekBarArrayPreference(Context context, AttributeSet attrs) {
		super(context,attrs); 
		this.context = context;

		int id = attrs.getAttributeResourceValue(androidns, "summary", 0);
		if (id == 0) summary = attrs.getAttributeValue(androidns, "summary");
		else summary = context.getString(id);

		// I don't need this long and ugly shit, i just need to read an attribute. Fuck you, google...
		TypedArray idontneedthisshit = context.getTheme().obtainStyledAttributes(attrs, R.styleable.idontneedthisshit, 0, 0);

		checkBoxValue = idontneedthisshit.getString(R.styleable.idontneedthisshit_checkBoxValue);

		setEntries(attrs.getAttributeResourceValue(androidns, "entries", 0));
		setEntryValues(attrs.getAttributeResourceValue(androidns, "entryValues", 0));

		defValue = attrs.getAttributeValue(androidns, "defaultValue");
		defaultSeekBarValue = idontneedthisshit.getString(R.styleable.idontneedthisshit_defaultSeekBarValue);
		if (defaultSeekBarValue == null) {
			defaultSeekBarValue = defValue;
		}
		reverse = idontneedthisshit.getBoolean(R.styleable.idontneedthisshit_reverse, false);
		
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
		
		CheckBox checkBox = null;
		if (checkBoxValue != null) {
			int index = findIndexOfValue(checkBoxValue);
			if (index >= 0) {
				checkBox = new CheckBox(context);
				checkBox.setText(index < arrayEntries.length ? arrayEntries[index] : "");
				checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						value = isChecked ? checkBoxValue : values.get(seekbar.getProgress());
						checkBoxChecked(isChecked);
					}
				});
				layout.addView(checkBox);
			} else
				checkBoxValue = null;
		}

		valueText = new TextView(context);
		valueText.setGravity(Gravity.CENTER_HORIZONTAL);
		
		params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(valueText, params);

		seekbar = new SeekBar(context);
		seekbar.setOnSeekBarChangeListener(this);
		padding = resources.getDimensionPixelSize(R.dimen.seekbar_padding_large);
		seekbar.setPadding(seekbar.getPaddingLeft(), padding, seekbar.getPaddingRight(), padding);
		layout.addView(seekbar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		if (shouldPersist()) {
			value = getPersistedString(defValue);
		}

		if (checkBox != null)
			checkBox.setChecked(checkBoxValue.equals(value));
		
		entries = new ArrayList<String>();
		values = new ArrayList<String>();
		int text_size = 3;
		for (int i = 0; i < arrayValues.length; i++) {
			if (checkBox == null || !arrayValues[i].equals(checkBoxValue)) {
				String entrie = i < arrayEntries.length ? arrayEntries[i] : "";
				entries.add(entrie);
				if (text_size > 1 && entrie.length() > 20)
					text_size = 1;
				if (text_size > 2 && entrie.length() > 12)
					text_size = 2;
				
				values.add(arrayValues[i]);
			}
		}

		int text_size_id = R.dimen.pref_seekbar_text_large;
		switch (text_size) {
			case 2:
				text_size_id = R.dimen.pref_seekbar_text_medium;
				break;
			case 1:
				text_size_id = R.dimen.pref_seekbar_text_small;
				break;
		}
		valueText.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(text_size_id));
		
		seekbar.setMax(values.size()-1);

		setSeekBarProgress();

		return layout;
	}

	@Override 
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		setSeekBarProgress();
	}
	
	private void setSeekBarProgress() {
		int progress = values.indexOf(value);
		if (progress < 0) {
			progress = values.indexOf(defaultSeekBarValue);
			if (progress < 0) {
				progress = 0;
			}
		}

		if (reverse)
			progress = values.size()-1-progress;

		if (progress == 0)
			this.onProgressChanged(seekbar, 0, false);
		else
			seekbar.setProgress(progress);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue) {
		super.onSetInitialValue(restore, defaultValue);
		if (restore) 
			if (shouldPersist()) {
				value = getPersistedString(defValue);
			}
		else 
			value = defValue;
	}
	
	@Override
	public CharSequence getSummary() {
		String entry = "";
		int index = findIndexOfValue(getPersistedString(defValue));
		if (index >= 0 && index < arrayEntries.length)
			entry = arrayEntries[index];
		
		return summary.replace("%s", entry);
	}

	@Override
	public void onProgressChanged(SeekBar s, int value, boolean fromTouch) {
		if (reverse)
			value = values.size()-1-value;

		if (fromTouch)
			this.value = values.get(value);
		valueText.setText(value < entries.size() ? entries.get(value) : "?");
	}

	@Override
	public void onStartTrackingTouch(SeekBar s) {}
	@Override
	public void onStopTrackingTouch(SeekBar s) {}

	public int findIndexOfValue(String value) {
		for (int i = 0; i < arrayValues.length; i++) {
			if (arrayValues[i].equals(value))
				return i;
		}
		
		return -1;
	}

	public String getEntry() {
		int index = findIndexOfValue(value);
		if (index >= 0 && index < arrayEntries.length)
			return arrayEntries[index];

		return "";
	}

	public String[] getEntries() {
		return arrayEntries;
	}

	public void setEntries(String[] entries) {
		arrayEntries = entries;
	}

	public void setEntries(int entriesResId) {
		if (entriesResId > 0)
			arrayEntries = context.getResources().getStringArray(entriesResId);
		else
			arrayEntries = new String[] {};
	}


	public String[] getEntryValues() {
		return arrayValues;
	}

	public void setEntryValues(String[] entryValues) {
		arrayValues = entryValues;
	}

	public void setEntryValues(int entryValuesResId) {
		if (entryValuesResId > 0)
			arrayValues = context.getResources().getStringArray(entryValuesResId);
		else
			arrayValues = new String[] {};
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void setValueIndex(int index) {
		if (index < arrayValues.length)
			value = arrayValues[index];
	}

	public void setCheckBoxValue(String value) {
		checkBoxValue = value;
	}

	public String getCheckBoxValue() {
		return checkBoxValue;
	}

	@Override
	public void showDialog(Bundle state) {

		super.showDialog(state);

		Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
		positiveButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (shouldPersist()) {
			persistString(value);
			callChangeListener(value);
			setSummary(summary.replace("%s", getEntry()));
		}

		((AlertDialog) getDialog()).dismiss();
	}
	
	private void checkBoxChecked(boolean value) {
		if (seekbar != null)
			seekbar.setEnabled(!value);
		if (valueText != null)
			valueText.setAlpha(value ? 0.1f : 1.0f);
	}
}