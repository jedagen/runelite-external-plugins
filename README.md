# Music Playlists Plugin

A RuneLite plugin that allows you to create and manage playlists of RuneScape music tracks. Build custom playlists, play them automatically, and control playback with shuffle and loop options.

## Features

- **Create Multiple Playlists**: Organize your favorite songs into custom playlists
- **Automatic Playback**: Play entire playlists with a single click
- **Playback Controls**: Play, stop, skip, shuffle, and loop functionality
- **Easy Song Management**: Add songs to playlists directly from the music tab
- **Drag and Drop Reordering**: Rearrange songs in your playlists by dragging them
- **Visual Player**: Radio-style display showing the currently playing song

## How to Use

### Getting Started

1. **Open the Music Tab**: Press `F4` or click the music icon in-game to open the music tab
2. **Register Songs**: Hover over songs in the music tab to register them with the plugin (this allows them to be played automatically)
3. **Access the Plugin**: Click the "Music Playlists" icon in the RuneLite sidebar

### Creating a Playlist

1. Click the `+` button next to the playlist dropdown
2. Enter a name for your playlist
3. The new playlist will be created and selected automatically

### Adding Songs to a Playlist

1. Open the music tab (`F4`)
2. Right-click on any song
3. Select "Add to Playlist" from the context menu
4. The song will be added to your currently selected playlist

### Playing a Playlist

1. Select a playlist from the dropdown
2. Click the **Play** button to start playback
3. Use **Skip** to move to the next song
4. Use **Stop** to stop playback
5. Toggle **Shuffle** to randomize the playback order
6. Toggle **Loop** to automatically restart the playlist when it ends

### Managing Playlists

- **Reorder Songs**: Drag and drop songs in the playlist list to reorder them
- **Remove Songs**: Right-click on a song in the playlist and select "Remove from Playlist"
- **Switch Playlists**: Select a different playlist from the dropdown to view and edit it

## Requirements

- The music tab must be open (`F4`) for the plugin to play songs
- Songs must be hovered over at least once to register them for automatic playback
- You must be logged into the game for playback to work

## Technical Notes

The plugin stores menu entry information when you hover over songs, allowing it to play them programmatically even if they're not currently visible in the music tab. This means you only need to hover over songs once to register them, and they'll be available for playlist playback afterward.
