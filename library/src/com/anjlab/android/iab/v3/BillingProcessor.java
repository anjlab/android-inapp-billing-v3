/**
 * Copyright 2014 AnjLab
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anjlab.android.iab.v3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

public class BillingProcessor extends BillingBase {

	/**
	 * Callback methods where billing events are reported. Apps must implement
	 * one of these to construct a BillingProcessor.
	 */
	public static interface IBillingHandler {
		void onProductPurchased(String productId);

		void onPurchaseHistoryRestored();

		void onBillingError(int errorCode, Throwable error);

		void onBillingInitialized();
	}

	private static final int PURCHASE_FLOW_REQUEST_CODE = 2061984;
	private static final String LOG_TAG = "viable";
	private static final String SETTINGS_VERSION = ".v2_4";
	private static final String RESTORE_KEY = ".products.restored"
			+ SETTINGS_VERSION;
	private static final String MANAGED_PRODUCTS_CACHE_KEY = ".products.cache"
			+ SETTINGS_VERSION;
	private static final String SUBSCRIPTIONS_CACHE_KEY = ".subscriptions.cache"
			+ SETTINGS_VERSION;
	private static final String PRODUCTS_LIST = "ITEM_ID_LIST";
	private static final String IN_APP_LIST = "inapp";
	private static final String KEY_RESPONSE_CODE = "RESPONSE_CODE";
	private static final String KEY_DETAIL_LIST = "DETAILS_LIST";
	private static final String KEY_PRODUCT_ID = "productId";
	private static final String KEY_PRICE = "price";

	private IInAppBillingService billingService;
	private String contextPackageName;
	private String purchasePayload;
	private String signatureBase64;
	private BillingCache cachedProducts;
	private BillingCache cachedSubscriptions;
	private IBillingHandler eventHandler;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			billingService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			billingService = IInAppBillingService.Stub.asInterface(service);
			if (!isPurchaseHistoryRestored() && loadOwnedPurchasesFromGoogle()) {
				setPurchaseHistoryRestored();
				if (eventHandler != null)
					eventHandler.onPurchaseHistoryRestored();
			}
			if (eventHandler != null)
				eventHandler.onBillingInitialized();
		}
	};

	public String getPriceOfSku(String sku, String packageName) {
		if (billingService != null) {
			try {
				ArrayList<String> skuList = new ArrayList<String>();
				skuList.add(sku);
				Bundle querySkus = new Bundle();
				querySkus.putStringArrayList(PRODUCTS_LIST, skuList);
				Bundle skuDetails = billingService.getSkuDetails(3,
						packageName, IN_APP_LIST, querySkus);
				int response = skuDetails.getInt(KEY_RESPONSE_CODE);
				if (response == 0) {
					ArrayList<String> responseList = skuDetails
							.getStringArrayList(KEY_DETAIL_LIST);
					for (String thisResponse : responseList) {
						JSONObject object = new JSONObject(thisResponse);
						String productSKU = object.getString(KEY_PRODUCT_ID);
						if (sku.equals(productSKU)) {
							return object.getString(KEY_PRICE);
						}

					}
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public BillingProcessor(Activity context, String licenseKey,
			IBillingHandler handler) {
		super(context);
		signatureBase64 = licenseKey;
		eventHandler = handler;
		contextPackageName = context.getApplicationContext().getPackageName();
		cachedProducts = new BillingCache(context, MANAGED_PRODUCTS_CACHE_KEY);
		cachedSubscriptions = new BillingCache(context, SUBSCRIPTIONS_CACHE_KEY);
		bindPlayServices();
	}

	private void bindPlayServices() {
		try {
			getContext()
					.bindService(
							new Intent(
									"com.android.vending.billing.InAppBillingService.BIND"),
							serviceConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
			Log.e(LOG_TAG, e.toString());
		}
	}

	@Override
	public void release() {
		if (serviceConnection != null && getContext() != null) {
			try {
				getContext().unbindService(serviceConnection);
			} catch (Exception e) {
				Log.e(LOG_TAG, e.toString());
			}
			billingService = null;
		}
		cachedProducts.release();
		super.release();
	}

	public boolean isInitialized() {
		return billingService != null;
	}

	public boolean isPurchased(String productId) {
		return cachedProducts.includesProduct(productId);
	}

	public boolean isSubscribed(String productId) {
		return cachedSubscriptions.includesProduct(productId);
	}

	public List<String> listOwnedProducts() {
		return cachedProducts.getContents();
	}

	public List<String> listOwnedSubscriptions() {
		return cachedSubscriptions.getContents();
	}

	private boolean loadPurchasesByType(String type, BillingCache cacheStorage) {
		if (!isInitialized())
			return false;
		try {
			Bundle bundle = billingService.getPurchases(
					Constants.GOOGLE_API_VERSION, contextPackageName, type,
					null);
			if (bundle.getInt(Constants.RESPONSE_CODE) == Constants.BILLING_RESPONSE_RESULT_OK) {
				cacheStorage.clear();
				for (String purchaseData : bundle
						.getStringArrayList(Constants.INAPP_PURCHASE_DATA_LIST)) {
					JSONObject purchase = new JSONObject(purchaseData);
					cacheStorage.put(purchase.getString("productId"),
							purchase.getString("purchaseToken"));
				}
			}
			return true;
		} catch (Exception e) {
			if (eventHandler != null)
				eventHandler.onBillingError(
						Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
			Log.e(LOG_TAG, e.toString());
		}
		return false;
	}

	public boolean loadOwnedPurchasesFromGoogle() {
		return isInitialized()
				&& loadPurchasesByType(Constants.PRODUCT_TYPE_MANAGED,
						cachedProducts)
				&& loadPurchasesByType(Constants.PRODUCT_TYPE_SUBSCRIPTION,
						cachedSubscriptions);
	}

	public boolean purchase(String productId) {
		return purchase(productId, Constants.PRODUCT_TYPE_MANAGED,
				cachedProducts);
	}

	public boolean subscribe(String productId) {
		return purchase(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION,
				cachedSubscriptions);
	}

	private boolean purchase(String productId, String purchaseType,
			BillingCache cacheStorage) {
		if (!isInitialized())
			return false;
		try {
			purchasePayload = UUID.randomUUID().toString();
			Bundle bundle = billingService.getBuyIntent(
					Constants.GOOGLE_API_VERSION, contextPackageName,
					productId, purchaseType, purchasePayload);
			if (bundle != null) {
				int response = bundle.getInt(Constants.RESPONSE_CODE);
				if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
					PendingIntent pendingIntent = bundle
							.getParcelable(Constants.BUY_INTENT);
					if (getContext() != null)
						getContext().startIntentSenderForResult(
								pendingIntent.getIntentSender(),
								PURCHASE_FLOW_REQUEST_CODE, new Intent(),
								Integer.valueOf(0), Integer.valueOf(0),
								Integer.valueOf(0));
					else if (eventHandler != null)
						eventHandler.onBillingError(
								Constants.BILLING_ERROR_LOST_CONTEXT, null);
				} else if (response == Constants.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
					if (!isPurchased(productId) && !isSubscribed(productId))
						loadOwnedPurchasesFromGoogle();
					if (eventHandler != null)
						eventHandler.onProductPurchased(productId);
				} else if (eventHandler != null)
					eventHandler
							.onBillingError(
									Constants.BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE,
									null);
			}
			return true;
		} catch (Exception e) {
			Log.e(LOG_TAG, e.toString());
		}
		return false;
	}

	public boolean consumePurchase(String productId) {
		if (!isInitialized())
			return false;
		try {
			String purchaseToken = cachedProducts
					.getProductPurchaseToken(productId);
			if (!TextUtils.isEmpty(purchaseToken)) {
				int response = billingService.consumePurchase(
						Constants.GOOGLE_API_VERSION, contextPackageName,
						purchaseToken);
				if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
					cachedProducts.remove(productId);
					Log.d(LOG_TAG, "Successfully consumed " + productId
							+ " purchase.");
					return true;
				} else {
					if (eventHandler != null)
						eventHandler.onBillingError(response, null);
					Log.e(LOG_TAG, String.format(
							"Failed to consume %s: error %d", productId,
							response));
				}
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, e.toString());
		}
		return false;
	}

	public boolean handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (requestCode != PURCHASE_FLOW_REQUEST_CODE)
			return false;
		int responseCode = data.getIntExtra(Constants.RESPONSE_CODE,
				Constants.BILLING_RESPONSE_RESULT_OK);
		if (resultCode == Activity.RESULT_OK
				&& responseCode == Constants.BILLING_RESPONSE_RESULT_OK) {
			String purchaseData = data
					.getStringExtra(Constants.INAPP_PURCHASE_DATA);
			String dataSignature = data
					.getStringExtra(Constants.RESPONSE_INAPP_SIGNATURE);
			try {
				JSONObject purchase = new JSONObject(purchaseData);
				String productId = purchase.getString("productId");
				String purchaseToken = purchase.getString("purchaseToken");
				String developerPayload = purchase
						.getString("developerPayload");
				if (purchasePayload.equals(developerPayload)) {
					if (verifyPurchaseSignature(purchaseData, dataSignature)) {
						cachedProducts.put(productId, purchaseToken);
						if (eventHandler != null)
							eventHandler.onProductPurchased(productId);
					} else {
						Log.e(LOG_TAG, "Public key signature doesn't match!");
						if (eventHandler != null)
							eventHandler.onBillingError(
									Constants.BILLING_ERROR_INVALID_SIGNATURE,
									null);
					}
				} else {
					Log.e(LOG_TAG, String.format("Payload mismatch: %s != %s",
							purchasePayload, developerPayload));
					if (eventHandler != null)
						eventHandler
								.onBillingError(
										Constants.BILLING_ERROR_INVALID_SIGNATURE,
										null);
				}
			} catch (Exception e) {
				Log.e(LOG_TAG, e.toString());
				if (eventHandler != null)
					eventHandler.onBillingError(
							Constants.BILLING_ERROR_OTHER_ERROR, null);
			}
		} else {
			if (eventHandler != null)
				eventHandler.onBillingError(
						Constants.BILLING_ERROR_OTHER_ERROR, null);
		}
		return true;
	}

	private boolean verifyPurchaseSignature(String purchaseData,
			String dataSignature) {
		if (!TextUtils.isEmpty(signatureBase64)) {
			try {
				return Security.verifyPurchase(signatureBase64, purchaseData,
						dataSignature);
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}

	private boolean isPurchaseHistoryRestored() {
		return loadBoolean(getPreferencesBaseKey() + RESTORE_KEY, false);
	}

	public void setPurchaseHistoryRestored() {
		saveBoolean(getPreferencesBaseKey() + RESTORE_KEY, true);
	}

}
