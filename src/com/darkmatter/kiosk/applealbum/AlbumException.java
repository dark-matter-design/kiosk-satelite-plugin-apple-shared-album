package com.darkmatter.kiosk.applealbum;

/** A deliberately non-diagnostic failure safe to translate into user-facing text. */
final class AlbumException extends Exception {
    enum Code {
        INVALID_URL, NOT_PUBLIC, EMPTY, NETWORK,
        METADATA_TOO_LARGE, IMAGE_TOO_LARGE, DECODE_TOO_LARGE, OUTPUT_TOO_LARGE,
        INVALID_RESPONSE, CANCELLED
    }

    final Code code;

    AlbumException(Code code) {
        super(code.name());
        this.code = code;
    }

    String publicMessage() {
        switch (code) {
            case INVALID_URL: return "Enter a supported public Apple Shared Album link.";
            case NOT_PUBLIC: return "This album is not publicly accessible. Invitations, approvals, and sign-in are not supported.";
            case EMPTY: return "The public album contains no supported still images.";
            case METADATA_TOO_LARGE: return "The album metadata exceeded the safe response limit.";
            case IMAGE_TOO_LARGE: return "A photo exceeded the safe download limit and no smaller rendition was available.";
            case DECODE_TOO_LARGE: return "A photo exceeded the safe decoded-image limit.";
            case OUTPUT_TOO_LARGE: return "A photo could not be fitted within the safe display limit.";
            case INVALID_RESPONSE: return "Apple returned an unsupported album response.";
            case CANCELLED: return "Album loading stopped.";
            default: return "Could not load the public album. Check the connection and try again.";
        }
    }
}
