package cn.chuanwise.xiaoming.minecraft.xiaoming.channel.trigger.server;

import cn.chuanwise.xiaoming.minecraft.xiaoming.channel.trigger.TriggerHandleReceipt;
import cn.chuanwise.xiaoming.minecraft.xiaoming.event.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class PlayerDeathTrigger extends PlayerTrigger<PlayerDeathEvent> {
    @Override
    protected TriggerHandleReceipt handle2(PlayerDeathEvent event) {
        final Map<String, Object> environment = new HashMap<>();
        environment.put("message", event.getMessage());
        return new TriggerHandleReceipt.Handled(environment);
    }
}