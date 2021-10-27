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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;


import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetailsParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
		void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details);

		void onPurchaseHistoryRestored();

		void onBillingError(int errorCode, @Nullable Throwable error);

		void onBillingInitialized();
	}

	/**
	 * Callback methods for notifying about success or failure attempt to fetch purchases from the server.
	 */
	public interface IPurchasesResponseListener {
		void onPurchasesSuccess();

		void onPurchasesError();
	}

	/**
	 * Callback methods where result of SkuDetails fetch returned or error message on failure.
	 */
	public interface ISkuDetailsResponseListener {
		void onSkuDetailsResponse(@Nullable List<SkuDetails> products);

		void onSkuDetailsError(String error);
	}

	private static final Date DATE_MERCHANT_LIMIT_1; //5th December 2012
	private static final Date DATE_MERCHANT_LIMIT_2; //21st July 2015

	static
	{
		Calendar calendar = Calendar.getInstance();
		calendar.set(2012, Calendar.DECEMBER, 5);
		DATE_MERCHANT_LIMIT_1 = calendar.getTime();
		calendar.set(2015, Calendar.JULY, 21);
		DATE_MERCHANT_LIMIT_2 = calendar.getTime();
	}

	private static final int PURCHASE_FLOW_REQUEST_CODE = 32459;
	private static final String LOG_TAG = "iabv3";
	private static final String SETTINGS_VERSION = ".v2_6";
	private static final String RESTORE_KEY = ".products.restored" + SETTINGS_VERSION;
	private static final String MANAGED_PRODUCTS_CACHE_KEY = ".products.cache" + SETTINGS_VERSION;
	private static final String SUBSCRIPTIONS_CACHE_KEY = ".subscriptions.cache" + SETTINGS_VERSION;
	private static final String PURCHASE_PAYLOAD_CACHE_KEY = ".purchase.last" + SETTINGS_VERSION;

	private static final long RECONNECT_TIMER_START_MILLISECONDS = 1000L;
	private static final long RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 15L;

	private long reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS;

	private BillingClient billingService;
	private String contextPackageName;
	private String signatureBase64;
	private BillingCache cachedProducts;
	private BillingCache cachedSubscriptions;
	private IBillingHandler eventHandler;
	private String developerMerchantId;
	private boolean isOneTimePurchasesSupported;
	private boolean isSubsUpdateSupported;
	private boolean isSubscriptionExtraParamsSupported;
	private boolean isOneTimePurchaseExtraParamsSupported;
	private boolean isHistoryTaskExecuted = false;

	private Handler handler = new Handler(Looper.getMainLooper());

	private class HistoryInitializationTask extends AsyncTask<Void, Void, Boolean>
	{
		@Override
		protected Boolean doInBackground(Void... nothing)
		{
			if (!isPurchaseHistoryRestored())
			{
				loadOwnedPurchasesFromGoogleAsync(null);
				return true;
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean restored)
		{

			isHistoryTaskExecuted = true;

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

	/**
	 * Returns a new {@link BillingProcessor}, without immediately binding to Play Services. If you use
	 * this factory, then you must call {@link #initialize()} afterwards.
	 */
	public static BillingProcessor newBillingProcessor(Context context, String licenseKey, IBillingHandler handler)
	{
		return newBillingProcessor(context, licenseKey, null, handler);
	}

	/**
	 * Returns a new {@link BillingProcessor}, without immediately binding to Play Services. If you use
	 * this factory, then you must call {@link #initialize()} afterwards.
	 */
	public static BillingProcessor newBillingProcessor(Context context, String licenseKey, String merchantId,
													   IBillingHandler handler)
	{
		return new BillingProcessor(context, licenseKey, merchantId, handler, false);
	}

	public BillingProcessor(Context context, String licenseKey, IBillingHandler handler)
	{
		this(context, licenseKey, null, handler);
	}

	public BillingProcessor(Context context, String licenseKey, String merchantId,
							IBillingHandler handler)
	{
		this(context, licenseKey, merchantId, handler, true);
	}

	private BillingProcessor(Context context, String licenseKey, String merchantId, IBillingHandler handler,
							 boolean bindImmediately)
	{
		super(context.getApplicationContext());
		signatureBase64 = licenseKey;
		eventHandler = handler;
		contextPackageName = getContext().getPackageName();
		cachedProducts = new BillingCache(getContext(), MANAGED_PRODUCTS_CACHE_KEY);
		cachedSubscriptions = new BillingCache(getContext(), SUBSCRIPTIONS_CACHE_KEY);
		developerMerchantId = merchantId;
		init(context);
		if (bindImmediately)
		{
			initialize();
		}
	}

	private static Intent getBindServiceIntent()
	{
		Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		intent.setPackage("com.android.vending");
		return intent;
	}

	public static boolean isIabServiceAvailable(Context context)
	{
		final PackageManager packageManager = context.getPackageManager();
		List<ResolveInfo> list = packageManager.queryIntentServices(getBindServiceIntent(), 0);
		return list != null && list.size() > 0;
	}

	private void init(Context context)
	{
		billingService = BillingClient.newBuilder(context)
				.enablePendingPurchases()
				.setListener(purchasesUpdatedListener)
				.build();

	}

	/**
	 * Establishing Connection to Google Play
	 * you should call this method if used {@link #newBillingProcessor} method or called constructor
	 * with bindImmediately = false
	 */
	public void initialize()
	{
		if (billingService != null && !billingService.isReady())
		{
			billingService.startConnection(new BillingClientStateListener()
			{
				@Override
				public void onBillingSetupFinished(@NonNull BillingResult billingResult)
				{
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
					{
						reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS;
						// The BillingClient is ready. You can query purchases here.
						Log.d("GooglePlayConnection; ", "IsConnected");

						//Initialize history of purchases if any exist.
						if (!isHistoryTaskExecuted) {
							new HistoryInitializationTask().execute();
						}
					}
					else
						{
						retryBillingClientConnection();
						reportBillingError(billingResult.getResponseCode(), new Throwable(billingResult.getDebugMessage()));
					}
				}

				@Override
				public void onBillingServiceDisconnected()
				{
					Log.d("ServiceDisconnected; ", "BillingServiceDisconnected, trying new Connection");

					//retrying connection to GooglePlay
					if (!isConnected())
					{
						retryBillingClientConnection();
					}
				}
			});
		}

	}

	/**
	 * Retries the billing client connection with exponential backoff
	 * Max out at the time specified by RECONNECT_TIMER_MAX_TIME_MILLISECONDS (15 minutes)
	 */
	private void retryBillingClientConnection()
	{
		handler.postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				initialize();
			}
		}, reconnectMilliseconds);

		reconnectMilliseconds = Math.min(reconnectMilliseconds * 2, RECONNECT_TIMER_MAX_TIME_MILLISECONDS);
	}

	/**
	 *  Check for billingClient is initialized and connected, if true then its ready for use.
	 * */
	public boolean isConnected()
	{
		return isInitialized() &&  billingService.isReady();
	}


	/**
	 * This method should be called when you are done with BillingProcessor.
	 * BillingClient object holds a binding to the in-app billing service and the manager to handle
	 * broadcast events, which will leak unless you dispose it correctly.
	**/
	public void release()
	{
		if (isConnected())
		{
			Log.d(LOG_TAG, "BillingClient can only be used once -- closing connection");
			billingService.endConnection();
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

	private void loadPurchasesByTypeAsync(String type, final BillingCache cacheStorage, final IPurchasesResponseListener listener)
	{
		if (!isConnected())
		{
			reportPurchasesError(listener);
			retryBillingClientConnection();
			return;
		}

		billingService.queryPurchasesAsync(type, new PurchasesResponseListener()
		{
			@Override
			public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list)
			{
				if (billingResult.getResponseCode() == Constants.BILLING_RESPONSE_RESULT_OK)
				{
					cacheStorage.clear();

					ArrayList<Purchase> purchaseList = new ArrayList<>(list);
					if (purchaseList != null)
					{
						for (int i = 0; i < purchaseList.size(); i++)
						{
							String jsonData = purchaseList.get(i).getOriginalJson();  //getting String ArrayList for purchases
							String signatureList = purchaseList.get(i).getSignature(); //getting String ArrayList for signatures

							if (!TextUtils.isEmpty(jsonData))
							{
								try {
									/**
									 *
									 * This is a replacement for the bundling in the old version
									 * here we query all users' purchases and save it locally
									 * However, it is also recommended to save and verify all purchases on own server
									 * */
									JSONObject purchase = new JSONObject(jsonData);
									String signature = signatureList != null && signatureList.length() >
											i ? purchaseList.get(i).getSignature() : null;
									cacheStorage.put(purchase.getString(Constants.RESPONSE_PRODUCT_ID),
											jsonData,
											signature);


								} catch (Exception e)
								{
									reportBillingError(Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
									Log.e(LOG_TAG, "Error in loadPurchasesByType", e);
									reportPurchasesError(listener);
								}
							}
						}

						reportPurchasesSuccess(listener);
					}
				}
				else
				{
					reportPurchasesError(listener);
				}
			}
		});
	}

	/**
	 * Attempt to fetch purchases from the server and update our cache if successful
	 *
	 * @param listener invokes method onPurchasesError if all retrievals are failure,
	 *                    onPurchasesSuccess if even one retrieval succeeded
	 */
	public void loadOwnedPurchasesFromGoogleAsync(final IPurchasesResponseListener listener)
	{
		loadPurchasesByTypeAsync(Constants.PRODUCT_TYPE_MANAGED, cachedProducts, new IPurchasesResponseListener()
		{
			@Override
			public void onPurchasesSuccess()
			{
				loadPurchasesByTypeAsync(Constants.PRODUCT_TYPE_SUBSCRIPTION, cachedSubscriptions, new IPurchasesResponseListener()
				{
					@Override
					public void onPurchasesSuccess()
					{
						BillingProcessor.this.reportPurchasesSuccess(listener);
					}

					@Override
					public void onPurchasesError()
					{
						BillingProcessor.this.reportPurchasesError(listener);
					}
				});
			}

			@Override
			public void onPurchasesError()
			{
				loadPurchasesByTypeAsync(Constants.PRODUCT_TYPE_SUBSCRIPTION, cachedSubscriptions, new IPurchasesResponseListener()
				{
					@Override
					public void onPurchasesSuccess()
					{
						BillingProcessor.this.reportPurchasesError(listener);
					}

					@Override
					public void onPurchasesError()
					{
						BillingProcessor.this.reportPurchasesError(listener);
					}
				});
			}
		});
	}

	public boolean purchase(Activity activity, String productId)
	{
		return purchase(activity, null, productId, Constants.PRODUCT_TYPE_MANAGED, null);
	}

	public boolean subscribe(Activity activity, String productId)
	{
		return purchase(activity, null, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, null);
	}

	public boolean purchase(Activity activity, String productId, String developerPayload)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_MANAGED, developerPayload);
	}

	public boolean subscribe(Activity activity, String productId, String developerPayload)
	{
		return purchase(activity, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, developerPayload);
	}

	/***
	 * Purchase a product
	 *
	 * @param activity the activity calling this method
	 * @param productId the product id to purchase
	 * @param extraParams A bundle object containing extra parameters to pass to
	 *                          getBuyIntentExtraParams()
	 * @see <a href="https://developer.android.com/google/play/billing/billing_reference.html#getBuyIntentExtraParams">extra
	 * params documentation on developer.android.com</a>
	 * @return {@code false} if the billing system is not initialized, {@code productId} is empty
	 * or if an exception occurs. Will return {@code true} otherwise.
	 */
	public boolean purchase(Activity activity, String productId, String developerPayload, Bundle extraParams)
	{
		if (!isOneTimePurchaseWithExtraParamsSupported(extraParams))
		{
			return purchase(activity, productId, developerPayload);
		}
		else
		{
			return purchase(activity, null, productId, Constants.PRODUCT_TYPE_MANAGED, developerPayload, extraParams);
		}
	}

	/**
	 * Subscribe to a product
	 *
	 * @param activity    the activity calling this method
	 * @param productId   the product id to purchase
	 * @param extraParams A bundle object containing extra parameters to pass to getBuyIntentExtraParams()
	 * @return {@code false} if the billing system is not initialized, {@code productId} is empty or if an exception occurs.
	 * Will return {@code true} otherwise.
	 * @see <a href="https://developer.android.com/google/play/billing/billing_reference.html#getBuyIntentExtraParams">extra
	 * params documentation on developer.android.com</a>
	 */
	public boolean subscribe(Activity activity, String productId, String developerPayload, Bundle extraParams)
	{
		return purchase(activity,
						null,
						productId,
						Constants.PRODUCT_TYPE_SUBSCRIPTION,
						developerPayload,
						isSubscriptionWithExtraParamsSupported(extraParams) ? extraParams : null);
	}

	/*
	 * todo: work on Onetimepurchase and handle all pending orders
	 *  currently all pending orders are refunded after 3days if not acknowledged
	 *  */

	/**
	 * @deprecated always returns true.
	 */
	@Deprecated
	public boolean isOneTimePurchaseSupported()
	{
		isOneTimePurchasesSupported = true;
		return isOneTimePurchasesSupported;
	}

	public boolean isSubscriptionUpdateSupported()
	{
		// Avoid calling the service again if this value is true
		if (isSubsUpdateSupported)
		{
			return true;
		}

		if (!isConnected())
		{
			return false;
		}

		BillingResult featureSupported = billingService.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE);
		int response = featureSupported.getResponseCode();

		if (response == BillingClient.BillingResponseCode.OK)
		{
			isSubsUpdateSupported = true;
		}

		return isSubsUpdateSupported;
	}

	/**
	 * Check API v7 support for subscriptions
	 *
	 * @param extraParams
	 * @return {@code true} if the current API supports calling getBuyIntentExtraParams() for
	 * subscriptions, {@code false} otherwise.
	 */
	public boolean isSubscriptionWithExtraParamsSupported(Bundle extraParams)
	{
		if (isSubscriptionExtraParamsSupported)
		{
			return true;
		}

		if(!isConnected())
		{
			return false;
		}

		BillingResult billingResult = billingService.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_ON_VR);

		int response = billingResult.getResponseCode();

		if (response == BillingClient.BillingResponseCode.OK)
		{
			isSubscriptionExtraParamsSupported = true;
		}

		return isSubscriptionExtraParamsSupported;
	}

	/**
	 * Check API v7 support for one-time purchases
	 *
	 * @param extraParams
	 * @return {@code true} if the current API supports calling getBuyIntentExtraParams() for
	 * one-time purchases, {@code false} otherwise.
	 */
	public boolean isOneTimePurchaseWithExtraParamsSupported(Bundle extraParams)
	{
		if (isOneTimePurchaseExtraParamsSupported)
		{
			return true;
		}

		if(!isConnected())
		{
			return false;
		}

		//For VR supported versions
		BillingResult billingResult = billingService.isFeatureSupported(BillingClient.FeatureType.IN_APP_ITEMS_ON_VR);

		int response = billingResult.getResponseCode();
		if (response == BillingClient.BillingResponseCode.OK)
		{
			isOneTimePurchaseExtraParamsSupported = true;
		}

		return isOneTimePurchaseExtraParamsSupported;
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
		return updateSubscription(activity, oldProductId, productId, null);
	}

	/**
	 * Change subscription i.e. upgrade or downgrade
	 *
	 * @param activity         the activity calling this method
	 * @param oldProductId     passing null or empty string will act the same as {@link #subscribe(Activity, String)}
	 * @param productId        the new subscription id
	 * @param developerPayload the developer payload
	 * @return {@code false} if {@code oldProductId} is not {@code null} AND change subscription
	 * is not supported.
	 */
	public boolean updateSubscription(Activity activity, String oldProductId, String productId, String developerPayload)
	{
		List<String> oldProductIds = null;
		if (!TextUtils.isEmpty(oldProductId))
		{
			oldProductIds = Collections.singletonList(oldProductId);
		}
		return updateSubscription(activity, oldProductIds, productId, developerPayload);
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
		return updateSubscription(activity, oldProductIds, productId, null);
	}

	/**
	 * Change subscription i.e. upgrade or downgrade
	 *
	 * @param activity         the activity calling this method
	 * @param oldProductIds    passing null will act the same as {@link #subscribe(Activity, String)}
	 * @param productId        the new subscription id
	 * @param developerPayload the developer payload
	 * @return {@code false} if {@code oldProductIds} is not {@code null} AND change subscription
	 * is not supported.
	 */
	public boolean updateSubscription(Activity activity, List<String> oldProductIds,
									  String productId, String developerPayload)
	{
		if (oldProductIds != null && !isSubscriptionUpdateSupported())
		{
			return false;
		}
		return purchase(activity, oldProductIds, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, developerPayload);
	}

	/**
	 * @param activity         the activity calling this method
	 * @param oldProductIds    passing null will act the same as {@link #subscribe(Activity, String)}
	 * @param productId        the new subscription id
	 * @param developerPayload the developer payload
	 * @param extraParams      A bundle object containing extra parameters to pass to getBuyIntentExtraParams()
	 * @return {@code false} if {@code oldProductIds} is not {@code null} AND change subscription
	 * is not supported.
	 * @see <a href="https://developer.android.com/google/play/billing/billing_reference.html#getBuyIntentExtraParams">extra
	 * params documentation on developer.android.com</a>
	 */
	public boolean updateSubscription(Activity activity, List<String> oldProductIds,
									  String productId, String developerPayload, Bundle extraParams)
	{
		if (oldProductIds != null && !isSubscriptionUpdateSupported())
		{
			return false;
		}

		// if API v7 is not supported, let's fallback to the previous method
		if (!isSubscriptionWithExtraParamsSupported(extraParams))
		{
			return updateSubscription(activity, oldProductIds, productId, developerPayload);
		}

		return purchase(activity,
						oldProductIds,
						productId,
						Constants.PRODUCT_TYPE_SUBSCRIPTION,
						developerPayload,
						extraParams);
	}

	private boolean purchase(Activity activity, String productId, String purchaseType,
							 String developerPayload)
	{
		return purchase(activity, null, productId, purchaseType, developerPayload);
	}

	private boolean purchase(Activity activity, List<String> oldProductIds, String productId,
							 String purchaseType, String developerPayload)
	{
		return purchase(activity, oldProductIds, productId, purchaseType, developerPayload, null);
	}

	private boolean purchase(final Activity activity, final List<String> oldProductIds, final String productId,
							 String purchaseType, String developerPayload, Bundle extraParamsBundle)
	{
		if (!isConnected() || TextUtils.isEmpty(productId) || TextUtils.isEmpty(purchaseType))
		{
			if (!isConnected())
			{
				retryBillingClientConnection();
			}

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

			SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
			List<String> skuList = new ArrayList<>();
			skuList.add(productId);
			if (!skuList.isEmpty())
			{
				params.setSkusList(skuList).setType(purchaseType);
				final String finalPurchasePayload = purchasePayload;
				billingService.querySkuDetailsAsync(params.build(),
						new com.android.billingclient.api.SkuDetailsResponseListener()
						{
							@Override
							public void onSkuDetailsResponse(BillingResult billingResult,
															 List<com.android.billingclient.api.SkuDetails> skuDetailsList)
							{

								if (!skuDetailsList.isEmpty())
								{
									final com.android.billingclient.api.SkuDetails skuDetails
											= skuDetailsList.get(skuDetailsList.size() - 1);
										skuDetails.getPrice();
										skuDetails.getTitle();
										skuDetails.getDescription();
										skuDetails.getSku();
										skuDetails.getIntroductoryPrice();
										skuDetails.getFreeTrialPeriod();

									Handler handler = new Handler(Looper.getMainLooper());

									handler.post(new Runnable()
									{
										@Override
										public void run()
										{
											BillingFlowParams.Builder billingFlowParamsBuilder = BillingFlowParams.newBuilder();
											billingFlowParamsBuilder.setSkuDetails(skuDetails);

											if (oldProductIds != null && oldProductIds.size() > 0)
											{
												TransactionDetails oldProductDetails = getSubscriptionTransactionDetails(oldProductIds.get(0));

												if (oldProductDetails != null)
												{
													billingFlowParamsBuilder.setSubscriptionUpdateParams(
															BillingFlowParams.SubscriptionUpdateParams
																	.newBuilder()
																	.setOldSkuPurchaseToken(oldProductDetails.purchaseInfo.purchaseData.purchaseToken)
																	.build());
												}
											}


											BillingFlowParams billingFlowParams = billingFlowParamsBuilder.build();


											int responseCode = billingService.launchBillingFlow(activity, billingFlowParams).getResponseCode();
											String responseMessage = "Billing response; ";

											if (responseCode == BillingClient.BillingResponseCode.OK) {
												Log.d("ReadyToPurchase", "Launch Billing Flow Successful");
											}

											if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
											{
												if (!isPurchased(productId) && !isSubscribed(productId))
												{
													loadOwnedPurchasesFromGoogleAsync(new IPurchasesResponseListener()
													{
														@Override
														public void onPurchasesSuccess()
														{
															TransactionDetails details = getPurchaseTransactionDetails(productId);
															if (!checkMerchant(details))
															{
																Log.i(LOG_TAG, "Invalid or tampered merchant id!");
																reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
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

														@Override
														public void onPurchasesError()
														{
															TransactionDetails details = getPurchaseTransactionDetails(productId);
															if (!checkMerchant(details))
															{
																Log.i(LOG_TAG, "Invalid or tampered merchant id!");
																reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
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
													});

												}
												else
												{
													TransactionDetails details = getPurchaseTransactionDetails(productId);
													if (!checkMerchant(details))
													{
														Log.i(LOG_TAG, "Invalid or tampered merchant id!");
														reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
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
											}

											if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
												//               showToast(responseMessage + "user cancel");
												Log.i(LOG_TAG, "User Cancelled launch flow");
											}
											// Handle the result.
										}
									});

								}
								else
								{
									// This will occur if product id does not match with the product type
									Log.d("onSkuResponse: ", "product id mismatch with Product type");
								}
							}
						});
			}
			else
			{
				Log.d("onProductId: ", "product id not specified");
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
		if (details.purchaseInfo.purchaseData.purchaseTime.before(DATE_MERCHANT_LIMIT_1)) //newest format applied
		{
			return true;
		}
		if (details.purchaseInfo.purchaseData.purchaseTime.after(DATE_MERCHANT_LIMIT_2)) //newest format applied
		{
			return true;
		}
		if (details.purchaseInfo.purchaseData.orderId == null ||
			details.purchaseInfo.purchaseData.orderId.trim().length() == 0)
		{
			return false;
		}
		int index = details.purchaseInfo.purchaseData.orderId.indexOf('.');
		if (index <= 0)
		{
			return false; //protect on missing merchant id
		}
		//extract merchant id
		String merchantId = details.purchaseInfo.purchaseData.orderId.substring(0, index);
		return merchantId.compareTo(developerMerchantId) == 0;
	}

	@Nullable
	private TransactionDetails getPurchaseTransactionDetails(String productId, BillingCache cache)
	{
		PurchaseInfo details = cache.getDetails(productId);
		if (details != null && !TextUtils.isEmpty(details.responseData))
		{
			return new TransactionDetails(details);
		}
		return null;
	}

	public void consumePurchaseAsync(final String productId, final IPurchasesResponseListener listener)
	{
		if (!isConnected())
		{
			reportPurchasesError(listener);
		}

		final ConsumeResponseListener consumeListener = new ConsumeResponseListener()
		{
			@Override
			public void onConsumeResponse(BillingResult billingResult, String purchaseToken)
			{
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
				{
					cachedProducts.remove(productId);
					Log.d(LOG_TAG, "Successfully consumed " + productId + " purchase.");

					reportPurchasesSuccess(listener);
				} else {
					Log.d(LOG_TAG, "Failure consume " + productId + " purchase.");
					reportBillingError(Constants.BILLING_ERROR_CONSUME_FAILED, new Exception(billingResult.getDebugMessage()));
					reportPurchasesError(listener);
				}
			}
		};


		try
		{
			TransactionDetails transaction = getPurchaseTransactionDetails(productId, cachedProducts);
			if (transaction != null && !TextUtils.isEmpty(transaction.purchaseInfo.purchaseData.purchaseToken))
			{
				billingService.queryPurchasesAsync(productId, new PurchasesResponseListener()
				{
					@Override
					public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list)
					{
						int response = billingResult.getResponseCode();

						if (response == Constants.BILLING_RESPONSE_RESULT_OK && !list.isEmpty())
						{

							Purchase purchase = list.get(0);
							ConsumeParams consumeParams =
									ConsumeParams.newBuilder()
											.setPurchaseToken(purchase.getPurchaseToken())
											.build();

							billingService.consumeAsync(consumeParams, consumeListener);
						}
						else
						{
							Log.e(LOG_TAG, String.format("Failed to consume %s: %d", productId, response));
							reportBillingError(response, null);
							reportPurchasesError(listener);
						}
					}
				});
			}
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Error in consumePurchase", e);
			reportBillingError(Constants.BILLING_ERROR_CONSUME_FAILED, e);
			reportPurchasesError(listener);
		}
	}

	private void getSkuDetailsAsync(final String productId, String purchaseType, final ISkuDetailsResponseListener listener)
	{
		ArrayList<String> productIdList = new ArrayList<String>();
		productIdList.add(productId);
		getSkuDetailsAsync(productIdList, purchaseType, new ISkuDetailsResponseListener()
		{
			@Override
			public void onSkuDetailsResponse(@Nullable List<SkuDetails> products)
			{
				if (products != null)
				{
					if (listener != null)
					{
						reportSkuDetailsResponseCaller(products, listener);
					}
				}
			}

			@Override
			public void onSkuDetailsError(String string)
			{
				reportSkuDetailsErrorCaller(string, listener);
			}
		});
	}

	private void getSkuDetailsAsync(final ArrayList<String> productIdList, String purchaseType, final ISkuDetailsResponseListener listener)
	{
		if (billingService != null && billingService.isReady() && productIdList != null && productIdList.size() > 0)
		{
			try
			{
				Bundle products = new Bundle();
				products.putStringArrayList(Constants.PRODUCTS_LIST, productIdList);
				SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder()
						.setSkusList(productIdList)
						.setType(purchaseType)
						.build();
				final ArrayList<SkuDetails> productDetails = new ArrayList<SkuDetails>();

				billingService.querySkuDetailsAsync(skuDetailsParams, new com.android.billingclient.api.SkuDetailsResponseListener()
				{
					@Override
					public void onSkuDetailsResponse(BillingResult billingResult, List<com.android.billingclient.api.SkuDetails> detailsList)
					{
						int response = billingResult.getResponseCode();
						if (response == BillingClient.BillingResponseCode.OK)
						{
							if (detailsList.size() > 0)
							{
								for (int i = 0; i < detailsList.size(); i++)
								{
									String jsonData = detailsList.get(i).getOriginalJson();
									JSONObject object = null;

									try
									{
										object = new JSONObject(jsonData);
										SkuDetails product = new SkuDetails(object);
										productDetails.add(product);
									}
									catch (JSONException jsonException)
									{
										jsonException.printStackTrace();
									}
								}
							}

							reportSkuDetailsResponseCaller(productDetails, listener);

						}
						else
						{
							reportBillingError(response, null);
							Log.e(LOG_TAG, String.format("Failed to retrieve info for %d products, %d",
									productIdList.size(),
									response));

							reportSkuDetailsErrorCaller(String.format("Failed to retrieve info for %d products, %d",
									productIdList.size(),
									response), listener);
						}
					}
				});

			}
			catch (Exception e)
			{
				Log.e(LOG_TAG, "Failed to call getSkuDetails", e);
				reportBillingError(Constants.BILLING_ERROR_SKUDETAILS_FAILED, e);

				reportSkuDetailsErrorCaller(e.getLocalizedMessage(), listener);

			}
		}
		else
		{
			reportSkuDetailsErrorCaller("Failed to call getSkuDetails. Service may not be connected", listener);
		}
	}

	public void getPurchaseListingDetailsAsync(String productId, final ISkuDetailsResponseListener listener)
	{
		 getSkuDetailsAsync(productId, Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	public void getPurchaseListingDetailsAsync(ArrayList<String> productIdList, final ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productIdList, Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	public void getSubscriptionListingDetailsAsync(String productId, ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	public void getSubscriptionsListingDetailsAsync(ArrayList<String> productIds, ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productIds, Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	@Nullable
	public TransactionDetails getPurchaseTransactionDetails(String productId)
	{
		return getPurchaseTransactionDetails(productId, cachedProducts);
	}

	@Nullable
	public TransactionDetails getSubscriptionTransactionDetails(String productId)
	{
		return getPurchaseTransactionDetails(productId, cachedSubscriptions);
	}

	private String detectPurchaseTypeFromPurchaseResponseData(JSONObject purchase)
	{
		String purchasePayload = getPurchasePayload();
		// regular flow, based on developer payload
		if (!TextUtils.isEmpty(purchasePayload) && purchasePayload.startsWith(Constants.PRODUCT_TYPE_SUBSCRIPTION))
		{
			return Constants.PRODUCT_TYPE_SUBSCRIPTION;
		}
		// backup check for the promo codes (no payload available)
		if (purchase != null && purchase.has(Constants.RESPONSE_AUTO_RENEWING))
		{
			return Constants.PRODUCT_TYPE_SUBSCRIPTION;
		}
		return Constants.PRODUCT_TYPE_MANAGED;
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
		if (resultCode == Activity.RESULT_OK &&
			responseCode == Constants.BILLING_RESPONSE_RESULT_OK)
		{
			String purchaseData = data.getStringExtra(Constants.INAPP_PURCHASE_DATA);
			String dataSignature = data.getStringExtra(Constants.RESPONSE_INAPP_SIGNATURE);
			try
			{
				JSONObject purchase = new JSONObject(purchaseData);
				String productId = purchase.getString(Constants.RESPONSE_PRODUCT_ID);
                if (verifyPurchaseSignature(productId, purchaseData, dataSignature))
				{
					String purchaseType = detectPurchaseTypeFromPurchaseResponseData(purchase);
					BillingCache cache = purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)
							? cachedSubscriptions : cachedProducts;
					cache.put(productId, purchaseData, dataSignature);
					if (eventHandler != null)
					{
						eventHandler.onProductPurchased(
								productId,
								new TransactionDetails(new PurchaseInfo(purchaseData, dataSignature, getPurchasePayload())));
					}
				}
                else
                {
                    Log.e(LOG_TAG, "Public key signature doesn't match!");
                    reportBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
                }
			}
			catch (Exception e)
			{
				Log.e(LOG_TAG, "Error in handleActivityResult", e);
				reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
			}
			savePurchasePayload(null);
		}
		else
		{
			reportBillingError(responseCode, null);
		}
		return true;
	}

	private boolean verifyPurchaseSignature(String productId, String purchaseData, String dataSignature)
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
		return verifyPurchaseSignature(transactionDetails.purchaseInfo.purchaseData.productId,
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

	private void reportPurchasesSuccess(final IPurchasesResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onPurchasesSuccess();
				}
			});
		}
	}

	private void reportPurchasesError(final IPurchasesResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onPurchasesError();
				}
			});
		}
	}

	private void reportSkuDetailsErrorCaller(final String error, final ISkuDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onSkuDetailsError(error);
				}
			});
		}
	}

	private void reportSkuDetailsResponseCaller(@Nullable final List<SkuDetails> products, final ISkuDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onSkuDetailsResponse(products);
				}
			});
		}
	}


	private void handlePurchase(Purchase purchase)
	{
		// Verify the purchase.
		// Ensure entitlement was not already granted for this purchaseToken.
		// Grant entitlement to the user.

		//Acknowledging purchase
		AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener()
		{
			@Override
			public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult)
			{

			}
		};

		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
		{
			if (!purchase.isAcknowledged())
			{
				AcknowledgePurchaseParams acknowledgePurchaseParams =
						AcknowledgePurchaseParams.newBuilder()
								.setPurchaseToken(purchase.getPurchaseToken())
								.build();

				Intent intent = new Intent();
				intent.putExtra(Constants.RESPONSE_CODE, BillingClient.BillingResponseCode.OK);
				intent.putExtra(Constants.INAPP_PURCHASE_DATA, purchase.getOriginalJson());
				intent.putExtra(Constants.RESPONSE_INAPP_SIGNATURE, purchase.getSignature());

				handleActivityResult(PURCHASE_FLOW_REQUEST_CODE, Activity.RESULT_OK, intent);

				billingService.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
			}
		}
	}

	private final PurchasesUpdatedListener purchasesUpdatedListener = new
			PurchasesUpdatedListener()
			{
				@Override
				public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases)
				{
					if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
					{
						if (purchases != null)
						{
							for (final Purchase purchase : purchases)
							{
								handlePurchase(purchase);
							}
						}
					} else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
					{
						loadOwnedPurchasesFromGoogleAsync(null);
					}
				}
			};
}
