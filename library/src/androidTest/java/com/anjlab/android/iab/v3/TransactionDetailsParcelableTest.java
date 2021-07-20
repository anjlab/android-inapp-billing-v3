package com.anjlab.android.iab.v3;

import android.os.Parcel;

import com.anjlab.android.iab.v3.util.ResourcesUtil;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

public class TransactionDetailsParcelableTest
{
    @Test
    public void testParcelable() throws Exception
    {
        PurchaseInfo purchaseInfo = new PurchaseInfo(ResourcesUtil.loadFile("transaction_details.json"), "signature");

        TransactionDetails details = new TransactionDetails(purchaseInfo);

        Parcel parcel = Parcel.obtain();
        details.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        TransactionDetails result = TransactionDetails.CREATOR.createFromParcel(parcel);

        assertEquals(details.purchaseInfo, result.purchaseInfo);

        // Only check that purchase info is not null, we check its parcel implementation in its own tests
        assertNotNull(result.purchaseInfo);
    }
}
