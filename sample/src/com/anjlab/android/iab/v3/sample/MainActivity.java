package com.anjlab.android.iab.v3.sample;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.IBillingHandler;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;

public class MainActivity extends Activity implements IBillingHandler {

	BillingProcessor bp;
	static final String LOG_TAG = "test";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bp = new BillingProcessor(this);
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
		bp.purchase("com.anjlab.test.iab.p6");
	}

}
