package com.caddish_hedgehog.hedgecam2;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;
//import com.caddish_hedgehog.hedgecam2.JniBitmap;
import com.caddish_hedgehog.hedgecam2.Prefs;
import com.caddish_hedgehog.hedgecam2.UI.QueueCounter;
import com.caddish_hedgehog.hedgecam2.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint.Align;
import android.graphics.Paint;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.RSInvalidStateException;
import android.renderscript.Script;
import android.renderscript.Type;
import android.support.annotation.RequiresApi;
import android.support.media.ExifInterface;
import android.util.Log;
import android.widget.TextView;

/** Handles the saving (and any required processing) of photos.
 */
public class ImageSaver {
	private static final String TAG = "HedgeCam/ImageSaver";

	// note that ExifInterface now has fields for these types, but that requires Android 6 or 7
	private static final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
	private static final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";
	private static final String TAG_DATETIME_ORIGINAL = "DateTimeOriginal";
	private static final String TAG_DATETIME_DIGITIZED = "DateTimeDigitized";

	private final Paint p = new Paint();

	private final MainActivity main_activity;
	private final HDRProcessor hdrProcessor;
	private NRProcessor nrProcessor;
	
	private final String software_name;

	/* We use a separate count n_images_to_save, rather than just relying on the queue size, so we can take() an image from queue,
	 * but only decrement the count when we've finished saving the image.
	 * In general, n_images_to_save represents the number of images still to process, including ones currently being processed.
	 * Therefore we should always have n_images_to_save >= queue.size().
	 */
	private int n_images_to_save = 0;
	private final List<Request> queue = new ArrayList<>(); // since we remove from the queue and then process in the saver thread, in practice the number of background photos - including the one being processed - is one more than the length of this queue
	private volatile Thread saving_thread;
	private final QueueCounter queueCounter;
	
	static class ProcessingSettings {
		public boolean save_base;
		public String hdr_tonemapping;
		public String hdr_local_contrast;
		public String hdr_n_tiles;
		public boolean hdr_deghost;
		public String hdr_unsharp_mask;
		public String hdr_unsharp_mask_radius;

		public boolean do_auto_stabilise;
		public double level_angle;
		public boolean mirror;
		public int adjust_levels;
		public double histogram_level;
		public boolean stamp;
		public String stamp_text;
		public String stamp_dateformat;
		public String stamp_timeformat;
		public String stamp_gpsformat;
		public boolean stamp_store_address;
		public boolean stamp_store_altitude;
		
		public String align;
		
		ProcessingSettings () {
			save_base = false;
			adjust_levels = 0;
			histogram_level = 0.0d;
			align = "none";
		}
	}
	
	static class Metadata {
		public String author;
		public String comment;
		public boolean comment_as_file;
	}

	static class Request {
		enum Type {
			JPEG,
			PNG,
			RAW,
			DUMMY
		}
		Type type = Type.JPEG;
		final Prefs.PhotoMode photo_mode; // for jpeg
		final List<CameraController.Photo> images;
		final String yuv_conversion;
		final DngCreator dngCreator; // for raw
		final Image image;
		final boolean image_capture_intent;
		final Uri image_capture_intent_uri;
		final boolean using_camera2;
		final int image_quality;
		final boolean allow_rotation;
		final ProcessingSettings processing_settings;
		final Metadata metadata;
		final boolean is_front_facing;
		final String prefix;
		final Date current_date;
		final int image_number;
		final boolean store_location;
		final Location location;
		final boolean store_geo_direction;
		final double geo_direction;
		int sample_factor = 1; // sampling factor for thumbnail, higher means lower quality
		
		Request(Type type,
			Prefs.PhotoMode photo_mode,
			List<CameraController.Photo> images,
			String yuv_conversion,
			DngCreator dngCreator, Image image,
			boolean image_capture_intent, Uri image_capture_intent_uri,
			boolean using_camera2, int image_quality,
			boolean allow_rotation,
			ProcessingSettings processing_settings,
			Metadata metadata,
			boolean is_front_facing,
			String prefix, Date current_date, int image_number,
			boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
			int sample_factor) {
			this.type = type;
			this.photo_mode = photo_mode;
			this.images = images;
			this.yuv_conversion = yuv_conversion;
			this.dngCreator = dngCreator;
			this.image = image;
			this.image_capture_intent = image_capture_intent;
			this.image_capture_intent_uri = image_capture_intent_uri;
			this.using_camera2 = using_camera2;
			this.image_quality = image_quality;
			this.allow_rotation = allow_rotation;
			this.processing_settings = processing_settings;
			this.metadata = metadata;
			this.is_front_facing = is_front_facing;
			this.prefix = prefix;
			this.current_date = current_date;
			this.image_number = image_number;
			this.store_location = store_location;
			this.location = location;
			this.store_geo_direction = store_geo_direction;
			this.geo_direction = geo_direction;
			this.sample_factor = sample_factor;
		}
	}

	ImageSaver(MainActivity main_activity) {
		if( MyDebug.LOG )
			Log.d(TAG, "ImageSaver");
		this.main_activity = main_activity;
		this.hdrProcessor = new HDRProcessor(main_activity);
		this.queueCounter = new QueueCounter((TextView)main_activity.findViewById(R.id.queue_count));

		String version = null;
		try {
			PackageInfo pInfo = main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0);
			version = pInfo.versionName;
		} catch(NameNotFoundException e) {}
		this.software_name = "HedgeCam" + (version != null ? " " + version : "");

		p.setAntiAlias(true);
	}

	/** Saves a photo.
	 *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
	 *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
	 *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
	 *  successfully.
	 */
	boolean saveImageJpeg(boolean do_in_background,
			boolean use_png,
			Prefs.PhotoMode photo_mode,
			List<CameraController.Photo> images,
			String yuv_conversion,
			boolean image_capture_intent, Uri image_capture_intent_uri,
			boolean using_camera2, int image_quality,
			boolean allow_rotation,
			ProcessingSettings processing_settings,
			Metadata metadata,
			boolean is_front_facing,
			String prefix, Date current_date, int image_number,
			boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
			int sample_factor) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "saveImageJpeg");
			Log.d(TAG, "do_in_background? " + do_in_background);
			Log.d(TAG, "number of images: " + images.size());
		}
		Request request = new Request(use_png ? Request.Type.PNG : Request.Type.JPEG,
				photo_mode,
				images,
				yuv_conversion,
				null, null,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				allow_rotation,
				processing_settings,
				metadata,
				is_front_facing,
				prefix, current_date, image_number,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);

		boolean success;
		if( do_in_background ) {
			if( MyDebug.LOG )
				Log.d(TAG, "add background request");
			addRequest(request);
			if( request.photo_mode == Prefs.PhotoMode.HDR || request.images.size() > 1 ) {
				// For (multi-image) HDR, we also add a dummy request, effectively giving it a cost of 2 - to reflect the fact that HDR is more memory intensive
				// (arguably it should have a cost of 3, to reflect the 3 JPEGs, but one can consider this comparable to RAW+JPEG, which have a cost
				// of 2, due to RAW and JPEG each needing their own request).
				// Similarly for saving multiple images (expo-bracketing)
				addDummyRequest();
			}
			success = true; // always return true when done in background
		}
		else {
			success = saveImageNow(request, false);
		}
		if( MyDebug.LOG )
			Log.d(TAG, "success: " + success);
		return success;
	}

	/** Saves a RAW photo.
	 *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
	 *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
	 *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
	 *  successfully.
	 */
	boolean saveImageRaw(boolean do_in_background,
			Prefs.PhotoMode photo_mode,
			DngCreator dngCreator, Image image,
			String prefix, Date current_date, int image_number) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "saveImageRaw");
			Log.d(TAG, "do_in_background? " + do_in_background);
		}
		Request request = new Request(Request.Type.RAW,
				photo_mode,
				null,
				null,
				dngCreator, image,
				false, null,
				false, 0,
				false,
				null,
				null,
				false,
				prefix, current_date, image_number,
				false, null, false, 0.0,
				1);

		boolean success;
		if( do_in_background ) {
			if( MyDebug.LOG )
				Log.d(TAG, "add background request");
			addRequest(request);
			if (request.photo_mode == Prefs.PhotoMode.HDR) {
				// For (multi-image) HDR, we also add a dummy request, effectively giving it a cost of 2 - to reflect the fact that HDR is more memory intensive
				// (arguably it should have a cost of 3, to reflect the 3 JPEGs, but one can consider this comparable to RAW+JPEG, which have a cost
				// of 2, due to RAW and JPEG each needing their own request).
				// Similarly for saving multiple images (expo-bracketing)
				addDummyRequest();
			}
			success = true; // always return true when done in background
		}
		else {
			success = saveImageNowRaw(request.dngCreator, request.image, request.prefix, request.current_date, false);
		}
		if( MyDebug.LOG )
			Log.d(TAG, "success: " + success);
		return success;
	}
	
	/** Adds a request to the background queue, blocking if the queue is already full
	 */
	private void addRequest(Request request) {
		if( MyDebug.LOG )
			Log.d(TAG, "addRequest");
		// this should not be synchronized on "this": BlockingQueue is thread safe, and if it's blocking in queue.put(), we'll hang because
		// the saver queue will need to synchronize on "this" in order to notifyAll() the main thread

		synchronized( this ) {
			if( MyDebug.LOG )
				Log.d(TAG, "ImageSaver thread adding to queue, size: " + queue.size());
			// see above for why we don't synchronize the queue.put call
			// but we synchronize modification to avoid risk of problems related to compiler optimisation (local caching or reordering)
			// also see FindBugs warning due to inconsistent synchronisation
			n_images_to_save++; // increment before adding to the queue, just to make sure the main thread doesn't think we're all done
			queue.add(request);
		}
		if( MyDebug.LOG ) {
			synchronized( this ) { // keep FindBugs happy
				Log.d(TAG, "ImageSaver thread added to queue, size is now: " + queue.size());
				Log.d(TAG, "images still to save is now: " + n_images_to_save);
			}
		}
		
		if( request.type != Request.Type.DUMMY ) {
			queueCounter.increase();
			if (request.processing_settings != null && request.processing_settings.save_base) {
				for (int i = 0; i < request.images.size(); i++)
					queueCounter.increase();
			}
		}

		if (saving_thread != null)
			return;
		
		saving_thread = new Thread() {
			@Override
			public void run() {
				int queue_size;
				synchronized( this ) {
					queue_size = queue.size();
				}
				while (queue_size > 0) {
					if( MyDebug.LOG )
						Log.d(TAG, "ImageSaver thread reading from queue, size: " + queue_size);
						
					Request request;
					synchronized( this ) {
						request = queue.get(0);
						queue.remove(0);
					}
					
					boolean success;
					if( request.type == Request.Type.RAW ) {
						if( MyDebug.LOG )
							Log.d(TAG, "request is raw");
						success = saveImageNowRaw(request.dngCreator, request.image, request.prefix, request.current_date, true);
					}
					else if( request.type == Request.Type.JPEG || request.type == Request.Type.PNG ) {
						if( MyDebug.LOG )
							Log.d(TAG, "request is jpeg");
						success = saveImageNow(request, true);
					}
					else if( request.type == Request.Type.DUMMY ) {
						if( MyDebug.LOG )
							Log.d(TAG, "request is dummy");
						success = true;
					}
					else {
						if( MyDebug.LOG )
							Log.e(TAG, "request is unknown type!");
						success = false;
					}
					if( MyDebug.LOG ) {
						if( success )
							Log.d(TAG, "ImageSaver thread successfully saved image");
						else
							Log.e(TAG, "ImageSaver thread failed to save image");
					}
					synchronized( this ) {
						n_images_to_save--;
						if( MyDebug.LOG )
							Log.d(TAG, "ImageSaver thread processed new request from queue, images to save is now: " + n_images_to_save);
						if( MyDebug.LOG && n_images_to_save < 0 ) {
							Log.e(TAG, "images to save has become negative");
							throw new RuntimeException();
						}
						queue_size = queue.size();
					}
					if( request.type != Request.Type.DUMMY )
						queueCounter.decrease();
				}
				main_activity.savingImage(false);
				queueCounter.reset();
				saving_thread = null;
			}
		};
		saving_thread.setPriority(1);
		
		main_activity.savingImage(true);
		saving_thread.start();

	}

	private void addDummyRequest() {
		Request dummy_request = new Request(Request.Type.DUMMY,
			Prefs.PhotoMode.Standard,
			null,
			null,
			null, null,
			false, null,
			false, 0,
			false,
			null,
			null,
			false,
			null, null, 0,
			false, null, false, 0.0,
			1);
		if( MyDebug.LOG )
			Log.d(TAG, "add dummy request");
		addRequest(dummy_request);
	}

	/** Loads a single jpeg as a Bitmaps.
	 * @param mutable Whether the bitmap should be mutable. Note that when converting to bitmaps
	 *				for the image post-processing (auto-stabilise etc), in general we need the
	 *				bitmap to be mutable (for photostamp to work).
	 */
	@SuppressWarnings("deprecation")
	private Bitmap loadBitmap(CameraController.Photo photo, String yuv_conversion, boolean mutable) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "loadBitmap");
			Log.d(TAG, "mutable?: " + mutable);
		}
		long time_s = System.currentTimeMillis();
		if (photo.y != null) {
			Allocation alloc = loadYUV(photo, yuv_conversion);
			Bitmap bitmap = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888);
			
			alloc.copyTo(bitmap);
			
			if( MyDebug.LOG )
				Log.d(TAG, "time after alloc.copyTo: " + (System.currentTimeMillis() - time_s));

			return bitmap;
		} else {
			BitmapFactory.Options options = new BitmapFactory.Options();
			if( MyDebug.LOG )
				Log.d(TAG, "options.inMutable is: " + options.inMutable);
			options.inMutable = mutable;
			if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
				// setting is ignored in Android 5 onwards
				options.inPurgeable = true;
			}
			Bitmap bitmap = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.length, options);
			if( bitmap == null ) {
				Log.e(TAG, "failed to decode bitmap");
			}
			if( MyDebug.LOG )
				Log.d(TAG, "time after BitmapFactory.decodeByteArray: " + (System.currentTimeMillis() - time_s));
			return bitmap;
		}
	}
	
	private Allocation loadAllocation(CameraController.Photo photo, String yuv_conversion) {
		if (photo.y != null) {
			return loadYUV(photo, yuv_conversion);
		} else {
			Bitmap bitmap = loadBitmap(photo, yuv_conversion, false);
			return Allocation.createFromBitmap(main_activity.getRenderScript(), bitmap);
		}
	}

	private Allocation loadYUV(CameraController.Photo photo, String conversion) {
		long time_s = System.currentTimeMillis();

		RenderScript rs = main_activity.getRenderScript();
		
		ScriptC_yuv yuvScript = new ScriptC_yuv(rs);
		if( MyDebug.LOG ) {
			Log.d(TAG, "YUV performance: time after new ScriptC_yuv: " + (System.currentTimeMillis() - time_s));
		}

		yuvScript.set_y_pixel_stride(photo.pixelStrideY);
		int row_space = Prefs.getRowSpaceYPref();
		yuvScript.set_y_row_stride(row_space >= 0 ? photo.width+row_space : photo.rowStrideY);

		yuvScript.set_uv_pixel_stride(photo.pixelStrideUV);
		row_space = Prefs.getRowSpaceUVPref();
		yuvScript.set_uv_row_stride(row_space >= 0 ? photo.width+row_space : photo.rowStrideUV);
	
		Type.Builder builder = new Type.Builder(rs, Element.U8(rs));
		builder.setX(photo.width).setY(photo.height);
		Allocation in_y = Allocation.createTyped(rs, builder.create());
		in_y.copyFrom(photo.y);
		yuvScript.set_inY(in_y);

		builder = new Type.Builder(rs, Element.U8(rs));
		builder.setX(photo.u.length);
		Allocation in_u = Allocation.createTyped(rs, builder.create());
		in_u.copyFrom(photo.u);
		yuvScript.set_inU(in_u);

		Allocation in_v = Allocation.createTyped(rs, builder.create());
		in_v.copyFrom(photo.v);
		yuvScript.set_inV(in_v);

		if( MyDebug.LOG ) {
			Log.d(TAG, "YUV performance: time after creating YUV allocations: " + (System.currentTimeMillis() - time_s));
		}

		builder = new Type.Builder(rs, Element.RGBA_8888(rs));
		builder.setX(photo.width);
		builder.setY(photo.height);
		Allocation out = Allocation.createTyped(rs, builder.create());

		if( MyDebug.LOG ) {
			Log.d(TAG, "YUV performance: time after creating out allocation: " + (System.currentTimeMillis() - time_s));
		}
		
		switch(conversion) {
			case "wide_range":
				yuvScript.forEach_YUV420ToRGB_wide_range(out);
				break;
			case "saturated":
				yuvScript.forEach_YUV420ToRGB_saturated(out);
				break;
			default:
				yuvScript.forEach_YUV420ToRGB(out);
		}
		
		if( MyDebug.LOG ) {
			Log.d(TAG, "YUV performance: time after converting to RGB: " + (System.currentTimeMillis() - time_s));
		}

		return out;
	}

	/** Helper class for loadBitmaps().
	 */
	private class LoadBitmapThread extends Thread {
		Bitmap bitmap;
		final BitmapFactory.Options options;
		final CameraController.Photo photo;
		final String yuv_conversion;
		LoadBitmapThread(BitmapFactory.Options options, CameraController.Photo photo, String yuv_conversion) {
			this.options = options;
			this.photo = photo;
			this.yuv_conversion = yuv_conversion;
		}

		public void run() {
			if (this.photo.y != null) {
				Allocation alloc = loadYUV(photo, yuv_conversion);
				this.bitmap = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888);
				
				alloc.copyTo(bitmap);
			} else {
				this.bitmap = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.length, options);
			}
		}
	}

	/** Converts the array of jpegs to Bitmaps. The bitmap with index mutable_id will be marked as mutable (or set to -1 to have no mutable bitmaps).
	 */
	@SuppressWarnings("deprecation")
	private List<Bitmap> loadBitmaps(List<CameraController.Photo> images, int mutable_id, String yuv_conversion) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "loadBitmaps");
			Log.d(TAG, "mutable_id: " + mutable_id);
		}
		BitmapFactory.Options mutable_options = new BitmapFactory.Options();
		mutable_options.inMutable = true; // bitmap that needs to be writable
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inMutable = false; // later bitmaps don't need to be writable
		if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
			// setting is ignored in Android 5 onwards
			mutable_options.inPurgeable = true;
			options.inPurgeable = true;
		}
		LoadBitmapThread [] threads = new LoadBitmapThread[images.size()];
		for(int i=0;i<images.size();i++) {
			threads[i] = new LoadBitmapThread( i==mutable_id ? mutable_options : options, images.get(i), yuv_conversion );
		}
		// start threads
		if( MyDebug.LOG )
			Log.d(TAG, "start threads");
		for(int i=0;i<images.size();i++) {
			threads[i].start();
		}
		// wait for threads to complete
		boolean ok = true;
		if( MyDebug.LOG )
			Log.d(TAG, "wait for threads to complete");
		try {
			for(int i=0;i<images.size();i++) {
				threads[i].join();
			}
		}
		catch(InterruptedException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "threads interrupted");
			e.printStackTrace();
			ok = false;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "threads completed");

		List<Bitmap> bitmaps = new ArrayList<>();
		for(int i=0;i<images.size() && ok;i++) {
			Bitmap bitmap = threads[i].bitmap;
			if( bitmap == null ) {
				Log.e(TAG, "failed to decode bitmap in thread: " + i);
				ok = false;
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "bitmap " + i + ": " + bitmap + " is mutable? " + bitmap.isMutable());
			}
			bitmaps.add(bitmap);
		}
		
		if( !ok ) {
			if( MyDebug.LOG )
				Log.d(TAG, "cleanup from failure");
			for(int i=0;i<images.size();i++) {
				if( threads[i].bitmap != null ) {
					threads[i].bitmap.recycle();
					threads[i].bitmap = null;
				}
			}
			bitmaps.clear();
			System.gc();
			return null;
		}

		return bitmaps;
	}
	
	/** May be run in saver thread or picture callback thread (depending on whether running in background).
	 */
	private boolean saveImageNow(final Request request, final boolean in_background) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveImageNow");

		if( request.type != Request.Type.JPEG && request.type != Request.Type.PNG) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with unsupported image format");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}

		if( request.images.size() == 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with zero images");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}
		
		if (request.sample_factor != 0) {
			
		}

		boolean success;
		if( request.photo_mode == Prefs.PhotoMode.NoiseReduction ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "average");
				Log.d(TAG, "processing image #" + request.image_number);
			}

			success = true;

			if (request.image_number == 1) {
				if (!request.image_capture_intent && request.processing_settings.save_base)
					saveSingleImageNow(request, request.images.get(0), null, null, "", false, false, in_background);

				Bitmap bitmap = loadBitmap(request.images.get(0), request.yuv_conversion, false);
				if (!in_background) main_activity.savingImage(true);
				boolean align = false;
				boolean crop_aligned = false;
				switch (request.processing_settings.align) {
					case "align":
						align = true;
						break;
					case "align_crop":
						align = true;
						crop_aligned = true;
						break;
				}
				nrProcessor = new NRProcessor(main_activity, main_activity.getRenderScript(), bitmap, align, crop_aligned);
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
					bitmap.recycle();
				}
			} else {
				if (nrProcessor != null) {
					Allocation alloc = loadAllocation(request.images.get(0), request.yuv_conversion);
					nrProcessor.addAllocation(alloc);
				}
			}

			if (request.sample_factor != 0) {
				if( MyDebug.LOG )
					Log.d(TAG, "saving NR image");
				Allocation nr_alloc = nrProcessor.finish(request.processing_settings.adjust_levels, request.processing_settings.histogram_level);
				nrProcessor = null;
				request.processing_settings.adjust_levels = Prefs.ADJUST_LEVELS_NONE;
				
				String suffix = "_NR";
				success = saveSingleImageNow(request, request.images.get(0), null, nr_alloc, suffix, true, true, in_background);
				if( MyDebug.LOG && !success )
					Log.e(TAG, "saveSingleImageNow failed for nr image");
			}

			System.gc();
		}
		else if( request.photo_mode == Prefs.PhotoMode.HDR || request.photo_mode == Prefs.PhotoMode.DRO ) {
			if( MyDebug.LOG )
				Log.d(TAG, "hdr");
			if( request.images.size() != 1 && request.images.size() != 3 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow expected either 1 or 3 images for hdr, not " + request.images.size());
				// throw runtime exception, as this is a programming error
				throw new RuntimeException();
			}

			long time_s = System.currentTimeMillis();
			if( request.images.size() > 1 && !request.image_capture_intent && request.processing_settings.save_base ) {
				// if there's only 1 image, we're in DRO mode, and shouldn't save the base image
				if( MyDebug.LOG )
					Log.d(TAG, "save base images");
				for(int i=0;i<request.images.size();i++) {
					// note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
					CameraController.Photo image = request.images.get(i);
					// don't update the thumbnails, only do this for the final image - so user doesn't think it's complete, click gallery, then wonder why the final image isn't there
					// also don't mark these images as being shared
					if( !saveSingleImageNow(request, image, null, null, "_EXP" + i, false, false, in_background) ) {
						if( MyDebug.LOG )
							Log.e(TAG, "saveSingleImageNow failed for exposure image");
						// we don't set success to false here - as for deciding whether to pause preview or not (which is all we use the success return for), all that matters is whether we saved the final HDR image
					}
					queueCounter.decrease();
				}
				if( MyDebug.LOG )
					Log.d(TAG, "HDR performance: time after saving base exposures: " + (System.currentTimeMillis() - time_s));
			}

			// note, even if we failed saving some of the expo images, still try to save the HDR image
			if( MyDebug.LOG )
				Log.d(TAG, "create HDR image");
			if (!in_background) main_activity.savingImage(true);

			// see documentation for HDRProcessor.processHDR() - because we're using release_bitmaps==true, we need to make sure that
			// the bitmap that will hold the output HDR image is mutable (in case of options like photo stamp)
			// see test testTakePhotoHDRPhotoStamp.
			int base_bitmap = (request.images.size()-1)/2;
			if( MyDebug.LOG )
				Log.d(TAG, "base_bitmap: " + base_bitmap);
			List<Bitmap> bitmaps = loadBitmaps(request.images, base_bitmap, request.yuv_conversion);
			if( bitmaps == null ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to load bitmaps");
				if (!in_background) main_activity.savingImage(false);
				return false;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "HDR performance: time after decompressing base exposures: " + (System.currentTimeMillis() - time_s));
			}
			if( MyDebug.LOG )
				Log.d(TAG, "before HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
			Allocation out_alloc = null;
			try {
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
					int local_contrast;
					try {local_contrast = Integer.parseInt(request.processing_settings.hdr_local_contrast);}
					catch (NumberFormatException e) {local_contrast = 5;}
					if (local_contrast < 0 || local_contrast > 10) local_contrast = 5;

					int unsharp_mask;
					try {unsharp_mask = Integer.parseInt(request.processing_settings.hdr_unsharp_mask);}
					catch (NumberFormatException e) {unsharp_mask = 5;}
					if (unsharp_mask < 0 || unsharp_mask > 10) unsharp_mask = 5;

					int unsharp_mask_radius;
					try {unsharp_mask_radius = Integer.parseInt(request.processing_settings.hdr_unsharp_mask_radius);}
					catch (NumberFormatException e) {unsharp_mask_radius = 5;}
					if (unsharp_mask_radius < 0 || unsharp_mask_radius > 20) unsharp_mask_radius = 5;

					int n_tiles;
					try {n_tiles = Integer.parseInt(request.processing_settings.hdr_n_tiles);}
					catch (NumberFormatException e) {n_tiles = 4;}

					HDRProcessor.TonemappingAlgorithm tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD;
					boolean align = false;
					boolean crop_aligned = false;
					if (request.photo_mode == Prefs.PhotoMode.HDR) {
						switch (request.processing_settings.hdr_tonemapping) {
							case "clamp":
								tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_CLAMP;
								break;
							case "exponential":
								tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_EXPONENTIAL;
								break;
							case "filmic":
								tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_FILMIC;
								break;
							case "aces":
								tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_ACES;
								break;
							case "reinhard_new":
								tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD_NEW;
								break;
						}
						
						switch (request.processing_settings.align) {
							case "align":
								align = true;
								break;
							case "align_crop":
								align = true;
								crop_aligned = true;
								break;
						}
					}
					out_alloc = hdrProcessor.processHDR(bitmaps, true, null, align, crop_aligned, (float)local_contrast/10, n_tiles, (float)unsharp_mask/10, unsharp_mask_radius, tonemapping_algorithm, request.processing_settings.hdr_deghost);
				}
				else {
					Log.e(TAG, "shouldn't have offered HDR as an option if not on Android 5");
					throw new RuntimeException();
				}
			}
			catch(HDRProcessorException e) {
				Log.e(TAG, "HDRProcessorException from processHDR: " + e.getCode());
				e.printStackTrace();
				if( e.getCode() == HDRProcessorException.UNEQUAL_SIZES ) {
					// this can happen on OnePlus 3T with old camera API with front camera, seems to be a bug that resolution changes when exposure compensation is set!
					Utils.showToast(null, R.string.failed_to_process_hdr);
					Log.e(TAG, "UNEQUAL_SIZES");
					bitmaps.clear();
					System.gc();
					if (!in_background) main_activity.savingImage(false);
					return false;
				}
				else {
					// throw RuntimeException, as we shouldn't ever get the error INVALID_N_IMAGES, if we do it's a programming error
					throw new RuntimeException();
				}
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "HDR performance: time after creating HDR image: " + (System.currentTimeMillis() - time_s));
			}
//			if( MyDebug.LOG )
//				Log.d(TAG, "after HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
			Bitmap hdr_bitmap = bitmaps.get(base_bitmap);
//			if( MyDebug.LOG )
//				Log.d(TAG, "hdr_bitmap: " + hdr_bitmap + " is mutable? " + hdr_bitmap.isMutable());
			bitmaps.clear();
			System.gc();
			if (!in_background) main_activity.savingImage(false);

			if( MyDebug.LOG )
				Log.d(TAG, "save HDR image");
			int base_image_id = ((request.images.size()-1)/2);
			if( MyDebug.LOG )
				Log.d(TAG, "base_image_id: " + base_image_id);
			String suffix = request.images.size() == 1 ? "_DRO" : "_HDR";
			success = saveSingleImageNow(request, request.images.get(base_image_id), hdr_bitmap, out_alloc, suffix, true, true, in_background);
			if( MyDebug.LOG && !success )
				Log.e(TAG, "saveSingleImageNow failed for hdr image");
			if( MyDebug.LOG ) {
				Log.d(TAG, "HDR performance: time after saving HDR image: " + (System.currentTimeMillis() - time_s));
			}
			hdr_bitmap.recycle();
			System.gc();
		}
		else {
			if( request.images.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow called with multiple images");
				int mid_image = request.images.size()/2;
				success = true;
				for(int i=0;i<request.images.size();i++) {
					// note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
					CameraController.Photo image = request.images.get(i);
					String filename_suffix = "_EXP" + i;
					boolean share_image = i == mid_image;
					if( !saveSingleImageNow(request, image, null, null, filename_suffix, true, share_image, in_background) ) {
						if( MyDebug.LOG )
							Log.e(TAG, "saveSingleImageNow failed for exposure image");
						success = false; // require all images to be saved in order for success to be true (used for pausing the preview)
					}
				}
			}
			else {
				String suffix = "";
				if (request.photo_mode == Prefs.PhotoMode.FastBurst)
					suffix = String.format("_B%03d", request.image_number);
				else if (request.photo_mode == Prefs.PhotoMode.FocusBracketing)
					suffix = String.format("_FB%02d", request.image_number);
				success = saveSingleImageNow(request, request.images.get(0), null, null, suffix, true, true, in_background);
			}
		}

		return success;
	}

	/** Performs the auto-stabilise algorithm on the image.
	 * @param data The jpeg data.
	 * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
	 * @param level_angle The angle in degrees to rotate the image.
	 * @param is_front_facing Whether the camera is front-facing.
	 * @return A bitmap representing the auto-stabilised jpeg.
	 */
	private Bitmap autoStabilise(CameraController.Photo photo, Bitmap bitmap, double level_angle, boolean is_front_facing) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "autoStabilise");
			Log.d(TAG, "level_angle: " + level_angle);
			Log.d(TAG, "is_front_facing: " + is_front_facing);
		}
		while( level_angle < -90 )
			level_angle += 180;
		while( level_angle > 90 )
			level_angle -= 180;
		if( MyDebug.LOG )
			Log.d(TAG, "auto stabilising... angle: " + level_angle);
		if( bitmap != null ) {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			if( MyDebug.LOG ) {
				Log.d(TAG, "level_angle: " + level_angle);
				Log.d(TAG, "decoded bitmap size " + width + ", " + height);
				Log.d(TAG, "bitmap size: " + width*height*4);
			}
				/*for(int y=0;y<height;y++) {
					for(int x=0;x<width;x++) {
						int col = bitmap.getPixel(x, y);
						col = col & 0xffff0000; // mask out red component
						bitmap.setPixel(x, y, col);
					}
				}*/
			Matrix matrix = new Matrix();
			double level_angle_rad_abs = Math.abs( Math.toRadians(level_angle) );
			int w1 = width, h1 = height;
			double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
			double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
			// apply a scale so that the overall image size isn't increased
			float orig_size = w1*h1;
			float rotated_size = (float)(w0*h0);
			float scale = (float)Math.sqrt(orig_size/rotated_size);
			if( main_activity.test_low_memory ) {
				if( MyDebug.LOG ) {
					Log.d(TAG, "TESTING LOW MEMORY");
					Log.d(TAG, "scale was: " + scale);
				}
				// test 20MP on Galaxy Nexus or Nexus 7; 29MP on Nexus 6 and 36MP OnePlus 3T
				if( width*height >= 7500 )
					scale *= 1.5f;
				else
					scale *= 2.0f;
			}
			if( MyDebug.LOG ) {
				Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
				Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
				Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
			}
			matrix.postScale(scale, scale);
			w0 *= scale;
			h0 *= scale;
			w1 *= scale;
			h1 *= scale;
			if( MyDebug.LOG ) {
				Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
				Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
			}
			if( is_front_facing ) {
				matrix.postRotate((float)-level_angle);
			}
			else {
				matrix.postRotate((float)level_angle);
			}
			Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
			// careful, as new_bitmap is sometimes not a copy!
			if( new_bitmap != bitmap ) {
				bitmap.recycle();
				bitmap = new_bitmap;
			}
			System.gc();
			if( MyDebug.LOG ) {
				Log.d(TAG, "rotated and scaled bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
				Log.d(TAG, "rotated and scaled bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
			}
			double tan_theta = Math.tan(level_angle_rad_abs);
			double sin_theta = Math.sin(level_angle_rad_abs);
			double denom = ( h0/w0 + tan_theta );
			double alt_denom = ( w0/h0 + tan_theta );
			if( denom == 0.0 || denom < 1.0e-14 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "zero denominator?!");
			}
			else if( alt_denom == 0.0 || alt_denom < 1.0e-14 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "zero alt denominator?!");
			}
			else {
				int w2 = (int)(( h0 + 2.0*h1*sin_theta*tan_theta - w0*tan_theta ) / denom);
				int h2 = (int)(w2*h0/w0);
				int alt_h2 = (int)(( w0 + 2.0*w1*sin_theta*tan_theta - h0*tan_theta ) / alt_denom);
				int alt_w2 = (int)(alt_h2*w0/h0);
				if( MyDebug.LOG ) {
					//Log.d(TAG, "h0 " + h0 + " 2.0*h1*sin_theta*tan_theta " + 2.0*h1*sin_theta*tan_theta + " w0*tan_theta " + w0*tan_theta + " / h0/w0 " + h0/w0 + " tan_theta " + tan_theta);
					Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
					Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
				}
				if( alt_w2 < w2 ) {
					if( MyDebug.LOG ) {
						Log.d(TAG, "chose alt!");
					}
					w2 = alt_w2;
					h2 = alt_h2;
				}
				if( w2 <= 0 )
					w2 = 1;
				else if( w2 >= bitmap.getWidth() )
					w2 = bitmap.getWidth()-1;
				if( h2 <= 0 )
					h2 = 1;
				else if( h2 >= bitmap.getHeight() )
					h2 = bitmap.getHeight()-1;
				int x0 = (bitmap.getWidth()-w2)/2;
				int y0 = (bitmap.getHeight()-h2)/2;
				if( MyDebug.LOG ) {
					Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
				}
				// We need the bitmap to be mutable for photostamp to work - contrary to the documentation for Bitmap.createBitmap
				// (which says it returns an immutable bitmap), we seem to always get a mutable bitmap anyway. A mutable bitmap
				// would result in an exception "java.lang.IllegalStateException: Immutable bitmap passed to Canvas constructor"
				// from the Canvas(bitmap) constructor call in the photostamp code, and I've yet to see this from Google Play.
				new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
				if( new_bitmap != bitmap ) {
					bitmap.recycle();
					bitmap = new_bitmap;
				}
				if( MyDebug.LOG )
					Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
				System.gc();
			}
		}
		return bitmap;
	}

	/** Applies any photo stamp options (if they exist).
	 * @param data The jpeg data.
	 * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
	 * @return A bitmap representing the stamped jpeg. Will be null if the input bitmap is null and
	 *		 no photo stamp is applied.
	 */
	private Bitmap stampImage(final Request request, CameraController.Photo photo, Bitmap bitmap) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stampImage");
		}
		final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
		boolean text_stamp = request.processing_settings.stamp_text.length() > 0;
		if( request.processing_settings.stamp || text_stamp ) {
			if( bitmap != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "stamp info to bitmap: " + bitmap);
				if( MyDebug.LOG )
					Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				if( MyDebug.LOG ) {
					Log.d(TAG, "decoded bitmap size " + width + ", " + height);
					Log.d(TAG, "bitmap size: " + width*height*4);
				}
				Canvas canvas = new Canvas(bitmap);
				int line_count = 0;
				if( request.processing_settings.stamp ) {
					if( MyDebug.LOG )
						Log.d(TAG, "stamp date");
					// doesn't respect user preferences such as 12/24 hour - see note about in draw() about DateFormat.getTimeInstance()
					String date_stamp = TextFormatter.getDateString(request.processing_settings.stamp_dateformat, request.current_date);
					String time_stamp = TextFormatter.getTimeString(request.processing_settings.stamp_timeformat, request.current_date);
					if( MyDebug.LOG ) {
						Log.d(TAG, "date_stamp: " + date_stamp);
						Log.d(TAG, "time_stamp: " + time_stamp);
					}
					if( date_stamp.length() > 0 || time_stamp.length() > 0 ) {
						String datetime_stamp = "";
						if( date_stamp.length() > 0 )
							datetime_stamp += date_stamp;
						if( time_stamp.length() > 0 ) {
							if( datetime_stamp.length() > 0 )
								datetime_stamp += " ";
							datetime_stamp += time_stamp;
						}
						applicationInterface.drawTextOnPhoto(canvas, p, datetime_stamp, width, height, line_count);
						line_count++;
					}
					String gps_stamp = main_activity.getTextFormatter().getGPSString(request.processing_settings.stamp_gpsformat, request.store_location, request.location, request.processing_settings.stamp_store_address, request.processing_settings.stamp_store_altitude, request.store_geo_direction, request.geo_direction);
					if( gps_stamp.length() > 0 ) {
						if( MyDebug.LOG )
							Log.d(TAG, "stamp with location_string: " + gps_stamp);
						applicationInterface.drawTextOnPhoto(canvas, p, gps_stamp, width, height, line_count);
						line_count++;
					}
				}
				if( text_stamp ) {
					if( MyDebug.LOG )
						Log.d(TAG, "stamp text");
					applicationInterface.drawTextOnPhoto(canvas, p, request.processing_settings.stamp_text, width, height, line_count);
					line_count++;
				}
			}
		}
		return bitmap;
	}

	/** May be run in saver thread or picture callback thread (depending on whether running in background).
	 *  The requests.images field is ignored, instead we save the supplied data or bitmap.
	 *  If bitmap is null, then the supplied jpeg data is saved. If bitmap is non-null, then the bitmap is
	 *  saved, but the supplied data is still used to read EXIF data from.
	 *  @param update_thumbnail - Whether to update the thumbnail (and show the animation).
	 *  @param share_image - Whether this image should be marked as the one to share (if multiple images can
	 *  be saved from a single shot (e.g., saving exposure images with HDR).
	 */
	@SuppressLint("SimpleDateFormat")
	@SuppressWarnings("deprecation")
	private boolean saveSingleImageNow(final Request request, CameraController.Photo photo, Bitmap bitmap, Allocation alloc, String filename_suffix, boolean update_thumbnail, boolean share_image, boolean in_background) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveSingleImageNow");

		String extension;
		boolean use_png = false;
		if( request.type == Request.Type.JPEG ) {
			extension = "jpg";
		} else if (request.type == Request.Type.PNG) {
			extension = "png";
			use_png = true;
		} else {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with unsupported image format");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}

		if( photo.jpeg == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveSingleImageNow called with no data");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}
		long time_s = System.currentTimeMillis();
		
		// unpack:
		final boolean using_camera2 = request.using_camera2;
		final Date current_date = request.current_date;
		final boolean store_location = request.store_location;
		final boolean store_geo_direction = request.store_geo_direction;
		
		int orientation = ExifInterface.ORIENTATION_UNDEFINED;
		if (photo.y != null) {
			orientation = photo.orientation;
		} else {
			orientation = getExifOrientation(photo.jpeg);
		}

		boolean success = false;
		final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
		StorageUtils storageUtils = main_activity.getStorageUtils();
		
		if (!in_background) main_activity.savingImage(true);

		boolean text_stamp = request.processing_settings.stamp_text.length() > 0;
		boolean need_bitmap = bitmap != null ||
			photo.y != null ||
			alloc != null ||
			request.processing_settings.do_auto_stabilise ||
			(request.allow_rotation && request.processing_settings.mirror) ||
			request.processing_settings.stamp ||
			text_stamp ||
			(request.image_capture_intent && request.image_capture_intent_uri == null) ||
			use_png;
	
		if( need_bitmap || request.processing_settings.adjust_levels != Prefs.ADJUST_LEVELS_NONE ) {
			int rotation = getPhotoRotation(orientation);

			if (photo.y != null && alloc == null) {
				alloc = loadYUV(photo, request.yuv_conversion);
			}

			if( request.processing_settings.adjust_levels != Prefs.ADJUST_LEVELS_NONE ) {
				RenderScript rs = main_activity.getRenderScript();
				ScriptC_auto_levels script = new ScriptC_auto_levels(rs);
				if (alloc == null) {
					bitmap = loadBitmap(photo, request.yuv_conversion, request.processing_settings.stamp || text_stamp);
					alloc = Allocation.createFromBitmap(rs, bitmap);
				}
				
				int [] max_min = new int[2];
				Allocation alloc_histogram = null;
				if (request.processing_settings.histogram_level == 0) {
					Allocation alloc_max_min = Allocation.createSized(rs, Element.U32(rs), 2);

					script.bind_max_min(alloc_max_min);
					script.invoke_init_max_min();

					if (request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS) {
						script.forEach_calc_min_max(alloc);
						if( MyDebug.LOG )
							Log.d(TAG, "time after script.forEach_calc_min_max: " + (System.currentTimeMillis() - time_s));
					} else if (request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS) {
						script.forEach_calc_max(alloc);
						if( MyDebug.LOG )
							Log.d(TAG, "time after script.forEach_calc_max: " + (System.currentTimeMillis() - time_s));
					}

					alloc_max_min.copyTo(max_min);
				} else {
					if( MyDebug.LOG )
						Log.d(TAG, "histogram_level = " + request.processing_settings.histogram_level);

					alloc_histogram = Allocation.createSized(rs, Element.U32(rs), 256);
					script.bind_histogram_array(alloc_histogram);
					script.invoke_init_histogram();
					script.forEach_histogram(alloc);
					if( MyDebug.LOG )
						Log.d(TAG, "time after script.forEach_histogram: " + (System.currentTimeMillis() - time_s));

					int [] histogram = new int[256];
					alloc_histogram.copyTo(histogram);
					if( MyDebug.LOG )
						Log.d(TAG, "time after alloc_histogram.copyTo: " + (System.currentTimeMillis() - time_s));
						
					int histogram_height = 0;
					for (int i = 0; i < 256; i++) {
						histogram_height = Math.max(histogram_height, histogram[i]);
					}
					if( MyDebug.LOG )
						Log.d(TAG, "histogram_height = " + histogram_height + ", time after calc: " + (System.currentTimeMillis() - time_s));

					int level = (int)(((double)histogram_height)*request.processing_settings.histogram_level);
					
					for (max_min[0] = 255; max_min[0] > 0; max_min[0]--) {
						if (histogram[max_min[0]] >= level)
							break;
					}
					
					if (request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS) {
						for (max_min[1] = 0; max_min[1] < 256; max_min[1]++) {
							if (histogram[max_min[1]] >= level)
								break;
						}
					}

					if( MyDebug.LOG )
						Log.d(TAG, "time after calc histogram levels: " + (System.currentTimeMillis() - time_s));
				}
				
				if( MyDebug.LOG )
					Log.d(TAG, "min: " + max_min[1] + ", max: " + max_min[0]);

				if (request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS && (max_min[1] == 0 || max_min[1] >= max_min[0]))
					request.processing_settings.adjust_levels = Prefs.ADJUST_LEVELS_LIGHTS;

				if ((request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_LIGHTS
						|| request.processing_settings.adjust_levels == Prefs.ADJUST_LEVELS_BOOST)
						&& (max_min[0] == 255 || max_min[0] == 0)) {
					if (!need_bitmap) {
						alloc = null;
						if (bitmap != null) {
							bitmap.recycle();
							bitmap = null;
						}
					}
				} else {
					switch (request.processing_settings.adjust_levels) {
						case Prefs.ADJUST_LEVELS_LIGHTS_SHADOWS:
							script.set_fDivider((float)(max_min[0]-max_min[1]) / 255.0f);
							script.set_fMin((float)max_min[1]);
							script.forEach_auto_ls(alloc, alloc);
							if( MyDebug.LOG )
								Log.d(TAG, "time after script.forEach_auto_ls: " + (System.currentTimeMillis() - time_s));
							break;
						case Prefs.ADJUST_LEVELS_LIGHTS:
							script.set_fDivider((float)max_min[0] / 255.0f);
							script.forEach_auto_l(alloc, alloc);
							if( MyDebug.LOG )
								Log.d(TAG, "time after script.forEach_auto_l: " + (System.currentTimeMillis() - time_s));
							break;
						case Prefs.ADJUST_LEVELS_BOOST:
							double divider = (double)max_min[0];
							double gamma = 1.0d/Math.sqrt(255.0d/(double)max_min[0]);
							if( MyDebug.LOG )
								Log.d(TAG, "gamma: " + gamma);

							int [] histogram = new int[256];
							double level_out;
							for (double level = 0; level < 256; level++) {
								level_out = Math.max(0.0d, Math.pow((level/divider), gamma)*255.0d);
								histogram[(int)level] = (int)Math.min(255.0d, level_out);
							}
							if (alloc_histogram == null) {
								alloc_histogram = Allocation.createSized(rs, Element.U32(rs), 256);
								script.bind_histogram_array(alloc_histogram);
							}
							alloc_histogram.copyFrom(histogram);
							script.forEach_apply_histogram(alloc, alloc);
							if( MyDebug.LOG )
								Log.d(TAG, "time after script.forEach_apply_histogram: " + (System.currentTimeMillis() - time_s));
							break;
					}
				}
			}

			if (alloc != null) {
				if( MyDebug.LOG )
					Log.d(TAG, "saving allocation to bitmap");

				Type type = alloc.getType();
				int width = type.getX();
				int height = type.getY();

				if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() != width || bitmap.getHeight() != height) {
					if( MyDebug.LOG ) {
						if (bitmap == null) {
							Log.d(TAG, "bitmap == null");
						} else {
							Log.d(TAG, "bitmap.isRecycled() == "+(bitmap.isRecycled() ? "true" : "false"));
						}
						Log.d(TAG, "creating new bitmap");
					}
					bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
					if( MyDebug.LOG )
						Log.d(TAG, "Save single image performance: time after creating new bitmap: " + (System.currentTimeMillis() - time_s));
				} else {
					if( MyDebug.LOG )
						Log.d(TAG, "using old bitmap");
				}
				alloc.copyTo(bitmap);

				if( MyDebug.LOG )
					Log.d(TAG, "Save single image performance: time after alloc.copyTo: " + (System.currentTimeMillis() - time_s));
			} else if( need_bitmap ) {
				bitmap = loadBitmap(photo, request.yuv_conversion,  request.processing_settings.stamp || text_stamp);
				if( MyDebug.LOG ) {
					Log.d(TAG, "Save single image performance: time after loadBitmap: " + (System.currentTimeMillis() - time_s));
				}
			}
			if (request.allow_rotation && bitmap != null && (rotation != 0 || request.processing_settings.mirror)) {
				bitmap = rotateBitmap(bitmap, rotation, request.processing_settings.mirror);
				if( MyDebug.LOG ) {
					Log.d(TAG, "Save single image performance: time after rotateBitmap: " + (System.currentTimeMillis() - time_s));
				}
				orientation = ExifInterface.ORIENTATION_NORMAL;
			} 
		}
		if (!request.allow_rotation && request.processing_settings.mirror) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "ExifInterface.TAG_ORIENTATION: " + orientation);
			}
			if (orientation == 0)
				orientation++;
			orientation++;
			if (orientation == 9)
				orientation = ExifInterface.ORIENTATION_TRANSPOSE;
		}
		if( request.processing_settings.do_auto_stabilise ) {
			boolean is_front_facing = request.processing_settings.mirror ? !request.is_front_facing : request.is_front_facing;
			bitmap = autoStabilise(photo, bitmap, request.processing_settings.level_angle, is_front_facing);
			if( MyDebug.LOG ) {
				Log.d(TAG, "Save single image performance: time after auto-stabilise: " + (System.currentTimeMillis() - time_s));
			}
		}
		bitmap = stampImage(request, photo, bitmap);
		if( MyDebug.LOG ) {
			Log.d(TAG, "Save single image performance: time after photostamp: " + (System.currentTimeMillis() - time_s));
		}

		File picFile = null;
		Uri saveUri = null; // if non-null, then picFile is a temporary file, which afterwards we should redirect to saveUri
		try {
			if( request.image_capture_intent ) {
				if( MyDebug.LOG )
					Log.d(TAG, "image_capture_intent");
				if( request.image_capture_intent_uri != null ) {
					// Save the bitmap to the specified URI (use a try/catch block)
					if( MyDebug.LOG )
						Log.d(TAG, "save to: " + request.image_capture_intent_uri);
					saveUri = request.image_capture_intent_uri;
				} else {
					// If the intent doesn't contain an URI, send the bitmap as a parcel
					// (it is a good idea to reduce its size to ~50k pixels before)
					if( MyDebug.LOG )
						Log.d(TAG, "sent to intent via parcel");
					if( bitmap != null ) {
						int width = bitmap.getWidth();
						int height = bitmap.getHeight();
						if( MyDebug.LOG ) {
							Log.d(TAG, "decoded bitmap size " + width + ", " + height);
							Log.d(TAG, "bitmap size: " + width*height*4);
						}
						final int small_size_c = 128;
						if( width > small_size_c ) {
							float scale = ((float)small_size_c)/(float)width;
							if( MyDebug.LOG )
								Log.d(TAG, "scale to " + scale);
							Matrix matrix = new Matrix();
							matrix.postScale(scale, scale);
							Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
							// careful, as new_bitmap is sometimes not a copy!
							if( new_bitmap != bitmap ) {
								bitmap.recycle();
								bitmap = new_bitmap;
							}
						}
					}
					if( MyDebug.LOG ) {
						if( bitmap != null ) {
							Log.d(TAG, "returned bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
							Log.d(TAG, "returned bitmap size: " + bitmap.getWidth()*bitmap.getHeight()*4);
						}
						else {
							Log.e(TAG, "no bitmap created");
						}
					}
					if( bitmap != null )
						main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
					main_activity.finish();
				}
			}
			else if( storageUtils.isUsingSAF() ) {
				saveUri = storageUtils.createOutputMediaFileSAF(request.prefix, filename_suffix, extension, current_date);
			}
			else {
				picFile = storageUtils.createOutputMediaFile(request.prefix, filename_suffix, extension, current_date);
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + picFile.getAbsolutePath());
			}
			
			if( saveUri != null && picFile == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveUri: " + saveUri);
				picFile = File.createTempFile("picFile", extension, main_activity.getCacheDir());
				if( MyDebug.LOG )
					Log.d(TAG, "temp picFile: " + picFile.getAbsolutePath());
			}
			
			if( picFile != null ) {
				long time_before_saving = System.currentTimeMillis();
				OutputStream outputStream = new FileOutputStream(picFile);
				try {
					if( bitmap != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "compress bitmap, quality " + request.image_quality);
						bitmap.compress(use_png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, request.image_quality, outputStream);
					}
					else {
						outputStream.write(photo.jpeg);
					}
				}
				finally {
					outputStream.close();
				}
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow saved photo");
				if( MyDebug.LOG ) {
					Log.d(TAG, "Save single image performance: time after saving photo: " + (System.currentTimeMillis() - time_s) + ", time for saving: " + (System.currentTimeMillis() - time_before_saving));
				}

				if( saveUri == null ) { // if saveUri is non-null, then we haven't succeeded until we've copied to the saveUri
					success = true;
				}
				if( picFile != null ) {
					if (!use_png) {
						if( bitmap != null ) {
							// need to update EXIF data!
							if( MyDebug.LOG )
								Log.d(TAG, "set Exif tags from data");
							setExifFromData(request, photo.jpeg, orientation, picFile);
						} else {
							try {
								ExifInterface exif = new ExifInterface(picFile.getAbsolutePath());
								if( store_geo_direction ) {
									if( MyDebug.LOG )
										Log.d(TAG, "add GPS direction exif info");

									modifyExif(exif, using_camera2, current_date, store_location, store_geo_direction, request.geo_direction);

									if( MyDebug.LOG ) {
										Log.d(TAG, "Save single image performance: time after adding GPS direction exif info: " + (System.currentTimeMillis() - time_s));
									}
								}
								else if( needGPSTimestampHack(using_camera2, store_location) ) {
									if( MyDebug.LOG )
										Log.d(TAG, "remove GPS timestamp hack");

									fixGPSTimestamp(exif, current_date);

									if( MyDebug.LOG ) {
										Log.d(TAG, "Save single image performance: time after removing GPS timestamp hack: " + (System.currentTimeMillis() - time_s));
									}
								}
								if (!request.allow_rotation && request.processing_settings.mirror)
									exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
								setMetadata(exif, request.metadata);
								exif.saveAttributes();
							}
							catch(NoClassDefFoundError exception) {
								// have had Google Play crashes from new ExifInterface() elsewhere for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn), so also catch here just in case
								if( MyDebug.LOG )
									Log.e(TAG, "exif orientation NoClassDefFoundError");
								exception.printStackTrace();
							}
						}
					}

					if( saveUri == null ) {
						// broadcast for SAF is done later, when we've actually written out the file
						storageUtils.broadcastFile(picFile, true, false, update_thumbnail);
						main_activity.test_last_saved_image = picFile.getAbsolutePath();
						
						if (request.metadata.comment_as_file && request.metadata.comment != null && request.metadata.comment.length() > 0) {
							OutputStream s = new FileOutputStream(storageUtils.createOutputMediaFile(request.prefix, filename_suffix, "txt", current_date));
							if (s != null) {
								OutputStreamWriter w = new OutputStreamWriter(s);
								w.write(request.metadata.comment);
								w.close();
								
								s.close();
							}
						}
					}
				}
				if( request.image_capture_intent ) {
					if( MyDebug.LOG )
						Log.d(TAG, "finish activity due to being called from intent");
					main_activity.setResult(Activity.RESULT_OK);
					main_activity.finish();
				}
				if( storageUtils.isUsingSAF() ) {
					// most Gallery apps don't seem to recognise the SAF-format Uri, so just clear the field
					storageUtils.clearLastMediaScanned();
				}

				if( saveUri != null ) {
					copyFileToUri(main_activity, saveUri, picFile);
					final long size = picFile.length();

					success = true;
					/* We still need to broadcastFile for SAF for two reasons:
						1. To call storageUtils.announceUri() to broadcast NEW_PICTURE etc.
						   Whilst in theory we could do this directly, it seems external apps that use such broadcasts typically
						   won't know what to do with a SAF based Uri (e.g, Owncloud crashes!) so better to broadcast the Uri
						   corresponding to the real file, if it exists.
						2. Whilst the new file seems to be known by external apps such as Gallery without having to call media
						   scanner, I've had reports this doesn't happen when saving to external SD cards. So better to explicitly
						   scan.
					*/
					File real_file = storageUtils.getFileFromDocumentUriSAF(saveUri, false);
					if( MyDebug.LOG )
						Log.d(TAG, "real_file: " + real_file);
					if( real_file != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "broadcast file");
						storageUtils.broadcastFile(real_file, true, false, true, size);
						main_activity.test_last_saved_image = real_file.getAbsolutePath();
					}
					else if( !request.image_capture_intent ) {
						if( MyDebug.LOG )
							Log.d(TAG, "announce SAF uri");
						// announce the SAF Uri
						// (shouldn't do this for a capture intent - e.g., causes crash when calling from Google Keep)
						storageUtils.announceUri(saveUri, true, false);
					}

					if (request.metadata.comment_as_file && request.metadata.comment != null && request.metadata.comment.length() > 0) {
						OutputStream s = main_activity.getContentResolver().openOutputStream(storageUtils.createOutputMediaFileSAF(request.prefix, filename_suffix, "txt", current_date));
						if (s != null) {
							OutputStreamWriter w = new OutputStreamWriter(s);
							w.write(request.metadata.comment);
							w.close();
							
							s.close();
						}
					}
				}
			}
		}
		catch(FileNotFoundException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "File not found: " + e.getMessage());
			e.printStackTrace();
			Utils.showToast(null, R.string.failed_to_save_photo);
		}
		catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "I/O error writing file: " + e.getMessage());
			e.printStackTrace();
			Utils.showToast(null, R.string.failed_to_save_photo);
		}
		catch(SecurityException e) {
			// received security exception from copyFileToUri()->openOutputStream() from Google Play
			if( MyDebug.LOG )
				Log.e(TAG, "security exception writing file: " + e.getMessage());
			e.printStackTrace();
			Utils.showToast(null, R.string.failed_to_save_photo);
		}

		if( success && saveUri == null ) {
			applicationInterface.addLastImage(picFile, share_image);
		}
		else if( success && storageUtils.isUsingSAF() ){
			applicationInterface.addLastImageSAF(saveUri, share_image);
		}

		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
		if( success && main_activity.getPreview().getCameraController() != null && update_thumbnail && request.sample_factor != 0 ) {
			// update thumbnail - this should be done after restarting preview, so that the preview is started asap
			CameraController.Size size = main_activity.getPreview().getCameraController().getPictureSize();
			int ratio = (int) Math.ceil((double) size.width / main_activity.getPreview().getView().getWidth());
			int sample_size = Integer.highestOneBit(ratio);
			sample_size *= request.sample_factor;
			if( MyDebug.LOG ) {
				Log.d(TAG, "	picture width: " + size.width);
				Log.d(TAG, "	preview width: " + main_activity.getPreview().getView().getWidth());
				Log.d(TAG, "	ratio		: " + ratio);
				Log.d(TAG, "	sample_size  : " + sample_size);
			}
			Bitmap thumbnail;
			int rotation = getPhotoRotation(orientation);
			boolean mirror = (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
					orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
					orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
					orientation == ExifInterface.ORIENTATION_TRANSPOSE);

			if( bitmap == null ) {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inMutable = false;
				if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
					// setting is ignored in Android 5 onwards
					options.inPurgeable = true;
				}
				options.inSampleSize = sample_size;
				thumbnail = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.length, options);
				// now get the rotation from the Exif data
				if( MyDebug.LOG )
					Log.d(TAG, "rotate thumbnail for exif tags?");
				if (rotation != 0 || mirror) {
					thumbnail = rotateBitmap(thumbnail, rotation, mirror);
				}
			}
			else {
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				Matrix matrix = new Matrix();
				float scale = 1.0f / (float)sample_size;
				matrix.postScale(scale, scale);
				if( MyDebug.LOG )
					Log.d(TAG, "	scale: " + scale);
				if( width > 0 && height > 0 ) {
					thumbnail = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
					if (rotation != 0 || mirror) {
						thumbnail = rotateBitmap(thumbnail, rotation, mirror);
					}
				}
				else {
					// received IllegalArgumentException on Google Play from Bitmap.createBitmap; documentation suggests this
					// means width or height are 0
					if( MyDebug.LOG )
						Log.e(TAG, "bitmap has zero width or height?!");
					thumbnail = null;
				}
				// don't need to rotate for exif, as we already did that when creating the bitmap
			}
			if( thumbnail == null ) {
				// received crashes on Google Play suggesting that thumbnail could not be created
				if( MyDebug.LOG )
					Log.e(TAG, "failed to create thumbnail bitmap");
			}
			else {
				final Bitmap thumbnail_f = thumbnail;
				main_activity.runOnUiThread(new Runnable() {
					public void run() {
						applicationInterface.updateThumbnail(thumbnail_f, false);
					}
				});
				if( MyDebug.LOG ) {
					Log.d(TAG, "Save single image performance: time after creating thumbnail: " + (System.currentTimeMillis() - time_s));
				}
			}
		}

		if( bitmap != null ) {
			bitmap.recycle();
		}

		if( picFile != null && saveUri != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "delete temp picFile: " + picFile);
			if( !picFile.delete() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to delete temp picFile: " + picFile);
			}
		}
		
		System.gc();
		
		if (!in_background) main_activity.savingImage(false);

		if( MyDebug.LOG ) {
			Log.d(TAG, "Save single image performance: total time: " + (System.currentTimeMillis() - time_s));
		}
		return success;
	}

	private void setExifFromData(final Request request, byte [] data, int orientation, File to_file) throws IOException {
		if( MyDebug.LOG ) {
			Log.d(TAG, "setExifFromData");
			Log.d(TAG, "to_file: " + to_file);
		}
		InputStream inputStream = null;
		try {
			inputStream = new ByteArrayInputStream(data);
			ExifInterface exif = new ExifInterface(inputStream);
			ExifInterface exif_new = new ExifInterface(to_file.getAbsolutePath());
			exif_new.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
			setExif(request, exif, exif_new);
		}
		finally {
			if( inputStream != null ) {
				inputStream.close();
			}
		}
	}

	/** Transfers exif tags from exif to exif_new.
	 *  Note that we use several ExifInterface tags that are now deprecated in API level 23 and 24. These are replaced with new tags that have
	 *  the same string value (e.g., TAG_APERTURE replaced with TAG_F_NUMBER, but both have value "FNumber"). We use the deprecated versions
	 *  to avoid complicating the code (we'd still have to read the deprecated values for older devices).
	 */
	@SuppressWarnings("deprecation")
	private void setExif(final Request request, ExifInterface exif, ExifInterface exif_new) throws IOException {
		if( MyDebug.LOG )
			Log.d(TAG, "setExif");

		if( MyDebug.LOG )
			Log.d(TAG, "read back EXIF data");
		String exif_aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER);
		String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
		String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
		String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
		String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
		String exif_gps_altitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE);
		String exif_gps_altitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF);
		String exif_gps_datestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
		String exif_gps_latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
		String exif_gps_latitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
		String exif_gps_longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
		String exif_gps_longitude_ref = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
		String exif_gps_processing_method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD);
		String exif_gps_timestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
		// leave width/height, as this may have changed! similarly TAG_IMAGE_LENGTH?
		String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS);
		String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
		String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
		// leave orientation - since we rotate bitmaps to account for orientation, we don't want to write it to the saved image!
		String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

		String exif_datetime_digitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
		String exif_subsec_time = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME);
		String exif_subsec_time_dig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED);
		String exif_subsec_time_orig = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL);

		String exif_aperture_value = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE);
		String exif_brightness_value = exif.getAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE);
		String exif_cfa_pattern = exif.getAttribute(ExifInterface.TAG_CFA_PATTERN);
		String exif_color_space = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE);
		String exif_components_configuration = exif.getAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION);
		String exif_compressed_bits_per_pixel = exif.getAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL);
		String exif_compression = exif.getAttribute(ExifInterface.TAG_COMPRESSION);
		String exif_contrast = exif.getAttribute(ExifInterface.TAG_CONTRAST);
		String exif_datetime_original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
		String exif_device_setting_description = exif.getAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION);
		String exif_digital_zoom_ratio = exif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO);
		// unclear if we should transfer TAG_EXIF_VERSION - don't want to risk conficting with whatever ExifInterface writes itself
		String exif_exposure_bias_value = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE);
		String exif_exposure_index = exif.getAttribute(ExifInterface.TAG_EXPOSURE_INDEX);
		String exif_exposure_mode = exif.getAttribute(ExifInterface.TAG_EXPOSURE_MODE);
		String exif_exposure_program = exif.getAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM);
		String exif_flash_energy = exif.getAttribute(ExifInterface.TAG_FLASH_ENERGY);
		String exif_focal_length_in_35mm_film = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
		String exif_focal_plane_resolution_unit = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT);
		String exif_focal_plane_x_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION);
		String exif_focal_plane_y_resolution = exif.getAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION);
		// TAG_F_NUMBER same as TAG_APERTURE
		String exif_gain_control = exif.getAttribute(ExifInterface.TAG_GAIN_CONTROL);
		String exif_gps_area_information = exif.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION);
		// don't care about TAG_GPS_DEST_*
		String exif_gps_differential = exif.getAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL);
		String exif_gps_dop = exif.getAttribute(ExifInterface.TAG_GPS_DOP);
		// TAG_GPS_IMG_DIRECTION, TAG_GPS_IMG_DIRECTION_REF won't have been recorded in the image yet - we add this ourselves in setGPSDirectionExif()
		// don't care about TAG_GPS_MAP_DATUM?
		String exif_gps_measure_mode = exif.getAttribute(ExifInterface.TAG_GPS_MEASURE_MODE);
		// don't care about TAG_GPS_SATELLITES?
		// don't care about TAG_GPS_SPEED, TAG_GPS_SPEED_REF, TAG_GPS_STATUS, TAG_GPS_TRACK, TAG_GPS_TRACK_REF, TAG_GPS_VERSION_ID
		String exif_image_description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION);
		// unclear what TAG_IMAGE_UNIQUE_ID, TAG_INTEROPERABILITY_INDEX are
		// TAG_ISO_SPEED_RATINGS same as TAG_ISO
		// skip TAG_JPEG_INTERCHANGE_FORMAT, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
		String exif_light_source = exif.getAttribute(ExifInterface.TAG_LIGHT_SOURCE);
		String exif_maker_note = exif.getAttribute(ExifInterface.TAG_MAKER_NOTE);
		String exif_max_aperture_value = exif.getAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE);
		String exif_metering_mode = exif.getAttribute(ExifInterface.TAG_METERING_MODE);
		String exif_oecf = exif.getAttribute(ExifInterface.TAG_OECF);
		String exif_photometric_interpretation = exif.getAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION);
		// skip PIXEL_X/Y_DIMENSION, as it may have changed
		// don't care about TAG_PLANAR_CONFIGURATION
		// don't care about TAG_PRIMARY_CHROMATICITIES, TAG_REFERENCE_BLACK_WHITE?
		// don't care about TAG_RESOLUTION_UNIT
		// TAG_ROWS_PER_STRIP may have changed (if it's even relevant)
		// TAG_SAMPLES_PER_PIXEL may no longer be relevant if we've changed the image dimensions?
		String exif_saturation = exif.getAttribute(ExifInterface.TAG_SATURATION);
		String exif_scene_capture_type = exif.getAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE);
		String exif_scene_type = exif.getAttribute(ExifInterface.TAG_SCENE_TYPE);
		String exif_sensing_method = exif.getAttribute(ExifInterface.TAG_SENSING_METHOD);
		String exif_sharpness = exif.getAttribute(ExifInterface.TAG_SHARPNESS);
		String exif_shutter_speed_value = exif.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE);
		// don't care about TAG_SPATIAL_FREQUENCY_RESPONSE, TAG_SPECTRAL_SENSITIVITY?
		// don't care about TAG_STRIP_*
		// don't care about TAG_SUBJECT_*
		// TAG_SUBSEC_TIME_DIGITIZED same as TAG_SUBSEC_TIME_DIG
		// TAG_SUBSEC_TIME_ORIGINAL same as TAG_SUBSEC_TIME_ORIG
		// TAG_THUMBNAIL_IMAGE_* may have changed
		// don't care about TAG_TRANSFER_FUNCTION?
		String exif_user_comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
		// don't care about TAG_WHITE_POINT?
		// TAG_X_RESOLUTION may have changed?
		// don't care about TAG_Y_*?

		if( MyDebug.LOG )
			Log.d(TAG, "now write new EXIF data");
		if( exif_aperture != null )
			exif_new.setAttribute(ExifInterface.TAG_F_NUMBER, exif_aperture);
		if( exif_datetime != null )
			exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
		if( exif_exposure_time != null )
			exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
		if( exif_flash != null )
			exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
		if( exif_focal_length != null )
			exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
		if( exif_gps_altitude != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, exif_gps_altitude);
		if( exif_gps_altitude_ref != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, exif_gps_altitude_ref);
		if( exif_gps_datestamp != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, exif_gps_datestamp);
		if( exif_gps_latitude != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exif_gps_latitude);
		if( exif_gps_latitude_ref != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exif_gps_latitude_ref);
		if( exif_gps_longitude != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exif_gps_longitude);
		if( exif_gps_longitude_ref != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exif_gps_longitude_ref);
		if( exif_gps_processing_method != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, exif_gps_processing_method);
		if( exif_gps_timestamp != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, exif_gps_timestamp);
		if( exif_iso != null )
			exif_new.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, exif_iso);
		if( exif_make != null )
			exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
		if( exif_model != null )
			exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
		if( exif_white_balance != null )
			exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);

		if( exif_datetime_digitized != null )
			exif_new.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exif_datetime_digitized);
		if( exif_subsec_time != null )
			exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME, exif_subsec_time);
		if( exif_subsec_time_dig != null )
			exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, exif_subsec_time_dig);
		if( exif_subsec_time_orig != null )
			exif_new.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, exif_subsec_time_orig);

		if( exif_aperture_value != null )
			exif_new.setAttribute(ExifInterface.TAG_APERTURE_VALUE, exif_aperture_value);
		if( exif_brightness_value != null )
			exif_new.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, exif_brightness_value);
		if( exif_cfa_pattern != null )
			exif_new.setAttribute(ExifInterface.TAG_CFA_PATTERN, exif_cfa_pattern);
		if( exif_color_space != null )
			exif_new.setAttribute(ExifInterface.TAG_COLOR_SPACE, exif_color_space);
		if( exif_components_configuration != null )
			exif_new.setAttribute(ExifInterface.TAG_COMPONENTS_CONFIGURATION, exif_components_configuration);
		if( exif_compressed_bits_per_pixel != null )
			exif_new.setAttribute(ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL, exif_compressed_bits_per_pixel);
		if( exif_compression != null )
			exif_new.setAttribute(ExifInterface.TAG_COMPRESSION, exif_compression);
		if( exif_contrast != null )
			exif_new.setAttribute(ExifInterface.TAG_CONTRAST, exif_contrast);
		if( exif_datetime_original != null )
			exif_new.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exif_datetime_original);
		if( exif_device_setting_description != null )
			exif_new.setAttribute(ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION, exif_device_setting_description);
		if( exif_digital_zoom_ratio != null )
			exif_new.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, exif_digital_zoom_ratio);
		if( exif_exposure_bias_value != null )
			exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, exif_exposure_bias_value);
		if( exif_exposure_index != null )
			exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_INDEX, exif_exposure_index);
		if( exif_exposure_mode != null )
			exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, exif_exposure_mode);
		if( exif_exposure_program != null )
			exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, exif_exposure_program);
		if( exif_flash_energy != null )
			exif_new.setAttribute(ExifInterface.TAG_FLASH_ENERGY, exif_flash_energy);
		if( exif_focal_length_in_35mm_film != null )
			exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, exif_focal_length_in_35mm_film);
		if( exif_focal_plane_resolution_unit != null )
			exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT, exif_focal_plane_resolution_unit);
		if( exif_focal_plane_x_resolution != null )
			exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION, exif_focal_plane_x_resolution);
		if( exif_focal_plane_y_resolution != null )
			exif_new.setAttribute(ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION, exif_focal_plane_y_resolution);
		if( exif_gain_control != null )
			exif_new.setAttribute(ExifInterface.TAG_GAIN_CONTROL, exif_gain_control);
		if( exif_gps_area_information != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, exif_gps_area_information);
		if( exif_gps_differential != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_DIFFERENTIAL, exif_gps_differential);
		if( exif_gps_dop != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_DOP, exif_gps_dop);
		if( exif_gps_measure_mode != null )
			exif_new.setAttribute(ExifInterface.TAG_GPS_MEASURE_MODE, exif_gps_measure_mode);
		if( exif_image_description != null )
			exif_new.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exif_image_description);
		if( exif_light_source != null )
			exif_new.setAttribute(ExifInterface.TAG_LIGHT_SOURCE, exif_light_source);
		if( exif_maker_note != null )
			exif_new.setAttribute(ExifInterface.TAG_MAKER_NOTE, exif_maker_note);
		if( exif_max_aperture_value != null )
			exif_new.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, exif_max_aperture_value);
		if( exif_metering_mode != null )
			exif_new.setAttribute(ExifInterface.TAG_METERING_MODE, exif_metering_mode);
		if( exif_oecf != null )
			exif_new.setAttribute(ExifInterface.TAG_OECF, exif_oecf);
		if( exif_photometric_interpretation != null )
			exif_new.setAttribute(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, exif_photometric_interpretation);
		if( exif_saturation != null )
			exif_new.setAttribute(ExifInterface.TAG_SATURATION, exif_saturation);
		if( exif_scene_capture_type != null )
			exif_new.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, exif_scene_capture_type);
		if( exif_scene_type != null )
			exif_new.setAttribute(ExifInterface.TAG_SCENE_TYPE, exif_scene_type);
		if( exif_sensing_method != null )
			exif_new.setAttribute(ExifInterface.TAG_SENSING_METHOD, exif_sensing_method);
		if( exif_sharpness != null )
			exif_new.setAttribute(ExifInterface.TAG_SHARPNESS, exif_sharpness);
		if( exif_shutter_speed_value != null )
			exif_new.setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, exif_shutter_speed_value);
		if( exif_user_comment != null )
			exif_new.setAttribute(ExifInterface.TAG_USER_COMMENT, exif_user_comment);

		modifyExif(exif_new, request.using_camera2, request.current_date, request.store_location, request.store_geo_direction, request.geo_direction);
		setMetadata(exif_new, request.metadata);
		exif_new.saveAttributes();
	}

	/** May be run in saver thread or picture callback thread (depending on whether running in background).
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private boolean saveImageNowRaw(DngCreator dngCreator, Image image, String prefix, Date current_date, boolean in_background) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveImageNowRaw");

		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
			if( MyDebug.LOG )
				Log.e(TAG, "RAW requires LOLLIPOP or higher");
			return false;
		}
		StorageUtils storageUtils = main_activity.getStorageUtils();
		boolean success = false;

		if (!in_background) main_activity.savingImage(true);

		OutputStream output = null;
		try {
			File picFile = null;
			Uri saveUri = null;

			if( storageUtils.isUsingSAF() ) {
				saveUri = storageUtils.createOutputMediaFileSAF(prefix, "", "dng", current_date);
				if( MyDebug.LOG )
					Log.d(TAG, "saveUri: " + saveUri);
				// When using SAF, we don't save to a temp file first (unlike for JPEGs). Firstly we don't need to modify Exif, so don't
				// need a real file; secondly copying to a temp file is much slower for RAW.
			}
			else {
				picFile = storageUtils.createOutputMediaFile(prefix, "", "dng", current_date);
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + picFile.getAbsolutePath());
			}

			if( picFile != null ) {
				output = new FileOutputStream(picFile);
			}
			else {
				output = main_activity.getContentResolver().openOutputStream(saveUri);
			}
			dngCreator.writeImage(output, image);
			image.close();
			image = null;
			dngCreator.close();
			dngCreator = null;
			output.close();
			output = null;

			if( saveUri == null ) {
				success = true;
				storageUtils.broadcastFile(picFile, true, false, false);
			}
			else {
				success = true;
				File real_file = storageUtils.getFileFromDocumentUriSAF(saveUri, false);
				if( MyDebug.LOG )
					Log.d(TAG, "real_file: " + real_file);
				if( real_file != null ) {
					if( MyDebug.LOG )
						Log.d(TAG, "broadcast file");
					storageUtils.broadcastFile(real_file, true, false, false);
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "announce SAF uri");
					storageUtils.announceUri(saveUri, true, false);
				}
			}

			MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
			if( success && saveUri == null ) {
				applicationInterface.addLastImage(picFile, false);
			}
			else if( success && storageUtils.isUsingSAF() ){
				applicationInterface.addLastImageSAF(saveUri, false);
			}

		}
		catch(FileNotFoundException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "File not found: " + e.getMessage());
			e.printStackTrace();
			Utils.showToast(null, R.string.failed_to_save_photo_raw);
		}
		catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "ioexception writing raw image file");
			e.printStackTrace();
			Utils.showToast(null, R.string.failed_to_save_photo_raw);
		}
		finally {
			if( output != null ) {
				try {
					output.close();
				}
				catch(IOException e) {
					if( MyDebug.LOG )
						Log.e(TAG, "ioexception closing raw output");
					e.printStackTrace();
				}
			}
			if( image != null ) {
				image.close();
			}
			if( dngCreator != null ) {
				dngCreator.close();
			}
		}

		System.gc();

		if (!in_background) main_activity.savingImage(false);

		return success;
	}
	
	private int getExifOrientation(byte [] jpeg) {
		if( MyDebug.LOG )
			Log.d(TAG, "getPhotoRotation");
		InputStream inputStream = null;
		int exif_orientation = ExifInterface.ORIENTATION_UNDEFINED;
		try {
			ExifInterface exif;

			inputStream = new ByteArrayInputStream(jpeg);
			exif = new ExifInterface(inputStream);

			exif_orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

			if( MyDebug.LOG )
				Log.d(TAG, "	exif orientation: " + exif_orientation);
		}
		catch(IOException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "exif orientation ioexception");
			exception.printStackTrace();
		}
		catch(NoClassDefFoundError exception) {
			// have had Google Play crashes from new ExifInterface() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
			if( MyDebug.LOG )
				Log.e(TAG, "exif orientation NoClassDefFoundError");
			exception.printStackTrace();
		}
		finally {
			if( inputStream != null ) {
				try {
					inputStream.close();
				}
				catch(IOException e) {
					e.printStackTrace();
				}
				inputStream = null;
			}
		}
		return exif_orientation;
	}
	
	private int getPhotoRotation(int exif_orientation) {
		int rotation = 0;
		switch (exif_orientation) {
			case ExifInterface.ORIENTATION_ROTATE_180:
			case ExifInterface.ORIENTATION_FLIP_VERTICAL:
				rotation = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_90:
			case ExifInterface.ORIENTATION_TRANSVERSE:
				rotation = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
			case ExifInterface.ORIENTATION_TRANSPOSE:
				rotation = 270;
				break;
		}
		if( MyDebug.LOG )
			Log.d(TAG, "	rotation: " + rotation);

		return rotation;
	}
	
	private Bitmap rotateBitmap(Bitmap bitmap, int rotation, boolean mirror) {
		if( MyDebug.LOG )
			Log.d(TAG, "	need to rotate bitmap due to exif orientation tag");
		Matrix m = new Matrix();
		if (rotation == 180) {
			if (mirror) m.postScale(1.0f, -1.0f);
			else m.postScale(-1.0f, -1.0f);
		} else {
			m.setRotate(rotation);
			if (mirror) m.postScale(-1.0f, 1.0f);
		}
		Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
		if( rotated_bitmap != bitmap ) {
			bitmap.recycle();
			bitmap = rotated_bitmap;
		}
		
		return bitmap;
	}
	
	private Allocation rotateAllocation(Allocation alloc, int rotation, boolean mirror) {
		if (rotation == 0 && !mirror)
			return alloc;

		RenderScript rs = main_activity.getRenderScript();

		ScriptC_rotate script = new ScriptC_rotate(rs);

		Type type = alloc.getType();
		int width;
		int height;
		
		if (rotation == 90 || rotation == 270) {
			width = type.getY();
			height = type.getX();
			
			byte[] a = new byte[width*height*4];
			alloc.copyTo(a);
			
			Type.Builder builder = new Type.Builder(rs, Element.RGBA_8888(rs));
			builder.setX(width*height);
			alloc = Allocation.createTyped(rs, builder.create(), Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
			alloc.copyFrom(a);
			alloc.syncAll(Allocation.USAGE_SCRIPT);
		} else {
			width = type.getX();
			height = type.getY();
		}

		Type.Builder builder = new Type.Builder(rs, Element.RGBA_8888(rs));
		builder.setX(width);
		builder.setY(height);
		Allocation alloc_new = Allocation.createTyped(rs, builder.create());
		
		script.set_alloc_in(alloc);
		script.set_max_x(width-1);
		script.set_max_y(height-1);
		
		if (mirror) {
			switch (rotation) {
				case 90:
					script.forEach_rotate90_mirror(alloc_new);
					break;
				case 180:
					script.forEach_rotate180_mirror(alloc_new);
					break;
				case 270:
					script.forEach_rotate270_mirror(alloc_new);
					break;
				default:
					script.forEach_mirror(alloc_new);
			}
		} else {
			switch (rotation) {
				case 90:
					script.forEach_rotate90(alloc_new);
					break;
				case 180:
					script.forEach_rotate180(alloc_new);
					break;
				default:
					script.forEach_rotate270(alloc_new);
			}
		}
		
		return alloc_new;
	}

	/** Makes various modifications to the exif data, if necessary.
	 */
	private void modifyExif(ExifInterface exif, boolean using_camera2, Date current_date, boolean store_location, boolean store_geo_direction, double geo_direction) {
		setGPSDirectionExif(exif, store_geo_direction, geo_direction);
		setDateTimeExif(exif);
		if( needGPSTimestampHack(using_camera2, store_location) ) {
			fixGPSTimestamp(exif, current_date);
		}
	}

	private void setMetadata(ExifInterface exif, Metadata data) {
		exif.setAttribute(ExifInterface.TAG_SOFTWARE, software_name);
		if (data != null) {
			exif.setCharset(main_activity.getResources().getString(R.string.charset));
			if (data.author != null && data.author.length() > 0) {
				exif.setAttribute(ExifInterface.TAG_ARTIST, data.author);
				exif.setAttribute(ExifInterface.TAG_COPYRIGHT, data.author);
			}
			if (!data.comment_as_file && data.comment != null && data.comment.length() > 0) {
				exif.setComment(data.comment);
			}
		}
	}

	private void setGPSDirectionExif(ExifInterface exif, boolean store_geo_direction, double geo_direction) {
		if( store_geo_direction ) {
			float geo_angle = (float)Math.toDegrees(geo_direction);
			if( geo_angle < 0.0f ) {
				geo_angle += 360.0f;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "save geo_angle: " + geo_angle);
			// see http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/GPS.html
			String GPSImgDirection_string = Math.round(geo_angle*100) + "/100";
			if( MyDebug.LOG )
				Log.d(TAG, "GPSImgDirection_string: " + GPSImgDirection_string);
		   	exif.setAttribute(TAG_GPS_IMG_DIRECTION, GPSImgDirection_string);
		   	exif.setAttribute(TAG_GPS_IMG_DIRECTION_REF, "M");
		}
	}

	private void setDateTimeExif(ExifInterface exif) {
		String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
		if( exif_datetime != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "write datetime tags: " + exif_datetime);
			exif.setAttribute(TAG_DATETIME_ORIGINAL, exif_datetime);
			exif.setAttribute(TAG_DATETIME_DIGITIZED, exif_datetime);
		}
	}
	
	private void fixGPSTimestamp(ExifInterface exif, Date current_date) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "fixGPSTimestamp");
			Log.d(TAG, "current datestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP));
			Log.d(TAG, "current timestamp: " + exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP));
			Log.d(TAG, "current datetime: " + exif.getAttribute(ExifInterface.TAG_DATETIME));
		}
		// Hack: Problem on Camera2 API (at least on Nexus 6) that if geotagging is enabled, then the resultant image has incorrect Exif TAG_GPS_DATESTAMP and TAG_GPS_TIMESTAMP (GPSDateStamp) set (date tends to be around 2038 - possibly a driver bug of casting long to int?).
		// This causes problems when viewing with Gallery apps (e.g., Gallery ICS; Google Photos seems fine however), as they show this incorrect date.
		// Update: Before v1.34 this was "fixed" by calling: exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, Long.toString(System.currentTimeMillis()));
		// However this stopped working on or before 20161006. This wasn't a change in Open Camera (whilst this was working fine in
		// 1.33 when I released it, the bug had come back when I retested that version) and I'm not sure how this ever worked, since
		// TAG_GPS_TIMESTAMP is meant to be a string such "21:45:23", and not the number of ms since 1970 - possibly it wasn't really
		// working , and was simply invalidating it such that Gallery then fell back to looking elsewhere for the datetime?
		// So now hopefully fixed properly...
		// Note, this problem also occurs on OnePlus 3T and Gallery ICS, if we don't have this function called
		SimpleDateFormat date_fmt = new SimpleDateFormat("yyyy:MM:dd", Locale.US);
		date_fmt.setTimeZone(TimeZone.getTimeZone("UTC")); // needs to be UTC time
		String datestamp = date_fmt.format(current_date);

		SimpleDateFormat time_fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
		time_fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
		String timestamp = time_fmt.format(current_date);

		if( MyDebug.LOG ) {
			Log.d(TAG, "datestamp: " + datestamp);
			Log.d(TAG, "timestamp: " + timestamp);
		}
		exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, datestamp);
		exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, timestamp);
	
		if( MyDebug.LOG )
			Log.d(TAG, "fixGPSTimestamp exit");
	}
	
	private boolean needGPSTimestampHack(boolean using_camera2, boolean store_location) {
		if( using_camera2 ) {
			return store_location;
		}
		return false;
	}

	/** Reads from picFile and writes the contents to saveUri.
	 */
	private void copyFileToUri(Context context, Uri saveUri, File picFile) throws IOException {
		if( MyDebug.LOG ) {
			Log.d(TAG, "copyFileToUri");
			Log.d(TAG, "saveUri: " + saveUri);
			Log.d(TAG, "picFile: " + saveUri);
		}
		InputStream inputStream = null;
		OutputStream realOutputStream = null;
		try {
			inputStream = new FileInputStream(picFile);
			realOutputStream = context.getContentResolver().openOutputStream(saveUri);
			// Transfer bytes from in to out
			byte [] buffer = new byte[1024];
			int len;
			while( (len = inputStream.read(buffer)) > 0 ) {
				realOutputStream.write(buffer, 0, len);
			}
		}
		finally {
			if( inputStream != null ) {
				inputStream.close();
			}
			if( realOutputStream != null ) {
				realOutputStream.close();
			}
		}
	}
	
	// for testing:
	
	HDRProcessor getHDRProcessor() {
		return hdrProcessor;
	}
}
