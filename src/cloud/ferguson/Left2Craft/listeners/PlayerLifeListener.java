/**
 * Author: Oats ©2021
 * Project: Left2Craft
 */

package cloud.ferguson.Left2Craft.listeners;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import cloud.ferguson.Left2Craft.Main;

public class PlayerLifeListener implements Listener {
  private Main plugin;

  public PlayerLifeListener(Main plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  @EventHandler
  public void playerDamageEvent(EntityDamageEvent a_event) {
    Entity eventEntity = a_event.getEntity();
    // Ensure we're dealing with a player
    if (eventEntity.getType() != EntityType.PLAYER) {
      return;
    }
    Player player = (Player) eventEntity;

    // Ensure this damage will kill the player
    boolean isKillDamage = player.getHealth() - a_event.getFinalDamage() <= 0;
    if (!isKillDamage) {
      return;
    }

    // Spawn a stand to work as the respawn token for the player and chest(s) for their inventory
    PlayerInventory playerInventory = player.getInventory();
    ItemStack[] dropsArray = playerInventory.getContents();
    List<ItemStack> drops = new ArrayList<ItemStack>();
    ItemStack currSlot = null;
    for (int i = 0; i < dropsArray.length; ++i) {
      currSlot = dropsArray[i];
      if (currSlot != null && currSlot.getAmount() > 0) {
        drops.add(dropsArray[i]);
      }
    }
    String worldName = plugin.getWorldName(player.getLocation().getWorld());
    Bukkit.broadcastMessage(plugin.PLUGIN_CHAT_PREFIX + "©l" + player.getName() + "©r has died in the " + worldName + "!");
    Location standLoc = plugin.spawnPlayerDeathChests(player, drops);
    // Move player to valid stand loc to avoid constant falling off voids
    player.teleport(standLoc);

    // Revive and change the player to spectator mode
    AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
    player.setHealth(maxHealthAttribute.getDefaultValue());
    // Clear the player's inventory
    playerInventory.clear();
    player.setGameMode(GameMode.SPECTATOR);

    // Prevent the event from propagating so the player doesn't see the respawn screen
    a_event.setCancelled(true);
  }
}
