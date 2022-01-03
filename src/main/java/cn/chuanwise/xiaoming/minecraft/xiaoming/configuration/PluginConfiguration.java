package cn.chuanwise.xiaoming.minecraft.xiaoming.configuration;

import cn.chuanwise.xiaoming.minecraft.xiaoming.channel.Channel;
import cn.chuanwise.xiaoming.minecraft.xiaoming.Plugin;
import cn.chuanwise.xiaoming.preservable.SimplePreservable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Data
public class PluginConfiguration extends SimplePreservable<Plugin> {
    boolean debug = false;

    @Data
    public static class Connection {
        boolean autoBind = true;

        int port = 23333;

        long responseTimeout = TimeUnit.MINUTES.toMillis(1);
        long responseDelay = TimeUnit.MILLISECONDS.toMillis(500);

        long verifyTimeout = TimeUnit.MINUTES.toMillis(5);

        int threadCount = 30;
        long checkActivePeriod = TimeUnit.SECONDS.toMillis(30);

        long idleTimeout = TimeUnit.SECONDS.toMillis(50);
    }
    Connection connection = new Connection();

    Map<String, ServerInfo> servers = new HashMap<>();

    @Data
    public static class Generator {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SingleGenerator {
            String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            int length = 10;
            int maxGenerateCount = 100;
        }

        SingleGenerator verifyCode = new SingleGenerator("0123456789", 4, 100);
        SingleGenerator password = new SingleGenerator("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+", 100, 100);
    }
    Generator generator = new Generator();
}