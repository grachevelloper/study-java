package ru.mirea.library.model;

import java.util.Objects;

public final class Book {
    private final long id;
    private final String isbn;
    private final String title;
    private final String author;
    private final int publicationYear;
    private final int totalCopies;
    private final int availableCopies;

    public Book(long id, String isbn, String title, String author, int publicationYear,
                int totalCopies, int availableCopies) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publicationYear = publicationYear;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public long getId() { return id; }
    public String getIsbn() { return isbn; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public int getPublicationYear() { return publicationYear; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Book book)) return false;
        return id == book.id && publicationYear == book.publicationYear
            && totalCopies == book.totalCopies && availableCopies == book.availableCopies
            && Objects.equals(isbn, book.isbn) && Objects.equals(title, book.title)
            && Objects.equals(author, book.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isbn, title, author, publicationYear, totalCopies, availableCopies);
    }
}
