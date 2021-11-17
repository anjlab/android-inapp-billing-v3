/**
 * Copyright 2014 AnjLab
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

public class Constants
{
	public static final String PRODUCT_TYPE_MANAGED = "inapp";
	public static final String PRODUCT_TYPE_SUBSCRIPTION = "subs";

	public static final String RESPONSE_ORDER_ID = "orderId";
	public static final String RESPONSE_PRODUCT_ID = "productId";
	public static final String RESPONSE_PACKAGE_NAME = "packageName";
	public static final String RESPONSE_PURCHASE_TIME = "purchaseTime";
	public static final String RESPONSE_PURCHASE_STATE = "purchaseState";
	public static final String RESPONSE_PURCHASE_TOKEN = "purchaseToken";
	public static final String RESPONSE_TYPE = "type";
	public static final String RESPONSE_TITLE = "title";
	public static final String RESPONSE_DESCRIPTION = "description";
	public static final String RESPONSE_PRICE = "price";
	public static final String RESPONSE_PRICE_CURRENCY = "price_currency_code";
	public static final String RESPONSE_PRICE_MICROS = "price_amount_micros";
	public static final String RESPONSE_SUBSCRIPTION_PERIOD = "subscriptionPeriod";
	public static final String RESPONSE_AUTO_RENEWING = "autoRenewing";
	public static final String RESPONSE_FREE_TRIAL_PERIOD = "freeTrialPeriod";
	public static final String RESPONSE_INTRODUCTORY_PRICE = "introductoryPrice";
	public static final String RESPONSE_INTRODUCTORY_PRICE_MICROS = "introductoryPriceAmountMicros";
	public static final String RESPONSE_INTRODUCTORY_PRICE_PERIOD = "introductoryPricePeriod";
	public static final String RESPONSE_INTRODUCTORY_PRICE_CYCLES = "introductoryPriceCycles";

	public static final int BILLING_ERROR_FAILED_LOAD_PURCHASES = 100;
	public static final int BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE = 101;
	public static final int BILLING_ERROR_INVALID_SIGNATURE = 102;
	public static final int BILLING_ERROR_INVALID_MERCHANT_ID = 104;
	public static final int BILLING_ERROR_FAILED_TO_ACKNOWLEDGE_PURCHASE = 115;

	@Deprecated
	public static final int BILLING_ERROR_LOST_CONTEXT = 103;
	public static final int BILLING_ERROR_PRODUCT_ID_NOT_SPECIFIED = 106;
	public static final int BILLING_ERROR_OTHER_ERROR = 110;
	public static final int BILLING_ERROR_CONSUME_FAILED = 111;
	public static final int BILLING_ERROR_SKUDETAILS_FAILED = 112;
}
