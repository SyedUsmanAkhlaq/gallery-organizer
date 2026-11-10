import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/albums/presentation/album_screen.dart';
import '../features/albums/presentation/albums_screen.dart';
import '../features/albums/presentation/viewer_screen.dart';
import '../features/debug/debug_screen.dart';
import '../features/placeholders/coming_soon_screen.dart';
import '../features/settings/settings_screen.dart';
import 'home_shell.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();

/// Route helpers so screens never hand-build paths.
abstract final class Routes {
  static String album(String albumId, String name) =>
      '/albums/${Uri.encodeComponent(albumId)}'
      '?name=${Uri.encodeQueryComponent(name)}';

  static const settings = '/settings';
  static const debug = '/debug';

  static String viewer(String albumId, int index) =>
      '/view/${Uri.encodeComponent(albumId)}?index=$index';
}

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/albums',
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => HomeShell(shell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/albums',
              builder: (context, state) => const AlbumsScreen(),
              routes: [
                GoRoute(
                  path: ':albumId',
                  builder: (context, state) => AlbumScreen(
                    albumId: state.pathParameters['albumId']!,
                    title: state.uri.queryParameters['name'] ?? 'Album',
                  ),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/categories',
              builder: (context, state) => const ComingSoonScreen(
                title: 'Categories',
                icon: Icons.checkroom_outlined,
                message: 'Clothing categories arrive in milestone 3.',
              ),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/people',
              builder: (context, state) => const ComingSoonScreen(
                title: 'People',
                icon: Icons.face_outlined,
                message: 'Face grouping arrives in milestone 4.',
              ),
            ),
          ]),
        ],
      ),
      GoRoute(
        path: Routes.settings,
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const SettingsScreen(),
      ),
      GoRoute(
        path: Routes.debug,
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => const DebugScreen(),
      ),
      // Full-screen viewer sits on the root navigator, above the tab bar.
      GoRoute(
        path: '/view/:albumId',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => ViewerScreen(
          albumId: state.pathParameters['albumId']!,
          initialIndex:
              int.tryParse(state.uri.queryParameters['index'] ?? '') ?? 0,
        ),
      ),
    ],
  );
});
