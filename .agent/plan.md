# Project Plan

Refine Chapter app UI and UX:
1. Replicate play/pause and skip buttons from image_3.png/image_4.png.
2. Add a settings page to toggle skip buttons between time-based (+10s/-10s) and chapter-based (Prev/Next Chapter).
3. Implement dynamic mono-color theming on 'Now Playing' based on cover art.
4. Add a scrollable chapter list on 'Now Playing'.
5. Implement smooth transitions between Mini Player and full-screen player using swipe gestures.
6. Remove back button on 'Now Playing' in favor of the swipe-down gesture.

## Project Brief

# Project Brief: Chapter

Chapter is a refined local audiobook player for Android that emphasizes high-fidelity interaction and a cohesive Material 3 Expressive design. The app focuses on providing an immersive, content-centric experience through smooth transitions and adaptive controls.

## Features

*   **Dynamic Mono-Color Theming**: An immersive 'Now Playing' UI that adapts its entire color scheme to a single dominant color extracted from the currently playing audiobook's cover art.
*   **Seamless Player Transitions**: High-fidelity motion transitions between a pill-shaped Mini Player and the full-screen interface, including an intuitive top-handle swipe-down gesture to minimize.
*   **Adaptive Navigation Controls**: User-configurable playback buttons that can be toggled between precise time-based skips (+/- 10s) and chapter-based jumps (Previous/Next Chapter).
*   **Integrated Chapter List**: A contextual chapter navigation list accessible directly within the 'Now Playing' screen via a simple vertical scroll.

## High-Level Technical Stack

*   **Kotlin**: The primary language for the application's core logic and playback service.
*   **Jetpack Compose**: Used for the expressive UI, shared element transitions, and gesture-driven animations.
*   **Media3 (ExoPlayer & MediaSession)**: Powers the background audio service, metadata extraction, and system media integration.
*   **Room (via KSP)**: Essential for persisting playback progress and storing user settings for navigation preferences.
*   **Coil & Palette**: Used for robust cover art extraction and generating dynamic UI colors from book artwork.
*   **Kotlin Coroutines & Flow**: Manages the reactive stream of playback states and UI events.

## Implementation Steps
**Total Duration:** 52m 34s

### Task_1_SetupDataLayer: Set up Room database for books and chapters, implement local file scanning/importing logic, and initialize basic Material 3 theme.
- **Status:** COMPLETED
- **Updates:** Room database for books and chapters has been successfully set up, including implementation of local file scanning/importing logic and initialization of a Material 3 'Expressive' theme. Key components include Book and Chapter entities, DAOs, a Repository, and an AudiobookScanner using MediaMetadataRetriever. Unit tests verify Room database operations. Project now builds with all necessary dependencies.
- **Acceptance Criteria:**
  - Room database correctly saves and retrieves book metadata
  - File scanning identifies local MP3/M4B files
  - App builds with Material 3 dependencies
- **Duration:** 5m 55s

### Task_2_PlaybackService: Implement Media3 ExoPlayer and MediaSessionService for background audio playback and progress tracking.
- **Status:** COMPLETED
- **Updates:** Playback service using Media3 ExoPlayer and MediaSessionService has been successfully implemented, including background audio playback, progress tracking, and persistence to Room. Key components include PlaybackService (extending MediaSessionService), PlayerViewModel for UI interaction, and a LibraryViewModel for managing the book collection. The service correctly saves playback position to the database every 10 seconds and upon pause/stop. Necessary permissions and service declarations have been added to the AndroidManifest.xml. Basic UI for Library and Now Playing screens has also been established.
- **Acceptance Criteria:**
  - Audio plays in the background
  - MediaSession notification appears
  - Playback position is saved to database on pause/stop
- **Duration:** 9m 10s

### Task_3_LibraryUI: Build the Home and Library screens using Jetpack Compose with Material 3 components and dynamic theming.
- **Status:** COMPLETED
- **Updates:** The Home and Library screens have been successfully built using Jetpack Compose with Material 3 components and dynamic theming, following the 'Expressive' design as specified in input_images/image_0.png. Key components include a HomeScreen with 'Subscriptions', 'New episodes' and 'Locally available' sections, a LibraryScreen with a grid layout and Large FAB for imports, and a DynamicBookTheme that extracts colors from cover art using the Palette API. Bottom navigation with pill-shaped indicators is fully functional, and full Edge-to-Edge display is supported. The UI also features horizontal scrolling lists and a prominent 'New episode' card with playback controls.
- **Acceptance Criteria:**
  - Library grid displays imported books with cover art
  - Bottom navigation works
  - Dynamic theming applied based on book covers (using Coil/Palette)
  - The implemented UI must match the design provided in input_images/image_0.png
- **Duration:** 5m 37s

### Task_4_NowPlayingUI: Develop the immersive Now Playing screen featuring blurred backgrounds, playback controls, chapter list, speed settings, and sleep timer.
- **Status:** COMPLETED
- **Updates:** The immersive Now Playing screen has been successfully developed, featuring blurred backgrounds, playback controls, and chapter support, matching the design in input_images/image_1.png. Key features include a full-screen Now Playing UI with a blurred cover art background, working playback speed and sleep timer controls, and chapter navigation. The UI follows the 'Expressive' Material 3 style with large cards, prominent buttons, and a bottom navigation bar with pill-shaped indicators. The screen is fully integrated with the background playback service and supports dynamic theming based on book covers using the Palette API. Full Edge-to-Edge display is also supported.
- **Acceptance Criteria:**
  - Full-screen Now Playing UI with blurred cover art background
  - Working playback speed and sleep timer controls
  - Chapter navigation for M4B files
  - The implemented UI must match the design provided in input_images/image_1.png
- **Duration:** 3m 34s

### Task_5_FinalPolish: Implement adaptive app icon, enable full edge-to-edge display, and perform final verification.
- **Status:** COMPLETED
- **Updates:** The critical stability issue (crash due to ImageVector in Coil's AsyncImage) in HomeScreen.kt and LibraryScreen.kt has been fixed by the coder agent. The app now correctly handles fallback icons for books without cover art. The app is stable and ready for final verification. Adaptive icon and edge-to-edge support are also implemented.
- **Acceptance Criteria:**
  - Adaptive icon is set
  - App is edge-to-edge (transparent status/nav bars)
  - App does not crash
  - All features work as expected
  - Build pass
- **Duration:** 13m 5s

### Task_6_UIRefinementAndMiniPlayer: Refine UI by removing podcast references/Discover tab, implementing a floating pill-shaped Mini Player, moving the Now Playing handle, and fixing cover art extraction logic.
- **Status:** COMPLETED
- **Updates:** The "Chapter" audiobook app has been significantly refined. Podcast references and the "Discover" tab have been removed from the navigation and UI, replaced with "Books", "Your Library", and "Recent Additions". A new floating, pill-shaped Mini Player has been implemented above the bottom navigation bar, matching the design in image_2.png. The Now Playing screen's dismiss handle has been moved to the top. Cover art extraction from local files is now more robust, with images saved to internal storage for reliable loading by Coil. The UI follows the Material 3 Expressive style with bold typography and large rounded corners. Build pass.
- **Acceptance Criteria:**
  - Discover tab and podcast references removed
  - Floating pill Mini Player implemented above bottom nav
  - Now Playing handle moved to top of the screen
  - Cover art extraction from local files fixed
  - The implemented UI must match the design provided in input_images/image_2.png
- **Duration:** 3m 43s

### Task_7_AdvancedPlaybackAndSettings: Implement Bookmarks, Sleep Timer, Variable Skip Durations, Silence Skipping, and a Settings page to toggle skip behavior. Update playback buttons based on image_3.png and image_4.png.
- **Status:** COMPLETED
- **Updates:** Advanced playback features and settings have been implemented. The "Now Playing" UI now replicates the design from image_3.png and image_4.png, including a large rounded rectangle (squircle) for the Play/Pause button and circular flanking buttons for skips. A Settings page allows users to toggle skip button behavior between time-based (+/- 10s) and chapter-based jumps. Variable skip durations (10s, 30s, 60s) and silence skipping are fully functional. The UI follows the Material 3 Expressive style and integrates with the existing Bookmarks and Sleep Timer features. Build pass.
- **Acceptance Criteria:**
  - Bookmarks and Sleep Timer functional
  - Settings page allows toggling between time-based and chapter-based skips
  - Variable skip durations and silence skipping implemented
  - Playback buttons match design in input_images/image_3.png and input_images/image_4.png.
  - The implemented UI must match the design provided in input_images/image_3.png.
  - The implemented UI must match the design provided in input_images/image_4.png.
- **Duration:** 8m 23s

### Task_8_ImmersiveUXAndVerify: Implement dynamic mono-color theming and scrollable chapter list on Now Playing. Add smooth swipe transitions between Mini Player and full-screen player, replacing the back button with a swipe-down gesture. Perform final verification.
- **Status:** COMPLETED
- **Updates:** The Chapter app's UX has been significantly enhanced with immersive features. Dynamic mono-color theming has been implemented using the Palette API, extracting a dominant seed color from the audiobook's cover art to create a cohesive, immersive look across the 'Now Playing' screen. A scrollable chapter list is now integrated directly below the playback controls, accessible via a simple vertical scroll. The back button has been replaced with a modern swipe-down gesture and a top drag handle, allowing users to smoothly minimize the full-screen player into the floating Mini Player. The playback controls now strictly follow the "squircle + flanking pills" design from image_3.png, and the app intelligently handles both time-based and chapter-based jumps. Build pass.
- **Acceptance Criteria:**
  - Now Playing uses dynamic mono-color theming based on cover art
  - Scrollable chapter list available on Now Playing screen
  - Smooth swipe transitions and swipe-down-to-dismiss implemented
  - App does not crash
  - Make sure all existing tests pass
  - Build pass
- **Duration:** 3m 7s

