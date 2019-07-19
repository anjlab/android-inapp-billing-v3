package com.anjlab.android.iab.v3;

import android.os.Parcel;

import com.anjlab.android.iab.v3.util.ResourcesUtil;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import static junit.framework.Assert.assertEquals;

public class BillingHistoryRecordTest
{

    private String historyResponseJson;

    @Before
    public void setup()
    {
        historyResponseJson = ResourcesUtil.loadFile("purchase_history_response.json");
    }

    @Test
    public void testCreatesFromJsonCorrectly() throws JSONException
    {
        BillingHistoryRecord record = new BillingHistoryRecord(historyResponseJson, "signature");

        assertEquals("sample-product-id", record.productId);
        assertEquals("sample-purchase-token", record.purchaseToken);
        assertEquals(1563441231403L, record.purchaseTime);
        assertEquals("sample-developer-payload", record.developerPayload);
        assertEquals("signature", record.signature);
    }

    @Test
    public void testParcelizesCorrectly() throws JSONException
    {
        BillingHistoryRecord record = new BillingHistoryRecord(historyResponseJson, "signature");

        Parcel parcel = Parcel.obtain();
        record.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BillingHistoryRecord restoredRecord = BillingHistoryRecord.CREATOR.createFromParcel(parcel);
        assertEquals("sample-product-id", restoredRecord.productId);
        assertEquals("sample-purchase-token", restoredRecord.purchaseToken);
        assertEquals(1563441231403L, restoredRecord.purchaseTime);
        assertEquals("sample-developer-payload", restoredRecord.developerPayload);
        assertEquals("signature", restoredRecord.signature);
    }
}
