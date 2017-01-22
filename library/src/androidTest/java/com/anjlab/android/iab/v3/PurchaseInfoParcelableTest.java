package com.anjlab.android.iab.v3;

import android.os.Parcel;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class PurchaseInfoParcelableTest
{
    private final String purchaseInfoJson =
            "{\"orderId\": \"GPA.1234-5678-9012-34567\",\"packageName\": \"com.example.app\","+
            "\"productId\": \"exampleSku\",\"purchaseTime\": 1345678900000,\"purchaseState\": 0,"+
            "\"developerPayload\": \"bGoa+V7g/yqDXvKRqq+JTFn4uQZbPiQJo4pf9RzJ\","+
            "\"purchaseToken\": \"opaque-token-up-to-1000-characters\"}";

    @Test
    public void testParcelable() throws Exception
    {
        PurchaseInfo purchaseInfo = new PurchaseInfo(purchaseInfoJson, "signature");

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
        PurchaseInfo purchaseInfo = new PurchaseInfo(purchaseInfoJson, "signature");

        PurchaseData responseData = purchaseInfo.parseResponseData();

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
