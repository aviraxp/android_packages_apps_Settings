/*
 * Copyright (C) 2013 The CyanogenMod project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;

import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManagerGlobal;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.button.ButtonBacklightBrightness;

public class ButtonSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SystemSettings";

    private static final String KEY_BUTTON_BACKLIGHT = "button_backlight";
    private static final String KEY_HOME_LONG_PRESS = "hardware_keys_home_long_press";
    private static final String KEY_HOME_DOUBLE_TAP = "hardware_keys_home_double_tap";
    private static final String KEY_MENU_PRESS = "hardware_keys_menu_press";
    private static final String KEY_MENU_LONG_PRESS = "hardware_keys_menu_long_press";
    private static final String 
                KEY_NAVBAR_SWITCH = "navigationbar_switch",
                KEY_HWKEYS_SWITCH = "hwkeys_switch"
                ;

    private static final String CATEGORY_POWER = "power_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_MENU = "menu_key";
    private static final String CATEGORY_ASSIST = "assist_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_CAMERA = "camera_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_BACKLIGHT = "key_backlight";
    private static final String CATEGORY_NAVBAR = "navigation_bar";

    // Available custom actions to perform on a key press.
    // Must match values for KEY_HOME_LONG_PRESS_ACTION in:
    // frameworks/base/core/java/android/provider/Settings.java
    private static final int ACTION_NOTHING = 0;
    private static final int ACTION_MENU = 1;
    private static final int ACTION_APP_SWITCH = 2;
    private static final int ACTION_SEARCH = 3;
    private static final int ACTION_VOICE_SEARCH = 4;
    private static final int ACTION_IN_APP_SEARCH = 5;
    private static final int ACTION_LAUNCH_CAMERA = 6;
    private static final int ACTION_LAST_APP = 7;
    private static final int ACTION_SLEEP = 8;

    // Masks for checking presence of hardware keys.
    // Must match values in frameworks/base/core/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;

    private ButtonBacklightBrightness mBacklight;

    private ListPreference mHomeLongPressAction;
    private ListPreference mHomeDoubleTapAction;
    private ListPreference mMenuPressAction;
    private ListPreference mMenuLongPressAction;
    
    private SwitchPreference
                mNavbarPreference,
                mHwKeysPreference
                ;

    private PreferenceCategory mNavigationPreferencesCat;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.button_settings);

        final Resources res = getResources();
        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final int deviceKeys = getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);

        final boolean hasPowerKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER);
        final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;
        final boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
        final boolean hasAssistKey = (deviceKeys & KEY_MASK_ASSIST) != 0;

        boolean hasAnyBindableKey = false;
        final PreferenceCategory homeCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_HOME);
        final PreferenceCategory menuCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_MENU);

        mHandler = new Handler();

        if (hasHomeKey) {
            int defaultLongPressAction = res.getInteger(
                    com.android.internal.R.integer.config_longPressOnHomeBehavior);
            if (defaultLongPressAction < ACTION_NOTHING ||
                    defaultLongPressAction > ACTION_SLEEP) {
                defaultLongPressAction = ACTION_NOTHING;
            }

            int defaultDoubleTapAction = res.getInteger(
                    com.android.internal.R.integer.config_doubleTapOnHomeBehavior);
            if (defaultDoubleTapAction < ACTION_NOTHING ||
                    defaultDoubleTapAction > ACTION_SLEEP) {
                defaultDoubleTapAction = ACTION_NOTHING;
            }

            int longPressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_HOME_LONG_PRESS_ACTION,
                    defaultLongPressAction);
            mHomeLongPressAction = initActionList(KEY_HOME_LONG_PRESS, longPressAction);

            int doubleTapAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                    defaultDoubleTapAction);
            mHomeDoubleTapAction = initActionList(KEY_HOME_DOUBLE_TAP, doubleTapAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(homeCategory);
        }

        if (hasMenuKey) {
            int pressAction = Settings.System.getInt(resolver,
                    Settings.System.KEY_MENU_ACTION, ACTION_MENU);
            mMenuPressAction = initActionList(KEY_MENU_PRESS, pressAction);

            int longPressAction = Settings.System.getInt(resolver,
                        Settings.System.KEY_MENU_LONG_PRESS_ACTION,
                        hasAssistKey ? ACTION_NOTHING : ACTION_SEARCH);
            mMenuLongPressAction = initActionList(KEY_MENU_LONG_PRESS, longPressAction);

            hasAnyBindableKey = true;
        } else {
            prefScreen.removePreference(menuCategory);
        }

        mBacklight =
                (ButtonBacklightBrightness) findPreference(KEY_BUTTON_BACKLIGHT);
        if (!mBacklight.isButtonSupported()) {
            prefScreen.removePreference(mBacklight);
        }
        
        loadPreferences();
    }
    
    private void loadPreferences() {
        // Add new preferences here
        String[] prefs = new String[] {
            KEY_NAVBAR_SWITCH,
            KEY_HWKEYS_SWITCH
        };
        
        // Load all preferences
        for ( String pref : prefs )
            reloadPreference(pref);
    }

    private void reloadPreference(String key) {
        switch(key) {
            case KEY_NAVBAR_SWITCH:
                mNavbarPreference = (SwitchPreference) findPreference(KEY_NAVBAR_SWITCH);
                mNavbarPreference.setOnPreferenceChangeListener(this);
                mNavbarPreference.setChecked(
                    Settings.System.getIntForUser(getContentResolver(),
                        Settings.System.DEV_FORCE_SHOW_NAVBAR,
                        hasNavbarByDefault(getContext()) ? 1 : 0,
                        UserHandle.USER_CURRENT) == 1
                );
                break;
            case KEY_HWKEYS_SWITCH:
                mHwKeysPreference = (SwitchPreference) findPreference(KEY_HWKEYS_SWITCH);
                mHwKeysPreference.setOnPreferenceChangeListener(this);
                if(Settings.System.getInt(getContentResolver(),
                        Settings.System.HARDWARE_BUTTONS_SUPPORTED,
                            0) == 0) removePreference(KEY_HWKEYS_SWITCH);

                mHwKeysPreference.setChecked(
                    Settings.System.getIntForUser(getContentResolver(),
                        Settings.System.HARDWARE_BUTTONS_ENABLED,
                        hasNavbarByDefault(getContext()) ? 0 : 1,
                        UserHandle.USER_CURRENT) == 1
                );
                break;
            default: break;
        }
    }

    private ListPreference initActionList(String key, int value) {
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        list.setValue(Integer.toString(value));
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private void handleActionListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);

        pref.setSummary(pref.getEntries()[index]);
        Settings.System.putInt(getContentResolver(), setting, Integer.valueOf(value));
    }

    @Override
    protected int getMetricsCategory() {
        return 0;
    }

    private void handleButtonBacklight(boolean hwKeysEnabled) {
        try {
            if(mBacklight.isButtonSupported()) {
                Settings.System.putInt(getContentResolver(),
                    Settings.System.BUTTON_BRIGHTNESS, hwKeysEnabled ? 255 : 0);
                mBacklight.applyTimeout(2 /* seconds */);
                mBacklight.updateSummary();
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHomeLongPressAction) {
            handleActionListChange(mHomeLongPressAction, newValue,
                    Settings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mHomeDoubleTapAction) {
            handleActionListChange(mHomeDoubleTapAction, newValue,
                    Settings.System.KEY_HOME_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mMenuPressAction) {
            handleActionListChange(mMenuPressAction, newValue,
                    Settings.System.KEY_MENU_ACTION);
            return true;
        } else if (preference == mMenuLongPressAction) {
            handleActionListChange(mMenuLongPressAction, newValue,
                    Settings.System.KEY_MENU_LONG_PRESS_ACTION);
            return true;
        }
        switch(preference.getKey()) {
            case KEY_NAVBAR_SWITCH:
                Log.d(TAG, "Navbar switch toggled: " + ((Boolean)newValue));
                Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.DEV_FORCE_SHOW_NAVBAR,
                    (Boolean)newValue ? 1 : 0, UserHandle.USER_CURRENT);
                Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HARDWARE_BUTTONS_ENABLED,
                    (Boolean)newValue ? 0 : 1, UserHandle.USER_CURRENT);
                mHwKeysPreference.setChecked(!(Boolean)newValue);
                handleButtonBacklight(!(boolean)newValue);
                break;
            case KEY_HWKEYS_SWITCH:
                Log.d(TAG, "Hw keys switch toggled: " + ((Boolean)newValue));
                Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HARDWARE_BUTTONS_ENABLED,
                    (Boolean)newValue ? 1 : 0, UserHandle.USER_CURRENT);
                handleButtonBacklight((boolean)newValue);
                break;
            default: break;
        }
        reloadPreference(preference.getKey());
        return false;
    }
    
    // Thanks https://goo.gl/JfubA9
    private static boolean hasNavbarByDefault(Context context) {
        Resources res;
        try {
            res = 
                context.getPackageManager().getResourcesForApplication("android");
        } catch(Exception ex) {
            res = context.getResources();
        }
        int id = res.getIdentifier("config_showNavigationBar", "bool", "android");
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        return Boolean.valueOf(res.getBoolean(id)) || "0".equals(navBarOverride);
    }

}
