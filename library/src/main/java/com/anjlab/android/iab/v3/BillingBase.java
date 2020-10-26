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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

class BillingBase
{
	private final Context context;

	BillingBase(@NonNull Context context)
	{
		this.context = context;
	}

	@NonNull
	Context getContext()
	{
		return context;
	}

	@NonNull
	String getPreferencesBaseKey()
	{
		return getContext().getPackageName() + "_preferences";
	}

	@Nullable
	private SharedPreferences getPreferences()
	{
		return PreferenceManager.getDefaultSharedPreferences(getContext());
	}

	boolean saveString(@NonNull String key, @Nullable String value)
	{
		SharedPreferences sp = getPreferences();
		if (sp != null)
		{
			SharedPreferences.Editor spe = sp.edit();
			spe.putString(key, value);
			spe.commit();
			return true;
		}
		return false;
	}

	@Nullable
	String loadString(@NonNull String key, @Nullable String defValue)
	{
		SharedPreferences sp = getPreferences();
		if (sp != null)
		{
			return sp.getString(key, defValue);
		}
		return defValue;
	}

	boolean saveBoolean(@NonNull String key, @NonNull Boolean value)
	{
		SharedPreferences sp = getPreferences();
		if (sp != null)
		{
			SharedPreferences.Editor spe = sp.edit();
			spe.putBoolean(key, value);
			spe.commit();
			return true;
		}
		return false;
	}

	boolean loadBoolean(@NonNull String key, boolean defValue)
	{
		SharedPreferences sp = getPreferences();
		if (sp != null)
		{
			return sp.getBoolean(key, defValue);
		}
		return defValue;
	}
}

