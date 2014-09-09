/**
 * Copyright 2014 AnjLab and Unic8
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anjlab.android.iab.v3;

/**
 * With this PurchaseInfo a developer is able verify
 * a purchase from the google play store on his own
 * server. An example implementation of how to verify
 * a purchase you can find <a href="https://github.com/mgoldsborough/google-play-in-app-billing-verification/blob/master/library/GooglePlay/InAppBilling/GooglePlayResponseValidator.php#L64">here</a>
 *
 */
public class PurchaseInfo {

	public final String responseData;

	public final String signature;

	PurchaseInfo(String responseData, String signature) {
		this.responseData = responseData;
		this.signature = signature;
	}
}
