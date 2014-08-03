/*
 * This file is part of LCMC written by Rasto Levrinc.
 *
 * Copyright (C) 2013, Rastislav Levrinc.
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

package lcmc.gui.dialog.drbdConfig;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;

import lcmc.model.Host;
import lcmc.model.drbd.DrbdInstallation;
import lcmc.gui.SpringUtilities;
import lcmc.gui.dialog.WizardDialog;
import lcmc.gui.dialog.host.DialogHost;
import lcmc.gui.resources.drbd.VolumeInfo;
import lcmc.gui.widget.Widget;
import lcmc.utilities.ExecCallback;
import lcmc.utilities.MyButton;
import lcmc.utilities.Tools;
import lcmc.utilities.ssh.ExecCommandConfig;

/**
 * @author Rasto Levrinc
 * @version $Id$
 *
 */
final class ProxyCheckInstallation extends DialogHost {
    private static final ImageIcon CHECKING_ICON =
                               Tools.createImageIcon(Tools.getDefault("Dialog.Host.CheckInstallation.CheckingIcon"));
    private static final ImageIcon NOT_INSTALLED_ICON =
                            Tools.createImageIcon(Tools.getDefault("Dialog.Host.CheckInstallation.NotInstalledIcon"));
    private static final ImageIcon ALREADY_INSTALLED_ICON =
                            Tools.createImageIcon(Tools.getDefault("Dialog.Host.CheckInstallation.InstalledIcon"));

    private static final String PROXY_PREFIX = "ProxyInst";
    private static final String PROXY_AUTO_OPTION = "proxyinst";
    private WizardDialog nextDialogObject = null;

    /** Checking proxy label. */
    private final JLabel checkingProxyLabel = new JLabel(": "
                                                         + Tools.getString("ProxyCheckInstallation.CheckingProxy"));

    private final MyButton installProxyButton =
                                         new MyButton(Tools.getString("ProxyCheckInstallation.ProxyInstallButton"));
    private Widget proxyInstallationMethodWidget;

    private final JLabel proxyCheckingIcon = new JLabel(CHECKING_ICON);
    private final Host proxyHost;
    private final VolumeInfo volumeInfo;
    private final WizardDialog origDialog;

    ProxyCheckInstallation(final WizardDialog previousDialog,
                           final Host proxyHost,
                           final VolumeInfo volumeInfo,
                           final WizardDialog origDialog,
                           final DrbdInstallation drbdInstallation) {
        init(previousDialog, proxyHost, drbdInstallation);
        this.proxyHost = proxyHost;
        this.volumeInfo = volumeInfo;
        this.origDialog = origDialog;
    }

    @Override
    protected void initDialogBeforeVisible() {
        super.initDialogBeforeVisible();
        enableComponentsLater(new JComponent[]{});
    }

    @Override
    protected void initDialogAfterVisible() {
        nextDialogObject = null;
        final ProxyCheckInstallation thisClass = this;
        Tools.invokeLater(new Runnable() {
            @Override
            public void run() {
                installProxyButton.setBackgroundColor(Tools.getDefaultColor("ConfigDialog.Button"));
                installProxyButton.setEnabled(false);
                proxyInstallationMethodWidget.setEnabled(false);
            }
        });
        installProxyButton.addActionListener(
            new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    nextDialogObject = new ProxyInst(thisClass,
                                                     getHost(),
                                                     volumeInfo,
                                                     origDialog,
                                                     getDrbdInstallation());
                    final InstallMethods im = (InstallMethods) proxyInstallationMethodWidget.getValue();
                    getDrbdInstallation().setProxyInstallMethodIndex(im.getIndex());
                    Tools.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            buttonClass(nextButton()).pressButton();
                        }
                    });
                }
            }
        );

        getHost().execCommand(new ExecCommandConfig().commandString("ProxyCheck.version")
                                                .progressBar(getProgressBar())
                                                .execCallback(new ExecCallback() {
                                                    @Override
                                                    public void done(final String answer) {
                                                        checkProxyInstallation(answer);
                                                    }

                                                    @Override
                                                    public void doneError(final String answer, final int errorCode) {
                                                        checkProxyInstallation(""); // not installed
                                                    }
                                                }));
    }

    void checkProxyInstallation(final String ans) {
        if (ans != null && ans.isEmpty() || "\n".equals(ans)) {
            Tools.invokeLater(new Runnable() {
                @Override
                public void run() {
                    checkingProxyLabel.setText(": " + Tools.getString("ProxyCheckInstallation.ProxyNotInstalled"));
                    proxyCheckingIcon.setIcon(NOT_INSTALLED_ICON);
                    final String toolTip = getInstToolTip(PROXY_PREFIX, "1");
                    proxyInstallationMethodWidget.setToolTipText(toolTip);
                    installProxyButton.setToolTipText(toolTip);
                    installProxyButton.setEnabled(true);
                    proxyInstallationMethodWidget.setEnabled(true);
                }
            });
            progressBarDone();
            printErrorAndRetry(Tools.getString("Dialog.Host.CheckInstallation.SomeFailed"));
        } else {
            Tools.invokeLater(new Runnable() {
                @Override
                public void run() {
                    checkingProxyLabel.setText(": " + ans.trim());
                    installProxyButton.setText(Tools.getString("ProxyCheckInstallation.ProxyCheckForUpgradeButton"));
                    if (false) {
                        // TODO: disabled
                        installProxyButton.setEnabled(true);
                        proxyInstallationMethodWidget.setEnabled(true);
                    }
                    proxyCheckingIcon.setIcon(ALREADY_INSTALLED_ICON);
                    buttonClass(finishButton()).setEnabled(true);
                    makeDefaultAndRequestFocus(buttonClass(finishButton()));
                }
            });
            progressBarDone();
            enableComponents();
            Tools.invokeLater(new Runnable() {
                @Override
                public void run() {
                    answerPaneSetText(Tools.getString("Dialog.Host.CheckInstallation.AllOk"));
                }
            });
        }
    }

    @Override
    public WizardDialog nextDialog() {
        return nextDialogObject;
    }

    @Override
    protected void finishDialog() {
        if (isPressedButton(finishButton())
            || isPressedButton(nextButton())) {
            if (nextDialogObject == null) {
                proxyHost.getBrowser().getClusterBrowser().getDrbdGraph().getDrbdInfo().addProxyHostNode(getHost());
            }
            if (nextDialogObject == null && origDialog != null) {
                nextDialogObject = origDialog;
                setPressedButton(nextButton());
            }
            getHost().getCluster().addProxyHost(getHost());
            if (volumeInfo != null) {
                volumeInfo.getDrbdResourceInfo().resetDrbdResourcePanel();
            }
        }
    }

    @Override
    protected String getHostDialogTitle() {
        return Tools.getString("ProxyCheckInstallation.Title");
    }

    @Override
    protected String getDescription() {
        return Tools.getString("ProxyCheckInstallation.Description");
    }

    private JPanel getInstallationPane() {
        final JPanel pane = new JPanel(new SpringLayout());
        /* get proxy installation methods */
        proxyInstallationMethodWidget = getInstallationMethods(
                             PROXY_PREFIX,
                             Tools.getApplication().isStagingPacemaker(),
                             null, /* last installed method */
                             PROXY_AUTO_OPTION,
                             installProxyButton);
        pane.add(new JLabel("Proxy"));
        pane.add(checkingProxyLabel);
        pane.add(proxyCheckingIcon);
        pane.add(proxyInstallationMethodWidget.getComponent());
        pane.add(installProxyButton);

        SpringUtilities.makeCompactGrid(pane, 1, 5,  //rows, cols
                                              1, 1,  //initX, initY
                                              1, 1); //xPad, yPad
        return pane;
    }

    @Override
    protected JComponent getInputPane() {
        final JPanel pane = new JPanel(new SpringLayout());
        pane.add(getInstallationPane());
        pane.add(getProgressBarPane());
        pane.add(getAnswerPane(Tools.getString("ProxyCheckInstallation.CheckingProxy")));
        SpringUtilities.makeCompactGrid(pane, 3, 1,  //rows, cols
                                              0, 0,  //initX, initY
                                              0, 0); //xPad, yPad

        return pane;
    }

    @Override
    protected boolean skipButtonEnabled() {
        return true;
    }

    @Override
    protected WizardDialog dialogAfterCancel() {
        return origDialog;
    }
}
