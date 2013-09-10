package com.anjlab.android.iab.v3;

public interface IBillingHandler {

	void onProductPurchased(String productId);
	
	void onPurchaseHistoryRestored();

	void onBillingError(int errorCode, Throwable error);
	
	void onBillingInitialized();
}
