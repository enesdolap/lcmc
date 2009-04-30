/*
 * This file is part of DRBD Management Console by LINBIT HA-Solutions GmbH
 * written by Rasto Levrinc.
 *
 * Copyright (C) 2009, LINBIT HA-Solutions GmbH.
 *
 * DRBD Management Console is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * DRBD Management Console is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with drbd; see the file COPYING.  If not, write to
 * the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package drbd.configs;

import java.util.Arrays;

/**
 * Here are commands for debian verson 4.
 */
public class DistResource_debian_4 extends
            java.util.ListResourceBundle {

    /** Get contents. */
    protected final Object[][] getContents() {
        return Arrays.copyOf(contents, contents.length);
    }

    /** Contents. */
    private static Object[][] contents = {
        /* distribution name that is used in the download url */
        {"distributiondir", "debian-etch"},

        /* support */
        {"Support", "debian-4"},

        {"HbInst.install.text.1", "http://download.opensuse.org repository"},
        {"HbInst.install.1", "echo 'deb http://download.opensuse.org/repositories/server:/ha-clustering/Debian_Etch/ ./' > /etc/apt/sources.list.d/ha-clustering.list "
                             + " && apt-get update"
                             + " && apt-get -y -q  --allow-unauthenticated install -o 'DPkg::Options::force=--force-confnew' heartbeat pacemaker"},

        {"HbInst.install.text.2", "etch repository"},
        {"HbInst.install.version.2", "2.0.7"},
        {"HbInst.install.2", "apt-get update && /usr/bin/apt-get -y -q install -o 'DPkg::Options::force=--force-confnew' heartbeat-2"},
    };
}
