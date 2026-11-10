import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/scan_snapshot.dart';
import 'providers.dart';

/// Compact scan status shown on top of the Categories and People tabs.
class ScanBanner extends ConsumerWidget {
  const ScanBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(scanStatusProvider).value;
    if (s == null) return const SizedBox.shrink();
    final repo = ref.read(nativeRepositoryProvider);
    final theme = Theme.of(context);
    if (s.isRunning) {
      final eta = s.eta;
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Analyzing: ${describeSnapshot(s)}'
              '${eta == null ? '' : ' · ~${formatEta(eta)} left'}',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: 6),
            LinearProgressIndicator(value: s.fraction),
          ],
        ),
      );
    }
    if (s.state == ScanRunState.idle || s.state == ScanRunState.paused) {
      return Card(
        margin: const EdgeInsets.all(12),
        child: ListTile(
          title: Text(s.state == ScanRunState.paused
              ? 'Analysis paused'
              : 'Photos not analyzed yet'),
          subtitle: const Text('Runs on this phone, in the background.'),
          trailing: FilledButton(
            onPressed: repo.startScan,
            child: Text(s.state == ScanRunState.paused ? 'Resume' : 'Start'),
          ),
        ),
      );
    }
    if (s.state == ScanRunState.failed) {
      return Card(
        margin: const EdgeInsets.all(12),
        child: ListTile(
          title: const Text('Analysis stopped with an error'),
          subtitle: Text(s.message ?? 'See Settings → Version (tap 7×) → Errors'),
          trailing: TextButton(onPressed: repo.startScan, child: const Text('Retry')),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}
