/**
 * Author: Oats ©2021
 * Project: Left2Craft
 */

package cloud.ferguson.Left2Craft.listeners;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import cloud.ferguson.Left2Craft.Main;

public class CampfireRitualListener implements Listener {
  private Main plugin;

  public CampfireRitualListener(Main plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  @EventHandler
  public void campfireLitEvent(BlockIgniteEvent a_event) {
    Block litBlock = a_event.getBlock();
    // Ensure we're dealing with a standard campfire
    if (litBlock.getType() != Material.CAMPFIRE) {
      return;
    }

    Player lightingPlayer = a_event.getPlayer(); // May be null if from environment
    startCampfireRitual(litBlock, lightingPlayer);
  }

  @EventHandler
  public void campfirePlaceEvent(BlockPlaceEvent a_event) {
    if (!a_event.canBuild()) {
      return;
    }

    Block placedBlock = a_event.getBlockPlaced();
    if (placedBlock.getType() != Material.CAMPFIRE) {
      return;
    }

    Player player = a_event.getPlayer();
    startCampfireRitual(placedBlock, player);
  }

  @EventHandler
  public void campfireBoostEvent(PlayerInteractEvent a_event) {
    Player boostingPlayer = a_event.getPlayer();

    // Ensure this is a right-click interact on a place-able block
    Action eventAction = a_event.getAction();
    if (eventAction != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    // Ensure this is a campfire block
    Block campfireBlock = a_event.getClickedBlock();
    if (campfireBlock.getType() != Material.CAMPFIRE) {
      return;
    }

    // Ensure we're dealing with gunpowder
    if (!a_event.hasItem()) {
      return;
    }
    ItemStack heldItem = a_event.getItem();
    if (heldItem.getType() != Material.GUNPOWDER) {
      return;
    }

    // Ensure the campfire is lit
    org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) campfireBlock.getBlockData();
    if (!campfireData.isLit()) {
      boostingPlayer.sendMessage(plugin.PLUGIN_CHAT_PREFIX +
            "The campfire must first be lit before the ritual!");
      return;
    }

    // Consume the gunpowder and switch to a soul campfire
    heldItem.setAmount(heldItem.getAmount() - 1);
    campfireBlock.setType(Material.SOUL_CAMPFIRE);

    // Spawn the armour stand into the world
    Location standLoc = campfireBlock.getLocation().clone().add(new Vector(0.5, 1, 0.5));
    World currWorld = standLoc.getWorld();
    ArmorStand armorStand = (ArmorStand) currWorld.spawnEntity(standLoc, EntityType.ARMOR_STAND);
    armorStand.setBasePlate(false);
    armorStand.setCustomName("Seek Armour");
    armorStand.setCustomNameVisible(true);
    armorStand.setArms(true);
    // Add some arrows for flavour
    armorStand.setArrowsInBody((int) Math.round(Math.random() * 4));
    armorStand.setInvulnerable(true);

    // Tell the player what the next step is
    boostingPlayer.sendMessage(plugin.PLUGIN_CHAT_PREFIX +
          "Gather matching armour and equip the stand to complete the respawn ritual, §l" + boostingPlayer.getDisplayName() + "§r!");

    // Play campfire boost effects
    currWorld.playSound(standLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f);
    armorStand.playEffect(EntityEffect.FIREWORK_EXPLODE);
  }

  @EventHandler
  public void standEquippedEvent(PlayerArmorStandManipulateEvent a_event) {
    // The stand/player slot state before the event has run
    ItemStack standItem = a_event.getArmorStandItem();
    ItemStack playerHandItem = a_event.getPlayerItem();
    // Discard item retrieval events
    if (standItem.getAmount() > 0 && playerHandItem.getAmount() == 0) {
      return;
    }

    // Check if the stand is fully equipped
    ArmorStand armorStand = a_event.getRightClicked();
    String armorType = plugin.isRitualStandEquipped(armorStand, playerHandItem);
    if (armorType.isEmpty()) {
      return;
    }

    // Ensure there is a player that could be revived
    Player chosenSpectator = plugin.getRandomSpectator();
    if (chosenSpectator == null) {
      Bukkit.broadcastMessage(plugin.PLUGIN_CHAT_PREFIX + "Couldn't find any online players to revive!");
      return;
    }

    // Stand is equipped, try to revive a player
    boolean didRespawn = plugin.checkRespawnRitual(chosenSpectator, armorStand, playerHandItem);
    if (didRespawn) {
      // Clean up the stand and the campfire
      Location campfireLoc = armorStand.getLocation();
      Block campfireBlock = campfireLoc.getBlock();
      if (campfireBlock.getType() == Material.SOUL_CAMPFIRE) {
        // Revert to an unlit standard campfire
        campfireBlock.setType(Material.CAMPFIRE);
        org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) campfireBlock.getBlockData();
        campfireData.setLit(false);
        campfireBlock.setBlockData(campfireData);
      }
      World currWorld = campfireLoc.getWorld();
      currWorld.playSound(campfireLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
      armorStand.playEffect(EntityEffect.FIREWORK_EXPLODE);
      armorStand.remove();
    }
  }

  /**
   * The first step in the ritual, notifies player(s) they need
   * to use gunpowder to ignite the campfire.
   * @param a_campfireBlock Campfire block that was lit.
   * @param a_player Player that lit/placed the campfire, or null if from environmental source.
   */
  private void startCampfireRitual(Block a_campfireBlock, Player a_player) {
    String message = plugin.PLUGIN_CHAT_PREFIX + "Ignite this §l§6Campfire§r with §l§8Gunpowder§r to prepare the respawn ritual";
    if (a_player != null) {
      // Player present, just tell them
      a_player.sendMessage(message + ", §l" + a_player.getDisplayName() + "§r!");
    } else {
      // No player present, tell everyone
      Bukkit.broadcastMessage(message + "!");
    }
  }
}
