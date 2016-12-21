## Upgrading Android In-App Billing v3 Library

### Upgrading to >= 1.0.37

If you were supporting promo codes and faced troubled described in #156,
you will need to change your workaround code:

```java
errorCode == Constants.BILLING_ERROR_OTHER_ERROR && _billingProcessor.loadOwnedPurchasesFromGoogle() && _billingProcessor.isPurchased(SKU)
```

`errorCode` needs to be changed to `Constants.BILLING_ERROR_INVALID_DEVELOPER_PAYLOAD`
