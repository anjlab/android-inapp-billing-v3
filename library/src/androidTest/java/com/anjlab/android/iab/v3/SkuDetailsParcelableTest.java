package com.anjlab.android.iab.v3;

import android.os.Parcel;

import com.anjlab.android.iab.v3.util.ResourcesUtil;

import org.json.JSONObject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class SkuDetailsParcelableTest
{
    @Test
    public void testParcelableInApp() throws Exception
    {
        testParcelable(loadSkuDetails("sku_in_app.json"));
    }

    @Test
    public void testParcelableSubscription() throws Exception
    {
        testParcelable(loadSkuDetails("sku_subscription.json"));
    }

    @Test
    public void testParcelableSubscriptionIntroductory() throws Exception
    {
        testParcelable(loadSkuDetails("sku_subscription_introductory.json"));
    }

    @Test
    public void testParcelableSubscriptionTrial() throws Exception
    {
        testParcelable(loadSkuDetails("sku_subscription_trial.json"));
    }

    private SkuDetails loadSkuDetails(String jsonFilePath) throws Exception
    {
        JSONObject details = new JSONObject(ResourcesUtil.loadFile(jsonFilePath));
        return new SkuDetails(details);
    }

    private void testParcelable(SkuDetails skuDetails) throws Exception
    {
        Parcel parcel = Parcel.obtain();

        skuDetails.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        SkuDetails result = SkuDetails.CREATOR.createFromParcel(parcel);

        assertEquals(skuDetails.productId, result.productId);
        assertEquals(skuDetails.priceLong, result.priceLong);
        assertEquals(skuDetails.priceText, result.priceText);
        assertEquals(skuDetails.priceValue, result.priceValue);
        assertEquals(skuDetails.description, result.description);
        assertEquals(skuDetails.isSubscription, result.isSubscription);
        assertEquals(skuDetails.currency, result.currency);
        assertEquals(skuDetails.title, result.title);
        assertEquals(skuDetails.subscriptionPeriod, result.subscriptionPeriod);
        assertEquals(skuDetails.subscriptionFreeTrialPeriod, result.subscriptionFreeTrialPeriod);
        assertEquals(skuDetails.introductoryPriceValue, result.introductoryPriceValue);
        assertEquals(skuDetails.introductoryPricePeriod, result.introductoryPricePeriod);
        assertEquals(skuDetails.introductoryPriceCycles, result.introductoryPriceCycles);
        assertEquals(skuDetails.introductoryPriceLong, result.introductoryPriceLong);
        assertEquals(skuDetails.introductoryPriceText, result.introductoryPriceText);
    }
}
