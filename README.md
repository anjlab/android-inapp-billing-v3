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
       compile 'com.anjlab.android.iab.v3:library:1.0.+'
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
		 * Called when BillingProcessor was initialized and it's ready to purchase 
		 */
	}
	
	@Override
	public void onProductPurchased(String productId, TransactionDetails details) {
		/*
		 * Called when requested PRODUCT ID was successfully purchased
		 */
	}
	
	@Override
	public void onBillingError(int errorCode, Throwable error) {
		/*
		 * Called when some error occurred. See Constants class for more details
		 * 
		 * Note - this includes handling the case where the user canceled the buy dialog:
		 * errorCode = Constants.BILLING_RESPONSE_RESULT_USER_CANCELED
		 */
	}
	
	@Override
	public void onPurchaseHistoryRestored() {
		/*
		 * Called when purchase history was restored and the list of all owned PRODUCT ID's 
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
bp.purchase(YOUR_ACTIVITY, "YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
bp.subscribe(YOUR_ACTIVITY, "YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
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

Check Play Market services availability
---------------------------------------
Before any usage it's good practice to check in-app billing services availability.
In some elder devices or chinese ones it may happen that Play Market is unavailable or is deprecated
 and doesn't support in-app billing.

Simply call static method `BillingProcessor.isIabServiceAvailable()`:
```java
    boolean isAvailable = BillingProcessor.isIabServiceAvailable();
    if(!isAvailable) {
        // continue
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

To get info for multiple products / subscriptions on one query, just pass a list of product ids:

```java
	bp.getPurchaseListingDetails(arrayListOfProductIds);
	bp.getSubscriptionListingDetails(arrayListOfProductIds);
```

where arrayListOfProductIds is a `ArrayList<String>` containing either IDs for products or subscriptions.

As a result you will get a `List<SkuDetails>` which contains objects described above.

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

Protection against fake "markets"
--------------------------------
There are number of attacks which exploits some vulnerabilities of Google's Play Market.
Among them is so-called *Freedom attack*: *Freedom* is special Android application, which
intercepts application calls to Play Market services and substitutes them with fake ones. So in the
  end attacked application *thinks* that it receives valid responses from Play Market.

In order to protect from this kind of attack you should specify your `merchantId`, which
can be found in your [Payments Merchant Account](https://payments.google.com/merchant).
Selecting *Settings->Public Profile* you will find your unique `merchantId`

**WARNING:** keep your `merchantId` in safe place!

Then using `merchantId` just call constructor:

    public BillingProcessor(Context context, String licenseKey, String merchantId, IBillingHandler handler);

Later one can easily check transaction validity using method:

    public boolean isValid(TransactionDetails transactionDetails);

P.S. This kind of protection works only for transactions dated between 5th December 2012 and
21st July 2015. Before December 2012 `orderId` wasn't contain `merchantId` and in the end of July this
 year Google suddenly changed `orderId` format.


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
