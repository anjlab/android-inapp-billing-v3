/**
 * Copyright 2014 AnjLab
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anjlab.android.iab.v3;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;

public class AsyncBilling
{

	static <T> void doAsync(BillingProcessor billingProcessor, IAsyncTask<T> backgroundTask, IAsyncResponse<T> uiResponse)
	{
		new AsyncBillingTask<>(billingProcessor, backgroundTask, uiResponse).execute();
	}

	interface IAsyncTask<T>
	{
		/**
		 * Task to execute with the given {@link BillingProcessor}
		 *
		 * @param bp the nonnull and initialized billing processor
		 * @return result
		 */
		T doInBackground(@NonNull BillingProcessor bp);
	}

	public interface IAsyncResponse<T>
	{
		/**
		 * Guaranteed callback from the async task
		 *
		 * @param response  the response from {@link IAsyncTask}, or {@code null} is invalid
		 * @param valid {@code true} if billing processor reference is still valid and initialized
		 */
		void onResponse(@Nullable T response, boolean valid);
	}

	/**
	 * Generic async task, binding a {@link BillingProcessor}
	 *
	 * @param <T> response type
	 */
	private static class AsyncBillingTask<T> extends AsyncTask<Void, Void, T>
	{

		private final WeakReference<BillingProcessor> bpRef;
		private final IAsyncTask<T> bgTask;
		private final IAsyncResponse<T> uiResponse;

		private AsyncBillingTask(BillingProcessor bp, IAsyncTask<T> bgTask, IAsyncResponse<T> uiResponse)
		{
			this.bpRef = new WeakReference<>(bp);
			this.bgTask = bgTask;
			this.uiResponse = uiResponse;
		}

		@Override
		protected T doInBackground(Void... voids)
		{
			BillingProcessor bp = bpRef.get();
			if (bp == null || !bp.isInitialized())
			{
				return null;
			}
			return bgTask.doInBackground(bp);
		}

		@Override
		protected void onCancelled()
		{
			uiResponse.onResponse(null, false);
		}

		@Override
		protected void onPostExecute(T t)
		{
			BillingProcessor bp = bpRef.get();
			if (t == null && (bp == null || !bp.isInitialized()))
			{
				onCancelled(); //we skipped the task as the processor isn't ready
			}
			else
			{
				uiResponse.onResponse(t, true);
			}
		}
	}

}
