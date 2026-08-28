package org.dromara.certmuse.catalog.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Shared physical-line reader for JSONL import workers. */
final class ImportJsonlLineReader {

    private ImportJsonlLineReader() {
    }

    static Line read(InputStream input, int maxLineBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean tooLarge = false;
        long consumedBytes = 0;
        int value;
        while ((value = input.read()) != -1) {
            consumedBytes++;
            if (value == '\n') {
                break;
            }
            if (!tooLarge) {
                if (output.size() < maxLineBytes + 1) {
                    output.write(value);
                }
                if (output.size() > maxLineBytes) {
                    tooLarge = true;
                }
            }
        }
        if (value == -1 && consumedBytes == 0) {
            return null;
        }
        byte[] rawBytes = output.toByteArray();
        byte[] bytes = rawBytes;
        if (!tooLarge && bytes.length > 0 && bytes[bytes.length - 1] == '\r') {
            bytes = Arrays.copyOf(bytes, bytes.length - 1);
        }
        return new Line(bytes, rawBytes, tooLarge, consumedBytes);
    }

    record Line(byte[] bytes, byte[] rawBytes, boolean tooLarge, long consumedBytes) {
    }
}
