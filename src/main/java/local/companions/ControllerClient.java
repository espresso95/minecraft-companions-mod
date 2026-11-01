package local.companions;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;

final class ControllerClient {
    private final net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private URI endpoint;
    private String token;
    volatile JsonObject state = new JsonObject();
    volatile JsonObject catalog = new JsonObject();
    volatile String error = "Start the local companions controller";
    private boolean fetching, catalogFetching;
    private long stateVersion;
    private volatile boolean authorizationExpired;

    private synchronized void configure() throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("companion-mailbox.json");
        if (!Files.isRegularFile(file)) throw new Exception("Not paired. Run npm run companions:pair for this Minecraft folder.");
        JsonObject config = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        URI target = LocalConnection.endpoint(config.get("endpoint").getAsString());
        if (!ControllerClient.text(config, "serverKey", "").equals(LocalConnection.key(client))) throw new Exception("Pair this device with the Minecraft server you joined");
        String credential = config.get("token").getAsString();
        if (!credential.equals(token)) authorizationExpired = false;
        if (authorizationExpired) throw new Exception("Pair this device again; its access was revoked");
        if (!credential.matches("[A-Za-z0-9_.-]{32,1024}")) throw new Exception("Invalid controller pairing token");
        endpoint = target;
        token = credential;
    }

    CompletableFuture<JsonObject> request(String method, String path, JsonObject body) {
        var result = new CompletableFuture<JsonObject>();
        client.execute(() -> {
            try {
                configure();
                send(endpoint, method, path, body, token).whenComplete((value, failure) -> {
                    if (failure != null) result.completeExceptionally(failure); else result.complete(value);
                });
            } catch (Exception e) { result.completeExceptionally(e); }
        });
        return result;
    }

    private CompletableFuture<JsonObject> send(URI target, String method, String path, JsonObject body, String credential) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target.resolve(path)).timeout(Duration.ofSeconds(body == null ? 5 : 60)).header("Accept", "application/json");
        if (credential != null) builder.header("Authorization", "Bearer " + credential);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
        if (body != null) builder.header("Content-Type", "application/json");
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() == 401) authorizationExpired = true;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new CompletionException(new Exception(result.has("error") ? result.get("error").getAsString() : "Controller returned " + response.statusCode()));
            return result;
        });
    }
    boolean paired() { try { configure(); return true; } catch (Exception e) { return false; } }
    CompletableFuture<JsonObject> pair(String address, String code) {
        try {
            URI target = LocalConnection.endpoint(address);
            var client = net.minecraft.client.MinecraftClient.getInstance();
            String key = LocalConnection.key(client); if (key.isEmpty()) throw new Exception("Join the host's local Minecraft server first");
            JsonObject body = new JsonObject(); body.addProperty("player", client.getSession().getUsername()); body.addProperty("code", code); body.addProperty("kind", "minecraft");
            return send(target, "POST", "/v1/pair", body, null).thenApply(result -> {
                JsonObject config = new JsonObject(); config.addProperty("endpoint", target.toString()); config.add("token", result.get("token")); config.addProperty("serverKey", key);
                Path file = FabricLoader.getInstance().getConfigDir().resolve("companion-mailbox.json");
                try { Files.createDirectories(file.getParent()); Files.writeString(file, config.toString()); try { Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException ignored) {} }
                catch (Exception e) { throw new CompletionException(e); }
                synchronized (this) { stateVersion++; fetching = false; authorizationExpired = false; state = new JsonObject(); catalog = new JsonObject(); error = null; }
                refresh(); return result;
            });
        } catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }
    synchronized void refresh() {
        if (catalog.size() == 0 && !catalogFetching) {
            catalogFetching = true;
            request("GET", "/v1/models", null).whenComplete((result, failure) -> {
                synchronized (this) { catalogFetching = false; if (failure == null) catalog = result; else error = message(failure); }
            });
        }
        if (!fetching) fetchState();
    }
    synchronized CompletableFuture<JsonObject> fetchState() {
        fetching = true; final long version = ++stateVersion;
        return request("GET", "/v1/state", null).whenComplete((result, failure) -> {
            synchronized (this) {
                if (version != stateVersion) return;
                fetching = false;
                if (failure != null) { state = new JsonObject(); catalog = new JsonObject(); error = message(failure); }
                else {
                    if (!text(catalog, "version", "").equals(text(result, "modelCatalogVersion", ""))) catalog = new JsonObject();
                    state = result; error = null;
                }
            }
        });
    }

    static String statusLabel(JsonObject agent) {
        return text(object(agent, "status"), "label", "Offline");
    }
    String statusFor(String name) {
        if (error != null) return "";
        var agent = agent(name);
        return agent.size() == 0 ? "" : statusLabel(agent);
    }

    static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        String text = failure.getMessage();
        if (text == null || text.isBlank()) return "Controller unavailable. Start Play Companions.command.";
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    JsonArray agents() { return state.has("agents") ? state.getAsJsonArray("agents") : new JsonArray(); }
    JsonObject work(String name) { return object(name == null ? state : agent(name), "work"); }
    String webAddress() { return endpoint != null && !("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost())) ? endpoint.toString() : text(object(state, "access"), "webUrl", "http://127.0.0.1:8767"); }
    JsonObject agent(String name) { for (var value : agents()) if (name != null && name.equalsIgnoreCase(text(value.getAsJsonObject(), "name", ""))) return value.getAsJsonObject(); return new JsonObject(); }
    java.util.List<String> actions(String name) { var result = new java.util.ArrayList<String>(); var a = agent(name); if (a.has("controls")) for (var value : a.getAsJsonArray("controls")) result.add(value.getAsString()); return result; }
    JsonArray models() { return catalog.has("models") ? catalog.getAsJsonArray("models") : new JsonArray(); }
    JsonObject model(String id) { for (var value : models()) if (id.equals(text(value.getAsJsonObject(), "id", ""))) return value.getAsJsonObject(); return new JsonObject(); }
    static JsonObject object(JsonObject parent, String key) { return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject(); }
    static String text(JsonObject value, String key, String fallback) { return value != null && value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : fallback; }
    static long count(JsonObject value, String key) { try { return value.get(key).getAsLong(); } catch (Exception e) { return 0; } }
    static String compact(long n) { return n >= 1_000_000 ? String.format(java.util.Locale.ROOT, "%.2fM", n / 1e6) : n >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", n / 1e3) : Long.toString(n); }
}
