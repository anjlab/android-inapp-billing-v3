package com.anjlab.android.iab.v3;

import java.util.ArrayList;
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
import android.os.RemoteException;
import android.util.Log;

public class BillingProcessor extends BillingBase implements IBillingHandler {

	public static final int PURCHASE_FLOW_REQUEST_CODE = 2061984;
	private static final String LOG_TAG = "iabv3";
	private static final String RESTORE_KEY = ".products.restored";

	IInAppBillingService billingService;
	String contextPackageName;
	String purchasePayload;
	BillingCache cachedProducts;
	IBillingHandler eventHandler;

	ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			billingService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			billingService = IInAppBillingService.Stub.asInterface(service);

			if (!isPurchaseHistoryRestored() && loadOwnedProductsFromGoogle())
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
		cachedProducts = new BillingCache(context);
		context.bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND"),  serviceConnection, Context.BIND_AUTO_CREATE);
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

	public void setBillingHandler(IBillingHandler handler) {
		eventHandler = handler;
	}

	public boolean loadOwnedProductsFromGoogle() {
		if (billingService != null) {
			try {
				Bundle bundle = billingService.getPurchases(3, contextPackageName, "inapp", null);
				int response = bundle.getInt(Constants.RESPONSE_CODE);
				if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
					ArrayList<String> responseList = bundle.getStringArrayList(Constants.INAPP_PURCHASE_ITEM_LIST);
					cachedProducts.clear();
					cachedProducts.putAll(responseList);
				}
				return true;
			} 
			catch (RemoteException e) {
				onBillingError(Constants.BILLING_ERROR_FAILED_LOAD_PURCHASED_PRODUCTS, e);
				Log.e(LOG_TAG, e.toString());
			}
		}
		return false;
	}

	public boolean isPurchased(String productId) {
		return cachedProducts.includes(productId);
	}

	public boolean purchase(String productId) {
		if (billingService != null) {
			try {
				purchasePayload = UUID.randomUUID().toString();
				Bundle bundle = billingService.getBuyIntent(3, contextPackageName, productId, "inapp", purchasePayload);
				if (bundle != null) {
					int response = bundle.getInt(Constants.RESPONSE_CODE);
					if (response == Constants.BILLING_RESPONSE_RESULT_OK) {
						PendingIntent pendingIntent = bundle.getParcelable(Constants.BUY_INTENT);
						if (getContext() != null)
							getContext().startIntentSenderForResult(pendingIntent.getIntentSender(), PURCHASE_FLOW_REQUEST_CODE, new Intent(), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0));
						else
							onBillingError(Constants.BILLING_ERROR_LOST_CONTEXT, null);
					} 
					else if (response == Constants.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED)
					{
						cachedProducts.put(productId);
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

	public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PURCHASE_FLOW_REQUEST_CODE) {
			int responseCode = data.getIntExtra(Constants.RESPONSE_CODE, Constants.BILLING_RESPONSE_RESULT_OK);

			if (resultCode == Activity.RESULT_OK && responseCode == Constants.BILLING_RESPONSE_RESULT_OK) {
				String purchaseData = data.getStringExtra(Constants.INAPP_PURCHASE_DATA);

				try {
					JSONObject jo = new JSONObject(purchaseData);
					String productId = jo.getString("productId");
					String developerPayload = jo.getString("developerPayload");
					if (purchasePayload.equals(developerPayload)) {
						cachedProducts.put(productId);
						onProductPurchased(productId);
					}
					else
					{
						Log.e(LOG_TAG, String.format("Payload mismatch: %s != %s", purchasePayload, developerPayload));
						onBillingError(Constants.BILLING_ERROR_INVALID_SIGNATURE, null);
					}
				}
				catch (Exception e) {
					Log.e(LOG_TAG, e.toString());
				}

			}
			else
				onBillingError(Constants.BILLING_ERROR_OTHER_ERROR, null);

			return true;
		}
		return false;
	}

	private boolean isPurchaseHistoryRestored() {
		return loadBoolean(getPreferencesBaseKey() + RESTORE_KEY, false);
	}

	public void setPurchaseHistoryRestored() {
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
