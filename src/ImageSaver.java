package com.caddish_hedgehog.hedgecam2;

import com.caddish_hedgehog.hedgecam2.CameraController.CameraController;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.support.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.renderscript.Allocation;
import android.support.annotation.RequiresApi;
import android.util.Log;

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
	private final NRProcessor nrProcessor;
	
	private final String software_name;

	/* We use a separate count n_images_to_save, rather than just relying on the queue size, so we can take() an image from queue,
	 * but only decrement the count when we've finished saving the image.
	 * In general, n_images_to_save represents the number of images still to process, including ones currently being processed.
	 * Therefore we should always have n_images_to_save >= queue.size().
	 */
	private int n_images_to_save = 0;
	private final List<Request> queue = new ArrayList<>(); // since we remove from the queue and then process in the saver thread, in practice the number of background photos - including the one being processed - is one more than the length of this queue
	private volatile Thread saving_thread;
	
	static class Request {
		enum Type {
			JPEG,
			RAW,
			DUMMY
		}
		Type type = Type.JPEG;
		final Prefs.PhotoMode photo_mode; // for jpeg
		enum SaveBase {
			SAVEBASE_NONE,
			SAVEBASE_FIRST,
			SAVEBASE_ALL
		}
		final SaveBase save_base;
		final String hdr_tonemapping;
		final String hdr_local_contrast;
		final String hdr_n_tiles;
		final List<byte []> jpeg_images;
		final DngCreator dngCreator; // for raw
		final Image image; // for raw
		final boolean image_capture_intent;
		final Uri image_capture_intent_uri;
		final boolean using_camera2;
		final int image_quality;
		final boolean do_auto_stabilise;
		final double level_angle;
		final boolean is_front_facing;
		final boolean mirror;
		final Date current_date;
		final int image_number;
		final int adjust_levels;
		final boolean preference_stamp;
		final String preference_textstamp;
		final String preference_stamp_dateformat;
		final String preference_stamp_timeformat;
		final String preference_stamp_gpsformat;
		final boolean store_location;
		final Location location;
		final boolean store_geo_direction;
		final double geo_direction;
		int sample_factor = 1; // sampling factor for thumbnail, higher means lower quality
		
		Request(Type type,
			Prefs.PhotoMode photo_mode,
			SaveBase save_base,
			String hdr_tonemapping,
			String hdr_local_contrast,
			String hdr_n_tiles,
			List<byte []> jpeg_images,
			DngCreator dngCreator, Image image,
			boolean image_capture_intent, Uri image_capture_intent_uri,
			boolean using_camera2, int image_quality,
			boolean do_auto_stabilise, double level_angle,
			boolean is_front_facing,
			boolean mirror,
			Date current_date, int image_number, int adjust_levels,
			boolean preference_stamp, String preference_textstamp, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
			boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
			int sample_factor) {
			this.type = type;
			this.photo_mode = photo_mode;
			this.save_base = save_base;
			this.hdr_tonemapping = hdr_tonemapping;
			this.hdr_local_contrast = hdr_local_contrast;
			this.hdr_n_tiles = hdr_n_tiles;
			this.jpeg_images = jpeg_images;
			this.dngCreator = dngCreator;
			this.image = image;
			this.image_capture_intent = image_capture_intent;
			this.image_capture_intent_uri = image_capture_intent_uri;
			this.using_camera2 = using_camera2;
			this.image_quality = image_quality;
			this.do_auto_stabilise = do_auto_stabilise;
			this.level_angle = level_angle;
			this.is_front_facing = is_front_facing;
			this.mirror = mirror;
			this.current_date = current_date;
			this.image_number = image_number;
			this.adjust_levels = adjust_levels;
			this.preference_stamp = preference_stamp;
			this.preference_textstamp = preference_textstamp;
			this.preference_stamp_dateformat = preference_stamp_dateformat;
			this.preference_stamp_timeformat = preference_stamp_timeformat;
			this.preference_stamp_gpsformat = preference_stamp_gpsformat;
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
		this.nrProcessor = new NRProcessor(main_activity);
		
		String version = null;
		try {
			PackageInfo pInfo = main_activity.getPackageManager().getPackageInfo(main_activity.getPackageName(), 0);
			version = pInfo.versionName;
		} catch(NameNotFoundException e) {}
		this.software_name = "HedgeCam" + (version != null ? " " + version : "");

		p.setAntiAlias(true);
	}
	
	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		if( hdrProcessor != null ) {
			hdrProcessor.onDestroy();
		}
	}

	/** Saves a photo.
	 *  If do_in_background is true, the photo will be saved in a background thread. If the queue is full, the function will wait
	 *  until it isn't full. Otherwise it will return immediately. The function always returns true for background saving.
	 *  If do_in_background is false, the photo is saved on the current thread, and the function returns whether the photo was saved
	 *  successfully.
	 */
	boolean saveImageJpeg(boolean do_in_background,
			Prefs.PhotoMode photo_mode,
			boolean save_expo,
			String hdr_tonemapping,
			String hdr_local_contrast,
			String hdr_n_tiles,
			List<byte []> images,
			boolean image_capture_intent, Uri image_capture_intent_uri,
			boolean using_camera2, int image_quality,
			boolean do_auto_stabilise, double level_angle,
			boolean is_front_facing,
			boolean mirror,
			Date current_date, int image_number, int adjust_levels,
			boolean preference_stamp, String preference_textstamp, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
			boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
			int sample_factor) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "saveImageJpeg");
			Log.d(TAG, "do_in_background? " + do_in_background);
			Log.d(TAG, "number of images: " + images.size());
		}
		return saveImage(do_in_background,
				false,
				photo_mode,
				save_expo,
				hdr_tonemapping,
				hdr_local_contrast,
				hdr_n_tiles,
				images,
				null, null,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				do_auto_stabilise, level_angle,
				is_front_facing,
				mirror,
				current_date, image_number, adjust_levels,
				preference_stamp, preference_textstamp, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);
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
			Date current_date, int image_number) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "saveImageRaw");
			Log.d(TAG, "do_in_background? " + do_in_background);
		}
		return saveImage(do_in_background,
				true,
				photo_mode,
				false,
				null,
				null,
				null,
				null,
				dngCreator, image,
				false, null,
				false, 0,
				false, 0.0,
				false,
				false,
				current_date, image_number, 0,
				false, null, null, null, null,
				false, null, false, 0.0,
				1);
	}

	/** Internal saveImage method to handle both JPEG and RAW.
	 */
	private boolean saveImage(boolean do_in_background,
			boolean is_raw,
			Prefs.PhotoMode photo_mode,
			boolean save_expo,
			String hdr_tonemapping,
			String hdr_local_contrast,
			String hdr_n_tiles,
			List<byte []> jpeg_images,
			DngCreator dngCreator, Image image,
			boolean image_capture_intent, Uri image_capture_intent_uri,
			boolean using_camera2, int image_quality,
			boolean do_auto_stabilise, double level_angle,
			boolean is_front_facing,
			boolean mirror,
			Date current_date, int image_number, int adjust_levels,
			boolean preference_stamp, String preference_textstamp, String preference_stamp_dateformat, String preference_stamp_timeformat, String preference_stamp_gpsformat,
			boolean store_location, Location location, boolean store_geo_direction, double geo_direction,
			int sample_factor) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "saveImage");
			Log.d(TAG, "do_in_background? " + do_in_background);
		}
		boolean success;
		
		//do_in_background = false;
		
		Request request = new Request(is_raw ? Request.Type.RAW : Request.Type.JPEG,
				photo_mode,
				save_expo ? Request.SaveBase.SAVEBASE_ALL : Request.SaveBase.SAVEBASE_NONE,
				hdr_tonemapping,
				hdr_local_contrast,
				hdr_n_tiles,
				jpeg_images,
				dngCreator, image,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				do_auto_stabilise, level_angle,
				is_front_facing,
				mirror,
				current_date, image_number, adjust_levels,
				preference_stamp, preference_textstamp, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);

		if( do_in_background ) {
			if( MyDebug.LOG )
				Log.d(TAG, "add background request");
			addRequest(request);
			if( ( request.photo_mode == Prefs.PhotoMode.HDR ) || ( !is_raw && request.jpeg_images.size() > 1 ) ) {
				// For (multi-image) HDR, we also add a dummy request, effectively giving it a cost of 2 - to reflect the fact that HDR is more memory intensive
				// (arguably it should have a cost of 3, to reflect the 3 JPEGs, but one can consider this comparable to RAW+JPEG, which have a cost
				// of 2, due to RAW and JPEG each needing their own request).
				// Similarly for saving multiple images (expo-bracketing)
				addDummyRequest();
			}
			success = true; // always return true when done in background
		}
		else {
			if( is_raw ) {
				success = saveImageNowRaw(request.dngCreator, request.image, request.current_date, false);
			}
			else {
				success = saveImageNow(request, false);
			}
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
						success = saveImageNowRaw(request.dngCreator, request.image, request.current_date, true);
					}
					else if( request.type == Request.Type.JPEG ) {
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
				}
				main_activity.savingImage(false);
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
			Request.SaveBase.SAVEBASE_NONE,
			null,
			null,
			null,
			null,
			null, null,
			false, null,
			false, 0,
			false, 0.0,
			false,
			false,
			null, 0, 0,
			false, null, null, null, null,
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
	private Bitmap loadBitmap(byte [] jpeg_image, boolean mutable) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "loadBitmap");
			Log.d(TAG, "mutable?: " + mutable);
		}
		BitmapFactory.Options options = new BitmapFactory.Options();
		if( MyDebug.LOG )
			Log.d(TAG, "options.inMutable is: " + options.inMutable);
		options.inMutable = mutable;
		if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
			// setting is ignored in Android 5 onwards
			options.inPurgeable = true;
		}
		Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg_image, 0, jpeg_image.length, options);
		if( bitmap == null ) {
			Log.e(TAG, "failed to decode bitmap");
		}
		return bitmap;
	}

	/** Helper class for loadBitmaps().
	 */
	private static class LoadBitmapThread extends Thread {
		Bitmap bitmap;
		final BitmapFactory.Options options;
		final byte [] jpeg;
		LoadBitmapThread(BitmapFactory.Options options, byte [] jpeg) {
			this.options = options;
			this.jpeg = jpeg;
		}

		public void run() {
			this.bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
		}
	}

	/** Converts the array of jpegs to Bitmaps. The bitmap with index mutable_id will be marked as mutable (or set to -1 to have no mutable bitmaps).
	 */
	@SuppressWarnings("deprecation")
	private List<Bitmap> loadBitmaps(List<byte []> jpeg_images, int mutable_id) {
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
		LoadBitmapThread [] threads = new LoadBitmapThread[jpeg_images.size()];
		for(int i=0;i<jpeg_images.size();i++) {
			threads[i] = new LoadBitmapThread( i==mutable_id ? mutable_options : options, jpeg_images.get(i) );
		}
		// start threads
		if( MyDebug.LOG )
			Log.d(TAG, "start threads");
		for(int i=0;i<jpeg_images.size();i++) {
			threads[i].start();
		}
		// wait for threads to complete
		boolean ok = true;
		if( MyDebug.LOG )
			Log.d(TAG, "wait for threads to complete");
		try {
			for(int i=0;i<jpeg_images.size();i++) {
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
		for(int i=0;i<jpeg_images.size() && ok;i++) {
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
			for(int i=0;i<jpeg_images.size();i++) {
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

		if( request.type != Request.Type.JPEG ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with non-jpeg request");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}
		else if( request.jpeg_images.size() == 0 ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with zero images");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}

		boolean success;
		if( request.photo_mode == Prefs.PhotoMode.NoiseReduction ) {
			if( MyDebug.LOG ) {
				Log.d(TAG, "average");
				Log.d(TAG, "processing image #" + request.image_number);
			}

			success = true;

//			saveBaseImages(request, "_", in_background);
			Bitmap bitmap = loadBitmap(request.jpeg_images.get(0), false);
			
			if (request.image_number == 1) {
				if (!in_background) main_activity.savingImage(true);
				nrProcessor.start(bitmap);
			} else {
				nrProcessor.addBitmap(bitmap);
			}

			if (request.sample_factor != 0) {
				if( MyDebug.LOG )
					Log.d(TAG, "saving NR image");
				Bitmap nr_bitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
				nrProcessor.finish(nr_bitmap, request.adjust_levels);
				
				String suffix = "_NR";
				success = saveSingleImageNow(request, request.jpeg_images.get(0), nr_bitmap, suffix, true, true, in_background);
				nr_bitmap.recycle();
				if( MyDebug.LOG && !success )
					Log.e(TAG, "saveSingleImageNow failed for nr image");
			}

			bitmap.recycle();
			System.gc();
		}
		else if( request.photo_mode == Prefs.PhotoMode.HDR || request.photo_mode == Prefs.PhotoMode.DRO ) {
			if( MyDebug.LOG )
				Log.d(TAG, "hdr");
			if( request.jpeg_images.size() != 1 && request.jpeg_images.size() != 3 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow expected either 1 or 3 images for hdr, not " + request.jpeg_images.size());
				// throw runtime exception, as this is a programming error
				throw new RuntimeException();
			}

			long time_s = System.currentTimeMillis();
			if( request.jpeg_images.size() > 1 ) {
				// if there's only 1 image, we're in DRO mode, and shouldn't save the base image
				saveBaseImages(request, "_EXP", in_background);
				if( MyDebug.LOG ) {
					Log.d(TAG, "HDR performance: time after saving base exposures: " + (System.currentTimeMillis() - time_s));
				}
			}

			// note, even if we failed saving some of the expo images, still try to save the HDR image
			if( MyDebug.LOG )
				Log.d(TAG, "create HDR image");
			if (!in_background) main_activity.savingImage(true);

			// see documentation for HDRProcessor.processHDR() - because we're using release_bitmaps==true, we need to make sure that
			// the bitmap that will hold the output HDR image is mutable (in case of options like photo stamp)
			// see test testTakePhotoHDRPhotoStamp.
			int base_bitmap = (request.jpeg_images.size()-1)/2;
			if( MyDebug.LOG )
				Log.d(TAG, "base_bitmap: " + base_bitmap);
			List<Bitmap> bitmaps = loadBitmaps(request.jpeg_images, base_bitmap);
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
			try {
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) {
					int local_contrast;
					try {local_contrast = Integer.parseInt(request.hdr_local_contrast);}
					catch (NumberFormatException e) {local_contrast = 5;}
					if (local_contrast < 0 || local_contrast > 10) local_contrast = 5;

					int n_tiles;
					try {n_tiles = Integer.parseInt(request.hdr_n_tiles);}
					catch (NumberFormatException e) {n_tiles = 4;}

					HDRProcessor.TonemappingAlgorithm tonemapping_algorithm;
					switch (request.hdr_tonemapping) {
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
						default:
							tonemapping_algorithm = HDRProcessor.TonemappingAlgorithm.TONEMAPALGORITHM_REINHARD;
							break;
					}
					hdrProcessor.processHDR(bitmaps, true, null, true, null, (float)local_contrast/10, n_tiles, tonemapping_algorithm); // this will recycle all the bitmaps except bitmaps.get(0), which will contain the hdr image
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
					main_activity.getPreview().showToast(null, R.string.failed_to_process_hdr);
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
			if( MyDebug.LOG )
				Log.d(TAG, "after HDR first bitmap: " + bitmaps.get(0) + " is mutable? " + bitmaps.get(0).isMutable());
			Bitmap hdr_bitmap = bitmaps.get(0);
			if( MyDebug.LOG )
				Log.d(TAG, "hdr_bitmap: " + hdr_bitmap + " is mutable? " + hdr_bitmap.isMutable());
			bitmaps.clear();
			System.gc();
			if (!in_background) main_activity.savingImage(false);

			if( MyDebug.LOG )
				Log.d(TAG, "save HDR image");
			int base_image_id = ((request.jpeg_images.size()-1)/2);
			if( MyDebug.LOG )
				Log.d(TAG, "base_image_id: " + base_image_id);
			String suffix = request.jpeg_images.size() == 1 ? "_DRO" : "_HDR";
			success = saveSingleImageNow(request, request.jpeg_images.get(base_image_id), hdr_bitmap, suffix, true, true, in_background);
			if( MyDebug.LOG && !success )
				Log.e(TAG, "saveSingleImageNow failed for hdr image");
			if( MyDebug.LOG ) {
				Log.d(TAG, "HDR performance: time after saving HDR image: " + (System.currentTimeMillis() - time_s));
			}
			hdr_bitmap.recycle();
			System.gc();
		}
		else {
			if( request.jpeg_images.size() > 1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow called with multiple images");
				int mid_image = request.jpeg_images.size()/2;
				success = true;
				for(int i=0;i<request.jpeg_images.size();i++) {
					// note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
					byte [] image = request.jpeg_images.get(i);
					String filename_suffix = "_EXP" + i;
					boolean share_image = i == mid_image;
					if( !saveSingleImageNow(request, image, null, filename_suffix, true, share_image, in_background) ) {
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
				success = saveSingleImageNow(request, request.jpeg_images.get(0), null, suffix, true, true, in_background);
			}
		}

		return success;
	}

	/** Saves all the images in request.jpeg_images, depending on the save_base option.
	 */
	private void saveBaseImages(Request request, String suffix, boolean in_background) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveBaseImages");
		if( !request.image_capture_intent && request.save_base != Request.SaveBase.SAVEBASE_NONE ) {
			if( MyDebug.LOG )
				Log.d(TAG, "save base images");
			for(int i=0;i<request.jpeg_images.size();i++) {
				// note, even if one image fails, we still try saving the other images - might as well give the user as many images as we can...
				byte [] image = request.jpeg_images.get(i);
				String filename_suffix = suffix + i;
				// don't update the thumbnails, only do this for the final image - so user doesn't think it's complete, click gallery, then wonder why the final image isn't there
				// also don't mark these images as being shared
				if( !saveSingleImageNow(request, image, null, filename_suffix, false, false, in_background) ) {
					if( MyDebug.LOG )
						Log.e(TAG, "saveSingleImageNow failed for exposure image");
					// we don't set success to false here - as for deciding whether to pause preview or not (which is all we use the success return for), all that matters is whether we saved the final HDR image
				}
				if( request.save_base == Request.SaveBase.SAVEBASE_FIRST )
					break; // only requested the first
			}
		}
	}

	/** Performs the auto-stabilise algorithm on the image.
	 * @param data The jpeg data.
	 * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
	 * @param level_angle The angle in degrees to rotate the image.
	 * @param is_front_facing Whether the camera is front-facing.
	 * @param exifTempFile Temporary file that can be used to read exif tags (for orientation)
	 * @return A bitmap representing the auto-stabilised jpeg.
	 */
	private Bitmap autoStabilise(byte [] data, Bitmap bitmap, double level_angle, boolean is_front_facing, File exifTempFile) {
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
		if( bitmap == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "need to decode bitmap to auto-stabilise");
			// bitmap doesn't need to be mutable here, as this won't be the final bitmap retured from the auto-stabilise code
			bitmap = loadBitmap(data, false);
			if( bitmap == null ) {
				main_activity.getPreview().showToast(null, R.string.failed_to_auto_stabilise);
				System.gc();
			}
			if( bitmap != null ) {
				// rotate the bitmap if necessary for exif tags
				if( MyDebug.LOG )
					Log.d(TAG, "rotate bitmap for exif tags?");
				bitmap = rotateForExif(bitmap, data, exifTempFile);
			}
		}
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

	/** Mirrors the image.
	 * @param data The jpeg data.
	 * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
	 * @param exifTempFile Temporary file that can be used to read exif tags (for orientation)
	 * @return A bitmap representing the mirrored jpeg.
	 */
	private Bitmap mirrorImage(byte [] data, Bitmap bitmap, File exifTempFile) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "mirrorImage");
		}
		if( bitmap == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "need to decode bitmap to mirror");
			// bitmap doesn't need to be mutable here, as this won't be the final bitmap retured from the auto-stabilise code
			bitmap = loadBitmap(data, false);
			if( bitmap == null ) {
				// don't bother warning to the user - we simply won't mirror the image
				System.gc();
			}
			if( bitmap != null ) {
				// rotate the bitmap if necessary for exif tags
				if( MyDebug.LOG )
					Log.d(TAG, "rotate bitmap for exif tags?");
				bitmap = rotateForExif(bitmap, data, exifTempFile);
			}
		}
		if( bitmap != null ) {
			Matrix matrix = new Matrix();
			matrix.preScale(-1.0f, 1.0f);
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
			// careful, as new_bitmap is sometimes not a copy!
			if( new_bitmap != bitmap ) {
				bitmap.recycle();
				bitmap = new_bitmap;
			}
			if( MyDebug.LOG )
				Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
		}
		return bitmap;
	}

	/** Applies any photo stamp options (if they exist).
	 * @param data The jpeg data.
	 * @param bitmap Optional argument - the bitmap if already unpacked from the jpeg data.
	 * @param exifTempFile Temporary file that can be used to read exif tags (for orientation)
	 * @return A bitmap representing the stamped jpeg. Will be null if the input bitmap is null and
	 *		 no photo stamp is applied.
	 */
	private Bitmap stampImage(final Request request, byte [] data, Bitmap bitmap, File exifTempFile) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stampImage");
		}
		final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
		boolean text_stamp = request.preference_textstamp.length() > 0;
		if( request.preference_stamp || text_stamp ) {
			if( bitmap == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "decode bitmap in order to stamp info");
				bitmap = loadBitmap(data, true);
				if( bitmap == null ) {
					main_activity.getPreview().showToast(null, R.string.failed_to_stamp);
					System.gc();
				}
				if( bitmap != null ) {
					// rotate the bitmap if necessary for exif tags
					if( MyDebug.LOG )
						Log.d(TAG, "rotate bitmap for exif tags?");
					bitmap = rotateForExif(bitmap, data, exifTempFile);
				}
			}
			if( bitmap != null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "stamp info to bitmap: " + bitmap);
				if( MyDebug.LOG )
					Log.d(TAG, "bitmap is mutable?: " + bitmap.isMutable());
				String preference_stamp_dateformat = request.preference_stamp_dateformat;
				String preference_stamp_timeformat = request.preference_stamp_timeformat;
				String preference_stamp_gpsformat = request.preference_stamp_gpsformat;
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				if( MyDebug.LOG ) {
					Log.d(TAG, "decoded bitmap size " + width + ", " + height);
					Log.d(TAG, "bitmap size: " + width*height*4);
				}
				Canvas canvas = new Canvas(bitmap);
				int line_count = 0;
				if( request.preference_stamp ) {
					if( MyDebug.LOG )
						Log.d(TAG, "stamp date");
					// doesn't respect user preferences such as 12/24 hour - see note about in draw() about DateFormat.getTimeInstance()
					String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, request.current_date);
					String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, request.current_date);
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
					String gps_stamp = main_activity.getTextFormatter().getGPSString(preference_stamp_gpsformat, request.store_location, request.location, request.store_geo_direction, request.geo_direction);
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
					applicationInterface.drawTextOnPhoto(canvas, p, request.preference_textstamp, width, height, line_count);
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
	private boolean saveSingleImageNow(final Request request, byte [] data, Bitmap bitmap, String filename_suffix, boolean update_thumbnail, boolean share_image, boolean in_background) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveSingleImageNow");

		if( request.type != Request.Type.JPEG ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveImageNow called with non-jpeg request");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}
		else if( data == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "saveSingleImageNow called with no data");
			// throw runtime exception, as this is a programming error
			throw new RuntimeException();
		}
		long time_s = System.currentTimeMillis();
		
		// unpack:
		final boolean image_capture_intent = request.image_capture_intent;
		final boolean using_camera2 = request.using_camera2;
		final Date current_date = request.current_date;
		final boolean store_location = request.store_location;
		final boolean store_geo_direction = request.store_geo_direction;

		boolean success = false;
		final MyApplicationInterface applicationInterface = main_activity.getApplicationInterface();
		StorageUtils storageUtils = main_activity.getStorageUtils();
		
		if (!in_background) main_activity.savingImage(true);

		boolean text_stamp = request.preference_textstamp.length() > 0;
		File exifTempFile = null;
		if( bitmap != null || request.do_auto_stabilise || request.mirror || request.preference_stamp || text_stamp ) {
			// either we have a bitmap, or will need to decode the bitmap to do post-processing
			// need to rotate the bitmap according to the exif orientation (which some devices use, e.g., Samsung)
			// so need to write to a temp file for this - we also use this later on to transfer the exif tags
			// on Android 7+, we can now read exif tags direct from the jpeg data
/*			if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
				try {
					if( MyDebug.LOG )
						Log.d(TAG, "write temp file to record EXIF data");
					exifTempFile = File.createTempFile("hedgecam_exif", "");
					OutputStream tempOutputStream = new FileOutputStream(exifTempFile);
					try {
						tempOutputStream.write(data);
					}
					finally {
						tempOutputStream.close();
					}
					if( MyDebug.LOG ) {
						Log.d(TAG, "Save single image performance: time after saving temp photo for EXIF: " + (System.currentTimeMillis() - time_s));
					}
				}
				catch(IOException e) {
					if (MyDebug.LOG)
						Log.e(TAG, "exception writing to temp file");
					e.printStackTrace();
				}
			}*/

			if( bitmap != null ) {
				// rotate the bitmap if necessary for exif tags
				if( MyDebug.LOG )
					Log.d(TAG, "rotate pre-existing bitmap for exif tags?");
				bitmap = rotateForExif(bitmap, data, exifTempFile);
			}
		}
		if( request.do_auto_stabilise ) {
			bitmap = autoStabilise(data, bitmap, request.level_angle, request.is_front_facing, exifTempFile);
		}
		if( MyDebug.LOG ) {
			Log.d(TAG, "Save single image performance: time after auto-stabilise: " + (System.currentTimeMillis() - time_s));
		}
		if( request.mirror ) {
			bitmap = mirrorImage(data, bitmap, exifTempFile);
		}
		bitmap = stampImage(request, data, bitmap, exifTempFile);
		if( MyDebug.LOG ) {
			Log.d(TAG, "Save single image performance: time after photostamp: " + (System.currentTimeMillis() - time_s));
		}

		File picFile = null;
		Uri saveUri = null; // if non-null, then picFile is a temporary file, which afterwards we should redirect to saveUri
		try {
			if( image_capture_intent ) {
				if( MyDebug.LOG )
					Log.d(TAG, "image_capture_intent");
				if( request.image_capture_intent_uri != null )
				{
					// Save the bitmap to the specified URI (use a try/catch block)
					if( MyDebug.LOG )
						Log.d(TAG, "save to: " + request.image_capture_intent_uri);
					saveUri = request.image_capture_intent_uri;
				}
				else
				{
					// If the intent doesn't contain an URI, send the bitmap as a parcel
					// (it is a good idea to reduce its size to ~50k pixels before)
					if( MyDebug.LOG )
						Log.d(TAG, "sent to intent via parcel");
					if( bitmap == null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "create bitmap");
						// bitmap we return doesn't need to be mutable
						bitmap = loadBitmap(data, false);
						if( bitmap != null ) {
							// rotate the bitmap if necessary for exif tags
							if( MyDebug.LOG )
								Log.d(TAG, "rotate bitmap for exif tags?");
							bitmap = rotateForExif(bitmap, data, exifTempFile);
						}
					}
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
					if( exifTempFile != null && !exifTempFile.delete() ) {
						if( MyDebug.LOG )
							Log.e(TAG, "failed to delete temp " + exifTempFile.getAbsolutePath());
					}
					exifTempFile = null;
					main_activity.finish();
				}
			}
			else if( storageUtils.isUsingSAF() ) {
				saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "jpg", current_date);
			}
			else {
				picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, filename_suffix, "jpg", current_date);
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + picFile.getAbsolutePath());
			}
			
			if( saveUri != null && picFile == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "saveUri: " + saveUri);
				picFile = File.createTempFile("picFile", "jpg", main_activity.getCacheDir());
				if( MyDebug.LOG )
					Log.d(TAG, "temp picFile: " + picFile.getAbsolutePath());
			}
			
			if( picFile != null ) {
				OutputStream outputStream = new FileOutputStream(picFile);
				try {
					if( bitmap != null ) {
						if( MyDebug.LOG )
							Log.d(TAG, "compress bitmap, quality " + request.image_quality);
						bitmap.compress(Bitmap.CompressFormat.JPEG, request.image_quality, outputStream);
					}
					else {
						outputStream.write(data);
					}
				}
				finally {
					outputStream.close();
				}
				if( MyDebug.LOG )
					Log.d(TAG, "saveImageNow saved photo");
				if( MyDebug.LOG ) {
					Log.d(TAG, "Save single image performance: time after saving photo: " + (System.currentTimeMillis() - time_s));
				}

				if( saveUri == null ) { // if saveUri is non-null, then we haven't succeeded until we've copied to the saveUri
					success = true;
				}
				if( picFile != null ) {
					if( bitmap != null ) {
						// need to update EXIF data!
//						if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
							if( MyDebug.LOG )
								Log.d(TAG, "set Exif tags from data");
							setExifFromData(request, data, picFile);
/*						}
						else {
							if( MyDebug.LOG )
								Log.d(TAG, "set Exif tags from file");
							if( exifTempFile != null ) {
								setExifFromFile(request, exifTempFile, picFile);
								if( MyDebug.LOG ) {
									Log.d(TAG, "Save single image performance: time after copying EXIF: " + (System.currentTimeMillis() - time_s));
								}
							}
							else {
								if( MyDebug.LOG )
									Log.d(TAG, "can't set Exif tags without file pre-Android 7");
							}
						}*/
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
							exif.setAttribute(ExifInterface.TAG_SOFTWARE, software_name);
							exif.saveAttributes();
						}
						catch(NoClassDefFoundError exception) {
							// have had Google Play crashes from new ExifInterface() elsewhere for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn), so also catch here just in case
							if( MyDebug.LOG )
								Log.e(TAG, "exif orientation NoClassDefFoundError");
							exception.printStackTrace();
						}
					}

					if( saveUri == null ) {
						// broadcast for SAF is done later, when we've actually written out the file
						storageUtils.broadcastFile(picFile, true, false, update_thumbnail);
						main_activity.test_last_saved_image = picFile.getAbsolutePath();
					}
				}
				if( image_capture_intent ) {
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
					else if( !image_capture_intent ) {
						if( MyDebug.LOG )
							Log.d(TAG, "announce SAF uri");
						// announce the SAF Uri
						// (shouldn't do this for a capture intent - e.g., causes crash when calling from Google Keep)
						storageUtils.announceUri(saveUri, true, false);
					}
				}
			}
		}
		catch(FileNotFoundException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "File not found: " + e.getMessage());
			e.printStackTrace();
			main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
		}
		catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "I/O error writing file: " + e.getMessage());
			e.printStackTrace();
			main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
		}
		catch(SecurityException e) {
			// received security exception from copyFileToUri()->openOutputStream() from Google Play
			if( MyDebug.LOG )
				Log.e(TAG, "security exception writing file: " + e.getMessage());
			e.printStackTrace();
			main_activity.getPreview().showToast(null, R.string.failed_to_save_photo);
		}

		if( exifTempFile != null && !exifTempFile.delete() ) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to delete temp " + exifTempFile.getAbsolutePath());
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
			if( bitmap == null ) {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inMutable = false;
				if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
					// setting is ignored in Android 5 onwards
					options.inPurgeable = true;
				}
				options.inSampleSize = sample_size;
				thumbnail = BitmapFactory.decodeByteArray(data, 0, data.length, options);
				// now get the rotation from the Exif data
				if( MyDebug.LOG )
					Log.d(TAG, "rotate thumbnail for exif tags?");
				thumbnail = rotateForExif(thumbnail, data, picFile);
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
					// don't need to rotate for exif, as we already did that when creating the bitmap
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

	private void setExifFromData(final Request request, byte [] data, File to_file) throws IOException {
		if( MyDebug.LOG ) {
			Log.d(TAG, "setExifFromData");
			Log.d(TAG, "to_file: " + to_file);
		}
		InputStream inputStream = null;
		try {
			inputStream = new ByteArrayInputStream(data);
			ExifInterface exif = new ExifInterface(inputStream);
			ExifInterface exif_new = new ExifInterface(to_file.getAbsolutePath());
			setExif(request, exif, exif_new);
		}
		finally {
			if( inputStream != null ) {
				inputStream.close();
			}
		}
	}

	/** Used to transfer exif tags, if we had to convert the jpeg info to a bitmap (for post-processing such as
	 *  auto-stabilise or photo stamp).
	 *  Note that we use several ExifInterface tags that are now deprecated in API level 23 and 24. These are replaced with new tags that have
	 *  the same string value (e.g., TAG_APERTURE replaced with TAG_F_NUMBER, but both have value "FNumber"). We use the deprecated versions
	 *  to avoid complicating the code (we'd still have to read the deprecated values for older devices).
	 */
	@SuppressWarnings("deprecation")
	private void setExifFromFile(final Request request, File from_file, File to_file) throws IOException {
		if( MyDebug.LOG ) {
			Log.d(TAG, "setExifFromFile");
			Log.d(TAG, "from_file: " + from_file);
			Log.d(TAG, "to_file: " + to_file);
		}
		try {
			ExifInterface exif = new ExifInterface(from_file.getAbsolutePath());
			ExifInterface exif_new = new ExifInterface(to_file.getAbsolutePath());
			setExif(request, exif, exif_new);
		}
		catch(NoClassDefFoundError exception) {
			// have had Google Play crashes from new ExifInterface() for Galaxy Ace4 (vivalto3g)
			if( MyDebug.LOG )
				Log.e(TAG, "exif orientation NoClassDefFoundError");
			exception.printStackTrace();
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

			exif_new.setAttribute(ExifInterface.TAG_SOFTWARE, software_name);

			modifyExif(exif_new, request.using_camera2, request.current_date, request.store_location, request.store_geo_direction, request.geo_direction);
			exif_new.saveAttributes();
	}

	/** May be run in saver thread or picture callback thread (depending on whether running in background).
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private boolean saveImageNowRaw(DngCreator dngCreator, Image image, Date current_date, boolean in_background) {
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
				saveUri = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_IMAGE, "", "dng", current_date);
				if( MyDebug.LOG )
					Log.d(TAG, "saveUri: " + saveUri);
				// When using SAF, we don't save to a temp file first (unlike for JPEGs). Firstly we don't need to modify Exif, so don't
				// need a real file; secondly copying to a temp file is much slower for RAW.
			}
			else {
				picFile = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_IMAGE, "", "dng", current_date);
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

			/*Location location = null;
			if( main_activity.getApplicationInterface().getGeotaggingPref() ) {
				location = main_activity.getApplicationInterface().getLocation();
				if( MyDebug.LOG )
					Log.d(TAG, "location: " + location);
			}*/

			if( saveUri == null ) {
				success = true;
				//Uri media_uri = storageUtils.broadcastFileRaw(picFile, current_date, location);
				//storageUtils.announceUri(media_uri, true, false);				
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
					//Uri media_uri = storageUtils.broadcastFileRaw(real_file, current_date, location);
					//storageUtils.announceUri(media_uri, true, false);
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
			main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
		}
		catch(IOException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "ioexception writing raw image file");
			e.printStackTrace();
			main_activity.getPreview().showToast(null, R.string.failed_to_save_photo_raw);
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

	/** Rotates the supplied bitmap according to the orientation tag stored in the exif data. On
	 *  Android 7 onwards, we use the jpeg data; on earlier versions the supplied exifTimeFile is
	 *  used. If no rotation is required, the input bitmap is returned.
	 * @param data Jpeg data containing the Exif information to use.
	 * @param exifTempFile Ignored on Android 7+. If this is null on older versions, the bitmap is
	 *					 returned without rotation.
	 */
	private Bitmap rotateForExif(Bitmap bitmap, byte [] data, File exifTempFile) {
		if( MyDebug.LOG )
			Log.d(TAG, "rotateForExif");
		InputStream inputStream = null;
		try {
			ExifInterface exif;

//			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
				if( MyDebug.LOG )
					Log.d(TAG, "Android 7: use data stream to read exif tags");
				inputStream = new ByteArrayInputStream(data);
				exif = new ExifInterface(inputStream);
/*			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "pre-Android 7: use file to read exif tags: " + exifTempFile);
				if( exifTempFile != null ) {
					exif = new ExifInterface(exifTempFile.getAbsolutePath());
				}
				else {
					if( MyDebug.LOG )
						Log.d(TAG, "but no file available to read exif tags from");
					return bitmap;
				}
			}*/

			int exif_orientation_s = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
			if( MyDebug.LOG )
				Log.d(TAG, "	exif orientation string: " + exif_orientation_s);
			boolean needs_tf = false;
			int exif_orientation = 0;
			// from http://jpegclub.org/exif_orientation.html
			// and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
			if( exif_orientation_s == ExifInterface.ORIENTATION_UNDEFINED || exif_orientation_s == ExifInterface.ORIENTATION_NORMAL ) {
				// leave unchanged
			}
			else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_180 ) {
				needs_tf = true;
				exif_orientation = 180;
			}
			else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_90 ) {
				needs_tf = true;
				exif_orientation = 90;
			}
			else if( exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_270 ) {
				needs_tf = true;
				exif_orientation = 270;
			}
			else {
				// just leave unchanged for now
				if( MyDebug.LOG )
					Log.e(TAG, "	unsupported exif orientation: " + exif_orientation_s);
			}
			if( MyDebug.LOG )
				Log.d(TAG, "	exif orientation: " + exif_orientation);

			if( needs_tf ) {
				if( MyDebug.LOG )
					Log.d(TAG, "	need to rotate bitmap due to exif orientation tag");
				Matrix m = new Matrix();
				m.setRotate(exif_orientation, bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f);
				Bitmap rotated_bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), m, true);
				if( rotated_bitmap != bitmap ) {
					bitmap.recycle();
					bitmap = rotated_bitmap;
				}
			}
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
		return bitmap;
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
