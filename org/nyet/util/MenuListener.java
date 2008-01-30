package org.nyet.util;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class MenuListener implements ActionListener {
    private SubActionListener sub;
    private Comparable id;
    public MenuListener(SubActionListener sub, Comparable id) {
	super();
	this.id = id;
	this.sub = sub;
    }
    public void actionPerformed(ActionEvent event) {
	sub.actionPerformed(event, id);
    }
}
