package com.caddish_hedgehog.hedgecam2.Preview.CameraSurface;

import com.caddish_hedgehog.hedgecam2.MyDebug;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
import com.caddish_hedgehog.hedgecam2.CameraController.CameraControllerException;
import com.caddish_hedgehog.hedgecam2.Preview.Preview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/** Provides support for the surface used for the preview, using a SurfaceView.
 */
public class MySurfaceView extends SurfaceView implements CameraSurface {
	private static final String TAG = "HedgeCam/MySurfaceView";

	private final Preview preview;
	private final int [] measure_spec = new int[2];
	private final Handler handler = new Handler();
	private final Runnable tick;

	@SuppressWarnings("deprecation")
	public
	MySurfaceView(Context context, final Preview preview) {
		super(context);
		if( MyDebug.LOG ) {
			Log.d(TAG, "new MySurfaceView");
		}
		this.preview = preview;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		getHolder().addCallback(preview);
		// deprecated setting, but required on Android versions prior to 3.0
		getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

		if (!preview.isUsingCanvasView()) {
			tick = new Runnable() {
				public void run() {
					/*if( MyDebug.LOG )
						Log.d(TAG, "invalidate()");*/
					preview.test_ticker_called = true;
					invalidate();
					handler.postDelayed(this, preview.getTickInterval());
				}
			};
		} else tick = null;
	}
	
	@Override
	public View getView() {
		return this;
	}
	
	@Override
	public void setPreviewDisplay(CameraController camera_controller) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewDisplay");
		try {
			camera_controller.setPreviewDisplay(this.getHolder());
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to set preview display: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void setVideoRecorder(MediaRecorder video_recorder) {
		video_recorder.setPreviewDisplay(this.getHolder().getSurface());
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return preview.touchEvent(event);
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (tick != null)
			preview.draw(canvas);
	}

	@Override
	protected void onMeasure(int widthSpec, int heightSpec) {
		preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
		super.onMeasure(measure_spec[0], measure_spec[1]);
	}

	@Override
	public void setTransform(Matrix matrix) {
		if( MyDebug.LOG )
			Log.d(TAG, "setting transforms not supported for MySurfaceView");
		throw new RuntimeException();
	}

	@Override
	public void onPause() {
		if( MyDebug.LOG )
			Log.d(TAG, "onPause()");
		if (tick != null)
			handler.removeCallbacks(tick);
	}

	@Override
	public void onResume() {
		if( MyDebug.LOG )
			Log.d(TAG, "onResume()");
		if (tick != null)
			tick.run();
	}
}
