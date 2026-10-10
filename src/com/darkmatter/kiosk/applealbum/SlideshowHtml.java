package com.darkmatter.kiosk.applealbum;

import java.nio.charset.StandardCharsets;

final class SlideshowHtml {
    static final int SDK_INLINE_LIMIT = 512 * 1024;
    private SlideshowHtml() {}

    static String image(byte[] jpeg, String fit) throws AlbumException {
        String objectFit = "Fill screen".equals(fit) ? "cover" : "contain";
        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}"
                + "img{width:100%;height:100%;display:block;object-fit:" + objectFit + ";}</style>"
                + "<img alt=\"\" src=\"data:image/jpeg;base64," + Base64Codec.encode(jpeg) + "\">";
        if (html.getBytes(StandardCharsets.UTF_8).length > SDK_INLINE_LIMIT) {
            throw new AlbumException(AlbumException.Code.TOO_LARGE);
        }
        return html;
    }

    static String message(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;background:#000;color:#ddd;font:20px sans-serif;display:grid;place-items:center}"
                + "p{max-width:34rem;padding:2rem;text-align:center}</style><p>" + safe + "</p>";
    }
}
