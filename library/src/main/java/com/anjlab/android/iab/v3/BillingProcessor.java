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

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BillingProcessor extends BillingBase
{

	/**
	 * Callback methods where billing events are reported.
	 * Apps must implement one of these to construct a BillingProcessor.
	 */
	public interface IBillingHandler
	{
		void onProductPurchased(@NonNull String productId, @Nullable PurchaseInfo details);

		void onPurchaseHistoryRestored();

		void onBillingError(int errorCode, @Nullable Throwable error);

		void onBillingInitialized();
	}

	/**
	 * Callback methods for notifying about success or failure attempt to fetch purchases from the server.
	 */
	public interface IPurchasesResponseListener
	{
		void onPurchasesSuccess();

		void onPurchasesError();
	}

	/**
	 * Callback methods where result of SkuDetails fetch returned or error message on failure.
	 *
	 * @deprecated use {@link IProductDetailsResponseListener} for the full Billing Library 8
	 *     {@link ProductDetails} shape (multi-offer subscriptions, pricing phases, base plans).
	 */
	@Deprecated
	public interface ISkuDetailsResponseListener
	{
		void onSkuDetailsResponse(@Nullable List<SkuDetails> products);

		void onSkuDetailsError(String error);
	}

	/**
	 * Callback methods delivering raw Billing Library 8 {@link ProductDetails} objects — the
	 * full product information including subscription offer trees, pricing phases, and base
	 * plans. Prefer this over the legacy {@link ISkuDetailsResponseListener}.
	 */
	public interface IProductDetailsResponseListener
	{
		void onProductDetailsResponse(@NonNull List<ProductDetails> products);

		void onProductDetailsError(@NonNull String error);
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
	private String signatureBase64;
	private BillingCache cachedProducts;
	private BillingCache cachedSubscriptions;
	private IBillingHandler eventHandler;
	private String developerMerchantId;
	private boolean isSubsUpdateSupported;
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
	 * @param context Context object
	 * @param licenseKey Licence key from Play Console
	 * @param handler callback instance
	 * @return BillingProcessor instance
	 */
	public static BillingProcessor newBillingProcessor(Context context, String licenseKey, IBillingHandler handler)
	{
		return newBillingProcessor(context, licenseKey, null, handler);
	}

	/**
	 * Returns a new {@link BillingProcessor}, without immediately binding to Play Services. If you use
	 * this factory, then you must call {@link #initialize()} afterwards.
	 * @param context Context object
	 * @param licenseKey Licence key from Play Console
	 * @param merchantId Google merchant ID
	 * @param handler callback instance
	 * @return BillingProcessor instance
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
		PurchasesUpdatedListener listener = new PurchasesUpdatedListener()
		{
			@Override
			public void onPurchasesUpdated(@NonNull BillingResult billingResult,
										   @Nullable List<Purchase> purchases)
			{
				int responseCode = billingResult.getResponseCode();

				if (responseCode == BillingClient.BillingResponseCode.OK)
				{
					if (purchases != null)
					{
						for (final Purchase purchase : purchases)
						{
							handlePurchase(purchase);
						}
					}
				}
				else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
				{
					String purchasePayload = getPurchasePayload();
					if (TextUtils.isEmpty(purchasePayload))
					{
						loadOwnedPurchasesFromGoogleAsync(null);
					}
					else
					{
						handleItemAlreadyOwned(purchasePayload.split(":")[1]);
						savePurchasePayload(null);
					}

					reportBillingError(responseCode, new Throwable(billingResult.getDebugMessage()));

				}
				else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED
						|| responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
						|| responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
						|| responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
						|| responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR
						|| responseCode == BillingClient.BillingResponseCode.ERROR
						|| responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED)
				{
					reportBillingError(responseCode, new Throwable(billingResult.getDebugMessage()));
				}
			}
		};

		PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
				.enableOneTimeProducts()
				.build();

		billingService = BillingClient.newBuilder(context)
				.setListener(listener)
				.enablePendingPurchases(pendingPurchasesParams)
				.enableAutoServiceReconnection()
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
						if (!isHistoryTaskExecuted)
						{
							new HistoryInitializationTask().execute();
						}
					}
					else
					{
						retryBillingClientConnection();
						reportBillingError(
								billingResult.getResponseCode(),
								new Throwable(billingResult.getDebugMessage()));
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

		reconnectMilliseconds =
				Math.min(reconnectMilliseconds * 2, RECONNECT_TIMER_MAX_TIME_MILLISECONDS);
	}

	/**
	 *  Check for billingClient is initialized and connected, if true then its ready for use.
	 * @return true or false
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

	private void loadPurchasesByTypeAsync(String type, final BillingCache cacheStorage,
										  final IPurchasesResponseListener listener)
	{
		if (!isConnected())
		{
			reportPurchasesError(listener);
			retryBillingClientConnection();
			return;
		}

		QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
				.setProductType(type)
				.build();

		billingService.queryPurchasesAsync(queryPurchasesParams, new PurchasesResponseListener()
		{
			@Override
			public void onQueryPurchasesResponse(@NonNull BillingResult billingResult,
												 @NonNull List<Purchase> list)
			{
				if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
				{
					cacheStorage.clear();
					for (Purchase purchaseItem : list)
					{
						String jsonData = purchaseItem.getOriginalJson();
						if (!TextUtils.isEmpty(jsonData))
						{
							try
							{
								/*
								  This is a replacement for the bundling in the old version
								  here we query all users' purchases and save it locally
								  However, it is also recommended to save and verify all purchases
								  on own server
								  */
								JSONObject purchase = new JSONObject(jsonData);
								cacheStorage.put(
										purchase.getString(Constants.RESPONSE_PRODUCT_ID),
										jsonData,
										purchaseItem.getSignature());
							}
							catch (Exception e)
							{
								reportBillingError(
										Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
								Log.e(LOG_TAG, "Error in loadPurchasesByType", e);
								reportPurchasesError(listener);
							}
						}
					}

					reportPurchasesSuccess(listener);
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
		final IPurchasesResponseListener successListener = new IPurchasesResponseListener()
		{
			@Override
			public void onPurchasesSuccess()
			{
				reportPurchasesSuccess(listener);
			}

			@Override
			public void onPurchasesError()
			{
				reportPurchasesError(listener);
			}
		};

		final IPurchasesResponseListener errorListener = new IPurchasesResponseListener()
		{
			@Override
			public void onPurchasesSuccess()
			{
				reportPurchasesError(listener);
			}

			@Override
			public void onPurchasesError()
			{
				reportPurchasesError(listener);
			}
		};

		loadPurchasesByTypeAsync(
				Constants.PRODUCT_TYPE_MANAGED,
				cachedProducts,
				new IPurchasesResponseListener()
				{
					@Override
					public void onPurchasesSuccess()
					{
						loadPurchasesByTypeAsync(
								Constants.PRODUCT_TYPE_SUBSCRIPTION,
								cachedSubscriptions,
								successListener);
					}

					@Override
					public void onPurchasesError()
					{
						loadPurchasesByTypeAsync(
								Constants.PRODUCT_TYPE_SUBSCRIPTION,
								cachedSubscriptions,
								errorListener);
					}
				});
	}

	/***
	 * Purchase a product
	 *
	 * @param activity the activity calling this method
	 * @param productId the product id to purchase
	 * @return {@code false} if the billing system is not initialized, {@code productId} is empty
	 * or if an exception occurs. Will return {@code true} otherwise.
	 */
	public boolean purchase(Activity activity, String productId)
	{
		return purchase(activity, null, productId, Constants.PRODUCT_TYPE_MANAGED);
	}

	/***
	 * Subscribe for a product
	 *
	 * @param activity the activity calling this method
	 * @param productId the product id to subscribe
	 * @return {@code false} if the billing system is not initialized, {@code productId} is empty
	 * or if an exception occurs. Will return {@code true} otherwise.
	 */
	public boolean subscribe(Activity activity, String productId)
	{
		return purchase(activity, null, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION);
	}

	/**
	 * Launch the billing flow using a pre-fetched {@link ProductDetails} object. Callers that
	 * already have the product details in hand (e.g. from
	 * {@link #getPurchaseProductDetailsAsync(String, IProductDetailsResponseListener)} or
	 * {@link #getSubscriptionProductDetailsAsync(String, IProductDetailsResponseListener)})
	 * can use this overload to avoid an extra round-trip to Play. For subscriptions the
	 * library picks the base plan offer (null {@code offerId}), falling back to the first
	 * offer in the list.
	 *
	 * @param activity the activity launching the flow
	 * @param productDetails the product to purchase
	 * @return {@code false} if the billing system is not initialized or an exception occurs,
	 *     {@code true} otherwise
	 */
	public boolean purchase(Activity activity, @NonNull ProductDetails productDetails)
	{
		return purchase(activity, productDetails, null);
	}

	/**
	 * Launch the billing flow using a pre-fetched {@link ProductDetails} object, optionally
	 * upgrading or downgrading an existing subscription. See
	 * {@link #purchase(Activity, ProductDetails)} for offer-selection details.
	 *
	 * @param activity the activity launching the flow
	 * @param productDetails the product to purchase
	 * @param oldProductId the existing subscription product id being replaced, or {@code null}
	 *     for a fresh purchase
	 * @return {@code false} if the billing system is not initialized or an exception occurs,
	 *     {@code true} otherwise
	 */
	public boolean purchase(Activity activity, @NonNull ProductDetails productDetails,
							@Nullable String oldProductId)
	{
		if (!isConnected())
		{
			retryBillingClientConnection();
			return false;
		}
		try
		{
			String productId = productDetails.getProductId();
			String productType = productDetails.getProductType();
			String purchasePayload = productType + ":" + productId;
			if (!BillingClient.ProductType.SUBS.equals(productType))
			{
				purchasePayload += ":" + UUID.randomUUID().toString();
			}
			savePurchasePayload(purchasePayload);
			startPurchaseFlow(activity, productDetails, oldProductId);
			return true;
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Error in purchase(ProductDetails)", e);
			reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
		}
		return false;
	}

	/**
	 * @deprecated always returns true.
	 * @return true
	 */
	@Deprecated
	public boolean isOneTimePurchaseSupported()
	{
		return true;
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

		BillingResult result =
				billingService.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE);
		isSubsUpdateSupported = result.getResponseCode() == BillingClient.BillingResponseCode.OK;

		return isSubsUpdateSupported;
	}

	/**
	 * Change subscription i.e. upgrade or downgrade
	 *
	 * @param activity         the activity calling this method
	 * @param oldProductId     passing null or empty string will act the same as {@link #subscribe(Activity, String)}
	 * @param productId        the new subscription id
	 * @return {@code false} if {@code oldProductId} is not {@code null} AND change subscription
	 * is not supported.
	 */
	public boolean updateSubscription(Activity activity, String oldProductId, String productId)
	{
		if (oldProductId != null && !isSubscriptionUpdateSupported())
		{
			return false;
		}
		return purchase(activity, oldProductId, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION);
	}

	private boolean purchase(Activity activity, String productId, String purchaseType)
	{
		return purchase(activity, null, productId, purchaseType);
	}

	private boolean purchase(final Activity activity, final String oldProductId, final String productId,
							 String purchaseType)
	{
		if (!isConnected() || TextUtils.isEmpty(productId) || TextUtils.isEmpty(purchaseType))
		{
			if (!isConnected())
			{
				retryBillingClientConnection();
			}

			return false;
		}

		if (TextUtils.isEmpty(productId))
		{
			reportBillingError(Constants.BILLING_ERROR_PRODUCT_ID_NOT_SPECIFIED, null);
			return false;
		}

		try
		{
			String purchasePayload = purchaseType + ":" + productId;
			if (!purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION))
			{
				purchasePayload += ":" + UUID.randomUUID().toString();
			}
			savePurchasePayload(purchasePayload);

			QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
					.setProductList(Collections.singletonList(
							QueryProductDetailsParams.Product.newBuilder()
									.setProductId(productId)
									.setProductType(purchaseType)
									.build()))
					.build();

			billingService.queryProductDetailsAsync(
					params,
					new ProductDetailsResponseListener()
					{
						@Override
						public void onProductDetailsResponse(
								@NonNull BillingResult billingResult,
								@NonNull QueryProductDetailsResult result)
						{
							List<ProductDetails> details = result.getProductDetailsList();
							if (details != null && !details.isEmpty())
							{
								startPurchaseFlow(activity, details.get(0), oldProductId);
							}
							else
							{
								// Product id did not resolve against the selected product type,
								// or Google returned it in result.getUnfetchedProductList().
								Log.d("onProductDetailsResponse: ", "product id mismatch with Product type");
								reportBillingError(
										Constants.BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE,
										null);
							}
						}
					});

			return true;
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Error in purchase", e);
			reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
		}
		return false;
	}

	private void startPurchaseFlow(final Activity activity,
								   final ProductDetails productDetails,
								   final String oldProductId)
	{
		final String productId = productDetails.getProductId();

		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				BillingFlowParams.ProductDetailsParams.Builder pdpBuilder =
						BillingFlowParams.ProductDetailsParams.newBuilder()
								.setProductDetails(productDetails);

				// Subscriptions require an offer token; one-time products must not set it.
				if (BillingClient.ProductType.SUBS.equals(productDetails.getProductType()))
				{
					List<ProductDetails.SubscriptionOfferDetails> offers =
							productDetails.getSubscriptionOfferDetails();
					if (offers == null || offers.isEmpty())
					{
						reportBillingError(
								Constants.BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE, null);
						return;
					}
					// Prefer the base plan (null offerId); fall back to the first offer.
					ProductDetails.SubscriptionOfferDetails chosen = offers.get(0);
					for (ProductDetails.SubscriptionOfferDetails offer : offers)
					{
						if (offer.getOfferId() == null)
						{
							chosen = offer;
							break;
						}
					}
					pdpBuilder.setOfferToken(chosen.getOfferToken());
				}

				BillingFlowParams.Builder billingFlowParamsBuilder = BillingFlowParams.newBuilder()
						.setProductDetailsParamsList(
								Collections.singletonList(pdpBuilder.build()));

				if (!TextUtils.isEmpty(oldProductId))
				{
					PurchaseInfo oldProductDetails = getSubscriptionPurchaseInfo(oldProductId);

					if (oldProductDetails != null)
					{
						String oldToken = oldProductDetails.purchaseData.purchaseToken;
						// No replacement mode set -> default WITH_TIME_PRORATION, preserving
						// pre-8.x behavior.
						billingFlowParamsBuilder.setSubscriptionUpdateParams(
								BillingFlowParams.SubscriptionUpdateParams
										.newBuilder()
										.setOldPurchaseToken(oldToken)
										.build());
					}
				}

				BillingFlowParams params = billingFlowParamsBuilder.build();

				int responseCode = billingService.launchBillingFlow(activity, params).getResponseCode();

				if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
				{
					handleItemAlreadyOwned(productId);
				}
			}
		});
	}

	private void handleItemAlreadyOwned(final String productId)
	{
		if (!isPurchased(productId) && !isSubscribed(productId))
		{
			loadOwnedPurchasesFromGoogleAsync(new IPurchasesResponseListener()
			{
				@Override
				public void onPurchasesSuccess()
				{
					handleOwnedPurchaseTransaction(productId);
				}

				@Override
				public void onPurchasesError()
				{
					handleOwnedPurchaseTransaction(productId);
				}
			});
		}
		else
		{
			handleOwnedPurchaseTransaction(productId);
		}
	}

	private void handleOwnedPurchaseTransaction(String productId)
	{
		PurchaseInfo details = getPurchaseInfo(productId);
		if (!checkMerchant(details))
		{
			Log.i(LOG_TAG, "Invalid or tampered merchant id!");
			reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
		}

		if (eventHandler != null)
		{
			if (details == null)
			{
				details = getSubscriptionPurchaseInfo(productId);
			}

			reportProductPurchased(productId, details);
		}
	}

	/**
	 * Checks merchant's id validity. If purchase was generated by Freedom alike program it doesn't know
	 * real merchant id, unless publisher GoogleId was hacked
	 * If merchantId was not supplied function checks nothing
	 *
	 * @param details PurchaseInfo
	 * @return boolean
	 */
	private boolean checkMerchant(PurchaseInfo details)
	{
		if (developerMerchantId == null) //omit merchant id checking
		{
			return true;
		}
		if (details.purchaseData.purchaseTime.before(DATE_MERCHANT_LIMIT_1)) //newest format applied
		{
			return true;
		}
		if (details.purchaseData.purchaseTime.after(DATE_MERCHANT_LIMIT_2)) //newest format applied
		{
			return true;
		}
		if (details.purchaseData.orderId == null ||
			details.purchaseData.orderId.trim().length() == 0)
		{
			return false;
		}
		int index = details.purchaseData.orderId.indexOf('.');
		if (index <= 0)
		{
			return false; //protect on missing merchant id
		}
		//extract merchant id
		String merchantId = details.purchaseData.orderId.substring(0, index);
		return merchantId.compareTo(developerMerchantId) == 0;
	}

	@Nullable
	private PurchaseInfo getPurchaseInfo(String productId, BillingCache cache)
	{
		PurchaseInfo details = cache.getDetails(productId);
		if (details != null && !TextUtils.isEmpty(details.responseData))
		{
			return details;
		}
		return null;
	}

	public void consumePurchaseAsync(final String productId, final IPurchasesResponseListener listener)
	{
		if (!isConnected())
		{
			reportPurchasesError(listener);
		}

		try
		{
			PurchaseInfo purchaseInfo = getPurchaseInfo(productId, cachedProducts);
			if (purchaseInfo != null && !TextUtils.isEmpty(purchaseInfo.purchaseData.purchaseToken))
			{
				ConsumeParams consumeParams =
						ConsumeParams.newBuilder()
									 .setPurchaseToken(purchaseInfo.purchaseData.purchaseToken)
									 .build();

				billingService.consumeAsync(consumeParams, new ConsumeResponseListener()
				{
					@Override
					public void onConsumeResponse(@NonNull BillingResult billingResult,
												  @NonNull String purchaseToken)
					{
						if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
						{
							cachedProducts.remove(productId);
							Log.d(LOG_TAG, "Successfully consumed " + productId + " purchase.");

							reportPurchasesSuccess(listener);
						}
						else
						{
							Log.d(LOG_TAG, "Failure consume " + productId + " purchase.");
							reportBillingError(Constants.BILLING_ERROR_CONSUME_FAILED,
											   new Exception(billingResult.getDebugMessage()));
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

	private void getSkuDetailsAsync(final String productId, String purchaseType,
									final ISkuDetailsResponseListener listener)
	{
		ArrayList<String> productIdList = new ArrayList<>();
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

	private void getProductDetailsAsync(final List<String> productIdList, final String purchaseType,
										final IProductDetailsResponseListener listener)
	{
		if (billingService == null || !billingService.isReady())
		{
			reportProductDetailsErrorCaller(
					"Failed to call queryProductDetails. Service may not be connected", listener);
			return;
		}
		if (productIdList == null || productIdList.isEmpty())
		{
			reportProductDetailsErrorCaller("Empty products list", listener);
			return;
		}

		try
		{
			List<QueryProductDetailsParams.Product> productList = new ArrayList<>(productIdList.size());
			for (String id : productIdList)
			{
				productList.add(QueryProductDetailsParams.Product.newBuilder()
						.setProductId(id)
						.setProductType(purchaseType)
						.build());
			}
			QueryProductDetailsParams queryParams = QueryProductDetailsParams.newBuilder()
					.setProductList(productList)
					.build();

			billingService.queryProductDetailsAsync(
					queryParams,
					new ProductDetailsResponseListener()
					{
						@Override
						public void onProductDetailsResponse(
								@NonNull BillingResult billingResult,
								@NonNull QueryProductDetailsResult result)
						{
							int response = billingResult.getResponseCode();
							if (response == BillingClient.BillingResponseCode.OK)
							{
								List<ProductDetails> detailsList = result.getProductDetailsList();
								reportProductDetailsResponseCaller(
										detailsList != null ? detailsList : Collections.emptyList(),
										listener);
							}
							else
							{
								reportBillingError(response, null);
								String errorMessage = String.format(Locale.US,
																	"Failed to retrieve info for %d products, %d",
																	productIdList.size(), response);
								Log.e(LOG_TAG, errorMessage);

								reportProductDetailsErrorCaller(errorMessage, listener);
							}
						}
					});
		}
		catch (Exception e)
		{
			Log.e(LOG_TAG, "Failed to call queryProductDetails", e);
			reportBillingError(Constants.BILLING_ERROR_SKUDETAILS_FAILED, e);

			String msg = e.getLocalizedMessage();
			reportProductDetailsErrorCaller(msg != null ? msg : "Unknown error", listener);
		}
	}

	@SuppressWarnings("deprecation")
	private void getSkuDetailsAsync(final ArrayList<String> productIdList, String purchaseType,
									final ISkuDetailsResponseListener listener)
	{
		// Legacy path: query ProductDetails via the shared helper and translate to the
		// deprecated SkuDetails shape on the way out.
		getProductDetailsAsync(productIdList, purchaseType, new IProductDetailsResponseListener()
		{
			@Override
			public void onProductDetailsResponse(@NonNull List<ProductDetails> products)
			{
				ArrayList<SkuDetails> translated = new ArrayList<>();
				for (ProductDetails pd : products)
				{
					try
					{
						translated.add(SkuDetails.fromProductDetails(pd));
					}
					catch (JSONException jsonException)
					{
						// A partial list would silently render missing or wrong pricing.
						// Fail the whole callback instead so callers can react.
						String error = "Failed to translate ProductDetails to legacy SkuDetails for product: "
								+ pd.getProductId();
						Log.e(LOG_TAG, error, jsonException);
						reportSkuDetailsErrorCaller(error, listener);
						return;
					}
				}
				reportSkuDetailsResponseCaller(translated, listener);
			}

			@Override
			public void onProductDetailsError(@NonNull String error)
			{
				reportSkuDetailsErrorCaller(error, listener);
			}
		});
	}

	/**
	 * @deprecated use {@link #getPurchaseProductDetailsAsync(String, IProductDetailsResponseListener)}
	 *     which returns the full Billing Library 8 {@link ProductDetails}.
	 */
	@Deprecated
	public void getPurchaseListingDetailsAsync(String productId, final ISkuDetailsResponseListener listener)
	{
		 getSkuDetailsAsync(productId, Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	/**
	 * @deprecated use {@link #getPurchaseProductDetailsAsync(List, IProductDetailsResponseListener)}
	 *     which returns the full Billing Library 8 {@link ProductDetails}.
	 */
	@Deprecated
	public void getPurchaseListingDetailsAsync(ArrayList<String> productIdList,
											   final ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productIdList, Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	/**
	 * @deprecated use {@link #getSubscriptionProductDetailsAsync(String, IProductDetailsResponseListener)}
	 *     which returns the full Billing Library 8 {@link ProductDetails}, including
	 *     multi-offer subscription trees and pricing phases.
	 */
	@Deprecated
	public void getSubscriptionListingDetailsAsync(String productId, ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	/**
	 * @deprecated use {@link #getSubscriptionProductDetailsAsync(List, IProductDetailsResponseListener)}
	 *     which returns the full Billing Library 8 {@link ProductDetails}, including
	 *     multi-offer subscription trees and pricing phases.
	 */
	@Deprecated
	public void getSubscriptionsListingDetailsAsync(ArrayList<String> productIds, ISkuDetailsResponseListener listener)
	{
		getSkuDetailsAsync(productIds, Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	/**
	 * Fetch one managed (one-time) product's {@link ProductDetails}. The listener receives
	 * Billing Library 8's native type with access to the full one-time offer details.
	 */
	public void getPurchaseProductDetailsAsync(String productId,
											   IProductDetailsResponseListener listener)
	{
		getProductDetailsAsync(Collections.singletonList(productId),
				Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	/**
	 * Fetch multiple managed (one-time) products' {@link ProductDetails} in a single call.
	 */
	public void getPurchaseProductDetailsAsync(List<String> productIds,
											   IProductDetailsResponseListener listener)
	{
		getProductDetailsAsync(productIds, Constants.PRODUCT_TYPE_MANAGED, listener);
	}

	/**
	 * Fetch one subscription product's {@link ProductDetails}. The listener receives Billing
	 * Library 8's native type with the full subscription offer tree — base plan, promotional
	 * offers, and all pricing phases. Use
	 * {@link ProductDetails#getSubscriptionOfferDetails()} to enumerate the offers.
	 */
	public void getSubscriptionProductDetailsAsync(String productId,
												   IProductDetailsResponseListener listener)
	{
		getProductDetailsAsync(Collections.singletonList(productId),
				Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	/**
	 * Fetch multiple subscription products' {@link ProductDetails} in a single call.
	 */
	public void getSubscriptionProductDetailsAsync(List<String> productIds,
												   IProductDetailsResponseListener listener)
	{
		getProductDetailsAsync(productIds, Constants.PRODUCT_TYPE_SUBSCRIPTION, listener);
	}

	@Nullable
	public PurchaseInfo getPurchaseInfo(String productId)
	{
		return getPurchaseInfo(productId, cachedProducts);
	}

	@Nullable
	public PurchaseInfo getSubscriptionPurchaseInfo(String productId)
	{
		return getPurchaseInfo(productId, cachedSubscriptions);
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

	private void verifyAndCachePurchase(Purchase purchase)
	{
		String purchaseData = purchase.getOriginalJson();
		String dataSignature = purchase.getSignature();
		try
		{
			JSONObject purchaseJsonObject = new JSONObject(purchaseData);
			String productId = purchaseJsonObject.getString(Constants.RESPONSE_PRODUCT_ID);
			if (verifyPurchaseSignature(productId, purchaseData, dataSignature))
			{
				String purchaseType =
						detectPurchaseTypeFromPurchaseResponseData(purchaseJsonObject);
				BillingCache cache = purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)
						? cachedSubscriptions : cachedProducts;
				cache.put(productId, purchaseData, dataSignature);
				if (eventHandler != null)
				{
					PurchaseInfo purchaseInfo = new PurchaseInfo(purchaseData,
																 dataSignature,
																 getPurchasePayload());
					reportProductPurchased(productId, purchaseInfo);
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
			Log.e(LOG_TAG, "Error in verifyAndCachePurchase", e);
			reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
		}
		savePurchasePayload(null);
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

	public boolean isValidPurchaseInfo(PurchaseInfo purchaseInfo)
	{
		return verifyPurchaseSignature(purchaseInfo.purchaseData.productId,
									   purchaseInfo.responseData,
									   purchaseInfo.signature) &&
			   checkMerchant(purchaseInfo);
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
		if (eventHandler != null && handler != null)
		{
			handler.post(() -> eventHandler.onBillingError(errorCode, error));
		}
	}

	private void reportPurchasesSuccess(final IPurchasesResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onPurchasesSuccess());
		}
	}

	private void reportPurchasesError(final IPurchasesResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onPurchasesError());
		}
	}

	private void reportSkuDetailsErrorCaller(final String error, final ISkuDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onSkuDetailsError(error));
		}
	}

	private void reportSkuDetailsResponseCaller(@Nullable final List<SkuDetails> products,
												final ISkuDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onSkuDetailsResponse(products));
		}
	}

	private void reportProductDetailsErrorCaller(@NonNull final String error,
												 final IProductDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onProductDetailsError(error));
		}
	}

	private void reportProductDetailsResponseCaller(@NonNull final List<ProductDetails> products,
													final IProductDetailsResponseListener listener)
	{
		if (listener != null && handler != null)
		{
			handler.post(() -> listener.onProductDetailsResponse(products));
		}
	}

	private void reportProductPurchased(@NonNull String productId, @Nullable PurchaseInfo details)
	{
		if (eventHandler != null && handler != null)
		{
			handler.post(() -> eventHandler.onProductPurchased(productId, details));
		}
	}

	private void handlePurchase(final Purchase purchase)
	{
		// Verify the purchase.
		// Ensure entitlement was not already granted for this purchaseToken.
		// Grant entitlement to the user.

		//Acknowledging purchase
		if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
		{
			if (purchase.isAcknowledged())
			{
				verifyAndCachePurchase(purchase);
			}
			else
			{
				AcknowledgePurchaseParams acknowledgePurchaseParams =
						AcknowledgePurchaseParams.newBuilder()
												 .setPurchaseToken(purchase.getPurchaseToken())
												 .build();

				billingService.acknowledgePurchase(
						acknowledgePurchaseParams,
						new AcknowledgePurchaseResponseListener()
						{
							@Override
							public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult)
							{
								if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
								{
									verifyAndCachePurchase(purchase);
								}
								else
								{
									reportBillingError(Constants.BILLING_ERROR_FAILED_TO_ACKNOWLEDGE_PURCHASE, null);
								}
							}
						});
			}
		}
	}
}
