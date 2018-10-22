package com.caddish_hedgehog.hedgecam2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TakeVideo extends Activity {
	private static final String TAG = "HedgeCam/TakeVideo";
	public static final String TAKE_VIDEO = "com.caddish_hedgehog.hedgecam2.TAKE_VIDEO";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(TAKE_VIDEO, true);
		this.startActivity(intent);
		if( MyDebug.LOG )
			Log.d(TAG, "finish");
		this.finish();
	}

	protected void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume");
		super.onResume();
	}
}
