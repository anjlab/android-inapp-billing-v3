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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * With this PurchaseInfo a developer is able verify
 * a purchase from the google play store on his own
 * server. An example implementation of how to verify
 * a purchase you can find here:
 * <pre>
 * See <a href="https://github.com/mgoldsborough/google-play-in-app-billing-verification/blob/
 * master/library/GooglePlay/InAppBilling/GooglePlayResponseValidator.php#L64"> here </a>
 * </pre>
 */
public class PurchaseInfo implements Parcelable
{
    private static final String LOG_TAG = "iabv3.purchaseInfo";

    public final String responseData;
    public final String signature;
    /**
     * @deprecated Google does not support developer payloads anymore.
     */
    @Deprecated
    public final String developerPayload;
    public final PurchaseData purchaseData;

    public PurchaseInfo(String responseData, String signature)
    {
        this.responseData = responseData;
        this.signature = signature;
        this.developerPayload = "";
        this.purchaseData = parseResponseDataImpl();
    }

    public PurchaseInfo(String responseData, String signature, String developerPayload)
    {
        this.responseData = responseData;
        this.signature = signature;
        this.developerPayload = developerPayload;
        this.purchaseData = parseResponseDataImpl();
    }

    PurchaseData parseResponseDataImpl()
    {
        try
        {
            JSONObject json = new JSONObject(responseData);
            PurchaseData data = new PurchaseData();
            data.orderId = json.optString(Constants.RESPONSE_ORDER_ID);
            data.packageName = json.optString(Constants.RESPONSE_PACKAGE_NAME);
            data.productId = json.optString(Constants.RESPONSE_PRODUCT_ID);
            long purchaseTimeMillis = json.optLong(Constants.RESPONSE_PURCHASE_TIME, 0);
            data.purchaseTime = purchaseTimeMillis != 0 ? new Date(purchaseTimeMillis) : null;
            data.purchaseState = PurchaseState.values()[json.optInt(Constants.RESPONSE_PURCHASE_STATE, 1)];
            data.developerPayload = developerPayload;
            data.purchaseToken = json.getString(Constants.RESPONSE_PURCHASE_TOKEN);
            data.autoRenewing = json.optBoolean(Constants.RESPONSE_AUTO_RENEWING);
            return data;
        }
        catch (JSONException e)
        {
            Log.e(LOG_TAG, "Failed to parse response data", e);
            return null;
        }
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(this.responseData);
        dest.writeString(this.developerPayload);
        dest.writeString(this.signature);
    }

    protected PurchaseInfo(Parcel in)
    {
        this.responseData = in.readString();
        this.developerPayload = in.readString();
        this.signature = in.readString();
        this.purchaseData = parseResponseDataImpl();
    }

    public static final Parcelable.Creator<PurchaseInfo> CREATOR =
            new Parcelable.Creator<PurchaseInfo>()
            {
                public PurchaseInfo createFromParcel(Parcel source)
                {
                    return new PurchaseInfo(source);
                }

                public PurchaseInfo[] newArray(int size)
                {
                    return new PurchaseInfo[size];
                }
            };

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || !(o instanceof PurchaseInfo))
        {
            return false;
        }
        PurchaseInfo other = (PurchaseInfo) o;
        return responseData.equals(other.responseData)
               && signature.equals(other.signature)
               && developerPayload.equals(other.developerPayload)
               && purchaseData.purchaseToken.equals(other.purchaseData.purchaseToken)
               && purchaseData.purchaseTime.equals(other.purchaseData.purchaseTime);
    }
}
