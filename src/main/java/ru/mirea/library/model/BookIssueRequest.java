package ru.mirea.library.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public final class BookIssueRequest {
    private final long id;
    private final long readerId;
    private final long bookId;
    private final RequestStatus status;
    private final LocalDateTime requestedAt;
    private final LocalDate issuedOn;
    private final LocalDate dueOn;
    private final LocalDate returnedOn;
    private final String note;

    public BookIssueRequest(long id, long readerId, long bookId, RequestStatus status,
                            LocalDateTime requestedAt, LocalDate issuedOn, LocalDate dueOn,
                            LocalDate returnedOn, String note) {
        this.id = id;
        this.readerId = readerId;
        this.bookId = bookId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.issuedOn = issuedOn;
        this.dueOn = dueOn;
        this.returnedOn = returnedOn;
        this.note = note;
    }

    public long getId() { return id; }
    public long getReaderId() { return readerId; }
    public long getBookId() { return bookId; }
    public RequestStatus getStatus() { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public LocalDate getDueOn() { return dueOn; }
    public LocalDate getReturnedOn() { return returnedOn; }
    public String getNote() { return note; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof BookIssueRequest request)) return false;
        return id == request.id && readerId == request.readerId && bookId == request.bookId
            && status == request.status && Objects.equals(requestedAt, request.requestedAt)
            && Objects.equals(issuedOn, request.issuedOn) && Objects.equals(dueOn, request.dueOn)
            && Objects.equals(returnedOn, request.returnedOn) && Objects.equals(note, request.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, readerId, bookId, status, requestedAt, issuedOn, dueOn, returnedOn, note);
    }
}
