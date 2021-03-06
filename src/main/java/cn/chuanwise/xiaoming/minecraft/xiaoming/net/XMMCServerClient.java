package cn.chuanwise.xiaoming.minecraft.xiaoming.net;

import cn.chuanwise.mclib.bukkit.net.protocol.NetLibProtocol;
import cn.chuanwise.mclib.bukkit.net.protocol.SendSameMessageInform;
import cn.chuanwise.net.netty.packet.PacketHandler;
import cn.chuanwise.toolkit.container.Container;
import cn.chuanwise.util.CollectionUtil;
import cn.chuanwise.util.Preconditions;
import cn.chuanwise.util.StringUtil;
import cn.chuanwise.util.Times;
import cn.chuanwise.xiaoming.contact.contact.GroupContact;
import cn.chuanwise.xiaoming.contact.contact.XiaoMingContact;
import cn.chuanwise.xiaoming.contact.message.Message;
import cn.chuanwise.xiaoming.minecraft.protocol.*;
import cn.chuanwise.xiaoming.minecraft.xiaoming.XMMCXiaoMingPlugin;
import cn.chuanwise.xiaoming.minecraft.xiaoming.configuration.PlayerConfiguration;
import cn.chuanwise.xiaoming.minecraft.xiaoming.configuration.PlayerInfo;
import cn.chuanwise.xiaoming.minecraft.xiaoming.configuration.PlayerVerifyCodeConfiguration;
import cn.chuanwise.xiaoming.minecraft.xiaoming.configuration.StringGenerator;
import cn.chuanwise.xiaoming.minecraft.xiaoming.event.*;
import cn.chuanwise.xiaoming.object.PluginObjectImpl;
import lombok.Data;

import java.util.*;
import java.util.concurrent.TimeoutException;

@Data
@SuppressWarnings("all")
public class XMMCServerClient extends PluginObjectImpl<XMMCXiaoMingPlugin> {
    protected final PacketHandler packetHandler;
    protected final OnlineClient onlineClient;

    public XMMCServerClient(OnlineClient onlineClient, PacketHandler packetHandler) {
        setPlugin(onlineClient.getPlugin());
        Preconditions.nonNull(packetHandler, "packet handler");

        this.packetHandler = packetHandler;
        this.onlineClient = onlineClient;

        setupBaseListeners();
        setupTriggerForwarders();
        setupPlayerInfoListeners();
    }

    private void setupBaseListeners() {
        packetHandler.setOnRequest(XMMCProtocol.REQUEST_CONFIRM_ACTIVE, v -> v);
        packetHandler.setOnInform(XMMCProtocol.INFORM_MESSAGE, argument -> {
            plugin.getXiaoMingBot().getEventManager().callEvent(new ServerMessageEvent(onlineClient, argument));
        });
    }

    public void sendMessage(String message) {
        packetHandler.inform(XMMCProtocol.INFORM_MESSAGE, message);
    }

    public void sendWideMessage(Set<String> playerNames, String message) {
        packetHandler.inform(XMMCProtocol.INFORM_WIDE_MESSAGE, new SendSameMessageInform(playerNames, message));
    }

    private void setupTriggerForwarders() {
        final Server server = plugin.getServer();

        packetHandler.setOnInform(NetLibProtocol.INFORM_PLAYER_CHANGE_WORLD_EVENT, x -> {
            final PlayerChangeWorldEvent event = new PlayerChangeWorldEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
        });
        packetHandler.setOnInform(XMMCProtocol.INFORM_TPS, x -> {
            final ServerTpsEvent event = new ServerTpsEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
        });
        packetHandler.setOnRequest(NetLibProtocol.REQUEST_PLAYER_CHAT_EVENT, x -> {
            final PlayerChatEvent event = new PlayerChatEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
            return event.isCancelled();
        });
        packetHandler.setOnInform(NetLibProtocol.INFORM_PLAYER_JOIN_EVENT, x -> {
            final PlayerJoinEvent event = new PlayerJoinEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
        });
        packetHandler.setOnInform(NetLibProtocol.INFORM_PLAYER_QUIT_EVENT, x -> {
            final PlayerQuitEvent event = new PlayerQuitEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
        });
        packetHandler.setOnInform(NetLibProtocol.INFORM_PLAYER_DEATH_EVENT, x -> {
            final PlayerDeathEvent event = new PlayerDeathEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
        });
        packetHandler.setOnRequest(NetLibProtocol.REQUEST_PLAYER_KICK_EVENT, x -> {
            final PlayerKickEvent event = new PlayerKickEvent(x, onlineClient);
            xiaoMingBot.getEventManager().callEvent(event);
            return event.isCancelled();
        });
    }

    private void setupPlayerInfoListeners() {
        packetHandler.setOnRequest(XMMCProtocol.REQUEST_PLAYER_BIND_INFO, request -> {
            return plugin.getPlayerConfiguration().getPlayerInfo(request)
                    .map(x -> {
                        return new PlayerBindInfo(x.getPlayerNames(), x.getAccountCodes());
                    })
                    .orElse(null);
        });
        packetHandler.setOnRequest(XMMCProtocol.REQUEST_PLAYER_VERIFY_CODE, playerName -> {
            // ???????????????????????????????????????
            if (plugin.getPlayerConfiguration().getPlayerInfo(playerName).isPresent()) {
                return null;
            }

            // ?????????????????????????????????
            final PlayerVerifyCodeConfiguration configuration = plugin.getPlayerVerifyCodeConfiguration();
            final PlayerVerifyCodeInfo info;

            // ???????????????????????????
            final Map<String, PlayerVerifyCodeConfiguration.VerifyInfo> verifyInfo = configuration.getVerifyInfo();
            final Container<Map.Entry<String, PlayerVerifyCodeConfiguration.VerifyInfo>> container =
                    CollectionUtil.findFirst(verifyInfo.entrySet(), x -> Objects.equals(x.getValue().getPlayerName(), playerName));

            String verifyCode;
            if (container.isEmpty()) {
                final StringGenerator generator = plugin.getBaseConfiguration().getGenerator().getVerifyCode();
                verifyCode = StringUtil.randomString(generator.getCharacters(), generator.getLength());
                while (verifyInfo.containsKey(verifyCode)) {
                    verifyCode = StringUtil.randomString(generator.getCharacters(), generator.getLength());
                }

                verifyInfo.put(verifyCode, new PlayerVerifyCodeConfiguration.VerifyInfo(System.currentTimeMillis(), playerName));
            } else {
                final Map.Entry<String, PlayerVerifyCodeConfiguration.VerifyInfo> entry = container.get();
                verifyCode = entry.getKey();
                entry.getValue().setTimeMillis(System.currentTimeMillis());
            }
            configuration.readyToSave();
            return new PlayerVerifyCodeInfo(verifyCode, configuration.getTimeout());
        });

        packetHandler.setOnRequest(XMMCProtocol.REQUEST_PLAYER_BIND, request -> {
            final String playerName = request.getPlayerName();
            final long accountCode = request.getAccountCode();
            final PlayerConfiguration playerConfiguration = plugin.getPlayerConfiguration();

            // ????????????????????????
            final Optional<PlayerInfo> optionalSameCodePlayerInfo = playerConfiguration.getPlayerInfo(accountCode);
            if (optionalSameCodePlayerInfo.isPresent()) {
                final PlayerInfo sameCodePlayerInfo = optionalSameCodePlayerInfo.get();
                if (sameCodePlayerInfo.hasPlayerName(playerName)) {
                    return new PlayerBindResponse.Error(PlayerBindResponse.Error.Type.REPEAT);
                }
            }

            // ???????????????????????????
            final Optional<PlayerInfo> optionalSameNamePlayerInfo = playerConfiguration.getPlayerInfo(playerName);
            if (optionalSameNamePlayerInfo.isPresent()) {
                final PlayerInfo sameNamePlayerInfo = optionalSameNamePlayerInfo.get();
                Preconditions.state(!sameNamePlayerInfo.hasAccountCode(accountCode), "internal error");
                return new PlayerBindResponse.Error(PlayerBindResponse.Error.Type.OTHER);
            }

            // ??????????????????
            final long timeout = playerConfiguration.getBoundTimeout();
            final String timeoutLength = Times.toTimeLength(timeout);
            final String message = "?????????" + onlineClient.getServerInfo().getName() + "??????????????????" + playerName + "???????????????\n" +
                    "????????????????????????????????? QQ ??????????????????????????? " + timeoutLength + " ????????????????????????\n" +
                    "??????????????????????????????????????????";
            final Optional<XiaoMingContact> optionalContact = plugin.getXiaoMingBot().getContactManager().sendMessagePossibly(accountCode, message);
            if (!optionalContact.isPresent()) {
                return new PlayerBindResponse.Error(PlayerBindResponse.Error.Type.FAILED);
            }
            final XiaoMingContact xiaomingContact = optionalContact.get();

            // ???????????????????????????
            xiaoMingBot.getScheduler().run(() -> {
                PlayerBindResultInform inform;
                try {
                    boolean bound = false;
                    if (xiaomingContact instanceof GroupContact) {
                        final Optional<Message> optionalMessage = ((GroupContact) xiaomingContact).getMember(accountCode).orElseThrow(NoSuchElementException::new).nextMessage(timeout);
                        if (!optionalMessage.isPresent()) {
                            ((GroupContact) xiaomingContact).atSend(accountCode, "???????????????????????????????????????????????????");
                            inform = PlayerBindResultInform.TIMEOUT;
                        } else {
                            bound = Objects.equals(optionalMessage.get().serialize(), "??????");
                            if (bound) {
                                ((GroupContact) xiaomingContact).atSend(accountCode, "?????????????????????" + playerName + "???");
                            } else {
                                ((GroupContact) xiaomingContact).atSend(accountCode, "?????????????????????");
                            }
                        }
                    } else {
                        final Optional<Message> optionalMessage = xiaomingContact.nextMessage(accountCode);
                        if (!optionalMessage.isPresent()) {
                            xiaomingContact.sendWarning("???????????????????????????????????????????????????");
                            inform = PlayerBindResultInform.TIMEOUT;
                        } else {
                            bound = Objects.equals(optionalMessage.get().serialize(), "??????");
                            if (bound) {
                                xiaomingContact.sendMessage("?????????????????????" + playerName + "???");
                            } else {
                                xiaomingContact.sendMessage("?????????????????????");
                            }
                        }
                    }

                    if (bound) {
                        playerConfiguration.forceBind(accountCode, playerName);
                        playerConfiguration.readyToSave();
                        inform = PlayerBindResultInform.ACCEPTED;
                    } else {
                        inform = PlayerBindResultInform.DENIED;
                    }
                } catch (InterruptedException exception) {
                    inform = PlayerBindResultInform.INTERRUPTED;
                }
                packetHandler.inform(XMMCProtocol.INFORM_PLAYER_BIND_RESULT, inform);
            });
            return new PlayerBindResponse.Wait(xiaomingContact.getAliasAndCode(), timeout);
        });

        packetHandler.setOnRequest(XMMCProtocol.REQUEST_PLAYER_UNBIND, playerName -> {
            final PlayerConfiguration playerConfiguration = plugin.getPlayerConfiguration();
            final Optional<PlayerInfo> optionalPlayerInfo = playerConfiguration.getPlayerInfo(playerName);
            if (!optionalPlayerInfo.isPresent()) {
                return false;
            }
            final PlayerInfo playerInfo = optionalPlayerInfo.get();

            return playerConfiguration.unbind(playerName);
        });
    }

    public Set<OnlinePlayerResponse.PlayerKey> getOnlinePlayerKeys() throws InterruptedException, TimeoutException {
        return packetHandler.obtain(XMMCProtocol.OBTAIN_ONLINE_PLAYERS).getPlayerKeys();
    }

    public AskResponse ask(String playerName, String message, long timeout) throws InterruptedException, TimeoutException {
        return packetHandler.request(XMMCProtocol.REQUEST_ASK, new AskRequest(playerName, message, timeout), timeout);
    }

    public boolean confirmActive() throws InterruptedException {
        boolean active;
        try {
            final long timeMillis = System.currentTimeMillis();
            active = timeMillis == packetHandler.request(XMMCProtocol.REQUEST_CONFIRM_ACTIVE, timeMillis);
        } catch (TimeoutException e) {
            active = false;
        }
        if (!active) {
            packetHandler.getContext().channel().close();
        }
        return active;
    }
}
