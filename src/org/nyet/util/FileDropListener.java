package org.nyet.util;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileDropListener implements DropTargetListener {

    private DataFlavor Linux = null;
    private DataFlavor Windows = null;

    private DropTarget dropTarget;
    private final FileDropHost fileDropHost;


    public FileDropListener(FileDropHost fileDropHost, Component dropTargetComponent) {

        this.fileDropHost = fileDropHost;
        this.setDropTarget(new DropTarget(dropTargetComponent, this));

    }


    @Override
    public void dragEnter(DropTargetDragEvent event) {

        final int action = event.getDropAction();
        event.acceptDrag(action);
    }


    @Override
    public void dragExit(DropTargetEvent arg0) {
    }


    @Override
    public void dragOver(DropTargetDragEvent arg0) {
    }


    /*
     * Code from:
     * http://stackoverflow.com/questions/1697936/java-drag-and-drop-on-mac-os-x
     * Only mildly refactored.
     */
    @Override
    public void drop(DropTargetDropEvent dropEvent) {
        try {

            final Transferable droppedItem = dropEvent.getTransferable();
            DataFlavor[] droppedItemFlavors = droppedItem.getTransferDataFlavors();

            droppedItemFlavors = (droppedItemFlavors.length == 0) ? dropEvent.getCurrentDataFlavors() : droppedItemFlavors;

            DataFlavor flavor = DataFlavor.selectBestTextFlavor(droppedItemFlavors);

            // Flavor will be null on Windows
            // In which case use the 1st available flavor
            flavor = (flavor == null) ? droppedItemFlavors[0] : flavor;

            this.Linux = new DataFlavor("text/uri-list;class=java.io.Reader");
            this.Windows = DataFlavor.javaFileListFlavor;

            handleDrop(dropEvent, droppedItem, flavor);

        }

        catch (final ArrayIndexOutOfBoundsException e) {
            System.err.println("DnD not initalized properly, please try again.");
        } catch (final IOException e) {
            System.err.println(e.getMessage());
        } catch (final UnsupportedFlavorException e) {
            System.err.println(e.getMessage());
        } catch (final ClassNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }


    private void handleDrop(DropTargetDropEvent dropEvent,
            Transferable droppedItem, DataFlavor flavor) throws UnsupportedFlavorException, IOException {

        // On Linux (and OS X) file DnD is a reader
        if (flavor.equals(this.Linux)) {

            acceptLinuxDrop(dropEvent, droppedItem, flavor);

        }

        // On Windows file DnD is a file list
        else if (flavor.equals(this.Windows)) {

            acceptWindowsDrop(dropEvent, droppedItem, flavor);

        } else {

            System.err.println("DnD Error");
            dropEvent.rejectDrop();

        }

    }


    private void acceptWindowsDrop(DropTargetDropEvent dropTargetEvent,
            Transferable droppedItem, DataFlavor flavor) throws UnsupportedFlavorException, IOException {

        dropTargetEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
        @SuppressWarnings("unchecked")
	final
        List<File> list = (List<File>) droppedItem.getTransferData(flavor);
        dropTargetEvent.dropComplete(true);

        if (list.size() == 1) {
            // System.out.println("File Dragged: " + list.get(0));
            this.fileDropHost.loadFile(new File(list.get(0).toString()));
        } else {
	    this.fileDropHost.loadFiles(list);
	}

    }


    private void acceptLinuxDrop(DropTargetDropEvent dropTargetEvent,
            Transferable tr, DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        dropTargetEvent.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

        final BufferedReader read = new BufferedReader(flavor.getReaderForText(tr));
	String fileName = java.net.URLDecoder.decode(read.readLine(), "UTF-8");
	// Remove 'file://' from start of URL (if there)
	if (fileName.startsWith("file://")) {
	    fileName = fileName.substring(7);
	}
        read.close();

        dropTargetEvent.dropComplete(true);
        // System.out.println("File Dragged:" + fileName);

        this.fileDropHost.loadFile(new File(fileName));
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent arg0) {
    }


public DropTarget getDropTarget() {
	return this.dropTarget;
}


public void setDropTarget(DropTarget dropTarget) {
	this.dropTarget = dropTarget;
}
}
