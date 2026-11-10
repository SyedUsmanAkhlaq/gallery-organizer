import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router.dart';
import '../../bridge/native_api.g.dart';
import '../../core/tap_counter.dart';
import '../scan/domain/scan_snapshot.dart';
import '../scan/presentation/providers.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final _taps = TapCounter();
  late final Future<AppInfoMsg> _appInfo =
      ref.read(nativeRepositoryProvider).api.getAppInfo();

  void _onVersionTap() {
    final messenger = ScaffoldMessenger.of(context);
    if (_taps.tap(DateTime.now())) {
      messenger.hideCurrentSnackBar();
      context.push(Routes.debug);
      return;
    }
    if (_taps.remaining <= 3) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          content: Text('${_taps.remaining} more taps for the debug screen'),
          duration: const Duration(milliseconds: 700),
        ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          const _Header('Library scan'),
          const ScanControlTile(),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
            child: Text(
              'The scan runs in the background with a notification and '
              'continues if you close the app. Everything is processed on '
              'this phone.',
              style: theme.textTheme.bodySmall,
            ),
          ),
          const _Header('About'),
          FutureBuilder<AppInfoMsg>(
            future: _appInfo,
            builder: (context, snap) {
              final info = snap.data;
              return ListTile(
                title: const Text('Version'),
                subtitle: Text(
                  info == null
                      ? '…'
                      : '${info.versionName} (${info.versionCode})',
                ),
                onTap: _onVersionTap,
              );
            },
          ),
        ],
      ),
    );
  }
}

/// Scan status line, progress bar and Start/Pause button.
class ScanControlTile extends ConsumerWidget {
  const ScanControlTile({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repo = ref.read(nativeRepositoryProvider);
    final async = ref.watch(scanStatusProvider);
    final s = async.value ?? ScanSnapshot.unknown;
    final eta = s.eta;
    final details = <String>[
      if (s.avgMsPerImage > 0) '${s.avgMsPerImage.toStringAsFixed(1)} ms/item',
      if (eta != null) '~${formatEta(eta)} left',
      if (async.hasError) 'Status unavailable: ${async.error}',
    ];
    return Column(
      children: [
        ListTile(
          title: Text(describeSnapshot(s)),
          subtitle: details.isEmpty ? null : Text(details.join(' · ')),
          trailing: s.isRunning
              ? OutlinedButton.icon(
                  onPressed: repo.pauseScan,
                  icon: const Icon(Icons.pause),
                  label: const Text('Pause'),
                )
              : FilledButton.icon(
                  onPressed: repo.startScan,
                  icon: const Icon(Icons.play_arrow),
                  label: Text(
                    s.state == ScanRunState.paused ? 'Resume' : 'Scan',
                  ),
                ),
        ),
        if (s.isRunning)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: LinearProgressIndicator(value: s.fraction),
          ),
      ],
    );
  }
}

class _Header extends StatelessWidget {
  const _Header(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: Theme.of(context).colorScheme.primary,
            ),
      ),
    );
  }
}
