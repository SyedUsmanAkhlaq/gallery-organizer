import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/gallery_access.dart';
import 'permission_controller.dart';
import 'permission_gate.dart';

/// Shown on top of the album list when Android 14 partial access is active.
class LimitedAccessBanner extends ConsumerWidget {
  const LimitedAccessBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final access = ref.watch(permissionControllerProvider);
    if (access != GalleryAccess.limited) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      color: theme.colorScheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'You allowed access to selected photos only. Categories and '
              'People will only cover these items.',
              style: theme.textTheme.bodyMedium,
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => ref
                      .read(permissionControllerProvider.notifier)
                      .changeSelection(),
                  child: const Text('Change selection'),
                ),
                TextButton(
                  onPressed: () => openPermissionSettings(context),
                  child: const Text('Grant full access'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
