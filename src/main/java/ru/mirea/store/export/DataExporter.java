package ru.mirea.store.export;
import java.nio.file.Path;
public interface DataExporter { Path export(StoreExportData data, Path targetDirectory); }
