package ru.mirea.library.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class SystemStatistics {
    private final long readerCount;
    private final long bookTitleCount;
    private final long totalCopyCount;
    private final long availableCopyCount;
    private final long requestCount;
    private final Map<RequestStatus, Long> requestsByStatus;
    private final long overdueRequestCount;

    public SystemStatistics(long readerCount, long bookTitleCount, long totalCopyCount,
                            long availableCopyCount, long requestCount,
                            Map<RequestStatus, Long> requestsByStatus,
                            long overdueRequestCount) {
        this.readerCount = readerCount;
        this.bookTitleCount = bookTitleCount;
        this.totalCopyCount = totalCopyCount;
        this.availableCopyCount = availableCopyCount;
        this.requestCount = requestCount;
        EnumMap<RequestStatus, Long> copy = new EnumMap<>(RequestStatus.class);
        copy.putAll(requestsByStatus);
        this.requestsByStatus = Collections.unmodifiableMap(copy);
        this.overdueRequestCount = overdueRequestCount;
    }

    public long getReaderCount() { return readerCount; }
    public long getBookTitleCount() { return bookTitleCount; }
    public long getTotalCopyCount() { return totalCopyCount; }
    public long getAvailableCopyCount() { return availableCopyCount; }
    public long getRequestCount() { return requestCount; }
    public Map<RequestStatus, Long> getRequestsByStatus() { return requestsByStatus; }
    public long getOverdueRequestCount() { return overdueRequestCount; }
}
