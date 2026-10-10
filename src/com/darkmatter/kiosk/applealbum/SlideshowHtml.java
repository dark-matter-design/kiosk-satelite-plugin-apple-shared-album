package com.darkmatter.kiosk.applealbum;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class SlideshowHtml {
    static final int SDK_INLINE_LIMIT = 512 * 1024;
    static final int PIXEL_SHIFT_INTERVAL_MS = 60_000;
    static final int PIXEL_SHIFT_AMPLITUDE_PX = 10;
    private SlideshowHtml() {}

    static String image(byte[] jpeg, String fit) throws AlbumException {
        return image(jpeg, fit, OverlayOptions.from(null), PhotoMetadata.EMPTY,
                new Date(), Locale.getDefault(), TimeZone.getDefault());
    }

    static String image(byte[] jpeg, String fit, OverlayOptions options, PhotoMetadata metadata,
            Date now, Locale locale, TimeZone zone) throws AlbumException {
        String objectFit = "Fill screen".equals(fit) ? "cover" : "contain";
        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}"
                + "img{width:100%;height:100%;display:block;object-fit:" + objectFit + ";}"
                + overlayCss(options) + "</style>"
                + "<img alt=\"\" src=\"data:image/jpeg;base64," + Base64Codec.encode(jpeg) + "\">"
                + overlayHtml(options, metadata, now, locale, zone);
        if (html.getBytes(StandardCharsets.UTF_8).length > SDK_INLINE_LIMIT) {
            throw new AlbumException(AlbumException.Code.TOO_LARGE);
        }
        return html;
    }

    private static String overlayCss(OverlayOptions options) {
        int timeSize = Math.max(22, Math.round(44 * options.scale / 100f));
        return ".overlay{position:absolute;z-index:1;isolation:isolate;display:flex;flex-direction:column;padding:28px;max-width:100%;max-height:100%;box-sizing:border-box;"
                + "pointer-events:none;color:" + options.color + ";font-family:" + options.font + ";font-weight:"
                + options.weight + ";line-height:1.08;" + options.cssPosition() + ";"
                + "text-shadow:0 2px 4px #000,0 0 12px #000,0 0 24px #000}"
                + ".overlay:before{content:'';position:absolute;z-index:-1;inset:-36px;background:radial-gradient(ellipse at center,rgba(0,0,0,.58),transparent 70%)}"
                + ".clock{font-size:" + timeSize + "px;white-space:nowrap}.current{font-size:"
                + Math.max(14, Math.round(timeSize * .52f)) + "px;opacity:.82;margin-top:.18em;white-space:nowrap}"
                + ".meta{font-size:" + Math.max(14, Math.round(timeSize * .42f)) + "px;opacity:.9;margin-top:.55em;max-width:28rem;overflow-wrap:anywhere}";
    }

    private static String overlayHtml(OverlayOptions options, PhotoMetadata metadata, Date now,
            Locale locale, TimeZone zone) {
        if (options == null || !options.visible(metadata)) return "";
        StringBuilder out = new StringBuilder("<section class=overlay aria-label=\"Photo information\">");
        if (options.clock) {
            out.append("<time class=clock id=clock datetime=\"").append(html(iso(now))).append("\">")
                    .append(html(formatTime(now, options.use24Hour, options.seconds, zone))).append("</time>");
        }
        if (options.currentDate) {
            out.append("<time class=current id=current-date datetime=\"").append(html(iso(now))).append("\">")
                    .append(html(formatCurrentDate(now, locale, zone))).append("</time>");
        }
        String capture = metadata == null ? null : metadata.formattedCaptureDate(locale, zone);
        String location = metadata == null ? null : metadata.formattedLocation();
        if ((options.photoDate && capture != null) || (options.location && location != null)) {
            out.append("<div class=meta>");
            if (options.location && location != null) out.append("<div class=location>").append(html(location)).append("</div>");
            if (options.photoDate && capture != null) out.append("<div class=capture>Taken ").append(html(capture)).append("</div>");
            out.append("</div>");
        }
        out.append("</section>");
        if (options.pixelShift) {
            out.append(pixelShiftScript());
        }
        if (options.clock || options.currentDate) {
            out.append("<script>(()=>{const h24=").append(options.use24Hour).append(",secs=").append(options.seconds)
                    .append(";function tick(){const d=new Date(),c=document.getElementById('clock'),x=document.getElementById('current-date');")
                    .append("if(c){let h=d.getHours(),s='';if(!h24){s=h<12?' AM':' PM';h=h%12||12}else h=String(h).padStart(2,'0');")
                    .append("c.textContent=h+':'+String(d.getMinutes()).padStart(2,'0')+(secs?':'+String(d.getSeconds()).padStart(2,'0'):'')+s;c.dateTime=d.toISOString()}")
                    .append("if(x){x.textContent=new Intl.DateTimeFormat(undefined,{weekday:'short',month:'short',day:'numeric'}).format(d);x.dateTime=d.toISOString()}")
                    .append("setTimeout(tick,secs?1000-d.getMilliseconds():60000-d.getSeconds()*1000-d.getMilliseconds())}tick()})()</script>");
        }
        return out.toString();
    }

    /**
     * Moves the complete text block, never the photo. The nine-position sequence is
     * deterministic from the wall-clock minute, so replacing the inline document
     * during a slideshow does not restart or randomize the burn-in protection.
     */
    private static String pixelShiftScript() {
        int a = PIXEL_SHIFT_AMPLITUDE_PX;
        return "<script>(()=>{const o=document.querySelector('.overlay'),p=[[0,0],[" + a + ",0],[" + a + "," + a
                + "],[0," + a + "],[-" + a + "," + a + "],[-" + a + ",0],[-" + a + ",-" + a
                + "],[0,-" + a + "],[" + a + ",-" + a + "]],m=" + PIXEL_SHIFT_INTERVAL_MS + ";"
                + "let timer=0,stopped=false;function stop(){stopped=true;if(timer)clearTimeout(timer)}"
                + "function shift(){if(stopped||!o)return;const n=Date.now(),v=p[Math.floor(n/m)%p.length];"
                + "o.style.transform='translate('+v[0]+'px,'+v[1]+'px)';timer=setTimeout(shift,m-n%m)}"
                + "addEventListener('pagehide',stop,{once:true});addEventListener('beforeunload',stop,{once:true});shift()})()</script>";
    }

    static String formatTime(Date date, boolean h24, boolean seconds, TimeZone zone) {
        java.util.Calendar calendar = java.util.Calendar.getInstance(zone == null ? TimeZone.getDefault() : zone, Locale.ROOT);
        calendar.setTime(date);
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        String suffix = "";
        if (!h24) { suffix = hour < 12 ? " AM" : " PM"; hour = hour % 12 == 0 ? 12 : hour % 12; }
        String result = (h24 && hour < 10 ? "0" : "") + hour + ":" + two(calendar.get(java.util.Calendar.MINUTE));
        if (seconds) result += ":" + two(calendar.get(java.util.Calendar.SECOND));
        return result + suffix;
    }

    static String formatCurrentDate(Date date, Locale locale, TimeZone zone) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale == null ? Locale.getDefault() : locale);
        format.setTimeZone(zone == null ? TimeZone.getDefault() : zone);
        return PhotoMetadata.boundedText(format.format(date), 80);
    }

    private static String two(int value) { return value < 10 ? "0" + value : Integer.toString(value); }
    private static String iso(Date value) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(value);
    }
    private static String html(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String message(String text) {
        String safe = html(text);
        return "<!doctype html><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;background:#000;color:#ddd;font:20px sans-serif;display:grid;place-items:center}"
                + "p{max-width:34rem;padding:2rem;text-align:center}</style><p>" + safe + "</p>";
    }
}
