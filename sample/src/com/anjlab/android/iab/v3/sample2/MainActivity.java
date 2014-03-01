package com.anjlab.android.iab.v3.sample2;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.IBillingHandler;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;

public class MainActivity extends Activity implements IBillingHandler {

	BillingProcessor bp;
	static final String LOG_TAG = "test";
    static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ9AMIIBCgKCAQEAhiglXguRsKwT1o5kmZ34UoeeKOxJlcmYotn0git statusTiPyzCpRtVx7ZB+XVb6dKRGY1uu0HsR0eW2nto7YmJWR/8/RsB1wDVi9gpqzluxRWmx5o7C5+qk4Tx+asJjAVYP8ESoDbp7sB7sudAuHE8pMir8vYEiaXyAxxeh/exLgxGyYDlXhe25Dy7ghnfkkXlh+qRCUbAWh9QGUMnX6sMTerjn/QNO/ODkoa0G9HZLfA+rWXrAxCCRTIIWFj1mXHHZNK7Mp0ApvoOOc9XJVCVfa6NJennaLFURo4MSbej1PKZT34WfImiltWTLir0L8XnZDaS2yZMMiQ/47TCv59ZygFbwnSQIDAQAB";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bp = new BillingProcessor(this);
        bp.verifyPurchasesWithLicenseKey(PUBLIC_KEY);
		bp.setBillingHandler(this);
	}

	@Override
	public void onDestroy() {
		if (bp != null) 
			bp.release();
		
		super.onDestroy();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!bp.handleActivityResult(requestCode, resultCode, data))
			super.onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	public void onPurchaseHistoryRestored() {
		Log.d(LOG_TAG, "onPurchaseHistoryRestored");
        for(String sku : bp.listOwnedProducts())
            Log.d(LOG_TAG, "Owned Managed Product: " + sku);
        for(String sku : bp.listOwnedSubscriptions())
            Log.d(LOG_TAG, "Owned Subscription: " + sku);
	}
	
	@Override
	public void onProductPurchased(String productId) {
		Log.d(LOG_TAG, "onProductPurchased: " + productId);
	}
	
	@Override
	public void onBillingError(int errorCode, Throwable error) {
		Log.d(LOG_TAG, "onBillingError: " + Integer.toString(errorCode));
	}

	@Override
	public void onBillingInitialized() {
		bp.purchase("com.anjlab.test.iab.s2.p5");
	}

}
