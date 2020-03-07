package com.caddish_hedgehog.hedgecam2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class Utils {
	private static final String TAG = "HedgeCam/Utils";
	private static MainActivity main_activity;
	private static Resources resources;
	private static String package_name;
	private static float scale;

	public static final ToastBoxer flash_toast = new ToastBoxer();
	public static final ToastBoxer focus_toast = new ToastBoxer();
	public static final ToastBoxer take_photo_toast = new ToastBoxer();
	public static final ToastBoxer pause_video_toast = new ToastBoxer();
	public static final ToastBoxer seekbar_toast = new ToastBoxer();
	private static int toast_padding;
	private static int toast_radius;
	private static float toast_font_size;
	private static int toast_color;
	private static Toast last_toast;

	public static void init(MainActivity activity) {
		main_activity = activity;
		resources = main_activity.getResources();
		package_name = main_activity.getApplicationContext().getPackageName();
		scale = resources.getDisplayMetrics().density;
		updateToastConfig();
	}
	
	public static MainActivity getMainActivity() {
		return main_activity;
	}

	public static Resources getResources() {
		return resources;
	}

	public static String getPackageName() {
		return package_name;
	}

	public static float getScale() {
		return scale;
	}

	public static String findEntryForValue(String value, int entries_id, int values_id) {
		String [] entries = getResources().getStringArray(entries_id);
		String [] values = getResources().getStringArray(values_id);
		for(int i=0;i<values.length;i++) {
			if( value.equals(values[i]) ) {
				return entries[i];
			}
		}
		return value;
	}

	public static String getStringResourceByName(String prefix, String string) {
		int resId = resources.getIdentifier(prefix.concat(string.replace("-", "_")), "string", getPackageName());
		if (resId == 0) {
			return string;
		} else {
			return resources.getString(resId);
		}
	}

	public static void showToast(final ToastBoxer clear_toast, final int message_id) {
		showToast(clear_toast, getResources().getString(message_id));
	}

	public static void showToast(final ToastBoxer clear_toast, final String message) {
		showToast(clear_toast, message, 32);
	}

	public static void showToast(final String message) {
		showToast(seekbar_toast, message, 32);
	}

	public static void showToast(final int message_id) {
		showToast(seekbar_toast, resources.getString(message_id), 32);
	}

	private static void showToast(final ToastBoxer clear_toast, final String message, final int offset_y_dp) {
		if (main_activity.cameraInBackground()) {
			Toast.makeText(main_activity, message, message.length() > 50 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
			return;
		}
		
		if( !Prefs.getBoolean(Prefs.SHOW_TOASTS, true) ) {
			return;
		}
		
		class RotatedTextView extends View {
			private String [] lines;
			private final Paint paint = new Paint();
			private final Rect bounds = new Rect();
			private final Rect sub_bounds = new Rect();
			private final RectF rect = new RectF();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.lines = text.split("\n");
			}

			void setText(String text) {
				this.lines = text.split("\n");
			}

			@Override
			protected void onDraw(Canvas canvas) {
				paint.setTextSize(toast_font_size);
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				paint.setAntiAlias(true);
				//paint.getTextBounds(text, 0, text.length(), bounds);
				boolean first_line = true;
				for(String line : lines) {
					paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "line: " + line + " sub_bounds: " + sub_bounds);
					}*/
					if( first_line ) {
						bounds.set(sub_bounds);
						first_line = false;
					}
					else {
						bounds.top = Math.min(sub_bounds.top, bounds.top);
						bounds.bottom = Math.max(sub_bounds.bottom, bounds.bottom);
						bounds.left = Math.min(sub_bounds.left, bounds.left);
						bounds.right = Math.max(sub_bounds.right, bounds.right);
					}
				}
				// above we've worked out the maximum bounds of each line - this is useful for left/right, but for the top/bottom
				// we would rather use a consistent height no matter what the text is (otherwise we have the problem of varying
				// gap between lines, depending on what the characters are).
				final String reference_text = "Ap";
				paint.getTextBounds(reference_text, 0, reference_text.length(), sub_bounds);
				bounds.top = sub_bounds.top;
				bounds.bottom = sub_bounds.bottom;
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "bounds: " + bounds);
				}*/
				int height = bounds.bottom - bounds.top; // height of each line
				bounds.bottom += ((lines.length-1) * height)/2;
				bounds.top -= ((lines.length-1) * height)/2;
				final int padding = (int) (14 * scale + 0.5f); // padding for the shaded rectangle; convert dps to pixels
				final int offset_y = (int) (offset_y_dp * scale + 0.5f); // convert dps to pixels
				canvas.save();
				canvas.rotate(main_activity.getMainUI().getUIRotation(), canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

				rect.left = canvas.getWidth()/2 - bounds.width()/2 + bounds.left - padding;
				rect.top = canvas.getHeight()/2 + bounds.top - padding + offset_y;
				rect.right = canvas.getWidth()/2 - bounds.width()/2 + bounds.right + padding;
				rect.bottom = canvas.getHeight()/2 + bounds.bottom + padding + offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(toast_color);
				final float radius = (toast_radius); // convert dps to pixels
				canvas.drawRoundRect(rect, radius, radius, paint);

				paint.setColor(Color.WHITE);
				int ypos = canvas.getHeight()/2 + offset_y - ((lines.length-1) * height)/2;
				for(String line : lines) {
					canvas.drawText(line, canvas.getWidth()/2 - bounds.width()/2, ypos, paint);
					ypos += height;
				}
				canvas.restore();
			}
		}

		if( MyDebug.LOG )
			Log.d(TAG, "showToast: " + message);
		// We get a crash on emulator at least if Toast constructor isn't run on main thread (e.g., the toast for taking a photo when on timer).
		// Also see http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				/*if( clear_toast != null && clear_toast.toast != null )
					clear_toast.toast.cancel();

				Toast toast = new Toast(activity);
				if( clear_toast != null )
					clear_toast.toast = toast;*/
				// This method is better, as otherwise a previous toast (with different or no clear_toast) never seems to clear if we repeatedly issue new toasts - this doesn't happen if we reuse existing toasts if possible
				// However should only do this if the previous toast was the most recent toast (to avoid messing up ordering)
				Toast toast;
				if( clear_toast != null && clear_toast.toast != null && clear_toast.toast == last_toast ) {
					if( MyDebug.LOG )
						Log.d(TAG, "reuse last toast: " + last_toast);
					toast = clear_toast.toast;
					// for performance, important to reuse the same view, instead of creating a new one (otherwise we get jerky preview update e.g. for changing manual focus slider)
					RotatedTextView view = (RotatedTextView)toast.getView();
					view.setText(message);
					view.invalidate(); // make sure the toast is redrawn
					toast.setView(view);
				}
				else {
					if( clear_toast != null && clear_toast.toast != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "cancel last toast: " + clear_toast.toast);
						clear_toast.toast.cancel();
					}
					toast = new Toast(main_activity);
					if( MyDebug.LOG )
						Log.d(TAG, "created new toast: " + toast);
					if( clear_toast != null )
						clear_toast.toast = toast;
					View text = new RotatedTextView(message, main_activity);
					toast.setView(text);
				}
				toast.setDuration(Toast.LENGTH_SHORT);
				toast.show();
				last_toast = toast;
			}
		});
	}

	public static void updateToastConfig() {
		toast_padding = resources.getDimensionPixelSize(R.dimen.toast_padding);
		toast_radius = resources.getDimensionPixelSize(R.dimen.toast_radius);
		switch( Prefs.getString(Prefs.POPUP_FONT_SIZE, "normal") ) {
			case "small":
				toast_font_size = resources.getDimension(R.dimen.popup_text_small_default);
				break;
			case "large":
				toast_font_size = resources.getDimension(R.dimen.popup_text_large_default);
				break;
			case "xlarge":
				toast_font_size = resources.getDimension(R.dimen.popup_text_xlarge_default);
				break;
			default:
				toast_font_size = resources.getDimension(R.dimen.popup_text_normal_default);
				break;
		}
		((TextView)main_activity.findViewById(R.id.seekbar_hint)).setTextSize(TypedValue.COMPLEX_UNIT_PX, toast_font_size);
		
		toast_color = resources.getColor(R.color.toast_color);
	}
}