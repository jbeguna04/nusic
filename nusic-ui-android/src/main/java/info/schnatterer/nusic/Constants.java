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
package info.schnatterer.nusic;

import android.app.AlarmManager;
import android.app.PendingIntent;

/**
 * Application-wide constants
 */
public interface Constants {
    /**
     * Enum that keeps track of application-wide request codes that are used for
     * setting repeating alarms {@link PendingIntent}s with {@link AlarmManager}
     * . Uses {@link #ordinal()} as numeric ID.
     *
     * @author schnatterer
     *
     */
    enum Alarms {
        NEW_RELEASES, RELEASED_TODAY;
    }

    /**
     * List of that keeps track of application-wide loaders, making sure the IDs
     * are unique.
     *
     * @author schnatterer
     *
     */
    interface Loaders {
        int RELEASE_LOADER_ALL = 0;
        int RELEASE_LOADER_JUST_ADDED = 1;
        int RELEASE_LOADER_ANNOUNCED = 2;
        int RELEASE_LOADER_AVAILABLE = 3;
    }

    /**
     * Name of the logcat appender, as configured in logback.xml
     */
    String FILE_APPENDER_NAME = "file";
}
