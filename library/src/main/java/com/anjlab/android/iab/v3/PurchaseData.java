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

import java.util.Date;

import android.os.Parcel;
import android.os.Parcelable;

public class PurchaseData implements Parcelable
{
    public String orderId;
    public String packageName;
    public String productId;
    public Date purchaseTime;
    public PurchaseState purchaseState;
    /**
     * @deprecated Google does not support developer payloads anymore.
     */
    @Deprecated
    public String developerPayload;
    public String purchaseToken;
    public boolean autoRenewing;

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(this.orderId);
        dest.writeString(this.packageName);
        dest.writeString(this.productId);
        dest.writeLong(purchaseTime != null ? purchaseTime.getTime() : -1);
        dest.writeInt(this.purchaseState == null ? -1 : this.purchaseState.ordinal());
        dest.writeString(this.developerPayload);
        dest.writeString(this.purchaseToken);
        dest.writeByte(autoRenewing ? (byte) 1 : (byte) 0);
    }

    public PurchaseData()
    {
    }

    protected PurchaseData(Parcel in)
    {
        this.orderId = in.readString();
        this.packageName = in.readString();
        this.productId = in.readString();
        long tmpPurchaseTime = in.readLong();
        this.purchaseTime = tmpPurchaseTime == -1 ? null : new Date(tmpPurchaseTime);
        int tmpPurchaseState = in.readInt();
        this.purchaseState =
                tmpPurchaseState == -1 ? null : PurchaseState.values()[tmpPurchaseState];
        this.developerPayload = in.readString();
        this.purchaseToken = in.readString();
        this.autoRenewing = in.readByte() != 0;
    }

    public static final Parcelable.Creator<PurchaseData> CREATOR =
            new Parcelable.Creator<PurchaseData>()
            {
                public PurchaseData createFromParcel(Parcel source)
                {
                    return new PurchaseData(source);
                }

                public PurchaseData[] newArray(int size)
                {
                    return new PurchaseData[size];
                }
            };
}