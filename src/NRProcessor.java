package com.caddish_hedgehog.hedgecam2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Log;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;

public class NRProcessor {
	private static final String TAG = "HedgeCam/NRProcessor";
	
	private final Context context;

	private int width = 0;
	private int height = 0;

	private short bitmaps = 0;

	private RenderScript rs;
	private ScriptC_nr script;

	private Allocation alloc_base;
	private Allocation alloc_avg;
	
	boolean use_mtb = true;
	private int median_value = -1;

	private int mtb_width;
	private int mtb_height;
	private int mtb_x;
	private int mtb_y;

	private ScriptC_create_mtb createMTBScript;
	private Allocation alloc_base_mtb;
	private Allocation alloc_mtb;
	private ScriptC_align_mtb alignMTBScript;
	
	private int initial_step_size = 1;
	
	private Rect crop_rect;

	public NRProcessor(Context context, RenderScript rs, Bitmap bitmap, boolean align, boolean crop) {
		if( MyDebug.LOG )
			Log.d(TAG, "new NRProcessor");

		long time_s = System.currentTimeMillis();
		this.context = context;

		this.rs = rs;
		this.script = new ScriptC_nr(rs);

		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();

		this.alloc_base = Allocation.createFromBitmap(rs, bitmap);
		if( MyDebug.LOG )
			Log.d(TAG, "time after Allocation.createFromBitmap: " + (System.currentTimeMillis() - time_s));
		bitmaps = 1;
		
		if (align) {
			/*mtb_width = width/2;
			mtb_height = height/2;
			mtb_x = mtb_width/2;
			mtb_y = mtb_height/2;*/
			mtb_width = width/4;
			mtb_height = height/4;
			mtb_x = (width-mtb_width)/2;
			mtb_y = (height-mtb_height)/2;

			if( MyDebug.LOG ) {
				Log.d(TAG, "mtb_width = " + mtb_width);
				Log.d(TAG, "mtb_height = " + mtb_height);
				Log.d(TAG, "mtb_x = " + mtb_x);
				Log.d(TAG, "mtb_y = " + mtb_y);
			}

			HDRProcessor.LuminanceInfo luminanceInfo = HDRProcessor.computeMedianLuminance(bitmap, mtb_x, mtb_y, mtb_width, mtb_height);
			if( MyDebug.LOG )
				Log.d(TAG, "time after HDRProcessor.computeMedianLuminance: " + (System.currentTimeMillis() - time_s));
			
			if (!use_mtb || !luminanceInfo.noisy) {
				createMTBScript = new ScriptC_create_mtb(rs);

				// set parameters
				if( use_mtb )
					createMTBScript.set_median_value(luminanceInfo.median_value);
				createMTBScript.set_start_x(mtb_x);
				createMTBScript.set_start_y(mtb_y);

				Type.Builder builder = new Type.Builder(rs, Element.U8(rs));
				builder.setX(mtb_width);
				builder.setY(mtb_height);
				alloc_base_mtb = Allocation.createTyped(rs, builder.create());
				
				createMTB(alloc_base, alloc_base_mtb);

				alignMTBScript = new ScriptC_align_mtb(rs);
				alignMTBScript.set_bitmap0(alloc_base_mtb);

				int max_dim = Math.max(width, height); // n.b., use the full width and height here, not the mtb_width, height
				int max_ideal_size = max_dim / 150;
				while( initial_step_size < max_ideal_size ) {
					initial_step_size *= 2;
				}
				if( MyDebug.LOG ) {
					Log.d(TAG, "max_dim: " + max_dim);
					Log.d(TAG, "max_ideal_size: " + max_ideal_size);
					Log.d(TAG, "initial_step_size: " + initial_step_size);
				}
				
				if (crop) {
					crop_rect = new Rect(0, 0, 0, 0);
				}
			}
		}
	
		Type.Builder builder = new Type.Builder(rs, Element.U16_3(rs));
		builder.setX(width);
		builder.setY(height);
		this.alloc_avg = Allocation.createTyped(rs, builder.create());
		script.set_alloc_avg(alloc_avg);
		
		script.forEach_start(alloc_base);

		if( MyDebug.LOG )
			Log.d(TAG, "new NRProcessor time: " + (System.currentTimeMillis() - time_s));
	}
	
	public void addBitmap(Bitmap bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "addBitmap()");
		Allocation alloc = Allocation.createFromBitmap(rs, bitmap);
		addAllocation(alloc);
	}

	public void addAllocation(Allocation alloc) {
		if( MyDebug.LOG )
			Log.d(TAG, "addAllocation()");

		Type type = alloc.getType();
		final int bitmap_width = type.getX();
		final int bitmap_height = type.getY();

		if (bitmap_width != width || bitmap_height != height) {
			if( MyDebug.LOG )
				Log.d(TAG, "size mismatch, skip image");
			return;
		}

		long time_s = System.currentTimeMillis();

		bitmaps++;
		
		Point p = null;
		if (createMTBScript != null) {
			if (alloc_mtb == null) {
				Type.Builder builder = new Type.Builder(rs, Element.U8(rs));
				builder.setX(mtb_width);
				builder.setY(mtb_height);
				alloc_mtb = Allocation.createTyped(rs, builder.create());
			}
			
			createMTB(alloc, alloc_mtb);
			p = alignMTB(alloc_mtb);
		}
		
		if (p == null || (p.x == 0 && p.y == 0))
			script.forEach_add(alloc);
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "bitmap #" + bitmaps + " x: " + p.x + ", y: " + p.y);
			
			script.set_offset_x(p.x);
			script.set_offset_y(p.y);
			script.set_divider(bitmaps-1);
			script.forEach_add_aligned(alloc);
			
			if (crop_rect != null) {
				if (p.x > 0)
					crop_rect.right = Math.max(p.x, crop_rect.right);
				else if (p.x < 0)
					crop_rect.left = Math.max(-p.x, crop_rect.left);
				
				if (p.y > 0)
					crop_rect.bottom = Math.max(p.y, crop_rect.bottom);
				else if (p.y < 0)
					crop_rect.top = Math.max(-p.y, crop_rect.top);
			}
		}

		if( MyDebug.LOG )
			Log.d(TAG, "addBitmap() time: " + (System.currentTimeMillis() - time_s));
	}
	
	public Allocation finish(int adjust_levels, double histogram_level) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "finish()");
			Log.d(TAG, "bitmaps = " + bitmaps);
		}

		long time_s = System.currentTimeMillis();

		int min = 0;
		int max = 0;
		Allocation alloc_histogram = null;
		int histogram_width = 255*bitmaps+1;
		if (adjust_levels != Prefs.ADJUST_LEVELS_NONE) {
			if (histogram_level == 0) {
				Allocation alloc_max_min = Allocation.createSized(rs, Element.U32(rs), 2);
				script.bind_max_min(alloc_max_min);
				script.invoke_init_max_min();
				if (adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS) {
					script.forEach_calc_min_max(alloc_avg);
					if( MyDebug.LOG )
						Log.d(TAG, "time after script.forEach_calc_min_max: " + (System.currentTimeMillis() - time_s));
				} else if (adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS || adjust_levels == Prefs.ADJUST_LEVELS_BOOST) {
					script.forEach_calc_max(alloc_avg);
					if( MyDebug.LOG )
						Log.d(TAG, "time after script.forEach_calc_max: " + (System.currentTimeMillis() - time_s));
				}
				
				int [] max_min = new int[2];
				alloc_max_min.copyTo(max_min);
				max = max_min[0];
				min = max_min[1];
			} else {
				if( MyDebug.LOG ) {
					Log.d(TAG, "histogram_level = " + histogram_level);
					Log.d(TAG, "histogram_width = " + histogram_width);
				}

				script.set_histogram_width(histogram_width);

				alloc_histogram = Allocation.createSized(rs, Element.U32(rs), histogram_width);
				script.bind_histogram_array(alloc_histogram);
				script.invoke_init_histogram();
				script.forEach_histogram(alloc_avg);
				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_histogram: " + (System.currentTimeMillis() - time_s));

				int [] histogram = new int[histogram_width];
				alloc_histogram.copyTo(histogram);
				if( MyDebug.LOG )
					Log.d(TAG, "time after alloc_histogram.copyTo: " + (System.currentTimeMillis() - time_s));
					
				int histogram_height = 0;
				for (int i = 0; i < histogram_width; i++) {
					histogram_height = Math.max(histogram_height, histogram[i]);
				}
				if( MyDebug.LOG )
					Log.d(TAG, "histogram_height = " + histogram_height + ", time after calc: " + (System.currentTimeMillis() - time_s));

/*				if( MyDebug.LOG ){
					for (int i = 0; i < histogram_width; i++) {
						Log.d(TAG, "histogram[" + i + "] = " + histogram[i]);
					}
				}*/

				int level = (int)(((double)histogram_height)*histogram_level);
				
				for (max = histogram_width-1; max > 0; max--) {
					if (histogram[max] >= level)
						break;
				}
				
				if (adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS) {
					for (min = 0; min < histogram_width; min++) {
						if (histogram[min] >= level)
							break;
					}
				}

				if( MyDebug.LOG )
					Log.d(TAG, "time after calc histogram levels: " + (System.currentTimeMillis() - time_s));
			}
			if( MyDebug.LOG )
				Log.d(TAG, "min: " + min + ", max: " + max);
		}
		
		if (adjust_levels == Prefs.ADJUST_LEVELS_BOOST) {
			if (max == 0 || max == 255*bitmaps) {
				adjust_levels = Prefs.ADJUST_LEVELS_NONE;
			} else {
				double divider = (double)max;
				double gamma = 1.0d/Math.sqrt((255.0d*(double)bitmaps)/max);

				if( MyDebug.LOG )
					Log.d(TAG, "divider: " + divider + ", gamma: " + gamma);

				int [] histogram = new int[histogram_width];
				double level_out;
				for (double level = 0; level < histogram_width; level++) {
					level_out = Math.max(0.0d, Math.pow((level/divider), gamma)*255.0d);
					histogram[(int)level] = (int)Math.min(255.0d, level_out);
				}
				if (alloc_histogram == null) {
					alloc_histogram = Allocation.createSized(rs, Element.U32(rs), histogram_width);
					script.bind_histogram_array(alloc_histogram);
				}
				alloc_histogram.copyFrom(histogram);
				script.forEach_finish_histogram(alloc_base);

				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_finish_exposure: " + (System.currentTimeMillis() - time_s));
			}
		} else {
			if (adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS) {
				if (min == 0 || max < min ) {
					adjust_levels = Prefs.ADJUST_LEVELS_LIGHTS;
				} else {
					if( MyDebug.LOG )
						Log.d(TAG, "finish(): adjusting lights and shadows...");

					max -= min;

					script.set_fDivider((float)max / 255.0f);
					script.set_fMin((float)min);
					script.forEach_finish_ls(alloc_base);
					if( MyDebug.LOG )
						Log.d(TAG, "time after script.forEach_finish_ls: " + (System.currentTimeMillis() - time_s));
				}
			}

			if (adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS) {
				if (max == 0 || max == 255*bitmaps) {
					adjust_levels = Prefs.ADJUST_LEVELS_NONE;
				} else {
					if( MyDebug.LOG )
						Log.d(TAG, "finish(): adjusting lights...");

					script.set_fDivider((float)max / 255.0f);
					script.forEach_finish_l(alloc_base);
					if( MyDebug.LOG )
						Log.d(TAG, "time after script.forEach_finish_l: " + (System.currentTimeMillis() - time_s));
				}
			}
		}

		if (adjust_levels == Prefs.ADJUST_LEVELS_NONE) {
			if( MyDebug.LOG )
				Log.d(TAG, "finish(): no adjusting levels");

			script.set_divider(bitmaps);
			script.forEach_finish(alloc_base);
			if( MyDebug.LOG )
				Log.d(TAG, "time after script.forEach_finish: " + (System.currentTimeMillis() - time_s));
		}

		if( MyDebug.LOG )
			Log.d(TAG, "finish() time: " + (System.currentTimeMillis() - time_s));

		if (crop_rect != null && (crop_rect.left > 0 || crop_rect.top > 0 || crop_rect.right > 0 || crop_rect.bottom > 0)) {
			if( MyDebug.LOG )
				Log.d(TAG, "finish(): crop left: " + crop_rect.left + ", top: " + crop_rect.top + ", right: " + crop_rect.right + ", bottom: " + crop_rect.bottom);
			
			Type.Builder builder = new Type.Builder(rs, Element.RGBA_8888(rs));
			builder.setX(width-crop_rect.left-crop_rect.right);
			builder.setY(height-crop_rect.top-crop_rect.bottom);
			Allocation out = Allocation.createTyped(rs, builder.create());
			if( MyDebug.LOG )
				Log.d(TAG, "time after create out allocation: " + (System.currentTimeMillis() - time_s));

			script.set_alloc_avg(alloc_base);
			script.set_offset_x(crop_rect.left);
			script.set_offset_y(crop_rect.top);
			
			script.forEach_crop(out);
			if( MyDebug.LOG )
				Log.d(TAG, "time after script.forEach_crop: " + (System.currentTimeMillis() - time_s));

			return out;
		}
		
		return alloc_base;
	}
	
	private void createMTB(Allocation alloc, Allocation alloc_mtb) {
		if( MyDebug.LOG )
			Log.d(TAG, "createMTB()");

		long time_s = System.currentTimeMillis();

		createMTBScript.set_out_bitmap(alloc_mtb);

		Script.LaunchOptions launch_options = new Script.LaunchOptions();
		launch_options.setX(mtb_x, mtb_x+mtb_width);
		launch_options.setY(mtb_y, mtb_y+mtb_height);
		if( use_mtb )
			createMTBScript.forEach_create_mtb(alloc, launch_options);
		else
			createMTBScript.forEach_create_greyscale(alloc, launch_options);
		if( MyDebug.LOG )
			Log.d(TAG, "time after createMTBScript: " + (System.currentTimeMillis() - time_s));

/*		if( MyDebug.LOG ) {
			// debugging
			byte [] mtb_bytes = new byte[mtb_width*mtb_height];
			alloc_mtb.copyTo(mtb_bytes);
			int [] pixels = new int[mtb_width*mtb_height];
			for(int j=0;j<mtb_width*mtb_height;j++) {
				byte b = mtb_bytes[j];
				pixels[j] = Color.argb(255, b, b, b);
			}
			Bitmap mtb_bitmap = Bitmap.createBitmap(pixels, mtb_width, mtb_height, Bitmap.Config.ARGB_8888);
			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/mtb_bitmap" + bitmaps + ".jpg");
			try {
				OutputStream outputStream = new FileOutputStream(file);
				mtb_bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
				outputStream.close();
				MainActivity mActivity = (MainActivity) context;
				mActivity.getStorageUtils().broadcastFile(file, true, false, true);
			}
			catch(IOException e) {
				e.printStackTrace();
			}
			mtb_bitmap.recycle();
			if( MyDebug.LOG )
				Log.d(TAG, "time after saving mtb bitmap into jpeg: " + (System.currentTimeMillis() - time_s));
		}*/
	}
	
	private Point alignMTB(Allocation alloc) {
		if( MyDebug.LOG )
			Log.d(TAG, "alignMTB()");
			
		long time_s = System.currentTimeMillis();

		alignMTBScript.set_bitmap1(alloc);

		int offset_x = 0;
		int offset_y = 0;
		int step_size = initial_step_size;
		while( step_size > 1 ) {
			alignMTBScript.set_off_x( offset_x );
			alignMTBScript.set_off_y( offset_y );
			alignMTBScript.set_step_size( step_size );
			if( MyDebug.LOG ) {
				Log.d(TAG, "call alignMTBScript for image: " + bitmaps);
				Log.d(TAG, "step_size: " + step_size);
			}
			Allocation errorsAllocation = Allocation.createSized(rs, Element.I32(rs), 9);
			alignMTBScript.bind_errors(errorsAllocation);
			alignMTBScript.invoke_init_errors();

			// see note inside align_mtb.rs/align_mtb() for why we sample over a subset of the image
			Script.LaunchOptions launch_options = new Script.LaunchOptions();
			int stop_x = mtb_width/step_size;
			int stop_y = mtb_height/step_size;
			if( MyDebug.LOG ) {
				Log.d(TAG, "stop_x: " + stop_x);
				Log.d(TAG, "stop_y: " + stop_y);
			}
			launch_options.setX(0, stop_x);
			launch_options.setY(0, stop_y);
			if( use_mtb )
				alignMTBScript.forEach_align_mtb(alloc_base_mtb, launch_options);
			else
				alignMTBScript.forEach_align(alloc_base_mtb, launch_options);
			if( MyDebug.LOG )
				Log.d(TAG, "time after alignMTBScript: " + (System.currentTimeMillis() - time_s));

			int best_error = -1;
			int best_id = -1;
			int [] errors = new int[9];
			errorsAllocation.copyTo(errors);
			for(int j=0;j<9;j++) {
				int this_error = errors[j];
				if( MyDebug.LOG )
					Log.d(TAG, "	errors[" + j + "]: " + this_error);
				if( best_id==-1 || this_error < best_error ) {
					best_error = this_error;
					best_id = j;
				}
			}
			if( MyDebug.LOG )
				Log.d(TAG, "	best_id " + best_id + " error: " + best_error);
			if( best_id != -1 ) {
				int this_off_x = best_id % 3;
				int this_off_y = best_id/3;
				this_off_x--;
				this_off_y--;
				if( MyDebug.LOG ) {
					Log.d(TAG, "this_off_x: " + this_off_x);
					Log.d(TAG, "this_off_y: " + this_off_y);
				}
				offset_x += this_off_x * step_size;
				offset_y += this_off_y * step_size;
				if( MyDebug.LOG ) {
					Log.d(TAG, "offset_x is now: " + offset_x);
					Log.d(TAG, "offset_y is now: " + offset_y);
				}
			}
			step_size /= 2;
		}
		
		return new Point(offset_x, offset_y);
	}
}