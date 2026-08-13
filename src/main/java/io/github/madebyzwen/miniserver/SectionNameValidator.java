package io.github.madebyzwen.miniserver;

final class SectionNameValidator {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 128;

    private SectionNameValidator() {
    }

    static boolean isValid(String section) {
        if (section == null) {
            return false;
        }

        int length = section.codePointCount(0, section.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }

        int first = section.codePointAt(0);
        int last = section.codePointBefore(section.length());
        if (isWhitespace(first) || isWhitespace(last)) {
            return false;
        }

        for (int offset = 0; offset < section.length();) {
            int codePoint = section.codePointAt(offset);
            if (Character.isISOControl(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
