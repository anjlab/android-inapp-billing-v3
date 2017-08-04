package com.anjlab.android.iab.v3;

import android.os.Parcel;

import com.anjlab.android.iab.v3.util.ResourcesUtil;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class PurchaseInfoParcelableTest
{

    private PurchaseInfo purchaseInfo;

    @Before
    public void init()
    {
        purchaseInfo = new PurchaseInfo(ResourcesUtil.loadFile("purchase_info.json"), "signature");
    }

    @Test
    public void testParcelable() throws Exception
    {
        Parcel parcel = Parcel.obtain();
        purchaseInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PurchaseInfo newInfo = PurchaseInfo.CREATOR.createFromParcel(parcel);

        assertEquals(purchaseInfo.responseData, newInfo.responseData);
        assertEquals(purchaseInfo.signature, newInfo.signature);
    }

    @Test
    public void testResponseDataParcelable() throws Exception
    {
        PurchaseData responseData = purchaseInfo.parseResponseDataImpl();

        Parcel parcel = Parcel.obtain();
        responseData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PurchaseData newData = PurchaseData.CREATOR.createFromParcel(parcel);

        assertEquals(responseData.autoRenewing, newData.autoRenewing);
        assertEquals(responseData.purchaseToken, newData.purchaseToken);
        assertEquals(responseData.developerPayload, newData.developerPayload);
        assertEquals(responseData.purchaseState, newData.purchaseState);
        assertEquals(responseData.purchaseTime, newData.purchaseTime);
        assertEquals(responseData.productId, newData.productId);
        assertEquals(responseData.packageName, newData.packageName);
        assertEquals(responseData.orderId, newData.orderId);
    }
}
