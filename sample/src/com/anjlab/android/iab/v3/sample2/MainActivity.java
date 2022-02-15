/*
  Copyright 2014 AnjLab

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.anjlab.android.iab.v3.sample2;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseInfo;
import com.anjlab.android.iab.v3.SkuDetails;

import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MainActivity extends Activity {
	// SAMPLE APP CONSTANTS
	private static final String ACTIVITY_NUMBER = "activity_num";
	private static final String LOG_TAG = "iabv3";

    // PRODUCT & SUBSCRIPTION IDS
    private static final String PRODUCT_ID = "com.anjlab.test.iab.s2.p5";
    private static final String SUBSCRIPTION_ID = "com.anjlab.test.iab.subs1";
    private static final String LICENSE_KEY = "THE_KEY"; //BuildConfig.LICENSE_KEY; // PUT YOUR MERCHANT KEY HERE;
    // put your Google merchant id here (as stated in public profile of your Payments Merchant Center)
    // if filled library will provide protection against Freedom alike Play Market simulators
    private static final String MERCHANT_ID=null;

	private BillingProcessor bp;
	private boolean readyToPurchase = false;


	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		TextView title = findViewById(R.id.titleTextView);
		title.setText(String.format(getString(R.string.title), getIntent().getIntExtra(ACTIVITY_NUMBER, 1)));

        if(!BillingProcessor.isIabServiceAvailable(this)) {
            showToast("In-app billing service is unavailable, please upgrade Android Market/Play to version >= 3.9.16");
        }

        bp = new BillingProcessor(this, LICENSE_KEY, MERCHANT_ID, new BillingProcessor.IBillingHandler() {
            @Override
            public void onProductPurchased(@NonNull String productId, @Nullable PurchaseInfo purchaseInfo) {
				showToast("onProductPurchased: " + productId);
                updateTextViews();
            }
            @Override
            public void onBillingError(int errorCode, @Nullable Throwable error) {
                showToast("onBillingError: " + errorCode);
            }
            @Override
            public void onBillingInitialized() {
				showToast("onBillingInitialized");
                readyToPurchase = true;
                updateTextViews();
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
        });
    }

	@Override
	protected void onResume() {
		super.onResume();

		updateTextViews();
	}

	@Override
    public void onDestroy() {
        if (bp != null)
            bp.release();
        super.onDestroy();
    }

    private void updateTextViews() {
        TextView text = findViewById(R.id.productIdTextView);
        text.setText(String.format("%s is%s purchased", PRODUCT_ID, bp.isPurchased(PRODUCT_ID) ? "" : " not"));
        text = findViewById(R.id.subscriptionIdTextView);
        text.setText(String.format("%s is%s subscribed", SUBSCRIPTION_ID, bp.isSubscribed(SUBSCRIPTION_ID) ? "" : " not"));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void onClick(View v) {
        if (!readyToPurchase) {
            showToast("Billing not initialized.");
            return;
        }
        if (v.getId() == R.id.purchaseButton) {
            bp.purchase(this,PRODUCT_ID);
        } else if (v.getId() == R.id.consumeButton) {
            bp.consumePurchaseAsync(PRODUCT_ID, new BillingProcessor.IPurchasesResponseListener()
            {
                @Override
                public void onPurchasesSuccess()
                {
                    showToast("Successfully consumed");
                    updateTextViews();
                }

                @Override
                public void onPurchasesError()
                {
                    showToast("Not consumed");
                }
            });
        } else if (v.getId() == R.id.productDetailsButton) {
            bp.getPurchaseListingDetailsAsync(PRODUCT_ID, new BillingProcessor.ISkuDetailsResponseListener() {
                @Override
                public void onSkuDetailsResponse(@Nullable List<SkuDetails> products) {
                    if (products != null && !products.isEmpty()) {
                        showToast(products.get(0).toString());
                    } else {
                        showToast("Failed to load SKU details");
                    }
                }

                @Override
                public void onSkuDetailsError(String error) {
                    showToast(error);
                }
            });
        } else if (v.getId() == R.id.subscribeButton) {
            bp.subscribe(this,SUBSCRIPTION_ID);
        } else if (v.getId() == R.id.updateSubscriptionsButton) {
            bp.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
                @Override
                public void onPurchasesSuccess()
                {
                    showToast("Subscriptions updated.");
                    updateTextViews();
                }

                @Override
                public void onPurchasesError()
                {
                    showToast("Subscriptions update eroor.");
                    updateTextViews();
                }
            });
        } else if (v.getId() == R.id.subsDetailsButton) {
            bp.getSubscriptionListingDetailsAsync(SUBSCRIPTION_ID, new BillingProcessor.ISkuDetailsResponseListener()
            {
                @Override
                public void onSkuDetailsResponse(@Nullable final List<SkuDetails> products) {
                    showToast(products != null ? products.toString() : "Failed to load subscription details");
                }

                @Override
                public void onSkuDetailsError(String string) {
                    showToast(string);
                }
            });
        } else if (v.getId() == R.id.launchMoreButton)
        {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(ACTIVITY_NUMBER, getIntent().getIntExtra(ACTIVITY_NUMBER, 1) + 1);
            startActivity(intent);
        }
    }

}
