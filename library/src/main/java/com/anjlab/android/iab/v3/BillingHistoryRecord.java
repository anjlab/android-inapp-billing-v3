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

import android.os.Parcel;
import android.os.Parcelable;

public class BillingHistoryRecord implements Parcelable
{

    public final String productId;
    public final String purchaseToken;
    public final long purchaseTime;
    public final String developerPayload;
    public final String signature;

    public BillingHistoryRecord(String dataAsJson, String signature) throws JSONException
    {
        this(new JSONObject(dataAsJson), signature);
    }

    public BillingHistoryRecord(JSONObject json, String signature) throws JSONException
    {
        productId = json.getString("productId");
        purchaseToken = json.getString("purchaseToken");
        purchaseTime = json.getLong("purchaseTime");
        developerPayload = json.getString("developerPayload");
        this.signature = signature;
    }

    public BillingHistoryRecord(String productId, String purchaseToken, long purchaseTime,
                                String developerPayload, String signature)
    {
        this.productId = productId;
        this.purchaseToken = purchaseToken;
        this.purchaseTime = purchaseTime;
        this.developerPayload = developerPayload;
        this.signature = signature;
    }

    protected BillingHistoryRecord(Parcel in)
    {
        productId = in.readString();
        purchaseToken = in.readString();
        purchaseTime = in.readLong();
        developerPayload = in.readString();
        signature = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(productId);
        dest.writeString(purchaseToken);
        dest.writeLong(purchaseTime);
        dest.writeString(developerPayload);
        dest.writeString(signature);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public static final Creator<BillingHistoryRecord> CREATOR = new Creator<BillingHistoryRecord>()
    {
        @Override
        public BillingHistoryRecord createFromParcel(Parcel in)
        {
            return new BillingHistoryRecord(in);
        }

        @Override
        public BillingHistoryRecord[] newArray(int size)
        {
            return new BillingHistoryRecord[size];
        }
    };

    @Override
    public String toString()
    {
        return "BillingHistoryRecord{" +
                "productId='" + productId + '\'' +
                ", purchaseToken='" + purchaseToken + '\'' +
                ", purchaseTime=" + purchaseTime +
                ", developerPayload='" + developerPayload + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
