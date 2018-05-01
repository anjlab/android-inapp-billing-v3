package com.anjlab.android.iab.v3;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.app.Fragment;

/**
 * @author Vitaliy Markus
 */

public interface Starter {

    void startIntentSenderForResult(IntentSender intent, int requestCode) throws IntentSender.SendIntentException;

    class ActivityStarter implements Starter {

        private final Activity activity;

        public ActivityStarter(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void startIntentSenderForResult(IntentSender intent, int requestCode) throws IntentSender.SendIntentException {
            activity.startIntentSenderForResult(intent, requestCode, new Intent(), 0, 0, 0);
        }
    }

    class FragmentStarter implements Starter {

        private final Fragment fragment;

        public FragmentStarter(Fragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public void startIntentSenderForResult(IntentSender intent, int requestCode) throws IntentSender.SendIntentException {
            fragment.startIntentSenderForResult(intent, requestCode, new Intent(), 0, 0, 0, new Bundle());
        }
    }
}
