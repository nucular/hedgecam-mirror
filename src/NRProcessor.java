package com.caddish_hedgehog.hedgecam2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class NRProcessor {
	private static final String TAG = "NRProcessor";

	private int width = 0;
	private int height = 0;
	
	private byte bitmaps = 0;
	
	private short[] pixels_r = null;
	private short[] pixels_g = null;
	private short[] pixels_b = null;
	
	private int[] row_pixels = null;
	
	public NRProcessor(Context context) {
	
	}
	
	public void start(Bitmap bitmap) {
		if( MyDebug.LOG )
			Log.d(TAG, "start()");

		final long debug_time = System.nanoTime();

		final int bitmap_width = bitmap.getWidth();
		final int bitmap_height = bitmap.getHeight();
		
		if (bitmap_width != width || bitmap_height != height) {
			width = bitmap_width;
			height = bitmap_height;
			int array_size = width*height;
			
			pixels_r = new short[array_size];
			pixels_g = new short[array_size];
			pixels_b = new short[array_size];
			
			row_pixels = new int[width];
		}
		
		int i = 0;
		int pixel = 0;
		for (int y = 0; y < bitmap_height; y++) {
			bitmap.getPixels(row_pixels, 0, bitmap_width, 0, y, bitmap_width, 1);
			
			for (int x = 0; x < bitmap_width; x++) {
				pixel = row_pixels[x];

				pixels_r[i] = (short)((pixel >> 16) & 0xFF);
				pixels_g[i] = (short)((pixel >> 8) & 0xFF);
				pixels_b[i] = (short)(pixel & 0xFF);
				
				i++;
			}
		}
		
		bitmaps = 1;
		if( MyDebug.LOG )
			Log.d(TAG, "start() time: " + (System.nanoTime() - debug_time));
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

		int i = 0;
		int pixel = 0;
		for (int y = 0; y < bitmap_height; y++) {
			bitmap.getPixels(row_pixels, 0, bitmap_width, 0, y, bitmap_width, 1);
			
			for (int x = 0; x < bitmap_width; x++) {
				pixel = row_pixels[x];

				pixels_r[i] += (pixel >> 16) & 0xFF;
				pixels_g[i] += (pixel >> 8) & 0xFF;
				pixels_b[i] += pixel & 0xFF;

				i++;
			}
		}

		bitmaps++;

		if( MyDebug.LOG )
			Log.d(TAG, "addBitmap() time: " + (System.nanoTime() - debug_time));
	}
	
	public void finish(Bitmap bitmap, int adjust_levels) {
		if( MyDebug.LOG )
			Log.d(TAG, "finish()");

		final int bitmap_width = bitmap.getWidth();
		final int bitmap_height = bitmap.getHeight();

		if (bitmap_width != width || bitmap_height != height) {
			return;
		}

		final long debug_time = System.nanoTime();

		int i = 0;

		if (adjust_levels == 1) {
			if( MyDebug.LOG )
				Log.d(TAG, "adjust levels: highlights");

			short max = 0;
			
			for (i = 0; i < bitmap_width*bitmap_height; i++) {
				if (pixels_r[i] > max)
					max = pixels_r[i];
				if (pixels_g[i] > max)
					max = pixels_g[i];
				if (pixels_b[i] > max)
					max = pixels_b[i];
			}
			
			i = 0;
			final float divider;
			if (max > 0)
				divider = (float)max/255f;
			else
				divider = bitmaps;
			for (int y = 0; y < bitmap_height; y++) {
				for (int x = 0; x < bitmap_width; x++) {
					row_pixels[x] = Color.rgb((int)((float)pixels_r[i]/divider), (int)((float)pixels_g[i]/divider), (int)((float)pixels_b[i]/divider));
					
					i++;
				}
				
				bitmap.setPixels(row_pixels, 0, bitmap_width, 0, y, bitmap_width, 1);
			}
		} else if (adjust_levels == 2) {
			if( MyDebug.LOG )
				Log.d(TAG, "adjust levels: highlights and shadows");

			short min = (short)(255*bitmaps);
			short max = 0;
			
			for (i = 0; i < bitmap_width*bitmap_height; i++) {
				if (pixels_r[i] > max)
					max = pixels_r[i];
				if (pixels_g[i] > max)
					max = pixels_g[i];
				if (pixels_b[i] > max)
					max = pixels_b[i];

				if (pixels_r[i] < min)
					min = pixels_r[i];
				if (pixels_g[i] < min)
					min = pixels_g[i];
				if (pixels_b[i] < min)
					min = pixels_b[i];
			}
			
			max -= min;
			
			i = 0;
			final float divider;
			if (max > 0)
				divider = (float)max/255f;
			else {
				divider = bitmaps;
				min = 0;
			}
			for (int y = 0; y < bitmap_height; y++) {
				for (int x = 0; x < bitmap_width; x++) {
					row_pixels[x] = Color.rgb((int)((float)(pixels_r[i]-min)/divider), (int)((float)(pixels_g[i]-min)/divider), (int)((float)(pixels_b[i]-min)/divider));
					
					i++;
				}
				
				bitmap.setPixels(row_pixels, 0, bitmap_width, 0, y, bitmap_width, 1);
			}
		} else {
			if( MyDebug.LOG )
				Log.d(TAG, "adjust levels: none");

			final int divider = bitmaps;
			for (int y = 0; y < bitmap_height; y++) {
				for (int x = 0; x < bitmap_width; x++) {
					row_pixels[x] = Color.rgb((int)(pixels_r[i]/divider), (int)(pixels_g[i]/divider), (int)(pixels_b[i]/divider));
					
					i++;
				}
				
				bitmap.setPixels(row_pixels, 0, bitmap_width, 0, y, bitmap_width, 1);
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "finish() time: " + (System.nanoTime() - debug_time));
	}
}