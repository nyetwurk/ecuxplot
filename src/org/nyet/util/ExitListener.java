package org.nyet.util;

import java.awt.event.*;

public class ExitListener extends WindowAdapter {
    @Override
    public void windowClosing(WindowEvent event) { System.exit(0); }
}

// vim: set sw=4 ts=8 expandtab:
