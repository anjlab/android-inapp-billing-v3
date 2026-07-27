## 3.0.0 (2026-07-27)

#### Breaking Changes

* **`minSdkVersion` raised 21 → 23.** Google Play Billing Library 8.1+
  dropped support for API 21–22. Consumers shipping to those levels must
  pin this library at `2.2.0` or raise their own `minSdkVersion`.
* **Consumers must now build against `compileSdk 35+`** (up from `34`).
  Play Billing 9.x depends on `androidx.core:1.15.0`, which declares
  `minCompileSdk=35`.
* Targets `com.android.billingclient:billing:9.1.0` (up from `7.0.0`).
* Play Billing 9.0 reclassified a system-blocked Play Store (e.g. OEM kids
  mode) from `ERROR` to `BILLING_UNAVAILABLE`. The library passes billing
  response codes through unchanged, so handlers branching on the old code
  for that scenario need updating.
* **Free trial and introductory price fields can now legitimately be empty.**
  From Play Billing 5 on, trials and introductory prices are separate offers
  and `getSubscriptionOfferDetails()` only returns what the signed-in user is
  eligible for. `subscriptionFreeTrialPeriod` / `introductoryPrice*` therefore
  come back empty for users who already consumed the trial, where the pre-3.0
  API reported the SKU's configured values regardless. Callers that feed these
  straight into a parser will crash for those users — see
  [UPGRADING.md](UPGRADING.md#upgrading-from-22-to-30).

#### Features

* New `ProductDetails`-based public API exposing the full Billing Library 9
  product surface (subscription offer trees, base plans, pricing phases,
  multiple promotional offers):
    - `IProductDetailsResponseListener` returning `List<ProductDetails>`
    - `getPurchaseProductDetailsAsync(String, …)` and `(List<String>, …)`
    - `getSubscriptionProductDetailsAsync(String, …)` and `(List<String>, …)`
    - `purchase(Activity, ProductDetails)` and
      `purchase(Activity, ProductDetails, String oldProductId)` overloads
      that skip the extra product-lookup round-trip when the caller already
      holds the details.
* Expose Play's obfuscated identifiers on the purchase flow
  ([#509](https://github.com/anjlab/android-inapp-billing-v3/issues/509)).
  New overloads taking `obfuscatedAccountId` / `obfuscatedProfileId` let you
  correlate a purchase with your own account records without sending Google
  personal data:
    - `purchase(Activity, String, String, String)`
    - `subscribe(Activity, String, String, String)`
    - `updateSubscription(Activity, String, String, String, String)`
    - `purchase(Activity, ProductDetails, String, String, String)`
* New `PurchaseState.Pending` constant for deferred payments awaiting
  completion. It is appended last in the enum so the existing ordinals — which
  are the Parcel wire format for `PurchaseData` — stay stable. `switch`
  statements over `PurchaseState` are no longer exhaustive and need a new
  branch.
* `BillingClient.Builder.enableAutoServiceReconnection()` is now enabled on
  the internal billing client alongside the library's existing manual
  reconnect loop.
* See [UPGRADING.md](UPGRADING.md#upgrading-from-22-to-30) for a migration
  walkthrough.

#### Deprecations

* The legacy `com.anjlab.android.iab.v3.SkuDetails` type and everything
  that returns or consumes it is now `@Deprecated`. These keep working via
  a translator that flattens Billing 9 `ProductDetails` into the legacy
  JSON shape, but the translation is **lossy for multi-offer subscriptions**
  — only one offer is surfaced (the best the user is eligible for: a trial,
  then an introductory offer, then the base plan), and only one regular
  pricing phase. Affected:
    - `com.anjlab.android.iab.v3.SkuDetails`
    - `BillingProcessor.ISkuDetailsResponseListener`
    - `getPurchaseListingDetailsAsync(…)` (both overloads)
    - `getSubscriptionListingDetailsAsync(String, …)`
    - `getSubscriptionsListingDetailsAsync(ArrayList<String>, …)`

#### Bug Fixes

* Report the owned-purchases cache even when the billing client connects on
  its own. `enableAutoServiceReconnection()` can bring the connection up
  without `BillingClientStateListener` ever firing, and
  `HistoryInitializationTask` hung off that callback exclusively. When it
  didn't run, `isPurchased()` stayed `false` and `onBillingInitialized()`
  never fired, so callers pushed users into a fresh purchase for a product
  they already owned (answered with `ITEM_ALREADY_OWNED`). `initialize()` now
  schedules the task itself when the client is already ready.
* Stop discarding free trial and introductory offers. Offer selection
  preferred the base plan (null `offerId`), but from Billing 5 on trials and
  introductory prices live in *separate* offers with a non-null `offerId`.
  Eligible users were reported as having no trial and were charged full price
  on purchase. Selection now prefers a trial, then an introductory offer, then
  the base plan, consistently in both `SkuDetails` translation and the
  purchase flow.
* Classify pricing phases by price rather than recurrence mode. A plain
  one-off free trial is `NON_RECURRING`, which the previous `FINITE_RECURRING`
  check dropped entirely, as it did single-payment introductory phases.
* Map a raw `purchaseState` of `4` to `PurchaseState.Pending` instead of
  indexing `PurchaseState.values()` directly, which threw
  `ArrayIndexOutOfBoundsException` on any value outside the enum. Unknown
  values now degrade to `PurchasedSuccessfully`, and an absent field keeps its
  historical `Canceled` default.
* Fail loudly when the legacy `SkuDetails` translation cannot parse a product.
  A `JSONException` now fires `onSkuDetailsError` instead of silently
  delivering a partial list that omitted the offending product.
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
  `setOfferToken()` for subscriptions, using the same `pickBestOffer`
  selection as the `SkuDetails` translation (trial, then introductory
  offer, then base plan) so the offer displayed is the offer charged.
* `enablePendingPurchases()` replaced with
  `enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
  (required in 8.x).
* `queryPurchasesAsync(String, …)` replaced with
  `queryPurchasesAsync(QueryPurchasesParams, …)`.
* `SkuDetails` carries the selected offer's `offerToken`, appended to its
  Parcel wire format. Reads are guarded by `dataAvail()` so a 2.x-written
  parcel still deserializes, but a 3.0-written parcel read by 2.x will not.

#### Build

* Gradle `7.5` → `9.0.0`
* Android Gradle Plugin `7.4.2` → `8.11.1`
* `compileSdk` / `targetSdk` `34` → `35` (required by Play Billing 9.x via
  `androidx.core:1.15.0`)
* `buildToolsVersion` `30.0.3` → `35.0.0`
* Added `kotlin-stdlib-jdk7`/`-jdk8` `1.8.22` dependency constraints,
  exported as `api` so consumers inherit them. Play Billing 9.x pulls
  `kotlin-stdlib:1.8.22` (via `androidx.core:1.15.0`) alongside
  `kotlinx-coroutines:1.6.4`, which still requests the `1.6.21` jdk7/jdk8
  artifacts; since Kotlin 1.8 folded those into `kotlin-stdlib`, the stale
  copies collide and fail `checkDebugDuplicateClasses`.
* Billing dependency is now `api`, not `implementation`, because the new
  public API exposes billing-client types in its signatures.
* Removed the deprecated `package=` attribute from the library
  `AndroidManifest.xml`; added `namespace 'com.anjlab.android.iab.v3'` in
  `library/build.gradle`.
* Added `android { publishing { singleVariant('release') {} } }` for AGP
  8.x maven-publish.
* `android.useAndroidX=true` / `android.enableJetifier` moved from
  `gradle-wrapper.properties` (wrong file) to `gradle.properties`.
  Jetifier is set to `false`: it is unnecessary for this dependency set and
  OOM'd the CI runner while transforming Robolectric's native runtime.

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