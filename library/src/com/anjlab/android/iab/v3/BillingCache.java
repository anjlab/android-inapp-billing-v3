package com.anjlab.android.iab.v3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import android.app.Activity;
import android.text.TextUtils;

public class BillingCache extends BillingBase {
    private static final String ENTRY_DELIMITER = "#####";
    private static final String LINE_DELIMITER  = ">>>>>";
	
	HashMap<String, String> data;
    String cacheKey;
	
	public BillingCache(Activity context, String key) {
		super(context);
	    data = new HashMap<String, String>();
        cacheKey = key;
		load();
	}
	
	private String getPreferencesCacheKey() {
		return getPreferencesBaseKey() + cacheKey;
	}
	
	private void load() {
		for(String entry : loadString(getPreferencesCacheKey(), "").split(Pattern.quote(ENTRY_DELIMITER)))
            if (!TextUtils.isEmpty(entry)) {
                String[] parts = entry.split(Pattern.quote(LINE_DELIMITER));
                if (parts.length > 1)
                    data.put(parts[0], parts[1]);
            }
	}

	private void flush() {
        ArrayList<String> output = new ArrayList<String>();
        for(String productId : data.keySet())
            output.add(productId + LINE_DELIMITER + data.get(productId));
		saveString(getPreferencesCacheKey(), TextUtils.join(ENTRY_DELIMITER, output));
	}
	
	public boolean includesProduct(String productId) {
		return data != null && data.containsKey(productId);
	}

    public String getProductPurchaseToken(String productId) {
        return data.containsKey(productId) ? data.get(productId) : null;
    }

    public void put(String productId, String purchaseToken) {
        if (!data.containsKey(productId))
        {
            data.put(productId, purchaseToken);
            flush();
        }
    }

    public void remove(String productId) {
        if (data.containsKey(productId))
        {
            data.remove(productId);
            flush();
        }
    }
	
	public void clear() {
        data.clear();
		flush();
	}

    public List<String> getContents() {
        return new ArrayList<String>(data.keySet());
    }
	
	@Override
	public String toString() {
		return TextUtils.join(", ", data.keySet());
	}
}
