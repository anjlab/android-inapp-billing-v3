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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BillingProcessor extends BillingBase
{
	/**
	 * Callback methods where billing events are reported.
	 * Apps must implement one of these to construct a BillingProcessor.
	 */
	public interface IBillingHandler
	{
		void onProductPurchased(String productId, TransactionDetails details);

		void onPurchaseHistoryRestored();

		void onBillingError(int errorCode, Throwable error);

		void onBillingInitialized();
	}

	private static final Date DATE_MERCHANT_LIMIT_1 = new Date(2012, 12, 5); //5th December 2012
	private static final Date DATE_MERCHANT_LIMIT_2 = new Date(2015, 7, 20); //21st July 2015

	private static final int PURCHASE_FLOW_REQUEST_CODE = 32459;
	private static final String LOG_TAG = "iabv3";
	private static final String SETTINGS_VERSION = ".v2_6";
	private static final String RESTORE_KEY = ".products.restored" + SETTINGS_VERSION;
	private static final String MANAGED_PRODUCTS_CACHE_KEY = ".products.cache" + SETTINGS_VERSION;
	private static final String SUBSCRIPTIONS_CACHE_KEY = ".subscriptions.cache" + SETTINGS_VERSION;
	private static final String PURCHASE_PAYLOAD_CACHE_KEY = ".purchase.last" + SETTINGS_VERSION;

	private IInAppBillingService billingService;
	private String contextPackageName;
	private String signatureBase64;
	private BillingCache cachedProducts;
	private BillingCache cachedSubscriptions;
	private IBillingHandler eventHandler;
	private String developerMerchantId;
	private boolean isOneTimePurchasesSupported;
	private boolean isSubsUpdateSupported;

	private class HistoryInitializationTask extends AsyncTask<Void, Void, Boolean>
	{
		@Override
		protected Boolean doInBackground(Void... nothing)
		{
			if (!isPurchaseHistoryRestored())
			{
				loadOwnedPurchasesFromGoogle();
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean restored)
		{
			if (restored)
			{
				setPurchaseHistoryRestored();
				if (eventHandler != null)
				{
					eventHandler.onPurchaseHistoryRestored();
				}
			}
			if (eventHandler != null)
			{
				eventHandler.onBillingInitialized();
			}
		}
	}

	private ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			billingService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			billingService = IInAppBillingService.Stub.asInterface(service);
			new HistoryInitializationTask().execute();
		}
	};

	public BillingProcessor(Context context, String licenseKey, IBillingHandler handler)
	{
		this(context, licenseKey, null, handler);
	}

	public BillingProcessor(Context context, String licenseKey, String merchantId,
							IBillingHandler handler)
	{
		super(context.getApplicationContext());
		signatureBase64 = licenseKey;
		eventHandler = handler;
		contextPackageName = getContext().getPackageName();
		cachedProducts = new BillingCache(getContext(), MANAGED_PRODUCTS_CACHE_KEY);
		cachedSubscriptions = new BillingCache(getContext(), SUBSCRIPTIONS_CACHE_KEY);
		developerMerchantId = merchantId;
		bindPlayServices();
	}

	private static Intent getBindServiceIntent()
	{
		Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		intent.setPackage("com.android.vending");
		return intent;
	}

	private void bindPlayServices()
	{
		try
		{
			getContext().bindService(getBindServiceIntent(), serviceConnection, Context.BIND_AUTO_CREATE);
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "error in bindPlayServices", e);
			reportBillingError(Constants.BILLING_ERROR_BIND_PLAY_STORE_FAILED, e);
		}
	}

	public static boolean isIabServiceAvailable(Context context)
	{
		final PackageManager packageManager = context.getPackageManager();
		List<ResolveInfo> list = packageManager.queryIntentServices(getBindServiceIntent(), 0);
		return list != null && list.size() > 0;
	}

	public void release()
	{
		if (isInitialized() && serviceConnection != null)
		{
			try
			{
				getContext().unbindService(serviceConnection);
			}
			catch (Exception e)
			{
				Log.e(LOG_TAG, "Error in release", e);
			}
			billingService = null;
		}
	}

	public boolean isInitialized()
	{
		return billingService != null;
	}

	public boolean isPurchased(String productId)
	{
		return cachedProducts.includesProduct(productId);
	}

	public boolean isSubscribed(String productId)
	{
		return cachedSubscriptions.includesProduct(productId);
	}

	public List<String> listOwnedProducts()
	{
		return cachedProducts.getContents();
	}

	public List<String> listOwnedSubscriptions()
	{
		return cachedSubscriptions.getContents();
	}

	private boolean loadPurchasesByType(String type, BillingCache cacheStorage)
	{
		if (!isInitialized())
		{
			return false;
		}
		try
		{
			Bundle bundle = billingService.getPurchases(Constants.GOOGLE_API_VERSION,
														contextPackageName, type, null);
			if (bundle.getInt(Constants.RESPONSE_CODE) == Constants.BILLING_RESPONSE_RESULT_OK)
			{
				cacheStorage.clear();
				ArrayList<String> purchaseList =
						bundle.getStringArrayList(Constants.INAPP_PURCHASE_DATA_LIST);
				ArrayList<String> signatureList =
						bundle.getStringArrayList(Constants.INAPP_DATA_SIGNATURE_LIST);
				if (purchaseList != null)
				{
					for (int i = 0; i < purchaseList.size(); i++)
					{
						String jsonData = purchaseList.get(i);

						if (!TextUtils.isEmpty(jsonData))
						{
							JSONObject purchase = new JSONObject(jsonData);
							String signature = signatureList != null && signatureList.size() >
																		i ? signatureList.get(i) : null;
							cacheStorage.put(purchase.getString(Constants.RESPONSE_PRODUCT_ID),
											 jsonData,
											 signature);
						}
					}
				}
			}
			return true;
		}
		catch (Exception e)
		{
			reportBillingError(Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
			Log.e(LOG_TAG, "Error in loadPurchasesByType", e);
		}
		return false;
	}

	public boolean loadOwnedPurchasesFromGoogle()
	{
		return isInitialized() &&
			   loadPurchasesByType(Constants.PRODUCT_TYPE_MANAGED, cachedProducts) &&
			   loadPurchasesByType(Constants.PRODUCT_TYPE_SUBSCRIPTION, cachedSubscriptions);
	}

	public boolean purchase(Activity activity, String productId)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_MANAGED, null);
	}

	public boolean subscribe(Activity activity, String productId)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, null);
	}

	public boolean purchase(Activity activity, String productId, String developerPayload)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_MANAGED, developerPayload);
	}

	public boolean subscribe(Activity activity, String productId, String developerPayload)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, developerPayload);
	}

	public boolean isOneTimePurchaseSupported()
	{
		if (isOneTimePurchasesSupported)
		{
			return true;
		}
		try
		{
			int response = billingService.isBillingSupported(Constants.GOOGLE_API_VERSION,
															 contextPackageName,
															 Constants.PRODUCT_TYPE_MANAGED);
			isOneTimePurchasesSupported = response == Constants.BILLING_RESPONSE_RESULT_OK;
		}
		catch (RemoteException e)
		{
			e.printStackTrace();
		}
		return isOneTimePurchasesSupported;
	}

	public boolean isSubscriptionUpdateSupported()
	{
		// Avoid calling the service again if this value is true
		if (isSubsUpdateSupported)
		{
			return true;
		}

		try
		{
			int response =
					billingService.isBillingSupported(Constants.GOOGLE_API_SUBSCRIPTION_CHANGE_VERSION,
													  contextPackageName,
													  Constants.PRODUCT_TYPE_SUBSCRIPTION);
			isSubsUpdateSupported = response == Constants.BILLING_RESPONSE_RESULT_OK;
		}
		catch (RemoteException e)
		{
			e.printStackTrace();
		}
		return isSubsUpdateSupported;
	}

	/**
	 * Change subscription i.e. upgrade or downgrade
	 *
	 * @param activity     the activity calling this method
	 * @param oldProductId passing null or empty string will act the same as {@link #subscribe(Activity, String)}
	 * @param productId    the new subscription id
	 * @return {@code false} if {@code oldProductId} is not {@code null} AND change subscription
	 * is not supported.
	 */
	public boolean updateSubscription(Activity activity, String oldProductId, String productId)
	{
		List<String> oldProductIds = null;
		if (!TextUtils.isEmpty(oldProductId))
		{
			oldProductIds = new ArrayList<>(1);
			oldProductIds.add(oldProductId);
		}
		return updateSubscription(activity, oldProductIds, productId);
	}

	/**
	 * Change subscription i.e. upgrade or downgrade
	 *
	 * @param activity      the activity calling this method
	 * @param oldProductIds passing null will act the same as {@link #subscribe(Activity, String)}
	 * @param productId     the new subscription id
	 * @return {@code false} if {@code oldProductIds} is not {@code null} AND change subscription
	 * is not supported.
	 */
	public boolean updateSubscription(Activity activity, List<String> oldProductIds,
									  String productId)
	{
		if (oldProductIds != null && !isSubscriptionUpdateSupported())
		{
			return false;
		}
		return purchase(activity, oldProductIds, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, null);
	}

	private boolean purchase(Activity activity, String productId, String purchaseType,
							 String developerPayload)
	{
		return purchase(activity, null, productId, purchaseType, developerPayload);
	}

	private boolean purchase(Activity activity, List<String> oldProductIds, String productId,
							 String purchaseType, String developerPayload)
	{
		if (!isInitialized() || TextUtils.isEmpty(productId) || TextUtils.isEmpty(purchaseType))
		{
			return false;
		}
		try
		{
			String purchasePayload = purchaseType + ":" + productId;
			if (!purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION))
			{
				purchasePayload += ":" + UUID.randomUUID().toString();
			}
			if (developerPayload != null)
			{
				purchasePayload += ":" + developerPayload;
			}
			savePurchasePayload(purchasePayload);
			Bundle bundle;
			if (oldProductIds != null && purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION))
			{
				bundle =
						billingService.getBuyIntentToReplaceSkus(Constants.GOOGLE_API_SUBSCRIPTION_CHANGE_VERSION,
																 contextPackageName,
																 oldProductIds,
																 productId,
																 purchaseType,
																 purchasePayload);
			}
			else
			{
				bundle = billingService.getBuyIntent(Constants.GOOGLE_API_VERSION,
													 contextPackageName,
													 productId,
													 purchaseType,
													 purchasePayload);
			}

			if (bundle != null)
			{
				int response = bundle.getInt(Constants.RESPONSE_CODE);
				if (response == Constants.BILLING_RESPONSE_RESULT_OK)
				{
					PendingIntent pendingIntent = bundle.getParcelable(Constants.BUY_INTENT);
					if (activity != null && pendingIntent != null)
					{
						activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
															PURCHASE_FLOW_REQUEST_CODE,
															new Intent(), 0, 0, 0);
					}
					else
					{
						reportBillingError(Constants.BILLING_ERROR_LOST_CONTEXT, null);
					}
				}
				else if (response == Constants.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED)
				{
					if (!isPurchased(productId) && !isSubscribed(productId))
					{
						loadOwnedPurchasesFromGoogle();
					}
					TransactionDetails details = getPurchaseTransactionDetails(productId);
					if (!checkMerchant(details))
					{
						Log.i(LOG_TAG, "Invalid or tampered merchant id!");
						reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
						return false;
					}
					if (eventHandler != null)
					{
						if (details == null)
						{
							details = getSubscriptionTransactionDetails(productId);
						}
						eventHandler.onProductPurchased(productId, details);
					}
				}
				else
				{
					reportBillingError(Constants.BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE, null);
				}
			}
			return true;
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Error in purchase", e);
			reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
		}
		return false;
	}

	/**
	 * Checks merchant's id validity. If purchase was generated by Freedom alike program it doesn't know
	 * real merchant id, unless publisher GoogleId was hacked
	 * If merchantId was not supplied function checks nothing
	 *
	 * @param details TransactionDetails
	 * @return boolean
	 */
	private boolean checkMerchant(TransactionDetails details)
	{
		if (developerMerchantId == null) //omit merchant id checking
		{
			return true;
		}
		if (details.purchaseTime.before(DATE_MERCHANT_LIMIT_1)) //new format [merchantId].[orderId] applied or not?
		{
			return true;
		}
		if (details.purchaseTime.after(DATE_MERCHANT_LIMIT_2)) //newest format applied
		{
			return true;
		}
		if (details.orderId == null || details.orderId.trim().length() == 0)
		{
			return false;
		}
		int index = details.orderId.indexOf('.');
		if (index <= 0)
		{
			return false; //protect on missing merchant id
		}
		//extract merchant id
		String merchantId = details.orderId.substring(0, index);
		return merchantId.compareTo(developerMerchantId) == 0;
	}

	private TransactionDetails getPurchaseTransactionDetails(String productId, BillingCache cache)
	{
		PurchaseInfo details = cache.getDetails(productId);
		if (details != null && !TextUtils.isEmpty(details.responseData))
		{
			try
			{
				return new TransactionDetails(details);
			}
			catch (JSONException e)
			{
				Log.e(LOG_TAG, "Failed to load saved purchase details for " + productId, e);
			}
		}
		return null;
	}

	public boolean consumePurchase(String productId)
	{
		if (!isInitialized())
		{
			return false;
		}
		try
		{
			TransactionDetails transaction = getPurchaseTransactionDetails(productId, cachedProducts);
			if (transaction != null && !TextUtils.isEmpty(transaction.purchaseToken))
			{
				int response = billingService.consumePurchase(Constants.GOOGLE_API_VERSION,
															  contextPackageName,
															  transaction.purchaseToken);
				if (response == Constants.BILLING_RESPONSE_RESULT_OK)
				{
					cachedProducts.remove(productId);
					Log.d(LOG_TAG, "Successfully consumed " + productId + " purchase.");
					return true;
				}
				else
				{
					reportBillingError(response, null);
					Log.e(LOG_TAG, String.format("Failed to consume %s: %d", productId, response));
				}
			}
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Error in consumePurchase", e);
			reportBillingError(Constants.BILLING_ERROR_CONSUME_FAILED, e);
		}
		return false;
	}

	private SkuDetails getSkuDetails(String productId, String purchaseType)
	{
		ArrayList<String> productIdList = new ArrayList<String>();
		productIdList.add(productId);
		List<SkuDetails> skuDetailsList = getSkuDetails(productIdList, purchaseType);
		if (skuDetailsList != null && skuDetailsList.size() > 0)
		{
			return skuDetailsList.get(0);
		}
		return null;
	}

	private List<SkuDetails> getSkuDetails(ArrayList<String> productIdList, String purchaseType)
	{
		if (billingService != null && productIdList != null && productIdList.size() > 0)
		{
			try
			{
				Bundle products = new Bundle();
				products.putStringArrayList(Constants.PRODUCTS_LIST, productIdList);
				Bundle skuDetails = billingService.getSkuDetails(Constants.GOOGLE_API_VERSION,
																 contextPackageName,
																 purchaseType,
																 products);
				int response = skuDetails.getInt(Constants.RESPONSE_CODE);

				if (response == Constants.BILLING_RESPONSE_RESULT_OK)
				{
					ArrayList<SkuDetails> productDetails = new ArrayList<SkuDetails>();
					List<String> detailsList = skuDetails.getStringArrayList(Constants.DETAILS_LIST);
					if (detailsList != null)
					{
						for (String responseLine : detailsList)
						{
							JSONObject object = new JSONObject(responseLine);
							SkuDetails product = new SkuDetails(object);
							productDetails.add(product);
						}
					}
					return productDetails;
				}
				else
				{
					reportBillingError(response, null);
					Log.e(LOG_TAG, String.format("Failed to retrieve info for %d products, %d",
												 productIdList.size(),
												 response));
				}
			}
			catch (Exception e)
			{
				Log.e(LOG_TAG, "Failed to call getSkuDetails", e);
				reportBillingError(Constants.BILLING_ERROR_SKUDETAILS_FAILED, e);
			}
		}
		return null;
	}

	public SkuDetails getPurchaseListingDetails(String productId)
	{
		return getSkuDetails(productId, Constants.PRODUCT_TYPE_MANAGED);
	}

	public SkuDetails getSubscriptionListingDetails(String productId)
	{
		return getSkuDetails(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION);
	}

	public List<SkuDetails> getPurchaseListingDetails(ArrayList<String> productIdList)
	{
		return getSkuDetails(productIdList, Constants.PRODUCT_TYPE_MANAGED);
	}

	public List<SkuDetails> getSubscriptionListingDetails(ArrayList<String> productIdList)
	{
		return getSkuDetails(productIdList, Constants.PRODUCT_TYPE_SUBSCRIPTION);
	}

	public TransactionDetails getPurchaseTransactionDetails(String productId)
	{
		return getPurchaseTransactionDetails(productId, cachedProducts);
	}

	public TransactionDetails getSubscriptionTransactionDetails(String productId)
	{
		return getPurchaseTransactionDetails(productId, cachedSubscriptions);
	}

	private String getDeveloperPayloadFromPurchaseData(JSONObject purchase)
	{
		String value = null;
		try
		{
			value = purchase.has(Constants.RESPONSE_PAYLOAD)
					? purchase.getString(Constants.RESPONSE_PAYLOAD) : null;
		}
		catch (JSONException e)
		{
			Log.e(LOG_TAG, "Failed to extract developer payload value!");
		}
		return value != null ? value : "";
	}

	private boolean validateDeveloperPayload(String expectedValue, String actualValue)
	{
		return expectedValue.equals(actualValue);
	}

	public boolean handleActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode != PURCHASE_FLOW_REQUEST_CODE)
		{
			return false;
		}
		if (data == null)
		{
			Log.e(LOG_TAG, "handleActivityResult: data is null!");
			return false;
		}
		int responseCode = data.getIntExtra(Constants.RESPONSE_CODE, Constants.BILLING_RESPONSE_RESULT_OK);
		Log.d(LOG_TAG, String.format("resultCode = %d, responseCode = %d", resultCode, responseCode));
		String purchasePayload = getPurchasePayload();
		if (resultCode == Activity.RESULT_OK &&
			responseCode == Constants.BILLING_RESPONSE_RESULT_OK &&
			!TextUtils.isEmpty(purchasePayload))
		{
			String purchaseData = data.getStringExtra(Constants.INAPP_PURCHASE_DATA);
			String dataSignature = data.getStringExtra(Constants.RESPONSE_INAPP_SIGNATURE);
			try
			{
				JSONObject purchase = new JSONObject(purchaseData);
				String productId = purchase.getString(Constants.RESPONSE_PRODUCT_ID);
				String developerPayload = getDeveloperPayloadFromPurchaseData(purchase);
				boolean purchasedSubscription =
						purchasePayload.startsWith(Constants.PRODUCT_TYPE_SUBSCRIPTION);
				if (validateDeveloperPayload(purchasePayload, developerPayload))
				{
					if (verifyPurchaseSignature(productId, purchaseData, dataSignature))
					{
						BillingCache cache =
								purchasedSubscription ? cachedSubscriptions : cachedProducts;
						cache.put(productId, purchaseData, dataSignature);
						if (eventHandler != null)
						{
							eventHandler.onProductPurchased(productId,
															new TransactionDetails(new PurchaseInfo(
																	purchaseData,
																	dataSignature)));
						}
					}
					else
					{
						Log.e(LOG_TAG, "Public key signature doesn't match!");
						reportBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
					}
				}
				else
				{
					Log.e(LOG_TAG, String.format("Payload mismatch: %s != %s",
												 purchasePayload,
												 developerPayload));
					reportBillingError(Constants.BILLING_ERROR_INVALID_DEVELOPER_PAYLOAD, null);
				}
			}
			catch (Exception e)
			{
				Log.e(LOG_TAG, "Error in handleActivityResult", e);
				reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
			}
		}
		else
		{
			reportBillingError(responseCode, null);
		}
		return true;
	}

	private boolean verifyPurchaseSignature(String productId, String purchaseData,
											String dataSignature)
	{
		try
		{
			/*
             * Skip the signature check if the provided License Key is NULL and return true in order to
             * continue the purchase flow
             */
			return TextUtils.isEmpty(signatureBase64) ||
				   Security.verifyPurchase(productId, signatureBase64, purchaseData, dataSignature);
		}
		catch (Exception e)
		{
			return false;
		}
	}

	public boolean isValidTransactionDetails(TransactionDetails transactionDetails)
	{
		return verifyPurchaseSignature(transactionDetails.productId,
									   transactionDetails.purchaseInfo.responseData,
									   transactionDetails.purchaseInfo.signature) &&
			   checkMerchant(transactionDetails);
	}

	private boolean isPurchaseHistoryRestored()
	{
		return loadBoolean(getPreferencesBaseKey() + RESTORE_KEY, false);
	}

	private void setPurchaseHistoryRestored()
	{
		saveBoolean(getPreferencesBaseKey() + RESTORE_KEY, true);
	}

	private void savePurchasePayload(String value)
	{
		saveString(getPreferencesBaseKey() + PURCHASE_PAYLOAD_CACHE_KEY, value);
	}

	private String getPurchasePayload()
	{
		return loadString(getPreferencesBaseKey() + PURCHASE_PAYLOAD_CACHE_KEY, null);
	}

	private void reportBillingError(int errorCode, Throwable error)
	{
		if (eventHandler != null)
		{
			eventHandler.onBillingError(errorCode, error);
		}
	}
}
