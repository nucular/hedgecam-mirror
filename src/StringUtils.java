package com.caddish_hedgehog.hedgecam2;

import android.content.Context;
import android.content.res.Resources;
import java.io.IOException;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Handles various text formatting options, used for photo stamp and video subtitles.
 */
public class StringUtils {
	private static final String TAG = "HedgeCam/TextUtils";

	private static Context context;
	private static Resources resources;
	private static final DecimalFormat decimal_format = new DecimalFormat("#0.0");
	private static final DecimalFormat decimal_format_1dp = new DecimalFormat("#.#");
	private static final DecimalFormat decimal_format_2dp = new DecimalFormat("#.##");
	private static String infinity;
	
	private static float exposure_step = 0.0f;

	public static void init(Context c) {
		context = c;
		resources = context.getResources();
		infinity = DecimalFormatSymbols.getInstance().getInfinity();
	}

	/** Formats the date according to the user preference preference_stamp_dateformat.
	 *  Returns "" if preference_stamp_dateformat is "preference_stamp_dateformat_none".
	 */
	public static String getDateString(String preference_stamp_dateformat, Date date) {
		String date_stamp = "";
		if( !preference_stamp_dateformat.equals("preference_stamp_dateformat_none") ) {
			switch(preference_stamp_dateformat) {
				case "preference_stamp_dateformat_yyyymmdd":
					date_stamp = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date);
					break;
				case "preference_stamp_dateformat_ddmmyyyy":
					date_stamp = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
					break;
				case "preference_stamp_dateformat_mmddyyyy":
					date_stamp = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(date);
					break;
				default:
					date_stamp = DateFormat.getDateInstance().format(date);
					break;
			}
		}
		return date_stamp;
	}

	/** Formats the time according to the user preference preference_stamp_timeformat.
	 *  Returns "" if preference_stamp_timeformat is "preference_stamp_timeformat_none".
	 */
	public static String getTimeString(String preference_stamp_timeformat, Date date) {
		String time_stamp = "";
		if( !preference_stamp_timeformat.equals("preference_stamp_timeformat_none") ) {
			switch(preference_stamp_timeformat) {
				case "preference_stamp_timeformat_12hour":
					time_stamp = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(date);
					break;
				case "preference_stamp_timeformat_24hour":
					time_stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date);
					break;
				default:
					time_stamp = DateFormat.getTimeInstance().format(date);
					break;
			}
		}
		return time_stamp;
	}

	/** Formats the GPS information according to the user preference_stamp_gpsformat preference_stamp_timeformat.
	 *  Returns "" if preference_stamp_gpsformat is "preference_stamp_gpsformat_none", or both store_location and
	 *  store_geo_direction are false.
	 */
    public static String getGPSString(String preference_stamp_gpsformat, boolean store_location, Location location, boolean store_address, boolean store_altitude, boolean store_geo_direction, double geo_direction) {
        if( !preference_stamp_gpsformat.equals("preference_stamp_gpsformat_none") ) {
			List<String> out = new ArrayList<>();
            if( store_location ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "location: " + location);
					
				if (store_address && Geocoder.isPresent()) {
					Geocoder geocoder = new Geocoder(context, Locale.getDefault());
					try {
						List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
						if (addresses.size() > 0) {
							Address addr = addresses.get(0);
							String item = addr.getLocality();
							if (item != null) out.add(item);
							
							item = addr.getThoroughfare();
							if (item != null) {
								store_location = false;

								out.add(item);
								item = addr.getFeatureName();
								if (item != null) out.add(item);
							} else {
								item = addr.getSubLocality();
								if (item != null) out.add(item);
							}
							
							if (out.size() == 0) {
								item = addr.getAdminArea();
								if (item != null) out.add(item);

								item = addr.getSubAdminArea();
								if (item != null) out.add(item);
							}
							if (out.size() == 0) {
								item = addr.getCountryName();
								if (item != null) out.add(item);
							}
						}
					}
					catch (IOException e) {}
				}
				if (store_location) {
	                if( preference_stamp_gpsformat.equals("preference_stamp_gpsformat_dms") ) {
	                    out.add(LocationSupplier.locationToDMS(location.getLatitude()));
						out.add(LocationSupplier.locationToDMS(location.getLongitude()));
	                } else {
	                    out.add(Location.convert(location.getLatitude(), Location.FORMAT_DEGREES));
						out.add(Location.convert(location.getLongitude(), Location.FORMAT_DEGREES));
					}
				}
                if( store_altitude && location.hasAltitude() ) {
                    out.add(decimal_format.format(location.getAltitude()) + context.getResources().getString(R.string.metres_abbreviation));
                }
            }
            if( store_geo_direction ) {
                float geo_angle = (float)Math.toDegrees(geo_direction);
                if( geo_angle < 0.0f ) {
                    geo_angle += 360.0f;
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "geo_angle: " + geo_angle);
                out.add(Float.toString(Math.round(geo_angle)) + (char)0x00B0);
            }
	        String gps_stamp = "";
			if (out.size() > 0)
				gps_stamp = TextUtils.join(", ", out);
	        if( MyDebug.LOG )
	            Log.d(TAG, "gps_stamp: " + gps_stamp);
	        return gps_stamp;
        }
		return "";
    }

	public static String formatTimeMS(long time_ms) {
		int ms = (int) (time_ms) % 1000 ;
		int seconds = (int) (time_ms / 1000) % 60 ;
		int minutes = (int) ((time_ms / (1000*60)) % 60);
		int hours   = (int) ((time_ms / (1000*60*60)));
		return String.format(Locale.getDefault(), "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms);
	}

	public static void setExposureStep(float value) {
		exposure_step = value;
	}

	public static String getExposureCompensationString(int exposure) {
		float exposure_ev = exposure * exposure_step;
		return  (exposure > 0 ? "+" : "") + decimal_format_2dp.format(exposure_ev) + " EV";
	}

	public static String getISOString(int iso) {
		return resources.getString(R.string.iso) + " " + iso;
	}
	
	public static String getISOString(String iso) {
		switch (iso) {
			case "auto":
				return resources.getString(R.string.iso) + ": " + resources.getString(R.string.auto);
			case "manual":
				return resources.getString(R.string.iso_manual);
			default:
				return resources.getString(R.string.iso) + ": " + fixISOString(iso);
		}
	}
	
	public static String fixISOString(String value) {
		if (value.length() >= 4 && value.substring(0, 4).equalsIgnoreCase("ISO_"))
			return value.substring(4);
		else if (value.length() >= 3 && value.substring(0, 3).equalsIgnoreCase("ISO"))
			return value.substring(3);
		else
			return value;
	}

	public static String getExposureTimeString(long exposure_time) {
		double exposure_time_s = exposure_time/1000000000.0;
		String string;
		if( exposure_time >= 500000000 ) {
			// show exposure times of more than 0.5s directly
			string = decimal_format_1dp.format(exposure_time_s) + resources.getString(R.string.seconds_abbreviation);
		}
		else {
			double exposure_time_r = 1.0/exposure_time_s;
			string = " 1/" + decimal_format_1dp.format(exposure_time_r) + " " + resources.getString(R.string.seconds_abbreviation);
		}
		return string;
	}

	public static String getFocusDistanceString(float focus_distance) {
		return getFocusDistanceString(focus_distance, false);
	}

	public static String getFocusDistanceString(float focus_distance, boolean use_infinity_sign) {
		if( focus_distance != 0.0f ) {
			String focus_distance_s;
			float real_focus_distance = 1.0f / focus_distance;
			if (Math.abs(real_focus_distance) > 0.2f ) {
				focus_distance_s = decimal_format_2dp.format(real_focus_distance) + " " + resources.getString(R.string.metres_abbreviation);
			} else {
				focus_distance_s = decimal_format_2dp.format(real_focus_distance*100) + " " + resources.getString(R.string.centimetres_abbreviation);
			}
			return focus_distance_s;
		} else {
			return use_infinity_sign ? infinity : resources.getString(R.string.infinite);
		}
	}

	/*public static String getFrameDurationString(long frame_duration) {
		double frame_duration_s = frame_duration/1000000000.0;
		double frame_duration_r = 1.0/frame_duration_s;
		return resources.getString(R.string.fps) + " " + decimal_format_1dp.format(frame_duration_r);
	}*/

}
