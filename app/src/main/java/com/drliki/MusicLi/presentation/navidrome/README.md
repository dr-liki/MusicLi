# navidrome

## Overview
Groups the presentation-layer Navidrome/Subsonic integration packages under `presentation.navidrome`, separating server login and initial folder selection from the connected Navidrome management dashboard.

## Subdirectories & Boundaries
- `auth`: [./auth/README.md](./auth/README.md) - `NavidromeLoginActivity`, `NavidromeLoginScreen`, and `NavidromeLoginViewModel` handling Navidrome login, local network permissions, music folder selection, and initial WorkManager sync.
- `dashboard`: [./dashboard/README.md](./dashboard/README.md) - `NavidromeDashboardScreen` and `NavidromeDashboardViewModel` managing account status, music folder filters, WorkManager-backed playlist/library synchronization, and disconnection.
