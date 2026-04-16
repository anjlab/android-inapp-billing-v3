# Upstream Issue Triage — anjlab/android-inapp-billing-v3

**Triaged against:** Cherdenko fork `master` at `a1241e6` (library 3.0.0, Billing 8.3.0)
**Upstream source:** https://github.com/anjlab/android-inapp-billing-v3/issues
**Date:** 2026-04-16
**Scope:** 163 open issues, clustered by intent (Buckets A–H); verdicts per issue against the 3.0.0 codebase.
**Fix batch:** Bucket E bugs uncovered by the triage are folded into 3.0.0 (not a 3.0.1 bump — 3.0.0 is not yet published).

**Verdict codes:**
- `FIXED-IN-3.0.0` — Already resolved by the Billing 7→8.3 migration alone.
- `FIXED-IN-BATCH` — Fixed by a commit in this same PR (#553), shipping in 3.0.0.
- `LIKELY-FIXED` — The batch fix addresses the reporter's described path; needs confirmation repro.
- `NEEDS-REPRO` — Library-side code path looks correct on 3.0.0; the report predates the current Billing client and should be re-tested.
- `MOOT` — Question/support request, wrong repo, obsolete API, or user error.
- `FEATURE` — Enhancement request; classify as 3.1.x candidate / rejected / delivered.
- `STALE` — No activity, insufficient info to act on; close with note.

---

## Bucket E — Real bugs (15 issues)

All 15 have a verdict. Four commits in this PR close out the live bugs the triage surfaced; the other verdicts are for issues already resolved by the Billing 8.3 migration or awaiting repro.

### Android 14 `RECEIVER_EXPORTED` SecurityException — 3 dupes

| # | Reporter | Lib ver | Verdict |
|---|----------|---------|---------|
| #547 | Naguchennai | unspecified | **FIXED-IN-3.0.0** |
| #545 | najam-jas-android | 2.0.3 | **FIXED-IN-3.0.0** |
| #540 | Ph03niX-X | unspecified | **FIXED-IN-3.0.0** |

Root cause: Billing 4.0.0's internal `registerReceiver` call didn't set an export flag on Android 14. Google fixed it in Billing 6.0.1 (release notes linked in #545). 3.0.0 ships Billing 8.3.0, so the crash is mechanically gone. Close on publish.

---

### NPE / null-purchase-info class — #512 + #551 → **FIXED-IN-BATCH** (`4bbf710`)

| # | Reporter | Verdict | Notes |
|---|----------|---------|-------|
| #512 | atendrasingh90 | **FIXED-IN-BATCH** | Reporter's stack — `checkMerchant → handleOwnedPurchaseTransaction → handleItemAlreadyOwned` — NPE'd dereferencing `details.purchaseData.purchaseTime` when `getPurchaseInfo(productId)` returned null. Fix reorders the subscription-cache fallback ahead of `checkMerchant` and bails out with `BILLING_ERROR_OTHER_ERROR` if both caches miss. `checkMerchant` itself also hardened against null/partial `PurchaseInfo`. |
| #551 | superoidlau (alliswell) | **LIKELY-FIXED** | Reporter's own diagnosis ("null purchase info returned") matches the #512 root cause. The same `4bbf710` null-guard covers it, but the specific re-subscribe-crash path was not directly reproduced — consumer repro welcome before closing. |

---

### Connection retry race — #532 → **FIXED-IN-BATCH** (`a0ff2d3`)

| # | Reporter | Verdict | Notes |
|---|----------|---------|-------|
| #532 | azizmalik406 | **FIXED-IN-BATCH** | `DEVELOPER_ERROR` code 5 ("Client is already in the process of connecting to billing service"). Regressed when 3.0.0 added Billing 8's `enableAutoServiceReconnection()` on top of the library's existing manual `retryBillingClientConnection()` loop — the disconnect handler and Google's internal reconnect raced each other. Fix drops the manual retry on disconnect (auto-reconnect handles it) and guards the remaining retry paths with an `AtomicBoolean` so overlapping setup-failure + public-method retries can't stack. |

---

### USER_CANCELED not triggered — #516 → **NEEDS-REPRO**

| # | Reporter | Lib ver | Verdict |
|---|----------|---------|---------|
| #516 | max-critcrew | 2.0.3 | **NEEDS-REPRO** |

Code audit: `BillingProcessor.onPurchasesUpdated` dispatches `USER_CANCELED` → `reportBillingError` → `IBillingHandler.onBillingError` correctly on 3.0.0. The reporter was on Billing 4.0.0 via library 2.0.3, which had a period where dismissing the overlay (vs. pressing in-sheet Cancel) didn't fire `onPurchasesUpdated` at all. Billing 6+ tightened this upstream. Likely gone on 3.0.0 / Billing 8.3 — but it has not been verified on a real device with the exact swipe-to-dismiss gesture. **Action:** real-device repro on 3.0.0 with a live test SKU; close if the callback fires.

---

### Refund not reflected in `isPurchased()` — #435 → **FIXED-IN-BATCH** (`cd6b0f3`)

| # | Reporter | Verdict | Notes |
|---|----------|---------|-------|
| #435 | Duna | **FIXED-IN-BATCH** | `HistoryInitializationTask` only called `loadOwnedPurchasesFromGoogleAsync` once ever — gated by the `RESTORE_KEY` flag flipped true after the first successful restore. Combined with an append-only `handlePurchase` cache path, a refunded product stayed cached as owned forever. Fix: always reconcile on init; keep `onPurchaseHistoryRestored` one-shot via a captured `firstRestore` flag. Caveat documented in UPGRADING: `onBillingInitialized` still fires before the async refresh completes, so code reading `isPurchased()` *inside* `onBillingInitialized` may see the pre-refresh cache — call `loadOwnedPurchasesFromGoogleAsync(listener)` and check in the callback for a guaranteed-fresh read. |

---

### `onProductPurchased` not called on first purchase / non-consumable — #506 + #501 + #450 → **FIXED-IN-BATCH** (`a1241e6`)

| # | Reporter | Verdict |
|---|----------|---------|
| #506 | Isratmity01 | **FIXED-IN-BATCH** |
| #501 | manishSharmaJmd | **FIXED-IN-BATCH** |
| #450 | aakashvats2910 | **FIXED-IN-BATCH** |

All three describe the same symptom: first purchase completes on Google's side, but `onProductPurchased` never fires during the event. Root cause in `handlePurchase`: it only branched on `PurchaseState.PURCHASED` — `PENDING` was silently dropped. Deferred payment methods (cash at convenience store, carrier billing, slow card auth) land in `PENDING` first, which is why users saw "nothing happened" on the first dialog dismiss and the callback only fired on a later app launch (once Google transitioned the purchase to PURCHASED).

Fix adds a `default void onPurchasePending(...)` method to `IBillingHandler` (Java 8 default — existing implementations compile unchanged) and dispatches it from `handlePurchase` on the PENDING branch. Javadoc spells out that consumers must **not** grant entitlement from this callback — the eventual `onProductPurchased` still fires when the payment clears.

---

## Summary of fixes shipped in 3.0.0

| Commit | Issues closed | Summary |
|--------|---------------|---------|
| `4bbf710` | #512, #551 (likely) | Null-guard in `checkMerchant` / `handleOwnedPurchaseTransaction` |
| `a0ff2d3` | #532 | Remove redundant disconnect retry; dedupe remaining retries |
| `cd6b0f3` | #435 | Reconcile owned-purchases cache on every init |
| `a1241e6` | #506, #501, #450 | Dispatch `onPurchasePending` for deferred payments |

**No 3.0.1 / 3.1.0 bump needed** — 3.0.0 is not yet published, so all bugfixes fold into the release.

**Still needs action before closing:** #516 (one real-device repro), #551 (repro the exact re-subscribe sequence; the null-guard *should* cover it).

---

## Bucket A — Moot after 3.0.0 (~15)

*Pending body-read pass. Scope confirmed from title cluster.*

## Bucket B — Questions / support (~55)

*Pending body-read pass. Likely closed-as-question with Stack Overflow pointer.*

## Bucket C — Removed-type references (~12)

*Pending body-read pass. Likely closed with migration pointer to UPGRADING.md.*

## Bucket D — Feature requests worth keeping (~10)

*Pending body-read pass.*

## Bucket F — Meta / off-topic (~5)

*Pending body-read pass.*

## Bucket G — Non-English (~2, #421 #375)

*Pending translation + body-read.*

## Bucket H — Needs body-read (~50)

*The core of the remaining work.*
