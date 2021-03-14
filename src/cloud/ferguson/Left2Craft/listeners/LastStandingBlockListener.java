/**
 * Author: Oats ©2021
 * Project: Left2Craft
 */

package cloud.ferguson.Left2Craft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import cloud.ferguson.Left2Craft.Main;

public class LastStandingBlockListener implements Listener {

  private Main plugin;

  public LastStandingBlockListener(Main plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  @EventHandler
  public void playerMoveEvent(PlayerMoveEvent a_event) {
    Player player = a_event.getPlayer();

    // Ensure we know about the player
    Location standingBlockLoc = a_event.getTo().clone();
    standingBlockLoc.subtract(0, 1, 0);
    Block currBlock = standingBlockLoc.getBlock();
    // Ensure the player is on a block they can stand on
    if (!(currBlock.isEmpty() || currBlock.isPassable())) {
      if (plugin.lastPlayerBlock.containsKey(player)) {
        // If the player has moved to a new block or world
        Location lastLoc = plugin.lastPlayerBlock.get(player).getLocation();
        Location newLoc = currBlock.getLocation();
        if (lastLoc.getWorld() != newLoc.getWorld() || lastLoc.distanceSquared(newLoc) > 0.1) {
          plugin.lastPlayerBlock.replace(player, currBlock);
        }
      } else {
        plugin.lastPlayerBlock.put(player, currBlock);
      }
    }
  }
}
