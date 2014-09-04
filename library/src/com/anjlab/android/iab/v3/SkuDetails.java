package com.anjlab.android.iab.v3;

import org.json.JSONException;
import org.json.JSONObject;

public class SkuDetails {

    public final String productId;

    public final String title;

    public final String description;

    public final boolean isSubscription;

    public final String currency;

    public final Double priceValue;

    public final String priceText;

    public SkuDetails(JSONObject source) throws JSONException {
        String responseType = source.getString(Constants.RESPONSE_TYPE);
        if (responseType == null)
            responseType = Constants.PRODUCT_TYPE_MANAGED;
        productId = source.getString(Constants.RESPONSE_PRODUCT_ID);
        title = source.getString(Constants.RESPONSE_TITLE);
        description = source.getString(Constants.RESPONSE_DESCRIPTION);
        isSubscription = responseType.equalsIgnoreCase(Constants.PRODUCT_TYPE_SUBSCRIPTION);
        currency = source.getString(Constants.RESPONSE_PRICE_CURRENCY);
        priceValue = source.getDouble(Constants.RESPONSE_PRICE_MICROS) / 1000000;
        priceText = source.getString(Constants.RESPONSE_PRICE);
    }

    @Override
    public String toString() {
        return String.format("%s: %s(%s) %f in %s (%s)", productId, title, description, priceValue, currency, priceText);
    }
}
