/**
 * Copyright 2014 AnjLab
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anjlab.android.iab.v3;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ProductDetails;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Legacy product details type preserved for source compatibility with Billing Library 4/5/6/7
 * consumers of this library. New code should use the {@link ProductDetails} type from the Billing
 * Library 9 directly, via {@link BillingProcessor.IProductDetailsResponseListener}.
 *
 * <p>This class flattens multi-offer subscription data down to a single offer / single regular
 * pricing phase (see {@link #fromProductDetails(ProductDetails)}). That is lossy.
 *
 * @deprecated use {@link ProductDetails} and {@link BillingProcessor.IProductDetailsResponseListener}
 *     instead. Slated for removal in a future major version.
 */
@Deprecated
public class SkuDetails implements Parcelable
{

    public final String productId;

    public final String title;

    public final String description;

    public final boolean isSubscription;

    public final String currency;

    public final Double priceValue;

    public final String subscriptionPeriod;

    public final String subscriptionFreeTrialPeriod;

    public final boolean haveTrialPeriod;

    public final double introductoryPriceValue;

    public final String introductoryPricePeriod;

    public final boolean haveIntroductoryPeriod;

    public final int introductoryPriceCycles;

    /**
     * Use this value to return the raw price from the product.
     * This allows math to be performed without needing to worry about errors
     * caused by floating point representations of the product's price.
     * <p>
     * This is in micros from the Play Store.
     */
    public final long priceLong;

    public final String priceText;

    public final long introductoryPriceLong;

    public final String introductoryPriceText;

    public final String responseData;

    /**
     * Opaque offer token for Billing Library 9 subscription purchases. Populated by
     * {@link #fromProductDetails(ProductDetails)} for subscription products; null for one-time
     * products or instances constructed from legacy JSON. Package-private — read by
     * {@link BillingProcessor} when launching a purchase flow, not intended for consumers.
     */
    @Nullable String offerToken;

    public SkuDetails(JSONObject source) throws JSONException
    {
        String responseType = source.optString(Constants.RESPONSE_TYPE);
        if (responseType == null)
        {
            responseType = Constants.PRODUCT_TYPE_MANAGED;
        }
        productId = source.optString(Constants.RESPONSE_PRODUCT_ID);
        title = source.optString(Constants.RESPONSE_TITLE);
        description = source.optString(Constants.RESPONSE_DESCRIPTION);
        isSubscription = responseType.equalsIgnoreCase(Constants.PRODUCT_TYPE_SUBSCRIPTION);
        currency = source.optString(Constants.RESPONSE_PRICE_CURRENCY);
        priceLong = source.optLong(Constants.RESPONSE_PRICE_MICROS);
        priceValue = priceLong / 1000000d;
        priceText = source.optString(Constants.RESPONSE_PRICE);
        subscriptionPeriod = source.optString(Constants.RESPONSE_SUBSCRIPTION_PERIOD);
        subscriptionFreeTrialPeriod = source.optString(Constants.RESPONSE_FREE_TRIAL_PERIOD);
        haveTrialPeriod = !TextUtils.isEmpty(subscriptionFreeTrialPeriod);
        introductoryPriceLong = source.optLong(Constants.RESPONSE_INTRODUCTORY_PRICE_MICROS);
        introductoryPriceValue = introductoryPriceLong / 1000000d;
        introductoryPriceText = source.optString(Constants.RESPONSE_INTRODUCTORY_PRICE);
        introductoryPricePeriod = source.optString(Constants.RESPONSE_INTRODUCTORY_PRICE_PERIOD);
        haveIntroductoryPeriod = !TextUtils.isEmpty(introductoryPricePeriod);
        introductoryPriceCycles = source.optInt(Constants.RESPONSE_INTRODUCTORY_PRICE_CYCLES);
        responseData = source.toString();
    }

    /**
     * Translates a Billing Library 9 {@link ProductDetails} into the legacy {@link SkuDetails}
     * shape so existing consumers of this library's {@code ISkuDetailsResponseListener} surface
     * continue to work unchanged after the 9.x upgrade.
     *
     * <p>Translation rules:
     * <ul>
     *   <li>For one-time products (INAPP), fields are read from
     *       {@link ProductDetails#getOneTimePurchaseOfferDetails()}.</li>
     *   <li>For subscriptions (SUBS), {@code pickBestOffer} selects the offer — a free
     *       trial first, then a discounted introductory offer, then the base plan. Within the
     *       chosen offer the {@code INFINITE_RECURRING} pricing phase becomes the regular price;
     *       remaining phases are classified <em>by price</em>, so a zero-price phase becomes the
     *       free trial and any other becomes the introductory price, regardless of recurrence
     *       mode. If no {@code INFINITE_RECURRING} phase exists (e.g. installment plans) the last
     *       phase is used as the regular price. This collapses multi-offer subscriptions to one
     *       offer. Consumers needing the full offer tree should use {@link ProductDetails}
     *       directly.</li>
     * </ul>
     *
     * @param pd the Billing Library 9 product details to translate
     * @return a legacy {@link SkuDetails} wrapping the flattened data
     * @throws JSONException if the constructed legacy JSON is malformed (should not happen)
     */
    @NonNull
    public static SkuDetails fromProductDetails(@NonNull ProductDetails pd) throws JSONException
    {
        JSONObject json = new JSONObject();
        json.put(Constants.RESPONSE_PRODUCT_ID, pd.getProductId());
        json.put(Constants.RESPONSE_TITLE, pd.getTitle());
        json.put(Constants.RESPONSE_DESCRIPTION, pd.getDescription());
        json.put(Constants.RESPONSE_TYPE, pd.getProductType());

        String offerTokenLocal = null;

        if (BillingClient.ProductType.INAPP.equals(pd.getProductType()))
        {
            ProductDetails.OneTimePurchaseOfferDetails otp = pd.getOneTimePurchaseOfferDetails();
            if (otp != null)
            {
                json.put(Constants.RESPONSE_PRICE, otp.getFormattedPrice());
                json.put(Constants.RESPONSE_PRICE_MICROS, otp.getPriceAmountMicros());
                json.put(Constants.RESPONSE_PRICE_CURRENCY, otp.getPriceCurrencyCode());
            }
        }
        else if (BillingClient.ProductType.SUBS.equals(pd.getProductType()))
        {
            List<ProductDetails.SubscriptionOfferDetails> offers = pd.getSubscriptionOfferDetails();
            if (offers != null && !offers.isEmpty())
            {
                ProductDetails.SubscriptionOfferDetails offer = pickBestOffer(offers);
                offerTokenLocal = offer.getOfferToken();

                ProductDetails.PricingPhase regular = null;
                ProductDetails.PricingPhase intro = null;
                ProductDetails.PricingPhase trial = null;

                List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                for (ProductDetails.PricingPhase pp : phases)
                {
                    int mode = pp.getRecurrenceMode();
                    long micros = pp.getPriceAmountMicros();

                    if (mode == ProductDetails.RecurrenceMode.INFINITE_RECURRING)
                    {
                        regular = pp;
                    }
                    else if (micros == 0L)
                    {
                        // Free trial. Play reports these as NON_RECURRING for a plain
                        // one-off trial, but as FINITE_RECURRING with a cycle count of 1
                        // for some configurations. Classifying on the price instead of
                        // the recurrence mode covers both; keying on FINITE_RECURRING
                        // alone silently dropped every NON_RECURRING trial, leaving
                        // subscriptionFreeTrialPeriod empty and haveTrialPeriod false.
                        trial = pp;
                    }
                    else
                    {
                        // Paid introductory phase, either repeating (FINITE_RECURRING)
                        // or a single discounted payment (NON_RECURRING).
                        intro = pp;
                    }
                }
                // Fallback: if there's no INFINITE_RECURRING phase (e.g. installment plans),
                // treat the last phase as the regular price.
                if (regular == null && !phases.isEmpty())
                {
                    regular = phases.get(phases.size() - 1);
                }

                if (regular != null)
                {
                    json.put(Constants.RESPONSE_PRICE, regular.getFormattedPrice());
                    json.put(Constants.RESPONSE_PRICE_MICROS, regular.getPriceAmountMicros());
                    json.put(Constants.RESPONSE_PRICE_CURRENCY, regular.getPriceCurrencyCode());
                    json.put(Constants.RESPONSE_SUBSCRIPTION_PERIOD, regular.getBillingPeriod());
                }
                if (intro != null)
                {
                    json.put(Constants.RESPONSE_INTRODUCTORY_PRICE, intro.getFormattedPrice());
                    json.put(Constants.RESPONSE_INTRODUCTORY_PRICE_MICROS, intro.getPriceAmountMicros());
                    json.put(Constants.RESPONSE_INTRODUCTORY_PRICE_PERIOD, intro.getBillingPeriod());
                    json.put(Constants.RESPONSE_INTRODUCTORY_PRICE_CYCLES, intro.getBillingCycleCount());
                }
                if (trial != null)
                {
                    json.put(Constants.RESPONSE_FREE_TRIAL_PERIOD, trial.getBillingPeriod());
                }
            }
        }

        SkuDetails result = new SkuDetails(json);
        result.offerToken = offerTokenLocal;
        return result;
    }

    /**
     * Picks the offer to represent this subscription in the legacy
     * {@link SkuDetails} shape, and to launch the purchase flow with.
     *
     * <p>Preference order: an offer carrying a free trial, then one carrying a
     * discounted introductory phase, then the base plan, then whatever came
     * first.
     *
     * <p>Before Play Billing 5 a subscription's free trial and introductory
     * price were properties of the SKU itself, and Play applied whichever the
     * user was eligible for automatically. From Billing 5 on they live in
     * <em>separate offers</em>, identified by a non-null {@code offerId}, and
     * the caller has to pass that offer's token to receive them. Preferring the
     * base plan (a null {@code offerId}) therefore discarded every trial and
     * introductory offer: {@code haveTrialPeriod} came back false for users who
     * were in fact eligible, and purchases silently charged full price.
     * Selecting the best available offer restores the pre-3.0 behaviour.
     *
     * <p>Note this can only surface what Play actually returns.
     * {@code getSubscriptionOfferDetails()} is filtered by user eligibility, so
     * a user who has already consumed the trial gets the base plan alone and
     * the trial fields stay empty. That difference is inherent to Billing 5+
     * and cannot be translated away.
     */
    @NonNull
    static ProductDetails.SubscriptionOfferDetails pickBestOffer(
            @NonNull List<ProductDetails.SubscriptionOfferDetails> offers)
    {
        ProductDetails.SubscriptionOfferDetails withTrial = null;
        ProductDetails.SubscriptionOfferDetails withIntro = null;
        ProductDetails.SubscriptionOfferDetails basePlan = null;

        for (ProductDetails.SubscriptionOfferDetails offer : offers)
        {
            for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList())
            {
                if (phase.getPriceAmountMicros() == 0L)
                {
                    if (withTrial == null)
                    {
                        withTrial = offer;
                    }
                }
                else if (phase.getRecurrenceMode()
                        != ProductDetails.RecurrenceMode.INFINITE_RECURRING)
                {
                    if (withIntro == null)
                    {
                        withIntro = offer;
                    }
                }
            }

            if (basePlan == null && offer.getOfferId() == null)
            {
                basePlan = offer;
            }
        }

        if (withTrial != null)
        {
            return withTrial;
        }
        if (withIntro != null)
        {
            return withIntro;
        }
        if (basePlan != null)
        {
            return basePlan;
        }
        return offers.get(0);
    }

    @Override
    public String toString()
    {
        return String.format(Locale.US, "%s: %s(%s) %f in %s (%s)",
                productId,
                title,
                description,
                priceValue,
                currency,
                priceText);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        SkuDetails that = (SkuDetails) o;

        if (isSubscription != that.isSubscription)
        {
            return false;
        }
        return !(productId != null ? !productId.equals(that.productId) : that.productId != null);
    }

    @Override
    public int hashCode()
    {
        int result = productId != null ? productId.hashCode() : 0;
        result = 31 * result + (isSubscription ? 1 : 0);
        return result;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(this.productId);
        dest.writeString(this.title);
        dest.writeString(this.description);
        dest.writeByte(isSubscription ? (byte) 1 : (byte) 0);
        dest.writeString(this.currency);
        dest.writeDouble(this.priceValue);
        dest.writeLong(this.priceLong);
        dest.writeString(this.priceText);
        dest.writeString(this.subscriptionPeriod);
        dest.writeString(this.subscriptionFreeTrialPeriod);
        dest.writeByte(this.haveTrialPeriod ? (byte) 1 : (byte) 0);
        dest.writeDouble(this.introductoryPriceValue);
        dest.writeLong(this.introductoryPriceLong);
        dest.writeString(this.introductoryPriceText);
        dest.writeString(this.introductoryPricePeriod);
        dest.writeByte(this.haveIntroductoryPeriod ? (byte) 1 : (byte) 0);
        dest.writeInt(this.introductoryPriceCycles);
        dest.writeString(this.responseData);
        dest.writeString(this.offerToken);
    }

    protected SkuDetails(Parcel in)
    {
        this.productId = in.readString();
        this.title = in.readString();
        this.description = in.readString();
        this.isSubscription = in.readByte() != 0;
        this.currency = in.readString();
        this.priceValue = in.readDouble();
        this.priceLong = in.readLong();
        this.priceText = in.readString();
        this.subscriptionPeriod = in.readString();
        this.subscriptionFreeTrialPeriod = in.readString();
        this.haveTrialPeriod = in.readByte() != 0;
        this.introductoryPriceValue = in.readDouble();
        this.introductoryPriceLong = in.readLong();
        this.introductoryPriceText = in.readString();
        this.introductoryPricePeriod = in.readString();
        this.haveIntroductoryPeriod = in.readByte() != 0;
        this.introductoryPriceCycles = in.readInt();
        this.responseData = in.readString();
        // offerToken was appended to the Parcel wire format in library 3.0.0.
        // Guard the read so parcels produced by 2.x consumers (no trailing field)
        // unparcel cleanly - e.g. IPC from an older process or a cached parcel
        // bytestream that survives an in-place library upgrade.
        this.offerToken = in.dataAvail() > 0 ? in.readString() : null;
    }

    public static final Parcelable.Creator<SkuDetails> CREATOR =
            new Parcelable.Creator<SkuDetails>()
            {
                public SkuDetails createFromParcel(Parcel source)
                {
                    return new SkuDetails(source);
                }

                public SkuDetails[] newArray(int size)
                {
                    return new SkuDetails[size];
                }
            };
}
