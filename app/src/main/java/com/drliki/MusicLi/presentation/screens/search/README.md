# search

## Overview
Intermediate package grouping search-specific UI subcomponents under the `presentation.screens.search` namespace. Contains no direct source files; child packages provide the genre browsing grid, typography, auto-sizing, and genre-icon resolution used by `SearchScreen` and Library tabs.

## Subdirectories & Boundaries
- `components`: [./components/README.md](./components/README.md) - Genre tile grid composable (`GenreCategoriesGrid`), word-safe title auto-sizing (`GenreTitleAutoSize`), deterministic variable-font styling (`GenreTypography`), and genre drawable resolution (`getGenreImageResource`).
