# jellyfin

## Overview
Groups the presentation-layer Jellyfin integration packages under `presentation.jellyfin`, separating server authentication and initial library selection from the connected Jellyfin management dashboard.

## Subdirectories & Boundaries
- `auth`: [./auth/README.md](./auth/README.md) - `JellyfinLoginActivity`, `JellyfinLoginScreen`, and `JellyfinLoginViewModel` handling Jellyfin login, local network permissions, music library selection, and initial sync scheduling.
- `dashboard`: [./dashboard/README.md](./dashboard/README.md) - `JellyfinDashboardScreen` and `JellyfinDashboardViewModel` managing account status, library filters, favorites/playlist synchronization, and disconnection.
