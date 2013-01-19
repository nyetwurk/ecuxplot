package org.nyet.util;
import java.io.File;
import java.util.List;
public interface FileDropHost {
    public void loadFile(File file);
    public void loadFiles(List<File> fileList);
}

