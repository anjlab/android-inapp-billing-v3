/**
 * Copyright 2014 AnjLab and Unic8
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
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * With this PurchaseInfo a developer is able verify
 * a purchase from the google play store on his own
 * server. An example implementation of how to verify
 * a purchase you can find <a href="https://github.com/mgoldsborough/google-play-in-app-billing-
 * verification/blob/master/library/GooglePlay/InAppBilling/GooglePlayResponseValidator.php#L64">here</a>
 */
public class PurchaseInfo implements Parcelable
{
    private static final String LOG_TAG = "iabv3.purchaseInfo";

    public final String responseData;
    public final String signature;
    public final PurchaseData purchaseData;

    public PurchaseInfo(String responseData, String signature)
    {
        this.responseData = responseData;
        this.signature = signature;
        this.purchaseData = parseResponseData();
    }

    /**
     * @deprecated dont call it directly, use {@see purchaseData}} instead.
     */
    @Deprecated
    public PurchaseData parseResponseData()
    {
        try
        {
            JSONObject json = new JSONObject(responseData);
            PurchaseData data = new PurchaseData();
            data.orderId = json.optString("orderId");
            data.packageName = json.optString("packageName");
            data.productId = json.optString("productId");
            long purchaseTimeMillis = json.optLong("purchaseTime", 0);
            data.purchaseTime = purchaseTimeMillis != 0 ? new Date(purchaseTimeMillis) : null;
            data.purchaseState = PurchaseState.values()[json.optInt("purchaseState", 1)];
            data.developerPayload = json.optString("developerPayload");
            data.purchaseToken = json.getString("purchaseToken");
            data.autoRenewing = json.optBoolean("autoRenewing");
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
        dest.writeString(this.signature);
    }

    protected PurchaseInfo(Parcel in)
    {
        this.responseData = in.readString();
        this.signature = in.readString();
        this.purchaseData = parseResponseData();
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
}
