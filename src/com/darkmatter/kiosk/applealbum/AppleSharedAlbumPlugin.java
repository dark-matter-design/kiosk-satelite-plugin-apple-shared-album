package com.darkmatter.kiosk.applealbum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

public final class AppleSharedAlbumPlugin implements KioskPlugin {
    private static final String RENDERER_KEY = "apple_album";
    private static final String RENDERER_TITLE = "Apple Shared Album";
    private static final long REFRESH_MS = 15 * 60 * 1000L;
    private final AtomicLong generation = new AtomicLong();
    private final Random random = new Random();
    private volatile PluginHost host;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pending;
    private AppleAlbumClient client;
    private final AppleAlbumClient.Transport testTransport;
    private volatile boolean running;
    private volatile String albumUrl = "";
    private volatile String order = "Shuffle";
    private volatile String fit = "Fit whole photo";
    private volatile int intervalSeconds = 30;
    private List<AppleAlbumClient.ImageRef> images = Collections.emptyList();
    private int cursor;
    private long loadedAt;

    public AppleSharedAlbumPlugin() { this(null); }

    AppleSharedAlbumPlugin(AppleAlbumClient.Transport testTransport) {
        this.testTransport = testTransport;
    }

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        this.running = true;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "apple-album-worker");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.client = new AppleAlbumClient(testTransport == null ? new SafeHttp() : testTransport);
        applySettings(settings);
        publishInitial();
        schedule(0, generation.incrementAndGet());
    }

    @Override public synchronized void configure(Map<String, Object> settings) {
        applySettings(settings);
        images = Collections.emptyList(); cursor = 0; loadedAt = 0;
        long current = generation.incrementAndGet();
        if (client != null) client.cancel();
        if (pending != null) pending.cancel(true);
        publishInitial();
        schedule(0, current);
    }

    @Override public synchronized void execute(String command, Map<String, Object> arguments) {
        if (!"refresh".equals(command)) return;
        images = Collections.emptyList(); loadedAt = 0;
        long current = generation.incrementAndGet();
        client.cancel();
        if (pending != null) pending.cancel(true);
        status("Refreshing public album…", false);
        schedule(0, current);
    }

    @Override public void onEvent(String event, Map<String, Object> payload) { }

    @Override public synchronized void stop() {
        running = false;
        generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        if (client != null) client.cancel();
        if (executor != null) executor.shutdownNow();
        try { if (host != null) host.removeScreensaver(RENDERER_KEY); } catch (RuntimeException ignored) { }
        host = null; client = null; executor = null; pending = null;
        images = Collections.emptyList();
    }

    private void applySettings(Map<String, Object> settings) {
        albumUrl = stringSetting(settings, "albumUrl", "").trim();
        order = stringSetting(settings, "order", "Shuffle");
        fit = stringSetting(settings, "fit", "Fit whole photo");
        intervalSeconds = Math.max(10, Math.min(300, intSetting(settings, "intervalSeconds", 30)));
    }

    private void publishInitial() {
        if (host == null) return;
        if (albumUrl.isEmpty()) {
            host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE,
                    SlideshowHtml.message("Add a public Apple Shared Album link in the plugin settings."));
            status("Add a public Apple Shared Album link.", false);
        } else {
            host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE, SlideshowHtml.message("Loading public album…"));
            status("Loading public album…", false);
        }
    }

    private synchronized void schedule(long delaySeconds, final long expectedGeneration) {
        if (!running || executor == null || expectedGeneration != generation.get()) return;
        pending = executor.schedule(new Runnable() {
            @Override public void run() { cycle(expectedGeneration); }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void cycle(long expectedGeneration) {
        CancellationToken cancel = new CancellationToken(expectedGeneration);
        try {
            cancel.check();
            final AppleAlbumClient currentClient;
            final String requestedUrl;
            final String requestedOrder;
            final boolean reload;
            List<AppleAlbumClient.ImageRef> localImages;
            synchronized (this) {
                cancel.check();
                if (albumUrl.isEmpty()) return;
                currentClient = client;
                requestedUrl = albumUrl;
                requestedOrder = order;
                reload = images.isEmpty() || System.currentTimeMillis() - loadedAt >= REFRESH_MS;
                localImages = images;
            }
            if (currentClient == null) throw new AlbumException(AlbumException.Code.CANCELLED);
            if (reload) {
                List<AppleAlbumClient.ImageRef> loaded = new ArrayList<>(currentClient.list(requestedUrl, cancel));
                if ("Oldest first".equals(requestedOrder)) Collections.reverse(loaded);
                if ("Shuffle".equals(requestedOrder)) Collections.shuffle(loaded, random);
                synchronized (this) {
                    cancel.check();
                    images = loaded; cursor = 0; loadedAt = System.currentTimeMillis();
                    localImages = images;
                }
            }
            AlbumException last = null;
            for (int attempt = 0; attempt < Math.min(3, localImages.size()); attempt++) {
                cancel.check();
                AppleAlbumClient.ImageRef image;
                synchronized (this) {
                    if (cursor >= images.size()) {
                        cursor = 0;
                        if ("Shuffle".equals(order)) Collections.shuffle(images, random);
                    }
                    image = images.get(cursor++);
                }
                try {
                    byte[] source = currentClient.image(image, cancel);
                    cancel.check();
                    byte[] display = PhotoEncoder.fit(source);
                    source = null;
                    String html = SlideshowHtml.image(display, fit);
                    display = null;
                    publishIfCurrent(expectedGeneration, html, localImages.size());
                    schedule(intervalSeconds, expectedGeneration);
                    return;
                } catch (AlbumException error) {
                    if (error.code == AlbumException.Code.CANCELLED) throw error;
                    last = error;
                }
            }
            throw last == null ? new AlbumException(AlbumException.Code.EMPTY) : last;
        } catch (AlbumException error) {
            if (error.code != AlbumException.Code.CANCELLED && isCurrent(expectedGeneration)) {
                statusIfCurrent(expectedGeneration, error.publicMessage());
                schedule(Math.min(60, intervalSeconds), expectedGeneration);
            }
        } catch (RuntimeException ignored) {
            if (isCurrent(expectedGeneration)) {
                statusIfCurrent(expectedGeneration, "Could not load the public album. Check the connection and try again.");
                schedule(Math.min(60, intervalSeconds), expectedGeneration);
            }
        }
    }

    private boolean isCurrent(long expected) { return running && generation.get() == expected; }

    /** Configure/stop use the same monitor, so a stale session cannot cross this publication gate. */
    private synchronized void publishIfCurrent(long expected, String html, int count) throws AlbumException {
        if (!isCurrent(expected) || host == null) throw new AlbumException(AlbumException.Code.CANCELLED);
        host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE, html);
        host.status("Connected · " + count + " photos", false);
    }

    private synchronized void statusIfCurrent(long expected, String text) {
        if (isCurrent(expected) && host != null) {
            try { host.status(text, true); } catch (RuntimeException ignored) { }
        }
    }

    private void status(String text, boolean error) {
        PluginHost current = host;
        if (current != null) try { current.status(text, error); } catch (RuntimeException ignored) { }
    }

    private static String stringSetting(Map<String, Object> settings, String key, String fallback) {
        Object value = settings == null ? null : settings.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static int intSetting(Map<String, Object> settings, String key, int fallback) {
        Object value = settings == null ? null : settings.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private final class CancellationToken implements AppleAlbumClient.Cancellation {
        private final long expected;
        CancellationToken(long expected) { this.expected = expected; }
        @Override public void check() throws AlbumException {
            if (!isCurrent(expected) || Thread.currentThread().isInterrupted()) {
                throw new AlbumException(AlbumException.Code.CANCELLED);
            }
        }
    }
}
