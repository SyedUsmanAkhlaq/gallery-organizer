import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/albums/presentation/album_screen.dart';
import '../features/albums/presentation/albums_screen.dart';
import '../features/albums/presentation/viewer_screen.dart';
import '../features/categories/categories_screen.dart';
import '../features/debug/debug_screen.dart';
import '../features/people/people_screen.dart';
import '../features/people/person_screen.dart';
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

  static String category(String key, String label) =>
      '/categories/${Uri.encodeComponent(key)}'
      '?name=${Uri.encodeQueryComponent(label)}';

  static String person(int id) => '/people/$id';

  /// [source] = MediaSourceKey.encoded of the grid the viewer pages through.
  static String viewer(String source, int index) =>
      '/view?src=${Uri.encodeQueryComponent(source)}&index=$index';
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
              builder: (context, state) => const CategoriesScreen(),
              routes: [
                GoRoute(
                  path: ':key',
                  builder: (context, state) => CategoryScreen(
                    categoryKey: state.pathParameters['key']!,
                    title: state.uri.queryParameters['name'] ?? 'Category',
                  ),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/people',
              builder: (context, state) => const PeopleScreen(),
              routes: [
                GoRoute(
                  path: ':id',
                  builder: (context, state) => PersonScreen(
                    personId: int.parse(state.pathParameters['id']!),
                  ),
                ),
              ],
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
        path: '/view',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) => ViewerScreen(
          source: state.uri.queryParameters['src'] ?? '',
          initialIndex:
              int.tryParse(state.uri.queryParameters['index'] ?? '') ?? 0,
        ),
      ),
    ],
  );
});
