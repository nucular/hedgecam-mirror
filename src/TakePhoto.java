package com.caddish_hedgehog.hedgecam2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Entry Activity for the "take photo" widget (see MyWidgetProviderTakePhoto).
 *  This redirects to MainActivity, but uses an intent extra/bundle to pass the
 *  "take photo" request.
 */
public class TakePhoto extends Activity {
	private static final String TAG = "HedgeCam/TakePhoto";
	public static final String TAKE_PHOTO = "com.caddish_hedgehog.hedgecam2.TAKE_PHOTO";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(TAKE_PHOTO, true);
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
