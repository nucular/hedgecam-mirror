package com.caddish_hedgehog.hedgecam2;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.ResolveInfo;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONException;

import com.android.vending.billing.IInAppBillingService;

class Donations {
	private static final String TAG = "HedgeCam/Donations";

	public static final int BILLING_RESPONSE_RESULT_OK = 0;
	public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
	public static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
	public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
	public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
	public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
	public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
	public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
	public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

	private static final int ACTIVITY_REQUEST_CODE = 4242;

	public static abstract class DonationsListener {
		public void onReady() {}
		public void onDonationMade(String id) {}
	}

	public class PlayDonation {
		public String id;
		public String amount;
		public long amount_micros;

		PlayDonation(String id, String amount, long amount_micros) {
			this.id = id;
			this.amount = amount;
			this.amount_micros = amount_micros;
		}
	}

	private final Context context;
	private DonationsListener listener;

	private AsyncTask<Void, Void, Integer> loadingTask;
	private boolean isReady;

	private IInAppBillingService service;
	private ServiceConnection serviceConnection;

	private final List<PlayDonation> playDonations = new ArrayList<PlayDonation>();

	private boolean wasDonations;

	Donations(final Activity context) {
		this.context = context;
	}

	public void init(final DonationsListener listener) {
		this.listener = listener;

		serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {
				if( MyDebug.LOG )
					Log.d(TAG, "onServiceDisconnected");
				if (loadingTask != null) {
					loadingTask.cancel(true);
					loadingTask = null;
				}
				service = null;
			}

			@Override
			public void onServiceConnected(ComponentName name, final IBinder binder) {
				if( MyDebug.LOG )
					Log.d(TAG, "onServiceConnected");

				loadingTask = new AsyncTask<Void, Void, Integer>() {
					private static final String TAG = "HedgeCam/Donations/AsyncTask";

					protected Integer doInBackground(Void... params) {
						if( MyDebug.LOG )
							Log.d(TAG, "doInBackground");
						service = IInAppBillingService.Stub.asInterface(binder);
						final String packageName = context.getPackageName();
						try {
							if (service.isBillingSupported(3, packageName, "inapp") == BILLING_RESPONSE_RESULT_OK) {
								if( MyDebug.LOG )
									Log.d(TAG, "Requesting skus list...");

								ArrayList<String> skuList = new ArrayList<String>();
								// OMFG!  Even Google Play billing has stupid bugs! We can`t use ITEM_ID_LIST with 6 items, because in this case the service will return DETAILS_LIST with 3 items only.
								// List with 4 ,5, 7, 8 and 9 items works fine. Are you sick Google?
								for (int i = 1; i <= 9; i++) {
									skuList.add("donation_" + i);
								}
								Bundle querySkus = new Bundle();
								querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
								Bundle skuDetails = service.getSkuDetails(3, packageName, "inapp", querySkus);

								if (skuDetails.containsKey("DETAILS_LIST")) {
									ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");
									if (responseList != null) {
										for (String thisResponse : responseList) {
											if( MyDebug.LOG ) {
												Log.d(TAG, "Donation details: " + thisResponse);
											}
											try {
												JSONObject o = new JSONObject(thisResponse);
												playDonations.add(new PlayDonation(o.optString("productId"), o.optString("price"), o.optLong("price_amount_micros")));
											} catch (JSONException e) {}
										}
									}
								} else {
									int response = getResponseCodeFromBundle(skuDetails);
									if (response != BILLING_RESPONSE_RESULT_OK) {
										if( MyDebug.LOG )
											Log.d(TAG, "getSkuDetails() failed, code: " + response);
									} else {
										if( MyDebug.LOG )
											Log.e(TAG, "getSkuDetails() returned a bundle with neither an error nor a detail list.");
									}
								}
								if( MyDebug.LOG )
									Log.d(TAG, "Skus list contains " + playDonations.size() + " items");

								if( MyDebug.LOG )
									Log.d(TAG, "Requesting old donations...");
								Bundle history = service.getPurchases(3, packageName, "inapp", null);
								if (history.containsKey("INAPP_PURCHASE_DATA_LIST")) {
									ArrayList<String> responseList = history.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
									if (responseList != null) {
										for (String thisResponse : responseList) {
											if( MyDebug.LOG ) {
												Log.d(TAG, "Old donation details: " + thisResponse);
											}

											String token = null;
											try {
												JSONObject o = new JSONObject(thisResponse);
												token = o.optString("purchaseToken");
											} catch (JSONException e) {}

											if (token != null) {
												if( MyDebug.LOG )
													Log.d(TAG, "Now we have some old donation and voraciously consume it... Om-nom-nom... :D");
												service.consumePurchase(3, packageName, token);
											}
										}
									}
								} else {
									int response = getResponseCodeFromBundle(history);
									if (response != BILLING_RESPONSE_RESULT_OK) {
										if( MyDebug.LOG )
											Log.d(TAG, "getPurchases() failed, code: " + response);
									} else {
										if( MyDebug.LOG )
											Log.e(TAG, "getPurchases() returned a bundle with neither an error nor a detail list.");
									}
								}

								if (service.isBillingSupported(6, packageName, "inapp") == BILLING_RESPONSE_RESULT_OK) {
									if( MyDebug.LOG )
										Log.d(TAG, "Requesting history...");

									history = service.getPurchaseHistory(6, packageName, "inapp", null, null);
									if (history.containsKey("INAPP_PURCHASE_DATA_LIST")) {
										ArrayList<String> responseList = history.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
										if (responseList != null) {
											for (String thisResponse : responseList) {
												if( MyDebug.LOG ) {
													Log.d(TAG, "History details: " + thisResponse);
												}

												String id = null;
												try {
													JSONObject o = new JSONObject(thisResponse);
													id = o.optString("productId");
												} catch (JSONException e) {}

												if (id != null) {
													for (PlayDonation d : playDonations) {
														if (d.id.equals(id)) {
															wasDonations = true;
															break;
														}
													}
												}

												if (wasDonations)
													break;
											}
										}
									} else {
										int response = getResponseCodeFromBundle(history);
										if (response != BILLING_RESPONSE_RESULT_OK) {
											if( MyDebug.LOG )
												Log.d(TAG, "getPurchaseHistory() failed, code: " + response);
										} else {
											if( MyDebug.LOG )
												Log.e(TAG, "getPurchaseHistory() returned a bundle with neither an error nor a detail list.");
										}
									}
								}
							}
						}
						catch (RemoteException e) {
							if( MyDebug.LOG )
								Log.d(TAG, "RemoteException");
						}
						// This is madness...
						return 0;
					}
					protected void onPostExecute(Integer unused) {
						if( MyDebug.LOG )
							Log.d(TAG, "onPostExecute");
						loadingTask = null;
						isReady = true;
						if (listener != null)
							listener.onReady();
					}
				};
				loadingTask.execute();
			}
		};

		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		List<ResolveInfo> intentServices = context.getPackageManager().queryIntentServices(serviceIntent, 0);
		if (intentServices != null && !intentServices.isEmpty()) {
			context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		}
		else {
			if( MyDebug.LOG )
				Log.d(TAG, "Billing service unavailable on device.");

			serviceConnection = null;

			if (listener != null)
				listener.onReady();
		}
	}

	public List<PlayDonation> getPlayDonations() {
		if( MyDebug.LOG )
			Log.d(TAG, "getPlayDonations()");

		if (!isReady)
			return new ArrayList<PlayDonation>();

		return playDonations;
	}

	public boolean wasThereDonations() {
		if (!isReady)
			return false;

		return wasDonations;
	}

	public void donate(String id) {
		if( MyDebug.LOG )
			Log.d(TAG, "donate()");
		if (!isReady || service == null)
			return;

		boolean found = false;
		for (Donations.PlayDonation item : playDonations) {
			if (item.id.equals(id)) {
				found = true;
				break;
			}
		}
		if (!found)
			return;

		try {
			Bundle buyIntentBundle = service.getBuyIntent(3, context.getPackageName(), id, "inapp", "");
			int response = getResponseCodeFromBundle(buyIntentBundle);

			if (response == BILLING_RESPONSE_RESULT_OK) {
				PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
				((Activity)context).startIntentSenderForResult(pendingIntent.getIntentSender(), ACTIVITY_REQUEST_CODE, new Intent(), 0, 0, 0);
			} else {
				if( MyDebug.LOG )
					Log.d(TAG, "getBuyIntent() failed, result code: " + response);
			}
		}
		catch (RemoteException e) {
			if( MyDebug.LOG )
				Log.d(TAG, "RemoteException");
		}
		catch (SendIntentException e) {
			if( MyDebug.LOG )
				Log.d(TAG, "SendIntentException");
		}
	}

	public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
		if( MyDebug.LOG )
			Log.d(TAG, "handleActivityResult(" + requestCode + ", " + resultCode + ", data)");
		if (!isReady)
			return false;

		if (requestCode != ACTIVITY_REQUEST_CODE)
			return false;
		
		if (data == null) {
			 if( MyDebug.LOG )
				Log.d(TAG, "Null data in activity result.");
			return true;
		}

		int responseCode = getResponseCodeFromIntent(data);
		if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
			if( MyDebug.LOG ) {
				Log.d(TAG, "Successful resultcode from purchase activity.");
				Log.d(TAG, "Purchase data: " + purchaseData);
				Log.d(TAG, "Data signature: " + dataSignature);
			}

			String id = null;
			String token = null;
			try {
				JSONObject o = new JSONObject(purchaseData);
				id = o.optString("productId");
				token = o.optString("purchaseToken");
			} catch (JSONException e) {}

			if (id != null) {
				boolean found = false;
				for (PlayDonation d : playDonations) {
					if (d.id.equals(id)) {
						found = true;
						break;
					}
				}

				// Just in case
				if (!found) 
					id = null;
			}

			if (service != null && token != null) {
				if( MyDebug.LOG )
					Log.d(TAG, "Now we have new donation and voraciously consume it... Om-nom-nom... :D");
				try {
					service.consumePurchase(3, context.getPackageName(), token);
				}
				catch (RemoteException e) {
					if( MyDebug.LOG )
						Log.d(TAG, "RemoteException");
				}
			}

			if (listener != null)
				listener.onDonationMade(id);
		} else if (resultCode == Activity.RESULT_CANCELED || responseCode == BILLING_RESPONSE_RESULT_USER_CANCELED) {
			if( MyDebug.LOG )
				Log.d(TAG, "Donation cancelled by user");
		}

		return true;
	}

	private int getResponseCodeFromIntent(Intent i) {
		Object o = i.getExtras().get("RESPONSE_CODE");
		return getResponseCode(o);
	}

	private int getResponseCodeFromBundle(Bundle b) {
		Object o = b.get("RESPONSE_CODE");
		return getResponseCode(o);
	}

	private int getResponseCode(Object o) {
		if (o == null) {
			if( MyDebug.LOG )
				Log.d(TAG, "Bundle with null response code, assuming OK (known issue)");
			return BILLING_RESPONSE_RESULT_OK;
		}
		else if (o instanceof Integer) return ((Integer)o).intValue();
		else if (o instanceof Long) return (int)((Long)o).longValue();
		else {
			return BILLING_RESPONSE_RESULT_ERROR;
		}
	}
	
	public void onDestroy() {
		listener = null;

		try {
			if (loadingTask != null) {
				loadingTask.cancel(true);
				loadingTask = null;
			}

			if (context != null && serviceConnection != null)
				context.unbindService(serviceConnection);
		} catch (Throwable e) {} // Just in case
		
		serviceConnection = null;
		service = null;
	}
}
