package top.mcocet.loginSequenceOnline;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPNet {
    private final JavaPlugin plugin;
    private DatagramSocket socket;
    private volatile boolean running;
    private int port;

    public UDPNet(JavaPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void startListening(PacketHandler handler) {
        running = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                plugin.getLogger().info("UDP服务已启动，端口：" + port);

                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (running) {
                    socket.receive(packet);
                    String message = new String(packet.getData(),
                            packet.getOffset(),
                            packet.getLength(),
                            StandardCharsets.UTF_8);

                    handler.handlePacket(message, packet.getAddress(), packet.getPort());
                }
            } catch (SocketException e) {
                if (running) plugin.getLogger().severe("Socket异常: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("UDP监听错误: " + e.getMessage());
            } finally {
                closeSocket();
            }
        }).start();
    }

    public void sendResponse(String message, InetAddress address, int port) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);

            if (isFolia()) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                    try {
                        socket.send(packet);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Folia发送失败： " + address.getHostAddress()
                                + ":" + port + " - " + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
                    }
                });
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        socket.send(packet);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Bukkit发送失败： " + address.getHostAddress()
                                + ":" + port + " - " + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().severe("发送响应失败: " + e.getMessage());
        }
    }

    boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void closeSocket() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public interface PacketHandler {
        void handlePacket(String message, InetAddress address, int port);
    }
}
