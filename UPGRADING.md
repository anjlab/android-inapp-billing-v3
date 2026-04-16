## Upgrading Android In-App Billing v3 Library

### Upgrading from 2.2 to 3.0

**TL;DR**: This release updates `com.android.billingclient:billing` from
`7.0.0` to `8.3.0`. **The everyday purchase API is unchanged** — code that
just calls `bp.purchase(activity, productId)` / `bp.subscribe(activity, productId)`
compiles and runs exactly as before, the new billing client is wired up
under the hood. You only need to touch your code if you were using
`SkuDetails` to display prices or offers in your UI.

#### Required changes in your app

1. **Raise `minSdkVersion` to 23.** Play Billing `8.1+` no longer supports
   API 21–22. If you must stay on those levels, pin this library at `2.2.0`.
2. **Ensure `compileSdk` is at least 34** if you haven't already.
3. **Use a modern AGP.** Any consumer AGP `8.1+` can consume the new `aar`
   without further changes.

That's it for most apps — the following sections only apply if you read
product details directly.

#### Purchasing is unchanged

```java
bp.purchase(activity, productId);      // one-time product
bp.subscribe(activity, productId);     // subscription
bp.updateSubscription(activity, oldProductId, newProductId);
```

These signatures are unchanged from 2.x. They now fetch Billing 8
`ProductDetails` internally instead of `SkuDetails`, and for subscriptions
the library automatically picks the base-plan offer (falling back to the
first available offer) when launching the flow. No code change required.

#### Displaying prices / offers: move off the deprecated `SkuDetails` API

If your app calls `getPurchaseListingDetailsAsync` or
`getSubscriptionListingDetailsAsync` to render prices or offers, those
methods and the `SkuDetails` type they return are now `@Deprecated`. They
keep working — backed internally by a translator from Billing 8
`ProductDetails` — but the translation **collapses multi-offer
subscriptions to a single offer** (preferring the base plan), so you lose
access to promotional offers, alternative pricing phases, and their offer
tokens. Migrate when you can.

**Before** (still works, deprecated):

```java
bp.getSubscriptionListingDetailsAsync(productId, new BillingProcessor.ISkuDetailsResponseListener() {
    @Override
    public void onSkuDetailsResponse(List<SkuDetails> products) {
        SkuDetails s = products.get(0);
        String price  = s.priceText;
        String period = s.subscriptionPeriod;
        // No access to multiple offers, base plans, or pricing phases.
    }
    @Override public void onSkuDetailsError(String error) { }
});
```

**After** (Billing 8 native):

```java
bp.getSubscriptionProductDetailsAsync(productId, new BillingProcessor.IProductDetailsResponseListener() {
    @Override
    public void onProductDetailsResponse(@NonNull List<ProductDetails> products) {
        ProductDetails pd = products.get(0);
        for (ProductDetails.SubscriptionOfferDetails offer : pd.getSubscriptionOfferDetails()) {
            // offer.getBasePlanId(), offer.getOfferId() (null = base plan),
            // offer.getPricingPhases().getPricingPhaseList(), offer.getOfferToken()
        }
    }
    @Override public void onProductDetailsError(@NonNull String error) { }
});
```

The matching `getPurchaseProductDetailsAsync` method exists for one-time
(INAPP) products — it exposes
`ProductDetails.getOneTimePurchaseOfferDetails()` instead of the
subscription offer tree.

#### Optional: skip the re-fetch when you already have `ProductDetails`

If you already hold a `ProductDetails` (for example, from rendering a
paywall) and want to launch the flow without another round-trip to Play,
there is now a `ProductDetails`-taking overload of `purchase`:

```java
bp.purchase(activity, productDetails);                         // new purchase
bp.purchase(activity, productDetails, oldProductId);           // sub upgrade/downgrade
```

This is strictly an optimization — the flat `bp.purchase(activity, productId)`
call above does the same thing and is preferred when you don't already
hold the details.

#### Behavioral notes

* `BillingClient.Builder.enableAutoServiceReconnection()` is now enabled
  and the library's previous manual retry on disconnect has been removed
  to avoid racing Google's internal reconnect. The manual retry on
  setup-failure / public-method paths stays, but is now deduped so
  overlapping retries can't stack. No action on your part.
* `BillingFlowParams.SubscriptionUpdateParams` for subscription updates
  still uses the implicit default replacement mode (`WITH_TIME_PRORATION`),
  matching pre-3.0 behavior — explicitly set a different mode if you need
  one.
* `BillingClient.BillingResponseCode` values are unchanged between Billing
  7 and 8.3, so `IBillingHandler.onBillingError(int, Throwable)` consumers
  don't need to change anything.
* Owned purchases are now re-queried from Google on **every** init, not
  only on the first-ever restore. This makes refunds eventually show up
  in `isPurchased()` without consumers needing to call
  `loadOwnedPurchasesFromGoogleAsync` themselves. `onPurchaseHistoryRestored`
  is still one-shot. If you need refund-accurate state *inside*
  `onBillingInitialized`, call `loadOwnedPurchasesFromGoogleAsync(listener)`
  explicitly and read `isPurchased` from the success callback — the
  reconciliation is async and `onBillingInitialized` fires before it
  completes.

#### New: observe pending (deferred-payment) purchases

`IBillingHandler` now has an optional `onPurchasePending(productId, details)`
callback that fires when Google reports a purchase in `PENDING` state
(deferred payment methods like cash-at-convenience-store, carrier
billing, slow card auth). This branch was previously missing, so those
purchases produced no callback on the first `onPurchasesUpdated` event.
The method is a Java 8 default, so existing `IBillingHandler`
implementations compile unchanged.

```java
@Override
public void onPurchasePending(@NonNull String productId, @Nullable PurchaseInfo details) {
    // Do NOT grant entitlement — the payment has not cleared yet.
    // Surface "payment pending" UI. The transition to PURCHASED fires
    // onProductPurchased on the next onPurchasesUpdated event or on
    // the next init (loadOwnedPurchasesFromGoogleAsync).
}
```

See [Handling pending transactions](https://developer.android.com/google/play/billing/integrate#pending).

### Upgrading from 2.0.x to 2.1.0

This release updates `com.android.billingclient:billing` library from version 4 to version 6.
While your apps will probably continue to work, you still need to follow steps from official
upgrade guide https://developer.android.com/google/play/billing/migrate-gpblv6 to make sure you're
ready for future library updates from Google

### Upgrading from 1.x to 2.0.0

Starting from Nov 1, 2021 Google will stop supporting v2 billing client library used as a dependency here.
This library was upgraded accordingly (thanks to @Equin and @showdpro), but this led to some major braking changes:

1. These methods were renamed:
    - `getSubscriptionTransactionDetails` renamed to `getSubscriptionPurchaseInfo`
    - `isValidTransactionDetails` renamed to `isValidPurchaseInfo`
1. Consume/purchase related methods to not accept `developerPayload` argument anymore (dropped support by Google). If you used it - you'll need to find the workaroud
1. Some synchronous methods were dropped in favour of their asynchronous versions (which have success/failure callback as their last argument):
    - use `consumePurchaseAsync` instead of `consumePurchase`
    - use `loadOwnedPurchasesFromGoogleAsync` instead of `loadOwnedPurchasesFromGoogle`
    - use `getPurchaseListingDetailsAsync` instead of `getPurchaseListingDetails`
    - use `getSubscriptionListingDetailsAsync` instead of `getSubscriptionListingDetails`
1. Deprecated `TransactionDetails` class has been removed. Please use `PurchaseInfo` instead, here is the property mapping:
    - TransactionDetails (productId) -> PurchaseInfo (purchaseData.productId)
    - TransactionDetails (orderId) -> PurchaseInfo (purchaseInfo.purchaseData.orderId)
    - TransactionDetails (purchaseToken) -> PurchaseInfo (purchaseInfo.purchaseData.purchaseToken)
    - TransactionDetails (purchaseTime) -> PurchaseInfo (purchaseInfo.purchaseData.purchaseTime)
1. `handleActivityResult` method was removed, you don't need to override your app's `onActivityResult` anymore
1. Some billing flow related constants were removed from `Constants` class (`BILLING_RESPONSE_*` constants). if your app relies on those - use `BillingClient.BillingResponseCode.*` constants instead  

### Upgrading to >= 1.0.44

The workaround below for the promo codes should no longer be valid. Promo codes should work just fine right out of the box

### Upgrading to >= 1.0.37

If you were supporting promo codes and faced troubled described in #156,
you will need to change your workaround code:

```java
errorCode == Constants.BILLING_ERROR_OTHER_ERROR && _billingProcessor.loadOwnedPurchasesFromGoogle() && _billingProcessor.isPurchased(SKU)
```

`errorCode` needs to be changed to `Constants.BILLING_ERROR_INVALID_DEVELOPER_PAYLOAD`
