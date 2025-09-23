# Player Risk Plugin

A comprehensive RuneLite plugin that analyzes nearby players and displays their gear value with intelligent color coding.

## Features

### 🎯 **Player Risk Analysis**
- Real-time calculation of player gear values using Grand Exchange prices
- Intelligent skull status detection and value adjustment
- Protect item prayer consideration with value range display

### 🎨 **Smart Color Coding**
- **Green**: Low-value targets (below 100K)
- **Yellow**: Medium-value targets (100K - 1M)
- **Red**: High-value targets (above 1M)
- Customizable color thresholds and colors

### 📊 **Value Display Options**
- **Short Format**: 1K, 1M, 1B (RuneScape style)
- **Long Format**: 1,000,000
- **Both Formats**: Display both simultaneously
- Configurable minimum value threshold

### 🔍 **Equipment Inspection**
- Right-click "Inspect Equipment" functionality
- Detailed breakdown of protected vs. unprotected value
- **Skull Status Detection**: Automatically detects and displays skull status
- Real-time price updates and skull status monitoring

### ⚡ **Performance Features**
- Intelligent caching system for Grand Exchange prices
- Asynchronous price fetching
- Automatic cache expiration and cleanup
- Fallback pricing for offline scenarios

## Configuration

### Display Settings
- **Show Player Risk**: Toggle the main value display
- **Show Value Overlay**: Display values above players
- **Show Equipment Tooltip**: Enable right-click inspection
- **Value Format**: Choose between short, long, or both formats

### Value Calculation
- **Detect Skull Status**: Automatically detect if players are skulled and adjust gear value calculations accordingly
- **Protect Item Prayer**: Account for Protect Item prayer in value calculations
- **Enable Outside Wilderness**: Show player risks even when not in PvP areas
- **Minimum Value Threshold**: Only display players above this value

### Color Customization
- **Low/Medium/High Value Colors**: Customize the color scheme
- **Value Thresholds**: Set custom breakpoints for color changes

## How It Works

### Value Calculation Logic
1. **Total Value**: Sum of all equipped items at current GE prices
2. **Skull Status Impact**:
   - **Non-skulled**: Keep 3 most valuable items (or 4 with Protect Item prayer)
   - **Skulled**: Keep 0 most valuable items (or 1 with Protect Item prayer)
3. **Protect Item Prayer**: Automatically accounts for Protect Item prayer status in calculations

### Price Fetching
- Uses the official RuneScape Wiki Grand Exchange API
- 5-minute cache expiration for optimal performance
- Fallback pricing system for offline scenarios
- Batch processing for multiple items

### Wilderness Detection
- Automatically detects when players are in wilderness areas
- Only processes and displays players in PvP zones
- Respects wilderness level boundaries

## Installation

1. Download the plugin JAR file
2. Place it in your RuneLite plugins folder
3. Restart RuneLite
4. Enable the plugin in the plugin configuration

## Usage

### Basic Operation
1. Enter any wilderness area (or enable "Outside Wilderness" setting)
2. Nearby players will automatically display their gear value
3. Values are color-coded based on total worth
4. Right-click players to inspect detailed equipment information

### Value Interpretation
- **Green Players**: Low-risk, low-reward targets
- **Yellow Players**: Balanced risk-reward ratio
- **Red Players**: High-value targets (use caution)

### Tips for PvP
- **Skull Status Awareness**: Skulled players lose more items on death (0 items kept vs 3 for non-skulled)
- **Protect Item Prayer**: Always use Protect Item prayer to keep 1 additional item
- **Risk Assessment**: Consider both the target's gear value and your own risk level
- **Dynamic Monitoring**: Values update automatically when players become skulled or change equipment

## Technical Details

### Dependencies
- RuneLite Client API
- Java 11+
- HTTP Client for GE API calls
- Concurrent data structures for performance

### Performance Considerations
- Asynchronous price fetching prevents UI blocking
- Intelligent caching reduces API calls
- Efficient player detection and filtering
- Memory-conscious data structures

### API Integration
- **Grand Exchange API**: `https://prices.runescape.wiki/api/v1/osrs/latest`
- **User Agent**: `PlayerRisk/1.0`
- **Rate Limiting**: Respects API guidelines
- **Error Handling**: Graceful fallbacks for API failures

## Contributing

This plugin is open source and welcomes contributions! Areas for improvement include:

- Enhanced item price accuracy
- Additional PvP risk indicators
- Custom overlay positioning
- Integration with other RuneLite plugins
- Performance optimizations

## License

This project is licensed under the BSD-2-Clause License - see the LICENSE file for details.

## Acknowledgments

This plugin combines and builds upon the excellent work of three existing RuneLite plugins:

- **[Player Highlighter](https://github.com/mezettle/runelite-plugins)**: Inspiration for player detection and highlighting functionality
- **[Equipment Inspector](https://github.com/botanicvelious/Equipment-Inspector)**: Equipment analysis and tooltip functionality
- **[Wilderness Player Alarm](https://github.com/adhansen/plugin-repo)**: Wilderness-specific player monitoring and alerting

Additional thanks to:
- **RuneLite Team**: Excellent plugin framework and API
- **RuneScape Wiki**: Grand Exchange price data and API

## Support

For issues, feature requests, or contributions, please visit the project repository or contact the development team.

---

**Note**: This plugin is designed for educational and entertainment purposes. Always follow RuneScape's terms of service and respect other players during PvP encounters.