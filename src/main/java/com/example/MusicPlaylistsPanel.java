package com.example;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MusicPlaylistsPanel extends PluginPanel
{
	private final MusicPlaylistsPlugin plugin;
	private final MusicPlaylistsConfig config;

	private JPanel playerPanel;
	private JLabel nowPlayingLabel;
	private JButton playButton;
	private JButton stopButton;
	private JButton skipButton;
	private JToggleButton loopButton;
	private JToggleButton shuffleButton;

	private JComboBox<String> playlistComboBox;
	private JButton newPlaylistButton;
	private JPanel playlistPanel;
	private JScrollPane playlistScrollPane;
	private DefaultListModel<String> playlistModel;
	private JList<String> playlistList;
	
	private boolean isInitializing = true;

	private Timer scrollTimer;
	private String scrollingText = "";
	private int scrollPosition = 0;
	private static final int SCROLL_SPEED = 2;
	private static final int SCROLL_PAUSE = 30;

	public MusicPlaylistsPanel(MusicPlaylistsPlugin plugin, MusicPlaylistsConfig config)
	{
		this.plugin = plugin;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		buildPlayerPanel();
		buildPlaylistControls();
		buildPlaylistPanel();

		refreshPlaylist();
		updatePlayerState();
		isInitializing = false;
	}

	private void buildPlayerPanel()
	{
		playerPanel = new JPanel();
		playerPanel.setLayout(new BorderLayout());
		playerPanel.setBackground(new Color(40, 40, 40));
		playerPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.DARKER_GRAY_COLOR),
			new EmptyBorder(10, 10, 10, 10)
		));

		JPanel displayPanel = new JPanel();
		displayPanel.setLayout(new BorderLayout());
		displayPanel.setBackground(new Color(20, 20, 20));
		displayPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 60, 60), 2),
			new EmptyBorder(8, 12, 8, 12)
		));

		nowPlayingLabel = new JLabel("Now playing: ");
		nowPlayingLabel.setForeground(new Color(255, 200, 0));
		nowPlayingLabel.setFont(new Font("Arial", Font.BOLD, 12));
		displayPanel.add(nowPlayingLabel, BorderLayout.CENTER);

		playerPanel.add(displayPanel, BorderLayout.NORTH);

		JPanel controlsPanel = new JPanel();
		controlsPanel.setLayout(new GridLayout(2, 3, 5, 5));
		controlsPanel.setBackground(new Color(40, 40, 40));

		playButton = new JButton("Play");
		playButton.setPreferredSize(new Dimension(80, 30));
		playButton.addActionListener(e -> plugin.playPlaylist());

		stopButton = new JButton("Stop");
		stopButton.setPreferredSize(new Dimension(80, 30));
		stopButton.addActionListener(e -> plugin.stopPlayback());

		skipButton = new JButton("Skip");
		skipButton.setPreferredSize(new Dimension(80, 30));
		skipButton.addActionListener(e -> plugin.skipTrack());

		loopButton = new JToggleButton("Loop");
		loopButton.setPreferredSize(new Dimension(80, 30));
		loopButton.addActionListener(e -> plugin.toggleLoop());

		shuffleButton = new JToggleButton("Shuffle");
		shuffleButton.setPreferredSize(new Dimension(90, 30));
		shuffleButton.addActionListener(e -> plugin.toggleShuffle());

		controlsPanel.add(playButton);
		controlsPanel.add(stopButton);
		controlsPanel.add(skipButton);
		controlsPanel.add(loopButton);
		controlsPanel.add(shuffleButton);
		JPanel emptyPanel = new JPanel();
		emptyPanel.setBackground(new Color(40, 40, 40));
		controlsPanel.add(emptyPanel);

		playerPanel.add(controlsPanel, BorderLayout.CENTER);

		scrollTimer = new Timer(50, e -> updateScrollingText());
		scrollTimer.start();

		add(playerPanel, BorderLayout.NORTH);
	}

	private void buildPlaylistControls()
	{
		JPanel controlsPanel = new JPanel();
		controlsPanel.setLayout(new BorderLayout());
		controlsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		controlsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout(5, 0));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		playlistComboBox = new JComboBox<>();
		playlistComboBox.setPreferredSize(new Dimension(200, 30));
		playlistComboBox.addActionListener(e -> {
			if (isInitializing)
			{
				return;
			}
			String selected = (String) playlistComboBox.getSelectedItem();
			if (selected != null && !selected.isEmpty())
			{
				plugin.setActivePlaylist(selected);
				plugin.onPlaylistChanged();
				refreshPlaylist();
			}
		});

		JButton deletePlaylistButton = new JButton("-");
		deletePlaylistButton.setPreferredSize(new Dimension(40, 30));
		deletePlaylistButton.setBackground(new Color(200, 50, 50));
		deletePlaylistButton.setForeground(Color.WHITE);
		deletePlaylistButton.addActionListener(e -> deletePlaylist());

		newPlaylistButton = new JButton("+");
		newPlaylistButton.setPreferredSize(new Dimension(40, 30));
		newPlaylistButton.addActionListener(e -> createNewPlaylist());

		JPanel buttonContainer = new JPanel();
		buttonContainer.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttonContainer.add(deletePlaylistButton);
		buttonContainer.add(newPlaylistButton);

		topPanel.add(playlistComboBox, BorderLayout.CENTER);
		topPanel.add(buttonContainer, BorderLayout.EAST);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
		buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton exportButton = new JButton("Export");
		exportButton.setPreferredSize(new Dimension(100, 30));
		exportButton.addActionListener(e -> exportPlaylist());

		JButton importButton = new JButton("Import");
		importButton.setPreferredSize(new Dimension(100, 30));
		importButton.addActionListener(e -> importPlaylist());

		buttonPanel.add(exportButton);
		buttonPanel.add(importButton);

		controlsPanel.add(topPanel, BorderLayout.NORTH);
		controlsPanel.add(buttonPanel, BorderLayout.SOUTH);
		add(controlsPanel, BorderLayout.CENTER);
	}

	private void buildPlaylistPanel()
	{
		playlistPanel = new JPanel();
		playlistPanel.setLayout(new BorderLayout());
		playlistPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		playlistPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		playlistModel = new DefaultListModel<>();
		playlistList = new JList<>(playlistModel);
		playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		playlistList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		playlistList.setForeground(Color.WHITE);
		playlistList.setDragEnabled(true);
		playlistList.setDropMode(DropMode.INSERT);

		playlistList.setTransferHandler(new TransferHandler()
		{
			@Override
			public int getSourceActions(JComponent c)
			{
				return MOVE;
			}

			@Override
			protected Transferable createTransferable(JComponent c)
			{
				JList<?> list = (JList<?>) c;
				int index = list.getSelectedIndex();
				if (index >= 0)
				{
					return new StringSelection((String) list.getSelectedValue());
				}
				return null;
			}

			@Override
			public boolean canImport(TransferSupport support)
			{
				return support.isDataFlavorSupported(DataFlavor.stringFlavor);
			}

			@Override
			public boolean importData(TransferSupport support)
			{
				if (!canImport(support))
				{
					return false;
				}

				JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
				int index = dl.getIndex();

				try
				{
					String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
					int sourceIndex = playlistModel.indexOf(data);

					if (sourceIndex >= 0)
					{
						playlistModel.remove(sourceIndex);
						if (index > sourceIndex)
						{
							index--;
						}
						playlistModel.insertElementAt(data, index);
						playlistList.setSelectedIndex(index);
						savePlaylistOrder();
						return true;
					}
				}
				catch (Exception e)
				{
				}

				return false;
			}
		});

		JPopupMenu contextMenu = new JPopupMenu();
		JMenuItem removeItem = new JMenuItem("Remove from Playlist");
		removeItem.addActionListener(e -> {
			int selectedIndex = playlistList.getSelectedIndex();
			if (selectedIndex >= 0)
			{
				playlistModel.remove(selectedIndex);
				savePlaylistOrder();
			}
		});
		contextMenu.add(removeItem);
		playlistList.setComponentPopupMenu(contextMenu);

		playlistScrollPane = new JScrollPane(playlistList);
		playlistScrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		playlistScrollPane.setBorder(BorderFactory.createEmptyBorder());

		playlistPanel.add(playlistScrollPane, BorderLayout.CENTER);

		Component[] components = getComponents();
		if (components.length > 1 && components[1] instanceof JPanel)
		{
			JPanel centerPanel = (JPanel) components[1];
			centerPanel.add(playlistPanel, BorderLayout.CENTER);
		}
	}

	private void updateScrollingText()
	{
		if (scrollingText.length() <= 20)
		{
			nowPlayingLabel.setText("Now playing: " + scrollingText);
			return;
		}

		String displayText = scrollingText;
		if (scrollPosition < SCROLL_PAUSE)
		{
			displayText = scrollingText.substring(0, Math.min(20, scrollingText.length()));
		}
		else
		{
			int start = (scrollPosition - SCROLL_PAUSE) / SCROLL_SPEED;
			if (start + 20 <= scrollingText.length())
			{
				displayText = scrollingText.substring(start, start + 20);
			}
			else
			{
				String end = scrollingText.substring(start);
				String beginning = scrollingText.substring(0, 20 - end.length());
				displayText = end + " - " + beginning;
			}
		}

		nowPlayingLabel.setText("Now playing: " + displayText);

		scrollPosition++;
		int maxPosition = SCROLL_PAUSE + (scrollingText.length() + 10) * SCROLL_SPEED;
		if (scrollPosition > maxPosition)
		{
			scrollPosition = 0;
		}
	}

	public void updateNowPlaying(String songName)
	{
		scrollingText = songName.isEmpty() ? "" : songName;
		scrollPosition = 0;
		if (songName.isEmpty())
		{
			nowPlayingLabel.setText("Now playing: ");
		}
	}

	public void updatePlayerState()
	{
		if (playButton == null || stopButton == null || skipButton == null || 
		    loopButton == null || shuffleButton == null)
		{
			return;
		}
		
		loopButton.setSelected(plugin.isLooping());
		shuffleButton.setSelected(plugin.isShuffling());
		playButton.setEnabled(!plugin.isPlaying());
		stopButton.setEnabled(plugin.isPlaying());
		skipButton.setEnabled(plugin.isPlaying());
		loopButton.setEnabled(true);
		shuffleButton.setEnabled(true);
	}

	public void refreshPlaylist()
	{
		String[] playlists = plugin.getAllPlaylists();
		String currentSelection = (String) playlistComboBox.getSelectedItem();
		playlistComboBox.removeAllItems();
		for (String playlist : playlists)
		{
			playlistComboBox.addItem(playlist);
		}

		if (currentSelection != null)
		{
			for (int i = 0; i < playlistComboBox.getItemCount(); i++)
			{
				if (playlistComboBox.getItemAt(i).equals(currentSelection))
				{
					playlistComboBox.setSelectedIndex(i);
					break;
				}
			}
		}
		else
		{
			String activePlaylist = config.activePlaylist();
			if (activePlaylist != null && !activePlaylist.isEmpty())
			{
				playlistComboBox.setSelectedItem(activePlaylist);
			}
		}

		String selectedPlaylist = (String) playlistComboBox.getSelectedItem();
		playlistModel.clear();

		if (selectedPlaylist == null || selectedPlaylist.isEmpty())
		{
			playlistModel.addElement("No playlist selected");
			playlistList.setEnabled(false);
			return;
		}

		playlistList.setEnabled(true);
		String songs = plugin.getPlaylistSongs(selectedPlaylist);
		if (songs == null || songs.isEmpty())
		{
			playlistModel.addElement("Playlist is empty");
			return;
		}

		String[] songArray = songs.split("\\|");
		for (String song : songArray)
		{
			String trimmed = song.trim();
			if (!trimmed.isEmpty())
			{
				playlistModel.addElement(trimmed);
			}
		}
	}

	private void createNewPlaylist()
	{
		String name = JOptionPane.showInputDialog(this, "Enter playlist name:", "New Playlist", JOptionPane.PLAIN_MESSAGE);
		if (name != null && !name.trim().isEmpty())
		{
			name = name.trim();
			String[] existing = plugin.getAllPlaylists();
			for (String existingName : existing)
			{
				if (existingName.equals(name))
				{
					JOptionPane.showMessageDialog(this, "A playlist with that name already exists.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
			}

			plugin.addPlaylist(name);
			refreshPlaylist();
			playlistComboBox.setSelectedItem(name);
			plugin.setActivePlaylist(name);
			plugin.onPlaylistChanged();
		}
	}

	private void savePlaylistOrder()
	{
		String selectedPlaylist = (String) playlistComboBox.getSelectedItem();
		if (selectedPlaylist == null || selectedPlaylist.isEmpty())
		{
			return;
		}

		StringBuilder songs = new StringBuilder();
		for (int i = 0; i < playlistModel.size(); i++)
		{
			String song = playlistModel.getElementAt(i);
			if (!song.equals("No playlist selected") && !song.equals("Playlist is empty"))
			{
				if (songs.length() > 0)
				{
					songs.append("|");
				}
				songs.append(song);
			}
		}

		plugin.setPlaylistSongs(selectedPlaylist, songs.toString());
	}

	public void showMessage(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Music Playlists", JOptionPane.INFORMATION_MESSAGE);
	}

	private void exportPlaylist()
	{
		String selectedPlaylist = (String) playlistComboBox.getSelectedItem();
		if (selectedPlaylist == null || selectedPlaylist.isEmpty())
		{
			showMessage("No playlist selected.");
			return;
		}

		String songs = plugin.getPlaylistSongs(selectedPlaylist);
		if (songs == null || songs.isEmpty())
		{
			showMessage("Playlist is empty.");
			return;
		}

		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export Playlist");
		fileChooser.setSelectedFile(new File(selectedPlaylist + ".txt"));
		fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));

		int result = fileChooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			String filePath = file.getAbsolutePath();
			if (!filePath.toLowerCase().endsWith(".txt"))
			{
				filePath += ".txt";
				file = new File(filePath);
			}

			try
			{
				String[] songArray = songs.split("\\|");
				try (PrintWriter writer = new PrintWriter(new FileWriter(file)))
				{
					for (String song : songArray)
					{
						String trimmed = song.trim();
						if (!trimmed.isEmpty())
						{
							writer.println(trimmed);
						}
					}
				}
				showMessage("Playlist exported successfully to: " + file.getName());
			}
			catch (IOException e)
			{
				showMessage("Error exporting playlist: " + e.getMessage());
			}
		}
	}

	private void importPlaylist()
	{
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Import Playlist");
		fileChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));

		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION)
		{
			File file = fileChooser.getSelectedFile();
			String fileName = file.getName();
			if (fileName.toLowerCase().endsWith(".txt"))
			{
				fileName = fileName.substring(0, fileName.length() - 4);
			}

			String playlistName = fileName.trim();
			if (playlistName.isEmpty())
			{
				showMessage("Invalid file name.");
				return;
			}

			String[] existing = plugin.getAllPlaylists();
			for (String existingName : existing)
			{
				if (existingName.equals(playlistName))
				{
					showMessage("A playlist with that name already exists.");
					return;
				}
			}

			try
			{
				List<String> songList = Files.readAllLines(Paths.get(file.getAbsolutePath()));
				StringBuilder songs = new StringBuilder();
				for (String song : songList)
				{
					String trimmed = song.trim();
					if (!trimmed.isEmpty())
					{
						if (songs.length() > 0)
						{
							songs.append("|");
						}
						songs.append(trimmed);
					}
				}

				plugin.addPlaylist(playlistName);
				if (songs.length() > 0)
				{
					plugin.setPlaylistSongs(playlistName, songs.toString());
				}

				refreshPlaylist();
				playlistComboBox.setSelectedItem(playlistName);
				plugin.setActivePlaylist(playlistName);
				plugin.onPlaylistChanged();
				showMessage("Playlist imported successfully: " + playlistName);
			}
			catch (IOException e)
			{
				showMessage("Error importing playlist: " + e.getMessage());
			}
		}
	}

	private void deletePlaylist()
	{
		String selectedPlaylist = (String) playlistComboBox.getSelectedItem();
		if (selectedPlaylist == null || selectedPlaylist.isEmpty())
		{
			showMessage("No playlist selected.");
			return;
		}

		int result = JOptionPane.showConfirmDialog(
			this,
			"Are you sure you want to delete the playlist '" + selectedPlaylist + "'?",
			"Delete Playlist",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);

		if (result == JOptionPane.YES_OPTION)
		{
			plugin.deletePlaylist(selectedPlaylist);
			refreshPlaylist();
			plugin.onPlaylistChanged();
		}
	}
}

