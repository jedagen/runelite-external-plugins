package com.playerrisk;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Point;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.awt.image.BufferedImage;
import lombok.Value;
import java.util.stream.Stream;
import java.util.Arrays;

@Slf4j
@PluginDescriptor(
	name = "Player Risk"
)
public class PlayerRiskPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PlayerRiskConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private MenuManager menuManager;

	private final PlayerRiskOverlay playerRiskOverlay = new PlayerRiskOverlay();
	private final PlayerRiskAboveHeadOverlay aboveHeadOverlay = new PlayerRiskAboveHeadOverlay();
	private final PlayerOutlineOverlay playerOutlineOverlay = new PlayerOutlineOverlay();
	// Data structure design:
	// - playerGearCache: Stores current equipment info and risk calculations
	// - playerAllItemsSet: SINGLE SOURCE OF TRUTH for all items ever seen (equipped + unequipped)
	private final Map<Player, PlayerGearInfo> playerGearCache = new ConcurrentHashMap<>();
	private final Map<Player, Set<Integer>> playerAllItemsSet = new ConcurrentHashMap<>();
	
	private PlayerRiskPanel logPanel;
	private NavigationButton navButton;
	private Player currentTargetPlayer = null;

	private static final String CHECK_RISK_OPTION = "Check Risk";

	@Override
	protected void startUp() throws Exception
	{
		log.info("Wilderness Player Risk plugin started!");
		
		validateAndAdjustThresholds();
		
		overlayManager.add(playerRiskOverlay);
		overlayManager.add(aboveHeadOverlay);
		overlayManager.add(playerOutlineOverlay);
		
		logPanel = new PlayerRiskPanel();
		logPanel.setItemManager(itemManager);
		logPanel.setMainPlugin(this);
		navButton = NavigationButton.builder()
			.tooltip("Player Risk")
			.icon(createIcon())
			.priority(5)
			.panel(logPanel)
			.build();
		
		clientToolbar.addNavigation(navButton);
		
		logPanel.addThresholdInfo(config.mediumThreshold(), config.highThreshold());
		
		if (config.enableCheckRiskMenu()) {
			addMenuItem();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Wilderness Player Risk plugin stopped!");
		overlayManager.remove(playerRiskOverlay);
		overlayManager.remove(aboveHeadOverlay);
		overlayManager.remove(playerOutlineOverlay);
		clientToolbar.removeNavigation(navButton);
		playerGearCache.clear();
		playerAllItemsSet.clear();
		
		// Remove the Check Risk menu option
		removeMenuItem();
	}

	private synchronized void removeMenuItem() {
		menuManager.removePlayerMenuItem(CHECK_RISK_OPTION);
	}

	private synchronized void addMenuItem() {
		if (client == null || !config.enableCheckRiskMenu()) return;
		if (Arrays.stream(client.getPlayerOptions()).noneMatch(CHECK_RISK_OPTION::equals)) {
			menuManager.addPlayerMenuItem(CHECK_RISK_OPTION);
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (!config.enableCheckRiskMenu()) {
			removeMenuItem();
		} else if (!config.enableOutsidePVP() || isInWilderness(client.getLocalPlayer())) {
			addMenuItem();
		} else {
			removeMenuItem();
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		Stream.of(event.getMenuEntries()).map(MenuEntry::getActor)
				.filter(a -> a instanceof Player)
				.map(Player.class::cast)
				.distinct()
				.map(p -> new PlayerInfo(p.getId(), p.getName(), p.getPlayerComposition()))
				.forEach(playerInfo -> storedPlayers.put(playerInfo.getId(), playerInfo));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER && event.getMenuOption().equals(CHECK_RISK_OPTION))
		{
			try
			{
				SwingUtilities.invokeAndWait(() -> clientToolbar.openPanel(navButton));
			}
			catch (InterruptedException e)
			{
				log.error("Failed to open panel", e);
				Thread.currentThread().interrupt();
				return;
			}
			catch (InvocationTargetException e)
			{
				log.error("Failed to open panel", e);
				return;
			}
			
			PlayerInfo p = getPlayerInfo(event.getId());
			if (p == null)
			{
				return;
			}

			// Find the target player in the current world view
			Player targetPlayer = client.getPlayers().stream()
				.filter(player -> player != null && player.getId() == p.getId())
				.findFirst()
				.orElse(null);
				
			if (targetPlayer != null && shouldShowPlayer(targetPlayer))
			{
				setCurrentTarget(targetPlayer);
				if (logPanel != null)
				{
					logPanel.addLog("Checking risk for player: " + targetPlayer.getName());
				}
			}
		}
		storedPlayers.clear();
	}

	private PlayerInfo getPlayerInfo(int id)
	{
		// First try to get the player from the current world view (like Equipment Inspector)
		Player p = client.getTopLevelWorldView().players().byIndex(id);
		if (p != null)
		{
			return new PlayerInfo(p.getId(), p.getName(), p.getPlayerComposition());
		}
		
		// Fallback to stored players if not found in current view
		return storedPlayers.getOrDefault(id, null);
	}

	@Value
	private static class PlayerInfo
	{
		int id;
		String name;
		PlayerComposition playerComposition;
	}

	private final Map<Integer, PlayerInfo> storedPlayers = new ConcurrentHashMap<>();

	private BufferedImage createIcon()
	{
		try
		{
			// Load the icon from the icon.png file
			java.io.File iconFile = new java.io.File("icon.png");
			if (iconFile.exists())
			{
				return javax.imageio.ImageIO.read(iconFile);
			}
			else
			{
				// Fallback to programmatically created icon if file doesn't exist
				log.warn("icon.png not found, using fallback icon");
				BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = icon.createGraphics();
				g2d.setColor(Color.ORANGE);
				g2d.fillRect(0, 0, 16, 16);
				g2d.setColor(Color.WHITE);
				g2d.drawString("$", 4, 12);
				g2d.dispose();
				return icon;
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load icon.png, using fallback icon: {}", e.getMessage());
			// Fallback to programmatically created icon if loading fails
			BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = icon.createGraphics();
			g2d.setColor(Color.ORANGE);
			g2d.fillRect(0, 0, 16, 16);
			g2d.setColor(Color.WHITE);
			g2d.drawString("$", 4, 12);
			g2d.dispose();
			return icon;
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		Player player = event.getPlayer();
		if (player != null && shouldShowPlayer(player))
		{
			updatePlayerGearInfo(player);
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		Player player = event.getPlayer();
		if (player != null)
		{
			playerGearCache.remove(player);
			playerAllItemsSet.remove(player);
			if (player == currentTargetPlayer)
			{
				currentTargetPlayer = null;
				if (logPanel != null)
				{
					logPanel.setTarget(null, null, null, false);
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			if (currentTargetPlayer != null && currentTargetPlayer.getPlayerComposition() != null)
			{
				checkForEquipmentChanges(currentTargetPlayer);
			}
			
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				WorldPoint localLocation = localPlayer.getWorldLocation();
				if (localLocation != null) {
					Collection<Player> allPlayers = client.getPlayers();
					int nearbyCount = 0;
					
					for (Player player : allPlayers) {
						if (player != null && player != localPlayer && shouldShowPlayer(player)) {
							WorldPoint playerLocation = player.getWorldLocation();
							if (playerLocation != null) {
								int distanceX = Math.abs(playerLocation.getX() - localLocation.getX());
								int distanceY = Math.abs(playerLocation.getY() - localLocation.getY());
								
								if (distanceX <= 30 && distanceY <= 30) {
									nearbyCount++;
									
									if (!playerGearCache.containsKey(player)) {
										log.debug("Found new nearby player: {} at distance ({}, {})", 
											player.getName(), distanceX, distanceY);
										updatePlayerGearInfo(player);
									}
								}
							}
						}
					}
					
					if (client.getTickCount() % 10 == 0) {
						log.debug("Currently tracking {} nearby players", nearbyCount);
					}
				}
			}
			
			checkForLocalPlayerAttack();
		}
	}

	private void checkForLocalPlayerAttack()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Check if local player is in combat
		if (localPlayer.getInteracting() instanceof Player)
		{
			Player target = (Player) localPlayer.getInteracting();
			if (shouldShowPlayer(target) && target != currentTargetPlayer)
			{
				// Local player is attacking this player, set as target
				setTargetOnAttack(target);
			}
		}
	}

	// New method to set the current target player (cleaner approach)
	public void setCurrentTarget(Player player)
	{
		if (currentTargetPlayer == player)
		{
			return;
		}
		
		log.info("Setting new target player: {}", player != null ? player.getName() : "null");
		
		currentTargetPlayer = player;
		
		if (logPanel != null)
		{
			if (player != null)
			{
				PlayerGearInfo gearInfo = playerGearCache.get(player);
				if (gearInfo != null)
				{
					log.info("Found gear info for target player: {} items, total value: {}", 
						gearInfo.getEquipment().size(), gearInfo.getTotalValue());
					
					// Build equipment maps directly from gear info (cleaner approach)
					Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
					Map<KitType, Integer> pricesMap = new HashMap<>();
					
					for (EquipmentItem item : gearInfo.getEquipment())
					{
						ItemComposition itemComp = client.getItemDefinition(item.getItemId());
						equipmentMap.put(item.getSlot(), itemComp);
						pricesMap.put(item.getSlot(), item.getPrice());
						
						log.debug("Equipment item: {} in {} slot, price: {}", 
							itemComp != null ? itemComp.getName() : "Unknown", item.getSlot(), item.getPrice());
					}
					
					logPanel.setTarget(player.getName(), equipmentMap, pricesMap, gearInfo.isSkulled());
				}
				else
				{
					log.info("No gear info found for target player, setting empty equipment");
					logPanel.setTarget(player.getName(), new HashMap<>(), new HashMap<>(), false);
				}
			}
			else
			{
				log.info("Clearing target player");
				logPanel.setTarget(null, null, null, false);
			}
		}
	}

	// Method to get the current target player (cleaner approach)
	public Player getCurrentTarget()
	{
		return currentTargetPlayer;
	}

	// Method to clear the current target (cleaner approach)
	public void clearCurrentTarget()
	{
		currentTargetPlayer = null;
		if (logPanel != null)
		{
			logPanel.setTarget(null, null, null, false);
			logPanel.addLog("Target cleared");
		}
	}

	// Method to force refresh the current target's equipment (cleaner approach)
	public void forceRefreshTargetEquipment()
	{
		if (currentTargetPlayer != null && logPanel != null)
		{
			log.info("Force refreshing equipment for target player: {}", currentTargetPlayer.getName());
			logPanel.addLog("Force refreshing equipment for target player: " + currentTargetPlayer.getName());
			
			updatePlayerGearInfo(currentTargetPlayer);
			
			PlayerGearInfo gearInfo = playerGearCache.get(currentTargetPlayer);
			if (gearInfo != null)
			{
				// Build equipment maps directly from gear info (cleaner approach)
				Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
				Map<KitType, Integer> pricesMap = new HashMap<>();
				
				for (EquipmentItem item : gearInfo.getEquipment())
				{
					ItemComposition itemComp = client.getItemDefinition(item.getItemId());
					equipmentMap.put(item.getSlot(), itemComp);
					pricesMap.put(item.getSlot(), item.getPrice());
				}
				
				logPanel.updateEquipmentData(equipmentMap, pricesMap);
				
				log.info("Equipment refresh completed for target player: {} items found", equipmentMap.size());
				logPanel.addLog("Equipment refresh completed: " + equipmentMap.size() + " items found");
			}
			else
			{
				log.warn("No gear info found after refresh for target player: {}", currentTargetPlayer.getName());
				logPanel.addLog("ERROR: No equipment data found after refresh");
			}
		}
		else
		{
			log.warn("No current target player to refresh");
			if (logPanel != null)
			{
				logPanel.addLog("No target player selected to refresh");
			}
		}
	}

	// Method to force refresh all player equipment (useful for debugging) (cleaner approach)
	public void forceRefreshAllPlayerEquipment()
	{
		log.info("Force refreshing equipment for all players");
		if (logPanel != null)
		{
			logPanel.addLog("Force refreshing equipment for all players");
		}
		
		// Clear the cache and re-read all players
		playerGearCache.clear();
		
		for (Player player : client.getPlayers())
		{
			if (shouldShowPlayer(player))
			{
				updatePlayerGearInfo(player);
			}
		}
		
		// Update the current target if we have one
		if (currentTargetPlayer != null)
		{
			setCurrentTarget(currentTargetPlayer);
		}
		
		log.info("Equipment refresh completed for all players");
		if (logPanel != null)
		{
			logPanel.addLog("Equipment refresh completed for all players");
		}
	}
	
	// Method to immediately refresh the target player's equipment display (cleaner approach)
	public void refreshTargetEquipmentDisplay()
	{
		if (currentTargetPlayer != null && logPanel != null)
		{
			PlayerGearInfo gearInfo = playerGearCache.get(currentTargetPlayer);
			if (gearInfo != null)
			{
				// Convert equipment to the format expected by the panel (cleaner approach)
				Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
				Map<KitType, Integer> pricesMap = new HashMap<>();
				
				for (EquipmentItem item : gearInfo.getEquipment())
				{
					ItemComposition itemComp = client.getItemDefinition(item.getItemId());
					equipmentMap.put(item.getSlot(), itemComp);
					pricesMap.put(item.getSlot(), item.getPrice());
				}
				
				// Update the panel with the current equipment data
				logPanel.updateEquipmentData(equipmentMap, pricesMap);
				
				log.info("Target equipment display refreshed: {} items", equipmentMap.size());
				logPanel.addLog("Equipment display refreshed: " + equipmentMap.size() + " items");
			}
		}
	}

	private void checkForEquipmentChanges(Player player)
	{
		if (player != currentTargetPlayer)
		{
			return;
		}
		
		PlayerGearInfo existingInfo = playerGearCache.get(player);
		if (existingInfo == null)
		{
			log.debug("No existing gear info for target player {}, updating", player.getName());
			updatePlayerGearInfo(player);
			return;
		}

		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			log.debug("No player composition for target player {}", player.getName());
			return;
		}
		
		// Check for specific equipment changes (cleaner approach)
		Map<KitType, Integer> currentEquipment = new HashMap<>();
		for (KitType kitType : KitType.values())
		{
			int itemId = composition.getEquipmentId(kitType);
			if (itemId != -1)
			{
				currentEquipment.put(kitType, itemId);
			}
		}
		
		// Compare with previous equipment to detect changes
		Map<KitType, Integer> previousEquipment = new HashMap<>();
		for (EquipmentItem item : existingInfo.getEquipment())
		{
			previousEquipment.put(item.getSlot(), item.getItemId());
		}
		
		// Check for equipped items
		for (Map.Entry<KitType, Integer> entry : currentEquipment.entrySet())
		{
			KitType slot = entry.getKey();
			Integer currentItemId = entry.getValue();
			Integer previousItemId = previousEquipment.get(slot);
			
			if (previousItemId == null || !previousItemId.equals(currentItemId))
			{
				// New item equipped or item changed
				try {
					ItemComposition equippedItem = client.getItemDefinition(currentItemId);
					String itemName = equippedItem != null ? equippedItem.getName() : "Unknown";
					
					if (previousItemId != null && !previousItemId.equals(currentItemId))
					{
						// Item was swapped - log both items
						ItemComposition previousItem = client.getItemDefinition(previousItemId);
						String previousItemName = previousItem != null ? previousItem.getName() : "Unknown";
						
						if (logPanel != null)
						{
							logPanel.addLog("Item swapped: " + previousItemName + " → " + itemName + " in " + slot.toString().toLowerCase().replace("_", " "));
						}
						log.info("Player {} swapped {} → {} in slot {}", player.getName(), previousItemName, itemName, slot);
					}
					else
					{
						// New item equipped
						if (logPanel != null)
						{
							logPanel.addLog("Item equipped: " + itemName + " in " + slot.toString().toLowerCase().replace("_", " "));
						}
						log.info("Player {} equipped {} in slot {}", player.getName(), itemName, slot);
					}
				} catch (Exception e) {
					log.debug("Could not get info for equipped item {}: {}", currentItemId, e.getMessage());
				}
			}
		}
		
		int currentItemCount = currentEquipment.size();
		if (currentItemCount != existingInfo.getEquipment().size())
		{
			log.info("Equipment count changed for target player {}: was {}, now {}", 
				player.getName(), existingInfo.getEquipment().size(), currentItemCount);
			
			if (logPanel != null)
			{
				logPanel.addLog("Equipment count changed: " + existingInfo.getEquipment().size() + " → " + currentItemCount + " items");
			}
			
			// Update the item union to ensure unequipped items are preserved
			updatePlayerItemUnion(player);
			
			updatePlayerGearInfo(player);
			
			if (logPanel != null)
			{
				PlayerGearInfo updatedGearInfo = playerGearCache.get(player);
				if (updatedGearInfo != null)
				{
					// Build equipment maps directly from gear info (cleaner approach)
					Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
					Map<KitType, Integer> pricesMap = new HashMap<>();
					
					for (EquipmentItem item : updatedGearInfo.getEquipment())
					{
						ItemComposition itemComp = client.getItemDefinition(item.getItemId());
						equipmentMap.put(item.getSlot(), itemComp);
						pricesMap.put(item.getSlot(), item.getPrice());
					}
					
					logPanel.updateEquipmentData(equipmentMap, pricesMap);
					logPanel.addLog("Equipment updated: " + equipmentMap.size() + " items");
				}
			}
		}
	}

	// Method to move swapped/removed equipment items to the additional equipment section
	// REMOVED: No longer tracking additional items
	
	// Method to check if a newly equipped item was previously in additional equipment
	// REMOVED: No longer tracking additional items
	
	// Method to clean up additional equipment when equipment is updated
	// REMOVED: No longer tracking additional items

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("PlayerRisk") && 
			(event.getKey().equals("renderMode") ||
			 event.getKey().equals("enableOutsidePVP") ||
			 event.getKey().equals("enableCheckRiskMenu") ||
			 event.getKey().equals("outlinePlayer") ||
			 event.getKey().equals("outlineWidth") ||
			 event.getKey().equals("outlineFeather") ||
			 event.getKey().equals("alwaysShowTotalEquipmentValue") || 
			 event.getKey().equals("assumeProtectItemIsOn") ||
			 event.getKey().equals("detectSkullStatus") ||
			 event.getKey().equals("mediumThreshold") ||
			 event.getKey().equals("highThreshold") ||
			 event.getKey().equals("lowValueColor") ||
			 event.getKey().equals("mediumValueColor") ||
			 event.getKey().equals("highValueColor")))
		{
			if (logPanel != null)
			{
				String configChangeMsg;
				if (event.getKey().equals("alwaysShowTotalEquipmentValue"))
				{
					configChangeMsg = config.alwaysShowTotalEquipmentValue() ? 
						"Now showing total equipment value" : 
						"Now showing value excluding top items (lost on death)";
				}
				else if (event.getKey().equals("assumeProtectItemIsOn"))
				{
					configChangeMsg = config.assumeProtectItemIsOn() ? 
						"Protect Item prayer assumed to be ON (ignoring 4 items)" : 
						"Protect Item prayer assumed to be OFF (ignoring 3 items)";
				}
				else if (event.getKey().equals("enableOutsidePVP"))
				{
					configChangeMsg = config.enableOutsidePVP() ? 
						"Plugin now enabled outside PVP zones" : 
						"Plugin now restricted to PVP zones only";
				}
				else if (event.getKey().equals("enableCheckRiskMenu"))
				{
					configChangeMsg = config.enableCheckRiskMenu() ? 
						"Check Risk menu option enabled" : 
						"Check Risk menu option disabled";
				}
				else if (event.getKey().equals("outlinePlayer"))
				{
					configChangeMsg = config.outlinePlayer() ? 
						"Player outlines enabled" : 
						"Player outlines disabled";
				}
				else if (event.getKey().equals("outlineWidth") || event.getKey().equals("outlineFeather"))
				{
					configChangeMsg = "Outline settings updated - Width: " + config.outlineWidth() + 
						", Feather: " + config.outlineFeather();
				}
				else if (event.getKey().equals("detectSkullStatus"))
				{
					configChangeMsg = config.detectSkullStatus() ? 
						"Skull status detection enabled" : 
						"Skull status detection disabled (assuming all players not skulled)";
				}
				else if (event.getKey().equals("mediumThreshold") || event.getKey().equals("highThreshold"))
				{
					validateAndAdjustThresholds();
					configChangeMsg = "Thresholds updated - Medium: " + config.mediumThreshold() + 
						", High: " + config.highThreshold();
				}
				else
				{
					configChangeMsg = "Color scheme updated";
				}
				logPanel.addLog("Config changed: " + configChangeMsg);
			}
			
			if (event.getKey().equals("detectSkullStatus") || event.getKey().equals("enableOutsidePVP"))
			{
				recheckAllPlayers();
			}
			else if (event.getKey().equals("enableCheckRiskMenu"))
			{
				// Refresh the menu based on new setting
				if (config.enableCheckRiskMenu()) {
					addMenuItem();
				} else {
					removeMenuItem();
				}
			}
			else
			{
				recalculateAllPlayerRisk();
			}
			
			overlayManager.remove(playerRiskOverlay);
			overlayManager.remove(aboveHeadOverlay);
			overlayManager.remove(playerOutlineOverlay);
			overlayManager.add(playerRiskOverlay);
			overlayManager.add(aboveHeadOverlay);
			overlayManager.add(playerOutlineOverlay);
		}
	}

	// Method to automatically set target when attacking (cleaner approach)
	public void setTargetOnAttack(Player target)
	{
		if (target != null && shouldShowPlayer(target))
		{
			setCurrentTarget(target);
			if (logPanel != null)
			{
				logPanel.addLog("Automatically set " + target.getName() + " as target (attacked)");
			}
		}
	}

	private boolean isInWilderness(Player player)
	{
		if (player == null)
		{
			return false;
		}
		
		// If enableOutsidePVP is true, allow the plugin to run anywhere
		if (config.enableOutsidePVP())
		{
			return true;
		}
		
		// Check if the player is in a PVP-enabled zone
		// This includes Wilderness, PVP worlds, and other PVP areas
		WorldPoint playerLocation = player.getWorldLocation();
		if (playerLocation == null)
		{
			return false;
		}
		
		// Check if in Wilderness (level 1-56)
		if (playerLocation.getY() > 3520 && playerLocation.getY() < 4000 && 
			playerLocation.getX() > 2940 && playerLocation.getX() < 3395)
		{
			return true;
		}
		
		// Check if in PVP world
		if (client.getWorldType().contains(WorldType.PVP))
		{
			return true;
		}
		
		// Check if in other PVP zones (like Clan Wars, etc.)
		// This is a basic check - you might want to expand this based on specific zones
		
		return false;
	}

	private boolean shouldShowPlayer(Player player)
	{
		if (player == null || player == client.getLocalPlayer())
		{
			log.debug("shouldShowPlayer: player is null or local player");
			return false;
		}
		
		// Always allow if enableOutsidePVP is true
		if (config.enableOutsidePVP())
		{
			log.debug("shouldShowPlayer: {} - enableOutsidePVP is true, allowing", player.getName());
			return true;
		}
		
		// Otherwise, only show in PVP zones
		boolean inWilderness = isInWilderness(player);
		log.debug("shouldShowPlayer: {} - enableOutsidePVP is false, inWilderness: {}", player.getName(), inWilderness);
		return inWilderness;
	}

	private boolean isPlayerSkulled(Player player)
	{
		return player != null && player.getSkullIcon() >= 0;
	}

	private boolean isVisible(Player targetPlayer, Graphics2D graphics)
	{
		if (targetPlayer == null || graphics == null)
		{
			return false;
		}

        LocalPoint targetLocation = targetPlayer.getLocalLocation();
        if (targetLocation == null)
        {
            return false;
        }

        Player mostValuableOnTile = null;
        int highestValue = -1;
        
        for (Player player : client.getPlayers())
        {
            if (player == null || !shouldShowPlayer(player))
            {
                continue;
            }

            LocalPoint loc = player.getLocalLocation();
            if (loc == null)
            {
                continue;
            }

            if (loc.getSceneX() == targetLocation.getSceneX() &&
                loc.getSceneY() == targetLocation.getSceneY())
            {
                PlayerGearInfo gearInfo = playerGearCache.get(player);
                if (gearInfo != null)
                {
                    int playerRisk = gearInfo.getFinalValue();
                    if (playerRisk > highestValue)
                    {
                        highestValue = playerRisk;
                        mostValuableOnTile = player;
                    }
                }
            }
        }

        return targetPlayer == mostValuableOnTile;
    }

	private void updatePlayerGearInfo(Player player)
	{
		if (player == null)
		{
			return;
		}

		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			log.debug("Player {} has no PlayerComposition", player.getName());
			if (logPanel != null)
			{
				logPanel.addErrorLog("Player " + player.getName() + " has no PlayerComposition");
			}
			return;
		}

		// Check skull status if enabled
		boolean isSkulled = false;
		if (config.detectSkullStatus())
		{
			isSkulled = isPlayerSkulled(player);
			if (logPanel != null)
			{
				logPanel.addLog("Player " + player.getName() + " skull status: " + (isSkulled ? "SKULLED" : "Not skulled"));
			}
		}

		// Read equipment directly from PlayerComposition (cleaner approach like Equipment Inspector)
		List<EquipmentItem> equipment = new ArrayList<>();
		int totalValue = 0;

		for (KitType kitType : KitType.values())
		{
			int itemId = composition.getEquipmentId(kitType);
			if (itemId != -1)
			{
				try
				{
					ItemComposition itemComposition = client.getItemDefinition(itemId);
					int price = itemManager.getItemPrice(itemId);
					
					if (logPanel != null)
					{
						logPanel.addEquipmentLog(player.getName(), itemId, price, kitType.toString());
					}
					
					equipment.add(new EquipmentItem(itemId, price, kitType));
					totalValue += price;
					
					log.debug("Player {} has item {} worth {} in slot {}", 
						player.getName(), itemId, price, kitType);
				}
				catch (Exception e)
				{
					log.debug("Could not read slot {} for player {}: {}", kitType, player.getName(), e.getMessage());
					if (logPanel != null)
					{
						logPanel.addErrorLog("Could not read slot " + kitType + " for player " + player.getName() + ": " + e.getMessage());
					}
				}
			}
		}

		// Calculate final value based on configuration
		int finalValue;
		if (config.alwaysShowTotalEquipmentValue())
		{
			finalValue = totalValue;
			if (logPanel != null)
			{
				logPanel.addPlayerLogWithSkull(player.getName(), equipment.size(), finalValue, false, isSkulled, "");
			}
		}
		else
		{
			int baseItemsToKeep = isSkulled ? 0 : 3;
			int itemsToIgnore = baseItemsToKeep + (config.assumeProtectItemIsOn() ? 1 : 0);
			finalValue = calculateValueExcludingTopItems(equipment, itemsToIgnore);
			
			if (logPanel != null)
			{
				String protectItemText = config.assumeProtectItemIsOn() ? " (Protect Item ON)" : "";
				logPanel.addPlayerLogWithSkull(player.getName(), equipment.size(), finalValue, true, isSkulled, protectItemText);
			}
		}

		// Create and store gear info
		PlayerGearInfo gearInfo = new PlayerGearInfo(equipment, totalValue, finalValue, isSkulled);
		playerGearCache.put(player, gearInfo);
		
		// Update item tracking - this is now the single source of truth for all items
		updatePlayerItemUnion(player);
		
		// Update current target if needed
		if (player == currentTargetPlayer)
		{
			setCurrentTarget(player);
		}
		
		log.info("Updated gear info for {}: {} items, total value: {}, final value: {}", 
			player.getName(), equipment.size(), totalValue, finalValue);
		
		// Log unequipped items info
		Set<Integer> unequippedItems = getPlayerItemUnion(player);
		unequippedItems.removeAll(getPlayerCurrentEquipment(player));
		if (!unequippedItems.isEmpty() && logPanel != null)
		{
			logPanel.addLog("Player has " + unequippedItems.size() + " unequipped items preserved in memory");
		}
	}

	private void validateAndAdjustThresholds()
	{
		int mediumThreshold = config.mediumThreshold();
		int highThreshold = config.highThreshold();
		
		if (mediumThreshold >= highThreshold)
		{
			int newHighThreshold = mediumThreshold + 1;
			configManager.setConfiguration("PlayerRisk", "highThreshold", newHighThreshold);
			
			if (logPanel != null)
			{
				logPanel.addLog("Threshold validation: Adjusted high threshold from " + highThreshold + 
					" to " + newHighThreshold + " to ensure medium < high");
			}
		}
		
		if (logPanel != null)
		{
			logPanel.addThresholdInfo(config.mediumThreshold(), config.highThreshold());
		}
	}

	private Color getPlayerRiskColor(int value)
	{
		if (value >= config.highThreshold())
		{
			return config.highValueColor();
		}
		else if (value >= config.mediumThreshold())
		{
			return config.mediumValueColor();
		}
		else
		{
			return config.lowValueColor();
		}
	}

	private void recheckAllPlayers()
	{
		if (logPanel != null)
		{
			logPanel.addLog("Skull detection config changed - rechecking all players for accurate skull status");
		}
		
		List<Player> playersToRecheck = new ArrayList<>(playerGearCache.keySet());
		playerGearCache.clear();
		
		for (Player player : playersToRecheck)
		{
			if (player != null && shouldShowPlayer(player))
			{
				updatePlayerGearInfo(player);
			}
		}
		
		if (logPanel != null)
		{
			logPanel.addLog("Rechecked " + playersToRecheck.size() + " players for skull status");
		}
	}

	private void recalculateAllPlayerRisk()
	{
		for (Map.Entry<Player, PlayerGearInfo> entry : playerGearCache.entrySet())
		{
			Player player = entry.getKey();
			PlayerGearInfo oldInfo = entry.getValue();
			
			int newFinalValue;
			if (config.alwaysShowTotalEquipmentValue())
			{
				newFinalValue = oldInfo.getTotalValue();
			}
			else
			{
				int baseItemsToKeep = oldInfo.isSkulled() ? 0 : 3;
				int itemsToIgnore = baseItemsToKeep + (config.assumeProtectItemIsOn() ? 1 : 0);
				newFinalValue = calculateValueExcludingTopItems(oldInfo.getEquipment(), itemsToIgnore);
			}
			
			PlayerGearInfo newInfo = new PlayerGearInfo(
				oldInfo.getEquipment(), 
				oldInfo.getTotalValue(), 
				newFinalValue,
				oldInfo.isSkulled()
			);
			
			playerGearCache.put(player, newInfo);
			
			if (logPanel != null)
			{
				logPanel.addLog("Recalculated value for " + player.getName() + ": " + newFinalValue);
			}
		}
	}

	private int calculateValueExcludingTopItems(List<EquipmentItem> equipment, int itemsToIgnore)
	{
		if (equipment.isEmpty())
		{
			return 0;
		}

		List<EquipmentItem> sortedEquipment = new ArrayList<>(equipment);
		sortedEquipment.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));

		int totalValue = 0;
		int count = Math.min(sortedEquipment.size(), itemsToIgnore);

		for (int i = count; i < sortedEquipment.size(); i++)
		{
			totalValue += sortedEquipment.get(i).getPrice();
		}

		return totalValue;
	}



	private String formatValue(int value)
	{
		if (value >= 1000000)
		{
			return String.format("%.1fM", value / 1000000.0);
		}
		else if (value >= 1000)
		{
			return String.format("%.1fK", value / 1000.0);
		}
		else
		{
			return String.valueOf(value);
		}
	}

	@Provides
	PlayerRiskConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayerRiskConfig.class);
	}

	public static class EquipmentItem
	{
		private final int itemId;
		private final int price;
		private final KitType slot;

		public EquipmentItem(int itemId, int price, KitType slot)
		{
			this.itemId = itemId;
			this.price = price;
			this.slot = slot;
		}

		public int getItemId() { return itemId; }
		public int getPrice() { return price; }
		public KitType getSlot() { return slot; }
	}

	public static class PlayerGearInfo
	{
		private final List<EquipmentItem> equipment;
		private final int totalValue;
		private final int finalValue;
		private final boolean isSkulled;

		public PlayerGearInfo(List<EquipmentItem> equipment, int totalValue, int finalValue, boolean isSkulled)
		{
			this.equipment = equipment;
			this.totalValue = totalValue;
			this.finalValue = finalValue;
			this.isSkulled = isSkulled;
		}

		public List<EquipmentItem> getEquipment() { return equipment; }
		public int getTotalValue() { return totalValue; }
		public int getFinalValue() { return finalValue; }
		public boolean isSkulled() { return isSkulled; }
	}

	private class PlayerRiskOverlay extends Overlay
	{
		public PlayerRiskOverlay()
		{
			setPosition(OverlayPosition.TOP_LEFT);
			setPriority(OverlayPriority.LOW);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			if (config.renderMode() == PlayerRiskConfig.RenderMode.OVERHEAD || 
				config.renderMode() == PlayerRiskConfig.RenderMode.NONE || 
				client.getGameState() != GameState.LOGGED_IN)
			{
				return null;
			}

			List<Player> nearbyPlayers = client.getPlayers().stream()
				.filter(PlayerRiskPlugin.this::shouldShowPlayer)
				.sorted((p1, p2) -> {
					PlayerGearInfo info1 = playerGearCache.get(p1);
					PlayerGearInfo info2 = playerGearCache.get(p2);
					if (info1 == null && info2 == null) return 0;
					if (info1 == null) return 1;
					if (info2 == null) return -1;
					return Integer.compare(info2.getFinalValue(), info1.getFinalValue());
				})
				.limit(5)
				.collect(Collectors.toList());

			if (nearbyPlayers.isEmpty())
			{
				return null;
			}

			int y = 10;
			graphics.setColor(Color.WHITE);
			graphics.setFont(new Font("Arial", Font.BOLD, 12));

			for (Player player : nearbyPlayers)
			{
				PlayerGearInfo gearInfo = playerGearCache.get(player);
				if (gearInfo != null)
				{
					String text = String.format("%s: %s", 
						player.getName(), 
						formatValue(gearInfo.getFinalValue()));
					
					graphics.setColor(getPlayerRiskColor(gearInfo.getFinalValue()));
					graphics.drawString(text, 10, y);
				
					y += 15;
				}
			}

			return new Dimension(200, y + 5);
		}
	}

	private class PlayerRiskAboveHeadOverlay extends Overlay
	{
		public PlayerRiskAboveHeadOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setPriority(OverlayPriority.HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			if (config.renderMode() == PlayerRiskConfig.RenderMode.UI || 
				config.renderMode() == PlayerRiskConfig.RenderMode.NONE || 
				client.getGameState() != GameState.LOGGED_IN)
			{
				return null;
			}
			
			for (Player player : client.getPlayers())
			{
				PlayerGearInfo gearInfo = playerGearCache.get(player);
				if (gearInfo == null)
				{
					continue;
				}

				// Check if this player is actually visible (not obscured by others on the same tile)
				if (!isVisible(player, graphics))
				{
					continue;
				}

				final int zOffset = player.getLogicalHeight() + config.aboveHeadDistance();
				final String valueText = formatValue(gearInfo.getFinalValue());
				
				Point textLocation = player.getCanvasTextLocation(graphics, valueText, zOffset);
				
				if (textLocation == null) {
					continue;
				}

				int x = textLocation.getX();
				int y = textLocation.getY();
				
				graphics.setColor(getPlayerRiskColor(gearInfo.getFinalValue()));
				graphics.setFont(new Font("Arial", Font.BOLD, config.aboveHeadTextSize()));
				graphics.drawString(valueText, x, y);
			}

			return null;
		}
	}

	private class PlayerOutlineOverlay extends Overlay
	{
		public PlayerOutlineOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setPriority(OverlayPriority.HIGH);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			if (!config.outlinePlayer() || client.getGameState() != GameState.LOGGED_IN)
			{
				return null;
			}

			// Only outline players that are actually visible (not obscured by others on the same tile)
			for (Player player : client.getPlayers())
			{
				if (!shouldShowPlayer(player))
				{
					continue;
				}

				PlayerGearInfo gearInfo = playerGearCache.get(player);
				if (gearInfo == null)
				{
					continue;
				}

				// Check if this player is actually visible (not obscured by others on the same tile)
				if (!isVisible(player, graphics))
				{
					continue;
				}

				// Get the appropriate color based on player risk
				Color outlineColor = getPlayerRiskColor(gearInfo.getFinalValue());

				// Draw the outline using ModelOutlineRenderer
				modelOutlineRenderer.drawOutline(player, config.outlineWidth(), outlineColor, config.outlineFeather());
			}

			return null;
		}
	}

	// Method to get comprehensive item information for the current target player
	public Map<String, Object> getComprehensiveTargetInfo()
	{
		// Use the new union-based system by default (cleaner approach)
		return getComprehensiveTargetInfoUnion();
	}
	
	// SINGLE SOURCE OF TRUTH: Updates the player's item union (all items ever seen)
	// This method consolidates all item tracking logic to prevent duplication and inconsistencies
	private void updatePlayerItemUnion(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		// Get current equipment from PlayerComposition
		Set<Integer> currentItems = new HashSet<>();
		PlayerComposition composition = player.getPlayerComposition();
		if (composition != null)
		{
			for (KitType kitType : KitType.values())
			{
				int itemId = composition.getEquipmentId(kitType);
				if (itemId != -1)
				{
					currentItems.add(itemId);
				}
			}
		}
		
		// Get or create the player's item union set
		Set<Integer> allItems = playerAllItemsSet.computeIfAbsent(player, k -> new HashSet<>());
		
		// Add current items to the union - this preserves all items ever seen
		allItems.addAll(currentItems);
		
		log.debug("Updated item union for player {}: current={}, all={}", 
			player.getName(), currentItems.size(), allItems.size());
		
		if (player == currentTargetPlayer && logPanel != null)
		{
			int unequippedCount = allItems.size() - currentItems.size();
			logPanel.addLog("Union updated: " + currentItems.size() + " current + " + 
				unequippedCount + " previous = " + allItems.size() + " total");
		}
	}
	
	// Get the union of all items the player has ever had (cleaner approach)
	public Set<Integer> getPlayerItemUnion(Player player)
	{
		return playerAllItemsSet.getOrDefault(player, new HashSet<>());
	}
	
	// Get current equipment items only (derived from playerGearCache)
	public Set<Integer> getPlayerCurrentEquipment(Player player)
	{
		PlayerGearInfo gearInfo = playerGearCache.get(player);
		if (gearInfo == null)
		{
			return new HashSet<>();
		}
		
		// Extract current equipment IDs from the gear info
		return gearInfo.getEquipment().stream()
			.map(EquipmentItem::getItemId)
			.collect(Collectors.toSet());
	}
	
	// Get comprehensive item information using the union system (cleaner approach)
	public Map<String, Object> getComprehensiveTargetInfoUnion()
	{
		if (currentTargetPlayer == null)
		{
			return null;
		}
		
		Map<String, Object> info = new HashMap<>();
		
		PlayerGearInfo gearInfo = playerGearCache.get(currentTargetPlayer);
		if (gearInfo != null)
		{
			// Build equipment maps directly from gear info (cleaner approach)
			Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
			Map<KitType, Integer> pricesMap = new HashMap<>();
			
			for (EquipmentItem item : gearInfo.getEquipment())
			{
				ItemComposition itemComp = client.getItemDefinition(item.getItemId());
				equipmentMap.put(item.getSlot(), itemComp);
				pricesMap.put(item.getSlot(), item.getPrice());
			}
			
			info.put("equipment", equipmentMap);
			info.put("prices", pricesMap);
			info.put("isSkulled", gearInfo.isSkulled());
		}
		
		Set<Integer> allItemIds = getPlayerItemUnion(currentTargetPlayer);
		List<ItemInfo> allItems = new ArrayList<>();
		
		for (Integer itemId : allItemIds)
		{
			try
			{
				ItemComposition itemComp = client.getItemDefinition(itemId);
				int price = itemManager.getItemPrice(itemId);
				allItems.add(new ItemInfo(itemId, 
					itemComp != null ? itemComp.getName() : "Unknown", price));
			}
			catch (Exception e)
			{
				log.debug("Could not get info for item {}: {}", itemId, e.getMessage());
				allItems.add(new ItemInfo(itemId, "Unknown", 0));
			}
		}
		
		allItems.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
		info.put("allItems", allItems);
		
		Set<Integer> currentEquipment = getPlayerCurrentEquipment(currentTargetPlayer);
		Set<Integer> unequippedItems = new HashSet<>(allItemIds);
		unequippedItems.removeAll(currentEquipment);
		
		info.put("unionStats", Map.of(
			"totalItems", allItemIds.size(),
			"currentEquipment", currentEquipment.size(),
			"previouslySeen", allItemIds.size() - currentEquipment.size(),
			"unequippedItems", unequippedItems.size()
		));
		
		// Add detailed unequipped items info
		List<ItemInfo> unequippedItemsList = new ArrayList<>();
		for (Integer itemId : unequippedItems) {
			try {
				ItemComposition itemComp = client.getItemDefinition(itemId);
				int price = itemManager.getItemPrice(itemId);
				unequippedItemsList.add(new ItemInfo(itemId, 
					itemComp != null ? itemComp.getName() : "Unknown", price));
			} catch (Exception e) {
				log.debug("Could not get info for unequipped item {}: {}", itemId, e.getMessage());
			}
		}
		unequippedItemsList.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
		info.put("unequippedItemsList", unequippedItemsList);
		
		return info;
	}
	
	@Value
	public static class ItemInfo
	{
		int itemId;
		String name;
		int price;
	}
	
		// Equipment integration logic moved from panel (cleaner approach)
	public List<ItemInfo> getInventoryItems(Player player) {
		if (player == null) {
			return new ArrayList<>();
		}
		
		// Get the union of all items
		Set<Integer> allItemIds = getPlayerItemUnion(player);
		List<ItemInfo> inventoryItems = new ArrayList<>();
		
		// Get current equipment
		PlayerGearInfo gearInfo = playerGearCache.get(player);
		if (gearInfo != null) {
			Set<Integer> currentEquipmentIds = gearInfo.getEquipment().stream()
				.map(EquipmentItem::getItemId)
				.collect(Collectors.toSet());
			
			// Filter out items that are currently equipped
			for (Integer itemId : allItemIds) {
				if (!currentEquipmentIds.contains(itemId)) {
					try {
						ItemComposition itemComp = client.getItemDefinition(itemId);
						int price = itemManager.getItemPrice(itemId);
						inventoryItems.add(new ItemInfo(itemId, 
							itemComp != null ? itemComp.getName() : "Unknown", price));
					} catch (Exception e) {
						log.debug("Could not get info for item {}: {}", itemId, e.getMessage());
						inventoryItems.add(new ItemInfo(itemId, "Unknown", 0));
					}
				}
			}
		}
		
		// Sort inventory items by price (highest first) to show most valuable unequipped items first
		inventoryItems.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
		
		return inventoryItems;
	}
	
	// Risk calculation logic moved from panel
	public long calculatePKProfit(Player player, boolean isSkulled, boolean hasProtectItem) {
		if (player == null) {
			return 0;
		}
		
		PlayerGearInfo gearInfo = playerGearCache.get(player);
		if (gearInfo == null || gearInfo.getEquipment().isEmpty()) {
			return 0;
		}
		
		// Get all items (equipped + unequipped) for comprehensive risk calculation
		List<EquipmentItem> allItems = new ArrayList<>(gearInfo.getEquipment());
		
		// Add unequipped items to the risk calculation
		Set<Integer> unequippedItemIds = getPlayerItemUnion(player);
		unequippedItemIds.removeAll(getPlayerCurrentEquipment(player));
		
		for (Integer itemId : unequippedItemIds) {
			try {
				int price = itemManager.getItemPrice(itemId);
				// Use a dummy slot for unequipped items
				allItems.add(new EquipmentItem(itemId, price, KitType.AMULET));
			} catch (Exception e) {
				log.debug("Could not get price for unequipped item {}: {}", itemId, e.getMessage());
			}
		}
		
		// Sort all items by price (highest first)
		allItems.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
		
		int itemsToKeep = isSkulled ? 0 : 3;
		if (hasProtectItem) {
			itemsToKeep++;
		}
		
		long totalLost = 0;
		for (int i = itemsToKeep; i < allItems.size(); i++) {
			totalLost += allItems.get(i).getPrice();
		}
		
		return totalLost;
	}
	
	// Equipment display logic moved from panel (cleaner approach)
	public Map<String, Object> getEquipmentDisplayInfo(Player player) {
		if (player == null) {
			return new HashMap<>();
		}
		
		PlayerGearInfo gearInfo = playerGearCache.get(player);
		if (gearInfo == null) {
			return new HashMap<>();
		}
		
		Map<String, Object> info = new HashMap<>();
		
		// Calculate risk values
		long riskWithProtectItem = calculatePKProfit(player, gearInfo.isSkulled(), true);
		long riskWithoutProtectItem = calculatePKProfit(player, gearInfo.isSkulled(), false);
		
		info.put("riskWithProtectItem", riskWithProtectItem);
		info.put("riskWithoutProtectItem", riskWithoutProtectItem);
		info.put("isSkulled", gearInfo.isSkulled());
		info.put("equipmentCount", gearInfo.getEquipment().size());
		info.put("totalValue", gearInfo.getTotalValue());
		
		// Add unequipped items count
		Set<Integer> unequippedItems = getPlayerItemUnion(player);
		unequippedItems.removeAll(getPlayerCurrentEquipment(player));
		info.put("unequippedCount", unequippedItems.size());
		
		// Calculate total value including unequipped items
		long totalValueWithUnequipped = gearInfo.getTotalValue();
		for (Integer itemId : unequippedItems) {
			try {
				totalValueWithUnequipped += itemManager.getItemPrice(itemId);
			} catch (Exception e) {
				log.debug("Could not get price for unequipped item {}: {}", itemId, e.getMessage());
			}
		}
		info.put("totalValueWithUnequipped", totalValueWithUnequipped);
		
		return info;
	}
	
	// Comprehensive target info method for the panel (cleaner approach)
	public Map<String, Object> getTargetInfoForPanel() {
		log.info("getTargetInfoForPanel() called, currentTargetPlayer: {}", currentTargetPlayer != null ? currentTargetPlayer.getName() : "null");
		
		if (currentTargetPlayer == null) {
			log.info("No current target player, returning empty info");
			Map<String, Object> emptyInfo = new HashMap<>();
			emptyInfo.put("hasTarget", false);
			emptyInfo.put("playerName", null);
			emptyInfo.put("equipment", new HashMap<>());
			emptyInfo.put("prices", new HashMap<>());
			emptyInfo.put("isSkulled", false);
			emptyInfo.put("inventoryItems", new ArrayList<>());
			emptyInfo.put("riskInfo", new HashMap<>());
			return emptyInfo;
		}
		
		log.info("Building target info for player: {}", currentTargetPlayer.getName());
		Map<String, Object> info = new HashMap<>();
		info.put("hasTarget", true);
		info.put("playerName", currentTargetPlayer.getName());
		
		// Get equipment info
		PlayerGearInfo gearInfo = playerGearCache.get(currentTargetPlayer);
		if (gearInfo != null) {
			log.info("Found gear info: {} items, total value: {}", gearInfo.getEquipment().size(), gearInfo.getTotalValue());
			// Build equipment maps directly from gear info (cleaner approach)
			Map<KitType, ItemComposition> equipmentMap = new HashMap<>();
			Map<KitType, Integer> pricesMap = new HashMap<>();
			
			for (EquipmentItem item : gearInfo.getEquipment()) {
				ItemComposition itemComp = client.getItemDefinition(item.getItemId());
				equipmentMap.put(item.getSlot(), itemComp);
				pricesMap.put(item.getSlot(), item.getPrice());
			}
			
			info.put("equipment", equipmentMap);
			info.put("prices", pricesMap);
			info.put("isSkulled", gearInfo.isSkulled());
			info.put("equipmentCount", gearInfo.getEquipment().size());
			info.put("totalValue", gearInfo.getTotalValue());
		} else {
			log.warn("No gear info found for current target player");
			info.put("equipment", new HashMap<>());
			info.put("prices", new HashMap<>());
			info.put("isSkulled", false);
			info.put("equipmentCount", 0);
			info.put("totalValue", 0);
		}
		
		// Get inventory items
		List<ItemInfo> inventoryItems = getInventoryItems(currentTargetPlayer);
		info.put("inventoryItems", inventoryItems);
		log.info("Found {} inventory items", inventoryItems.size());
		
		// Get risk calculation info
		Map<String, Object> riskInfo = new HashMap<>();
		if (gearInfo != null) {
			riskInfo.put("pkProfitNoSkullNoProtect", calculatePKProfit(currentTargetPlayer, false, false));
			riskInfo.put("pkProfitNoSkullWithProtect", calculatePKProfit(currentTargetPlayer, false, true));
			riskInfo.put("pkProfitSkullNoProtect", calculatePKProfit(currentTargetPlayer, true, false));
			riskInfo.put("pkProfitSkullWithProtect", calculatePKProfit(currentTargetPlayer, true, true));
		} else {
			riskInfo.put("pkProfitNoSkullNoProtect", 0L);
			riskInfo.put("pkProfitNoSkullWithProtect", 0L);
			riskInfo.put("pkProfitSkullNoProtect", 0L);
			riskInfo.put("pkProfitSkullWithProtect", 0L);
		}
		info.put("riskInfo", riskInfo);
		
		log.info("Returning target info with {} equipment items", ((Map<?, ?>) info.get("equipment")).size());
		return info;
	}
	
	// Clear target method (cleaner approach)
	public void clearTarget() {
		log.info("clearTarget() method called");
		currentTargetPlayer = null;
		if (logPanel != null) {
			log.info("Log panel found, updating display");
			logPanel.addLog("Target cleared");
			// Update the panel display to reflect the cleared target
			logPanel.updateFromMainPlugin();
		} else {
			log.warn("Log panel is null, cannot update display");
		}
	}
	
	// Refresh target equipment method (cleaner approach)
	public void refreshTargetEquipment() {
		log.info("refreshTargetEquipment() method called");
		if (currentTargetPlayer != null) {
			log.info("Refreshing equipment for target player: {}", currentTargetPlayer.getName());
			if (logPanel != null) {
				logPanel.addLog("Refreshing equipment for target player: " + currentTargetPlayer.getName());
			}
			
			// Clear the target's gear info from cache first
			playerGearCache.remove(currentTargetPlayer);
			playerAllItemsSet.remove(currentTargetPlayer);
			
			if (logPanel != null) {
				logPanel.addLog("Cleared cached equipment data for target player");
			}
			
			// Force update the player's gear info (fresh reading)
			updatePlayerGearInfo(currentTargetPlayer);
			
			// Verify the refresh worked
			PlayerGearInfo refreshedGearInfo = playerGearCache.get(currentTargetPlayer);
			if (refreshedGearInfo != null) {
				log.info("Equipment refresh successful: {} items, total value: {}", 
					refreshedGearInfo.getEquipment().size(), refreshedGearInfo.getTotalValue());
				if (logPanel != null) {
					logPanel.addLog("Equipment refresh successful: " + refreshedGearInfo.getEquipment().size() + " items");
				}
			} else {
				log.warn("Equipment refresh failed - no gear info found after refresh");
				if (logPanel != null) {
					logPanel.addLog("ERROR: Equipment refresh failed - no gear info found");
				}
			}
			
			if (logPanel != null) {
				// Update the panel display with the refreshed data
				logPanel.updateFromMainPlugin();
			}
		} else {
			log.warn("No current target player to refresh");
			if (logPanel != null) {
				logPanel.addLog("No target player selected to refresh");
			}
		}
	}
	
	// Refresh all players method (cleaner approach)
	public void refreshAllPlayers() {
		log.info("refreshAllPlayers() method called");
		log.info("Refreshing equipment for all players");
		log.info("Config - enableOutsidePVP: {}", config.enableOutsidePVP());
		log.info("Config - detectSkullStatus: {}", config.detectSkullStatus());
		
		if (logPanel != null) {
			logPanel.addLog("Refreshing equipment for all players");
			logPanel.addLog("Config - enableOutsidePVP: " + config.enableOutsidePVP());
		}
		
		Player previousTarget = currentTargetPlayer;
		
		playerGearCache.clear();
		playerAllItemsSet.clear();
		
		log.info("Cache cleared, now actively scanning for nearby players");
		if (logPanel != null) {
			logPanel.addLog("Cache cleared, now actively scanning for nearby players");
		}
		
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) {
			log.warn("Local player is null, cannot scan for nearby players");
			if (logPanel != null) {
				logPanel.addLog("ERROR: Local player is null, cannot scan");
			}
			return;
		}
		
		WorldPoint localLocation = localPlayer.getWorldLocation();
		if (localLocation == null) {
			log.warn("Local player location is null, cannot scan for nearby players");
			if (logPanel != null) {
				logPanel.addLog("ERROR: Local player location is null, cannot scan");
			}
			return;
		}
		
		log.info("Local player at: {}, {}", localLocation.getX(), localLocation.getY());
		
		Collection<Player> loadedPlayers = client.getPlayers();
		log.info("Found {} currently loaded players", loadedPlayers.size());
		
		int scanRadius = 20;
		List<Player> nearbyPlayers = new ArrayList<>();
		
		for (Player player : loadedPlayers) {
			if (player != null && player != localPlayer) {
				WorldPoint playerLocation = player.getWorldLocation();
				if (playerLocation != null) {
					int distanceX = Math.abs(playerLocation.getX() - localLocation.getX());
					int distanceY = Math.abs(playerLocation.getY() - localLocation.getY());
					
					if (distanceX <= scanRadius && distanceY <= scanRadius) {
						nearbyPlayers.add(player);
						log.debug("Found nearby loaded player: {} at distance ({}, {})", 
							player.getName(), distanceX, distanceY);
					}
				}
			}
		}
		
		log.info("Found {} nearby players within {} tile radius", nearbyPlayers.size(), scanRadius);
		if (logPanel != null) {
			logPanel.addLog("Found " + nearbyPlayers.size() + " nearby players within " + scanRadius + " tile radius");
		}
		
		int refreshedCount = 0;
		int skippedCount = 0;
		
		for (Player player : nearbyPlayers) {
			if (player == null) {
				log.debug("Skipping null player");
				continue;
			}
			
			log.debug("Processing nearby player: {} (shouldShow: {})", player.getName(), shouldShowPlayer(player));
			
			if (shouldShowPlayer(player)) {
				log.debug("Refreshing nearby player: {}", player.getName());
				updatePlayerGearInfo(player);
				refreshedCount++;
			} else {
				log.debug("Skipping nearby player: {} - shouldShowPlayer returned false", player.getName());
				skippedCount++;
			}
		}
		
		log.info("Refreshed {} nearby players, skipped {} nearby players", refreshedCount, skippedCount);
		if (logPanel != null) {
			logPanel.addLog("Refreshed " + refreshedCount + " nearby players, skipped " + skippedCount + " nearby players");
		}
		
		log.info("Attempting to force-load additional players in the area");
		if (logPanel != null) {
			logPanel.addLog("Attempting to force-load additional players in the area");
		}
		
		try {
			onGameTick(new GameTick());
		} catch (Exception e) {
			log.debug("Manual game tick check failed: {}", e.getMessage());
		}
		
		if (previousTarget != null) {
			Player refreshedTarget = nearbyPlayers.stream()
				.filter(p -> p != null && p.getName() != null && p.getName().equals(previousTarget.getName()))
				.findFirst()
				.orElse(null);
			
			if (refreshedTarget != null) {
				log.info("Restoring previous target: {}", refreshedTarget.getName());
				currentTargetPlayer = refreshedTarget;
				if (logPanel != null) {
					logPanel.addLog("Restored previous target: " + refreshedTarget.getName());
				}
			} else {
				log.info("Previous target no longer exists, clearing target");
				currentTargetPlayer = null;
				if (logPanel != null) {
					logPanel.addLog("Previous target no longer exists, clearing target");
				}
			}
		}
		
		log.info("Equipment refresh completed for all nearby players");
		if (logPanel != null) {
			logPanel.addLog("Equipment refresh completed for all nearby players");
			
			logPanel.updateFromMainPlugin();
			
			logPanel.addLog("Current player cache contains " + playerGearCache.size() + " players");
			if (currentTargetPlayer != null) {
				logPanel.addLog("Current target: " + currentTargetPlayer.getName());
			} else {
				logPanel.addLog("No current target selected");
			}
		}
	}
}

