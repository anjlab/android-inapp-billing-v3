## 3.0.0 (2026-04-15)

#### Breaking Changes

* **`minSdkVersion` raised 21 → 23.** Google Play Billing Library 8.1+
  dropped support for API 21–22. Consumers shipping to those levels must
  pin this library at `2.2.0` or raise their own `minSdkVersion`.
* Targets `com.android.billingclient:billing:8.3.0` (up from `7.0.0`).

#### Features

* New `ProductDetails`-based public API exposing the full Billing Library 8
  product surface (subscription offer trees, base plans, pricing phases,
  multiple promotional offers):
    - `IProductDetailsResponseListener` returning `List<ProductDetails>`
    - `getPurchaseProductDetailsAsync(String, …)` and `(List<String>, …)`
    - `getSubscriptionProductDetailsAsync(String, …)` and `(List<String>, …)`
    - `purchase(Activity, ProductDetails)` and
      `purchase(Activity, ProductDetails, String oldProductId)` overloads
      that skip the extra product-lookup round-trip when the caller already
      holds the details.
* `BillingClient.Builder.enableAutoServiceReconnection()` is now enabled on
  the internal billing client alongside the library's existing manual
  reconnect loop.
* See [UPGRADING.md](UPGRADING.md#upgrading-from-22-to-30) for a migration
  walkthrough.

#### Deprecations

* The legacy `com.anjlab.android.iab.v3.SkuDetails` type and everything
  that returns or consumes it is now `@Deprecated`. These keep working via
  a translator that flattens Billing 8 `ProductDetails` into the legacy
  JSON shape, but the translation is **lossy for multi-offer subscriptions**
  — only the base plan is surfaced. Affected:
    - `com.anjlab.android.iab.v3.SkuDetails`
    - `BillingProcessor.ISkuDetailsResponseListener`
    - `getPurchaseListingDetailsAsync(…)` (both overloads)
    - `getSubscriptionListingDetailsAsync(String, …)`
    - `getSubscriptionsListingDetailsAsync(ArrayList<String>, …)`

#### Bug Fixes

* Fix `NullPointerException` in `checkMerchant` when Google returns
  `ITEM_ALREADY_OWNED` but neither local cache has a `PurchaseInfo`
  record ([#512](https://github.com/anjlab/android-inapp-billing-v3/issues/512),
  [#551](https://github.com/anjlab/android-inapp-billing-v3/issues/551)).
  The subscription-cache fallback now runs ahead of the merchant check
  and both caches missing yields a `BILLING_ERROR_OTHER_ERROR` report
  instead of a crash.
* Stop racing Billing 8's new `enableAutoServiceReconnection()` on
  disconnect ([#532](https://github.com/anjlab/android-inapp-billing-v3/issues/532)).
  The manual retry posted from `onBillingServiceDisconnected` collided
  with Google's internal reconnect and produced `DEVELOPER_ERROR`
  ("Client is already in the process of connecting"). The manual retry
  has been removed from the disconnect path and the remaining retry
  sites are now deduped via an `AtomicBoolean`.
* Reconcile the owned-purchases cache on every init so refunds
  eventually clear ([#435](https://github.com/anjlab/android-inapp-billing-v3/issues/435)).
  Previously `loadOwnedPurchasesFromGoogleAsync` only ran on the
  first-ever restore, so a refunded product stayed cached as owned
  indefinitely. `onPurchaseHistoryRestored` is still one-shot.
* Add `IBillingHandler.onPurchasePending(productId, details)` and
  dispatch it from `handlePurchase` when Google reports a purchase in
  `PENDING` state
  ([#506](https://github.com/anjlab/android-inapp-billing-v3/issues/506),
  [#501](https://github.com/anjlab/android-inapp-billing-v3/issues/501),
  [#450](https://github.com/anjlab/android-inapp-billing-v3/issues/450)).
  Previously the `PENDING` branch was missing, so deferred payment
  methods (cash at convenience store, carrier billing, slow card auth)
  silently produced no callback on the first `onPurchasesUpdated` event.
  The method is a Java 8 default — existing implementations compile
  unchanged.

#### Internal

* Migrated all internal call sites from `SkuDetails` / `SkuDetailsParams` /
  `querySkuDetailsAsync` to `ProductDetails` / `QueryProductDetailsParams` /
  `queryProductDetailsAsync` (+ the new `QueryProductDetailsResult`
  callback signature).
* Purchase flow now builds `BillingFlowParams.ProductDetailsParams` with
  `setOfferToken()` for subscriptions, preferring the base plan (null
  `offerId`) with fallback to the first offer.
* `enablePendingPurchases()` replaced with
  `enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
  (required in 8.x).
* `queryPurchasesAsync(String, …)` replaced with
  `queryPurchasesAsync(QueryPurchasesParams, …)`.

#### Build

* Gradle `7.5` → `9.0.0`
* Android Gradle Plugin `7.4.2` → `8.11.1`
* `buildToolsVersion` `30.0.3` → `34.0.0`
* Billing dependency is now `api`, not `implementation`, because the new
  public API exposes billing-client types in its signatures.
* Removed the Gradle-9-incompatible `hierynomus` license plugin.
* Removed the deprecated `package=` attribute from the library
  `AndroidManifest.xml`; added `namespace 'com.anjlab.android.iab.v3'` in
  `library/build.gradle`.
* Added `android { publishing { singleVariant('release') {} } }` for AGP
  8.x maven-publish.
* `android.useAndroidX=true` / `android.enableJetifier=true` moved from
  `gradle-wrapper.properties` (wrong file) to `gradle.properties`.

---

## 1.0.44 (8/7/2017)

#### Features

* [#295](https://github.com/anjlab/android-inapp-billing-v3/pull/295):  Address a bug with a developer payload check for the promo codes - [@serggl](https://github.com/serggl).
* [#293](https://github.com/anjlab/android-inapp-billing-v3/pull/293):  Nullability and javadocs - [@AllanWang](https://github.com/AllanWang).
* [#289](https://github.com/anjlab/android-inapp-billing-v3/pull/289):  Add proguard rule - [@AllanWang](https://github.com/AllanWang).

## 1.0.43 (7/24/2017)

#### Features

* [#287](https://github.com/anjlab/android-inapp-billing-v3/pull/287):  Support for getBuyIntentExtraParams() - [@ratm](https://github.com/ratm).

## 1.0.42 (7/7/2017)

#### Bug Fixes

* [#286](https://github.com/anjlab/android-inapp-billing-v3/pull/286):  Removed Joda Time dependency introduced in 1.0.41 - [@moni890185](https://github.com/moni890185).

## 1.0.41 (7/2/2017)

#### Features

* [#281](https://github.com/anjlab/android-inapp-billing-v3/pull/281):  Support for introductory price on subscriptions - [@landarskiy](https://github.com/landarskiy).

## 1.0.40 (6/3/2017)

#### Features

* [#273](https://github.com/anjlab/android-inapp-billing-v3/pull/273):  Added ability to include developer payload in updateSubscription() methods - [@autonomousapps](https://github.com/autonomousapps).

#### Refactor

* [#271](https://github.com/anjlab/android-inapp-billing-v3/pull/271):  Converted single-element arraylist into singleton list - [@autonomousapps](https://github.com/autonomousapps).

## 1.0.39 (4/3/2017)

#### Features

* [#252](https://github.com/anjlab/android-inapp-billing-v3/pull/252):  Created new factory constructors that allow for late-init of play services - [@autonomousapps](https://github.com/autonomousapps).

## 1.0.38 (1/1/2017)

#### Bug Fixes

* [#224](https://github.com/anjlab/android-inapp-billing-v3/pull/224):  Minor type for the function isOneTimePurchaseSupported() - [@omerfarukyilmaz](https://github.com/omerfarukyilmaz).

## 1.0.37 (12/24/2016)

#### Features

* [#223](https://github.com/anjlab/android-inapp-billing-v3/pull/223): additional service availability checker - [@MedetZhakupov](https://github.com/MedetZhakupov).

#### Docs
* [#220](https://github.com/anjlab/android-inapp-billing-v3/pull/220): document some promo codes usage nuances - [@serggl](https://github.com/serggl).

## 1.0.36 (11/22/2016)

#### Code Cleanup

* [deprecate PurchaseInfo.parseResponseData](https://github.com/anjlab/android-inapp-billing-v3/commit/d0d5492df200a3e7d324d7dacf8d364428554449) - [@serggl](https://github.com/serggl).

## 1.0.35 (11/22/2016)

#### Bug Fixes

* [#210](https://github.com/anjlab/android-inapp-billing-v3/issues/210):  address null pointer issue in isIabServiceAvailable - [@serggl](https://github.com/serggl).