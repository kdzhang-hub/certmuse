package org.dromara.certmuse.catalog.support;

/** A validated inclusive byte range for a private textbook PDF. */
public record TextbookPdfByteRange(long start, long end) {
    public static TextbookPdfByteRange parse(String header, long fileSize) {
        if (fileSize < 1 || header == null || header.isBlank()) {
            return new TextbookPdfByteRange(0, Math.max(fileSize - 1, 0));
        }
        if (!header.startsWith("bytes=") || header.indexOf(',') >= 0) {
            throw TextbookPdfStreamException.invalidRange();
        }
        String value = header.substring("bytes=".length()).trim();
        int separator = value.indexOf('-');
        if (separator < 0 || separator != value.lastIndexOf('-')) {
            throw TextbookPdfStreamException.invalidRange();
        }
        try {
            String startValue = value.substring(0, separator).trim();
            String endValue = value.substring(separator + 1).trim();
            long start;
            long end;
            if (startValue.isEmpty()) {
                long suffixLength = Long.parseLong(endValue);
                if (suffixLength < 1) throw TextbookPdfStreamException.invalidRange();
                start = Math.max(fileSize - suffixLength, 0);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startValue);
                end = endValue.isEmpty() ? fileSize - 1 : Math.min(Long.parseLong(endValue), fileSize - 1);
            }
            if (start < 0 || start >= fileSize || end < start) throw TextbookPdfStreamException.invalidRange();
            return new TextbookPdfByteRange(start, end);
        } catch (NumberFormatException exception) {
            throw TextbookPdfStreamException.invalidRange();
        }
    }

    public long length() { return end - start + 1; }
    public String value() { return "bytes=" + start + '-' + end; }
}
