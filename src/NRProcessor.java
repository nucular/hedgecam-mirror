package com.caddish_hedgehog.hedgecam2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;

public class NRProcessor {
	private static final String TAG = "HedgeCam/NRProcessor";

	private int width = 0;
	private int height = 0;

	private short bitmaps = 0;

	private RenderScript rs;
	private ScriptC_nr script;

	private Allocation alloc_base;
	private Allocation alloc_avg;

	public NRProcessor(RenderScript rs, Bitmap bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "new NRProcessor");

		final long debug_time = System.nanoTime();

		this.rs = rs;
		this.script = new ScriptC_nr(rs);

		this.width = bitmap.getWidth();
		this.height = bitmap.getHeight();

		this.alloc_base = Allocation.createFromBitmap(rs, bitmap);
	
		Type.Builder builder = new Type.Builder(rs, Element.U16_3(rs));
		builder.setX(width);
		builder.setY(height);
		this.alloc_avg = Allocation.createTyped(rs, builder.create());
		script.set_alloc_avg(alloc_avg);
		
		script.forEach_start(alloc_base);
		bitmaps = 1;

		if( MyDebug.LOG )
			Log.d(TAG, "new NRProcessor time: " + (System.nanoTime() - debug_time));
	}
	
	public void addBitmap(Bitmap bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "addBitmap()");

		final int bitmap_width = bitmap.getWidth();
		final int bitmap_height = bitmap.getHeight();

		if (bitmap_width != width || bitmap_height != height) {
			return;
		}

		final long debug_time = System.nanoTime();

		Allocation alloc = Allocation.createFromBitmap(rs, bitmap);
		script.forEach_add(alloc);

		bitmaps++;

		if( MyDebug.LOG )
			Log.d(TAG, "addBitmap() time: " + (System.nanoTime() - debug_time));
	}
	
	public Allocation finish(int adjust_levels) {
		if( MyDebug.LOG )
			Log.d(TAG, "finish()");

		final long debug_time = System.nanoTime();

		int min = 0;
		int max = 0;
		if (adjust_levels > 0) {
			Allocation alloc_max_min = Allocation.createSized(rs, Element.U32(rs), 2);
			script.bind_max_min(alloc_max_min);
			script.invoke_init_max_min();
			if (adjust_levels == 2) {
				script.forEach_calc_min_max(alloc_avg);
				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_calc_min_max: " + (System.nanoTime() - debug_time));
			} else if (adjust_levels == 1) {
				script.forEach_calc_max(alloc_avg);
				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_calc_max: " + (System.nanoTime() - debug_time));
			}
			
			int [] max_min = new int[2];
			alloc_max_min.copyTo(max_min);
			max = max_min[0];
			min = max_min[1];
			if( MyDebug.LOG )
				Log.d(TAG, "min: " + min + ", max: " + max);
		}
		if (adjust_levels == 2) {
			if (min == 0 || max < min ) {
				adjust_levels = 1;
			} else {
				if( MyDebug.LOG )
					Log.d(TAG, "finish(): adjusting lights and shadows...");

				max -= min;

				script.set_fDivider((float)max / 255.0f);
				script.set_fMin((float)min);
				script.forEach_finish_ls(alloc_base);
				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_finish_ls: " + (System.nanoTime() - debug_time));
			}
		}

		if (adjust_levels == 1) {
			if (max == 0 || max == 255*bitmaps) {
				adjust_levels = 0;
			} else {
				if( MyDebug.LOG )
					Log.d(TAG, "finish(): adjusting lights...");

				script.set_fDivider((float)max / 255.0f);
				script.forEach_finish_l(alloc_base);
				if( MyDebug.LOG )
					Log.d(TAG, "time after script.forEach_finish_l: " + (System.nanoTime() - debug_time));
			}
		}

		if (adjust_levels == 0) {
			if( MyDebug.LOG )
				Log.d(TAG, "finish(): no adjusting levels");

			script.set_divider(bitmaps);
			script.forEach_finish(alloc_base);
			if( MyDebug.LOG )
				Log.d(TAG, "time after script.forEach_finish: " + (System.nanoTime() - debug_time));
		}
		
		
		if( MyDebug.LOG )
			Log.d(TAG, "finish() time: " + (System.nanoTime() - debug_time));
		
		return alloc_base;
	}
}