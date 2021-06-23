/**
 * Author: Oats ©2021
 * Project: Left2Craft
 */

package cloud.ferguson.Left2Craft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import cloud.ferguson.Left2Craft.Main;

public class PlayerSigninListener implements Listener {
  private Main plugin;

  public PlayerSigninListener(Main plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  @EventHandler
  public void playerSigninEvent(PlayerJoinEvent a_event) {
    Player player = a_event.getPlayer();
    player.sendMessage(plugin.PLUGIN_CHAT_PREFIX + "Welcome, you can use lit §l§6Campfires§r to respawn players!");
  }
}
