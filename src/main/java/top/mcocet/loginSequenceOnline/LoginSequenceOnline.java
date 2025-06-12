package top.mcocet.loginSequenceOnline;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

public class LoginSequenceOnline extends JavaPlugin {
    private UDPNet udpNet;
    private int port;
    private  int serverVersion;

    @Override
    public void onEnable() {
        // 初始化日志和配置
        printLogo();
        createConfig();

        // 初始化网络模块
        port = getConfig().getInt("Port", 1234);
        udpNet = new UDPNet(this, port);
        udpNet.startListening(this::handleIncomingPacket);

        getLogger().info("LoginSequenceOnline已启用！");
        getLogger().info("LoginSequenceOnline已支持Folia！");

        if (udpNet.isFolia()){
            getLogger().info("检测到服务器为Folia类！");
            // 获取服务器版本字符串（示例格式：1.21.4-R0.1-SNAPSHOT）
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            // 转换为数字版本号
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            // 检查是否低于1.21.4
            // 此API在Folia 1.21.4中添加
            if (major < 1 || (major == 1 && (minor < 21 || (minor == 21 && patch < 4)))) {
                serverVersion = 0;
                sayLog(ChatColor.RED + "Folia版本过低，将无法获取服务器TPS信息！");
                sayLog(ChatColor.YELLOW + "请升级至Folia 1.21.4+ 或使用Bukkit类服务器！");
            } else {
                serverVersion = 1;
            }
        } else {
            getLogger().info("检测到服务器为Bukkit类！");
        }
    }

    private void handleIncomingPacket(String message, InetAddress address, int port) {
        if (message.contains("LoginSequence-Hello")) {
            processHelloPacket(address, port);
        } else if (message.contains("LoginSequence-Info")) {
            processInfoPacket(address, port);
        }
    }

    private void processHelloPacket(InetAddress address, int port) {
        getLogger().info("收到 “LoginSequence-Hello” 消息，来自：" + address.getHostAddress() + ":" + port);
        udpNet.sendResponse("LoginSequence-Online", address, port);
        sayLog(ChatColor.GREEN + "已发送 “LoginSequence-Online” 响应");
    }

    private void processInfoPacket(InetAddress address, int port) {
        getLogger().info("收到 “LoginSequence-Info” 请求，来自：" + address.getHostAddress() + ":" + port);

        long memUsed = (Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        int onlinePlayers = getServer().getOnlinePlayers().size();
        double tps = getCurrentTPS();
        String response = String.format("LoginSequence-Data Memory:%dMB Online:%d TPS:%.1f", memUsed, onlinePlayers, tps);
        udpNet.sendResponse(response, address, port);
        sayLog(ChatColor.GREEN + "已发送 “LoginSequence-Data” 响应");
        sayLog(ChatColor.AQUA + response);
    }

    private double getCurrentTPS() {
        try {
            if (udpNet.isFolia()) {
                return (Bukkit.getTPS()[0]);
            } else {
                return getStandardTPS();
            }
        } catch (Exception e) {
            sayLog(ChatColor.RED + "获取TPS失败: " + e.getMessage());
            return (-1.0);
        }
    }

    private double getStandardTPS() throws Exception {
        Object minecraftServer = getServer().getClass().getMethod("getServer").invoke(getServer());
        double[] recentTps = (double[]) minecraftServer.getClass().getField("recentTps").get(minecraftServer);
        return recentTps[0];
    }

    // 以下原有方法保持不变
    private void printLogo() {
        // logo
        sayLog(ChatColor.AQUA+"    "+" "+ChatColor.BLUE+" __ "+" "+ChatColor.YELLOW+" ___"+" "+ChatColor.GOLD+"  __ ");
        sayLog(ChatColor.AQUA+"|   "+" "+ChatColor.BLUE+"(__ "+" "+ChatColor.YELLOW+"|___"+" "+ChatColor.GOLD+" /  \\");
        sayLog(ChatColor.AQUA+"|___"+" "+ChatColor.BLUE+" __)"+" "+ChatColor.YELLOW+"|___"+" "+ChatColor.GOLD+" \\__/ "+ChatColor.GREEN+"    LoginSequenceOnline v1.3");
        sayLog("");
    }
    private void createConfig() {File configFile = new File(getDataFolder(), "Config.yml");
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
    }
    private void sayLog(String s) {
        CommandSender sender = Bukkit.getConsoleSender();
        sender.sendMessage(s);
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已禁用");
        if (udpNet != null) {
            udpNet.closeSocket();
        }
    }
}
