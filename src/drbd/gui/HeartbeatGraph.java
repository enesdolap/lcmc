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

package drbd.gui;

import drbd.utilities.Tools;
import drbd.utilities.MyMenuItem;
import drbd.gui.resources.Info;
import drbd.gui.resources.ServiceInfo;
import drbd.gui.resources.GroupInfo;
import drbd.gui.resources.HbConnectionInfo;
import drbd.gui.resources.HostInfo;
import drbd.gui.resources.ConstraintPHInfo;
import drbd.data.Subtext;
import drbd.data.Host;
import drbd.data.ConfigData;
import drbd.data.AccessMode;

import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.Edge;
import edu.uci.ics.jung.graph.ArchetypeVertex;
import edu.uci.ics.jung.graph.ArchetypeEdge;
import edu.uci.ics.jung.graph.impl.SparseVertex;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import edu.uci.ics.jung.visualization.Coordinates;
import edu.uci.ics.jung.visualization.PickedState;
import edu.uci.ics.jung.visualization.VertexShapeFactory;

import edu.uci.ics.jung.utils.Pair;
import java.awt.Shape;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.RoundRectangle2D;
import java.awt.GradientPaint;
import java.awt.BasicStroke;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JPopupMenu;
import javax.swing.JMenu;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import EDU.oswego.cs.dl.util.concurrent.Mutex;

/**
 * This class creates graph and provides methods to add new nodes, edges,
 * remove or modify them.
 *
 * @author Rasto Levrinc
 * @version $Id$
 *
 */
public class HeartbeatGraph extends ResourceGraph {
    /** List with edges that are order constraints. */
    private final List<Edge> edgeIsOrderList = new ArrayList<Edge>();
    /** List with edges that are colocation constraints. */
    private final List<Edge> edgeIsColocationList = new ArrayList<Edge>();
    /** List with vertices that are present. */
    private final List<Vertex> vertexIsPresentList = new ArrayList<Vertex>();
    /** Map from vertex to 'Add service' menu. */
    private final Map<Vertex, JMenu> vertexToAddServiceMap =
                                                new HashMap<Vertex, JMenu>();
    /** Map from vertex to 'Add existing service' menu. */
    private final Map<Vertex, JMenu> vertexToAddExistingServiceMap =
                                                new HashMap<Vertex, JMenu>();
    /** Map from edge to the hb connection info of this constraint. */
    private final Map<Edge, HbConnectionInfo> edgeToHbconnectionMap =
                                new LinkedHashMap<Edge, HbConnectionInfo>();
    /** Map from hb connection info to the edge. */
    private final Map<HbConnectionInfo, Edge> hbconnectionToEdgeMap =
                                new LinkedHashMap<HbConnectionInfo, Edge>();
    /** Pcmk connection lock. */
    private final Mutex mHbConnectionLock = new Mutex();
    /** Map from the vertex to the host. */
    private final Map<Vertex, HostInfo> vertexToHostMap =
                                         new LinkedHashMap<Vertex, HostInfo>();
    /** Map from the host to the vertex. */
    private final Map<Info, Vertex> hostToVertexMap =
                                         new LinkedHashMap<Info, Vertex>();
    /** Map from the vertex to the constraint placeholder. */
    private final Map<Vertex, ConstraintPHInfo> vertexToConstraintPHMap =
                                 new HashMap<Vertex, ConstraintPHInfo>();
    /** Map from the host to the vertex. */
    private final Map<Info, Vertex> constraintPHToVertexMap =
                                         new HashMap<Info, Vertex>();

    /** The first X position of the host. */
    private int hostDefaultXPos = 10;
    /** X position of a new block device. */
    private static final int BD_X_POS = 15;
    /** Y position of the host. */
    private static final int HOST_Y_POS = 40;
    /** Vertical step in pixels by which the hosts are drawn in the graph. */
    private static final int HOST_STEP_X = 230;
    /** Minimum vertical position. */
    private static final int MIN_Y_POS = 20;
    /** Maximum horizontal position. */
    private static final int MAX_X_POS = 2600;
    /** Maximum vertical position. */
    private static final int MAX_Y_POS = 2600;
    /** Height of the vertices. */
    private static final int VERTEX_HEIGHT = 50;
    /** Host standby icon. */
    private static final ImageIcon HOST_STANDBY_ICON =
     Tools.createImageIcon(Tools.getDefault("HeartbeatGraph.HostStandbyIcon"));
    /** Icon that indicates a running service. */
    private static final ImageIcon SERVICE_RUNNING_ICON =
                                     Tools.createImageIcon(Tools.getDefault(
                                       "HeartbeatGraph.ServiceRunningIcon"));
    /** Icon that indicates an unmanaged service. */
    private static final ImageIcon SERVICE_UNMANAGED_ICON =
                                     Tools.createImageIcon(Tools.getDefault(
                                       "HeartbeatGraph.ServiceUnmanagedIcon"));
    /** Icon that indicates a migrated service. */
    private static final ImageIcon SERVICE_MIGRATED_ICON =
                                     Tools.createImageIcon(Tools.getDefault(
                                       "HeartbeatGraph.ServiceMigratedIcon"));
    /** Icon that indicates a not running service. */
    private static final ImageIcon SERVICE_NOT_RUNNING_ICON =
                                Tools.createImageIcon(Tools.getDefault(
                                    "HeartbeatGraph.ServiceNotRunningIcon"));
    /**
     * Prepares a new <code>HeartbeatGraph</code> object.
     */
    public HeartbeatGraph(final ClusterBrowser clusterBrowser) {
        super(clusterBrowser);
    }

    /**
     * Inits the graph.
     */
    protected final void initGraph() {
        super.initGraph(new DirectedSparseGraph());
    }

    /**
     * Returns true if vertex v is one of the ancestors or the same as
     * vertex p.
     */
    private boolean isAncestor(final Vertex v,
                               final Vertex p,
                               final List<Vertex> list) {
        if (p.equals(v)) {
            return true;
        }
        for (final Object pre : p.getPredecessors()) {
            if (list.contains((Vertex) pre)) {
                return false;
            }
            list.add((Vertex) pre);
            if (isAncestor(v, (Vertex) pre, list)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if service exists already in the parent ancestor line
     * or parent already has this service as a child.
     */
    public final boolean existsInThePath(final ServiceInfo si,
                                         final ServiceInfo parent) {
        if (parent == null) {
            return true;
        }
        if (si.equals(parent)) {
            return true;
        }
        final Vertex v = getVertex(si);
        if (v == null) {
            return false;
        }
        final Vertex pv = getVertex(parent);
        if (pv == null) {
            return false;
        }
        if (v.isSuccessorOf(pv) || isAncestor(v, pv, new ArrayList<Vertex>())) {
            return true;
        }
        return false;
    }

    /**
     * Returns heartbeat ids of all services with colocation with this service.
     */
    public final String[] getColocationNeighbours(final ServiceInfo si) {
        final List<String> parentsHbIds = new ArrayList<String>();
        final Vertex v = getVertex(si);
        if (v != null) {
            for (Object pV : v.getPredecessors()) {
                final ServiceInfo psi = (ServiceInfo) getInfo((Vertex) pV);
                parentsHbIds.add(psi.getService().getHeartbeatId());
            }
        }

        return parentsHbIds.toArray(new String [parentsHbIds.size()]);
    }

    /** Returns heartbeat ids from this service info's parents. */
    public final List<ServiceInfo> getParents(final ServiceInfo si) {
        //final List<String> parentsHbIds = new ArrayList<String>();
        final List<ServiceInfo> parents = new ArrayList<ServiceInfo>();
        final Vertex v = getVertex(si);
        if (v != null) {
            for (Object pV : v.getPredecessors()) {
                final ServiceInfo psi = (ServiceInfo) getInfo((Vertex) pV);
                parents.add(psi);
            }
        }
        return parents;
    }

    /** Returns children of the service. */
    public final List<ServiceInfo> getChildren(final ServiceInfo si) {
        final List<ServiceInfo> children = new ArrayList<ServiceInfo>();
        final Vertex v = getVertex(si);
        if (v != null) {
            for (Object pV : v.getSuccessors()) {
                final ServiceInfo psi = (ServiceInfo) getInfo((Vertex) pV);
                children.add(psi);
            }
        }
        return children;
    }

    /** Returns children and parents of the service. */
    public final List<ServiceInfo> getChildrenAndParents(final ServiceInfo si) {
        final List<ServiceInfo> chAndP = new ArrayList<ServiceInfo>();
        chAndP.addAll(getChildren(si));
        chAndP.addAll(getParents(si));
        return chAndP;
    }

    /**
     * Returns all connections from this service.
     */
    public final HbConnectionInfo[] getHbConnections(final ServiceInfo si) {
        final List<HbConnectionInfo> infos = new ArrayList<HbConnectionInfo>();
        final Vertex v = getVertex(si);
        if (v != null) {
            for (Object pV : v.getPredecessors()) {
                MyEdge edge = (MyEdge) ((Vertex) pV).findEdge(v);

                if (edge == null) {
                    edge = (MyEdge) v.findEdge((Vertex) pV);
                }
                if (edge != null) {
                    infos.add(edgeToHbconnectionMap.get(edge));
                }
            }
            for (Object sV : v.getSuccessors()) {
                MyEdge edge = (MyEdge) v.findEdge((Vertex) sV);

                if (edge == null) {
                    edge = (MyEdge) ((Vertex) sV).findEdge(v);
                }
                if (edge != null) {
                    infos.add(edgeToHbconnectionMap.get(edge));
                }
            }
        }
        return infos.toArray(new HbConnectionInfo[infos.size()]);
    }

    /**
     * Returns id that is used for saving of the vertex positions to a file.
     */
    protected final String getId(final Info i) {
        return "hb=" + i.getId();
    }

    /**
     * Exchanges object in vertex, e.g., wenn ra changes to/from m/s resource.
     */
    public final void exchangeObjectInTheVertex(final ServiceInfo newSI,
                                                final ServiceInfo oldSI) {
        final Vertex v = getVertex(oldSI);
        removeVertex(oldSI);
        putInfoToVertex(newSI, v);
        putVertexToInfo(v, (Info) newSI);
        somethingChanged();
    }

    /**
     * adds resource/service to the graph. Adds also edge from the parent.
     * If vertex exists add only the edge. If parent is null add only vertex
     * without edge. Return true if vertex existed in the graph before.
     */
    public final boolean addResource(final ServiceInfo serviceInfo,
                                     final ServiceInfo parent,
                                     final Point2D pos,
                                     final boolean colocationOnly,
                                     final boolean orderOnly,
                                     final boolean testOnly) {
        //vv.stop();
        boolean vertexExists = true;
        Vertex v = getVertex(serviceInfo);
        if (v == null) {
            final SparseVertex sv = new SparseVertex();
            if (pos == null) {
                final Point2D newPos = getSavedPosition(serviceInfo);
                if (newPos == null) {
                    final Point2D.Float max = getFilledGraphSize();
                    final float maxYPos = (float) max.getY();
                    getVertexLocations().setLocation(
                                            sv,
                                            new Point2D.Float(BD_X_POS,
                                                              maxYPos + 40));
                } else {
                    getVertexLocations().setLocation(sv, newPos);
                }
            } else {
                getVertexLocations().setLocation(sv, pos);
            }

            v = getGraph().addVertex(sv);
            somethingChanged();
            putInfoToVertex(serviceInfo, v);
            putVertexToInfo(v, (Info) serviceInfo);
            vertexExists = false;
        } else if (testOnly) {
            addTestEdge(getVertex(parent), getVertex(serviceInfo));
        }

        if (parent != null && !testOnly) {
            if (!orderOnly) {
                addColocation(null, serviceInfo, parent);
            }
            if (!colocationOnly) {
                addOrder(null, parent, serviceInfo);
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                repaint();
            }
        });
        return vertexExists;
    }

    /**
     * Adds order constraint from parent to the service.
     */
    public final void addOrder(final String ordId,
                               final ServiceInfo parent,
                               final ServiceInfo serviceInfo) {
        if (parent == null || serviceInfo == null) {
            return;
        }
        Vertex vP = getVertex(parent);
        Vertex v = getVertex(serviceInfo);
        if (parent.isConstraintPH() || serviceInfo.isConstraintPH()) {
            /* if it is sequential rsc set change it to show to the resource
             * instead on placeholder. */

            final ConstraintPHInfo cphi;
            if (parent.isConstraintPH()) {
                cphi = (ConstraintPHInfo) parent;
                final ServiceInfo si = cphi.prevInSequence(serviceInfo, false);
                if (si != null) {
                    vP = getVertex(si);
                }
            } else {
                cphi = (ConstraintPHInfo) serviceInfo;
                final ServiceInfo si = cphi.nextInSequence(parent, false);
                if (si != null) {
                    v = getVertex(si);
                }
            }
        }
        if (v == null || vP == null) {
            return;
        }

        //ServiceInfo colRsc = null;
        //ServiceInfo colWithRsc = null;
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        MyEdge edge = null;
        try {
            edge = (MyEdge) vP.findEdge(v);

            if (edge == null) {
                edge = (MyEdge) v.findEdge(vP);
                if (edge != null) {
                    edge.reverse();
                }
            } else {
                edge.reset();
            }
        } catch (final Exception e) {
            /* ignore */
        }
        HbConnectionInfo hbci;
        if (edge == null) {
            hbci = getClusterBrowser().getNewHbConnectionInfo();
            edge = (MyEdge) getGraph().addEdge(new MyEdge(vP, v));
            edgeToHbconnectionMap.put(edge, hbci);
            hbconnectionToEdgeMap.put(hbci, edge);
        } else {
            hbci = edgeToHbconnectionMap.get(edge);
        }
        mHbConnectionLock.release();
        if (hbci != null) {
            hbci.addOrder(ordId, parent, serviceInfo);
            if (!edgeIsOrderList.contains(edge)) {
                edgeIsOrderList.add(edge);
            }
        }
    }

    /**
     * Reverse the edge.
     */
    public final void reverse(final HbConnectionInfo hbConnectionInfo) {
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge e = hbconnectionToEdgeMap.get(hbConnectionInfo);
        mHbConnectionLock.release();
        if (e != null && edgeIsColocationList.contains(e)) {
            ((MyEdge) e).reverse();
        }
    }

    /** Returns whether these two services are colocated. */
    public final boolean isColocation(final ServiceInfo parent,
                                      final ServiceInfo serviceInfo) {
        final Vertex vP = getVertex(parent);
        final Vertex v = getVertex(serviceInfo);
        if (vP == null || v == null) {
            return false;
        }

        MyEdge edge = (MyEdge) vP.findEdge(v);
        if (edge == null) {
            edge = (MyEdge) v.findEdge(vP);
        }
        if (edge != null) {
            final HbConnectionInfo hbci = edgeToHbconnectionMap.get(edge);
            if (edgeIsColocationList.contains(edge)) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether these two services are ordered. */
    public final boolean isOrder(final ServiceInfo parent,
                                 final ServiceInfo serviceInfo) {
        final Vertex vP = getVertex(parent);
        final Vertex v = getVertex(serviceInfo);
        if (vP == null || v == null) {
            return false;
        }

        MyEdge edge = (MyEdge) vP.findEdge(v);
        if (edge == null) {
            edge = (MyEdge) v.findEdge(vP);
        }
        if (edge != null) {
            final HbConnectionInfo hbci = edgeToHbconnectionMap.get(edge);
            if (edgeIsOrderList.contains(edge)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds colocation constraint from parent to the service.
     */
    public final void addColocation(final String colId,
                                    final ServiceInfo rsc,
                                    final ServiceInfo withRsc) {
        if (rsc == null || withRsc == null) {
            return;
        }
        Vertex vRsc = getVertex(rsc);
        Vertex vWithRsc = getVertex(withRsc);
        if (rsc.isConstraintPH() || withRsc.isConstraintPH()) {
            /* if it is sequential rsc set change it to show to the resource
             * instead on placeholder. */

            final ConstraintPHInfo cphi;
            if (rsc.isConstraintPH()) {
                cphi = (ConstraintPHInfo) rsc;
                final ServiceInfo si = cphi.nextInSequence(withRsc,
                                                           true);
                if (si != null) {
                    vRsc = getVertex(si);
                }
            } else {
                cphi = (ConstraintPHInfo) withRsc;
                final ServiceInfo si = cphi.prevInSequence(rsc,
                                                           true);
                if (si != null) {
                    vWithRsc = getVertex(si);
                }
            }
        }
        if (vWithRsc == null || vRsc == null) {
            return;
        }
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        MyEdge edge = null;
        try {
            edge = (MyEdge) vWithRsc.findEdge(vRsc);
            if (edge == null) {
                edge = (MyEdge) vRsc.findEdge(vWithRsc);
            }
        } catch (final Exception e) {
            /* ignore */
        }
        HbConnectionInfo hbci;
        if (edge == null) {
            hbci = getClusterBrowser().getNewHbConnectionInfo();
            edge = (MyEdge) getGraph().addEdge(new MyEdge(vWithRsc, vRsc));
            edgeToHbconnectionMap.put(edge, hbci);
            hbconnectionToEdgeMap.put(hbci, edge);
        } else {
            hbci = edgeToHbconnectionMap.get(edge);
        }
        mHbConnectionLock.release();
        if (hbci != null) {
            hbci.addColocation(colId, rsc, withRsc);
            if (!edgeIsColocationList.contains(edge)) {
                edgeIsColocationList.add(edge);
            }
        }
    }

    /** Removes items from order list. */
    public final void clearOrderList() {
        for (final Object edge : getGraph().getEdges()) {
            final HbConnectionInfo hbci =
                                    edgeToHbconnectionMap.get((Edge) edge);
            if (hbci != null && !hbci.isNew()) {
                /* don't remove the new ones. */
                edgeIsOrderList.remove(edge);
            }
        }
    }

    /** Removes items from colocation list. */
    public final void clearColocationList() {
        for (final Object edge : getGraph().getEdges()) {
            final HbConnectionInfo hbci =
                                    edgeToHbconnectionMap.get((Edge) edge);
            if (hbci != null && !hbci.isNew()) {
                /* don't remove the new ones. */
                edgeIsColocationList.remove(edge);
            }
        }
    }

    /**
     * Removes all elements in vertexIsPresentList.
     */
    public final void clearVertexIsPresentList() {
        vertexIsPresentList.clear();
    }

    /**
     * Returns label for service vertex.
     */
    protected final String getMainText(final ArchetypeVertex v,
                                       final boolean testOnly) {
        String str;
        if (vertexToHostMap.containsKey(v)) {
            str = vertexToHostMap.get(v).toString();
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            str = vertexToConstraintPHMap.get(v).getMainTextForGraph();
        } else {
            final ServiceInfo si = (ServiceInfo) getInfo((Vertex) v);
            if (si == null) {
                return "";
            }
            if (si.getService().isRemoved()) {
                str = Tools.getString("HeartbeatGraph.Removing");
            } else if (vertexIsPresentList.contains(v)) {
                str = si.getMainTextForGraph();
            } else {
                if (si.getService().isNew()) {
                    str = si.getMainTextForGraph();
                } else {
                    str = Tools.getString("HeartbeatGraph.Unconfigured");
                }
            }
        }
        return str;
    }

    /** Returns shape of the service vertex. */
    protected final Shape getVertexShape(final Vertex v,
                                   final VertexShapeFactory factory) {
        if (vertexToHostMap.containsKey(v)) {
            return factory.getRectangle(v);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return factory.getEllipse(v);
        } else {
            final RoundRectangle2D r = factory.getRoundRectangle(v);

            return new RoundRectangle2D.Double(r.getX(),
                                        r.getY(),
                                        r.getWidth(),
                                        r.getHeight(),
                                        20,
                                        20);
        }
    }

    /**
     * Picks info.
     */
    public final void pickInfo(final Info i) {
        final GroupInfo groupInfo = ((ServiceInfo) i).getGroupInfo();
        if (groupInfo == null) {
            super.pickInfo(i);
        } else {
            /* group is picked in the graph, if group service was selected. */
            final Vertex v = getVertex(groupInfo);
            final PickedState ps = getVisualizationViewer().getPickedState();
            ps.clearPickedEdges();
            ps.clearPickedVertices();
            ps.pick(v, true);
        }
    }

    /** Handles right click on the service vertex and creates popup menu. */
    protected final JPopupMenu handlePopupVertex(final Vertex v,
                                                 final Point2D p) {
        if (vertexToHostMap.containsKey(v)) {
            final HostInfo hi = (HostInfo) getInfo(v);
            return hi.getPopup(p);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            final ConstraintPHInfo cphi = vertexToConstraintPHMap.get(v);
            return cphi.getPopup(p);
        } else {
            final ServiceInfo si = (ServiceInfo) getInfo(v);
            return si.getPopup(p);
        }
    }

    /**
     * Recreates the add existing service popup, since it can change in
     * very unlikely event.
     */
    private void reloadAddExistingServicePopup(final JMenu addServiceMenuItem,
                                               final Vertex v) {
        if (addServiceMenuItem == null) {
            return;
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        addServiceMenuItem.removeAll();
        boolean separatorAdded = false;
        final boolean tOnly = isTestOnly();
        for (final ServiceInfo asi
                            : getClusterBrowser().getExistingServiceList(si)) {
            final MyMenuItem mmi = new MyMenuItem(
                          asi.toString(),
                          null,
                          null,
                          new AccessMode(ConfigData.AccessType.ADMIN, false),
                          new AccessMode(ConfigData.AccessType.OP, false)) {
                private static final long serialVersionUID = 1L;
                public void action() {
                    si.addServicePanel(asi,
                                       null,
                                       false, /* TODO: colocation only */
                                       false, /* order only */
                                       true,
                                       getClusterBrowser().getDCHost(),
                                       tOnly);
                    repaint();
                }
            };
            if (asi.getStringValue().equals("Filesystem")
                || asi.getStringValue().equals("IPaddr2")) {

                mmi.setSpecialFont();
                addServiceMenuItem.add(mmi);
            } else {
                if (!separatorAdded) {
                    addServiceMenuItem.addSeparator();
                    separatorAdded = true;
                }
                addServiceMenuItem.add(mmi);
            }
        }
    }

    /**
     * Handles right click on the edge and creates popup menu.
     */
    protected final JPopupMenu handlePopupEdge(final Edge edge) {
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get(edge);
        return hbci.getPopup();
    }

    /**
     * Handles right click on the background and creates popup menu.
     */
    protected final JPopupMenu handlePopupBackground(final Point2D pos) {
        return getClusterBrowser().getServicesInfo().getPopup(pos);
    }

    /**
     * After service vertex v was released in position pos, set its location
     * there, so that it doesn't jump back. If position is outside of the view,
     * bring the vertex back in the view.
     */
    protected final void vertexReleased(final Vertex v, final Point2D pos) {
        double x = pos.getX();
        double y = pos.getY();
        final double minPos = (getVertexWidth(v)
                               - getDefaultVertexWidth(v)) / 2;
        x = x < minPos ? minPos : x;
        x = x > MAX_X_POS ? MAX_X_POS : x;
        y = y < MIN_Y_POS ? MIN_Y_POS : y;
        y = y > MAX_Y_POS ? MAX_Y_POS : y;
        final Coordinates c = getLayout().getCoordinates(v);
        c.setX(x);
        c.setY(y);
        pos.setLocation(x, y);
        getVertexLocations().setLocation(v, pos);
    }

    /**
     * Selects Info in the view if a service vertex was pressed. If more
     * vertices were selected, it does not do anything.
     */
    protected final void oneVertexPressed(final Vertex v) {
        pickVertex(v);
        if (vertexToHostMap.containsKey(v)) {
            final HostInfo hi = vertexToHostMap.get(v);
            if (hi != null) {
                getClusterBrowser().setRightComponentInView(hi);
            }
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            final ConstraintPHInfo cphi = vertexToConstraintPHMap.get(v);
            if (cphi != null) {
                getClusterBrowser().setRightComponentInView(cphi);
            }
        } else {
            final ServiceInfo si = (ServiceInfo) getInfo(v);
            if (si != null) {
                si.selectMyself();
            }
        }
    }

    /**
     * Is called, when background of the graph is clicked. It deselects
     * selected node.
     */
    protected final void backgroundClicked() {
        getClusterBrowser().getServicesInfo().selectMyself();
    }

    /**
     * Returns tool tip when mouse is over a service vertex.
     */
    public final String getVertexToolTip(final Vertex v) {
        final boolean tOnly = isTestOnly();

        if (vertexToHostMap.containsKey(v)) {
            return vertexToHostMap.get(v).getToolTipForGraph(tOnly);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return vertexToConstraintPHMap.get(v).getToolTipForGraph(tOnly);
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        return si.getToolTipText(tOnly);
    }

    /**
     * Returns the tool tip for the edge.
     */
    public final String getEdgeToolTip(final Edge edge) {
        // TODO: move this to the clusterbrowser
        final Pair p = edge.getEndpoints();
        final boolean edgeIsOrder = edgeIsOrderList.contains(edge);
        final boolean edgeIsColocation = edgeIsColocationList.contains(edge);

        final Vertex v = (Vertex) p.getSecond();
        final Vertex parent = (Vertex) p.getFirst();
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        final ServiceInfo siP = (ServiceInfo) getInfo(parent);
        final StringBuffer s = new StringBuffer(100);
        s.append(siP.toString());
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get(edge);
        if (edgeIsOrder && !hbci.isOrdScoreNull(null, null)) {
            s.append(" is started before ");
        } else {
            s.append(" and ");
        }
        s.append(si.toString());
        if (!edgeIsOrder) {
            s.append(" are located");
        }
        if (edgeIsColocation) {
            final HbConnectionInfo.ColScoreType colSType =
             edgeToHbconnectionMap.get(edge).getColocationScoreType(null, null);
            if (colSType == HbConnectionInfo.ColScoreType.NEGATIVE
                || colSType == HbConnectionInfo.ColScoreType.MINUS_INFINITY) {
                s.append(" not on the same host");
            } else {
                s.append(" on the same host");
            }
        } else {
            s.append(" not necessarily on the same host");
        }

        return s.toString();
    }

    /**
     * Returns color of the vertex, depending if the service is configured,
     * removed or color of the host.
     */
    protected final Color getVertexFillColor(final Vertex v) {
        final boolean tOnly = isTestOnly();
        if (vertexToHostMap.containsKey(v)) {
            return vertexToHostMap.get(v).getHost().getPmColors()[0];
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            if (vertexToConstraintPHMap.get(v).getService().isNew()) {
                return Tools.getDefaultColor(
                                       "HeartbeatGraph.FillPaintUnconfigured");
            } else {
                // TODO fillpaint.placeholder
                return Tools.getDefaultColor(
                                        "HeartbeatGraph.FillPaintPlaceHolder");
            }
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        if (si == null) {
            return null;
        }
        if (getClusterBrowser().allHostsDown()) {
            return Tools.getDefaultColor("HeartbeatGraph.FillPaintUnknown");
        } else if (si.getService().isOrphaned()) {
            return Tools.getDefaultColor("HeartbeatGraph.FillPaintUnknown");
        } else if (si.isFailed(tOnly)) {
            return Tools.getDefaultColor("HeartbeatGraph.FillPaintFailed");
        } else if (!si.isRunning(tOnly)) {
            return ClusterBrowser.FILL_PAINT_STOPPED;
        } else if (getClusterBrowser().clStatusFailed()) {
            return Tools.getDefaultColor("HeartbeatGraph.FillPaintUnknown");
        } else if (vertexIsPresentList.contains(v) || tOnly) {
            final List<Color> colors = si.getHostColors(tOnly);
            if (colors.size() >= 1) {
                return colors.get(0);
            } else {
                return Color.WHITE; /* more colors */
            }
        } else if (!si.getService().isNew()) {
            return Tools.getDefaultColor("HeartbeatGraph.FillPaintRemoved");
        } else {
            return Tools.getDefaultColor(
                                      "HeartbeatGraph.FillPaintUnconfigured");
        }
    }

    /** Returns label that describes the edge. */
    protected final String getLabelForEdgeStringer(final ArchetypeEdge e) {
        if (isTestEdge((Edge) e)) {
            return "ptest...";
        }
        final boolean edgeIsOrder = edgeIsOrderList.contains((Edge) e);
        final boolean edgeIsColocation =
                                    edgeIsColocationList.contains((Edge) e);
        final Pair p = ((Edge) e).getEndpoints();
        final ServiceInfo s1 = (ServiceInfo) getInfo((Vertex) p.getSecond());
        final ServiceInfo s2 = (ServiceInfo) getInfo((Vertex) p.getFirst());
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get((Edge) e);
        if (hbci == null) {
            return "";
        }
        String colDesc = null;
        String ordDesc = null;
        String desc = null;
        String leftArrow = "\u2190 "; /* <- */
        String rightArrow = " \u2192"; /* -> */
        final HbConnectionInfo.ColScoreType colSType =
                                    hbci.getColocationScoreType(null, null);
        if (edgeIsColocation) {
            if (hbci.isColocationTwoDirections()) {
                leftArrow = "\u21AE "; /* </> */
                rightArrow = "\u21AE "; /* </> */
            } else if (colSType == HbConnectionInfo.ColScoreType.NEGATIVE
                || colSType == HbConnectionInfo.ColScoreType.MINUS_INFINITY) {
                leftArrow = "\u2194 "; /* <-> */
                rightArrow = "\u2194 "; /* <-> */
            } else if (colSType == HbConnectionInfo.ColScoreType.IS_NULL) {
                leftArrow = "\u21E0 "; /* < - - */
                rightArrow = " \u21E2"; /* - - > */
            } else if (colSType == HbConnectionInfo.ColScoreType.MIXED) {
                leftArrow = "\u219A "; /* </- */
                rightArrow = " \u219B"; /* -/> */
            }
        }
        if (edgeIsOrder && edgeIsColocation) {
            if (colSType == HbConnectionInfo.ColScoreType.NEGATIVE
                || colSType == HbConnectionInfo.ColScoreType.MINUS_INFINITY) {
                colDesc = "repelled";
            } else {
                colDesc = "col";
            }
            ordDesc = "ord";
        } else if (edgeIsOrder) {
            ordDesc = "ordered";
        } else if (edgeIsColocation) {
            if (colSType == HbConnectionInfo.ColScoreType.NEGATIVE
                || colSType == HbConnectionInfo.ColScoreType.MINUS_INFINITY) {
                colDesc = "repelled";
                leftArrow = "\u2194 "; /* <-> */
                rightArrow = "\u2194 "; /* <-> */
            } else {
                colDesc = "colocated";
            }
        } else if (s1.getService().isNew() || s2.getService().isNew()) {
            desc = Tools.getString("HeartbeatGraph.Unconfigured");
        } else {
            desc = Tools.getString("HeartbeatGraph.Removing");
        }
        if (desc != null) {
            return desc;
        }
        final Vertex v1 = (Vertex) p.getSecond();
        final Vertex v2 = (Vertex) p.getFirst();
        double s1X = 0;
        double s2X = 0;
        final Point2D loc1 = getVertexLocations().getLocation(v1);
        if (loc1 != null) {
            s1X = loc1.getX();
        }
        final Point2D loc2 = getVertexLocations().getLocation(v2);
        if (loc2 != null) {
            s2X = loc2.getX();
        }
        final StringBuffer sb = new StringBuffer(15);
        if (edgeIsColocation) {
            final boolean left = hbci.isWithRsc(s2);
            if (s1X >= s2X) {
                if (left) {
                    sb.append(leftArrow); /* <- */
                    sb.append(colDesc);
                    if (edgeIsOrder) {
                        sb.append('/');
                    }
                }
                if (edgeIsOrder) {
                    sb.append(ordDesc);
                }
                if (!left) {
                    if (edgeIsOrder) {
                        sb.append('/');
                    }
                    sb.append(colDesc);
                    sb.append(rightArrow); /* -> */
                }
            } else {
                if (!left) {
                    sb.append(leftArrow); /* <- */
                    sb.append(colDesc);
                    if (edgeIsOrder) {
                        sb.append('/');
                    }
                }
                if (edgeIsOrder) {
                    sb.append(ordDesc);
                }
                if (left) {
                    if (edgeIsOrder) {
                        sb.append('/');
                    }
                    sb.append(colDesc);
                    sb.append(rightArrow); /* -> */
                }
            }
        } else if (edgeIsOrder) {
            sb.append(ordDesc);
        }
        return sb.toString();
    }

    /** Returns paint of the edge. */
    protected final Paint getEdgeDrawPaint(final Edge e) {
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
        if (hbci != null && hbci.isNew()) {
            return Tools.getDefaultColor("ResourceGraph.EdgeDrawPaintNew");
        } else if (edgeIsOrderList.contains(e)
                   && edgeIsColocationList.contains(e)) {
            return super.getEdgeDrawPaint(e);
        } else if (edgeIsOrderList.contains(e)
                   || edgeIsColocationList.contains(e)) {
            return Tools.getDefaultColor("ResourceGraph.EdgeDrawPaintBrighter");
        } else {
            return Tools.getDefaultColor("ResourceGraph.EdgeDrawPaintRemoved");
        }
    }

    /** Returns paint of the picked edge. */
    protected final Paint getEdgePickedPaint(final Edge e) {
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
        if (hbci != null && hbci.isNew()) {
            return Tools.getDefaultColor("ResourceGraph.EdgePickedPaintNew");
        } else if (edgeIsOrderList.contains(e)
                   && edgeIsColocationList.contains(e)) {
            return super.getEdgePickedPaint(e);
        } else if (edgeIsOrderList.contains(e)
                   || edgeIsColocationList.contains(e)) {
            return Tools.getDefaultColor(
                                    "ResourceGraph.EdgePickedPaintBrighter");
        } else {
            return Tools.getDefaultColor(
                                    "ResourceGraph.EdgePickedPaintRemoved");
        }
    }

    /**
     * Remove edges that were marked as not present, meaning there are no
     * colocation nor order constraints.
     */
    public final void killRemovedEdges() {
        final List<Edge> edges =
                            new ArrayList<Edge>(getGraph().getEdges().size());
        for (Object e : getGraph().getEdges()) {
            edges.add((Edge) e);
        }

        final boolean tOnly = isTestOnly();
        for (int i = 0; i < edges.size(); i++) {
            final Edge e = edges.get(i);
            final Pair p = e.getEndpoints();
            final ServiceInfo s1 =
                            (ServiceInfo) getInfo((Vertex) p.getSecond());
            final ServiceInfo s2 =
                            (ServiceInfo) getInfo((Vertex) p.getFirst());
            if (s1 == null
                || (s1.getService().isNew() && !s1.getService().isRemoved())
                || s2 == null
                || (s2.getService().isNew() && !s2.getService().isRemoved())) {
                continue;
            }
            if (!edgeIsOrderList.contains(e)) {
                final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
                if (hbci != null) {
                    hbci.removeOrders();
                }
            }

            if (!edgeIsColocationList.contains(e)) {
                final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
                if (hbci != null) {
                    hbci.removeColocations();
                }
            }
            removeEdge(e, false);
        }
    }

    /** Removes edge if it is not in the list of constraints. */
    private void removeEdge(final Edge e, final boolean testOnly) {
        if (!edgeIsOrderList.contains(e)
            && !edgeIsColocationList.contains(e)) {
            ((MyEdge) e).reset();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        getGraph().removeEdge(e);
                    } catch (final Exception ignore) {
                        /* ignore */
                    }
                }
            });
            try {
                mHbConnectionLock.acquire();
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
            edgeToHbconnectionMap.remove(e);
            if (hbci != null) {
                hbconnectionToEdgeMap.remove(hbci);
                hbci.removeMyself(testOnly);
            }
            mHbConnectionLock.release();
        }
    }

    /** Remove vertices that were marked as not present. */
    public final void killRemovedVertices() {
        /* Make copy first. */
        final List<Vertex> vertices = new ArrayList<Vertex>();
        for (final Object vo : getGraph().getVertices()) {
            vertices.add((Vertex) vo);
        }
        for (final Vertex v : vertices) {
            if (vertexToHostMap.containsKey(v)) {
                continue;
            } else if (!v.getInEdges().isEmpty()
                || !v.getOutEdges().isEmpty()) {
                continue;
            } else if (vertexToConstraintPHMap.containsKey(v)) {
                final ConstraintPHInfo cphi = (ConstraintPHInfo) getInfo(v);
                if (!vertexIsPresentList.contains(v)) {
                    cphi.setUpdated(false);
                    if (!getClusterBrowser().clStatusFailed()
                        && cphi.getService().isRemoved()) {
                        getVertexLocations().setLocation(v, null);
                        getGraph().removeVertex(v);
                        removeInfo(v);
                        removeVertex(cphi);
                        cphi.removeInfo();
                        getVertexToMenus().remove(v);
                        vertexToConstraintPHMap.remove(v);
                        constraintPHToVertexMap.remove(cphi);
                        somethingChanged();
                    } else {
                        cphi.getService().setNew(true);
                    }
                }
                cphi.resetRscSetConnectionData();
            } else {
                if (!vertexIsPresentList.contains(v)) {
                    final ServiceInfo si = (ServiceInfo) getInfo(v);
                    if (si != null
                        && !si.getService().isNew()
                        && !getClusterBrowser().clStatusFailed()) {

                        si.setUpdated(false);
                        getVertexLocations().setLocation(v, null);
                        //SwingUtilities.invokeLater(new Runnable() {
                        //    public void run() {
                                getGraph().removeVertex(v);
                        //    }
                        //});
                        removeInfo(v);
                        removeVertex(si);
                        si.removeInfo();
                        //TODO: unregister removePopup(v);
                        getVertexToMenus().remove(v);
                        vertexToAddServiceMap.remove(v);
                        vertexToAddExistingServiceMap.remove(v);
                        //TODO: positions are still there
                        somethingChanged();
                    }
                }
            }
        }
    }

    /** Mark vertex and its edges to be removed later. */
    protected final void removeInfo(final Info i) {
        final Vertex v = getVertex(i);
        /* remove edges */

        final Set inEdges = v.getInEdges();
        final Set outEdges = v.getOutEdges();

        for (final Object e : inEdges) {
            edgeIsOrderList.remove((Edge) e);
            edgeIsColocationList.remove((Edge) e);
        }

        for (final Object e : outEdges) {
            edgeIsOrderList.remove((Edge) e);
            edgeIsColocationList.remove((Edge) e);
        }

        /* remove vertex */
        vertexIsPresentList.remove(v);
        ((ServiceInfo) i).getService().setNew(false);
        resetSavedPosition(i);
        getVertexLocations().reset();
    }

    /** Set vertex as present. */
    public final void setVertexIsPresent(final ServiceInfo si) {
        final Vertex v = getVertex(si);
        if (v == null) {
            //Tools.Toolsrror("no vertex associated with service info");
            /* group vertices */
            return;
        }
        vertexIsPresentList.add(v);
    }

    /** Returns an icon for the vertex. */
    protected final List<ImageIcon> getIconsForVertex(final ArchetypeVertex v,
                                                      final boolean testOnly) {
        final List<ImageIcon> icons = new ArrayList<ImageIcon>();
        final HostInfo hi = vertexToHostMap.get(v);
        if (hi != null) {
            if (hi.getHost().isClStatus()) {
                icons.add(HostBrowser.HOST_ON_ICON_LARGE);
            } else {
                icons.add(HostBrowser.HOST_ICON_LARGE);
            }
            if (hi.isStandby(testOnly)) {
                icons.add(HOST_STANDBY_ICON);
            }
            return icons;
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return null;
        }

        final ServiceInfo si = (ServiceInfo) getInfo((Vertex) v);
        if (si == null) {
            return null;
        }
        if (si.isStopped(testOnly) || getClusterBrowser().allHostsDown()) {
            icons.add(SERVICE_NOT_RUNNING_ICON);
        } else {
            icons.add(SERVICE_RUNNING_ICON);
        }
        if (!si.isManaged(testOnly) || si.getService().isOrphaned()) {
            icons.add(SERVICE_UNMANAGED_ICON);
        }
        if (si.getMigratedTo(testOnly) != null
            || si.getMigratedFrom(testOnly) != null) {
            icons.add(SERVICE_MIGRATED_ICON);
        }
        return icons;
    }

    /** Returns whether to show an edge arrow. */
    protected final boolean showEdgeArrow(final Edge e) {
        if (edgeIsOrderList.contains(e)) {
            final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
            return hbci != null && !hbci.isOrderTwoDirections();
        }
        return false;
    }

    /** Returns whether to show a hollow arrow. */
    protected final boolean showHollowArrow(final Edge e) {
        if (edgeIsOrderList.contains(e)) {
            final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
            return hbci != null && hbci.isOrdScoreNull(null, null);
        }
        return false;
    }

    /** Reloads popup menus for all services. */
    public final void reloadServiceMenus() {
        for (final Object v : getGraph().getVertices()) {
            final JMenu existingServiceMenuItem =
                                vertexToAddExistingServiceMap.get((Vertex) v);
            reloadAddExistingServicePopup(existingServiceMenuItem,
                                          (Vertex) v);
        }
    }

    /**
     * Is called after an edge was pressed.
     */
    protected final void oneEdgePressed(final Edge e) {
        final HbConnectionInfo hbci = edgeToHbconnectionMap.get(e);
        getClusterBrowser().setRightComponentInView(hbci);
    }

    /**
     * Removes the connection, the order, the colocation or both.
     */
    public final void removeConnection(final HbConnectionInfo hbci,
                                       final Host dcHost,
                                       final boolean testOnly) {
        final ServiceInfo siP = hbci.getLastServiceInfoParent();
        final ServiceInfo siC = hbci.getLastServiceInfoChild();
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge edge = hbconnectionToEdgeMap.get(hbci);
        mHbConnectionLock.release();
        if (edgeIsOrderList.contains(edge)) {
            siC.removeOrder(siP, dcHost, testOnly);
        }
        final ServiceInfo siRsc = hbci.getLastServiceInfoRsc();
        final ServiceInfo siWithRsc = hbci.getLastServiceInfoWithRsc();
        if (edgeIsColocationList.contains(edge)) {
            if (!testOnly) {
                edgeIsOrderList.remove(edge);
                edgeIsColocationList.remove(edge);
            }
            siRsc.removeColocation(siWithRsc, dcHost, testOnly);
        } else {
            if (!testOnly) {
                edgeIsOrderList.remove(edge);
                edgeIsColocationList.remove(edge);
            }
        }
        if (testOnly) {
            addExistingTestEdge(edge);
        } else {
            if (hbci.isNew()) {
                removeEdge(edge, testOnly);
            }
        }
    }

    /**
     * Removes order.
     */
    public final void removeOrder(final HbConnectionInfo hbci,
                                  final Host dcHost,
                                  final boolean testOnly) {
        if (hbci == null) {
            return;
        }
        final ServiceInfo siP = hbci.getLastServiceInfoParent();
        final ServiceInfo siC = hbci.getLastServiceInfoChild();
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge edge = hbconnectionToEdgeMap.get(hbci);
        mHbConnectionLock.release();
        if (edgeIsOrderList.contains(edge)) {
            if (!testOnly) {
                edgeIsOrderList.remove(edge);
            }
            siC.removeOrder(siP, dcHost, testOnly);
        }
        if (testOnly) {
            addExistingTestEdge(edge);
        } else {
            if (hbci.isNew()) {
                removeEdge(edge, testOnly);
            }
        }
    }

    /**
     * Adds order.
     */
    public final void addOrder(final HbConnectionInfo hbConnectionInfo,
                               final Host dcHost,
                               final boolean testOnly) {
        final ServiceInfo siRsc = hbConnectionInfo.getLastServiceInfoRsc();
        final ServiceInfo siWithRsc =
                                  hbConnectionInfo.getLastServiceInfoWithRsc();
        if ((siWithRsc != null
             && siWithRsc.getService() != null
             && siWithRsc.getService().isNew())
            || (siRsc != null
                && siRsc.getService() != null
                && siRsc.getService().isNew())) {
            addOrder(null, siRsc, siWithRsc);
        } else {
            siWithRsc.addOrder(siRsc, dcHost, testOnly);
        }
        if (testOnly) {
            try {
                mHbConnectionLock.acquire();
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            final Edge edge = hbconnectionToEdgeMap.get(hbConnectionInfo);
            mHbConnectionLock.release();
            addExistingTestEdge(edge);
        }
    }

    /** Removes colocation. */
    public final void removeColocation(final HbConnectionInfo hbci,
                                       final Host dcHost,
                                       final boolean testOnly) {
        final ServiceInfo siRsc = hbci.getLastServiceInfoRsc();
        final ServiceInfo siWithRsc = hbci.getLastServiceInfoWithRsc();
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge edge = hbconnectionToEdgeMap.get(hbci);
        mHbConnectionLock.release();
        if (edgeIsColocationList.contains(edge)) {
            if (!testOnly) {
                edgeIsColocationList.remove(edge);
            }
            siRsc.removeColocation(siWithRsc, dcHost, testOnly);
        }
        if (testOnly) {
            addExistingTestEdge(edge);
        } else {
            if (hbci.isNew()) {
                removeEdge(edge, testOnly);
            }
        }
    }

    /** Adds colocation. */
    public final void addColocation(final HbConnectionInfo hbConnectionInfo,
                                    final Host dcHost,
                                    final boolean testOnly) {
        final ServiceInfo siP = hbConnectionInfo.getLastServiceInfoParent();
        final ServiceInfo siC = hbConnectionInfo.getLastServiceInfoChild();
        if (siC != null
            && siC.getService() != null
            && siC.getService().isNew()) {
            addColocation(null, siP, siC);
        } else {
            siP.addColocation(siC, dcHost, testOnly);
        }
        if (testOnly) {
            try {
                mHbConnectionLock.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            final Edge edge = hbconnectionToEdgeMap.get(hbConnectionInfo);
            mHbConnectionLock.release();
            addExistingTestEdge(edge);
        }
    }

    /**
     * Returns whether this hb connection is order.
     */
    public final boolean isOrder(final HbConnectionInfo hbConnectionInfo) {
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge edge = hbconnectionToEdgeMap.get(hbConnectionInfo);
        mHbConnectionLock.release();
        return edgeIsOrderList.contains(edge);
    }

    /**
     * Returns whether this hb connection is colocation.
     */
    public final boolean isColocation(final HbConnectionInfo hbConnectionInfo) {
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        final Edge edge = hbconnectionToEdgeMap.get(hbConnectionInfo);
        mHbConnectionLock.release();
        return edgeIsColocationList.contains(edge);
    }

    /**
     * Adds host to the graph.
     */
    public final void addHost(final HostInfo hostInfo) {

        Vertex v = super.getVertex(hostInfo);
        if (v == null) {
            /* add host vertex */
            final SparseVertex sv = new SparseVertex();
            v = getGraph().addVertex(sv);
            somethingChanged();
            putInfoToVertex(hostInfo, v);
            vertexToHostMap.put(v, hostInfo);
            hostToVertexMap.put(hostInfo, v);
            putVertexToInfo(v, (Info) hostInfo);
            Point2D hostPos = getSavedPosition(hostInfo);
            if (hostPos == null) {
                hostPos = new Point2D.Double(hostDefaultXPos, HOST_Y_POS);
                hostDefaultXPos += HOST_STEP_X;
            }
            getVertexLocations().setLocation(sv, hostPos);
            // TODO: vertexLocations needs locking
        }
    }

    /**
     * Small text that appears above the icon.
     */
    protected final String getIconText(final Vertex v, final boolean testOnly) {
        if (isTestOnlyAnimation()) {
            return "ptest...";
        }
        if (vertexToHostMap.containsKey(v)) {
            return vertexToHostMap.get(v).getIconTextForGraph(testOnly);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return vertexToConstraintPHMap.get(v).getIconTextForGraph(testOnly);
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        if (si == null) {
            return null;
        }
        return si.getIconTextForGraph(testOnly);
    }

    /**
     * Small text that appears in the right corner.
     */
    protected final Subtext getRightCornerText(final Vertex v,
                                               final boolean testOnly) {
        if (vertexToHostMap.containsKey(v)) {
            final HostInfo hi = vertexToHostMap.get(v);
            return hi.getRightCornerTextForGraph(testOnly);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return null;
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        if (si != null) {
            return si.getRightCornerTextForGraph(testOnly);
        }
        return null;
    }

    /**
     * Small text that appears down.
     */
    protected final Subtext[] getSubtexts(final Vertex v,
                                          final boolean testOnly) {
        if (vertexToHostMap.containsKey(v)) {
            return vertexToHostMap.get(v).getSubtextsForGraph(testOnly);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
            return vertexToConstraintPHMap.get(v).getSubtextsForGraph(testOnly);
        }
        final ServiceInfo si = (ServiceInfo) getInfo(v);
        if (si == null) {
            return null;
        }
        return si.getSubtextsForGraph(testOnly);
    }

    /**
     * Returns how much of the disk is used. Probably useful only for disks.
     * -1 for not used or not applicable.
     */
    protected final int getUsed(final Vertex v) {
        if (vertexToHostMap.containsKey(v)) {
            return ((HostInfo) getInfo(v)).getUsed();
        }
        return ((ServiceInfo) getInfo(v)).getUsed();
    }

    /**
     * This method draws how much of the vertex is used for something.
     * It draws more colors for verteces that have more background colors.
     */
    protected final void drawInside(final Vertex v,
                              final Graphics2D g2d,
                              final double x,
                              final double y,
                              final Shape shape) {
        final boolean tOnly = isTestOnly();
        final float height = (float) shape.getBounds().getHeight();
        final float width = (float) shape.getBounds().getWidth();
        if (vertexToHostMap.containsKey(v)) {
            final HostInfo hi = (HostInfo) getInfo(v);
            drawInsideVertex(g2d,
                             v,
                             hi.getHost().getPmColors(),
                             x,
                             y,
                             height,
                             width);
        } else if (vertexToConstraintPHMap.containsKey(v)) {
        } else {
            final ServiceInfo si = (ServiceInfo) getInfo(v);
            if (si == null) {
                return;
            }
            final List<Color> colors = si.getHostColors(tOnly);
            final int number = colors.size();
            if (number > 1) {
                for (int i = 1; i < number; i++) {
                    final Paint p = new GradientPaint(
                                                (float) x + width / number,
                                                (float) y,
                                                getVertexFillSecondaryColor(v),
                                                (float) x + width / number,
                                                (float) y + height,
                                                colors.get(i),
                                                false);
                    g2d.setPaint(p);
                    final RoundRectangle2D s =
                       new RoundRectangle2D.Double(
                                    x + (width / number) * i - 5,
                                    y,
                                    width / number + (i < number - 1 ? 20 : 5),
                                    height,
                                    20,
                                    20);
                    g2d.fill(s);
                }
            }
            final double used = getUsed(v);
            if (used > 0) {
                /** Show how much is used. */
                final double freeWidth = width * (100 - used) / 100;
                final RoundRectangle2D freeShape =
                   new RoundRectangle2D.Double(x + width - freeWidth,
                                               y,
                                               freeWidth,
                                               height,
                                               20,
                                               20);
                g2d.setColor(new Color(255, 255, 255, 180));
                g2d.fill(freeShape);
            }
        }
        if (isPicked(v)) {
            if (tOnly) {
                g2d.setColor(Color.RED);
            } else {
                g2d.setColor(Color.BLACK);
            }
        } else {
            g2d.setColor(Color.WHITE);
        }
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(shape);
    }

    /**
     * Returns all crm connections.
     */
    public final List<HbConnectionInfo> getAllHbConnections() {
        final List<HbConnectionInfo> allConnections =
                                            new ArrayList<HbConnectionInfo>();
        try {
            mHbConnectionLock.acquire();
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        for (final HbConnectionInfo hbci : hbconnectionToEdgeMap.keySet()) {
            allConnections.add(hbci);
        }
        mHbConnectionLock.release();
        return allConnections;
    }

    /**
     * Returns the vertex that represents the specified resource or its group.
     */
    protected final Vertex getVertex(final Info i) {
        if (i == null) {
            return null;
        }
        if (hostToVertexMap.containsKey(i)) {
            return super.getVertex(i);
        }
        final GroupInfo gi = ((ServiceInfo) i).getGroupInfo();
        if (gi == null) {
            return super.getVertex(i);
        } else {
            return super.getVertex(gi);
        }
    }

    /** Adds placeholder that is used to create resource sets. */
    public final void addConstraintPlaceholder(
                                     final ConstraintPHInfo rsoi,
                                     final Point2D pos,
                                     final boolean testOnly) {
        if (testOnly) {
            return;
        }
        final SparseVertex sv = new SparseVertex();
        if (pos == null) {
            final Point2D newPos = getSavedPosition(rsoi);
            if (newPos == null) {
                final Point2D.Float max = getFilledGraphSize();
                final float maxYPos = (float) max.getY();
                getVertexLocations().setLocation(
                                        sv,
                                        new Point2D.Float(BD_X_POS,
                                                          maxYPos + 40));
            } else {
                getVertexLocations().setLocation(sv, newPos);
            }
        } else {
            getVertexLocations().setLocation(sv, pos);
        }

        final Vertex v = getGraph().addVertex(sv);
        putInfoToVertex(rsoi, v);
        putVertexToInfo(v, (Info) rsoi);
        constraintPHToVertexMap.put(rsoi, v);
        vertexToConstraintPHMap.put(v, rsoi);
        somethingChanged();
    }

    /** Returns height of the vertex. */
    protected final int getDefaultVertexHeight(final Vertex v) {
        if (vertexToConstraintPHMap.containsKey(v)) {
            return 50;
        } else {
            return VERTEX_HEIGHT;
        }
    }


    /** Returns the width of the service vertex shape. */
    protected final int getDefaultVertexWidth(final Vertex v) {
        if (vertexToConstraintPHMap.containsKey(v)) {
            return 50;
        } else {
            return super.getDefaultVertexWidth(v);
        }
    }

    /** Sets the vertex width. */
    protected final void setVertexWidth(final Vertex v, final int size) {
        super.setVertexWidth(v, size);
    }

    /** Sets the vertex height. */
    protected final void setVertexHeight(final Vertex v, final int size) {
        super.setVertexHeight(v, size);
    }
}
