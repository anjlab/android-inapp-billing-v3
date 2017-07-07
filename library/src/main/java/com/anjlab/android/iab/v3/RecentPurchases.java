package com.anjlab.android.iab.v3;

import java.util.HashMap;

/**
 * Created by Galeen on 7.6.2017 г..
 */

public class RecentPurchases
{
    private HashMap<String, PurchaseInfo> inapps, subscriptions;

    public RecentPurchases(HashMap<String, PurchaseInfo> inapps, HashMap<String, PurchaseInfo> subscriptions)
    {
        this.inapps = inapps;
        this.subscriptions = subscriptions;
    }

    public HashMap<String, PurchaseInfo> getInapps()
    {
        return inapps;
    }

    public void setInapps(HashMap<String, PurchaseInfo> inapps)
    {
        this.inapps = inapps;
    }

    public HashMap<String, PurchaseInfo> getSubscriptions()
    {
        return subscriptions;
    }

    public void setSubscriptions(HashMap<String, PurchaseInfo> subscriptions)
    {
        this.subscriptions = subscriptions;
    }

}
