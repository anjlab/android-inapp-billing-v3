Android In-App Billing v3 Library
=======================

This is a simple, straight-forward implementation of the Android v3 In-app billing API.

It supports only 'Managed Product' purchases (for now).

Getting Started
===============

* You project should build against Android 2.2 SDK at least.

* Add this *Android In-App Billing v3 Library* to your project.

* Open the *AndroidManifest.xml* of your application and add this permission:
```xml
  <uses-permission android:name="com.android.vending.BILLING" />
```
* Create instance of BillingProcessor class in your Activity source code:
```java
  BillingProcessor bp;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		bp = new BillingProcessor(this);
	}
```

* Implement IBillingHandler Interface to handle purchase results and errors:
```java
	bp.setBillingHandler(this);
```

* override Activity's onActivityResult method:
```java
  @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!bp.handleActivityResult(requestCode, resultCode, data))
			super.onActivityResult(requestCode, resultCode, data);
	}
```

* Call purchase(productId) method for a BillingProcessor instance:
```java
bp.purchase("YOUR PRODUCT ID FROM GOOGLE PLAY CONSOLE HERE");
```
* **That's it! A super small and fast in-app library ever!**

* **And dont forget**
 to release your BillingProcessor instance! 
```java
	@Override
	public void onDestroy() {
		if (bp != null) 
			bp.release();
		
		super.onDestroy();
	}
```

IBillingHandler callback interface
-----------------------------------
has 4 methods:

```java
  void onProductPurchased(String productId);
```
  called then requested PRODUCT ID was successfully purchased
```java
	void onPurchaseHistoryRestored();
```
  called then purchase history was restored and the list of all owned PRODUCT IDs was loaded from Google Play
```java
	void onBillingError(int errorCode, Throwable error);
```
  called then some error occured. See Constants class for more details
```java
	void onBillingInitialized();
```
  called then BillingProcessor was initialized and its ready to purchase 

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request

[1]: http://developer.android.com/guide/market/billing/index.html
[2]: https://github.com/robotmedia/AndroidBillingLibrary/blob/master/AndroidBillingLibrary/src/net/robotmedia/billing/helper/AbstractBillingActivity.java
[3]: https://github.com/robotmedia/AndroidBillingLibrary/blob/master/AndroidBillingLibrary/src/net/robotmedia/billing/BillingController.java
[4]: https://github.com/robotmedia/AndroidBillingLibrary/blob/master/AndroidBillingLibrary/src/net/robotmedia/billing/IBillingObserver.java
[5]: https://github.com/robotmedia/AndroidBillingLibrary/tree/master/DungeonsRedux
