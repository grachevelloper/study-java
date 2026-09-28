package ru.mirea.library.model;

import java.util.Objects;

public final class BookIssueRequestDetails {
    private final BookIssueRequest request;
    private final String readerName;
    private final String bookTitle;
    private final String bookAuthor;

    public BookIssueRequestDetails(BookIssueRequest request, String readerName,
                                   String bookTitle, String bookAuthor) {
        this.request = Objects.requireNonNull(request);
        this.readerName = readerName;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
    }

    public BookIssueRequest getRequest() { return request; }
    public String getReaderName() { return readerName; }
    public String getBookTitle() { return bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
}
