package com.anjlab.android.iab.v3;

public class BillingError extends Exception
{
    private int responseCode;

    public BillingError(int responseCode)
    {
        this.responseCode = responseCode;
    }

    public int getResponseCode()
    {
        return responseCode;
    }

    @Override
    public String toString()
    {
        return "BillingError: Code " + Integer.toString(responseCode);
    }
}
