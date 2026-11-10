import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router.dart';
import '../../bridge/native_api.g.dart';
import '../../core/tap_counter.dart';
import '../scan/domain/analysis.dart';
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

  Future<void> _deleteAll(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete all analysis data?'),
        content: const Text(
          'This deletes clothing labels, face data, people and their names. '
          'It cannot be undone. Photos stay untouched. Scanning stays paused '
          'until you start it again.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(nativeRepositoryProvider).deleteAllAnalysisData();
    ref.read(analysisEditsProvider.notifier).bump();
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('All analysis data deleted')),
      );
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
          const _Header('Analysis'),
          const _AnalysisSettings(),
          const _Header('Privacy'),
          ListTile(
            leading: const Icon(Icons.delete_forever_outlined),
            title: const Text('Delete all analysis data'),
            subtitle: const Text(
              'Removes categories, faces, people and names stored by this app. '
              'Your photos are not touched.',
            ),
            onTap: () => _deleteAll(context),
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


class _AnalysisSettings extends ConsumerStatefulWidget {
  const _AnalysisSettings();

  @override
  ConsumerState<_AnalysisSettings> createState() => _AnalysisSettingsState();
}

class _AnalysisSettingsState extends ConsumerState<_AnalysisSettings> {
  /// Local copy while dragging sliders; saved on release.
  AnalysisSettings? _draft;

  Future<void> _save(AnalysisSettings next) async {
    setState(() => _draft = next);
    await ref.read(nativeRepositoryProvider).saveSettings(next);
    ref.read(analysisEditsProvider.notifier).bump();
  }

  Future<void> _saveSensitivity(AnalysisSettings next) async {
    setState(() => _draft = next);
    final messenger = ScaffoldMessenger.of(context);
    final merged = await ref
        .read(nativeRepositoryProvider)
        .setClusteringSensitivity(next.clusteringSensitivity);
    ref.read(analysisEditsProvider.notifier).bump();
    messenger.showSnackBar(SnackBar(
      content: Text(merged == 0
          ? 'Sensitivity saved'
          : 'Sensitivity saved · merged $merged groups'),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(analysisSettingsProvider);
    final s = _draft ?? async.value;
    if (s == null) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: LinearProgressIndicator(),
      );
    }
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ListTile(
          title: const Text('Face grouping'),
          subtitle: Text(
            'Stricter makes fewer mistakes but may split a person into '
            'several. Looser merges more. Applies to new photos; looser also '
            'merges existing groups right away.',
            style: theme.textTheme.bodySmall,
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Row(
            children: [
              const Text('Strict'),
              Expanded(
                child: Slider(
                  value: s.clusteringSensitivity,
                  divisions: 10,
                  label: s.clusteringSensitivity.toStringAsFixed(1),
                  onChanged: (v) => setState(
                    () => _draft = s.copyWith(clusteringSensitivity: v),
                  ),
                  onChangeEnd: (v) =>
                      _saveSensitivity(s.copyWith(clusteringSensitivity: v)),
                ),
              ),
              const Text('Loose'),
            ],
          ),
        ),
        ListTile(
          title: const Text('Category confidence'),
          subtitle: Text(
            'A photo joins a clothing category only if the model is at least '
            '${formatScore(s.clothingThreshold)} sure. Lower shows more '
            'photos (more mistakes). No re-scan needed.',
            style: theme.textTheme.bodySmall,
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: Slider(
            value: s.clothingThreshold.clamp(0.2, 0.9),
            min: 0.2,
            max: 0.9,
            divisions: 14,
            label: formatScore(s.clothingThreshold),
            onChanged: (v) =>
                setState(() => _draft = s.copyWith(clothingThreshold: v)),
            onChangeEnd: (v) => _save(s.copyWith(clothingThreshold: v)),
          ),
        ),
        SwitchListTile(
          title: const Text('Show people seen in only one photo'),
          value: s.showSinglePhotoPeople,
          onChanged: (v) => _save(s.copyWith(showSinglePhotoPeople: v)),
        ),
        SwitchListTile(
          title: const Text('Hardware acceleration'),
          subtitle: const Text(
            'Picks the fastest of NNAPI / XNNPACK / CPU. Turn off if '
            'analysis crashes.',
          ),
          value: s.useAcceleration,
          onChanged: (v) => _save(s.copyWith(useAcceleration: v)),
        ),
      ],
    );
  }
}
