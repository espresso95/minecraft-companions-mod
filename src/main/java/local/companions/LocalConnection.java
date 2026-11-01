package local.companions;

import net.minecraft.client.MinecraftClient;
import java.net.*;

final class LocalConnection {
    static boolean privateAddress(InetAddress value) { return value.isLoopbackAddress() || value.isSiteLocalAddress() || value.isLinkLocalAddress() || (value.getAddress().length == 16 && (value.getAddress()[0] & 0xFE) == 0xFC); }
    static InetSocketAddress server(MinecraftClient client) {
        if (client.world == null || client.isInSingleplayer() || client.getNetworkHandler() == null) return null;
        var address = client.getNetworkHandler().getConnection().getAddress(); return address instanceof InetSocketAddress a && privateAddress(a.getAddress()) ? a : null;
    }
    static String key(MinecraftClient client) { var value = server(client); return value == null ? "" : (value.getAddress().isLoopbackAddress() ? "loopback" : value.getAddress().getHostAddress()) + ":" + value.getPort(); }
    static URI endpoint(String text) throws Exception {
        URI value = URI.create(text.contains("://") ? text : "http://" + text);
        String host = value.getHost();
        if (!"http".equals(value.getScheme()) || host == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null || !(value.getPath().isEmpty() || value.getPath().equals("/")) || value.getPort() < 1 || value.getPort() > 65535) throw new Exception("Enter the host's local control address, including its port");
        boolean local = host.equalsIgnoreCase("localhost") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".local");
        if (!local && host.matches("[0-9a-fA-F:.\\[\\]]+")) local = privateAddress(InetAddress.getByName(host));
        if (!local) throw new Exception("Companions connects to local or private LAN addresses only");
        return value;
    }
}
