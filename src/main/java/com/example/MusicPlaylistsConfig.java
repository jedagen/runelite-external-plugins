package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("musicplaylists")
public interface MusicPlaylistsConfig extends Config
{
	@ConfigItem(
		keyName = "activePlaylist",
		name = "Active Playlist",
		description = "The currently selected playlist",
		hidden = true
	)
	default String activePlaylist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "playlistNames",
		name = "Playlist Names",
		description = "Comma-separated list of playlist names",
		hidden = true
	)
	default String playlistNames()
	{
		return "";
	}
}

