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

import java.util.Date;

public class TransactionDetails {

    public final String productId;

    public final String orderId;

    public final String purchaseToken;

    public final Date purchaseTime;
	
	public final JSONObject rawJSONObject;

    public TransactionDetails(JSONObject source) throws JSONException {
		rawJSONObject = source;
        productId = source.getString(Constants.RESPONSE_PRODUCT_ID);
        orderId = source.getString(Constants.RESPONSE_ORDER_ID);
        purchaseToken = source.getString(Constants.RESPONSE_PURCHASE_TOKEN);
        purchaseTime = new Date(source.getInt(Constants.RESPONSE_PURCHASE_TIME));
    }

    @Override
    public String toString() {
        return String.format("%s purchased at %s(%s). Token: %s", productId, purchaseTime, orderId, purchaseToken);
    }
}
