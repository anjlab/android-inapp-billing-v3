package com.anjlab.android.iab.v3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import android.app.Activity;
import android.text.TextUtils;

public class BillingCache extends BillingBase {
	private static final String CACHE_DELIMITER = "|";
	
	ArrayList<String> products;
    String cacheKey;
	
	public BillingCache(Activity context, String key) {
		super(context);
		products = new ArrayList<String>();
        cacheKey = key;
		load();
	}
	
	private String getPreferencesCacheKey() {
		return getPreferencesBaseKey() + cacheKey;
	}
	
	private void load() {
		for(String cachedProductId : loadString(getPreferencesCacheKey(), "").split(Pattern.quote(CACHE_DELIMITER)))
			if (cachedProductId != null && cachedProductId.length() > 0)
				products.add(cachedProductId);
	}

	private void flush() {
		saveString(getPreferencesCacheKey(), TextUtils.join(CACHE_DELIMITER, products));
	}
	
	public boolean includes(String productId) {
		return products != null && products.indexOf(productId) > -1;
	}
	
	public void put(String productId) {
		if (products.indexOf(productId) < 0)
		{
			products.add(productId);
			flush();
		}
	}
	
	public void putAll(Collection<? extends String> productIds) {
		products.addAll(productIds);
		flush();
	}
	
	public void clear() {
		products.clear();
		flush();
	}

    public List<String> getContents() {
        return new ArrayList<String>(products);
    }
	
	@Override
	public String toString() {
		return TextUtils.join(", ", products);
	}
}
