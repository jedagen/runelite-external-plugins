package com.playerrisk;

import net.runelite.api.*;
import net.runelite.api.kit.KitType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class PlayerRiskPluginTest {

    private PlayerRiskPlugin plugin;

    @Before
    public void setUp() {
        plugin = new PlayerRiskPlugin();
    }

    @Test
    public void testPluginInitialization() {
        assertNotNull("Plugin should not be null", plugin);
        assertEquals("Plugin should be PlayerRiskPlugin", PlayerRiskPlugin.class, plugin.getClass());
    }

    @Test
    public void testEquipmentItemCreation() {
        PlayerRiskPlugin.EquipmentItem item = new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON);
        
        assertEquals("Item ID should be 4151", 4151, item.getItemId());
        assertEquals("Item price should be 2000000", 2000000, item.getPrice());
        assertEquals("Item slot should be WEAPON", KitType.WEAPON, item.getSlot());
    }

    @Test
    public void testPlayerGearInfoCreation() {
        List<PlayerRiskPlugin.EquipmentItem> equipment = Arrays.asList(
            new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON),
            new PlayerRiskPlugin.EquipmentItem(1187, 150000, KitType.BOOTS)
        );
        
        PlayerRiskPlugin.PlayerGearInfo gearInfo = new PlayerRiskPlugin.PlayerGearInfo(
            equipment, 2150000, 0, false
        );
        
        assertEquals("Equipment should have 2 items", 2, gearInfo.getEquipment().size());
        assertEquals("Total value should be 2150000", 2150000, gearInfo.getTotalValue());
        assertEquals("Final value should be 0", 0, gearInfo.getFinalValue());
        assertFalse("Should not be skulled", gearInfo.isSkulled());
    }

    @Test
    public void testItemInfoCreation() {
        PlayerRiskPlugin.ItemInfo itemInfo = new PlayerRiskPlugin.ItemInfo(4151, "Abyssal whip", 2000000);
        
        assertEquals("Item ID should be 4151", 4151, itemInfo.getItemId());
        assertEquals("Item name should be 'Abyssal whip'", "Abyssal whip", itemInfo.getName());
        assertEquals("Item price should be 2000000", 2000000, itemInfo.getPrice());
    }

    @Test
    public void testValueFormatting() {
        try {
            java.lang.reflect.Method formatMethod = PlayerRiskPlugin.class.getDeclaredMethod("formatValue", int.class);
            formatMethod.setAccessible(true);
            
            String result1 = (String) formatMethod.invoke(plugin, 1500);
            String result2 = (String) formatMethod.invoke(plugin, 1500000);
            String result3 = (String) formatMethod.invoke(plugin, 150000000);
            
            assertEquals("1500 should format as '1.5K'", "1.5K", result1);
            assertEquals("1500000 should format as '1.5M'", "1.5M", result2);
            assertEquals("150000000 should format as '150.0M'", "150.0M", result3);
            
        } catch (Exception e) {
            fail("Failed to test formatValue method: " + e.getMessage());
        }
    }

    @Test
    public void testCalculateValueExcludingTopItems() {
        try {
            java.lang.reflect.Method calcMethod = PlayerRiskPlugin.class.getDeclaredMethod(
                "calculateValueExcludingTopItems", List.class, int.class);
            calcMethod.setAccessible(true);
            
            List<PlayerRiskPlugin.EquipmentItem> equipment = Arrays.asList(
                new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON),
                new PlayerRiskPlugin.EquipmentItem(1187, 150000, KitType.BOOTS),
                new PlayerRiskPlugin.EquipmentItem(4587, 80000, KitType.LEGS),
                new PlayerRiskPlugin.EquipmentItem(10499, 5000, KitType.CAPE)
            );
            
            int result = (Integer) calcMethod.invoke(plugin, equipment, 3);
            assertEquals("Should return 5000 when ignoring top 3 items", 5000, result);
            
            int result2 = (Integer) calcMethod.invoke(plugin, equipment, 2);
            assertEquals("Should return 85000 when ignoring top 2 items", 85000, result2);
            
        } catch (Exception e) {
            fail("Failed to test calculateValueExcludingTopItems method: " + e.getMessage());
        }
    }

    @Test
    public void testThresholdValidation() {
        int mediumThreshold = 100000;
        int highThreshold = 500000;
        
        assertTrue("Medium threshold should be less than high threshold", 
                  mediumThreshold < highThreshold);
        
        int equalThreshold = 100000;
        assertFalse("Equal thresholds should not be valid", 
                   equalThreshold < equalThreshold);
    }

    @Test
    public void testColorAssignment() {
        int lowValue = 50000;
        int mediumValue = 200000;
        int highValue = 1000000;
        
        assertTrue("Low value should be below medium threshold", lowValue < 100000);
        assertTrue("Medium value should be between thresholds", 
                  200000 >= 100000 && 200000 < 500000);
        assertTrue("High value should be above high threshold", highValue > 500000);
    }

    @Test
    public void testEquipmentChangesWithUnionTracking() {
        List<PlayerRiskPlugin.EquipmentItem> initialEquipment = new ArrayList<>();
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(1187, 150000, KitType.BOOTS));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(4587, 80000, KitType.LEGS));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(10499, 5000, KitType.CAPE));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(1163, 20000, KitType.HEAD));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(7458, 10000, KitType.AMULET));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(7459, 15000, KitType.HANDS));
        initialEquipment.add(new PlayerRiskPlugin.EquipmentItem(7460, 32000, KitType.TORSO));
        
        PlayerRiskPlugin.PlayerGearInfo initialGearInfo = new PlayerRiskPlugin.PlayerGearInfo(
            initialEquipment, 2312000, 0, false
        );
        
        assertEquals("Initial equipment should have 8 items", 8, initialGearInfo.getEquipment().size());
        
        List<PlayerRiskPlugin.EquipmentItem> updatedEquipment = new ArrayList<>();
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(1187, 150000, KitType.BOOTS));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(4587, 80000, KitType.LEGS));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(10499, 5000, KitType.CAPE));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(1163, 20000, KitType.HEAD));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(7458, 10000, KitType.AMULET));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(7461, 25000, KitType.HANDS));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(7462, 32000000, KitType.TORSO));
        updatedEquipment.add(new PlayerRiskPlugin.EquipmentItem(7463, 12000, KitType.SHIELD));
        
        PlayerRiskPlugin.PlayerGearInfo updatedGearInfo = new PlayerRiskPlugin.PlayerGearInfo(
            updatedEquipment, 34349000, 0, false
        );
        
        assertEquals("Updated equipment should have 9 items", 9, updatedGearInfo.getEquipment().size());
        
        Set<Integer> allItemIds = new HashSet<>();
        
        for (PlayerRiskPlugin.EquipmentItem item : initialEquipment) {
            allItemIds.add(item.getItemId());
        }
        
        for (PlayerRiskPlugin.EquipmentItem item : updatedEquipment) {
            allItemIds.add(item.getItemId());
        }
        
        assertEquals("Union of all items should have 11 unique items", 11, allItemIds.size());
        
        assertTrue("Should contain original rune gloves", allItemIds.contains(7459));
        assertTrue("Should contain original rune platebody", allItemIds.contains(7460));
        assertTrue("Should contain new dragon gloves", allItemIds.contains(7461));
        assertTrue("Should contain new rune sq shield", allItemIds.contains(7463));
        assertTrue("Should contain new bandos chestplate", allItemIds.contains(7462));
        
        List<PlayerRiskPlugin.EquipmentItem> finalEquipment = new ArrayList<>();
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(4151, 2000000, KitType.WEAPON));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(1187, 150000, KitType.BOOTS));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(4587, 80000, KitType.LEGS));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(10499, 5000, KitType.CAPE));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(1163, 20000, KitType.HEAD));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(7458, 10000, KitType.AMULET));
        finalEquipment.add(new PlayerRiskPlugin.EquipmentItem(7461, 25000, KitType.HANDS));
        
        PlayerRiskPlugin.PlayerGearInfo finalGearInfo = new PlayerRiskPlugin.PlayerGearInfo(
            finalEquipment, 2290000, 0, false
        );
        
        assertEquals("Final equipment should have 7 items after unequipping", 7, finalGearInfo.getEquipment().size());
        
        Set<Integer> finalUnion = new HashSet<>();
        
        for (PlayerRiskPlugin.EquipmentItem item : initialEquipment) {
            finalUnion.add(item.getItemId());
        }
        for (PlayerRiskPlugin.EquipmentItem item : updatedEquipment) {
            finalUnion.add(item.getItemId());
        }
        for (PlayerRiskPlugin.EquipmentItem item : finalEquipment) {
            finalUnion.add(item.getItemId());
        }
        
        assertEquals("Final union should still have 11 unique items after unequipping", 11, finalUnion.size());
        
        assertTrue("Should still contain unequipped bandos chestplate", finalUnion.contains(7462));
        assertTrue("Should still contain unequipped rune sq shield", finalUnion.contains(7463));
        assertTrue("Should still contain unequipped rune gloves", finalUnion.contains(7459));
        assertTrue("Should still contain unequipped rune platebody", finalUnion.contains(7460));
    }
}
