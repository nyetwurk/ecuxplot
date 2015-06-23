package org.nyet.util;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class MenuListener implements ActionListener {
    private SubActionListener listener;
    private Comparable<?> parentId;
    public MenuListener(SubActionListener listener, Comparable<?> parentId) {
	super();
	this.parentId = parentId;
	this.listener = listener;
    }
    @Override
    public void actionPerformed(ActionEvent event) {
	listener.actionPerformed(event, parentId);
    }
}
