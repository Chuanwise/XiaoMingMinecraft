package cn.chuanwise.xiaoming.minecraft.xiaoming.event;

import cn.chuanwise.mclib.bukkit.net.protocol.PlayerDeathInform;
import cn.chuanwise.xiaoming.minecraft.xiaoming.net.OnlineClient;
import lombok.Data;

import java.util.UUID;

@Data
public class PlayerDeathEvent
        extends SimpleXiaoMingMinecraftEvent
        implements PlayerEvent {

    private final PlayerDeathInform inform;
    private final UUID playerUuid;
    private final OnlineClient onlineClient;
    private final String playerName;
    private final String message;

    public PlayerDeathEvent(PlayerDeathInform inform, OnlineClient onlineClient) {
        this.inform = inform;

        this.playerUuid = inform.getPlayerUuid();
        this.playerName = inform.getPlayerName();
        this.message = inform.getMessage();

        this.onlineClient = onlineClient;
    }
}
