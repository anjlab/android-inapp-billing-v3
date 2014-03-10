package com.anjlab.android.iab.v3;

import java.util.List;
import java.util.UUID;

import org.json.JSONObject;

import com.android.vending.billing.IInAppBillingService;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

public class BillingProcessor extends BillingBase implements IBillingHandler {

	public static final int PURCHASE_FLOW_REQUEST_CODE = 2061984;
    private static final String LOG_TAG = "iabv3";
    private static final String SETTINGS_VERSION = ".v2_4";
	private static final String RESTORE_KEY = ".products.restored" + SETTINGS_VERSION;
    private static final String MANAGED_PRODUCTS_CACHE_KEY = ".products.cache" + SETTINGS_VERSION;
    private static final String SUBSCRIPTIONS_CACHE_KEY = ".subscriptions.cache" + SETTINGS_VERSION;

	IInAppBillingService billingService;
	String contextPackageName;
	String purchasePayload;
    String signatureBase64;
    BillingCache cachedProducts;
    BillingCache cachedSubscriptions;
	IBillingHandler eventHandler;

	ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			billingService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			billingService = IInAppBillingService.Stub.asInterface(service);

			if (!isPurchaseHistoryRestored() && loadOwnedPurchasesFromGoogle())
            {
				setPurchaseHistoryRestored();
				onPurchaseHistoryRestored();
			}

			onBillingInitialized();
		}
	};

	public BillingProcessor(Activity context) {
		super(context);
		contextPackageName = context.getApplicationContext().getPackageName();
        cachedProducts = new BillingCache(context, MANAGED_PRODUCTS_CACHE_KEY);
        cachedSubscriptions = new BillingCache(context, SUBSCRIPTIONS_CACHE_KEY);
		bindPlayServices();
	}
	
	private void bindPlayServices() {
		try {
			getContext().bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),  serviceConnection, Context.BIND_AUTO_CREATE);
		}
		catch (Exception e) {
			Log.e(LOG_TAG, e.toString());
		}
	}

	@Override
	public void release() {
		if (serviceConnection != null && getContext() != null) {
			try {
				getContext().unbindService(serviceConnection);
			}
			catch (Exception e) {
				Log.e(LOG_TAG, e.toString());
			}
			billingService = null;
		}
		cachedProducts.release();
		super.release();
	}

    public void verifyPurchasesWithLicenseKey(String signature) {
        signatureBase64 = signature;
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

	public void setBillingHandler(IBillingHandler handler) {
		eventHandler = handler;
	}

    private boolean loadPurchasesByType(String type, BillingCache cacheStorage) {
        if (isInitialized())
            try {
                Bundle bundle = billingService.getPurchases(Constants.GOOGLE_API_VERSION, contextPackageName, type, null);

                if (bundle.getInt(Constants.RESPONSE_CODE) == Constants.BILLING_RESPONSE_RESULT_OK) {
                    cacheStorage.clear();
                    for (String purchaseData : bundle.getStringArrayList(Constants.INAPP_PURCHASE_DATA_LIST)) {
                        JSONObject purchase = new JSONObject(purchaseData);
                        cacheStorage.put(purchase.getString("productId"), purchase.getString("purchaseToken"));
                    }
                }
                return true;
            }
            catch (Exception e) {
                onBillingError(Constants.BILLING_ERROR_FAILED_LOAD_PURCHASES, e);
                Log.e(LOG_TAG, e.toString());
            }
        return false;
    }

    public boolean loadOwnedPurchasesFromGoogle() {
        return isInitialized() && restorePurchases() && restoreSubscriptions();
    }

    public boolean restorePurchases() {
        return isInitialized() && loadPurchasesByType(Constants.PRODUCT_TYPE_MANAGED, cachedProducts);
    }

    public boolean restoreSubscriptions() {
        return isInitialized() && loadPurchasesByType(Constants.PRODUCT_TYPE_SUBSCRIPTION, cachedSubscriptions);
    }

    public boolean purchase(String productId) {
        return purchase(productId, Constants.PRODUCT_TYPE_MANAGED);
    }

    public boolean subscribe(String productId) {
        return purchase(productId, Constants.PRODUCT_TYPE_SUBSCRIPTION);
    }

	private boolean purchase(String productId, String purchaseType) {
		if (isInitialized()) {
			try {
				purchasePayload = String.format("%s:%s", purchaseType, UUID.randomUUID());

				Bundle bundle = billingService.getBuyIntent(Constants.GOOGLE_API_VERSION, contextPackageName, productId, purchaseType, purchasePayload);
				if (bundle != null) {
                    int response = bundle.getInt(Constants.RESPONSE_CODE);
					if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
						PendingIntent pendingIntent = bundle.getParcelable(Constants.BUY_INTENT);
						if (getContext() != null)
							getContext().startIntentSenderForResult(pendingIntent.getIntentSender(), PURCHASE_FLOW_REQUEST_CODE, new Intent(), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0));
						else
							onBillingError(Constants.BILLING_ERROR_LOST_CONTEXT, null);
					} 
					else if (response == Constants.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
                        if (!isPurchased(productId) && !isSubscribed(productId))
                            loadOwnedPurchasesFromGoogle();
                        onProductPurchased(productId);
					}
					else
						onBillingError(Constants.BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE, null);
				}
				return true;
			} 
			catch (Exception e) {
				Log.e(LOG_TAG, e.toString());
			}
		}
		return false;
	}

    public boolean consumePurchase(String productId) {
        if (isInitialized()) {
            try {
                String purchaseToken = cachedProducts.getProductPurchaseToken(productId);
                if (!TextUtils.isEmpty(purchaseToken)) {
                    int response =  billingService.consumePurchase(Constants.GOOGLE_API_VERSION, contextPackageName, purchaseToken);
                    if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
                        cachedProducts.remove(productId);
                        Log.d(LOG_TAG, "Successfully consumed " + productId + " purchase.");
                        return  true;
                    }
                    else {
                        onBillingError(response, null);
                        Log.e(LOG_TAG, String.format("Failed to consume %s: error %d", productId, response));
                    }
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, e.toString());
            }
        }
        return false;
    }

	public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PURCHASE_FLOW_REQUEST_CODE) {
			int responseCode = data.getIntExtra(Constants.RESPONSE_CODE, Constants.BILLING_RESPONSE_RESULT_OK);

			if (resultCode == Activity.RESULT_OK && responseCode == Constants.BILLING_RESPONSE_RESULT_OK) {
				String purchaseData = data.getStringExtra(Constants.INAPP_PURCHASE_DATA);
                String dataSignature = data.getStringExtra(Constants.RESPONSE_INAPP_SIGNATURE);

				try {
					JSONObject purchase = new JSONObject(purchaseData);
                    String productId = purchase.getString("productId");
                    String purchaseToken = purchase.getString("purchaseToken");
					String developerPayload = purchase.getString("developerPayload");
					if (purchasePayload.equals(developerPayload)) {
                        if (verifyPurchaseSignature(purchaseData, dataSignature)) {
                            BillingCache cache = developerPayload.startsWith(Constants.PRODUCT_TYPE_SUBSCRIPTION) ? cachedSubscriptions : cachedProducts;
                            cache.put(productId, purchaseToken);
                            onProductPurchased(productId);
                        }
                        else {
                            Log.e(LOG_TAG, "Public key signature doesn't match!");
                            onBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
                        }
					}
					else {
						Log.e(LOG_TAG, String.format("Payload mismatch: %s != %s", purchasePayload, developerPayload));
						onBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
					}
				}
				catch (Exception e) {
                    Log.e(LOG_TAG, e.toString());
                    onBillingError(Constants.BILLING_ERROR_OTHER_ERROR, null);
				}
			}
			else
				onBillingError(Constants.BILLING_ERROR_OTHER_ERROR, null);

			return true;
		}
		return false;
	}

    private boolean verifyPurchaseSignature(String purchaseData, String dataSignature) {
        if (!TextUtils.isEmpty(signatureBase64)) {
            try {
                return Security.verifyPurchase(signatureBase64, purchaseData, dataSignature);
            }
            catch (Exception e) {
                return false;
            }
        }
        return true;
    }

	private boolean isPurchaseHistoryRestored() {
		return loadBoolean(getPreferencesBaseKey() + RESTORE_KEY, false);
	}

	private void setPurchaseHistoryRestored() {
		saveBoolean(getPreferencesBaseKey() + RESTORE_KEY, true);
	}

	@Override
	public void onProductPurchased(String productId) {
		if (eventHandler != null)
			eventHandler.onProductPurchased(productId);
	}

	@Override
	public void onPurchaseHistoryRestored() {
		if (eventHandler != null)
			eventHandler.onPurchaseHistoryRestored();
	}

	@Override
	public void onBillingError(int errorCode, Throwable error) {
		if (eventHandler != null)
			eventHandler.onBillingError(errorCode, error);
	}

	@Override
	public void onBillingInitialized() {
		if (eventHandler != null)
			eventHandler.onBillingInitialized();
	}

}
