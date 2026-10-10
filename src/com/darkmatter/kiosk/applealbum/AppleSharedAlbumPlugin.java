package com.darkmatter.kiosk.applealbum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    static final long DEFAULT_INTERVAL_SECONDS = 30L;
    static final long MIN_INTERVAL_SECONDS = 5L;
    static final long MAX_INTERVAL_SECONDS = 300L;
    static final long INTERVAL_STEP_SECONDS = 5L;
    private final AtomicLong generation = new AtomicLong();
    private final Random random = new Random();
    private final AppleAlbumClient.Transport testTransport;
    private volatile PluginHost host;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pending;
    private AppleAlbumClient client;
    private volatile boolean running;
    private volatile String albumUrl = "";
    private volatile String order = "Shuffle";
    private volatile String fit = "Fit whole photo";
    private volatile String transition = "Fade";
    private volatile String motion = "None";
    private volatile long intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private volatile double transitionSeconds = 1.0;
    private volatile long refreshMillis = 15 * 60 * 1000L;
    private Boolean screenOn;
    private Boolean screensaverActive;
    private int screenRevision;
    private int saverRevision;
    private List<AppleAlbumClient.ImageRef> images = Collections.emptyList();
    private int cursor;
    private long loadedAt;
    private AppleAlbumClient.ImageRef currentRef;
    private byte[] currentJpeg;
    private String lastShownId = "";

    public AppleSharedAlbumPlugin() { this(null); }
    AppleSharedAlbumPlugin(AppleAlbumClient.Transport testTransport) { this.testTransport = testTransport; }

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "apple-album-worker"); thread.setDaemon(true); return thread;
            }
        });
        client = new AppleAlbumClient(testTransport == null ? new SafeHttp() : testTransport);
        applySettings(settings);
        publishInitial();
        try {
            host.subscribe("screen.state");
            host.subscribe("screensaver.state");
            readInitialState(host, "isScreenOn", true, screenRevision);
            readInitialState(host, "isScreensaverActive", false, saverRevision);
        } catch (UnsupportedOperationException ignored) {
            screenOn = Boolean.TRUE; screensaverActive = Boolean.TRUE;
        }
        schedule(0, generation.incrementAndGet());
    }

    private void readInitialState(final PluginHost sourceHost, String command, final boolean screen, final int revision) {
        sourceHost.executeCommand(command, Collections.<String, Object>emptyMap(), new PluginHost.CommandCallback() {
            @Override public void onResult(boolean ok, Object data, String error) {
                synchronized (AppleSharedAlbumPlugin.this) {
                    if (!running || host != sourceHost || !ok) return;
                    if (screen && revision == screenRevision) screenOn = Boolean.TRUE.equals(data);
                    if (!screen && revision == saverRevision) screensaverActive = Boolean.TRUE.equals(data);
                    visibilityChangedLocked();
                }
            }
        });
    }

    @Override public synchronized void configure(Map<String, Object> settings) {
        String previousUrl = albumUrl;
        applySettings(settings);
        if (!albumUrl.equals(previousUrl)) {
            clearAlbumLocked();
            publishInitial();
        }
        loadedAt = 0;
        restartLocked(albumUrl.isEmpty() ? "Add a public Apple Shared Album link." : "Loading public album…");
    }

    @Override public synchronized void execute(String command, Map<String, Object> arguments) {
        if (!"refresh".equals(command)) throw new IllegalArgumentException("Unknown command");
        loadedAt = 0;
        restartLocked("Refreshing public album…");
    }

    @Override public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("ks.screen.state".equals(event)) {
            screenRevision++; screenOn = Boolean.TRUE.equals(payload.get("on")); visibilityChangedLocked();
        } else if ("ks.screensaver.state".equals(event)) {
            saverRevision++; screensaverActive = Boolean.TRUE.equals(payload.get("active")); visibilityChangedLocked();
        }
    }

    @Override public synchronized void stop() {
        running = false; generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        if (client != null) client.cancel();
        if (executor != null) executor.shutdownNow();
        try { if (host != null) host.removeScreensaver(RENDERER_KEY); } catch (RuntimeException ignored) { }
        host = null; client = null; executor = null; pending = null; clearAlbumLocked();
    }

    private void restartLocked(String message) {
        long current = generation.incrementAndGet();
        if (client != null) client.cancel();
        if (pending != null) pending.cancel(true);
        status(message, false);
        if (workAllowedLocked()) schedule(0, current);
    }

    private void visibilityChangedLocked() {
        long current = generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        if (!workAllowedLocked()) {
            if (client != null) client.cancel();
            status("Paused while the screensaver is inactive or the screen is off.", false);
        } else {
            status(currentJpeg == null ? "Connecting to public album…" : "Connected · " + images.size() + " photos", false);
            schedule(0, current);
        }
    }

    private boolean workAllowedLocked() {
        return running && !Boolean.FALSE.equals(screenOn) && !Boolean.FALSE.equals(screensaverActive);
    }

    private void applySettings(Map<String, Object> settings) {
        albumUrl = stringSetting(settings, "albumUrl", "").trim();
        order = selectSetting(settings, "order", "Shuffle", "Shuffle", "Oldest first", "Newest first");
        fit = selectSetting(settings, "fit", "Fit whole photo", "Fit whole photo", "Fill screen");
        transition = selectSetting(settings, "transition", "Fade", "None", "Fade", "Slide");
        motion = selectSetting(settings, "motion", "None", "None", "Ken Burns");
        intervalSeconds = boundedIntervalSeconds(settings == null ? null : settings.get("intervalSeconds"));
        transitionSeconds = boundedNumber(settings == null ? null : settings.get("transitionSeconds"), 1.0, 0.2, 3.0, 0.2);
        double refresh = boundedNumber(settings == null ? null : settings.get("refreshMinutes"), 15, 1, 120, 1);
        refreshMillis = (long) refresh * 60_000L;
    }

    private void publishInitial() {
        if (host == null) return;
        if (albumUrl.isEmpty()) {
            host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE, SlideshowHtml.message("Add a public Apple Shared Album link in the plugin settings."));
            status("Add a public Apple Shared Album link.", false);
        } else {
            host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE, SlideshowHtml.message("Loading public album…"));
            status("Loading public album…", false);
        }
    }

    private synchronized void schedule(long delaySeconds, final long expectedGeneration) {
        if (!workAllowedLocked() || executor == null || expectedGeneration != generation.get() || albumUrl.isEmpty()) return;
        pending = executor.schedule(new Runnable() { @Override public void run() { cycle(expectedGeneration); } }, delaySeconds, TimeUnit.SECONDS);
    }

    private void cycle(long expectedGeneration) {
        CancellationToken cancel = new CancellationToken(expectedGeneration);
        try {
            cancel.check();
            final AppleAlbumClient currentClient;
            final String requestedUrl;
            final String requestedOrder;
            final boolean reload;
            synchronized (this) {
                cancel.check(); currentClient = client; requestedUrl = albumUrl; requestedOrder = order;
                reload = images.isEmpty() || System.currentTimeMillis() - loadedAt >= refreshMillis;
            }
            if (currentClient == null) throw new AlbumException(AlbumException.Code.CANCELLED);
            if (reload) {
                List<AppleAlbumClient.ImageRef> loaded = new ArrayList<>(currentClient.list(requestedUrl, cancel));
                synchronized (this) { cancel.check(); reconcileLocked(loaded, requestedOrder); loadedAt = System.currentTimeMillis(); }
            }
            AlbumException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                cancel.check();
                AppleAlbumClient.ImageRef image;
                synchronized (this) { image = nextImageLocked(); }
                if (image == null) throw new AlbumException(AlbumException.Code.EMPTY);
                try {
                    byte[] source = currentClient.image(image, cancel); cancel.check();
                    String chosenTransition = transition;
                    int incomingBudget = "None".equals(chosenTransition) ? PhotoEncoder.SINGLE_IMAGE_BYTES : PhotoEncoder.TRANSITION_INCOMING_BYTES;
                    byte[] incoming = PhotoEncoder.fit(source, incomingBudget); source = null;
                    byte[] outgoing = null; byte[] previous;
                    synchronized (this) { previous = currentJpeg; }
                    if (previous != null && !"None".equals(chosenTransition)) outgoing = PhotoEncoder.fit(previous, PhotoEncoder.TRANSITION_OUTGOING_BYTES);
                    String html = SlideshowHtml.image(incoming, outgoing, fit, chosenTransition, transitionSeconds, motion, intervalSeconds);
                    publishIfCurrent(expectedGeneration, html, images.size(), image, incoming);
                    schedule(intervalSeconds, expectedGeneration); return;
                } catch (AlbumException error) {
                    if (error.code == AlbumException.Code.CANCELLED) throw error; last = error;
                }
            }
            throw last == null ? new AlbumException(AlbumException.Code.EMPTY) : last;
        } catch (AlbumException error) {
            if (error.code != AlbumException.Code.CANCELLED && isCurrent(expectedGeneration)) {
                synchronized (this) { loadedAt = 0; }
                statusIfCurrent(expectedGeneration, error.publicMessage()); schedule(Math.min(60, intervalSeconds), expectedGeneration);
            }
        } catch (RuntimeException ignored) {
            if (isCurrent(expectedGeneration)) {
                synchronized (this) { loadedAt = 0; }
                statusIfCurrent(expectedGeneration, "Could not load the public album. Check the connection and try again.");
                schedule(Math.min(60, intervalSeconds), expectedGeneration);
            }
        }
    }

    private void reconcileLocked(List<AppleAlbumClient.ImageRef> loaded, String requestedOrder) {
        Set<String> ids = new HashSet<>(); List<AppleAlbumClient.ImageRef> unique = new ArrayList<>();
        for (AppleAlbumClient.ImageRef image : loaded) if (ids.add(image.id)) unique.add(image);
        if ("Oldest first".equals(requestedOrder)) Collections.reverse(unique);
        if ("Shuffle".equals(requestedOrder)) shuffleAvoidingFirst(unique, lastShownId, random);
        boolean currentStillExists = currentRef != null && ids.contains(currentRef.id);
        images = unique; cursor = 0;
        if (currentStillExists) {
            for (int i = 0; i < images.size(); i++) if (images.get(i).id.equals(currentRef.id)) { cursor = i + 1; break; }
        } else if (currentRef != null) { currentRef = null; currentJpeg = null; }
    }

    private AppleAlbumClient.ImageRef nextImageLocked() {
        if (images.isEmpty()) return null;
        if (cursor >= images.size()) { cursor = 0; if ("Shuffle".equals(order)) shuffleAvoidingFirst(images, lastShownId, random); }
        if (images.size() > 1 && images.get(cursor).id.equals(lastShownId)) cursor = (cursor + 1) % images.size();
        return images.get(cursor++);
    }

    static void shuffleAvoidingFirst(List<AppleAlbumClient.ImageRef> list, String previousId, Random random) {
        Collections.shuffle(list, random);
        if (list.size() > 1 && list.get(0).id.equals(previousId)) Collections.swap(list, 0, 1);
    }

    private synchronized boolean isCurrent(long expected) { return workAllowedLocked() && generation.get() == expected; }

    private synchronized void publishIfCurrent(long expected, String html, int count, AppleAlbumClient.ImageRef image, byte[] incoming) throws AlbumException {
        if (!isCurrent(expected) || host == null) throw new AlbumException(AlbumException.Code.CANCELLED);
        host.publishScreensaver(RENDERER_KEY, RENDERER_TITLE, html);
        currentRef = image; currentJpeg = incoming; lastShownId = image.id;
        host.status("Connected · " + count + " photos", false);
    }

    private synchronized void statusIfCurrent(long expected, String text) {
        if (generation.get() == expected && running && host != null) try { host.status(text, true); } catch (RuntimeException ignored) { }
    }

    private void clearAlbumLocked() {
        images = Collections.emptyList(); cursor = 0; loadedAt = 0; currentRef = null; currentJpeg = null; lastShownId = "";
    }

    private void status(String text, boolean error) {
        PluginHost current = host; if (current != null) try { current.status(text, error); } catch (RuntimeException ignored) { }
    }

    private static String stringSetting(Map<String, Object> settings, String key, String fallback) {
        Object value = settings == null ? null : settings.get(key); return value instanceof String ? (String) value : fallback;
    }
    private static String selectSetting(Map<String, Object> settings, String key, String fallback, String... allowed) {
        String value = stringSetting(settings, key, fallback); for (String option : allowed) if (option.equals(value)) return value; return fallback;
    }
    static long boundedIntervalSeconds(Object value) {
        return Math.round(boundedNumber(value, DEFAULT_INTERVAL_SECONDS, MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS, INTERVAL_STEP_SECONDS));
    }
    static double boundedNumber(Object value, double fallback, double min, double max, double step) {
        if (!(value instanceof Number)) return fallback;
        double number = ((Number) value).doubleValue(); if (!Double.isFinite(number)) return fallback;
        number = Math.max(min, Math.min(max, number));
        double snapped = min + Math.round((number - min) / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    private final class CancellationToken implements AppleAlbumClient.Cancellation {
        private final long expected;
        CancellationToken(long expected) { this.expected = expected; }
        @Override public void check() throws AlbumException {
            if (!isCurrent(expected) || Thread.currentThread().isInterrupted()) throw new AlbumException(AlbumException.Code.CANCELLED);
        }
    }
}
