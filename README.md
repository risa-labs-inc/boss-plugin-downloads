# BOSS Downloads

Active and completed downloads, in a left sidebar panel.

Renders the host's `DownloadDataProvider` with live progress, transfer speed and per-item
actions, so a download started from an embedded browser tab stays visible and controllable
without opening a separate manager.

## What it does

- **Live progress** per download: progress bar, bytes transferred, and current speed.
- **Pause and resume** an in-flight download.
- **Cancel** a download, or remove a finished one from the list. Removal asks first.
- **Reveal in Finder or Explorer**, or open the completed file directly.
- **Clear all completed** entries in one action.
- Status icons distinguish completed, failed, downloading and paused.

An empty list shows an icon and a short message. That is the normal resting state, not an
error.

## MCP tools

| Tool | Purpose |
|---|---|
| `downloads_list` | List downloads with status and progress |
| `download_pause` | Pause a download by id |
| `download_resume` | Resume a paused download by id |
| `download_cancel` | Cancel a download by id |
| `download_open` | Open a completed download's file |
| `downloads_clear_completed` | Drop all completed entries from the list |

## Requirements

- BOSS >= 9.2.20, boss-plugin-api >= 1.0.20
- `context.downloadDataProvider` must be present. Without it `register()` throws.
- No external binaries.

## Build

```bash
./gradlew buildPluginJar
cp build/libs/boss-plugin-downloads-*.jar ~/.boss/plugins/
```

Then reload from Toolbox. For a dev-mode host use `~/.boss_debug/plugins/` instead.

See [AGENTS.md](AGENTS.md) for architecture and conventions.
