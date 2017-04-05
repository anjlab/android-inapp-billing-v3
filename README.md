# Android In-App Billing v3 Library [![Build Status](https://travis-ci.org/anjlab/android-inapp-billing-v3.svg?branch=master)](https://travis-ci.org/anjlab/android-inapp-billing-v3)  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.anjlab.android.iab.v3/library/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.anjlab.android.iab.v3/library)

This is a simple, straight-forward implementation of the Android v3 In-app billing API.

It supports: In-App Product Purchases (both non-consumable and consumable) and Subscriptions.

## Getting Started

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
		// or bp = BillingProcessor.newBillingProcessor(this, "YOUR LICENSE KEY FROM GOOGLE PLAY CONSOLE HERE", this);
		// See below on why this is a useful alternative
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

_Without a developer payload:_
```java
bp.purchase(YOUR_ACTIVITY, "YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
bp.subscribe(YOUR_ACTIVITY, "YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```
_With a developer payload:_
```java
bp.purchase(YOUR_ACTIVITY, "YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE", "DEVELOPER PAYLOAD HERE");
bp.subscribe(YOUR_ACTIVITY, "YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE", "DEVELOPER PAYLOAD HERE");
```
_IMPORTANT: when you provide a payload, internally the library prepends a string to your payload. For subscriptions, it prepends `"subs:\<productId\>:"`, and for products, it prepends `"inapp:\<productId\>:\<UUID\>:"`. This is important to know if you do any validation on the payload returned from Google Play after a successful purchase._
* **That's it! A super small and fast in-app library ever!**

* **And don't forget**
 to release your BillingProcessor instance! 
```java
	@Override
	public void onDestroy() {
		if (bp != null) 
			bp.release();
		
		super.onDestroy();
	}
```

### Instantiating a `BillingProcessor` with late initialization
The basic `new BillingProcessor(...)` actually binds to Play Services inside the constructor. This can, very rarely, lead to a race condition where Play Services are bound and `onBillingInitialized()` is called before the constructor finishes, and can lead to NPEs. To avoid this, we have the following:
```java
bp = BillingProcessor.newBillingProcessor(this, "YOUR LICENSE KEY FROM GOOGLE PLAY CONSOLE HERE", this); // doesn't bind
bp.initialize(); // binds
```

## Testing In-app Billing

Here is a [complete guide](https://developer.android.com/google/play/billing/billing_testing.html).
Make sure you read it before you start testing

## Check Play Market services availability

Before any usage it's good practice to check in-app billing services availability.
In some older devices or chinese ones it may happen that Play Market is unavailable or is deprecated
 and doesn't support in-app billing.

Simply call static method `BillingProcessor.isIabServiceAvailable()`:
```java
    boolean isAvailable = BillingProcessor.isIabServiceAvailable();
    if(!isAvailable) {
        // continue
    }
```
Please notice that calling `BillingProcessor.isIabServiceAvailable()` (only checks Play Market app installed or not) is not enough because there might be a case when it returns true but still payment won't succeed.
Therefore, it's better to call `isOneTimePurchaseSupported()` after initializing `BillingProcessor`:
```java
    boolean isOneTimePurchaseSupported = billingProcessor.isOneTimePurchaseSupported();
    if(isOneTimePurchaseSupported) {
        // launch payment flow
    }
```
or call `isSubscriptionUpdateSupported()` for checking update subscription use case:
```java
    boolean isSubsUpdateSupported = billingProcessor.isSubscriptionUpdateSupported();
    if(isSubsUpdateSupported) {
        // launch payment flow
    }
```

## Consume Purchased Products

You can always consume made purchase and allow to buy same product multiple times. To do this you need:
```java
	bp.consumePurchase("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
```

## Restore Purchases & Subscriptions

```java
	bp.loadOwnedPurchasesFromGoogle();
```

## Getting Listing Details of Your Products

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

## Getting Purchase Transaction Details
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

## Handle Canceled Subscriptions

Call `bp.getSubscriptionTransactionDetails(...)` and check the `purchaseInfo.purchaseData.autoRenewing` flag.
It will be set to `False` once subscription gets cancelled.
Also notice, that you will need to call periodically `bp.loadOwnedPurchasesFromGoogle()` method in order to update subscription information

## Promo Codes Support

You can use promo codes along with this library, they are supported with one extra notice. According to [this issue](https://github.com/googlesamples/android-play-billing/issues/7)
there is currently a bug in Google Play Services, and it does not respect _Developer Payload_ token made by this library, causing a security validation fault (`BILLING_ERROR_INVALID_DEVELOPER_PAYLOAD` error code).
While Google engineers are working on fixing this (lets hope so, you can also leave a feedback on this issue to make them work faster).

Still, there are couple of workarounds you can use:

1. Handle `BILLING_ERROR_INVALID_DEVELOPER_PAYLOAD` error code in your `onBillingError` implementation. You can check out [#156](https://github.com/anjlab/android-inapp-billing-v3/issues/156) for a suggested workaround. This does not look nice, but it works.
2. Avoid using promo codes in a purchase dialog, prefer entering these codes in Google Play's App _Redeem promo code_ menu.
One way to do this is to distribute your promo codes in form of a redeem link (`https://play.google.com/redeem?code=YOURPROMOCODE`) instead of just a `YOURPROMOCODE` values.
You can find a sample on how to bundle it inside your app [here](https://gist.github.com/SuperThomasLab/6d44b4920dbdc8482a2467d95f66c5df).

## Protection Against Fake "Markets"

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
