package io.github.madebyzwen.miniserver;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class UrlPathDecoder {

    private UrlPathDecoder() {
    }

    static String decode(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(rawPath.length());
        int index = 0;
        while (index < rawPath.length()) {
            char character = rawPath.charAt(index);
            if (character == '%') {
                if (index + 2 >= rawPath.length()) {
                    return null;
                }
                int high = hexValue(rawPath.charAt(index + 1));
                int low = hexValue(rawPath.charAt(index + 2));
                if (high < 0 || low < 0) {
                    return null;
                }
                bytes.write((high << 4) | low);
                index += 3;
                continue;
            }

            int nextPercent = rawPath.indexOf('%', index);
            int end = nextPercent < 0 ? rawPath.length() : nextPercent;
            byte[] unescaped = rawPath.substring(index, end).getBytes(StandardCharsets.UTF_8);
            bytes.write(unescaped, 0, unescaped.length);
            index = end;
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character == 0x7f) {
                return true;
            }
        }
        return false;
    }

    static boolean containsEncodedPathSeparator(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        for (int index = 0; index + 2 < rawPath.length(); index++) {
            if (rawPath.charAt(index) != '%') {
                continue;
            }
            int high = hexValue(rawPath.charAt(index + 1));
            int low = hexValue(rawPath.charAt(index + 2));
            int value = high < 0 || low < 0 ? -1 : (high << 4) | low;
            if (value == '/' || value == '\\') {
                return true;
            }
        }
        return false;
    }

    private static int hexValue(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;
    }
}
