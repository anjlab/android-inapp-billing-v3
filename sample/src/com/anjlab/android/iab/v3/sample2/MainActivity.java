package com.anjlab.android.iab.v3.sample2;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.IBillingHandler;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements IBillingHandler, View.OnClickListener {

    BillingProcessor bp;
    boolean readyToPurchase = false;
    static final String LOG_TAG = "iabv3";

    // PUT YOUR TEST PRODUCT / SUBSCRIPTION IDS HERE
    static final String PRODUCT_ID = "com.anjlab.test.iab.s2.p5";
    static final String SUBSCRIPTION_ID = "com.anjlab.test.iab.subs1";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.consumeButton).setOnClickListener(this);
        findViewById(R.id.purchaseButton).setOnClickListener(this);
        findViewById(R.id.subscribeButton).setOnClickListener(this);
        findViewById(R.id.updateSubscriptionsButton).setOnClickListener(this);

        bp = new BillingProcessor(this);
//        bp.verifyPurchasesWithLicenseKey("YOUR MERCHANT KEY HERE");
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
        showToast("onPurchaseHistoryRestored");
        for(String sku : bp.listOwnedProducts())
            Log.d(LOG_TAG, "Owned Managed Product: " + sku);
        for(String sku : bp.listOwnedSubscriptions())
            Log.d(LOG_TAG, "Owned Subscription: " + sku);
        updateTextViews();
    }

    @Override
    public void onProductPurchased(String productId) {
        showToast("onProductPurchased: " + productId);
        updateTextViews();
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        showToast("onBillingError: " + Integer.toString(errorCode));
    }

    @Override
    public void onBillingInitialized() {
        readyToPurchase = true;
        updateTextViews();
    }

    private void updateTextViews() {
        TextView text = (TextView)findViewById(R.id.productIdTextView);
        text.setText(String.format("%s is%s purchased", PRODUCT_ID, bp.isPurchased(PRODUCT_ID) ? "" : " not"));

        text = (TextView)findViewById(R.id.subscriptionIdTextView);
        text.setText(String.format("%s is%s subscribed", SUBSCRIPTION_ID, bp.isSubscribed(SUBSCRIPTION_ID) ? "" : " not"));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void onClick(View v) {
        if (readyToPurchase)
            switch (v.getId()) {
                case R.id.purchaseButton:
                    bp.purchase(PRODUCT_ID);
                    break;
                case R.id.consumeButton:
                    Boolean consumed = bp.consumePurchase(PRODUCT_ID);
                    updateTextViews();
                    if (consumed)
                        showToast("Successfully consumed");
                    break;
                case R.id.subscribeButton:
                    bp.subscribe(SUBSCRIPTION_ID);
                    break;
                case R.id.updateSubscriptionsButton:
                    if (bp.restoreSubscriptions()) {
                        showToast("Subscriptions updated.");
                        updateTextViews();
                    }
                    break;
                default:
                    break;
            }
        else
            showToast("Billing was not initialized yet.");
    }

}
