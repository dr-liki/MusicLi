# Deezer Network Client

## Overview
Retrofit service interface and Gson response models for querying the Deezer artist search API to retrieve artist artwork URLs.

## Interface
- `[DeezerApiService.kt:DeezerApiService]` - Retrofit interface defining `suspend fun searchArtist(query: String, limit: Int = 1): DeezerSearchResponse` targeting `GET search/artist`.
- `[DeezerModels.kt:DeezerSearchResponse]` - Search wrapper containing `data: List<DeezerArtist>` and `total: Int`.
- `[DeezerModels.kt:DeezerArtist]` - Artist DTO with `id`, `name`, `picture`, `pictureSmall`, `pictureMedium`, `pictureBig`, `pictureXl`, `albumCount` (`nb_album`), and `fanCount` (`nb_fan`).

## Invariants
- `[DeezerApiService.kt:DeezerApiService.searchArtist]` defaults `limit` to `1` and maps `q` to the artist name query parameter.

## Side Effects
- Network HTTP `GET` request to `search/artist` via Retrofit (`[DeezerApiService.kt:DeezerApiService.searchArtist]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
