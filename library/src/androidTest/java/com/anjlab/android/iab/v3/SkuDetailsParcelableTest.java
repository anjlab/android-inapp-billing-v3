package com.anjlab.android.iab.v3;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class SkuDetailsParcelableTest
{
    @Test
    public void testParcelable() throws Exception
    {
        String skuDetailsJson =
                "{\"productId\": \"test-id\",\"type\": \"inapp\",\"price\": \"€7.99\","+
                "\"price_amount_micros\": \"7990000\",\"price_currency_code\": \"GBP\","+
                "\"title\": \"Test Product\",\"description\": \"A great product for testing.\"}";
        JSONObject details = new JSONObject(skuDetailsJson);
        SkuDetails skuDetails = new SkuDetails(details);

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
    }
}
