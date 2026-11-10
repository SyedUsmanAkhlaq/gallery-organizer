import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/router.dart';
import '../../albums/domain/media.dart';
import '../domain/analysis.dart';
import 'providers.dart';

/// Bottom sheet: top-3 clothing labels with scores and the people found in
/// this photo. Useful for checking/tuning thresholds.
Future<void> showAssetInfo(BuildContext context, MediaItem item) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (context) => _AssetInfo(item: item),
  );
}

class _AssetInfo extends ConsumerStatefulWidget {
  const _AssetInfo({required this.item});

  final MediaItem item;

  @override
  ConsumerState<_AssetInfo> createState() => _AssetInfoState();
}

class _AssetInfoState extends ConsumerState<_AssetInfo> {
  late final Future<AssetDetails> _details =
      ref.read(nativeRepositoryProvider).details(widget.item.id);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: FutureBuilder<AssetDetails>(
          future: _details,
          builder: (context, snap) {
            final d = snap.data;
            if (d == null) {
              return Padding(
                padding: const EdgeInsets.all(24),
                child: Center(
                  child: snap.hasError
                      ? Text('Failed: ${snap.error}')
                      : const CircularProgressIndicator(),
                ),
              );
            }
            if (!d.analyzed) {
              return const Padding(
                padding: EdgeInsets.all(16),
                child: Text('Not analyzed yet. It will be after the next scan.'),
              );
            }
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Clothing (top 3)', style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                for (final l in d.labels)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 3),
                    child: Row(
                      children: [
                        SizedBox(width: 130, child: Text(l.label)),
                        Expanded(
                          child: LinearProgressIndicator(value: l.score),
                        ),
                        SizedBox(
                          width: 48,
                          child: Text(
                            formatScore(l.score),
                            textAlign: TextAlign.end,
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 16),
                Text('People', style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                if (d.people.isEmpty)
                  const Text('No recognizable faces.')
                else
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final p in d.people)
                        ActionChip(
                          avatar: const Icon(Icons.person, size: 18),
                          label: Text(p.displayName),
                          onPressed: () {
                            final router = GoRouter.of(context);
                            Navigator.of(context).pop();
                            router.push(Routes.person(p.id));
                          },
                        ),
                    ],
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
