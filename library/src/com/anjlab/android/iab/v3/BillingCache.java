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

import android.app.Activity;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

class BillingCache extends BillingBase {
    private static final String ENTRY_DELIMITER = "#####";
    private static final String LINE_DELIMITER  = ">>>>>";

    private HashMap<String, PurchaseInfo> data;
    private String cacheKey;

	public BillingCache(Activity context, String key) {
		super(context);
	    data = new HashMap<String, PurchaseInfo>();
        cacheKey = key;
		load();
	}

	private String getPreferencesCacheKey() {
		return getPreferencesBaseKey() + cacheKey;
	}

	private void load() {
		for(String entry : loadString(getPreferencesCacheKey(), "").split(Pattern.quote(ENTRY_DELIMITER))) {
            if (!TextUtils.isEmpty(entry)) {
                String[] parts = entry.split(Pattern.quote(LINE_DELIMITER));
                if (parts.length > 1)
                    data.put(parts[0], new PurchaseInfo(parts[1], parts[2]));
            }
		}
	}

	private void flush() {
        ArrayList<String> output = new ArrayList<String>();
        for(String productId : data.keySet()) {
            PurchaseInfo info = data.get(productId);
            output.add(productId + LINE_DELIMITER + info.jsonObject + LINE_DELIMITER + info.signature);
        }
		saveString(getPreferencesCacheKey(), TextUtils.join(ENTRY_DELIMITER, output));
	}

	public boolean includesProduct(String productId) {
		return data != null && data.containsKey(productId);
	}

    public PurchaseInfo getDetails(String productId) {
        return data.containsKey(productId) ? data.get(productId) : null;
    }

    public void put(String productId, String details, String signature) {
        if (!data.containsKey(productId)) {
            data.put(productId, new PurchaseInfo(details, signature));
            flush();
        }
    }

    public void remove(String productId) {
        if (data.containsKey(productId)) {
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
