package com.anjlab.android.iab.v3;

import android.os.Parcel;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class SkuDetailsParcelableTest
{
  @Test
  public void testInAppParcelable() throws Exception
  {
    String skuDetailsJson = "{\"productId\": \"test-id\",\"type\": \"inapp\",\"price\": \"€7.99\"," +
            "\"price_amount_micros\": \"7990000\",\"price_currency_code\": \"GBP\"," + "\"title\": \"Test Product\"," +
            "\"description\": \"A great product for testing.\"}";
    testParcelable(skuDetailsJson);
  }

  @Test
  public void testSubsParcelable() throws Exception
  {
    String skuDetailsJson = "{\"productId\": \"test-id\",\"type\": \"subs\",\"price\": \"€7.99\"," +
            "\"price_amount_micros\": \"7990000\",\"price_currency_code\": \"GBP\"," + "\"title\": \"Test Product\"," +
            "\"description\": \"A great product for testing.\"}";
    testParcelable(skuDetailsJson);
  }

  @Test
  public void testSubsWithIntroductoryPriceParcelable() throws Exception
  {
    String skuDetailsJson = "{\"productId\":\"test_id\",\"type\":\"subs\",\"price\":\"£45.00\",\"price_amount_micros\"" +
            ":45000000," + "\"price_currency_code\":\"GBP\",\"" + "introductoryPricePeriod\":\"P1Y\"," +
            "\"introductoryPrice\":" + "\"£22.50\",\"introductoryPriceCycles\":1,\"title\":\"Test title\"," +
            "\"description\":\"Test description\"}";
    testParcelable(skuDetailsJson);
  }

  private void testParcelable(String skuDetailsJson) throws JSONException
  {
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
    assertEquals(skuDetails.priceValue, result.priceValue);
    assertEquals(skuDetails.priceLong, result.priceLong);
    assertEquals(skuDetails.priceText, result.priceText);
    assertEquals(skuDetails.introductoryPrice, result.introductoryPrice);
    assertEquals(skuDetails.introductoryPricePeriod, result.introductoryPricePeriod);
    assertEquals(skuDetails.introductoryPriceCycles, result.introductoryPriceCycles);
  }
}
