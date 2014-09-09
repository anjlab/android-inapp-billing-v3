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
