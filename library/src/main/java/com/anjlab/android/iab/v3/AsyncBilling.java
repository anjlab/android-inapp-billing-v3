package com.anjlab.android.iab.v3;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * Created by Allan Wang on 2017-08-07.
 */

public class AsyncBilling {

    public static void loadPurchasesFromGoogleAsync(BillingProcessor billingProcessor, IAsyncResponse<Boolean> response) {
        doAsync(billingProcessor, new IAsyncTask<Boolean>() {
            @Override
            public Boolean doInBackground(@NonNull BillingProcessor billingProcessor) {
                return billingProcessor.loadOwnedPurchasesFromGoogle();
            }
        }, response);
    }

    public interface IAsyncTask<T> {
        T doInBackground(@NonNull BillingProcessor billingProcessor);
    }

    public interface IAsyncResponse<T> {
        void onResponse(T response);

        void onCancelled();
    }

    public static <T> void doAsync(BillingProcessor billingProcessor, IAsyncTask<T> backgroundTask, IAsyncResponse<T> uiResponse) {
        new AsyncBillingTask<>(billingProcessor, backgroundTask, uiResponse).execute();
    }

    private static class AsyncBillingTask<T> extends AsyncTask<Void, Void, T> {

        private final WeakReference<BillingProcessor> bpRef;
        private final IAsyncTask<T> bgTask;
        private final IAsyncResponse<T> uiResponse;

        private AsyncBillingTask(BillingProcessor bp, IAsyncTask<T> bgTask, IAsyncResponse<T> uiResponse) {
            this.bpRef = new WeakReference<>(bp);
            this.bgTask = bgTask;
            this.uiResponse = uiResponse;
        }

        @Override
        protected T doInBackground(Void... voids) {
            BillingProcessor bp = bpRef.get();
            if (bp == null || !bp.isInitialized()) return null;
            return bgTask.doInBackground(bp);
        }

        @Override
        protected void onCancelled() {
            uiResponse.onCancelled();
        }

        @Override
        protected void onPostExecute(T t) {
            //todo check if we should verify that the billing processor is still valid
            //even though the response may not depend on the billing processor, it may indicate that
            //the bounded activity is finished
            uiResponse.onResponse(t);
        }
    }

}
