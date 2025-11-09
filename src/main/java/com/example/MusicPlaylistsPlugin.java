package com.example;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Slf4j
@PluginDescriptor(
	name = "Music Playlists"
)
public class MusicPlaylistsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MusicPlaylistsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	private MusicPlaylistsPanel panel;
	private NavigationButton navButton;

	private boolean isPlaying = false;
	private boolean isLooping = false;
	private boolean isShuffling = false;
	private int currentTrackIndex = -1;
	private List<Integer> currentPlayOrder = new ArrayList<>();
	private int currentlyPlayingTrackId = -1;
	
	private java.util.Map<String, SongWidgetInfo> songWidgetMap = new java.util.HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		panel = new MusicPlaylistsPanel(this, config);
		
		BufferedImage icon = null;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Panel icon not found, using default icon");
		}
		
		NavigationButton.NavigationButtonBuilder builder = NavigationButton.builder()
			.tooltip("Music Playlists")
			.panel(panel);
		
		if (icon != null)
		{
			builder.icon(icon);
		}
		
		navButton = builder.build();
		clientToolbar.addNavigation(navButton);
		loadSongsFromFile();
		log.info("Music Playlists plugin started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopPlayback();
		clientToolbar.removeNavigation(navButton);
		log.info("Music Playlists plugin stopped!");
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (event.getType() == MenuAction.CC_OP.getId() && event.getOption().equals("Play"))
		{
			final MenuEntry entry = event.getMenuEntry();
			String target = entry.getTarget();
			
			if (target != null && target.contains("<col="))
			{
				String songName = target.replaceAll("<[^>]+>", "");
				if (!songWidgetMap.containsKey(songName))
				{
					SongWidgetInfo widgetInfo = new SongWidgetInfo(
						entry.getIdentifier(),
						entry.getParam0(),
						entry.getParam1(),
						target
					);
					songWidgetMap.put(songName, widgetInfo);
					saveSongToFile(songName, widgetInfo);
				}
				
				client.createMenuEntry(-1)
					.setOption("Add to Playlist")
					.setTarget(target)
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {
						addSongToActivePlaylist(songName);
					});
			}
		}
	}
	
	private static class SongWidgetInfo
	{
		final int identifier;
		final int param0;
		final int param1;
		final String target;
		
		SongWidgetInfo(int identifier, int param0, int param1, String target)
		{
			this.identifier = identifier;
			this.param0 = param0;
			this.param1 = param1;
			this.target = target;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
	}

	public void addSongToActivePlaylist(String songName)
	{
		String activePlaylist = config.activePlaylist();
		if (activePlaylist == null || activePlaylist.isEmpty())
		{
			panel.showMessage("No playlist selected. Please create or select a playlist first.");
			return;
		}

		String songs = getPlaylistSongs(activePlaylist);
		if (songs == null || songs.isEmpty())
		{
			setPlaylistSongs(activePlaylist, songName);
		}
		else
		{
			setPlaylistSongs(activePlaylist, songs + "|" + songName);
		}

		panel.refreshPlaylist();
	}

	public void playPlaylist()
	{
		String activePlaylist = config.activePlaylist();
		if (activePlaylist == null || activePlaylist.isEmpty())
		{
			panel.showMessage("No playlist selected.");
			return;
		}

		String songs = getPlaylistSongs(activePlaylist);
		if (songs == null || songs.isEmpty())
		{
			panel.showMessage("Playlist is empty.");
			return;
		}

		stopPlayback();

		String[] songArray = songs.split("\\|");
		currentPlayOrder.clear();
		for (int i = 0; i < songArray.length; i++)
		{
			currentPlayOrder.add(i);
		}

		if (isShuffling)
		{
			Collections.shuffle(currentPlayOrder, new Random());
		}

		currentTrackIndex = 0;
		isPlaying = true;
		clientThread.invokeLater(() -> {
			playCurrentTrack();
			panel.updatePlayerState();
		});
	}

	public void stopPlayback()
	{
		isPlaying = false;
		currentTrackIndex = -1;
		currentPlayOrder.clear();
		currentlyPlayingTrackId = -1;
		if (panel != null)
		{
			panel.updatePlayerState();
		}
	}

	public void skipTrack()
	{
		if (!isPlaying || currentPlayOrder.isEmpty())
		{
			return;
		}

		playNextTrack();
	}

	private void playCurrentTrack()
	{
		if (currentTrackIndex < 0 || currentTrackIndex >= currentPlayOrder.size())
		{
			stopPlayback();
			return;
		}

		String activePlaylist = config.activePlaylist();
		if (activePlaylist == null || activePlaylist.isEmpty())
		{
			stopPlayback();
			return;
		}

		String songs = getPlaylistSongs(activePlaylist);
		if (songs == null || songs.isEmpty())
		{
			stopPlayback();
			return;
		}

		String[] songArray = songs.split("\\|");
		int actualIndex = currentPlayOrder.get(currentTrackIndex);
		if (actualIndex >= songArray.length)
		{
			stopPlayback();
			return;
		}

		String songName = songArray[actualIndex].trim();
		playSongByName(songName);
		panel.updateNowPlaying(songName);
	}

	private void playNextTrack()
	{
		if (!isPlaying || currentPlayOrder.isEmpty())
		{
			stopPlayback();
			return;
		}

		currentTrackIndex++;
		if (currentTrackIndex >= currentPlayOrder.size())
		{
			if (isLooping)
			{
				currentTrackIndex = 0;
				playCurrentTrack();
			}
			else
			{
				stopPlayback();
				panel.updateNowPlaying("");
			}
			return;
		}

		playCurrentTrack();
	}

	private void playSongByName(String songName)
	{
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}

			Widget musicTab = client.getWidget(239, 0);
			if (musicTab == null)
			{
				log.warn("Music tab not found. Please open the music tab manually (F4 or click the music icon).");
				panel.showMessage("Please open the music tab to play songs. You can press F4 or click the music icon.");
				return;
			}

			String searchKey = songName.trim();
			log.debug("Searching for song: '" + searchKey + "' in map with " + songWidgetMap.size() + " songs");
			
			SongWidgetInfo widgetInfo = null;
			widgetInfo = songWidgetMap.get(searchKey);
			
			if (widgetInfo == null)
			{
				for (java.util.Map.Entry<String, SongWidgetInfo> entry : songWidgetMap.entrySet())
				{
					String mapKey = entry.getKey();
					if (mapKey.equalsIgnoreCase(searchKey) || 
					    mapKey.replaceAll("\\s+", " ").trim().equalsIgnoreCase(searchKey.replaceAll("\\s+", " ").trim()))
					{
						widgetInfo = entry.getValue();
						log.info("Found song match: '" + mapKey + "' for '" + searchKey + "'");
						break;
					}
				}
			}
			
			if (widgetInfo != null)
			{
				log.debug("Trying to invoke menu action for song: " + songName + " with params: param0=" + widgetInfo.param0 + ", param1=" + widgetInfo.param1 + ", identifier=" + widgetInfo.identifier);
				boolean success = false;
				
				Widget targetWidget = client.getWidget(widgetInfo.param1, 0);
				if (targetWidget != null)
				{
					log.debug("Found widget " + widgetInfo.param1 + " for song: " + songName);
					String[] actions = targetWidget.getActions();
					if (actions != null && widgetInfo.param0 < actions.length && actions[widgetInfo.param0] != null && actions[widgetInfo.param0].equals("Play"))
					{
						try
						{
							Method[] widgetMethods = targetWidget.getClass().getMethods();
							for (Method m : widgetMethods)
							{
								if (m.getName().equals("doAction") || m.getName().equals("invokeAction"))
								{
									try
									{
										Class<?>[] paramTypes = m.getParameterTypes();
										if (paramTypes.length == 1 && paramTypes[0] == int.class)
										{
											m.invoke(targetWidget, widgetInfo.param0);
											log.info("Playing song: " + songName + " using widget.doAction(" + widgetInfo.param0 + ")");
											success = true;
											break;
										}
									}
									catch (Exception e)
									{
										log.debug("Could not invoke widget action method: " + m.getName(), e);
									}
								}
							}
						}
						catch (Exception e)
						{
							log.debug("Error trying widget action methods", e);
						}
					}
				}
				
				if (!success)
				{
					Method[] methods = client.getClass().getMethods();
					List<String> invokeMethods = new ArrayList<>();
					for (Method m : methods)
					{
						if (m.getName().contains("Menu") || m.getName().contains("Action") || m.getName().contains("invoke"))
						{
							invokeMethods.add(m.toString());
						}
					}
					log.debug("Available menu/action methods on client: " + invokeMethods);
					
					try
					{
						Method menuActionMethod = client.getClass().getMethod("menuAction", int.class, int.class, MenuAction.class, int.class, int.class, String.class, String.class);
						menuActionMethod.invoke(client, widgetInfo.param0, widgetInfo.param1, MenuAction.CC_OP, widgetInfo.identifier, -1, "Play", widgetInfo.target);
						log.info("Playing song: " + songName + " using menuAction method");
						success = true;
					}
					catch (NoSuchMethodException e1)
					{
						log.debug("menuAction method not found, trying alternatives");
						try
						{
							Method invokeMethod = client.getClass().getMethod("invokeMenuAction", int.class, int.class, int.class, int.class, int.class);
							invokeMethod.invoke(client, widgetInfo.param0, widgetInfo.param1, MenuAction.CC_OP.getId(), widgetInfo.identifier, -1);
							log.info("Playing song: " + songName + " using stored menu entry (method 1)");
							success = true;
						}
						catch (NoSuchMethodException e2)
						{
							log.debug("Method 1 (int params) not found, trying alternatives");
							try
							{
								Method invokeMethod = client.getClass().getMethod("invokeMenuAction", String.class, String.class, int.class, int.class, int.class, int.class);
								invokeMethod.invoke(client, "Play", widgetInfo.target, widgetInfo.param1, MenuAction.CC_OP.getId(), widgetInfo.param0, widgetInfo.identifier);
								log.info("Playing song: " + songName + " using stored menu entry (method 2)");
								success = true;
							}
							catch (NoSuchMethodException e3)
							{
								log.debug("Method 2 (String params) not found, trying dynamic search");
								for (Method m : methods)
								{
									if (m.getName().equals("invokeMenuAction"))
									{
										try
										{
											Class<?>[] paramTypes = m.getParameterTypes();
											log.debug("Found invokeMenuAction with " + paramTypes.length + " parameters: " + java.util.Arrays.toString(paramTypes));
											if (paramTypes.length == 5 && paramTypes[0] == int.class)
											{
												m.invoke(client, widgetInfo.param0, widgetInfo.param1, MenuAction.CC_OP.getId(), widgetInfo.identifier, -1);
												log.info("Playing song: " + songName + " using invokeMenuAction (found method)");
												success = true;
												break;
											}
											else if (paramTypes.length == 6 && paramTypes[0] == String.class)
											{
												m.invoke(client, "Play", widgetInfo.target, widgetInfo.param1, MenuAction.CC_OP.getId(), widgetInfo.param0, widgetInfo.identifier);
												log.info("Playing song: " + songName + " using invokeMenuAction String version");
												success = true;
												break;
											}
										}
										catch (Exception e4)
										{
											log.debug("Could not invoke found method", e4);
										}
									}
								}
							}
							catch (Exception e3)
							{
								log.debug("Method 2 failed", e3);
							}
						}
						catch (Exception e2)
						{
							log.debug("Method 1 failed", e2);
						}
					}
					catch (Exception e1)
					{
						log.warn("Error calling menuAction method", e1);
					}
				}
				
				if (success)
				{
					return;
				}
				else
				{
					log.warn("Could not invoke using stored menu entry, trying widget search. Widget info: param0=" + widgetInfo.param0 + ", param1=" + widgetInfo.param1);
				}
			}

			log.debug("Menu entry not found, searching widget tree for: '" + searchKey + "'");
			Widget targetWidget = findMusicTrackWidget(musicTab, songName);
			if (targetWidget != null)
			{
				int widgetId = targetWidget.getId();
				String[] actions = targetWidget.getActions();
				int actionIndex = -1;
				if (actions != null)
				{
					for (int i = 0; i < actions.length; i++)
					{
						if (actions[i] != null && actions[i].equals("Play"))
						{
							actionIndex = i;
							break;
						}
					}
				}

				if (actionIndex >= 0)
				{
					try
					{
						String widgetText = targetWidget.getText();
						String target = widgetText != null ? widgetText : "<col=ff9040>" + songName + "</col>";
						
						try
						{
							Method invokeMethod = client.getClass().getMethod("invokeMenuAction", int.class, int.class, int.class, int.class, int.class);
							invokeMethod.invoke(client, actionIndex, widgetId, MenuAction.CC_OP.getId(), 0, -1);
							log.info("Playing song: " + songName + " using widget (method 1)");
							return;
						}
						catch (NoSuchMethodException e1)
						{
							try
							{
								Method invokeMethod = client.getClass().getMethod("invokeMenuAction", String.class, String.class, int.class, int.class, int.class, int.class);
								invokeMethod.invoke(client, "Play", target, widgetId, MenuAction.CC_OP.getId(), actionIndex, -1);
								log.info("Playing song: " + songName + " using widget (method 2)");
								return;
							}
							catch (Exception e2)
							{
								log.warn("Could not invoke menu action. Trying to use menu entry creation...", e2);
								MenuEntry menuEntry = client.createMenuEntry(-1)
									.setOption("Play")
									.setTarget(target)
									.setType(MenuAction.CC_OP)
									.setParam0(actionIndex)
									.setParam1(widgetId)
									.setIdentifier(0);
								
								try
								{
									Method onClickMethod = menuEntry.getClass().getMethod("getOnClick");
									Object onClick = onClickMethod.invoke(menuEntry);
									if (onClick != null && onClick instanceof Runnable)
									{
										((Runnable) onClick).run();
										log.info("Playing song: " + songName + " using menu entry onClick");
										return;
									}
								}
								catch (Exception e3)
								{
									log.warn("Could not invoke menu entry onClick", e3);
								}
							}
						}
						catch (Exception e)
						{
							log.warn("Error invoking widget action for song: " + songName, e);
						}
					}
					catch (Exception e)
					{
						log.warn("Error creating menu entry for song: " + songName, e);
					}
				}
			}

			log.warn("Song not found in widget tree: " + songName);
			log.warn("Available songs in map: " + songWidgetMap.keySet());
			panel.showMessage("Song not found: " + songName + ". Make sure the music tab is open and the song is visible, or hover over it to register it.");
		});
	}

	private Widget findMusicTrackWidget(Widget widget, String songName)
	{
		if (widget == null)
		{
			return null;
		}

		String text = widget.getText();
		if (text != null)
		{
			String cleanText = text.replaceAll("<[^>]+>", "").trim();
			String searchName = songName.trim();
			
			if (cleanText.equalsIgnoreCase(searchName))
			{
				String[] actions = widget.getActions();
				if (actions != null)
				{
					for (String action : actions)
					{
						if (action != null && action.equals("Play"))
						{
							return widget;
						}
					}
				}
			}
			
			String cleanTextNoSpaces = cleanText.replaceAll("\\s+", "");
			String searchNameNoSpaces = searchName.replaceAll("\\s+", "");
			if (cleanTextNoSpaces.equalsIgnoreCase(searchNameNoSpaces))
			{
				String[] actions = widget.getActions();
				if (actions != null)
				{
					for (String action : actions)
					{
						if (action != null && action.equals("Play"))
						{
							return widget;
						}
					}
				}
			}
			
			if (cleanText.toLowerCase().contains(searchName.toLowerCase()) || 
			    searchName.toLowerCase().contains(cleanText.toLowerCase()))
			{
				String[] actions = widget.getActions();
				if (actions != null)
				{
					for (String action : actions)
					{
						if (action != null && action.equals("Play"))
						{
							log.debug("Found song using partial match: '" + cleanText + "' matches '" + searchName + "'");
							return widget;
						}
					}
				}
			}
		}

		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				Widget result = findMusicTrackWidget(child, songName);
				if (result != null)
				{
					return result;
				}
			}
		}

		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget child : dynamicChildren)
			{
				Widget result = findMusicTrackWidget(child, songName);
				if (result != null)
				{
					return result;
				}
			}
		}

		Widget[] nestedChildren = widget.getNestedChildren();
		if (nestedChildren != null)
		{
			for (Widget child : nestedChildren)
			{
				Widget result = findMusicTrackWidget(child, songName);
				if (result != null)
				{
					return result;
				}
			}
		}

		return null;
	}

	private void logAvailableSongs(Widget musicTab, String searchName)
	{
		List<String> foundSongs = new ArrayList<>();
		collectSongNames(musicTab, foundSongs, 50);
		
		if (!foundSongs.isEmpty())
		{
			log.warn("Searching for: '" + searchName + "'. Found " + foundSongs.size() + " songs in music tab:");
			for (int i = 0; i < Math.min(20, foundSongs.size()); i++)
			{
				String song = foundSongs.get(i);
				if (song.toLowerCase().contains(searchName.toLowerCase()) || 
				    searchName.toLowerCase().contains(song.toLowerCase()))
				{
					log.warn("  -> " + song + " (possible match)");
				}
				else
				{
					log.warn("  - " + song);
				}
			}
			if (foundSongs.size() > 20)
			{
				log.warn("  ... and " + (foundSongs.size() - 20) + " more");
			}
		}
		else
		{
			log.warn("No songs found in music tab. Make sure the music tab is open and visible.");
		}
	}
	
	private String getAvailableMethods(Class<?> clazz, String methodName)
	{
		StringBuilder sb = new StringBuilder();
		for (Method m : clazz.getMethods())
		{
			if (m.getName().equals(methodName))
			{
				sb.append(m.toString()).append("; ");
			}
		}
		return sb.toString();
	}
	
	private void exploreWidgetStructure(Widget widget, int depth, int maxDepth)
	{
		if (widget == null || depth > maxDepth)
		{
			return;
		}
		
		String indent = "  ".repeat(depth);
		String text = widget.getText();
		int widgetId = widget.getId();
		String[] actions = widget.getActions();
		
		if (text != null && !text.isEmpty() && text.length() > 2)
		{
			String cleanText = text.replaceAll("<[^>]+>", "").trim();
			if (!cleanText.isEmpty())
			{
				log.debug(indent + "Widget " + widgetId + ": '" + cleanText + "'");
				if (actions != null)
				{
					for (int i = 0; i < actions.length; i++)
					{
						if (actions[i] != null)
						{
							log.debug(indent + "  Action[" + i + "]: " + actions[i]);
						}
					}
				}
			}
		}
		
		Widget[] children = widget.getChildren();
		if (children != null && children.length > 0)
		{
			log.debug(indent + "Widget " + widgetId + " has " + children.length + " children");
			for (int i = 0; i < Math.min(children.length, 10); i++)
			{
				if (children[i] != null)
				{
					exploreWidgetStructure(children[i], depth + 1, maxDepth);
				}
			}
		}
		
		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null && dynamicChildren.length > 0)
		{
			log.debug(indent + "Widget " + widgetId + " has " + dynamicChildren.length + " dynamic children");
			for (int i = 0; i < Math.min(dynamicChildren.length, 10); i++)
			{
				if (dynamicChildren[i] != null)
				{
					exploreWidgetStructure(dynamicChildren[i], depth + 1, maxDepth);
				}
			}
		}
		
		Widget[] nestedChildren = widget.getNestedChildren();
		if (nestedChildren != null && nestedChildren.length > 0)
		{
			log.debug(indent + "Widget " + widgetId + " has " + nestedChildren.length + " nested children");
			for (int i = 0; i < Math.min(nestedChildren.length, 10); i++)
			{
				if (nestedChildren[i] != null)
				{
					exploreWidgetStructure(nestedChildren[i], depth + 1, maxDepth);
				}
			}
		}
	}

	private void collectSongNames(Widget widget, List<String> songNames, int maxSongs)
	{
		if (widget == null || songNames.size() >= maxSongs)
		{
			return;
		}

		String text = widget.getText();
		if (text != null && !text.isEmpty())
		{
			String cleanText = text.replaceAll("<[^>]+>", "").trim();
			if (!cleanText.isEmpty() && cleanText.length() > 2)
			{
				String[] actions = widget.getActions();
				boolean hasPlayAction = false;
				if (actions != null)
				{
					for (String action : actions)
					{
						if (action != null && action.equals("Play"))
						{
							hasPlayAction = true;
							break;
						}
					}
				}
				
				if (hasPlayAction || (cleanText.length() > 5 && cleanText.matches(".*[a-zA-Z].*")))
				{
					if (!songNames.contains(cleanText))
					{
						songNames.add(cleanText);
						log.debug("Found potential song: '" + cleanText + "' (hasPlayAction: " + hasPlayAction + ")");
					}
				}
			}
		}

		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				if (songNames.size() >= maxSongs)
				{
					break;
				}
				collectSongNames(child, songNames, maxSongs);
			}
		}

		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget child : dynamicChildren)
			{
				if (songNames.size() >= maxSongs)
				{
					break;
				}
				collectSongNames(child, songNames, maxSongs);
			}
		}
		
		Widget[] nestedChildren = widget.getNestedChildren();
		if (nestedChildren != null)
		{
			for (Widget child : nestedChildren)
			{
				if (songNames.size() >= maxSongs)
				{
					break;
				}
				collectSongNames(child, songNames, maxSongs);
			}
		}
	}

	public void toggleLoop()
	{
		isLooping = !isLooping;
		panel.updatePlayerState();
	}

	public void toggleShuffle()
	{
		isShuffling = !isShuffling;
		if (isPlaying && !currentPlayOrder.isEmpty() && currentTrackIndex >= 0)
		{
			if (isShuffling)
			{
				List<Integer> remaining = new ArrayList<>(currentPlayOrder.subList(currentTrackIndex, currentPlayOrder.size()));
				Collections.shuffle(remaining, new Random());
				currentPlayOrder.subList(currentTrackIndex, currentPlayOrder.size()).clear();
				currentPlayOrder.addAll(remaining);
			}
		}
		panel.updatePlayerState();
	}

	public void setActivePlaylist(String playlistName)
	{
		configManager.setConfiguration("musicplaylists", "activePlaylist", playlistName);
	}

	public void onPlaylistChanged()
	{
		stopPlayback();
		panel.refreshPlaylist();
	}

	public boolean isPlaying()
	{
		return isPlaying;
	}

	public boolean isLooping()
	{
		return isLooping;
	}

	public boolean isShuffling()
	{
		return isShuffling;
	}

	public String getPlaylistSongs(String playlistName)
	{
		return configManager.getConfiguration("musicplaylists", "playlist_" + playlistName, String.class);
	}

	public void setPlaylistSongs(String playlistName, String songs)
	{
		configManager.setConfiguration("musicplaylists", "playlist_" + playlistName, songs);
	}

	public String[] getAllPlaylists()
	{
		String playlistNames = config.playlistNames();
		if (playlistNames == null || playlistNames.isEmpty())
		{
			return new String[0];
		}
		return playlistNames.split(",");
	}

	public void addPlaylist(String playlistName)
	{
		String playlistNames = config.playlistNames();
		if (playlistNames == null || playlistNames.isEmpty())
		{
			configManager.setConfiguration("musicplaylists", "playlistNames", playlistName);
		}
		else
		{
			configManager.setConfiguration("musicplaylists", "playlistNames", playlistNames + "," + playlistName);
		}
	}

	public void deletePlaylist(String playlistName)
	{
		configManager.unsetConfiguration("musicplaylists", "playlist_" + playlistName);
		String playlistNames = config.playlistNames();
		if (playlistNames != null && !playlistNames.isEmpty())
		{
			String[] names = playlistNames.split(",");
			StringBuilder newNames = new StringBuilder();
			for (String name : names)
			{
				if (!name.equals(playlistName))
				{
					if (newNames.length() > 0)
					{
						newNames.append(",");
					}
					newNames.append(name);
				}
			}
			configManager.setConfiguration("musicplaylists", "playlistNames", newNames.toString());
		}
	}

	private File getSongsFile()
	{
		String runeliteDir = System.getProperty("user.home") + File.separator + ".runelite";
		File pluginDir = new File(runeliteDir, "music-playlists");
		if (!pluginDir.exists())
		{
			pluginDir.mkdirs();
		}
		return new File(pluginDir, "songs.txt");
	}

	private void saveSongToFile(String songName, SongWidgetInfo widgetInfo)
	{
		try
		{
			File songsFile = getSongsFile();
			boolean fileExists = songsFile.exists();
			
			if (fileExists)
			{
				List<String> lines = Files.readAllLines(Paths.get(songsFile.getAbsolutePath()));
				for (String line : lines)
				{
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#"))
					{
						continue;
					}
					String[] parts = line.split("\\|", 2);
					if (parts.length > 0 && parts[0].equals(songName))
					{
						return;
					}
				}
			}
			
			try (PrintWriter writer = new PrintWriter(new FileWriter(songsFile, true)))
			{
				if (!fileExists)
				{
					writer.println("# Music Playlists Song Data");
					writer.println("# Format: songName|identifier|param0|param1|base64target");
				}
				
				String encodedTarget = Base64.getEncoder().encodeToString(widgetInfo.target.getBytes("UTF-8"));
				writer.println(songName + "|" + widgetInfo.identifier + "|" + widgetInfo.param0 + "|" + widgetInfo.param1 + "|" + encodedTarget);
			}
		}
		catch (IOException e)
		{
			log.warn("Error saving song to file: " + songName, e);
		}
	}

	private void loadSongsFromFile()
	{
		java.util.Set<String> resourceSongs = new java.util.HashSet<>();
		
		try (InputStream resourceStream = getClass().getResourceAsStream("/songs.txt"))
		{
			if (resourceStream != null)
			{
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, "UTF-8")))
				{
					String line;
					while ((line = reader.readLine()) != null)
					{
						line = line.trim();
						if (line.isEmpty() || line.startsWith("#"))
						{
							continue;
						}

						String[] parts = line.split("\\|", 5);
						if (parts.length == 5)
						{
							try
							{
								String songName = parts[0];
								int identifier = Integer.parseInt(parts[1]);
								int param0 = Integer.parseInt(parts[2]);
								int param1 = Integer.parseInt(parts[3]);
								String encodedTarget = parts[4];
								
								String target = new String(Base64.getDecoder().decode(encodedTarget), "UTF-8");
								
								SongWidgetInfo widgetInfo = new SongWidgetInfo(identifier, param0, param1, target);
								if (!songWidgetMap.containsKey(songName))
								{
									songWidgetMap.put(songName, widgetInfo);
									resourceSongs.add(songName);
								}
							}
							catch (Exception e)
							{
								log.debug("Error parsing resource song line: " + line, e);
							}
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Error loading songs from resource file", e);
		}

		File songsFile = getSongsFile();
		boolean localFileExists = songsFile.exists();
		
		if (localFileExists)
		{
			try
			{
				List<String> lines = Files.readAllLines(Paths.get(songsFile.getAbsolutePath()));
				for (String line : lines)
				{
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#"))
					{
						continue;
					}

					String[] parts = line.split("\\|", 5);
					if (parts.length == 5)
					{
						try
						{
							String songName = parts[0];
							int identifier = Integer.parseInt(parts[1]);
							int param0 = Integer.parseInt(parts[2]);
							int param1 = Integer.parseInt(parts[3]);
							String encodedTarget = parts[4];
							
							String target = new String(Base64.getDecoder().decode(encodedTarget), "UTF-8");
							
							SongWidgetInfo widgetInfo = new SongWidgetInfo(identifier, param0, param1, target);
							songWidgetMap.put(songName, widgetInfo);
						}
						catch (Exception e)
						{
							log.debug("Error parsing local song line: " + line, e);
						}
					}
				}
			}
			catch (IOException e)
			{
				log.warn("Error loading songs from local file", e);
			}
		}

		try (InputStream resourceStream = getClass().getResourceAsStream("/songs.txt"))
		{
			if (resourceStream != null)
			{
				java.util.Set<String> localSongs = new java.util.HashSet<>();
				if (localFileExists)
				{
					try
					{
						List<String> localLines = Files.readAllLines(Paths.get(songsFile.getAbsolutePath()));
						for (String localLine : localLines)
						{
							localLine = localLine.trim();
							if (!localLine.isEmpty() && !localLine.startsWith("#"))
							{
								String[] localParts = localLine.split("\\|", 2);
								if (localParts.length > 0)
								{
									localSongs.add(localParts[0]);
								}
							}
						}
					}
					catch (IOException e)
					{
						log.debug("Error reading local songs for merge check", e);
					}
				}

				java.util.List<String> songsToAdd = new ArrayList<>();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, "UTF-8")))
				{
					String resourceLine;
					while ((resourceLine = reader.readLine()) != null)
					{
						resourceLine = resourceLine.trim();
						if (resourceLine.isEmpty() || resourceLine.startsWith("#"))
						{
							continue;
						}

						if (!localFileExists)
						{
							songsToAdd.add(resourceLine);
						}
						else
						{
							String[] parts = resourceLine.split("\\|", 2);
							if (parts.length > 0)
							{
								String songName = parts[0];
								if (!localSongs.contains(songName))
								{
									songsToAdd.add(resourceLine);
								}
							}
						}
					}
				}

				if (!songsToAdd.isEmpty() || !localFileExists)
				{
					try (PrintWriter writer = new PrintWriter(new FileWriter(songsFile, true)))
					{
						if (!localFileExists)
						{
							writer.println("# Music Playlists Song Data");
							writer.println("# Format: songName|identifier|param0|param1|base64target");
						}

						for (String songLine : songsToAdd)
						{
							writer.println(songLine);
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Error merging resource songs to local file", e);
		}

		log.info("Loaded " + songWidgetMap.size() + " songs (resource: " + resourceSongs.size() + " new, local: " + (songWidgetMap.size() - resourceSongs.size()) + ")");
	}

	@Provides
	MusicPlaylistsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MusicPlaylistsConfig.class);
	}
}

