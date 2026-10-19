package me.TreeOfSelf.PandaHeads;

import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.UUID;

public class SkinUtils {

    /**
     * Gets skin data from file.
     *
     * @param skinFilePath file path
     * @param useSlim whether slim format should be used
     * @return property containing skin value and signature if successful, otherwise null.
     */
    public static String[] setSkinFromFile(String skinFilePath, boolean useSlim) {
        File skinFile = new File(skinFilePath);
        try (FileInputStream input = new FileInputStream(skinFile)) {
            if (input.read() == 137) {
                try {
                    String reply = urlRequest(URI.create("https://api.mineskin.org/generate/upload?model=" + (useSlim ? "slim" : "steve")).toURL(), false, skinFile);
                    return getSkinFromReply(reply);
                } catch (IOException e) {
                    System.out.println("ERROR BIG ERROR");
                    System.out.println(e);
                }
            }
        } catch (IOException e) {
            System.out.println("ERROR BIG ERROR");
            System.out.println(e);
        }
        return null;
    }

    /**
     * Sets skin setting from the provided URL.
     *
     * @param skinUrl string url of the skin
     * @return property containing skin value and signature if successful, otherwise null.
     */
    @Nullable
    public static String[] fetchSkinByUrl(String skinUrl, boolean useSlim) {
        try {
            URL url = URI.create(String.format("https://api.mineskin.org/generate/url?url=%s&model=%s", URLEncoder.encode(skinUrl, StandardCharsets.UTF_8), useSlim ? "slim" : "steve")).toURL();
            String reply = urlRequest(url, false, null);
            return getSkinFromReply(reply);
        } catch (IOException e) {
            System.out.println("ERROR BIG ERROR");
            System.out.println(e);
        }
        return null;
    }

    /**
     * Sets skin by playername.
     *
     * @param playername name of the player who has the skin wanted
     * @return property containing skin value and signature if successful, otherwise null.
     */
    @Nullable
    public static String[] fetchSkinByName(String playername) {
        try {
            String reply = urlRequest(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playername).toURL(), true, null);

            if (reply == null || !reply.contains("id")) {
                reply = urlRequest(URI.create(String.format("http://skinsystem.ely.by/textures/signed/%s.png?proxy=true", playername)).toURL(), false, null);
            } else {
                String uuid = JsonParser.parseString(reply).getAsJsonObject().get("id").getAsString();
                reply = urlRequest(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false").toURL(), true, null);
            }
            return getSkinFromReply(reply);
        } catch (IOException e) {
            System.out.println("ERROR BIG ERROR");
            System.out.println(e);
        }
        return null;
    }

    public enum Status { OK, PENDING, FAILED }

    /** Skin lookup result; skin is {value, signature, name} when status is OK. */
    public record Lookup(Status status, @Nullable String[] skin) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private record CacheEntry(@Nullable String[] skin, long expires) {}

    private record Waiter(Executor executor, Runnable onReady) {}

    private static final long OK_TTL_MS = 30 * 60 * 1000L;
    private static final long FAILED_TTL_MS = 5 * 60 * 1000L;
    private static final long REQUEST_SPACING_MS = 1000L;
    private static final long RATE_LIMIT_BACKOFF_MS = 60 * 1000L;
    private static final int MAX_QUEUED = 64;
    private static final int MAX_CACHED = 4096;

    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();
    // uuid -> callbacks waiting for it; holds exactly the uuids in QUEUE
    private static final Map<UUID, List<Waiter>> PENDING = new HashMap<>();
    private static final LinkedBlockingQueue<UUID> QUEUE = new LinkedBlockingQueue<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static {
        Thread worker = new Thread(SkinUtils::workerLoop, "PandaHeads skin lookup");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Skin by UUID from Mojang's session server, without blocking. A cache miss queues the request
     * (one per second, paused on HTTP 429) and returns PENDING; onReady then runs on the executor
     * once the lookup has finished. Never call Mojang from the server thread directly.
     */
    public static Lookup lookupSkin(@Nullable UUID uuid, @Nullable Executor executor, @Nullable Runnable onReady) {
        if (uuid == null) return new Lookup(Status.FAILED, null);
        CacheEntry cached = CACHE.get(uuid);
        if (cached != null && cached.expires > System.currentTimeMillis()) {
            return new Lookup(cached.skin != null ? Status.OK : Status.FAILED, cached.skin);
        }
        synchronized (PENDING) {
            List<Waiter> waiters = PENDING.get(uuid);
            if (waiters == null) {
                if (PENDING.size() >= MAX_QUEUED) return new Lookup(Status.PENDING, null);
                waiters = new ArrayList<>();
                PENDING.put(uuid, waiters);
                QUEUE.add(uuid);
            }
            if (executor != null && onReady != null) waiters.add(new Waiter(executor, onReady));
        }
        return new Lookup(Status.PENDING, null);
    }

    private static void workerLoop() {
        long nextRequest = 0;
        while (true) {
            UUID uuid;
            try {
                uuid = QUEUE.take();
                long wait = nextRequest - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
            nextRequest = System.currentTimeMillis() + REQUEST_SPACING_MS;

            String[] skin = null;
            boolean retry = false;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    skin = getSkinFromReply(response.body().replaceAll("\\s", "")); // the reply is pretty-printed JSON
                } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    retry = true;
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                retry = true;
            }

            if (retry) {
                // Mojang is rate limiting or down: try this one again later, keep its waiters
                nextRequest = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS;
                QUEUE.add(uuid);
                continue;
            }

            if (CACHE.size() >= MAX_CACHED) CACHE.clear();
            CACHE.put(uuid, new CacheEntry(skin, System.currentTimeMillis() + (skin != null ? OK_TTL_MS : FAILED_TTL_MS)));
            List<Waiter> waiters;
            synchronized (PENDING) {
                waiters = PENDING.remove(uuid);
            }
            if (waiters != null) {
                for (Waiter waiter : waiters) waiter.executor.execute(waiter.onReady);
            }
        }
    }

    @Nullable
    public static UUID getUUIDFromComponentMap(DataComponentMap componentMap) {
        UUID uuid;
        if (componentMap.has(DataComponents.CUSTOM_DATA)
            && ((componentMap.get(DataComponents.CUSTOM_DATA).copyTag().contains("PublicBukkitValues")
            && componentMap.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("PublicBukkitValues").map(c -> c.contains("head-drop:headdrop-user")).orElse(false))
            || componentMap.get(DataComponents.CUSTOM_DATA).copyTag().contains("HeadDrops_Owner"))) {
            CompoundTag root = componentMap.get(DataComponents.CUSTOM_DATA).copyTag();
            if (root.getCompound("PublicBukkitValues").map(c -> c.contains("head-drop:headdrop-user")).orElse(false)) {
                uuid = UUID.fromString(root.getCompound("PublicBukkitValues").flatMap(c -> c.getString("head-drop:headdrop-user")).orElseThrow());
            } else {
                uuid = UUID.fromString(root.getString("HeadDrops_Owner").orElseThrow());
            }
        } else {
            if (componentMap.has(DataComponents.PROFILE)) {
                uuid = componentMap.get(DataComponents.PROFILE).partialProfile().id();
            } else {
                return null;
            }
        }
        return uuid;
    }

    public static boolean isHeadDrop(DataComponentMap componentMap) {
        if (!componentMap.has(DataComponents.CUSTOM_DATA)) return false;
        CompoundTag root = componentMap.get(DataComponents.CUSTOM_DATA).copyTag();
        return root.getCompound("PublicBukkitValues").map(c -> c.contains("head-drop:headdrop-user")).orElse(false)
            || root.contains("HeadDrops_Owner");
    }

    /**
     * Sets skin from reply that was got from API.
     * Used internally only.
     *
     * @param reply API reply
     * @return property containing skin value and signature if successful, otherwise null.
     */
    @Nullable
    protected static String[] getSkinFromReply(String reply) {
        if (reply == null || reply.contains("error") || reply.isEmpty() || !reply.contains("\"signature\":\"")) {
            return null;
        }

        String value = reply.split("\"value\":\"")[1].split("\"")[0];
        String signature = reply.split("\"signature\":\"")[1].split("\"")[0];
        String name = reply.split("\"name\":\"")[1].split("\"")[0];

        String[] returnValue = new String[3];
        returnValue[0] = value;
        returnValue[1] = signature;
        returnValue[2] = name;

        return returnValue;
    }

    /**
     * Gets reply from a skin website.
     * Used internally only.
     *
     * @param url url of the website
     * @param useGetMethod whether to use GET method instead of POST
     * @param image image to upload, otherwise null
     * @return reply from website as string
     * @throws IOException IOException is thrown when connection fails for some reason.
     */
    private static String urlRequest(URL url, boolean useGetMethod, @Nullable File image) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        String reply = null;

        if (connection instanceof HttpsURLConnection httpsConnection) {
            httpsConnection.setUseCaches(false);
            httpsConnection.setDoOutput(true);
            httpsConnection.setDoInput(true);
            httpsConnection.setRequestMethod(useGetMethod ? "GET" : "POST");
            if (image != null) {
                String boundary = UUID.randomUUID().toString();
                httpsConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                httpsConnection.setRequestProperty("User-Agent", "User-Agent");

                OutputStream outputStream = httpsConnection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

                final String LINE = "\r\n";
                writer.append("--").append(boundary).append(LINE);
                writer.append("Content-Disposition: form-data; name=\"file\"").append(LINE);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE);
                writer.append(LINE);
                writer.append(image.getName()).append(LINE);
                writer.flush();

                writer.append("--").append(boundary).append(LINE);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(image.getName()).append("\"").append(LINE);
                writer.append("Content-Type: image/png").append(LINE);
                writer.append("Content-Transfer-Encoding: binary").append(LINE);
                writer.append(LINE);
                writer.flush();

                byte[] fileBytes = Files.readAllBytes(image.toPath());
                outputStream.write(fileBytes, 0, fileBytes.length);

                outputStream.flush();
                writer.append(LINE);
                writer.flush();

                writer.append("--").append(boundary).append("--").append(LINE);
                writer.close();
            }

            if (httpsConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reply = getContent(connection);
            }
            httpsConnection.disconnect();
        } else {
            reply = getContent(connection);
        }
        return reply;
    }

    /**
     * Reads response from API.
     * Used just to avoid duplicate code.
     *
     * @param connection connection where to take output stream from
     * @return API reply as String
     * @throws IOException exception when something went wrong
     */
    private static String getContent(URLConnection connection) throws IOException {
        try (
                InputStream is = connection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                Scanner scanner = new Scanner(isr)
        ) {
            StringBuilder reply = new StringBuilder();
            while (scanner.hasNextLine()) {
                String line = scanner.next();
                if (line.trim().isEmpty())
                    continue;
                reply.append(line);
            }

            return reply.toString();
        }
    }
}
