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

import org.joda.time.Period;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SkuDetails implements Parcelable
{

	public final String productId;

	public final String title;

	public final String description;

	public final boolean isSubscription;

	public final String currency;

	public final double priceValue;

	public final Period subscriptionPeriod;

    public final Period subscriptionFreeTrialPeriod;

    public final double introductoryPriceValue;

    public final Period introductoryPricePeriod;

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
        subscriptionPeriod = parsePeriod(source.optString(Constants.RESPONSE_SUBSCRIPTION_PERIOD, null));
        subscriptionFreeTrialPeriod = parsePeriod(source.optString(Constants.RESPONSE_FREE_TRIAL_PERIOD, null));
        introductoryPriceLong = source.optLong(Constants.RESPONSE_INTRODUCTORY_PRICE_MICROS);
        introductoryPriceValue = introductoryPriceLong / 1000000d;
        introductoryPriceText = source.optString(Constants.RESPONSE_INTRODUCTORY_PRICE, null);
		introductoryPricePeriod = parsePeriod(source.optString(Constants.RESPONSE_INTRODUCTORY_PRICE_PERIOD, null));
		introductoryPriceCycles = source.optInt(Constants.RESPONSE_INTRODUCTORY_PRICE_CYCLES);
	}

	private Period parsePeriod(String iso8601representation)
	{
		if(iso8601representation == null)
		{
			return null;
		}
		else
		{
			return Period.parse(iso8601representation);
		}
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
		dest.writeString(this.subscriptionPeriod == null ? null : subscriptionPeriod.toString());
		dest.writeString(this.subscriptionFreeTrialPeriod == null ? null : subscriptionFreeTrialPeriod.toString());
		dest.writeDouble(this.introductoryPriceValue);
		dest.writeLong(this.introductoryPriceLong);
		dest.writeString(this.introductoryPriceText);
		dest.writeString(this.introductoryPricePeriod == null ? null : introductoryPricePeriod.toString());
		dest.writeInt(this.introductoryPriceCycles);
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
		this.subscriptionPeriod = parsePeriod(in.readString());
		this.subscriptionFreeTrialPeriod = parsePeriod(in.readString());
		this.introductoryPriceValue = in.readDouble();
		this.introductoryPriceLong = in.readLong();
		this.introductoryPriceText = in.readString();
		this.introductoryPricePeriod = parsePeriod(in.readString());
		this.introductoryPriceCycles = in.readInt();
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
