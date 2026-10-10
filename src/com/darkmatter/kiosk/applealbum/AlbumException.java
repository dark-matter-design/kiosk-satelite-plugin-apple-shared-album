package com.darkmatter.kiosk.applealbum;

/** A deliberately non-diagnostic failure safe to translate into user-facing text. */
final class AlbumException extends Exception {
    enum Code { INVALID_URL, NOT_PUBLIC, EMPTY, NETWORK, TOO_LARGE, INVALID_RESPONSE, CANCELLED }

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
            case TOO_LARGE: return "The album response or image exceeded the safe size limit.";
            case INVALID_RESPONSE: return "Apple returned an unsupported album response.";
            case CANCELLED: return "Album loading stopped.";
            default: return "Could not load the public album. Check the connection and try again.";
        }
    }
}
