## Upgrading Android In-App Billing v3 Library

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
