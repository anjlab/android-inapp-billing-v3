Android In-App Billing v3 Library
=======================

This is a simple, straight-forward implementation of the Android v3 In-app billing API.

It supports: In-App Product Purchases (both non-consumable and consumable) and Subscriptions.

Getting Started
===============

* You project should build against Android 2.2 SDK at least.

* Add this *Android In-App Billing v3 Library* to your project:
  - If you guys are using Eclipse, download latest jar version from the [releases](https://github.com/anjlab/android-inapp-billing-v3/releases) section of this repository and add it as a dependency
  - If you guys are using Android Studio and Gradle, add this to you build.gradle file:
```groovy
    repositories {
        mavenCentral()
    }
    dependencies {
       compile 'com.anjlab.android.iab.v3:library:1.0.+@aar'
    }
```

* Open the *AndroidManifest.xml* of your application and add this permission:
```xml
  <uses-permission android:name="com.android.vending.BILLING" />
```
* Create instance of BillingProcessor class and implement callback in your Activity source code. Constructor will take 3 parameters:
  - **Context**
  - **Your License Key from Google Developer console.** This will be used to verify purchase signatures. You can pass NULL if you would like to skip this check (*You can find your key in Google Play Console -> Your App Name -> Services & APIs*)
  - **IBillingHandler Interface implementation to handle purchase results and errors** (see below)
```java
public class SomeActivity extends Activity implements BillingProcessor.IBillingHandler {
	BillingProcessor bp;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bp = new BillingProcessor(this, "YOUR LICENSE KEY FROM GOOGLE PLAY CONSOLE HERE", this);
	}
	
	// IBillingHandler implementation
	
	@Override
	public void onBillingInitialized() {
		/*
		 * Called then BillingProcessor was initialized and its ready to purchase 
		 */
	}
	
	@Override
	public void onProductPurchased(String productId, TransactionDetails details) {
		/*
		 * Called then requested PRODUCT ID was successfully purchased
		 */
	}
	
	@Override
	public void onBillingError(int errorCode, Throwable error) {
		/*
		 * Called then some error occured. See Constants class for more details
		 */
	}
	
	@Override
	public void onPurchaseHistoryRestored() {
		/*
		 * Called then purchase history was restored and the list of all owned PRODUCT ID's 
		 * was loaded from Google Play
		 */
	}
}
```

* override Activity's onActivityResult method:
```java
  @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!bp.handleActivityResult(requestCode, resultCode, data))
			super.onActivityResult(requestCode, resultCode, data);
	}
```

* Call `purchase` method for a BillingProcessor instance to initiate purchase or `subscribe` to initiate a subscription:
```java
bp.purchase("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
bp.subscribe("YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```
* **That's it! A super small and fast in-app library ever!**

* **And dont forget**
 to release your BillingProcessor instance! 
```java
	@Override
	public void onDestroy() {
		if (bp != null) 
			bp.release();
		
		super.onDestroy();
	}
```

Consume Purchased Products
--------------------------
You can always consume made purchase and allow to buy same product multiple times. To do this you need:
```java
	bp.consumePurchase("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
```

Restore Purchases & Subscriptions
--------------------------
```java
	bp.loadOwnedPurchasesFromGoogle();
```

Notice On Canceled/Expired Subscriptions
--------------------------
Since Google's v3 API doesn't provide any callbacks to handle canceled and/or expired subscriptions you have to handle it on your own.
The easiest way to do this - call periodically `bp.loadOwnedPurchasesFromGoogle()` method.

Getting Listing Details of Your Products
--------------------------
To query listing price and a description of your product / subscription listed in Google Play use these methods:

```java
    bp.getPurchaseListingDetails("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
    bp.getSubscriptionListingDetails("YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```

As a result you will get a `SkuDetails` object with the following info included:

```java
    public final String productId;
    public final String title;
    public final String description;
    public final boolean isSubscription;
    public final String currency;
    public final Double priceValue;
    public final String priceText;
```

Getting Purchase Transaction Details
--------------------------
As a part or 1.0.9 changes, `TransactionDetails` object is passed to `onProductPurchased` method of a handler class.
However, you can always retrieve it later calling these methods:

```java
    bp.getPurchaseTransactionDetails("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
    bp.getSubscriptionTransactionDetails("YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```

As a result you will get a `TransactionDetails` object with the following info included:

```java
    public final String productId;
    public final String orderId;
    public final String purchaseToken;
    public final Date purchaseTime;
    
    // containing the raw json string from google play and the signature to
    // verify the purchase on your own server
    public final PurchaseInfo purchaseInfo;
```

## License

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

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. **Create New Pull Request**
