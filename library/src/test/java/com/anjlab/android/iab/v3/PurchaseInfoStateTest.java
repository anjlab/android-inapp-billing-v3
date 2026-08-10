package com.anjlab.android.iab.v3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Parsing rules for the raw {@code purchaseState} field of Google Play's purchase JSON
 * in {@link PurchaseInfo#parseResponseDataImpl()}.
 *
 * <p>Google encodes a pending purchase as {@code 4}. The library modelled only states
 * {@code 0..3} and indexed {@link PurchaseState#values()} by the raw int, so a pending
 * purchase threw {@link ArrayIndexOutOfBoundsException} straight out of the {@link
 * PurchaseInfo} constructor — the very constructor the library calls itself when it
 * reports a pending purchase, so its own pending path crashed.
 *
 * <p>Uses Robolectric so the real {@code org.json.JSONObject} and {@code android.util.Log}
 * are on the classpath, matching {@link SkuDetailsFromProductDetailsTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PurchaseInfoStateTest
{
    /**
     * Builds a minimal-but-valid purchase JSON. {@code purchaseStateField} is spliced in
     * verbatim (e.g. {@code "\"purchaseState\":4,"}); pass {@code ""} to omit the field.
     */
    private static String purchaseJson(String purchaseStateField)
    {
        return "{"
                + "\"orderId\":\"GPA.1234-5678-9012-34567\","
                + "\"packageName\":\"com.example.app\","
                + "\"productId\":\"exampleSku\","
                + "\"purchaseTime\":1345678900000,"
                + purchaseStateField
                + "\"purchaseToken\":\"opaque-token\""
                + "}";
    }

    private static PurchaseData parse(String json)
    {
        return new PurchaseInfo(json, "signature").purchaseData;
    }

    @Test
    public void pendingStateParsesToPendingWithoutCrashing()
    {
        PurchaseData data = parse(purchaseJson("\"purchaseState\":4,"));

        assertNotNull("a pending purchase JSON must parse, not throw", data);
        assertEquals(PurchaseState.Pending, data.purchaseState);
    }

    @Test
    public void purchasedStateParsesToPurchasedSuccessfully()
    {
        PurchaseData data = parse(purchaseJson("\"purchaseState\":0,"));

        assertNotNull(data);
        assertEquals(PurchaseState.PurchasedSuccessfully, data.purchaseState);
    }

    @Test
    public void unknownStateDegradesToPurchasedInsteadOfCrashing()
    {
        PurchaseData data = parse(purchaseJson("\"purchaseState\":99,"));

        assertNotNull("an unmodelled state must not throw", data);
        assertEquals(PurchaseState.PurchasedSuccessfully, data.purchaseState);
    }

    @Test
    public void absentStateKeepsHistoricalDefault()
    {
        PurchaseData data = parse(purchaseJson(""));

        assertNotNull(data);
        assertEquals(PurchaseState.Canceled, data.purchaseState);
    }
}
