package com.anjlab.android.iab.v3;

import android.os.Parcel;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

public class TransactionDetailsParcelableTest
{
    @Test
    public void testParcelable() throws Exception
    {
        String purchaseInfoJson =
                "{\"orderId\": \"GPA.1234-5678-9012-34567\",\"packageName\": \"com.example.app\"," +
                "\"productId\": \"exampleSku\",\"purchaseTime\": 1345678900000,\"purchaseState\": 0," +
                "\"developerPayload\": \"bGoa+V7g/yqDXvKRqq+JTFn4uQZbPiQJo4pf9RzJ\"," +
                "\"purchaseToken\": \"opaque-token-up-to-1000-characters\"}";

        PurchaseInfo purchaseInfo = new PurchaseInfo(purchaseInfoJson, "signature");

        TransactionDetails details = new TransactionDetails(purchaseInfo);

        Parcel parcel = Parcel.obtain();
        details.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TransactionDetails result = TransactionDetails.CREATOR.createFromParcel(parcel);

        assertEquals(details.productId, result.productId);
        assertEquals(details.orderId, result.orderId);
        assertEquals(details.purchaseToken, result.purchaseToken);
        assertEquals(details.purchaseTime, result.purchaseTime);

        // Only check that purchase info is not null, we check it's parcel implementationin it's own tests
        assertNotNull(result.purchaseInfo);
    }
}
