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

import java.util.Date;
import java.util.Locale;

public class TransactionDetails implements Parcelable
{

	/**
	 * @deprecated use {@see purchaseInfo.purchaseData.productId}} instead.
	 */
	@Deprecated
	public final String productId;

	/**
	 * @deprecated use {@see purchaseInfo.purchaseData.orderId}} instead.
	 */
	@Deprecated
	public final String orderId;

	/**
	 * @deprecated use {@see purchaseInfo.purchaseData.purchaseToken}} instead.
	 */
	@Deprecated
	public final String purchaseToken;

	/**
	 * @deprecated use {@see purchaseInfo.purchaseData.purchaseTime}} instead.
	 */
	@Deprecated
	public final Date purchaseTime;

	public final PurchaseInfo purchaseInfo;

	public TransactionDetails(PurchaseInfo info)
	{
		purchaseInfo = info;
		productId = purchaseInfo.purchaseData.productId;
		orderId = purchaseInfo.purchaseData.orderId;
		purchaseToken = purchaseInfo.purchaseData.purchaseToken;
		purchaseTime = purchaseInfo.purchaseData.purchaseTime;
	}

	@Override
	public String toString()
	{
		return String.format(Locale.US, "%s purchased at %s(%s). Token: %s, Signature: %s",
							 productId,
							 purchaseTime,
							 orderId,
							 purchaseToken,
							 purchaseInfo.signature);
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

		TransactionDetails details = (TransactionDetails) o;

		return !(orderId != null ? !orderId.equals(details.orderId) : details.orderId != null);
	}

	@Override
	public int hashCode()
	{
		return orderId != null ? orderId.hashCode() : 0;
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeParcelable(this.purchaseInfo, flags);
	}

	protected TransactionDetails(Parcel in)
	{
		this.purchaseInfo = in.readParcelable(PurchaseInfo.class.getClassLoader());
		this.productId = purchaseInfo.purchaseData.productId;
		this.orderId = purchaseInfo.purchaseData.orderId;
		this.purchaseToken = purchaseInfo.purchaseData.purchaseToken;
		this.purchaseTime = purchaseInfo.purchaseData.purchaseTime;
	}

	public static final Parcelable.Creator<TransactionDetails> CREATOR =
			new Parcelable.Creator<TransactionDetails>()
			{
				public TransactionDetails createFromParcel(Parcel source)
				{
					return new TransactionDetails(source);
				}

				public TransactionDetails[] newArray(int size)
				{
					return new TransactionDetails[size];
				}
			};
}
