package com.playerrisk;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemComposition;
import net.runelite.api.kit.KitType;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.Component;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Player;

@Slf4j
public class PlayerRiskPanel extends PluginPanel
{
	private final JTextArea logArea;
	private final JScrollPane scrollPane;
	private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger logCounter = new AtomicInteger(0);
	private static final int MAX_LOGS = 100;
	
	private JPanel equipmentPanels;
	private JPanel header;
	private JLabel nameLabel;
	private JPanel statsPanel;
	private JPanel logsPanel;
	private JButton toggleLogsButton;
	private boolean logsExpanded = false;
	
	private String currentTarget = null;
	private Map<KitType, ItemComposition> currentEquipment = new HashMap<>();
	private Map<KitType, Integer> currentPrices = new HashMap<>();
	private Set<Integer> inventoryItemIds = new HashSet<>();
	private boolean currentTargetSkulled = false;
	private ItemManager itemManager;
	
	private PlayerRiskPlugin mainPlugin;
	
	private JButton refreshButton;
	
	private GridBagConstraints c;

	public PlayerRiskPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setPreferredSize(new Dimension(320, 990));
		setMinimumSize(new Dimension(320, 890));

		header = new JPanel();
		header.setLayout(new BorderLayout());
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, new Color(58, 58, 58)),
			new EmptyBorder(0, 0, 5, 0)));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		nameLabel = new JLabel("No player selected");
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeFont());
		header.add(nameLabel, BorderLayout.CENTER);

		statsPanel = new JPanel();
		statsPanel.setLayout(new GridBagLayout());
		statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		equipmentPanels = new JPanel(new GridBagLayout());
		equipmentPanels.setBackground(ColorScheme.DARK_GRAY_COLOR);
		
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;

		logsPanel = new JPanel();
		logsPanel.setLayout(new BorderLayout());
		logsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		logsPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

		toggleLogsButton = new JButton("▼ Show Logs");
		toggleLogsButton.addActionListener(e -> toggleLogs());
		toggleLogsButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		toggleLogsButton.setForeground(Color.WHITE);
		toggleLogsButton.setBorder(new EmptyBorder(5, 10, 5, 10));

		logArea = new JTextArea();
		logArea.setEditable(false);
		logArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		logArea.setForeground(Color.WHITE);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		logArea.setLineWrap(true);
		logArea.setWrapStyleWord(true);

		scrollPane = new JScrollPane(logArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new Dimension(300, 300));
		scrollPane.setVisible(false);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton clearButton = new JButton("Clear Logs");
		clearButton.addActionListener(e -> clearLogs());
		buttonPanel.add(clearButton);

		JButton exportButton = new JButton("Export Logs");
		exportButton.addActionListener(e -> exportLogs());
		buttonPanel.add(exportButton);

		logsPanel.add(toggleLogsButton, BorderLayout.NORTH);
		logsPanel.add(scrollPane, BorderLayout.CENTER);
		logsPanel.add(buttonPanel, BorderLayout.SOUTH);

		JPanel mainContentPanel = new JPanel();
		mainContentPanel.setLayout(new BoxLayout(mainContentPanel, BoxLayout.Y_AXIS));
		mainContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mainContentPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		
		mainContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		mainContentPanel.add(equipmentPanels);
		mainContentPanel.add(statsPanel);
		
		mainContentPanel.add(Box.createVerticalGlue());
		
		JPanel bottomContainer = new JPanel();
		bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));
		bottomContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		
		JButton clearTargetButton = new JButton("Clear Target");
		clearTargetButton.addActionListener(e -> clearTarget());
		clearTargetButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clearTargetButton.setForeground(Color.WHITE);
		clearTargetButton.setBorder(new EmptyBorder(5, 10, 5, 10));
		
		JButton refreshButton = new JButton("🔄 Refresh Equipment");
		refreshButton.addActionListener(e -> refreshEquipment());
		refreshButton.setBorder(new EmptyBorder(5, 10, 5, 10));
		
		JButton refreshAllButton = new JButton("🔄 Refresh All Players");
		refreshAllButton.addActionListener(e -> refreshAllPlayers());
		refreshAllButton.setBorder(new EmptyBorder(5, 10, 5, 10));
		
		this.refreshButton = refreshButton;
		
		JPanel targetButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
		targetButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		targetButtonPanel.setBorder(new EmptyBorder(10, 0, 15, 0));
		targetButtonPanel.setPreferredSize(new Dimension(300, 50));
		targetButtonPanel.setMinimumSize(new Dimension(300, 50));
		
		targetButtonPanel.add(clearTargetButton);
		targetButtonPanel.add(refreshButton);
		targetButtonPanel.add(refreshAllButton);
		
		JPanel buttonContainerPanel = new JPanel();
		buttonContainerPanel.setLayout(new BoxLayout(buttonContainerPanel, BoxLayout.Y_AXIS));
		buttonContainerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttonContainerPanel.setPreferredSize(new Dimension(300, 110));
		buttonContainerPanel.setMinimumSize(new Dimension(300, 110));
		buttonContainerPanel.add(targetButtonPanel);
		
		bottomContainer.add(buttonContainerPanel);
		
		JScrollPane mainContentScrollPane = new JScrollPane(mainContentPanel);
		mainContentScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		mainContentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		mainContentScrollPane.setBorder(null);
		mainContentScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		
		mainContentScrollPane.setPreferredSize(new Dimension(300, 750));
		mainContentScrollPane.setMinimumSize(new Dimension(300, 400));
		
		mainContentScrollPane.getViewport().setScrollMode(javax.swing.JViewport.BLIT_SCROLL_MODE);
		
		bottomContainer.add(mainContentScrollPane);

		add(header, BorderLayout.NORTH);
		add(bottomContainer, BorderLayout.CENTER);
		add(logsPanel, BorderLayout.SOUTH);

		addLog("Plugin panel initialized. Equipment lookup logs will appear here.");
	}



	public void setTarget(String playerName, Map<KitType, ItemComposition> equipment, 
		Map<KitType, Integer> prices, boolean isSkulled)
	{
		updateFromMainPlugin();
	}
	
	public void updateFromMainPlugin() {
		addLog("updateFromMainPlugin() called");
		if (mainPlugin == null) {
			addLog("Main plugin reference not set");
			return;
		}
		
		addLog("Getting target info from main plugin");
		Map<String, Object> targetInfo = mainPlugin.getTargetInfoForPanel();
		
		boolean hasTarget = (Boolean) targetInfo.get("hasTarget");
		addLog("Has target: " + hasTarget);
		
		if (!hasTarget) {
			addLog("No target, clearing display");
			currentTarget = null;
			currentEquipment.clear();
			currentPrices.clear();
			inventoryItemIds.clear();
			currentTargetSkulled = false;
			
			updateDisplay();
			
			addLog("Display cleared - no target selected. Use 'Check Risk' on a player to set a target.");
			return;
		}
		
		currentTarget = (String) targetInfo.get("playerName");
		@SuppressWarnings("unchecked")
		Map<KitType, ItemComposition> equipment = (Map<KitType, ItemComposition>) targetInfo.get("equipment");
		@SuppressWarnings("unchecked")
		Map<KitType, Integer> prices = (Map<KitType, Integer>) targetInfo.get("prices");
		currentTargetSkulled = (Boolean) targetInfo.get("isSkulled");
		
		currentEquipment = equipment != null ? equipment : new HashMap<>();
		currentPrices = prices != null ? prices : new HashMap<>();
		
		addLog("Updated target: " + currentTarget + ", equipment count: " + currentEquipment.size());
		
		@SuppressWarnings("unchecked")
		List<PlayerRiskPlugin.ItemInfo> inventoryItems = (List<PlayerRiskPlugin.ItemInfo>) targetInfo.get("inventoryItems");
		if (inventoryItems != null && !inventoryItems.isEmpty()) {
			addLog("Found " + inventoryItems.size() + " inventory items (including unequipped)");
			
			// Store inventory items separately for display purposes
			this.inventoryItemIds.clear();
			for (PlayerRiskPlugin.ItemInfo item : inventoryItems) {
				this.inventoryItemIds.add(item.getItemId());
			}
			
			addLog("Tracked " + inventoryItems.size() + " unequipped/inventory items");
			addLog("Total items now tracked: " + currentEquipment.size() + " equipped + " + inventoryItems.size() + " inventory = " + (currentEquipment.size() + inventoryItems.size()) + " total");
		}
		
		addLog("Calling updateDisplay()");
		updateDisplay();
		addLog("updateDisplay() completed");
	}

	private void updateDisplay()
	{
		if (currentTarget == null || currentTarget.isEmpty())
		{
			nameLabel.setText("No player selected");
			nameLabel.setForeground(Color.GRAY);
			clearEquipmentDisplay();
			clearStatsDisplay();
			return;
		}

		String skullStatus = currentTargetSkulled ? " [SKULLED]" : "";
		String equipmentCount = " (" + currentEquipment.size() + " equipped + " + inventoryItemIds.size() + " unequipped)";
		long totalValue = currentPrices.values().stream().mapToLong(Integer::longValue).sum();
		String totalValueText = totalValue > 0 ? " - " + QuantityFormatter.quantityToStackSize(totalValue) : "";
		
		nameLabel.setText("Player: " + currentTarget + skullStatus + equipmentCount + totalValueText);
		nameLabel.setForeground(currentTargetSkulled ? Color.RED : Color.WHITE);
		updateEquipmentDisplay();
		updateStatsDisplay();
	}

	private void updateEquipmentDisplay()
	{
		equipmentPanels.removeAll();
		
		c.gridy = 0;
		
		long totalValue = currentPrices.values().stream().mapToLong(Integer::longValue).sum();
		equipmentPanels.add(new TotalPanel(totalValue), c);
		c.gridy++;

		addLog("Updating equipment display. Equipment count: " + currentEquipment.size());
		
		if (currentEquipment.isEmpty())
		{
			JLabel noEquipmentLabel = new JLabel("No equipment visible");
			noEquipmentLabel.setForeground(Color.GRAY);
			noEquipmentLabel.setFont(FontManager.getRunescapeFont());
			noEquipmentLabel.setHorizontalAlignment(SwingConstants.CENTER);
			noEquipmentLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
			equipmentPanels.add(noEquipmentLabel, c);
			c.gridy++;
		}
		else
		{
			List<Map.Entry<KitType, ItemComposition>> sortedItems = new ArrayList<>(currentEquipment.entrySet());
			sortedItems.sort((a, b) -> {
				Integer priceA = currentPrices.get(a.getKey());
				Integer priceB = currentPrices.get(b.getKey());
				return Long.compare(priceB != null ? priceB : 0, priceA != null ? priceA : 0);
			});
			
			int itemsToKeep = currentTargetSkulled ? 0 : 3;
			if (itemsToKeep > 0)
			{
				addSectionHeader("Kept on Death", Color.GREEN);
				
				for (int i = 0; i < Math.min(itemsToKeep, sortedItems.size()); i++)
				{
					Map.Entry<KitType, ItemComposition> entry = sortedItems.get(i);
					KitType kitType = entry.getKey();
					ItemComposition item = entry.getValue();
					Integer price = currentPrices.get(kitType);
					
					if (price != null)
					{
						AsyncBufferedImage itemImage = itemManager != null ? itemManager.getImage(item.getId()) : null;
						boolean isInventoryItem = isInventoryItem(kitType, item);
						equipmentPanels.add(new ItemPanel(item, kitType, itemImage, price, false, false, isInventoryItem), c);
						c.gridy++;
					}
				}
			}
			
			int protectItemIndex = currentTargetSkulled ? 1 : 3;
			addSectionHeader("Kept if Protect Item", Color.YELLOW);
			
			if (protectItemIndex < sortedItems.size())
			{
				Map.Entry<KitType, ItemComposition> entry = sortedItems.get(protectItemIndex);
				KitType kitType = entry.getKey();
				ItemComposition item = entry.getValue();
				Integer price = currentPrices.get(kitType);
				
				if (price != null)
				{
					AsyncBufferedImage itemImage = itemManager != null ? itemManager.getImage(item.getId()) : null;
					boolean isInventoryItem = isInventoryItem(kitType, item);
					equipmentPanels.add(new ItemPanel(item, kitType, itemImage, price, false, true, isInventoryItem), c);
					c.gridy++;
				}
			}
			else
			{
				JLabel noProtectItemLabel = new JLabel("No item in this category");
				noProtectItemLabel.setForeground(Color.GRAY);
				noProtectItemLabel.setFont(FontManager.getRunescapeSmallFont());
				noProtectItemLabel.setHorizontalAlignment(SwingConstants.CENTER);
				noProtectItemLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
				equipmentPanels.add(noProtectItemLabel, c);
				c.gridy++;
			}
			
			int lostItemsStart = currentTargetSkulled ? 2 : 4;
			if (lostItemsStart < sortedItems.size())
			{
				addSectionHeader("Lost on Death", Color.RED);
				
				for (int i = lostItemsStart; i < sortedItems.size(); i++)
				{
					Map.Entry<KitType, ItemComposition> entry = sortedItems.get(i);
					KitType kitType = entry.getKey();
					ItemComposition item = entry.getValue();
					Integer price = currentPrices.get(kitType);
					
					if (price != null)
					{
						AsyncBufferedImage itemImage = itemManager != null ? itemManager.getImage(item.getId()) : null;
						boolean isInventoryItem = isInventoryItem(kitType, item);
						equipmentPanels.add(new ItemPanel(item, kitType, itemImage, price, true, false, isInventoryItem), c);
						c.gridy++;
					}
				}
			}
			
					// Add unequipped items section if we have any
		if (!inventoryItemIds.isEmpty() && mainPlugin != null)
		{
			addSectionHeader("Unequipped Items", Color.CYAN);
			
			List<PlayerRiskPlugin.ItemInfo> unequippedItems = mainPlugin.getInventoryItems(mainPlugin.getCurrentTarget());
			if (unequippedItems != null && !unequippedItems.isEmpty())
			{
				for (PlayerRiskPlugin.ItemInfo itemInfo : unequippedItems)
				{
					// Create a dummy KitType for unequipped items
					KitType dummySlot = KitType.AMULET; // Use a slot that's unlikely to conflict
					
					ItemComposition itemComp = itemManager != null ? itemManager.getItemComposition(itemInfo.getItemId()) : null;
					if (itemComp != null)
					{
						AsyncBufferedImage itemImage = itemManager != null ? itemManager.getImage(itemInfo.getItemId()) : null;
						equipmentPanels.add(new ItemPanel(itemComp, dummySlot, itemImage, itemInfo.getPrice(), false, false, true), c);
						c.gridy++;
					}
				}
				
				addLog("Displayed " + unequippedItems.size() + " unequipped items");
				
				// Show total value of unequipped items
				long totalUnequippedValue = unequippedItems.stream()
					.mapToLong(PlayerRiskPlugin.ItemInfo::getPrice)
					.sum();
				addLog("Total unequipped items value: " + QuantityFormatter.quantityToStackSize(totalUnequippedValue));
			}
		}
		}
		
		equipmentPanels.revalidate();
		equipmentPanels.repaint();
	}
	
	

	private boolean isInventoryItem(KitType kitType, ItemComposition item)
	{
		// Check if this item is in our inventory/unequipped items list
		return inventoryItemIds.contains(item.getId());
	}

	private void addSectionHeader(String title, Color color)
	{
		JLabel headerLabel = new JLabel(title);
		headerLabel.setForeground(color);
		headerLabel.setFont(FontManager.getRunescapeFont());
		headerLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
		equipmentPanels.add(headerLabel, c);
		c.gridy++;
	}

	private void updateStatsDisplay()
	{
		statsPanel.removeAll();
		
		if (currentEquipment.isEmpty())
		{
			JLabel noStatsLabel = new JLabel("No equipment data available");
			noStatsLabel.setForeground(Color.GRAY);
			noStatsLabel.setFont(FontManager.getRunescapeFont());
			noStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
			noStatsLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
			statsPanel.add(noStatsLabel);
			statsPanel.revalidate();
			statsPanel.repaint();
			return;
		}
		
		long pkProfitNoSkullNoProtect = 0;
		long pkProfitNoSkullWithProtect = 0;
		long pkProfitSkullNoProtect = 0;
		long pkProfitSkullWithProtect = 0;
		
		if (mainPlugin != null) {
			Map<String, Object> targetInfo = mainPlugin.getTargetInfoForPanel();
			@SuppressWarnings("unchecked")
			Map<String, Object> riskInfo = (Map<String, Object>) targetInfo.get("riskInfo");
			
			if (riskInfo != null) {
				pkProfitNoSkullNoProtect = (Long) riskInfo.get("pkProfitNoSkullNoProtect");
				pkProfitNoSkullWithProtect = (Long) riskInfo.get("pkProfitNoSkullWithProtect");
				pkProfitSkullNoProtect = (Long) riskInfo.get("pkProfitSkullNoProtect");
				pkProfitSkullWithProtect = (Long) riskInfo.get("pkProfitSkullWithProtect");
			}
		}

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;

		statsPanel.add(createStatRow("No Skull + Item Protect:", pkProfitNoSkullWithProtect, Color.GREEN), c);
		c.gridy++;
		statsPanel.add(createStatRow("No Skull:", pkProfitNoSkullNoProtect, Color.YELLOW), c);
		c.gridy++;
		statsPanel.add(createStatRow("Skull + Item Protect:", pkProfitSkullWithProtect, Color.ORANGE), c);
		c.gridy++;
		statsPanel.add(createStatRow("Skull:", pkProfitSkullNoProtect, Color.RED), c);
		c.gridy++;
		
		// Add unequipped items summary
		if (!inventoryItemIds.isEmpty()) {
			long totalUnequippedValue = 0;
			if (mainPlugin != null) {
				List<PlayerRiskPlugin.ItemInfo> unequippedItems = mainPlugin.getInventoryItems(mainPlugin.getCurrentTarget());
				if (unequippedItems != null) {
					totalUnequippedValue = unequippedItems.stream()
						.mapToLong(PlayerRiskPlugin.ItemInfo::getPrice)
						.sum();
				}
			}
			
			statsPanel.add(createStatRow("Unequipped Items (" + inventoryItemIds.size() + "):", totalUnequippedValue, Color.CYAN), c);
			c.gridy++;
			
			// Add note about unequipped items being included in risk calculation
			JLabel unequippedNote = new JLabel("Note: Unequipped items are included in risk calculations");
			unequippedNote.setForeground(Color.CYAN);
			unequippedNote.setFont(FontManager.getRunescapeSmallFont());
			unequippedNote.setHorizontalAlignment(SwingConstants.CENTER);
			unequippedNote.setBorder(new EmptyBorder(5, 0, 5, 0));
			statsPanel.add(unequippedNote, c);
			c.gridy++;
		}

		statsPanel.revalidate();
		statsPanel.repaint();
	}

	private JPanel createStatRow(String label, long value, Color color)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 3, 0));

		JLabel labelComponent = new JLabel(label);
		labelComponent.setForeground(Color.WHITE);
		labelComponent.setFont(FontManager.getRunescapeFont());

		JLabel valueComponent = new JLabel(QuantityFormatter.quantityToStackSize(value));
		valueComponent.setForeground(color);
		valueComponent.setFont(FontManager.getRunescapeFont());
		valueComponent.setToolTipText(NumberFormat.getNumberInstance(Locale.US).format(value));

		row.add(labelComponent, BorderLayout.WEST);
		row.add(valueComponent, BorderLayout.EAST);

		return row;
	}



	private void clearEquipmentDisplay()
	{
		equipmentPanels.removeAll();
		equipmentPanels.revalidate();
		equipmentPanels.repaint();
	}

	private void clearStatsDisplay()
	{
		statsPanel.removeAll();
		statsPanel.revalidate();
		statsPanel.repaint();
	}
	
	public void clearTarget()
	{
		addLog("Clear Target button clicked");
		if (mainPlugin != null) {
			addLog("Main plugin reference found, calling clearTarget");
			mainPlugin.clearTarget();
		} else {
			addLog("ERROR: Main plugin reference not set - cannot clear target");
		}
	}

	public void refreshEquipment()
	{
		addLog("Refresh Equipment button clicked");
		if (mainPlugin != null) {
			addLog("Main plugin reference found, calling refreshTargetEquipment");
			mainPlugin.refreshTargetEquipment();
		} else {
			addLog("ERROR: Main plugin reference not set - cannot refresh equipment");
		}
	}

	public void refreshAllPlayers()
	{
		addLog("Refresh All Players button clicked");
		if (mainPlugin != null) {
			addLog("Main plugin reference found, calling refreshAllPlayers");
			mainPlugin.refreshAllPlayers();
		} else {
			addLog("ERROR: Main plugin reference not set - cannot refresh all players' equipment");
		}
	}
	

	
	public void logAdditionalItemsState()
	{
		addLog("=== Additional Items Debug Info ===");
		addLog("Total additional items: " + 0);
		
		addLog("No additional items found");
		
		if (currentTarget != null)
		{
			addLog("Current target: " + currentTarget);
			addLog("Current equipment count: " + currentEquipment.size());
			addLog("Current prices count: " + currentPrices.size());
		}
		else
		{
			addLog("No current target");
		}
		addLog("=== End Debug Info ===");
	}
	
	public void updateEquipmentData(Map<KitType, ItemComposition> equipment, Map<KitType, Integer> prices)
	{
		if (equipment != null)
		{
			currentEquipment = equipment;
		}
		if (prices != null)
		{
			currentPrices = prices;
		}
		
		inventoryItemIds.clear();
		
		updateDisplay();
	}



	private void toggleLogs()
	{
		logsExpanded = !logsExpanded;
		scrollPane.setVisible(logsExpanded);
		
		if (logsExpanded)
		{
			toggleLogsButton.setText("▲ Hide Logs");
		}
		else
		{
			toggleLogsButton.setText("▼ Show Logs");
		}
		
		revalidate();
		repaint();
	}

	public void addLog(String message)
	{
		String timestamp = java.time.LocalTime.now().toString();
		String logEntry = String.format("[%s] %s", timestamp, message);
		
		logQueue.offer(logEntry);
		logCounter.incrementAndGet();

		while (logQueue.size() > MAX_LOGS)
		{
			logQueue.poll();
		}

		SwingUtilities.invokeLater(this::updateLogDisplay);
	}

	private void updateLogDisplay()
	{
		StringBuilder sb = new StringBuilder();
		for (String log : logQueue)
		{
			sb.append(log).append("\n");
		}
		logArea.setText(sb.toString());
		
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	private void clearLogs()
	{
		logQueue.clear();
		logCounter.set(0);
		logArea.setText("");
		addLog("Logs cleared.");
	}

	private void exportLogs()
	{
		try
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Wilderness Player Risk Plugin Logs\n");
			sb.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n");
			sb.append("Total log entries: ").append(logCounter.get()).append("\n\n");
			
			for (String log : logQueue)
			{
				sb.append(log).append("\n");
			}

			Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.setContents(
					new java.awt.datatransfer.StringSelection(sb.toString()),
					null
				);

			addLog("Logs exported to clipboard.");
		}
		catch (Exception e)
		{
			addLog("Failed to export logs: " + e.getMessage());
		}
	}

	public void addEquipmentLog(String playerName, int itemId, int price, String slot)
	{
		String message = String.format("Player: %s | Item: %d | Price: %d | Slot: %s", 
			playerName, itemId, price, slot);
		addLog(message);
	}

	public void addPlayerLog(String playerName, int itemCount, int totalValue, boolean isTop3, String additionalInfo)
	{
		String valueType = isTop3 ? "Value Excluding Top Items (Lost on Death)" : "Total Equipment Value";
		String message = String.format("Player: %s | Items: %d | %s: %d%s", 
			playerName, itemCount, valueType, totalValue, additionalInfo);
		addLog(message);
	}

	public void addPlayerLogWithSkull(String playerName, int itemCount, int totalValue, boolean isTop3, boolean isSkulled, String additionalInfo)
	{
		String valueType = isTop3 ? "Value Excluding Top Items (Lost on Death)" : "Total Equipment Value";
		String skullText = isSkulled ? " [SKULLED]" : "";
		String message = String.format("Player: %s%s | Items: %d | %s: %d%s", 
			playerName, skullText, itemCount, valueType, totalValue, additionalInfo);
		addLog(message);
	}

	public void addErrorLog(String error)
	{
		addLog("ERROR: " + error);
	}

	public void addThresholdInfo(int mediumThreshold, int highThreshold)
	{
		addLog("Color thresholds - Medium: " + mediumThreshold + " GP, High: " + highThreshold + " GP");
	}
	
	public void addEquipmentChangeLog(String changeType, String itemName, String slot)
	{
		String message = String.format("EQUIPMENT CHANGE: %s - %s in %s", changeType, itemName, slot);
		addLog(message);
	}

	public void setItemManager(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		if (currentTarget != null && !currentEquipment.isEmpty())
		{
			updateEquipmentDisplay();
		}
	}

	public void setMainPlugin(PlayerRiskPlugin plugin)
	{
		this.mainPlugin = plugin;
		addLog("Main plugin reference set: " + (plugin != null ? "SUCCESS" : "FAILED"));
	}
	
	public void checkPanelStatus() {
		addLog("=== Panel Status Check ===");
		addLog("Main plugin reference: " + (mainPlugin != null ? "SET" : "NULL"));
		addLog("Current target: " + (currentTarget != null ? currentTarget : "null"));
		addLog("Equipment count: " + currentEquipment.size());
		addLog("Prices count: " + currentPrices.size());
		addLog("Inventory item IDs count: " + inventoryItemIds.size());
		addLog("Current target skulled: " + currentTargetSkulled);
		addLog("=== End Status Check ===");
	}

	private class ItemPanel extends JPanel
	{
		private final boolean isInventory;
		
		public ItemPanel(ItemComposition item, KitType kitType, AsyncBufferedImage itemImage, Integer itemPrice, boolean wouldBeLost, boolean isProtectItem, boolean isInventory)
		{
			this.isInventory = isInventory;
			setBorder(new EmptyBorder(5, 5, 5, 5));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);

			if (wouldBeLost)
			{
				setBorder(new CompoundBorder(
					new MatteBorder(2, 2, 2, 2, Color.RED),
					new EmptyBorder(3, 3, 3, 3)
				));
			}
			else if (isProtectItem)
			{
				setBorder(new CompoundBorder(
					new MatteBorder(2, 2, 2, 2, Color.YELLOW),
					new EmptyBorder(3, 3, 3, 3)
				));
			}

			setLayout(new BorderLayout(8, 0));

			JLabel imageLabel;
			if (itemImage != null)
			{
				imageLabel = new JLabel();
				itemImage.addTo(imageLabel);
				imageLabel.setPreferredSize(new Dimension(30, 30));
				imageLabel.setMinimumSize(new Dimension(30, 30));
				imageLabel.setMaximumSize(new Dimension(30, 30));
			}
			else
			{
				imageLabel = createPlaceholderLabel();
			}
			
			imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

			JPanel infoPanel = new JPanel();
			infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
			infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			JLabel nameLabel = new JLabel(item.getName());
			nameLabel.setFont(FontManager.getRunescapeFont());
			nameLabel.setForeground(Color.WHITE);

			String slotText = isInventory ? "Inventory" : 
				String.valueOf(kitType).toLowerCase().replace("_", " ");
			JLabel slotLabel = new JLabel(slotText);
			slotLabel.setFont(FontManager.getRunescapeSmallFont());
			slotLabel.setForeground(Color.GRAY);

			JLabel priceLabel = getPriceLabel(itemPrice);
			if (wouldBeLost)
			{
				priceLabel.setForeground(Color.RED);
			}
			else if (isProtectItem)
			{
				priceLabel.setForeground(Color.YELLOW);
			}

			infoPanel.add(nameLabel);
			infoPanel.add(slotLabel);
			infoPanel.add(priceLabel);

			add(imageLabel, BorderLayout.WEST);
			add(infoPanel, BorderLayout.CENTER);
			
			String statusText;
			if (isInventory)
			{
				statusText = "Unequipped item (preserved in memory)";
			}
			else if (isProtectItem)
			{
				statusText = "Protected by Protect Item prayer";
			}
			else if (wouldBeLost)
			{
				statusText = "Lost on death";
			}
			else
			{
				statusText = "Kept on death";
			}
			
			String tooltip = String.format("<html><b>%s</b><br>Slot: %s<br>Price: %s<br>Status: %s</html>",
				item.getName(),
				slotText,
				NumberFormat.getNumberInstance(Locale.US).format(itemPrice),
				statusText);
			setToolTipText(tooltip);
			
			setupWikiContextMenu(item.getName());
		}
		
		private void setupWikiContextMenu(String itemName)
		{
			JPopupMenu popupMenu = new JPopupMenu();
			
			JMenuItem wikiItem = new JMenuItem("Wiki");
			wikiItem.addActionListener(e -> openOSRSWiki(itemName));
			popupMenu.add(wikiItem);
			
			// Add context menu for unequipped items
			if (isInventory) {
				JMenuItem unequippedInfoItem = new JMenuItem("Mark as Unequipped");
				unequippedInfoItem.addActionListener(e -> {
					if (mainPlugin != null && mainPlugin.getCurrentTarget() != null) {
						// Log to the panel instead
						addLog("Item marked as unequipped: " + itemName);
					}
				});
				popupMenu.add(unequippedInfoItem);
			}
			
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent e) {
					if (e.isPopupTrigger()) {
						popupMenu.show(ItemPanel.this, e.getX(), e.getY());
					}
				}
				
				@Override
				public void mouseReleased(java.awt.event.MouseEvent e) {
					if (e.isPopupTrigger()) {
						popupMenu.show(ItemPanel.this, e.getX(), e.getY());
					}
				}
			});
		}
		
		private void openOSRSWiki(String itemName)
		{
			try {
				String formattedName = itemName.replace(" ", "_")
					.replace("'", "")
					.replace("-", "")
					.replace(",", "")
					.replace(".", "");
				
				String wikiUrl = "https://oldschool.runescape.wiki/w/" + formattedName;
				
				java.awt.Desktop.getDesktop().browse(new java.net.URI(wikiUrl));
				
				addLog("Opened OSRS Wiki for: " + itemName);
			} catch (Exception ex) {
				addLog("Failed to open OSRS Wiki for: " + itemName + " - " + ex.getMessage());
			}
		}
		


		private JLabel createPlaceholderLabel()
		{
			JLabel placeholder = new JLabel("📦");
			placeholder.setFont(new Font("Arial", Font.PLAIN, 20));
			placeholder.setForeground(Color.WHITE);
			placeholder.setPreferredSize(new Dimension(30, 30));
			placeholder.setMinimumSize(new Dimension(30, 30));
			placeholder.setMaximumSize(new Dimension(30, 30));
			return placeholder;
		}

		private JLabel getPriceLabel(long amount)
		{
			String itemPriceString = QuantityFormatter.quantityToStackSize(amount);
			JLabel price = new JLabel(itemPriceString);
			
			if (amount > 10000000)
			{
				price.setForeground(Color.GREEN);
			}
			else if (amount > 100000)
			{
				price.setForeground(Color.WHITE);
			}
			else if (amount == 0)
			{
				price.setForeground(Color.LIGHT_GRAY);
			}
			else
			{
				price.setForeground(Color.YELLOW);
			}
			
			price.setFont(FontManager.getRunescapeFont());
			price.setToolTipText(NumberFormat.getNumberInstance(Locale.US).format(amount));
			return price;
		}
	}

	private class TotalPanel extends JPanel
	{
		public TotalPanel(long totalValue)
		{
			setBorder(new EmptyBorder(10, 0, 10, 0));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

			JLabel totalValueLabel = new JLabel("Total Equipment Value: " + QuantityFormatter.quantityToStackSize(totalValue));
			totalValueLabel.setForeground(Color.WHITE);
			totalValueLabel.setFont(FontManager.getRunescapeFont());
			totalValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
			totalValueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

			JPanel riskRangePanel = new JPanel();
			riskRangePanel.setLayout(new BoxLayout(riskRangePanel, BoxLayout.Y_AXIS));
			riskRangePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
			riskRangePanel.setBorder(new EmptyBorder(10, 0, 0, 0));
			riskRangePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

			JLabel riskRangeHeader = new JLabel("Risk Range");
			riskRangeHeader.setForeground(Color.ORANGE);
			riskRangeHeader.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 20f));
			riskRangeHeader.setHorizontalAlignment(SwingConstants.CENTER);
			riskRangeHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

			long riskWithProtectItem = 0;
			long riskWithoutProtectItem = 0;
			
			if (mainPlugin != null) {
				Map<String, Object> targetInfo = mainPlugin.getTargetInfoForPanel();
				@SuppressWarnings("unchecked")
				Map<String, Object> riskInfo = (Map<String, Object>) targetInfo.get("riskInfo");
				
				if (riskInfo != null) {
					riskWithProtectItem = (Long) riskInfo.get("pkProfitNoSkullWithProtect");
					riskWithoutProtectItem = (Long) riskInfo.get("pkProfitNoSkullNoProtect");
				}
			}

			String riskRangeText = QuantityFormatter.quantityToStackSize(riskWithProtectItem) + " - " + 
				QuantityFormatter.quantityToStackSize(riskWithoutProtectItem);
			
			JLabel riskRangeLabel = new JLabel(riskRangeText);
			riskRangeLabel.setForeground(Color.WHITE);
			riskRangeLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD, 20f));
			riskRangeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			riskRangeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

			riskRangePanel.add(riskRangeHeader);
			riskRangePanel.add(riskRangeLabel);

			add(totalValueLabel);
			add(riskRangePanel);
		}
	}


}
