/**
 * Author: Oats ©2021
 * Project: Left2Craft
 */

package cloud.ferguson.Left2Craft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import cloud.ferguson.Left2Craft.listeners.CampfireRitualListener;
import cloud.ferguson.Left2Craft.listeners.LastStandingBlockListener;
import cloud.ferguson.Left2Craft.listeners.PlayerLifeListener;
import cloud.ferguson.Left2Craft.listeners.PlayerSigninListener;

public class Main extends JavaPlugin {
  public final String PLUGIN_CHAT_PREFIX = "§d[Left2Craft]§r ";

  public final String ARMOR_PREFIX_LEATHER = "LEATHER";
  public final String ARMOR_PREFIX_IRON = "IRON";
  public final String ARMOR_PREFIX_GOLD = "GOLDEN";
  public final String ARMOR_PREFIX_DIAMOND = "DIAMOND";
  public final String ARMOR_PREFIX_CHAINMAIL = "CHAINMAIL";
  public final String ARMOR_PREFIX_NETHERITE = "NETHERITE";

  // See: https://minecraft.gamepedia.com/Tick
  public final int TICKS_PER_SECOND = 20;

  // See: https://minecraft.gamepedia.com/Hunger#Mechanics
  public final int HUNGER_MAX = 20;
  public final float HUNGER_START_SATURATION = 5f;

  // How far up or down to search for a valid death-chest spawn location
  private final int SPAWN_LOC_SEARCH_LIMIT = 15;

  // Block each player was last standing on
  public HashMap<Player, Block> lastPlayerBlock = new HashMap<Player, Block>();

  @Override
  public void onEnable() {
    super.onEnable();

    new PlayerSigninListener(this);
    new PlayerLifeListener(this);
    new LastStandingBlockListener(this);
    new CampfireRitualListener(this);
    new PlayerSigninListener(this);
  }

  /**
   * Returns a more display-ready version of the world name.
   * 
   * @param a_world World to return name for.
   * @return Display version of the world name or internal name as fallback.
   */
  public String getWorldName(World a_world) {
    Environment worldEnv = a_world.getEnvironment();
    String worldName = a_world.getName();
    if (worldEnv == Environment.NORMAL) {
      worldName = "§2Overworld§r";
    } else if (worldEnv == Environment.NETHER) {
      worldName = "§4Nether§r";
    } else if (worldEnv == Environment.THE_END) {
      worldName = "§8End§r";
    }
    return worldName;
  }

  /**
   * Spawns chest(s) with the player's items at a valid position.
   * 
   * @param a_playerEnt Player entity that has died.
   * @param a_drops     The items this player will drop.
   * @return Position of the spawned player chests.
   */
  public Location spawnPlayerDeathChests(Player a_player, List<ItemStack> a_drops) {
    Location playerLoc = a_player.getLocation();

    // If we know about the player already, use their last valid standing position
    if (lastPlayerBlock.containsKey(a_player)) {
      playerLoc = lastPlayerBlock.get(a_player).getLocation();
    }

    Location chestLoc = findValidStandLoc(playerLoc);
    // Spawn first chest with as much of the player's stuff as possible
    List<ItemStack> remainingDrops = spawnPlayerChest(chestLoc, a_drops);

    if (remainingDrops.size() > 0) {
      // Spawn a chest with their remaining stuff
      chestLoc = chestLoc.clone().add(new Vector(0, 1, 0));
      remainingDrops = spawnPlayerChest(chestLoc, remainingDrops);
    }

    // Spawn the armour stand
    chestLoc = chestLoc.clone().add(new Vector(0, 1, 0));
    Block standBlock = chestLoc.getBlock();
    if (!standBlock.isEmpty()) {
      standBlock.breakNaturally();
    }

    Location extraClearLoc = chestLoc.clone().add(new Vector(0, 1, 0));
    Block clearBlock = extraClearLoc.getBlock();
    if (!clearBlock.isEmpty()) {
      // Break the block above too to ensure enough space to see the chest name
      clearBlock.breakNaturally();
    }

    return chestLoc;
  }

  /**
   * Returns the online Player matching the input name.
   * 
   * @param a_playerName Player name to search for.
   * @return Player if found or nil if no online player matched.
   */
  public Player findPlayerByName(String a_playerName) {
    Player foundPlayer = null;
    List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    for (Player player : onlinePlayers) {
      if (player.getDisplayName().compareToIgnoreCase(a_playerName) == 0) {
        foundPlayer = player;
        break;
      }
    }
    return foundPlayer;
  }

  /**
   * Return the armour type prefix so that it can be compared with other armour
   * pieces.
   * 
   * @param a_armorMaterial Material to extract the armour type prefix from.
   * @return Armour type prefix, or blank if not valid armour material.
   */
  public String getArmorTypePrefix(Material a_armorMaterial) {
    String fullType = a_armorMaterial.toString();
    String[] typeParts = fullType.split("_");
    if (typeParts.length > 0) {
      String armorType = typeParts[0];
      if (isKnownArmorType(armorType)) {
        return armorType;
      }
    }
    return "";
  }

  /**
   * Selects a random spectator player for potential revival.
   * 
   * @return Selected player or null if no players are in spectator.
   */
  public Player getRandomSpectator() {
    List<Player> spectators = new ArrayList<>(Bukkit.getOnlinePlayers());
    spectators.removeIf((player) -> player.getGameMode() != GameMode.SPECTATOR);
    int numSpectators = spectators.size();
    if (numSpectators > 0) {
      int chosenPlayerIndex = (int) Math.round(Math.random() * (numSpectators - 1));
      Player chosenPlayer = spectators.get(chosenPlayerIndex);
      return chosenPlayer;
    } else {
      return null;
    }
  }

  /**
   * Checks whether a ritual stand is fully equipped with matching armour.
   * 
   * @param a_armorStand   Armour stand to check.
   * @param a_incomingItem (Nullable) Item being added to the armour stand via an
   *                       event or similar.
   * @return The ritual tier to use from the stand's equipped armour. Or blank if
   *         stand isn't equipped properly.
   */
  public String isRitualStandEquipped(ArmorStand a_armorStand, ItemStack a_incomingItem) {
    // Ensure the stand is fully equipped
    EntityEquipment standGear = a_armorStand.getEquipment();
    ItemStack[] standArmor = standGear.getArmorContents();
    String armorType = "";
    int lowestArmorTier = -1;
    int filledArmorSlots = 0;
    ItemStack currStandArmor = null;
    for (int i = 0; i < standArmor.length; ++i) {
      currStandArmor = standArmor[i];
      if (currStandArmor.getAmount() > 0) {
        Material slotArmourMaterial = standArmor[i].getType();
        String slotArmorType = getArmorTypePrefix(slotArmourMaterial);
        if (!slotArmorType.isEmpty()) {
          filledArmorSlots += 1;
          int slotArmorTier = getArmorRitualTier(slotArmorType);
          if (lowestArmorTier == -1 || slotArmorTier < lowestArmorTier) {
            lowestArmorTier = slotArmorTier;
            armorType = slotArmorType;
          }
        }
      }
    }

    // Because the incoming item wont have been added yet, it will need to be
    // counted separately
    if (a_incomingItem != null && a_incomingItem.getAmount() > 0) {
      Material slotArmourMaterial = a_incomingItem.getType();
      String slotArmorType = getArmorTypePrefix(slotArmourMaterial);
      if (!slotArmorType.isEmpty()) {
        filledArmorSlots += 1;

        int slotArmorTier = getArmorRitualTier(slotArmorType);
        if (lowestArmorTier == -1 || slotArmorTier < lowestArmorTier) {
          lowestArmorTier = slotArmorTier;
          armorType = slotArmorType;
        }
      }
    }

    // If wearing mixed armour types or not enough armour, the ritual can't proceed
    boolean hasValidArmor = !armorType.isEmpty();
    boolean hasEnoughArmor = filledArmorSlots >= 4;
    if (!hasValidArmor || !hasEnoughArmor) {
      return "";
    }

    return armorType;
  }

  /**
   * Checks for whether the respawn ritual has been applied correctly. Performs
   * the ritual if so.
   * 
   * @param a_player       Player to revive.
   * @param a_armorStand   Armour stand prepared for reviving the player.
   * @param a_incomingItem (Nullable) Item being added to the armour stand via an
   *                       event or similar.
   * @return True if player was revived, false if not.
   */
  public boolean checkRespawnRitual(Player a_player, ArmorStand a_armorStand, ItemStack a_incomingItem) {
    // Check for armour tiers on the armour
    String armorType = isRitualStandEquipped(a_armorStand, a_incomingItem);

    // Ensure the stand is fully equipped
    if (armorType.isEmpty()) {
      Bukkit.broadcastMessage(PLUGIN_CHAT_PREFIX + "Can't revive §l" + a_player.getDisplayName()
          + "§r yet, their stand needs full matching armour!");
      return false;
    }

    // Ensure this is a stand spawned from the campfire ritual
    if (!a_armorStand.isInvulnerable()) {
      Bukkit.broadcastMessage(
          PLUGIN_CHAT_PREFIX + "Only stands from the §l§6Campfire§r ritual can be used for revival!");
      return false;
    }

    // Prepare the player for respawning
    AttributeInstance maxHealthAttribute = a_player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
    double newHealth = maxHealthAttribute.getDefaultValue();
    a_player.teleport(a_armorStand);
    a_player.setHealth(newHealth);
    a_player.setGameMode(GameMode.SURVIVAL);

    // Clear player XP and active effects
    a_player.setTotalExperience(0);
    Collection<PotionEffect> activeEffects = a_player.getActivePotionEffects();
    for (PotionEffect effect : activeEffects) {
      // There will potentially be overlap here if the player has multiple effects of
      // the same type, but that shouldn't cause any issues
      a_player.removePotionEffect(effect.getType());
    }

    // Apply respawn buff from armour used
    applyStandRitualEffect(a_player, armorType);

    String worldName = getWorldName(a_player.getLocation().getWorld());
    String ritualName = armorType.substring(0, 1).toUpperCase() + armorType.substring(1).toLowerCase(); // Capitalise
    Bukkit.broadcastMessage(PLUGIN_CHAT_PREFIX + "§l" + a_player.getName() + "§r was revived in the " + worldName
        + " using the §l" + ritualName + "§r ritual!");

    return true;
  }

  /**
   * Applies the relevant bonus/debuff from respawning the player using certain
   * armour tiers.
   * 
   * @param a_revivedPlayer   The player that is being revived
   * @param a_armorTypePrefix Armour type prefix that was used on the stand.
   */
  public void applyStandRitualEffect(Player a_revivedPlayer, String a_armorTypePrefix) {
    if (!isKnownArmorType(a_armorTypePrefix)) {
      Bukkit.broadcastMessage(
          PLUGIN_CHAT_PREFIX + "§4Unknown§r armour stand material! §l" + a_armorTypePrefix + "§r");
      return;
    }

    AttributeInstance maxHealthAttribute = a_revivedPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
    double maxHealth = maxHealthAttribute.getDefaultValue();
    double newHealth = maxHealth;
    int newHunger = HUNGER_MAX;
    float newSaturation = HUNGER_START_SATURATION;
    if (a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_LEATHER) == 0) {
      // Leather - 2 Hearts, 0 Hunger, Weakness debuff for a minute
      newHealth = 4;
      newHunger = 0;
      newSaturation = 0; // Also clear saturation to force full eating
      a_revivedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, TICKS_PER_SECOND * 60, 0));
    } else if (a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_IRON) == 0) {
      // Iron - 50% Hearts, 50% Hunger
      newHealth = maxHealth * 0.5;
      newHunger = HUNGER_MAX / 2;
    } else if (a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_GOLD) == 0) {
      // Gold - Full Hearts, 1 Hunger, Strength buff for a minute
      newHealth = maxHealth;
      newHunger = 2;
      a_revivedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, TICKS_PER_SECOND * 60, 0));
    } else if (a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_DIAMOND) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_CHAINMAIL) == 0) {
      // Diamond/Chainmail - Full Hearts, Full Hunger
      newHealth = maxHealth;
      newHunger = HUNGER_MAX;
    } else if (a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_NETHERITE) == 0) {
      // Netherite - Gapple effect, 10 min strength potion
      // See: https://minecraft.gamepedia.com/Enchanted_Golden_Apple
      newHealth = maxHealth;
      newHunger = HUNGER_MAX;
      a_revivedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, TICKS_PER_SECOND * 60 * 2, 3));
      a_revivedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, TICKS_PER_SECOND * 20, 1));
      a_revivedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, TICKS_PER_SECOND * 60 * 5, 0));
      a_revivedPlayer
          .addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, TICKS_PER_SECOND * 60 * 5, 0));
      a_revivedPlayer
          .addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, TICKS_PER_SECOND * 60 * 10, 0));
    }

    a_revivedPlayer.setHealth(newHealth);
    a_revivedPlayer.setFoodLevel(newHunger);
    a_revivedPlayer.setSaturation(newSaturation);
  }

  /**
   * Returns whether
   * 
   * @param a_armorTypePrefix Armour type prefix to check for.
   */
  public Boolean isKnownArmorType(String a_armorTypePrefix) {
    return a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_LEATHER) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_IRON) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_GOLD) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_DIAMOND) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_CHAINMAIL) == 0
        || a_armorTypePrefix.compareToIgnoreCase(ARMOR_PREFIX_NETHERITE) == 0;
  }

  /**
   * Returns the effective ritual tier of the input armour.
   * 
   * @param a_armorType Armour type to retrieve tier for.
   * @return Ritual tier (higher is stronger), or -1 if invalid armour.
   */
  public int getArmorRitualTier(String a_armorType) {
    int ritualTier = -1;
    if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_LEATHER) == 0) {
      ritualTier = 1;
    } else if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_IRON) == 0) {
      ritualTier = 2;
    } else if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_GOLD) == 0) {
      ritualTier = 3;
    } else if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_DIAMOND) == 0) {
      ritualTier = 4;
    } else if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_CHAINMAIL) == 0) {
      ritualTier = 4;
    } else if (a_armorType.compareToIgnoreCase(ARMOR_PREFIX_NETHERITE) == 0) {
      ritualTier = 5;
    }

    return ritualTier;
  }

  /**
   * Finds enough free-space near the player death location to spawn the chest and
   * stand.
   * 
   * @param a_playerLoc Player death location.
   * @return Valid spawn location (or death location if not found)
   */
  private Location findValidStandLoc(Location a_playerLoc) {
    World world = a_playerLoc.getWorld();
    // Start scanning from the block above to better represent where player is
    // standing
    Location rayStartLoc = a_playerLoc.clone().add(new Vector(0, 1, 0));
    Location validSpawnLoc = null;

    // Try searching down and up for a valid block
    // Search down
    RayTraceResult rayHit = world.rayTrace(rayStartLoc, new Vector(0, -1, 0), SPAWN_LOC_SEARCH_LIMIT,
        FluidCollisionMode.NEVER, true, 1, (entity) -> entity == null);
    if (rayHit != null) {
      // Pick one block closer so we hopefully get a clear air-block
      validSpawnLoc = rayHit.getHitBlock().getLocation().clone().add(new Vector(0, 1, 0));
    }

    // Search up
    if (validSpawnLoc == null) {
      rayHit = world.rayTrace(rayStartLoc, new Vector(0, 1, 0), SPAWN_LOC_SEARCH_LIMIT, FluidCollisionMode.NEVER, true,
          1, (entity) -> entity == null);
      if (rayHit != null) {
        // Pick some blocks closer so we hopefully get a clear air-block
        validSpawnLoc = rayHit.getHitBlock().getLocation().clone().add(new Vector(0, -2, 0));
      }
    }

    if (validSpawnLoc == null) {
      // Fall back to the player death location
      validSpawnLoc = rayStartLoc; // Try to get the air above player standing spot
    }

    return validSpawnLoc;
  }

  /**
   * Spawns a chest for the player's loot. Will try to store as much of the drops
   * as possible and will return the remaining drops.
   * 
   * @param a_chestLoc Location to spawn the chest at.
   * @param a_drops    Items to try storing in the chest.
   * @return Remaining items that couldn't be stored.
   */
  private List<ItemStack> spawnPlayerChest(Location a_chestLoc, List<ItemStack> a_drops) {
    // Spawn and prepare the chest block
    Block chestBlock = a_chestLoc.getBlock();
    if (!chestBlock.isPassable()) {
      // Break the block first before replacing
      chestBlock.breakNaturally();
    }
    chestBlock.setType(Material.CHEST);
    Chest chestState = (Chest) chestBlock.getState();
    Inventory chestContents = chestState.getBlockInventory();
    chestContents.clear();

    // Move the drops into the chest
    int chestSize = chestContents.getSize();
    int dropsSize = a_drops.size();
    int smallerSize = Math.min(chestSize, dropsSize);
    for (int i = smallerSize - 1; i >= 0; --i) {
      chestContents.addItem(a_drops.get(i));
      a_drops.remove(i);
    }

    return a_drops;
  }
}
