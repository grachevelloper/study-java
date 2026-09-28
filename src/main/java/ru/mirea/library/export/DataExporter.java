package ru.mirea.library.export;

import java.nio.file.Path;

public interface DataExporter {
    Path export(LibraryExportData data, Path targetDirectory);
}
