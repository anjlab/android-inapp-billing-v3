/**
 * Copyright 2021 AnjLab
 * Updated by Afolabi Somoye
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anjlab.android.iab.v3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
//import com.android.vending.billing.IInAppBillingService;
//import com.android.vending.billing.IInAppBillingService;

public class BillingProcessor extends BillingBase {


    public BillingProcessor() {
    }

    /**
     * Callback methods where billing events are reported.
     * Apps must implement one of these to construct a BillingProcessor.
     */
    public interface IBillingHandler {
        void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details);

        void onPurchaseHistoryRestored();

        void onBillingError(int errorCode, @Nullable Throwable error);

        void onBillingInitialized();
    }

    private static final Date DATE_MERCHANT_LIMIT_1; //5th December 2012
    private static final Date DATE_MERCHANT_LIMIT_2; //21st July 2015
    BillingResult billingResult;

    Handler handler = new Handler(Looper.getMainLooper());


    static {
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

    //private IInAppBillingService billingService;
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
    //BillingClient billingClient;
    Context context;

    private class HistoryInitializationTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... nothing) {
            if (!isPurchaseHistoryRestored()) {
                loadOwnedPurchasesFromGoogle();
                return true;
            }
            return false;

        }

        @Override
        protected void onPostExecute(Boolean restored) {
            if (restored) {
                setPurchaseHistoryRestored();
                if (eventHandler != null) {
                    eventHandler.onPurchaseHistoryRestored();
                }
            }
            if (eventHandler != null) {
                eventHandler.onBillingInitialized();
            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            billingService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
           // billingService = IInAppBillingService.Stub.asInterface(service);
            new HistoryInitializationTask().execute();
        }
    };

    /**
     * Returns a new {@link BillingProcessor}, without immediately binding to Play Services. If you use
     * this factory, then you must call {@link #initialize()} afterwards.
     */
    public static BillingProcessor newBillingProcessor(Context context, String licenseKey, IBillingHandler handler) {
        return newBillingProcessor(context, licenseKey, null, handler);
    }

    /**
     * Returns a new {@link BillingProcessor}, without immediately binding to Play Services. If you use
     * this factory, then you must call {@link #initialize()} afterwards.
     */
    public static BillingProcessor newBillingProcessor(Context context, String licenseKey, String merchantId,
                                                       IBillingHandler handler) {
        return new BillingProcessor(context, licenseKey, merchantId, handler, false);
    }

    public BillingProcessor(Context context, String licenseKey, IBillingHandler handler) {
        this(context, licenseKey, null, handler);
    }

    public BillingProcessor(Context context, String licenseKey, String merchantId,
                            IBillingHandler handler) {
        this(context, licenseKey, merchantId, handler, true);
    }

    private BillingProcessor(Context context, String licenseKey, String merchantId, IBillingHandler handler,
                             boolean bindImmediately) {
        super(context.getApplicationContext());
        signatureBase64 = licenseKey;
        eventHandler = handler;
        contextPackageName = getContext().getPackageName();
        //contextPackageName = context.getPackageName();
        cachedProducts = new BillingCache(getContext(), MANAGED_PRODUCTS_CACHE_KEY);
        //cachedProducts = new BillingCache(context, MANAGED_PRODUCTS_CACHE_KEY);
        cachedSubscriptions = new BillingCache(getContext(), SUBSCRIPTIONS_CACHE_KEY);
        //cachedSubscriptions = new BillingCache(context, SUBSCRIPTIONS_CACHE_KEY);
        developerMerchantId = merchantId;
        if (bindImmediately) {
            bindPlayServices();
        }
    }

    //Start Connection for BillingClient
    public Handler getHandler() {
        return handler;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    private static BillingProcessor instance = null;

    public static BillingProcessor getInstance() {
        if (instance == null){
            instance = new BillingProcessor();
        }
        return instance;
    }

    // I have used billing service here in order to be consistent with the old parameter name
    public BillingClient getBillingService() {
        return billingService;
    }

    public void setBillingService(BillingClient billingService) {
        this.billingService = billingService;
    }

    //Initializing and Establishing Connection to Google Play
    public void connect(final Context context) {
        billingService = BillingClient.newBuilder(context)
                .enablePendingPurchases()
                .setListener(purchasesUpdatedListener)
                .build();

        setBillingService(billingService);

        billingService.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.d("GooglePlayConnection; ","IsConnected");

                    //Initialize history of purchases if any exist.
                    new HistoryInitializationTask().execute();
                }

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
                    showToast(context,"unavailable service");
                    Log.d("ConnectionService; ","IsNotAvailable");

                }

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    //showToast(context,"Canceling connection");
                    Log.d("UserAction; ","Canceling connection");

                }
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    //showToast(context,"Canceling connection");
                    Log.d("UserOwnership; ","ItemAlreadyOwned");

                }

            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.d("ServiceDisconnected; ","BillingServiceDisconnected, trying new Connection");

                //retrying connection to GooglePlay
                if (!isConnected()){
                    connect(context);
                }
            }
        });
    }

    public void showToast(Context context,String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    boolean isConnected() {
        return getBillingService().isReady();
    }


    //End of Starting Connection


    /**
     * Binds to Play Services. When complete, caller will be notified via
     * {@link IBillingHandler#onBillingInitialized()}.
     */
    public void initialize() {
        bindPlayServices();
    }

    private static Intent getBindServiceIntent() {
        Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        intent.setPackage("com.android.vending");
        return intent;
    }

    private void bindPlayServices() {
        try {
            getContext().bindService(getBindServiceIntent(), serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(LOG_TAG, "error in bindPlayServices", e);
            reportBillingError(Constants.BILLING_ERROR_BIND_PLAY_STORE_FAILED, e);
        }
    }

    public static boolean isIabServiceAvailable(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentServices(getBindServiceIntent(), 0);
        return list != null && list.size() > 0;
    }

    public void release() {
        if (isInitialized() && serviceConnection != null) {
            try {
                getContext().unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in release", e);
            }
            billingService = null;
        }
    }

    public boolean isInitialized() {
        return getBillingService() != null;
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
        if (!isInitialized()) {
            return false;
        }
        try {
            /**
             *
             * This is a replacement for the bundling in the old version
             * here we query all users' purchases and save it locally
             * However, it is also recommended to save and verify all purchases on own server
             * */
            PurchasesResult purchasesResult = billingService.queryPurchases(type);
            List<Purchase> bundle = purchasesResult.getPurchasesList();

			/*Bundle bundle = billingService.getPurchases(Constants.GOOGLE_API_VERSION,
														contextPackageName, type, null);*/
            //if (bundle.getInt(Constants.RESPONSE_CODE) == Constants.BILLING_RESPONSE_RESULT_OK)
            if (purchasesResult.getResponseCode() == Constants.BILLING_RESPONSE_RESULT_OK) {
                cacheStorage.clear();

                ArrayList<Purchase> purchaseList = new ArrayList<>();
                purchaseList.addAll(bundle);
                //String purchaseList = bundle.getOriginalJson();
                //bundle.getStringArrayList(Constants.INAPP_PURCHASE_DATA_LIST);
                //ArrayList<String> signatureList = purchaseList.get(0).
                //String signatureList = bundle.getSignature();
                //bundle.getStringArrayList(Constants.INAPP_DATA_SIGNATURE_LIST);
                if (purchaseList != null) {
                    for (int i = 0; i < purchaseList.size(); i++) {
                        String jsonData = purchaseList.get(i).toString();  //getting String ArrayList for purchases
                        String signatureList = purchaseList.get(i).getSignature(); //getting String ArrayList for signatures

                        if (!TextUtils.isEmpty(jsonData)) {
                            JSONObject purchase = new JSONObject(jsonData);
                            String signature = signatureList != null && signatureList.length() >
                                    i ? purchaseList.get(i).getSignature() : null;
                            //	i ? purchaseList.get(i).getSignature()signatureList.get(i) : null;

                            cacheStorage.put(purchase.getString(Constants.RESPONSE_PRODUCT_ID),
                                    jsonData,
                                    signature);
                        }
                    }
                }
                return true;
            }
        } catch (Exception e) {
            reportBillingError(Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
            Log.e(LOG_TAG, "Error in loadPurchasesByType", e);
        }
        return false;
    }

    /**
     * Attempt to fetch purchases from the server and update our cache if successful
     *
     * @return {@code true} if all retrievals are successful, {@code false} otherwise
     */
    public boolean loadOwnedPurchasesFromGoogle() {
        return loadPurchasesByType(Constants.PRODUCT_TYPE_MANAGED, cachedProducts) &&
                loadPurchasesByType(Constants.PRODUCT_TYPE_SUBSCRIPTION, cachedSubscriptions);
    }

    public boolean purchase(Activity activity, String productId) {
        return purchase(activity, null, productId, Constants.PRODUCT_TYPE_MANAGED, null);
    }

    public boolean subscribe(Activity activity, String productId) {
        return purchase(activity, null, productId, Constants.PRODUCT_TYPE_SUBSCRIPTION, null);
    }

    public boolean purchase(Activity activity, String productId, String developerPayload) {
        return purchase(activity, productId, Constants.PRODUCT_TYPE_MANAGED, developerPayload);
    }

    public boolean subscribe(Activity activity, String productId, String developerPayload) {
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
    public boolean purchase(Activity activity, String productId, String developerPayload, Bundle extraParams) {
        if (!isOneTimePurchaseWithExtraParamsSupported(extraParams))  // might remove this
        {
            return purchase(activity, productId, developerPayload);
        } else {
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
    public boolean subscribe(Activity activity, String productId, String developerPayload, Bundle extraParams) {
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
	/*public boolean isOneTimePurchaseSupported()
	{

		//This will represent the inapp purchase checks
		if(!isInitialized() && !isConnected())
		{
			Log.e(LOG_TAG, "Make sure BillingProcessor was initialized before calling isOneTimePurchaseSupported()");
			return false;
		}
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
	}*/

    public boolean isSubscriptionUpdateSupported() {
        // Avoid calling the service again if this value is true
        if (isSubsUpdateSupported) {
            return true;
        }
        BillingResult featureSupported = billingService.isFeatureSupported("subscriptionsUpdate");
        int response = featureSupported.getResponseCode();
        if (response == BillingClient.BillingResponseCode.OK) {
            isSubsUpdateSupported = response == Constants.BILLING_RESPONSE_RESULT_OK;
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
    public boolean isSubscriptionWithExtraParamsSupported(Bundle extraParams) {
        if (isSubscriptionExtraParamsSupported) {
            return true;
        }


        BillingResult billingResult = billingService.isFeatureSupported("subscriptionsOnVr");

        int response = billingResult.getResponseCode();
					/*billingService.isBillingSupportedExtraParams(Constants.GOOGLE_API_VR_SUPPORTED_VERSION,
																 contextPackageName,
																 Constants.PRODUCT_TYPE_SUBSCRIPTION, extraParams);*/
        if (response == BillingClient.BillingResponseCode.OK) {
            isSubscriptionExtraParamsSupported = response == Constants.BILLING_RESPONSE_RESULT_OK;
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
    public boolean isOneTimePurchaseWithExtraParamsSupported(Bundle extraParams) {
        if (isOneTimePurchaseExtraParamsSupported) {
            return true;
        }

        //For VR supported versions
        billingResult = billingService.isFeatureSupported("inAppItemsOnVr");


        int response = billingResult.getResponseCode();
					/*billingService.isBillingSupportedExtraParams(Constants.GOOGLE_API_VR_SUPPORTED_VERSION,
																 contextPackageName,
																 Constants.PRODUCT_TYPE_MANAGED, extraParams);*/
        if (response == BillingClient.BillingResponseCode.OK) {

            isOneTimePurchaseExtraParamsSupported = response == Constants.BILLING_RESPONSE_RESULT_OK;
        }
        return isOneTimePurchaseExtraParamsSupported;
    }

    /**
     * Checks if API supports version 6 which required to request purchase history
     * @param type product type, accepts either {@value Constants#PRODUCT_TYPE_MANAGED}
     *                or {@value Constants#PRODUCT_TYPE_SUBSCRIPTION}
     * @return {@code true} if feature supported {@code false} otherwise
     */
	/*public boolean isRequestBillingHistorySupported(String type) throws BillingCommunicationException
	{
		if (!type.equals(Constants.PRODUCT_TYPE_MANAGED) && !type.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION))
		{
			throw new RuntimeException("Unsupported type " + type);
		}

		IInAppBillingService billing = billingService;

		if (billing != null)
		{

			try
			{
				int response = billing.isBillingSupported(Constants.GOOGLE_API_REQUEST_PURCHASE_HISTORY_VERSION,
						contextPackageName, type);
				return response == Constants.BILLING_RESPONSE_RESULT_OK;
			}
			catch (RemoteException e)
			{
				throw new BillingCommunicationException(e);
			}

		}
		else
		{
			throw new BillingCommunicationException("Billing service isn't connected");
		}
	}*/

    /**
     * Change subscription i.e. upgrade or downgrade
     *
     * @param activity     the activity calling this method
     * @param oldProductId passing null or empty string will act the same as {@link #subscribe(Activity, String)}
     * @param productId    the new subscription id
     * @return {@code false} if {@code oldProductId} is not {@code null} AND change subscription
     * is not supported.
     */
    public boolean updateSubscription(Activity activity, String oldProductId, String productId) {
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
    public boolean updateSubscription(Activity activity, String oldProductId, String productId, String developerPayload) {
        List<String> oldProductIds = null;
        if (!TextUtils.isEmpty(oldProductId)) {
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
                                      String productId) {
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
                                      String productId, String developerPayload) {
        if (oldProductIds != null && !isSubscriptionUpdateSupported()) {
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
                                      String productId, String developerPayload, Bundle extraParams) {
        if (oldProductIds != null && !isSubscriptionUpdateSupported()) {
            return false;
        }

        // if API v7 is not supported, let's fallback to the previous method
        if (!isSubscriptionWithExtraParamsSupported(extraParams)) {
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
                             String developerPayload) {
        return purchase(activity, null, productId, purchaseType, developerPayload);
    }

    private boolean purchase(Activity activity, List<String> oldProductIds, String productId,
                             String purchaseType, String developerPayload) {
        return purchase(activity, oldProductIds, productId, purchaseType, developerPayload, null);
    }

    private boolean purchase(final Activity activity, List<String> oldProductIds, final String productId,
                             String purchaseType, String developerPayload, Bundle extraParamsBundle) {
        if (!isInitialized() || TextUtils.isEmpty(productId) || TextUtils.isEmpty(purchaseType)) {
            return false;
        }
        try {
            String purchasePayload = purchaseType + ":" + productId;
            if (!purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)) {
                purchasePayload += ":" + UUID.randomUUID().toString();
            }
            if (developerPayload != null) {
                purchasePayload += ":" + developerPayload;
            }
            savePurchasePayload(purchasePayload);
            Bundle bundle;
            /*if (oldProductIds != null && purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)) {
                if (extraParamsBundle == null) // API v5
                {
                    //PurchasesResult result =
                    bundle = billingService.getBuyIntentToReplaceSkus(Constants.GOOGLE_API_SUBSCRIPTION_CHANGE_VERSION,
                            contextPackageName,
                            oldProductIds,
                            productId,
                            purchaseType,
                            purchasePayload);
                } else // API v7+ supported
                {
                    if (!extraParamsBundle.containsKey(Constants.EXTRA_PARAMS_KEY_SKU_TO_REPLACE)) {
                        extraParamsBundle.putStringArrayList(Constants.EXTRA_PARAMS_KEY_SKU_TO_REPLACE,
                                new ArrayList<>(oldProductIds));
                    }

                    bundle = billingService.getBuyIntentExtraParams(Constants.GOOGLE_API_VR_SUPPORTED_VERSION,
                            contextPackageName,
                            productId,
                            purchaseType,
                            purchasePayload,
                            extraParamsBundle);
                }
            } else {
                if (extraParamsBundle == null) // API v3
                {
                    bundle = billingService.getBuyIntent(Constants.GOOGLE_API_VERSION,
                            contextPackageName,
                            productId,
                            purchaseType,
                            purchasePayload);
                } else // API v7+
                {
                    bundle = billingService.getBuyIntentExtraParams(Constants.GOOGLE_API_VR_SUPPORTED_VERSION,
                            contextPackageName,
                            productId,
                            purchaseType,
                            purchasePayload,
                            extraParamsBundle);
                }
            }*/

            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            List<String> skuList = new ArrayList<>();
            skuList.add(productId);
            if (skuList.size()>0 && !skuList.isEmpty()) {
                params.setSkusList(skuList).setType(purchaseType);
                //params.build();
                billingService.querySkuDetailsAsync(params.build(),
                        new SkuDetailsResponseListener() {
                            @Override
                            public void onSkuDetailsResponse(BillingResult billingResult,
                                                             List<com.android.billingclient.api.SkuDetails> skuDetailsList) {
                                // Process the result.
                                //Toast.makeText(activity, "Processing Results...", Toast.LENGTH_SHORT).show();

                                if (skuDetailsList.size()>0 && !skuDetailsList.isEmpty()) {
                                    com.android.billingclient.api.SkuDetails skuDetails = null;
                                    for (int i = 0; i < skuDetailsList.size(); i++) {
                                        skuDetails = skuDetailsList.get(i);
                                        skuDetails.getPrice();
                                        skuDetails.getTitle();
                                        skuDetails.getDescription();
                                        skuDetails.getSku();
                                        skuDetails.getIntroductoryPrice();
                                        skuDetails.getFreeTrialPeriod();

                                    }


                                    Handler handler = new Handler(Looper.getMainLooper());


                                    final com.android.billingclient.api.SkuDetails finalSkuDetails = skuDetails;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {

                                            // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
                                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                                    .setSkuDetails(finalSkuDetails)
                                                    .build();
                                            int responseCode = billingService.launchBillingFlow(activity, billingFlowParams).getResponseCode();
                                            String responseMessage = "Billing response; ";

                                            if (responseCode == BillingClient.BillingResponseCode.OK) {
                                               // showToast(activity,responseMessage + "Ok");
                                                // Toast.makeText(activity, responseMessage + "Ok", Toast.LENGTH_SHORT).show();
                                                 Log.d("ReadyToPurchase","Launch Billing Flow Successful");

                                            }

                                            if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                                                //         showToast(responseMessage + "owned");
                                                //isSubscribe = true;
                                                if (!isPurchased(productId) && !isSubscribed(productId)) {
                                                    loadOwnedPurchasesFromGoogle();
                                                }
                                                TransactionDetails details = getPurchaseTransactionDetails(productId);
                                                if (!checkMerchant(details)) {
                                                    Log.i(LOG_TAG, "Invalid or tampered merchant id!");
                                                    reportBillingError(Constants.BILLING_ERROR_INVALID_MERCHANT_ID, null);
                                                    //return false;
                                                }
                                                if (eventHandler != null) {
                                                    if (details == null) {
                                                        details = getSubscriptionTransactionDetails(productId);
                                                    }
                                                    eventHandler.onProductPurchased(productId, details);
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
                                else {
                                    // This will occur if product id does not match with the product type
                                    Log.d("onSkuResponse: ", "product id mismatch with Product type");
                                    // showToast("product id mismatch with Product type");
                                }

                            }
                        });
            } else {
                Log.d("onProductId: ", "product id not specified");
            }

            return true;
        } catch (Exception e) {
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
    private boolean checkMerchant(TransactionDetails details) {
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
                details.purchaseInfo.purchaseData.orderId.trim().length() == 0) {
            return false;
        }
        int index = details.purchaseInfo.purchaseData.orderId.indexOf('.');
        if (index <= 0) {
            return false; //protect on missing merchant id
        }
        //extract merchant id
        String merchantId = details.purchaseInfo.purchaseData.orderId.substring(0, index);
        return merchantId.compareTo(developerMerchantId) == 0;
    }

    @Nullable
    private TransactionDetails getPurchaseTransactionDetails(String productId, BillingCache cache) {
        PurchaseInfo details = cache.getDetails(productId);
        if (details != null && !TextUtils.isEmpty(details.responseData)) {
            return new TransactionDetails(details);
        }
        return null;
    }

    public boolean consumePurchase(String productId) {
        if (!isInitialized()) {
            return false;
        }
        try {
            TransactionDetails transaction = getPurchaseTransactionDetails(productId, cachedProducts);
            if (transaction != null && !TextUtils.isEmpty(transaction.purchaseInfo.purchaseData.purchaseToken)) {
              /*  int response = billingService.consumePurchase(Constants.GOOGLE_API_VERSION,
                        contextPackageName,
                        transaction.purchaseInfo.purchaseData.purchaseToken);*/
                PurchasesResult purchasesResult = billingService.queryPurchases(productId); //added
                int response = purchasesResult.getResponseCode(); // added

                if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
                    cachedProducts.remove(productId);
                    Log.d(LOG_TAG, "Successfully consumed " + productId + " purchase.");
                    return true;
                } else {
                    reportBillingError(response, null);
                    Log.e(LOG_TAG, String.format("Failed to consume %s: %d", productId, response));
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error in consumePurchase", e);
            reportBillingError(Constants.BILLING_ERROR_CONSUME_FAILED, e);
        }
        return false;
    }

    private SkuDetails getSkuDetails(String productId, String purchaseType) {
        ArrayList<String> productIdList = new ArrayList<String>();
        productIdList.add(productId);
        List<SkuDetails> skuDetailsList = getSkuDetails(productIdList, purchaseType);
        if (skuDetailsList != null && skuDetailsList.size() > 0) {
            return skuDetailsList.get(0);
        }
        return null;
    }

    private List<SkuDetails> getSkuDetails(final ArrayList<String> productIdList, String purchaseType) {
        if (billingService != null && productIdList != null && productIdList.size() > 0) {
            try {
                Bundle products = new Bundle();
                products.putStringArrayList(Constants.PRODUCTS_LIST, productIdList);
				/*Bundle skuDetails = billingService.getSkuDetails(Constants.GOOGLE_API_VERSION,
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
					return productDetails;*/
                SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder()
                        .setSkusList(productIdList)
                        .setType(purchaseType)
                        .build();
                final ArrayList<SkuDetails> productDetails = new ArrayList<SkuDetails>();

                billingService.querySkuDetailsAsync(skuDetailsParams, new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(BillingResult billingResult, List<com.android.billingclient.api.SkuDetails> detailsList) {
                        int response = billingResult.getResponseCode();
                        if (response == BillingClient.BillingResponseCode.OK) {
                            if (detailsList.size() > 0) {
                                for (int i = 0; i < detailsList.size(); i++) {
                                    String jsonData = detailsList.get(0).getOriginalJson();

                                    JSONObject object = null;
                                    try {
                                        object = new JSONObject(jsonData);
                                        SkuDetails product = new SkuDetails(object);
                                        productDetails.add(product);


                                    } catch (JSONException jsonException) {
                                        jsonException.printStackTrace();
                                    }
                                }


                            }
                        } else {
                            reportBillingError(response, null);
                            Log.e(LOG_TAG, String.format("Failed to retrieve info for %d products, %d",
                                    productIdList.size(),
                                    response));
                        }
                    }
                });
                return productDetails;

            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to call getSkuDetails", e);
                reportBillingError(Constants.BILLING_ERROR_SKUDETAILS_FAILED, e);
            }
        }
        return null;
    }

    public SkuDetails getPurchaseListingDetails(String productId) {
        return getSkuDetails(productId, Constants.PRODUCT_TYPE_MANAGED);
    }

    public SkuDetails getSubscriptionListingDetails(String productId) {
        return getSkuDetails(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION);
    }

    public List<SkuDetails> getPurchaseListingDetails(ArrayList<String> productIdList) {
        return getSkuDetails(productIdList, Constants.PRODUCT_TYPE_MANAGED);
    }

    public List<SkuDetails> getSubscriptionListingDetails(ArrayList<String> productIdList) {
        return getSkuDetails(productIdList, Constants.PRODUCT_TYPE_SUBSCRIPTION);
    }

    @Nullable
    public TransactionDetails getPurchaseTransactionDetails(String productId) {
        return getPurchaseTransactionDetails(productId, cachedProducts);
    }

    @Nullable
    public TransactionDetails getSubscriptionTransactionDetails(String productId) {
        return getPurchaseTransactionDetails(productId, cachedSubscriptions);
    }

    private String detectPurchaseTypeFromPurchaseResponseData(JSONObject purchase) {
        String purchasePayload = getPurchasePayload();
        // regular flow, based on developer payload
        if (!TextUtils.isEmpty(purchasePayload) && purchasePayload.startsWith(Constants.PRODUCT_TYPE_SUBSCRIPTION)) {
            return Constants.PRODUCT_TYPE_SUBSCRIPTION;
        }
        // backup check for the promo codes (no payload available)
        if (purchase != null && purchase.has(Constants.RESPONSE_AUTO_RENEWING)) {
            return Constants.PRODUCT_TYPE_SUBSCRIPTION;
        }
        return Constants.PRODUCT_TYPE_MANAGED;
    }

    /**
    * You can handle this in the onActivity of your app, This is especially good for those interested in saving the
    * purchaseToken in their server. Google recommended using purchaseToken to handle this for consumed products as this is unique for
    * all purchases made by users
    * */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PURCHASE_FLOW_REQUEST_CODE) {
            return false;
        }
        if (data == null) {
            Log.e(LOG_TAG, "handleActivityResult: data is null!");
            return false;
        }
        int responseCode = data.getIntExtra(Constants.RESPONSE_CODE, Constants.BILLING_RESPONSE_RESULT_OK);
        Log.d(LOG_TAG, String.format("resultCode = %d, responseCode = %d", resultCode, responseCode));
        if (resultCode == Activity.RESULT_OK &&
                responseCode == Constants.BILLING_RESPONSE_RESULT_OK) {
            String purchaseData = data.getStringExtra(Constants.INAPP_PURCHASE_DATA);
            String dataSignature = data.getStringExtra(Constants.RESPONSE_INAPP_SIGNATURE);
            try {
                JSONObject purchase = new JSONObject(purchaseData);
                String productId = purchase.getString(Constants.RESPONSE_PRODUCT_ID);
                if (verifyPurchaseSignature(productId, purchaseData, dataSignature)) {
                    String purchaseType = detectPurchaseTypeFromPurchaseResponseData(purchase);
                    BillingCache cache = purchaseType.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)
                            ? cachedSubscriptions : cachedProducts;
                    cache.put(productId, purchaseData, dataSignature);
                    if (eventHandler != null) {
                        eventHandler.onProductPurchased(
                                productId,
                                new TransactionDetails(new PurchaseInfo(purchaseData, dataSignature)));
                    }
                } else {
                    Log.e(LOG_TAG, "Public key signature doesn't match!");
                    reportBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in handleActivityResult", e);
                reportBillingError(Constants.BILLING_ERROR_OTHER_ERROR, e);
            }
            savePurchasePayload(null);
        } else {
            reportBillingError(responseCode, null);
        }
        return true;
    }

    private boolean verifyPurchaseSignature(String productId, String purchaseData, String dataSignature) {
        try {
            /*
             * Skip the signature check if the provided License Key is NULL and return true in order to
             * continue the purchase flow
             */
            return TextUtils.isEmpty(signatureBase64) ||
                    Security.verifyPurchase(productId, signatureBase64, purchaseData, dataSignature);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValidTransactionDetails(TransactionDetails transactionDetails) {
        return verifyPurchaseSignature(transactionDetails.purchaseInfo.purchaseData.productId,
                transactionDetails.purchaseInfo.responseData,
                transactionDetails.purchaseInfo.signature) &&
                checkMerchant(transactionDetails);
    }

    private boolean isPurchaseHistoryRestored() {
        return loadBoolean(getPreferencesBaseKey() + RESTORE_KEY, false);
    }

    private void setPurchaseHistoryRestored() {
        saveBoolean(getPreferencesBaseKey() + RESTORE_KEY, true);
    }

    private void savePurchasePayload(String value) {
        saveString(getPreferencesBaseKey() + PURCHASE_PAYLOAD_CACHE_KEY, value);
    }

    private String getPurchasePayload() {
        return loadString(getPreferencesBaseKey() + PURCHASE_PAYLOAD_CACHE_KEY, null);
    }

    private void reportBillingError(int errorCode, Throwable error) {
        if (eventHandler != null) {
            eventHandler.onBillingError(errorCode, error);
        }
    }

    /**
     * Returns the most recent purchase made by the user for each SKU, even if that purchase is expired, canceled, or consumed.
     *
     * @param type product type, accepts either {@value Constants#PRODUCT_TYPE_MANAGED} or
     * {@value Constants#PRODUCT_TYPE_SUBSCRIPTION}
     * @param extraParams a Bundle with extra params that would be appended into http request
     *      query string. Not used at this moment. Reserved for future functionality.
     *
     * @return @NotNull list of billing history records
     * @throws BillingCommunicationException if billing isn't connected or there was an error during request execution
     */
    public List<BillingHistoryRecord> getPurchaseHistory(String type, Bundle extraParams) throws BillingCommunicationException {

        if (!type.equals(Constants.PRODUCT_TYPE_MANAGED) && !type.equals(Constants.PRODUCT_TYPE_SUBSCRIPTION)) {
            throw new RuntimeException("Unsupported type " + type);
        }

        //IInAppBillingService billing = billingService;
        BillingClient billing = billingService;


        if (billing != null) {

            try {

                List<BillingHistoryRecord> result = new ArrayList<>();
                int resultCode;
                String continuationToken = null;

                PurchasesResult purchasesResult = billingService.queryPurchases(type);
                List<Purchase> bundle = purchasesResult.getPurchasesList();

                resultCode = purchasesResult.getResponseCode();

                ArrayList<Purchase> purchasesList = new ArrayList<>();
                purchasesList.addAll(bundle);

                if (purchasesList != null) {
                    do {
					/*Bundle resultBundle = billing.getPurchaseHistory(Constants.GOOGLE_API_REQUEST_PURCHASE_HISTORY_VERSION,
							contextPackageName, type, continuationToken, extraParams);*/
                        //resultCode = resultBundle.getInt(Constants.RESPONSE_CODE);

                        if (resultCode == Constants.BILLING_RESPONSE_RESULT_OK) {


                            //List<String> purchaseData = resultBundle.getStringArrayList(Constants.INAPP_PURCHASE_DATA_LIST);

                            //List<String> signatures = resultBundle.getStringArrayList(Constants.INAPP_DATA_SIGNATURE_LIST);

                            if (purchasesList != null) {

                                for (int i = 0, max = purchasesList.size(); i < max; i++) {
                                    //String data = purchaseData.get(i);
                                    String data = purchasesList.get(i).getOriginalJson(); // replacing the comment above

                                    //String signature = signatures.get(i);
                                    String signature = purchasesList.get(i).getSignature();
                                    if (data != null && signature != null) {
                                        BillingHistoryRecord record = new BillingHistoryRecord(data, signature);
                                        result.add(record);
                                    }


                                }

                                //continuationToken = resultBundle.getString(Constants.INAPP_CONTINUATION_TOKEN);
                            }
                        }

//				} while (continuationToken != null && resultCode == Constants.BILLING_RESPONSE_RESULT_OK);
                    } while (purchasesList.size() < 5 && resultCode == Constants.BILLING_RESPONSE_RESULT_OK);
                }


                return result;

            } catch (JSONException e) {
                throw new BillingCommunicationException(e);
            }

        } else {
            throw new BillingCommunicationException("Billing service isn't connected");
        }
    }




    void handlePurchase(Purchase purchase) {
        // Purchase retrieved from BillingClient#queryPurchasesAsync or your PurchasesUpdatedListener.
        //Purchase purchase =

        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.

        ConsumeParams consumeParams =
                ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();


        ConsumeResponseListener listener = new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // Handle the success of the consume operation.
                    handler.post(new Runnable() {
                        @Override
                        public void run() {

                          //  showToast(context,"You have completed payment for iab " + purchaseToken);
                        }
                    });

                }
            }
        };


        //Acknowledging purchase
        AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {

            }
        };

        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();

                Intent intent = new Intent();
                intent.putExtra(Constants.RESPONSE_CODE, BillingClient.BillingResponseCode.OK);
                intent.putExtra(Constants.INAPP_PURCHASE_DATA,purchase.getOriginalJson());
                intent.putExtra(Constants.RESPONSE_INAPP_SIGNATURE,purchase.getSignature());

                handleActivityResult(PURCHASE_FLOW_REQUEST_CODE,Activity.RESULT_OK, intent);

               billingService.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }


            billingService.consumeAsync(consumeParams, listener);
        }


    }


    PurchasesUpdatedListener purchasesUpdatedListener = new
            PurchasesUpdatedListener() {
                @Override
                public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {

                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                        if (purchases != null) {

                            for (final Purchase purchase : purchases) {

                                handlePurchase(purchase);

                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String json = purchase.getOriginalJson();
                                        //Toast.makeText(context,"Success",Toast.LENGTH_LONG).show();

                                        Log.d("Purchase; ",json);
                                    }
                                });
                            }
                        }
                    } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        if (purchases != null) {

                            for (final Purchase purchase : purchases) {



                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String json = purchase.getOriginalJson();
                                        //Toast.makeText(getInstance().context,"Purchased: "+json,Toast.LENGTH_LONG).show();
                                        Log.d("Already Purchase; ",json);
                                    }
                                });
                                //showToast("You have have already make payment for iab " +
                                //  purchase.getSignature()
                                //);
                            }
                        }
                    }

                }
            };
}
