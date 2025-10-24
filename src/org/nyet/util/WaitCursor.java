package org.nyet.util;

import java.awt.event.*;
import javax.swing.*;

public class WaitCursor implements Cursors {
  private final static MouseAdapter mouseAdapter =
    new MouseAdapter() {};

  public static void startWaitCursor(RootPaneContainer root) {
    // NOTE: WaitCursor may not be visible when cursor is over dropdown menus,
    // popup components, or other layered components that interfere with glass pane.
    // Move cursor to main window content area to see the spinner clearly.
    root.getGlassPane().setCursor(WAIT_CURSOR);
    root.getGlassPane().addMouseListener(mouseAdapter);
    root.getGlassPane().setVisible(true);
  }

  public static void startWaitCursor(JComponent component) {
    final RootPaneContainer root =
      ((RootPaneContainer) component.getTopLevelAncestor());
    startWaitCursor(root);
  }

  public static void startWaitCursor(JFrame frame) {
    startWaitCursor(frame.getRootPane());
  }

  public static void stopWaitCursor(RootPaneContainer root) {
    root.getGlassPane().setCursor(DEFAULT_CURSOR);
    root.getGlassPane().removeMouseListener(mouseAdapter);
    root.getGlassPane().setVisible(false);
  }

  public static void stopWaitCursor(JFrame frame) {
    stopWaitCursor(frame.getRootPane());
  }

  public static void stopWaitCursor(JComponent component) {
    final RootPaneContainer root =
      ((RootPaneContainer) component.getTopLevelAncestor());
    stopWaitCursor(root);
  }
}

// vim: set sw=4 ts=8 expandtab:
