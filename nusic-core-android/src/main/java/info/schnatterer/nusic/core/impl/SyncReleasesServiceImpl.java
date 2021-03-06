/**
 * Copyright (C) 2013 Johannes Schnatterer
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
 *
 * nusic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with nusic.  If not, see <http://www.gnu.org/licenses/>.
 */
package info.schnatterer.nusic.core.impl;

import info.schnatterer.nusic.core.ArtistService;
import info.schnatterer.nusic.core.DeviceMusicService;
import info.schnatterer.nusic.core.PreferencesService;
import info.schnatterer.nusic.core.RemoteMusicDatabaseService;
import info.schnatterer.nusic.core.ServiceException;
import info.schnatterer.nusic.core.SyncReleasesService;
import info.schnatterer.nusic.core.event.ArtistProgressListener;
import info.schnatterer.nusic.core.event.ProgressListener;
import info.schnatterer.nusic.core.event.ProgressUpdater;
import info.schnatterer.nusic.core.i18n.CoreMessageKey;
import info.schnatterer.nusic.data.DatabaseException;
import info.schnatterer.nusic.data.model.Artist;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link SyncReleasesService}.
 *
 * @author schnatterer
 *
 */
public class SyncReleasesServiceImpl implements SyncReleasesService {
    private static final Logger LOG = LoggerFactory
            .getLogger(SyncReleasesServiceImpl.class);

    @Inject
    private RemoteMusicDatabaseService remoteMusicDatabaseService;
    @Inject
    private DeviceMusicService deviceMusicService;
    @Inject
    private PreferencesService preferencesService;
    @Inject
    private ArtistService artistService;
    private Set<ProgressListener<Artist, Boolean>> listenerList = new HashSet<ProgressListener<Artist, Boolean>>();
    private ProgressUpdater<Artist, Boolean> progressUpdater = new ProgressUpdater<Artist, Boolean>(
            listenerList) {
    };

    @Override
    public void syncReleases() {
        Date startDate = createStartDate(preferencesService
                .getDownloadReleasesTimePeriod());

        // Use a date before the refresh to store afterwards in order to
        Date dateCreated = new Date();
        refreshReleases(startDate, null);
        preferencesService.setLastReleaseRefresh(dateCreated);
    }

    private Date createStartDate(int months) {
        if (months <= 0) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -months);
        return cal.getTime();
    }

    private void refreshReleases(Date startDate, Date endDate) {

        // TODO create service for checking wifi and available internet connection

        Artist[] artists = queryArtists();
        if (artists == null) {
            return;
        }

        progressUpdater.progressStarted(artists.length);
        ServiceException potentialException = null;
        for (int i = 0; i < artists.length; i++) {

            try {
                potentialException = processArtist(artists, i, startDate, endDate);

                if (potentialException != null &&
                    potentialException.getCause() instanceof DatabaseException) {
                    // Allow for displaying errors to the user.
                    progressUpdater.progressFailed(artists[i], i + 1,
                        new AndroidServiceException(CoreMessageKey.ERROR_WRITING_TO_DB, potentialException), null);
                    return;
                }
            } catch (Exception e) {
                LOG.warn("Unexpected exception during sync, cancelling sync", e);
                progressUpdater.progressFailed(artists[i], i + 1, e, null);
                return;
            }

            progressUpdater.progress(artists[i], i + 1, potentialException);
        }
        progressUpdater.progressFinished(true);

    }

    /**
     * Finds releases for an artists and stores them. Logs {@link ServiceException}s and propagates
     * them to the {@link #progressUpdater}.
     * @return any {@link ServiceException} that might have occurred, or {@code null} if none occurred
     */
    private ServiceException processArtist(Artist[] artists, int i, Date startDate, Date endDate) {
        Artist artist = artists[i];
        /* TODO find out if its more efficient to concat all artist in one big query and then
         * process it page by page (keep URL limit of 2048 chars in mind) */
        try {
            artist = remoteMusicDatabaseService.findReleases(artist, startDate, endDate);
            if (artist == null) {
                LOG.warn("Artist {} of {} is null.", i, artists.length);
            } else if (artist.getReleases().size() > 0) {
                artistService.saveOrUpdate(artist);
                // After saving, release memory for releases
                artist.setReleases(null);
            }
            // Release memory for artist
            artists[i] = null;

        } catch (ServiceException e) {
            LOG.warn(e.getMessage(), e.getCause());
            return e;
        }
        return null;
    }

    /**
     * Queries artists from device. Logs all errors and propagates to {@link #progressUpdater}.
     *
     * @return a list of artists. Empty list if none found, <code>null</code> on error.
     */
    private Artist[] queryArtists() {
        Artist[] artists = null;
        try {
            artists = deviceMusicService.getArtists();
            if (artists == null) {
                LOG.warn("No artists were returned. No music files on device?");
                artists = new Artist[0];
            }
        } catch (Exception e) {
            LOG.warn("Error querying artists from device", e);
            progressUpdater.progressFailed(null, 0, e, null);
        }

        return artists;
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
}
