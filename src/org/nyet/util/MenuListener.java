package org.nyet.util;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class MenuListener implements ActionListener {
    private final SubActionListener listener;
    private final Comparable<?> parentId;
    public MenuListener(SubActionListener listener, Comparable<?> parentId) {
        super();
        this.parentId = parentId;
        this.listener = listener;
    }
    @Override
    public void actionPerformed(ActionEvent event) {
        this.listener.actionPerformed(event, this.parentId);
    }
}

// vim: set sw=4 ts=8 expandtab:
