package org.nyet.util;

import java.awt.event.*;
import javax.swing.*;

public class WaitCursor implements Cursors {
  private final static MouseAdapter mouseAdapter =
    new MouseAdapter() {};

  public static void startWaitCursor(RootPaneContainer root) {
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
