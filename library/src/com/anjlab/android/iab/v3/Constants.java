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

public class Constants {
    public static final int GOOGLE_API_VERSION = 3;

    public static final String PRODUCT_TYPE_MANAGED = "inapp";
    public static final String PRODUCT_TYPE_SUBSCRIPTION = "subs";

	public static final int BILLING_RESPONSE_RESULT_OK = 0; 				//Success
	public static final int BILLING_RESPONSE_RESULT_USER_CANCELED =	1; 		//User pressed back or canceled a dialog
	public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;//Billing API version is not supported for the type requested
	public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4; 	//Requested product is not available for purchase
	public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5; 	//Invalid arguments provided to the API. This error can also indicate that the application was not correctly signed or properly set up for In-app Billing in Google Play, or does not have the necessary permissions in its manifest
	public static final int BILLING_RESPONSE_RESULT_ERROR =	6; 				//Fatal error during the API action
	public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7; //Failure to purchase since item is already owned
	public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8; 	//Failure to consume since item is not owned

	public static final String RESPONSE_CODE = "RESPONSE_CODE";
	public static final String DETAILS_LIST = "DETAILS_LIST";
    public static final String PRODUCTS_LIST = "ITEM_ID_LIST";
	public static final String BUY_INTENT = "BUY_INTENT";
	public static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
	public static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
	public static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    public static final String RESPONSE_ORDER_ID = "orderId";
    public static final String RESPONSE_PRODUCT_ID = "productId";
    public static final String RESPONSE_TYPE = "type";
    public static final String RESPONSE_TITLE = "title";
    public static final String RESPONSE_DESCRIPTION = "description";
    public static final String RESPONSE_PRICE = "price";
    public static final String RESPONSE_PRICE_CURRENCY = "price_currency_code";
    public static final String RESPONSE_PRICE_MICROS = "price_amount_micros";
    public static final String RESPONSE_PURCHASE_TOKEN = "purchaseToken";
    public static final String RESPONSE_PURCHASE_TIME = "purchaseTime";
    public static final String RESPONSE_PAYLOAD = "developerPayload";

	public static final int BILLING_ERROR_FAILED_LOAD_PURCHASES = 100;
	public static final int BILLING_ERROR_FAILED_TO_INITIALIZE_PURCHASE = 101;
	public static final int BILLING_ERROR_INVALID_SIGNATURE = 102;
	public static final int BILLING_ERROR_LOST_CONTEXT = 103;
	public static final int BILLING_ERROR_OTHER_ERROR = 110;
}
