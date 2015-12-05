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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class TransactionDetails implements Parcelable {

	public final String productId;

	public final String orderId;

	public final String purchaseToken;

	public final Date purchaseTime;

	public final PurchaseInfo purchaseInfo;

	public TransactionDetails(PurchaseInfo info) throws JSONException {
		JSONObject source = new JSONObject(info.responseData);
		purchaseInfo = info;
		productId = source.getString(Constants.RESPONSE_PRODUCT_ID);
		orderId = source.optString(Constants.RESPONSE_ORDER_ID);
		purchaseToken = source.getString(Constants.RESPONSE_PURCHASE_TOKEN);
		purchaseTime = new Date(source.getLong(Constants.RESPONSE_PURCHASE_TIME));
	}

	@Override
	public String toString() {
		return String.format("%s purchased at %s(%s). Token: %s, Signature: %s", productId, purchaseTime, orderId, purchaseToken, purchaseInfo.signature);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TransactionDetails details = (TransactionDetails) o;

		return !(orderId != null ? !orderId.equals(details.orderId) : details.orderId != null);

	}

	@Override
	public int hashCode() {
		return orderId != null ? orderId.hashCode() : 0;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.productId);
		dest.writeString(this.orderId);
		dest.writeString(this.purchaseToken);
		dest.writeLong(purchaseTime != null ? purchaseTime.getTime() : -1);
		dest.writeParcelable(this.purchaseInfo, flags);
	}

	protected TransactionDetails(Parcel in) {
		this.productId = in.readString();
		this.orderId = in.readString();
		this.purchaseToken = in.readString();
		long tmpPurchaseTime = in.readLong();
		this.purchaseTime = tmpPurchaseTime == -1 ? null : new Date(tmpPurchaseTime);
		this.purchaseInfo = in.readParcelable(PurchaseInfo.class.getClassLoader());
	}

	public static final Parcelable.Creator<TransactionDetails> CREATOR = new Parcelable.Creator<TransactionDetails>() {
		public TransactionDetails createFromParcel(Parcel source) {
			return new TransactionDetails(source);
		}

		public TransactionDetails[] newArray(int size) {
			return new TransactionDetails[size];
		}
	};
}
