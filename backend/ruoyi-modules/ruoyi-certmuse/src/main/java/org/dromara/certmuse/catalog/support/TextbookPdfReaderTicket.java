package org.dromara.certmuse.catalog.support;

import java.io.Serializable;

/** Private object metadata retained server-side for one browser reader ticket. */
public record TextbookPdfReaderTicket(String objectKey, String fileName, long fileSize, String audience) implements Serializable {
    public static final String PRIVATE_AUDIENCE = "private";
    public static final String PUBLIC_AUDIENCE = "public";

    /** Preserves compatibility with tickets issued before public reading was introduced. */
    public TextbookPdfReaderTicket(String objectKey, String fileName, long fileSize) {
        this(objectKey, fileName, fileSize, PRIVATE_AUDIENCE);
    }

    public boolean isPublic() {
        return PUBLIC_AUDIENCE.equals(audience);
    }
}
