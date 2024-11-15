package me.wikmor.vcm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class VCM extends JavaPlugin implements Listener {
		private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
		private static VCM instance;
		private LuckPerms luckPerms;
		private Map<String, String> cachedFormats = new HashMap<>();
		private Map<UUID, Long> chatCooldowns = new HashMap<>();

		public void onEnable() {
				instance = this;
				this.luckPerms = (LuckPerms) this.getServer().getServicesManager().load(LuckPerms.class);
				if (this.luckPerms == null) {
						this.getLogger().severe("LuckPerms is not loaded. Disabling plugin.");
						this.getServer().getPluginManager().disablePlugin(this);
				} else {
						this.saveDefaultConfig();
						this.getServer().getPluginManager().registerEvents(this, this);
				}
		}

		public void onDisable() {
		}

		public static VCM getInstance() {
				return instance;
		}

		public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				if (args.length == 1 && "reload".equals(args[0])) {
						this.reloadConfig();
						sender.sendMessage(this.colorize("&aVCM has been reloaded."));
						return true;
				} else {
						Player player;
						String nickname;
						if ((command.getName().equalsIgnoreCase("setpronouns") || command.getName().equalsIgnoreCase("pronouns")) && sender instanceof Player) {
								player = (Player) sender;
								if (args.length > 0) {
										nickname = String.join(" ", args);
										this.setPronouns(player, nickname);
										player.sendMessage(this.colorize("&aYour pronouns have been set to: " + nickname));
										return true;
								} else {
										player.sendMessage(this.colorize("&cUsage: /setpronouns <pronouns>"));
										return false;
								}
						} else if ((command.getName().equalsIgnoreCase("nick") || command.getName().equalsIgnoreCase("nickname")) && sender instanceof Player) {
								player = (Player) sender;
								if (args.length > 0) {
										nickname = String.join(" ", args);
										nickname = this.colorize(nickname);
										int minLength = this.getConfig().getInt("nickname.min-length", 1);
										int maxLength = this.getConfig().getInt("nickname.max-length", 16);
										if (nickname.length() >= minLength && nickname.length() <= maxLength) {
												this.setNickname(player, nickname);
												player.setDisplayName(nickname);
												player.sendMessage(this.colorize("&aYour nickname has been set to: " + nickname));
												return true;
										} else {
												player.sendMessage(this.colorize("&cNickname must be between " + minLength + " and " + maxLength + " characters long."));
												return false;
										}
								} else {
										player.sendMessage(this.colorize("&cUsage: /nick <nickname>"));
										return false;
								}
						} else {
								return false;
						}
				}
		}

		private void setPronouns(Player player, String pronouns) {
				this.luckPerms.getUserManager().modifyUser(player.getUniqueId(), (user) -> {
						user.data().clear(NodeType.META.predicate((mn) -> {
								return mn.getMetaKey().equals("pronouns");
						}));
						user.data().add(MetaNode.builder("pronouns", pronouns).build());
				});
		}

		private void setNickname(Player player, String nickname) {
				String colorizedNickname = this.colorize(nickname);
				this.luckPerms.getUserManager().modifyUser(player.getUniqueId(), (user) -> {
						user.data().clear(NodeType.META.predicate((mn) -> {
								return mn.getMetaKey().equals("vcm_nickname");
						}));
						user.data().add(MetaNode.builder("vcm_nickname", colorizedNickname).build());
				});
				this.getLogger().info("Set nickname for player " + player.getName() + " to: " + colorizedNickname);
		}

		public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
				return (List) (args.length == 1 ? Collections.singletonList("reload") : new ArrayList());
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onChat(AsyncPlayerChatEvent event) {
				Player player = event.getPlayer();
				UUID playerId = player.getUniqueId();
				long currentTime = System.currentTimeMillis();
				long cooldownTime = this.getConfig().getInt("chat-cooldown", 3) * 1000L;

				if (!player.hasPermission("vcm.chatbypass")) {
						if (chatCooldowns.containsKey(playerId) && (currentTime - chatCooldowns.get(playerId)) < cooldownTime) {
								player.sendMessage(this.colorize("&cPlease wait before sending another message."));
								event.setCancelled(true);
								return;
						}
						chatCooldowns.put(playerId, currentTime);
				}

				String message = event.getMessage();
				CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
				String group = metaData.getPrimaryGroup();
				String formatKey = "group-formats." + group;
				String format = cachedFormats.computeIfAbsent(formatKey, key -> this.getConfig().getString(this.getConfig().getString(key) != null ? key : "chat-format"));

				format = format.replace("{prefix}", metaData.getPrefix() != null ? metaData.getPrefix() : "")
								.replace("{suffix}", metaData.getSuffix() != null ? metaData.getSuffix() : "")
								.replace("{prefixes}", (CharSequence) metaData.getPrefixes().keySet().stream().map((key) -> {
										return (String) metaData.getPrefixes().get(key);
								}).collect(Collectors.joining()))
								.replace("{suffixes}", (CharSequence) metaData.getSuffixes().keySet().stream().map((key) -> {
										return (String) metaData.getSuffixes().get(key);
								}).collect(Collectors.joining()))
								.replace("{world}", player.getWorld().getName())
								.replace("{name}", player.getName())
								.replace("{displayname}", player.getDisplayName())
								.replace("{username-color}", metaData.getMetaValue("username-color") != null ? metaData.getMetaValue("username-color") : "")
								.replace("{message-color}", metaData.getMetaValue("message-color") != null ? metaData.getMetaValue("message-color") : "")
								.replace("{pronouns}", metaData.getMetaValue("pronouns") != null ? metaData.getMetaValue("pronouns") : "");
				format = this.colorize(this.translateHexColorCodes(this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders(player, format) : format));
				event.setFormat(format.replace("{message}", player.hasPermission("vcm.colorcodes") && player.hasPermission("vcm.rgbcodes") ? this.colorize(this.translateHexColorCodes(message)) : (player.hasPermission("vcm.colorcodes") ? this.colorize(message) : (player.hasPermission("vcm.rgbcodes") ? this.translateHexColorCodes(message) : message))).replace("%", "%%"));
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onPlayerJoin(PlayerJoinEvent event) {
				Player player = event.getPlayer();
				CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
				String nickname = metaData.getMetaValue("vcm_nickname");
				if (nickname != null) {
						player.setDisplayName(this.colorize(nickname));
						this.getLogger().info("Retrieved nickname for player " + player.getName() + ": " + nickname);
				}
		}

		private String colorize(String message) {
				return ChatColor.translateAlternateColorCodes('&', message);
		}

		private String translateHexColorCodes(String message) {
				Matcher matcher = HEX_PATTERN.matcher(message);
				StringBuffer buffer = new StringBuffer(message.length() + 32);

				while (matcher.find()) {
						String group = matcher.group(1);
						matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + '§' + group.charAt(1) + '§' + group.charAt(2) + '§' + group.charAt(3) + '§' + group.charAt(4) + '§' + group.charAt(5));
				}

				return matcher.appendTail(buffer).toString();
		}
}
