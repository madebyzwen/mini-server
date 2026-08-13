package io.github.madebyzwen.miniserver;

/**
 * Reports that a requested persistence Section does not exist.
 */
final class SectionNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    SectionNotFoundException() {
        super("Section not found.");
    }
}
