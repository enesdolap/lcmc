/*
 * This file is part of LCMC written by Rasto Levrinc.
 *
 * Copyright (C) 2014, Rastislav Levrinc.
 *
 * The LCMC is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * The LCMC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LCMC; see the file COPYING.  If not, write to
 * the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package lcmc.event;

import lcmc.common.domain.Value;
import lcmc.host.domain.Host;

import java.util.List;

public class HwBridgesChangedEvent {
    private final Host host;
    private final List<Value> bridges;

    public Host getHost() {
        return host;
    }

    public List<Value> getBridges() {
        return bridges;
    }

    public HwBridgesChangedEvent(final Host host, final List<Value> bridges) {
        this.host = host;
        this.bridges = bridges;
    }
}
