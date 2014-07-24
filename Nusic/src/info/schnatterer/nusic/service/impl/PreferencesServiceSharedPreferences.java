/* Copyright (C) 2013 Johannes Schnatterer
 * 
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *  
 * This file is part of nusic.
 * 
 * nusic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * nusic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with nusic.  If not, see <http://www.gnu.org/licenses/>.
 */
package info.schnatterer.nusic.service.impl;

import info.schnatterer.nusic.Application;
import info.schnatterer.nusic.Constants;
import info.schnatterer.nusic.R;
import info.schnatterer.nusic.service.PreferencesService;
import info.schnatterer.nusic.service.event.PreferenceChangedListener;
import info.schnatterer.nusic.util.DateUtil;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Implements {@link PreferencesService} using Android's
 * {@link SharedPreferences}.
 * 
 * @author schnatterer
 * 
 */
public class PreferencesServiceSharedPreferences implements PreferencesService,
		OnSharedPreferenceChangeListener {

	/*
	 * Preferences that are not accessible through preferences menu
	 * (preferences.xml)
	 */
	private final String KEY_LAST_APP_VERSION = "last_app_version";
	private final int DEFAULT_LAST_APP_VERSION = -1;

	public final String KEY_LAST_RELEASES_REFRESH = "last_release_refresh";
	public final Date DEFAULT_LAST_RELEASES_REFRESH = null;

	public final String KEY_NEXT_RELEASES_REFRESH = "next_release_refresh";
	public final Date DEFAULT_NEXT_RELEASES_REFRESH = null;

	public final String KEY_LAST_RELEASES_REFRESH_SUCCESSFULL = "last_release_refresh_succesful";
	public final Boolean DEFAULT_LAST_RELEASES_REFRESH_SUCCESSFULL = Boolean.FALSE;

	private final String KEY_JUST_ADDED_TIME_PERIOD = "just_added_time_period";
	// Define in constructor!
	private final Integer DEFAULT_JUST_ADDED_TIME_PERIOD;

	public final String KEY_ENABLED_CONNECTIVITY_RECEIVER = "connectivityReceiver";
	public final Boolean DEFAULT_ENABLED_CONNECTIVITY_RECEIVER = Boolean.FALSE;

	/*
	 * Preferences that are defined in constants_prefernces.xml -> accessible
	 * for preferences.xml
	 */
	public final String KEY_DOWLOAD_ONLY_ON_WIFI;
	public final Boolean DEFAULT_DOWLOAD_ONLY_ON_WIFI;

	public final String KEY_INCLUDE_FUTURE_RELEASES;
	public final Boolean DEFAULT_INCLUDE_FUTURE_RELEASES;

	public final String KEY_DOWNLOAD_RELEASES_TIME_PERIOD;
	public final String DEFAULT_DOWNLOAD_RELEASES_TIME_PERIOD;

	public final String KEY_FULL_UPDATE;
	public final Boolean DEFAULT_FULL_UPDATE;

	public final String KEY_REFRESH_PERIOD;
	public final String DEFAULT_REFRESH_PERIOD;

	public final String KEY_ENABLED_RELEASED_TODAY;
	public final Boolean DEFAULT_KEY_ENABLED_RELEASED_TODAY;

	public final String KEY_RELEASED_TODAY_HOUR_OF_DAY;
	public final Integer DEFAULT_KEY_RELEASED_TODAY_HOUR_OF_DAY;

	public final String KEY_RELEASED_TODAY_MINUTE;
	public final Integer DEFAULT_KEY_RELEASED_TODAY_MINUTE;

	private final SharedPreferences sharedPreferences;
	// private static Context context = null;
	private static PreferencesServiceSharedPreferences instance = new PreferencesServiceSharedPreferences();

	/**
	 * Caches the result of {@link #checkAppStart()}. To allow idempotent method
	 * calls.
	 */
	private static AppStart appStart = null;

	private Set<PreferenceChangedListener> preferenceChangedListeners = new HashSet<PreferenceChangedListener>();

	/**
	 * 
	 * @return A singleton of this class
	 */
	public static final PreferencesServiceSharedPreferences getInstance() {
		return instance;
	}

	/**
	 * Creates a {@link PreferencesService} the default shared preferences.
	 */
	protected PreferencesServiceSharedPreferences() {
		// PreferencesServiceSharedPreferences.context = context;
		this.sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(getContext());

		if (sharedPreferences != null) {
			sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		}

		// // Initialize new preferences with defaults from xml
		// PreferenceManager.setDefaultValues(getContext(), R.xml.preferences,
		// false);

		if (getContext() != null) {
			KEY_DOWLOAD_ONLY_ON_WIFI = getContext().getString(
					R.string.preferences_key_download_only_on_wifi);
			DEFAULT_DOWLOAD_ONLY_ON_WIFI = getContext().getResources()
					.getBoolean(
							R.bool.preferences_default_download_only_on_wifi);

			KEY_INCLUDE_FUTURE_RELEASES = getContext().getString(
					R.string.preferences_key_include_future_releases);
			DEFAULT_INCLUDE_FUTURE_RELEASES = getContext().getResources()
					.getBoolean(
							R.bool.preferences_default_include_future_releases);

			KEY_DOWNLOAD_RELEASES_TIME_PERIOD = getContext().getString(
					R.string.preferences_key_download_releases_time_period);
			DEFAULT_DOWNLOAD_RELEASES_TIME_PERIOD = getContext()
					.getResources()
					.getString(
							R.string.preferences_default_download_releases_time_period);

			KEY_FULL_UPDATE = getContext().getString(
					R.string.preferences_key_full_update);
			DEFAULT_FULL_UPDATE = getContext().getResources().getBoolean(
					R.bool.preferences_default_full_update);

			KEY_REFRESH_PERIOD = getContext().getString(
					R.string.preferences_key_refresh_period);
			DEFAULT_REFRESH_PERIOD = getContext().getResources().getString(
					R.string.preferences_default_refresh_period);

			DEFAULT_JUST_ADDED_TIME_PERIOD = parseIntOrThrow(
					KEY_REFRESH_PERIOD, DEFAULT_REFRESH_PERIOD);

			KEY_ENABLED_RELEASED_TODAY = getContext().getString(
					R.string.preferences_key_is_enabled_released_today);
			DEFAULT_KEY_ENABLED_RELEASED_TODAY = getContext()
					.getResources()
					.getBoolean(
							R.bool.preferences_default_is_enabled_released_today);

			KEY_RELEASED_TODAY_HOUR_OF_DAY = getContext().getString(
					R.string.preferences_key_released_today_hour_of_day);
			DEFAULT_KEY_RELEASED_TODAY_HOUR_OF_DAY = parseIntOrThrow(
					KEY_RELEASED_TODAY_HOUR_OF_DAY,
					getContext()
							.getString(
									R.string.preferences_default_released_today_hour_of_day));

			KEY_RELEASED_TODAY_MINUTE = getContext().getString(
					R.string.preferences_key_released_today_minute);
			DEFAULT_KEY_RELEASED_TODAY_MINUTE = parseIntOrThrow(
					KEY_RELEASED_TODAY_MINUTE,
					getContext().getString(
							R.string.preferences_default_released_today_minute));

		} else {
			// e.g. for Testing
			KEY_DOWLOAD_ONLY_ON_WIFI = null;
			DEFAULT_DOWLOAD_ONLY_ON_WIFI = null;

			KEY_INCLUDE_FUTURE_RELEASES = null;
			DEFAULT_INCLUDE_FUTURE_RELEASES = null;

			KEY_DOWNLOAD_RELEASES_TIME_PERIOD = null;
			DEFAULT_DOWNLOAD_RELEASES_TIME_PERIOD = null;

			KEY_FULL_UPDATE = null;
			DEFAULT_FULL_UPDATE = null;

			KEY_REFRESH_PERIOD = null;
			DEFAULT_REFRESH_PERIOD = null;

			DEFAULT_JUST_ADDED_TIME_PERIOD = null;

			KEY_ENABLED_RELEASED_TODAY = null;
			DEFAULT_KEY_ENABLED_RELEASED_TODAY = null;

			KEY_RELEASED_TODAY_HOUR_OF_DAY = null;
			DEFAULT_KEY_RELEASED_TODAY_HOUR_OF_DAY = null;

			KEY_RELEASED_TODAY_MINUTE = null;
			DEFAULT_KEY_RELEASED_TODAY_MINUTE = null;
		}
	}

	private Integer parseIntFromPreferenceOrThrow(String key,
			String defaultValue) {
		String prefValue = sharedPreferences.getString(key, defaultValue);
		return parseIntOrThrow(key, prefValue);
	}

	private Integer parseIntOrThrow(String key, String prefValue) {
		try {
			return Integer.parseInt(prefValue);
		} catch (NumberFormatException e) {
			throw new RuntimeException(
					"Unable to parse integer from property \"" + key
							+ "\", value:" + prefValue, e);
		}
	}

	@Override
	public AppStart checkAppStart() {
		if (appStart == null) {
			PackageInfo pInfo;
			try {
				pInfo = getContext().getPackageManager().getPackageInfo(
						getContext().getPackageName(), 0);
				int lastVersionCode = sharedPreferences.getInt(
						KEY_LAST_APP_VERSION, DEFAULT_LAST_APP_VERSION);
				// String versionName = pInfo.versionName;
				int currentVersionCode = pInfo.versionCode;
				appStart = checkAppStart(currentVersionCode, lastVersionCode);
				// Update version in preferences
				sharedPreferences.edit()
						.putInt(KEY_LAST_APP_VERSION, currentVersionCode)
						.commit();
			} catch (NameNotFoundException e) {
				Log.w(Constants.LOG,
						"Unable to determine current app version from pacakge manager. Defenisvely assuming normal app start.");
			}
		}
		return appStart;
	}

	public AppStart checkAppStart(int currentVersionCode, int lastVersionCode) {
		if (lastVersionCode == -1) {
			return AppStart.FIRST_TIME;
		} else if (lastVersionCode < currentVersionCode) {
			return AppStart.FIRST_TIME_VERSION;
		} else if (lastVersionCode > currentVersionCode) {
			Log.w(Constants.LOG, "Current version code (" + currentVersionCode
					+ ") is less then the one recognized on last startup ("
					+ lastVersionCode
					+ "). Defenisvely assuming normal app start.");
			return AppStart.NORMAL;
		} else {
			return AppStart.NORMAL;
		}
	}

	@Override
	public Date getLastReleaseRefresh() {
		long lastReleaseRefreshMillis = sharedPreferences.getLong(
				KEY_LAST_RELEASES_REFRESH, 0);
		if (lastReleaseRefreshMillis == 0) {
			return DEFAULT_LAST_RELEASES_REFRESH;
		}
		return DateUtil.toDate(lastReleaseRefreshMillis);
	}

	@Override
	public boolean setLastReleaseRefresh(Date date) {
		return sharedPreferences.edit()
				.putLong(KEY_LAST_RELEASES_REFRESH, DateUtil.toLong(date))
				.commit();
	}

	@Override
	public Date getNextReleaseRefresh() {
		long nextReleaseRefreshMillis = sharedPreferences.getLong(
				KEY_NEXT_RELEASES_REFRESH, 0);
		if (nextReleaseRefreshMillis == 0) {
			return DEFAULT_NEXT_RELEASES_REFRESH;
		}
		return DateUtil.toDate(nextReleaseRefreshMillis);
	}

	@Override
	public boolean setNextReleaseRefresh(Date date) {
		return sharedPreferences.edit()
				.putLong(KEY_NEXT_RELEASES_REFRESH, DateUtil.toLong(date))
				.commit();
	}

	@Override
	public boolean isForceFullRefresh() {
		return sharedPreferences.getBoolean(
				KEY_LAST_RELEASES_REFRESH_SUCCESSFULL,
				DEFAULT_LAST_RELEASES_REFRESH_SUCCESSFULL);
	}

	@Override
	public boolean setForceFullRefresh(boolean isSuccessfull) {
		return sharedPreferences
				.edit()
				.putBoolean(KEY_LAST_RELEASES_REFRESH_SUCCESSFULL,
						isSuccessfull).commit();
	}

	@Override
	public void clearPreferences() {
		sharedPreferences.edit().clear().commit();
	}

	@Override
	public boolean isUseOnlyWifi() {
		return sharedPreferences.getBoolean(KEY_DOWLOAD_ONLY_ON_WIFI,
				DEFAULT_DOWLOAD_ONLY_ON_WIFI);
	}

	@Override
	public boolean isIncludeFutureReleases() {
		return sharedPreferences.getBoolean(KEY_INCLUDE_FUTURE_RELEASES,
				DEFAULT_INCLUDE_FUTURE_RELEASES);
	}

	@Override
	public int getDownloadReleasesTimePeriod() {
		return parseIntFromPreferenceOrThrow(KEY_DOWNLOAD_RELEASES_TIME_PERIOD,
				DEFAULT_DOWNLOAD_RELEASES_TIME_PERIOD);
	}

	@Override
	public int getRefreshPeriod() {
		return parseIntFromPreferenceOrThrow(KEY_REFRESH_PERIOD,
				DEFAULT_REFRESH_PERIOD);
	}

	@Override
	public boolean isFullUpdate() {
		return sharedPreferences.getBoolean(KEY_FULL_UPDATE,
				DEFAULT_FULL_UPDATE);
	}

	@Override
	public int getJustAddedTimePeriod() {
		return sharedPreferences.getInt(KEY_JUST_ADDED_TIME_PERIOD,
				DEFAULT_JUST_ADDED_TIME_PERIOD);
	}

	@Override
	public void registerOnSharedPreferenceChangeListener(
			PreferenceChangedListener preferenceChangedListener) {
		preferenceChangedListeners.add(preferenceChangedListener);
	}

	@Override
	public void unregisterOnSharedPreferenceChangeListener(
			PreferenceChangedListener preferenceChangedListener) {
		preferenceChangedListeners.remove(preferenceChangedListener);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		for (PreferenceChangedListener preferenceChangedListener : preferenceChangedListeners) {
			preferenceChangedListener.onPreferenceChanged(key,
					sharedPreferences.getAll().get(key));
		}
	}

	@Override
	public boolean isEnabledConnectivityReceiver() {
		return sharedPreferences.getBoolean(KEY_ENABLED_CONNECTIVITY_RECEIVER,
				DEFAULT_ENABLED_CONNECTIVITY_RECEIVER);
	}

	@Override
	public boolean setEnabledConnectivityReceiver(boolean enabled) {
		return sharedPreferences.edit()
				.putBoolean(KEY_ENABLED_CONNECTIVITY_RECEIVER, enabled)
				.commit();
	}

	protected static Context getContext() {
		return Application.getContext();
	}

	@Override
	public boolean isEnabledReleasedToday() {
		return sharedPreferences.getBoolean(KEY_ENABLED_RELEASED_TODAY,
				DEFAULT_KEY_ENABLED_RELEASED_TODAY);
	}

	@Override
	public int getReleasedTodayScheduleHourOfDay() {
		return sharedPreferences.getInt(KEY_RELEASED_TODAY_HOUR_OF_DAY,
				DEFAULT_KEY_RELEASED_TODAY_HOUR_OF_DAY);
	}

	@Override
	public int getReleasedTodayScheduleMinute() {
		return sharedPreferences.getInt(KEY_RELEASED_TODAY_MINUTE,
				DEFAULT_KEY_RELEASED_TODAY_MINUTE);
	}
}
