package org.nyet.util;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public interface SubActionListener extends ActionListener{
    public void actionPerformed(ActionEvent event, Comparable<?> parentId);
}
