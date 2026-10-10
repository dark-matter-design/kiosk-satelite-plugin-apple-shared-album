package com.darkmatter.kiosk.applealbum;

import java.nio.charset.StandardCharsets;

final class SlideshowHtml {
    static final int SDK_INLINE_LIMIT = 512 * 1024;
    private SlideshowHtml() {}

    static String image(byte[] jpeg, String fit) throws AlbumException {
        return image(jpeg, null, fit, "None", 1.0, "None", 30);
    }

    static String image(byte[] incoming, byte[] outgoing, String fit, String transition,
            double transitionSeconds, String motion, long intervalSeconds) throws AlbumException {
        if (incoming == null || incoming.length == 0) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        String objectFit = "Fill screen".equals(fit) ? "cover" : "contain";
        String effect = (outgoing == null || "None".equals(transition)) ? "none"
                : ("Slide".equals(transition) ? "slide" : "fade");
        double duration = Math.max(0.2, Math.min(3.0, transitionSeconds));
        long motionMillis = Math.max(5000L, Math.min(300000L, intervalSeconds * 1000L));
        StringBuilder html = new StringBuilder(1024 + (incoming.length + (outgoing == null ? 0 : outgoing.length)) * 4 / 3);
        html.append("<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">")
            .append("<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}")
            .append(".layer{position:absolute;inset:0;overflow:hidden;background:#000}")
            .append("img{width:100%;height:100%;display:block;object-fit:").append(objectFit).append("}")
            .append(".incoming{z-index:2}.fade{opacity:0;animation:fade ").append(duration).append("s ease forwards}")
            .append(".slide{transform:translate3d(100%,0,0);animation:slide ").append(duration).append("s ease forwards}")
            .append(".motion img{animation:motion ").append(motionMillis).append("ms ease-in-out forwards;transform-origin:50% 50%}")
            .append("@keyframes fade{to{opacity:1}}@keyframes slide{to{transform:translate3d(0,0,0)}}")
            .append("@keyframes motion{from{transform:scale(1.01) translate3d(-.5%,-.35%,0)}to{transform:scale(1.08) translate3d(.8%,.55%,0)}}")
            .append("@media(prefers-reduced-motion:reduce){.fade,.slide,.motion img{animation:none!important;opacity:1;transform:none!important}}")
            .append("</style>");
        if (outgoing != null && !"none".equals(effect)) {
            html.append("<div class=\"layer outgoing\"><img alt=\"\" src=\"data:image/jpeg;base64,")
                .append(Base64Codec.encode(outgoing)).append("\"></div>");
        }
        html.append("<div class=\"layer incoming ").append(effect);
        if ("Ken Burns".equals(motion)) html.append(" motion");
        html.append("\"><img alt=\"\" src=\"data:image/jpeg;base64,")
            .append(Base64Codec.encode(incoming)).append("\"></div>");
        String result = html.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > SDK_INLINE_LIMIT) {
            throw new AlbumException(AlbumException.Code.TOO_LARGE);
        }
        return result;
    }

    static String message(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;background:#000;color:#ddd;font:20px sans-serif;display:grid;place-items:center}"
                + "p{max-width:34rem;padding:2rem;text-align:center}</style><p>" + safe + "</p>";
    }
}
