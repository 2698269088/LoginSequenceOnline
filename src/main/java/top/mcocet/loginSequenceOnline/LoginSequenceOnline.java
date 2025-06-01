package top.mcocet.loginSequenceOnline;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class LoginSequenceOnline extends JavaPlugin {
    private DatagramSocket socket;
    private int port;
    private volatile boolean running;

    @Override
    public void onEnable() {
        getLogger().info("LoginSequenceOnline 插件已启用");

        // 检查并创建配置文件
        File configFile = new File(getDataFolder(), "Config.yml");
        if (!configFile.exists()) {
            getLogger().info("配置文件不存在，正在创建...");
            try {
                if (!getDataFolder().exists()) {
                    getDataFolder().mkdirs();
                }
                configFile.createNewFile();
                getLogger().info("配置文件创建成功");
            } catch (IOException e) {
                getLogger().severe("创建配置文件时出错");
                e.printStackTrace();
                return;
            }
        }

        // 读取配置文件中的端口信息，如果没有则设置默认值
        port = getConfig().getInt("Port", 1234); // 如果没有找到 Port，则默认为 1234
        if (port == 1234) {
            getConfig().set("Port", 1234); // 如果是默认值，写入配置文件
            saveConfig();
        }

        // 启动 UDP 监听线程
        running = true; // 初始化运行状态
        new Thread(this::startUDPListener).start();
    }

    private void startUDPListener() {
        try {
            socket = new DatagramSocket(port);
            getLogger().info("UDP 服务已启动，监听端口：" + port);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running) { // 修改循环条件为运行状态标志位
                socket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);

                if (message.contains("LoginSequence-Hello")) {
                    getLogger().info("收到 'LoginSequence-Hello' 消息，来自：" + packet.getAddress().getHostAddress() + ":" + packet.getPort());

                    // 构造响应消息
                    String response = "LoginSequence-Online";
                    byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
                    socket.send(responsePacket); // 发送响应
                    getLogger().info("已发送 'LoginSequence-Online' 响应");
                }
                // 返回服务器信息
                if (message.contains("LoginSequence-Info")) {
                    getLogger().info("收到服务器信息请求，来自：" + packet.getAddress().getHostAddress());
                    long memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    int onlinePlayers = getServer().getOnlinePlayers().size();
                    double tps;
                    try {
                        // 通过反射获取MinecraftServer实例
                        Object minecraftServer = getServer().getClass().getMethod("getServer").invoke(getServer());
                        // 获取TPS数组（索引0对应最近1分钟的TPS）
                        double[] recentTps = (double[]) minecraftServer.getClass().getField("recentTps").get(minecraftServer);
                        tps = recentTps[0];
                    } catch (Exception e) {
                        getLogger().warning("获取TPS失败，错误：" + e.getMessage());
                        tps = 0.0;
                    }

                    // 构造响应（TPS 保留 1 位小数）
                    String response = String.format("LoginSequence-Data Memory:%dMB Online:%d TPS:%.1f",
                            memUsed, onlinePlayers, tps);

                    byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length,
                            packet.getAddress(), packet.getPort());
                    socket.send(responsePacket);
                    getLogger().info("已发送服务器状态：" + response);
                }

            }
        } catch (SocketException e) {
            if (running) { // 仅在非正常关闭时记录错误
                getLogger().severe("UDP 监听时出错");
                e.printStackTrace();
            }
        } catch (Exception e) {
            getLogger().severe("UDP 监听时出错");
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("LoginSequenceOnline 插件已禁用");
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close(); // 关闭socket
        }
        getLogger().info("UDP监听服务已关闭");
    }
}