package com.anjlab.android.iab.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ProductDetails;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * Tests the translation rules in {@link SkuDetails#fromProductDetails(ProductDetails)}:
 *   - Offer selection: prefer the base-plan offer (null {@code offerId}), fall back to the first.
 *   - Pricing-phase classification: {@code INFINITE_RECURRING} → regular price,
 *     {@code FINITE_RECURRING} with price 0 → free trial,
 *     {@code FINITE_RECURRING} with price > 0 → introductory price.
 *   - Fallback: no {@code INFINITE_RECURRING} phase → last phase becomes the regular price.
 *   - Offer token propagation: set for SUBS, null for INAPP.
 *
 * <p>Uses Robolectric so real {@code TextUtils} / {@code JSONObject} are available, and
 * Mockito 5 to stub the final Billing-client {@link ProductDetails} hierarchy.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SkuDetailsFromProductDetailsTest
{
    // ------------------------------------------------------------------
    // mock builders
    // ------------------------------------------------------------------

    private static ProductDetails.PricingPhase mockPhase(int recurrenceMode,
                                                         long priceMicros,
                                                         String formattedPrice,
                                                         String currency,
                                                         String billingPeriod,
                                                         int billingCycleCount)
    {
        ProductDetails.PricingPhase p = mock(ProductDetails.PricingPhase.class);
        when(p.getRecurrenceMode()).thenReturn(recurrenceMode);
        when(p.getPriceAmountMicros()).thenReturn(priceMicros);
        when(p.getFormattedPrice()).thenReturn(formattedPrice);
        when(p.getPriceCurrencyCode()).thenReturn(currency);
        when(p.getBillingPeriod()).thenReturn(billingPeriod);
        when(p.getBillingCycleCount()).thenReturn(billingCycleCount);
        return p;
    }

    private static ProductDetails.SubscriptionOfferDetails mockOffer(String offerId,
                                                                     String offerToken,
                                                                     ProductDetails.PricingPhase... phases)
    {
        ProductDetails.SubscriptionOfferDetails offer =
                mock(ProductDetails.SubscriptionOfferDetails.class);
        when(offer.getOfferId()).thenReturn(offerId);
        when(offer.getOfferToken()).thenReturn(offerToken);
        ProductDetails.PricingPhases pricingPhases = mock(ProductDetails.PricingPhases.class);
        when(pricingPhases.getPricingPhaseList()).thenReturn(Arrays.asList(phases));
        when(offer.getPricingPhases()).thenReturn(pricingPhases);
        return offer;
    }

    private static ProductDetails mockInappProduct(String id,
                                                   String title,
                                                   String description,
                                                   long priceMicros,
                                                   String formattedPrice,
                                                   String currency)
    {
        ProductDetails pd = mock(ProductDetails.class);
        when(pd.getProductId()).thenReturn(id);
        when(pd.getTitle()).thenReturn(title);
        when(pd.getDescription()).thenReturn(description);
        when(pd.getProductType()).thenReturn(BillingClient.ProductType.INAPP);
        ProductDetails.OneTimePurchaseOfferDetails otp =
                mock(ProductDetails.OneTimePurchaseOfferDetails.class);
        when(otp.getFormattedPrice()).thenReturn(formattedPrice);
        when(otp.getPriceAmountMicros()).thenReturn(priceMicros);
        when(otp.getPriceCurrencyCode()).thenReturn(currency);
        when(pd.getOneTimePurchaseOfferDetails()).thenReturn(otp);
        return pd;
    }

    private static ProductDetails mockSubsProduct(String id,
                                                  String title,
                                                  ProductDetails.SubscriptionOfferDetails... offers)
    {
        ProductDetails pd = mock(ProductDetails.class);
        when(pd.getProductId()).thenReturn(id);
        when(pd.getTitle()).thenReturn(title);
        when(pd.getDescription()).thenReturn("");
        when(pd.getProductType()).thenReturn(BillingClient.ProductType.SUBS);
        when(pd.getSubscriptionOfferDetails()).thenReturn(Arrays.asList(offers));
        return pd;
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    public void inappTranslatesOneTimeOffer() throws JSONException
    {
        ProductDetails pd = mockInappProduct(
                "coins_100", "100 Coins", "One hundred coins",
                990_000L, "$0.99", "USD");

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        assertEquals("coins_100", result.productId);
        assertEquals("100 Coins", result.title);
        assertEquals("One hundred coins", result.description);
        assertFalse(result.isSubscription);
        assertEquals("USD", result.currency);
        assertEquals("$0.99", result.priceText);
        assertEquals(990_000L, result.priceLong);
        assertEquals(0.99d, result.priceValue, 0.0001d);
        assertNull("INAPP must not set offerToken", result.offerToken);
    }

    @Test
    public void subsWithSingleInfinitePhase_classifiesAsRegular() throws JSONException
    {
        ProductDetails.PricingPhase monthly = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                4_990_000L, "$4.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails basePlan = mockOffer(null, "token-base", monthly);
        ProductDetails pd = mockSubsProduct("premium_monthly", "Premium", basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        assertTrue(result.isSubscription);
        assertEquals("$4.99", result.priceText);
        assertEquals(4_990_000L, result.priceLong);
        assertEquals("USD", result.currency);
        assertEquals("P1M", result.subscriptionPeriod);
        assertEquals("token-base", result.offerToken);
        // No trial, no intro.
        assertTrue(result.subscriptionFreeTrialPeriod == null
                || result.subscriptionFreeTrialPeriod.isEmpty());
        assertEquals(0L, result.introductoryPriceLong);
    }

    @Test
    public void subsWithFreeTrial_classifiesTrialAndRegular() throws JSONException
    {
        ProductDetails.PricingPhase trial = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                0L, "Free", "USD", "P7D", 1);
        ProductDetails.PricingPhase monthly = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                4_990_000L, "$4.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails basePlan = mockOffer(null, "token", trial, monthly);
        ProductDetails pd = mockSubsProduct("premium", "Premium", basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        assertEquals("P7D", result.subscriptionFreeTrialPeriod);
        assertTrue("trial must set haveTrialPeriod", result.haveTrialPeriod);
        assertEquals("P1M", result.subscriptionPeriod);
        assertEquals(4_990_000L, result.priceLong);
        assertEquals(0L, result.introductoryPriceLong);
    }

    @Test
    public void subsWithIntroPrice_classifiesIntroAndRegular() throws JSONException
    {
        ProductDetails.PricingPhase intro = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                990_000L, "$0.99", "USD", "P1M", 3);
        ProductDetails.PricingPhase monthly = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                4_990_000L, "$4.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails basePlan = mockOffer(null, "token", intro, monthly);
        ProductDetails pd = mockSubsProduct("premium", "Premium", basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        assertEquals("$0.99", result.introductoryPriceText);
        assertEquals(990_000L, result.introductoryPriceLong);
        assertEquals("P1M", result.introductoryPricePeriod);
        assertEquals(3, result.introductoryPriceCycles);
        assertTrue(result.haveIntroductoryPeriod);
        // Regular is still $4.99
        assertEquals(4_990_000L, result.priceLong);
        assertEquals("$4.99", result.priceText);
    }

    @Test
    public void subsWithTrialAndIntroAndRegular_classifiesAllThree() throws JSONException
    {
        ProductDetails.PricingPhase trial = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                0L, "Free", "USD", "P7D", 1);
        ProductDetails.PricingPhase intro = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                990_000L, "$0.99", "USD", "P1M", 3);
        ProductDetails.PricingPhase monthly = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                4_990_000L, "$4.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails basePlan =
                mockOffer(null, "token", trial, intro, monthly);
        ProductDetails pd = mockSubsProduct("premium", "Premium", basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        assertEquals("P7D", result.subscriptionFreeTrialPeriod);
        assertTrue(result.haveTrialPeriod);
        assertEquals(990_000L, result.introductoryPriceLong);
        assertEquals(3, result.introductoryPriceCycles);
        assertTrue(result.haveIntroductoryPeriod);
        assertEquals(4_990_000L, result.priceLong);
    }

    @Test
    public void subsMultipleOffers_prefersBasePlan() throws JSONException
    {
        ProductDetails.PricingPhase promoPhase = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                2_990_000L, "$2.99", "USD", "P1M", 0);
        ProductDetails.PricingPhase basePhase = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                4_990_000L, "$4.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails promoOffer =
                mockOffer("promo1", "token-promo", promoPhase);
        ProductDetails.SubscriptionOfferDetails basePlan =
                mockOffer(null, "token-base", basePhase);
        ProductDetails pd = mockSubsProduct("premium", "Premium", promoOffer, basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        // Base plan wins even though it's second in the list.
        assertEquals("token-base", result.offerToken);
        assertEquals(4_990_000L, result.priceLong);
    }

    @Test
    public void subsNoBasePlan_fallsBackToFirstOffer() throws JSONException
    {
        ProductDetails.PricingPhase phase1 = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                1_990_000L, "$1.99", "USD", "P1M", 0);
        ProductDetails.PricingPhase phase2 = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                2_990_000L, "$2.99", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails promo1 =
                mockOffer("promo1", "token-promo1", phase1);
        ProductDetails.SubscriptionOfferDetails promo2 =
                mockOffer("promo2", "token-promo2", phase2);
        ProductDetails pd = mockSubsProduct("premium", "Premium", promo1, promo2);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        // First offer wins when no offer is the base plan.
        assertEquals("token-promo1", result.offerToken);
        assertEquals(1_990_000L, result.priceLong);
    }

    @Test
    public void subsNoInfinitePhase_usesLastPhaseAsRegular() throws JSONException
    {
        // Installment-plan style: only FINITE phases.
        ProductDetails.PricingPhase first = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                10_000_000L, "$10.00", "USD", "P1M", 3);
        ProductDetails.PricingPhase last = mockPhase(
                ProductDetails.RecurrenceMode.FINITE_RECURRING,
                20_000_000L, "$20.00", "USD", "P1M", 6);
        ProductDetails.SubscriptionOfferDetails basePlan = mockOffer(null, "token", first, last);
        ProductDetails pd = mockSubsProduct("installment", "Installment Plan", basePlan);

        SkuDetails result = SkuDetails.fromProductDetails(pd);

        // Last phase becomes the regular price by fallback when no INFINITE_RECURRING exists.
        assertEquals(20_000_000L, result.priceLong);
        assertEquals("$20.00", result.priceText);
    }

    @Test
    public void offerTokenPropagatesForSubsNotInapp() throws JSONException
    {
        ProductDetails.PricingPhase phase = mockPhase(
                ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                1_000_000L, "$1.00", "USD", "P1M", 0);
        ProductDetails.SubscriptionOfferDetails basePlan = mockOffer(null, "subs-token", phase);
        ProductDetails subs = mockSubsProduct("sub1", "Subscription", basePlan);
        ProductDetails inapp = mockInappProduct(
                "coin1", "Coin", "One coin", 1_000_000L, "$1.00", "USD");

        SkuDetails subsResult = SkuDetails.fromProductDetails(subs);
        SkuDetails inappResult = SkuDetails.fromProductDetails(inapp);

        assertNotNull(subsResult);
        assertNotNull(inappResult);
        assertEquals("subs-token", subsResult.offerToken);
        assertNull(inappResult.offerToken);
    }
}
