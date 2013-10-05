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

import info.schnatterer.nusic.Constants;
import info.schnatterer.nusic.R;
import info.schnatterer.nusic.db.DatabaseException;
import info.schnatterer.nusic.db.dao.ArtistDao;
import info.schnatterer.nusic.db.dao.impl.ArtistDaoSqlite;
import info.schnatterer.nusic.db.model.Artist;
import info.schnatterer.nusic.service.ArtistQueryService;
import info.schnatterer.nusic.service.PreferencesService;
import info.schnatterer.nusic.service.PreferencesService.AppStart;
import info.schnatterer.nusic.service.QueryMusicMetadataService;
import info.schnatterer.nusic.service.ReleasesService;
import info.schnatterer.nusic.service.ServiceException;
import info.schnatterer.nusic.service.event.ArtistProgressListener;
import info.schnatterer.nusic.service.event.ProgressListener;
import info.schnatterer.nusic.service.event.ProgressUpdater;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

public class ReleasesServiceImpl implements ReleasesService {
	private Context context;
	private QueryMusicMetadataService queryMusicMetadataService = new QueryMusicMetadataServiceMusicBrainz();
	private ArtistQueryService artistQueryService = new ArtistQueryServiceImpl();
	private PreferencesService preferencesService = PreferencesServiceSharedPreferences
			.getInstance();

	private ArtistDao artistDao = null;

	private Set<ProgressListener<Artist, Boolean>> listenerList = new HashSet<ProgressListener<Artist, Boolean>>();
	private ProgressUpdater<Artist, Boolean> progressUpdater = new ProgressUpdater<Artist, Boolean>(
			listenerList) {
	};

	public ReleasesServiceImpl(Context context) {
		this.context = context;
		if (context != null) {
			this.artistDao = new ArtistDaoSqlite(context);
		}
	}

	@Override
	public boolean isUpdateNeccesarry() {
		AppStart appStart = PreferencesServiceSharedPreferences.getInstance()
				.checkAppStart();

		switch (appStart) {
		case FIRST_TIME_VERSION:
		case FIRST_TIME:
			return true;
		default:
			break;
		}
		/*
		 * TODO check if there are new artists on the device and refresh if
		 * neccessary. Then update interface comment for this method!
		 */
		return false;
	}

	@Override
	public boolean refreshReleases(boolean updateOnlyIfNeccesary) {
		if (updateOnlyIfNeccesary && !isUpdateNeccesarry()) {
			progressUpdater.progressFinished(false);
			return false;
		}
		// TODO write test for logic!
		boolean fullUpdate;
		if (preferencesService.isForceFullRefresh())
			fullUpdate = true;
		else {
			fullUpdate = preferencesService.isFullUpdate();
		}

		Date startDate = createStartDate(fullUpdate,
				preferencesService.getDownloadReleasesTimePeriod(),
				preferencesService.getLastReleaseRefresh());
		Date endDate = createEndDate(preferencesService
				.isIncludeFutureReleases());

		// Use a date before the refresh to store afterwards in order to
		Date dateCreated = new Date();
		refreshReleases(startDate, endDate);
		preferencesService.setLastReleaseRefresh(dateCreated);
		return true;
	}

	private Date createEndDate(boolean includeFutureReleases) {
		if (!includeFutureReleases) {
			return new Date(); // Today
		} else {
			return null;
		}
	}

	private Date createStartDate(boolean isFullUpdate, int months,
			Date lastReleaseRefresh) {
		if (lastReleaseRefresh == null) {
			// Same as full Update
			return createStartDateFullUpdate(months);
		}
		if (isFullUpdate) {
			return createStartDateFullUpdate(months);
		}
		return lastReleaseRefresh;
	}

	private Date createStartDateFullUpdate(int months) {
		if (months <= 0) {
			return null;
		}
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -months);
		return cal.getTime();
	}

	private void refreshReleases(Date startDate, Date endDate) {

		// TODO create service for checking wifi and available internet
		// connection

		Artist[] artists;
		try {
			artists = getArtists();

			progressUpdater.progressStarted(artists.length);
			ServiceException potentialException = null;
			for (int i = 0; i < artists.length; i++) {
				Artist artist = artists[i];
				/*
				 * TODO find out if its more efficient to concat all artist in
				 * one big query and then process it page by page (keep URL
				 * limit of 2048 chars in mind)
				 */
				try {
					queryMusicMetadataService.findReleases(artist, startDate,
							endDate).getReleases();

					// TODO query images from lastfm
					// de.umass.lastfm.Artist artistInfo =
					// de.umass.lastfm.Artist.getInfo(artist.getArtistName(),
					// Constants.LASTFM_API_KEY);

					if (artist.getReleases().size() > 0) {
						artistDao.saveOrUpdate(artist);
						// After saving, release memory for releases

						// for (Release release : artist.getReleases()) {
						// // clear reference from release to artist as well
						// release.setArtist(null);
						// }
						artist.setReleases(null);
					}
					// Release memory for artist
					artists[i] = null;

				} catch (ServiceException e) {
					Log.w(Constants.LOG, e.getMessage(), e.getCause());
					// Allow for displaying errors to the user.
					potentialException = e;
				} catch (DatabaseException databaseException) {
					Log.w(Constants.LOG, databaseException.getMessage(),
							databaseException.getCause());
					progressUpdater
							.progressFailed(
									artist,
									i + 1,
									new ServiceException(
											R.string.ReleasesService_errorPersistingData,
											databaseException), null);
					return;
				} catch (Throwable t) {
					Log.w(Constants.LOG, t);
					progressUpdater.progressFailed(artist, i + 1, t, null);
					preferencesService.setForceFullRefresh(true);
					return;
				}

				progressUpdater.progress(artist, i + 1, potentialException);
			}
			if (potentialException == null) {
				// "Full" Success
				preferencesService.setForceFullRefresh(false);
			} else {
				/*
				 * "Partial" Success, some artists failed. Force a full refresh
				 * next time. Maybe better luck then.
				 */
				preferencesService.setForceFullRefresh(false);
			}
			progressUpdater.progressFinished(true);
			return;
			// } catch (ServiceException e) {
		} catch (Throwable t) {
			Log.w(Constants.LOG, t);
			progressUpdater.progressFailed(null, 0, t, null);
			return;
		}
	}

	protected ContentResolver getContentResolver() {
		return context.getContentResolver();
	}

	@Override
	public void addArtistProcessedListener(ArtistProgressListener l) {
		if (l != null) {
			listenerList.add(l);
		}
	}

	@Override
	public boolean removeArtistProcessedListener(
			ArtistProgressListener artistProcessedListener) {
		if (artistProcessedListener != null) {
			return listenerList.remove(artistProcessedListener);
		} else {
			return false;
		}
	}

	@Override
	public void removeArtistProcessedListeners() {
		listenerList.clear();
	}

	public Artist[] getArtists() throws ServiceException {
		return artistQueryService.getArtists(getContentResolver());
	}

}
