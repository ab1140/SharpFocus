# SharpFocus for Rider

[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-orange)](https://plugins.jetbrains.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](../vscode-extension/LICENSE)

**Information Flow Analysis for C# in JetBrains Rider**

SharpFocus is a JetBrains Rider plugin that brings information-flow analysis to C#. Using program slicing techniques, it helps you understand data dependencies and code relationships at a glance.

![Plugin Demo](../vscode-extension/images/advanced-mode-2.png)

---

## Features

### Focus Mode
Click any variable, parameter, or field and see the complete dataflow instantly.

- **Normal Mode**: Subtle highlighting with faded irrelevant code
- **Advanced Mode**: Color-coded relations with gutter icons

### Navigation
- Tree view with hierarchical flow visualization
- CodeVision annotations showing flow statistics
- Keyboard shortcuts: `Ctrl+Alt+N` (next) / `Ctrl+Alt+P` (previous)
- Navigate between sources, transforms, and sinks

### CodeVision Integration
See inline annotations above the focused symbol showing:
- Number of locations that influence it
- Number of locations it influences
- Click to open flow details

---

## Installation

### From JetBrains Marketplace
1. Open Rider
2. Go to **Settings → Plugins**
3. Search for "SharpFocus"
4. Click **Install**

### Manual Installation
1. Download the plugin ZIP from [Releases](https://github.com/trrahul/SharpFocus/releases)
2. Open Rider → **Settings → Plugins**
3. Click ⚙️ → **Install Plugin from Disk**
4. Select the downloaded ZIP file

---

## Quick Start

1. Open a C# project in Rider
2. Click on any variable or parameter (or use `Ctrl+Shift+Alt+F`)
3. Watch SharpFocus highlight the complete dataflow
4. Open the **SharpFocus Flow** tool window (View → Tool Windows)
5. Use `Ctrl+Alt+N` / `Ctrl+Alt+P` to navigate through flow locations

---

## Settings

Configure SharpFocus in **Settings → Tools → SharpFocus**:

- **Analysis Mode**: 
  - **FOCUS**: Trigger automatically on click (recommended)
  - **MANUAL**: Trigger only via keyboard shortcut
  
- **Display Mode**:
  - **NORMAL**: Minimalist highlighting
  - **ADVANCED**: Color-coded with gutter icons

- **Server Path**: Custom language server location (optional)

---

## Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Show Focus Mode | `Ctrl+Shift+Alt+F` | `Cmd+Shift+Alt+F` |
| Clear Focus | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Navigate Next | `Ctrl+Alt+N` | `Cmd+Alt+N` |
| Navigate Previous | `Ctrl+Alt+P` | `Cmd+Alt+P` |

---

## Display Modes

### Normal Mode
- Fades out irrelevant code
- Highlights focused symbol in golden
- Clean, minimalist appearance
- Shows CodeVision hints inline

### Advanced Mode
- **Golden**: Focused symbol (seed)
- **Orange**: Source (influences the focused symbol)
- **Purple**: Transform (both influences and is influenced)
- **Cyan**: Sink (influenced by the focused symbol)
- Gutter icons for quick identification

---

## Architecture

SharpFocus consists of two components:

1. **Rider Plugin** (this repo): Kotlin-based UI and integration
2. **Language Server**: C# analysis engine using Roslyn

The plugin communicates with a bundled language server that performs the actual dataflow analysis.

---

## Current Limitations

- **Intra-method analysis only**: Cross-method analysis coming in future versions
- **C# only**: Other .NET languages not yet supported
- **Requires .NET 8.0+**: Ensure you have .NET SDK installed

---

## Building from Source

### Prerequisites
- JDK 21
- Gradle 8.13+
- PowerShell (for server bundling)
- .NET SDK 8.0+

### Build Steps

```bash
# Clone the repository
git clone https://github.com/trrahul/SharpFocus.git
cd SharpFocus/rider-plugin

# Build the plugin (with server bundling)
./gradlew buildPlugin

# Output: build/distributions/SharpFocus-0.1.0.zip
```

### Development

```bash
# Quick build (skip server bundling)
./gradlew buildPlugin -PskipServerBundle=true

# Run in Rider sandbox
.\run-ide.ps1
```
---


## Learn More

- [Blog Series](https://www.rahultr.dev/posts/part1-getting-started/)

---

## License

MIT License - see [LICENSE](../vscode-extension/LICENSE) for details.

---

## Author

**Rahul TR**
- GitHub: [@trrahul](https://github.com/trrahul)
- Website: [rahultr.dev](https://www.rahultr.dev)
