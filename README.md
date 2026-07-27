# Android In-App Billing v3 Library [![Build Status](https://github.com/anjlab/android-inapp-billing-v3/actions/workflows/connected-check.yml/badge.svg)](https://github.com/anjlab/android-inapp-billing-v3/actions/workflows/connected-check.yml)  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.anjlab.android.iab.v3/library/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.anjlab.android.iab.v3/library)
This is a simple, straight-forward wrapper around **Google Play Billing Library 9.x**.

It supports: In-App Product Purchases (both non-consumable and consumable) and Subscriptions.

## Maintainers Wanted

This project is looking for maintainers.

For now only pull requests of external contributors are being reviewed, accepted and welcomed — see [Contributing](#contributing) below for the PR workflow. No more bug fixes or new features will be implemented by the Anjlab team.

If you are interesting in giving this project some :heart:, please chime in!

## Play Billing 9.x Upgrade Notice

This library now targets `com.android.billingclient:billing:9.1.0`. The legacy
`SkuDetails` type and the `getPurchaseListingDetailsAsync` / `getSubscriptionListingDetailsAsync`
methods are preserved for source-compatibility but are marked `@Deprecated` —
under the hood they translate from Play Billing 9's `ProductDetails`, which flattens
multi-offer subscriptions down to a single offer. The library picks the best offer
Play reports you are eligible for — a free trial first, then a discounted
introductory offer, then the base plan — and uses that same offer for the purchase
itself, so the price you display is the price charged. Consumers that need multiple
promotional offers or pricing phases should migrate to the new `ProductDetails`-based
API — see the [3.0.0 CHANGELOG entry](CHANGELOG.md#300-2026-07-27) and the
[2.2 → 3.0 upgrade guide](UPGRADING.md#upgrading-from-22-to-30) for the full walkthrough.

**Breaking change (2.x → 3.0)**: `minSdkVersion` is now **23** (Android 6.0).
Play Billing 8.1 dropped support for API 21–22, so consumers still shipping to
those levels must pin to `2.2.0` or upgrade their own `minSdkVersion`.
Play Billing 9.x additionally requires consumers to build against
**`compileSdk 35+`** (it depends on `androidx.core:1.15.0`, which enforces this).

Older history: this was originally Google's v2 Billing API implementation —
source archived [here](https://github.com/anjlab/android-inapp-billing-v3/tree/v2_billing_1_1_0).
Version-by-version migration notes: [UPGRADING.md](UPGRADING.md). Full release
history: [CHANGELOG.md](CHANGELOG.md).

## Getting Started

* Your project must build against `compileSdk 35+` and `minSdk 23+` (Android 6.0 Marshmallow).

* Add this library via Gradle:
```groovy
repositories {
  mavenCentral()
}
dependencies {
  implementation 'com.anjlab.android.iab.v3:library:3.0.0'
}
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
    bp.initialize();
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
  public void onProductPurchased(String productId, PurchaseInfo purchaseInfo) {
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
    * errorCode = BillingClient.BillingResponseCode.USER_CANCELED
    *
    * Codes from Play itself are BillingClient.BillingResponseCode.* constants;
    * codes raised by this library are Constants.BILLING_ERROR_* (100 and above).
    */
  }
	
  @Override
  public void onPurchaseHistoryRestored() {
    /*
    * Called when purchase history was restored and the list of all owned PRODUCT ID's 
    * was loaded from Google Play
    */
  }

  @Override
  public void onPurchasePending(String productId, PurchaseInfo purchaseInfo) {
    /*
    * Optional - this is a default method, so you only override it if you care.
    *
    * Called when Google reports a purchase in PENDING state (deferred payment:
    * cash-at-convenience-store, carrier billing, slow card auth). The purchase is
    * NOT entitled yet - do not grant anything here. Show a "payment pending" UI and
    * wait for the transition to PURCHASED, which arrives via onProductPurchased.
    *
    * See "Handling Pending Purchases" below.
    */
  }
}
```

* Call `purchase` method for a BillingProcessor instance to initiate purchase or `subscribe` to initiate a subscription:

```java
bp.purchase(YOUR_ACTIVITY, "YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
bp.subscribe(YOUR_ACTIVITY, "YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```

Both also accept Play's optional obfuscated identifiers, which let you correlate a
purchase with your own account records without sending Google any personal data:

```java
bp.purchase(YOUR_ACTIVITY, "YOUR PRODUCT ID", obfuscatedAccountId, obfuscatedProfileId);
bp.subscribe(YOUR_ACTIVITY, "YOUR SUBSCRIPTION ID", obfuscatedAccountId, obfuscatedProfileId);
bp.updateSubscription(YOUR_ACTIVITY, oldProductId, "NEW SUBSCRIPTION ID",
                      obfuscatedAccountId, obfuscatedProfileId);
```

If you already hold a `ProductDetails` object you can skip the extra round-trip to
Play and launch the flow directly:

```java
bp.purchase(YOUR_ACTIVITY, productDetails);
bp.purchase(YOUR_ACTIVITY, productDetails, oldProductId); // subscription upgrade/downgrade
```


* **That's it! A super small and fast in-app library ever!**

* **And don't forget**
 to release your BillingProcessor instance! 
```java
@Override
public void onDestroy() {
  if (bp != null) {
    bp.release();
  }		
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

Simply call static method `BillingProcessor.isIabServiceAvailable(context)`:
```java
boolean isAvailable = BillingProcessor.isIabServiceAvailable(this);
if(!isAvailable) {
  // continue
}
```
Please notice that calling `BillingProcessor.isIabServiceAvailable()` (only checks Play Market app installed or not) is not enough because there might be a case when it returns true but still payment won't succeed.
Therefore, it's better to call `bp.isConnected()` after initializing `BillingProcessor`:
```java
boolean isConnected = billingProcessor.isConnected();
if(isConnected) {
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
bp.consumePurchaseAsync("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE", new IPurchasesResponseListener());
```

## Restore Purchases & Subscriptions

```java
bp.loadOwnedPurchasesFromGoogleAsync(new IPurchasesResponseListener());
```

## Getting Listing Details of Your Products

### The `ProductDetails` API (recommended)

These return Play Billing 9's native `ProductDetails`, with the full subscription
offer tree — base plan, promotional offers, and every pricing phase:

```java
bp.getPurchaseProductDetailsAsync("YOUR PRODUCT ID", new IProductDetailsResponseListener() {
  @Override public void onProductDetailsResponse(List<ProductDetails> products) { /* ... */ }
  @Override public void onProductDetailsError(String error) { /* ... */ }
});
bp.getSubscriptionProductDetailsAsync("YOUR SUBSCRIPTION ID", listener);
```

Both also take a `List<String>` to query several products in one call. See the
[upgrade guide](UPGRADING.md#displaying-prices--offers-move-off-the-deprecated-skudetails-api)
for how to read offers and pricing phases out of a `ProductDetails`.

### The legacy `SkuDetails` API (deprecated)

These are kept for source compatibility and are marked `@Deprecated`. They translate
`ProductDetails` down to a single offer, which is lossy — you lose access to multiple
promotional offers and to individual pricing phases:

```java
bp.getPurchaseListingDetailsAsync("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE", new ISkuDetailsResponseListener());
bp.getSubscriptionListingDetailsAsync("YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE", new ISkuDetailsResponseListener());
```

As a result you will get a callback call including `List<SkuDetails>` data with one SkuDetails object with the following info included:

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
bp.getPurchaseListingDetailsAsync(arrayListOfProductIds, new ISkuDetailsResponseListener());
bp.getSubscriptionsListingDetailsAsync(arrayListOfProductIds, new ISkuDetailsResponseListener());
```

where arrayListOfProductIds is a `ArrayList<String>` containing either IDs for products or subscriptions.
Note the plural `getSubscriptionsListingDetailsAsync` on the list overload.


## Getting Purchase Info Details
`PurchaseInfo` object is passed to `onProductPurchased` method of a handler class.
However, you can always retrieve it later calling these methods:

```java
bp.getPurchaseInfo("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
bp.getSubscriptionPurchaseInfo("YOUR SUBSCRIPTION ID FROM GOOGLE PLAY CONSOLE HERE");
```

As a result you will get a `PurchaseInfo` object with the following info included:

```java
public final String responseData;
public final String signature;

// PurchaseData contains orderId, productId, purchaseTime, purchaseToken, purchaseState and autoRenewing fields 
public final PurchaseData purchaseData;
```

`purchaseData.purchaseState` is a `PurchaseState` enum with five values:

| Value | Meaning |
|---|---|
| `PurchasedSuccessfully` | Paid and entitled. This is the only value that should grant access. |
| `Canceled` | Not completed. Also the value reported when Play omits the state field entirely. |
| `Refunded` | Refunded by Google or the developer. |
| `SubscriptionExpired` | Subscription term ended without renewal. |
| `Pending` | Deferred payment awaiting completion — **not** entitled yet. See [Handling Pending Purchases](#handling-pending-purchases-and-the-subscription-it-never-confirms-race). |

`Pending` was added in 3.0.0 and is appended last in the enum, so existing ordinals
stay stable. If you `switch` over `PurchaseState`, add a branch for it.

## Handle Canceled Subscriptions

Call `bp.getSubscriptionPurchaseInfo(...)` and check the `purchaseData.autoRenewing` flag.
It will be set to `False` once subscription gets cancelled.
Also notice, that you will need to call periodically `bp.loadOwnedPurchasesFromGoogleAsync()` method in order to update subscription information

## Handling Pending Purchases (and the subscription "it never confirms" race)

Not every purchase is entitled the moment the buyer taps *Buy*. Deferred payment
methods (cash-at-counter, carrier billing), **slow credit-card authorization**, and
**subscriptions** — which Google often takes longer to clear than a one-time product —
first arrive in the **`PENDING`** state. A pending purchase is **not** yet owned, so it
is reported through `onPurchasePending(...)`, **not** `onProductPurchased(...)`.

`onPurchasePending` is a `default` no-op on `IBillingHandler`; if you don't override it,
a pending purchase is silently swallowed and your UI shows nothing.

The purchase later transitions to `PURCHASED`. That transition is delivered **either**
via `onProductPurchased(...)` on the *next* `onPurchasesUpdated` event, **or** on the
next `bp.loadOwnedPurchasesFromGoogleAsync(...)` (e.g. the next `initialize()`). So a
screen that only reacts to the real-time `onProductPurchased` callback and gates on
`purchaseState == PurchasedSuccessfully` can miss the transition entirely: the buyer
completes payment, sees no confirmation on that screen, and the purchase only "appears"
later elsewhere in the app (or on the next launch) when something happens to re-query.
The race hides well because fast one-time purchases usually win it and clear before you
look — subscriptions and slow-auth cards lose it.

To handle it robustly:

1. **Override `onPurchasePending`** and surface a "payment processing" state — do **not**
   grant entitlement there.
2. **Re-query owned purchases when the screen resumes / is returned to**, and refresh your
   purchase UI from the result rather than relying only on the one-shot callback:

   ```java
   @Override
   public void onResume() {
       super.onResume();
       if (bp != null && bp.isInitialized()) {
           bp.loadOwnedPurchasesFromGoogleAsync(new IPurchasesResponseListener() {
               @Override public void onPurchasesSuccess() { refreshPurchaseUi(); }
               @Override public void onPurchasesError()   { /* keep last known state */ }
           });
       }
   }
   ```

3. **Keep only one active `BillingProcessor` (BillingClient) at a time.** Google delivers
   `onPurchasesUpdated` to the client that launched the flow; if several clients are
   connected on the same license key, the update can land on the wrong handler.
   Re-querying on resume (step 2) also makes your UI correct regardless of which client
   received the real-time callback.

## Promo Codes Support

You can use promo codes along with this library. Promo codes can be entered in the purchase dialog or in the Google Play app. The URL https://play.google.com/redeem?code=YOUR_PROMO_CODE will launch the Google Play app with the promo code already entered. This could come in handy if you want to give users the option to enter a promo code within your app.

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

    public boolean isValidPurchaseInfo(PurchaseInfo purchaseInfo);

P.S. This kind of protection works only for transactions dated between 5th December 2012 and
21st July 2015. Before December 2012 `orderId` wasn't contain `merchantId` and in the end of July this
 year Google suddenly changed `orderId` format.
 
## Proguard

The necessary proguard rules are already added in the library. No further configurations are needed.

The contents in the consumer proguard file contains:

```
-keep class com.android.vending.billing.**
-keep class com.android.billingclient.api.**
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
