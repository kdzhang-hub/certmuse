package org.dromara.certmuse.catalog.support;

/** Safe stream-boundary failures that must not expose private object details. */
public class TextbookPdfStreamException extends RuntimeException {
    private final int status;

    private TextbookPdfStreamException(int status, Throwable cause) {
        super(null, cause, false, false);
        this.status = status;
    }

    public int status() { return status; }
    public static TextbookPdfStreamException unavailable() { return new TextbookPdfStreamException(404, null); }
    public static TextbookPdfStreamException invalidRange() { return new TextbookPdfStreamException(416, null); }
    public static TextbookPdfStreamException storageUnavailable(Throwable cause) { return new TextbookPdfStreamException(503, cause); }
}
