import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/native_api.g.dart';
import '../albums/presentation/providers.dart';
import '../scan/domain/scan_snapshot.dart';
import '../scan/presentation/providers.dart';
import '../settings/settings_screen.dart';

/// Hidden diagnostics (Settings → tap the version 7 times). Shows only
/// counts, timings and sanitized errors; never paths or face data.
class DebugScreen extends ConsumerStatefulWidget {
  const DebugScreen({super.key});

  @override
  ConsumerState<DebugScreen> createState() => _DebugScreenState();
}

class _DebugScreenState extends ConsumerState<DebugScreen> {
  late Future<DebugInfoMsg> _info;
  late Future<List<ErrorEntryMsg>> _errors;
  IdCheckMsg? _idCheck;
  String? _idCheckError;
  bool _checking = false;

  GalleryHostApi get _api => ref.read(nativeRepositoryProvider).api;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _info = _api.getDebugInfo();
    _errors = _api.getRecentErrors();
  }

  void _reload() => setState(_load);

  Future<void> _checkIds() async {
    setState(() {
      _checking = true;
      _idCheckError = null;
    });
    try {
      final media = ref.read(mediaRepositoryProvider);
      final albums = await media.fetchAlbums();
      final all = albums.where((a) => a.isAll).firstOrNull ?? albums.firstOrNull;
      final ids = <String>[
        if (all != null)
          for (final item in await media.fetchPage(all.id, page: 0, size: 50))
            item.id,
      ];
      final result = await _api.verifyAssetIds(ids);
      if (mounted) setState(() => _idCheck = result);
    } catch (e) {
      if (mounted) setState(() => _idCheckError = '$e');
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  Future<void> _copyErrors() async {
    final errors = await _errors;
    final text = errors
        .map((e) => '=== ${_time(e.timestampMillis)} [${e.source}] '
            '${e.message}\n${e.stack ?? ''}')
        .join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Copied ${errors.length} errors')),
      );
    }
  }

  Future<void> _clearErrors() async {
    await _api.clearErrors();
    _reload();
  }

  void _testError() {
    // Goes through PlatformDispatcher.onError → native log.
    unawaited(Future<void>.microtask(
      () => throw StateError('Test error from debug screen'),
    ));
    Future<void>.delayed(const Duration(milliseconds: 600), () {
      if (mounted) _reload();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Debug'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _reload,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 32),
        children: [
          const _Section('Scan (live)'),
          const ScanControlTile(),
          const _LiveScanDetails(),
          const _Section('Stages'),
          FutureBuilder<DebugInfoMsg>(
            future: _info,
            builder: (context, snap) {
              final info = snap.data;
              if (info == null) return _futureState(snap);
              return Column(
                children: [
                  for (final s in info.stages) _StageRow(stats: s),
                  const _Section('Database'),
                  _kv('Indexed images', '${info.indexedImages}'),
                  _kv('Indexed videos', '${info.indexedVideos}'),
                  _kv('Database size', _bytes(info.dbSizeBytes)),
                  _kv('WorkManager state', info.workState),
                ],
              );
            },
          ),
          const _Section('Categories'),
          _kv('Counts per category', 'available in milestone 3'),
          const _Section('Faces'),
          _kv('Faces / clusters', 'available in milestone 4'),
          const _Section('Asset ID check'),
          ListTile(
            title: Text(_idCheck == null
                ? 'Compare photo_manager IDs with MediaStore _ID'
                : '${_idCheck!.matched} / ${_idCheck!.checked} IDs match'),
            subtitle: _idCheckError != null
                ? Text(_idCheckError!)
                : (_idCheck != null && _idCheck!.missing.isNotEmpty)
                    ? Text('Missing: ${_idCheck!.missing.join(', ')}')
                    : null,
            trailing: _checking
                ? const SizedBox.square(
                    dimension: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : OutlinedButton(
                    onPressed: _checkIds,
                    child: const Text('Verify'),
                  ),
          ),
          FutureBuilder<List<ErrorEntryMsg>>(
            future: _errors,
            builder: (context, snap) {
              final errors = snap.data;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _Section('Errors (${errors?.length ?? '…'}, newest first)'),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        FilledButton.tonalIcon(
                          onPressed: _api.shareLog,
                          icon: const Icon(Icons.share),
                          label: const Text('Share log'),
                        ),
                        OutlinedButton.icon(
                          onPressed: _copyErrors,
                          icon: const Icon(Icons.copy),
                          label: const Text('Copy'),
                        ),
                        OutlinedButton(
                          onPressed: _clearErrors,
                          child: const Text('Clear'),
                        ),
                        TextButton(
                          onPressed: _testError,
                          child: const Text('Log test error'),
                        ),
                      ],
                    ),
                  ),
                  if (errors == null)
                    _futureState(snap)
                  else if (errors.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('No errors recorded.'),
                    )
                  else
                    for (final e in errors) _ErrorTile(entry: e),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _LiveScanDetails extends ConsumerWidget {
  const _LiveScanDetails();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(scanStatusProvider).value ?? ScanSnapshot.unknown;
    return Column(
      children: [
        _kv('State', s.state.name),
        _kv('Stage', stageLabel(s.stage)),
        _kv('Progress', '${s.scanned} / ${s.total}'),
        _kv('Avg per item', '${s.avgMsPerImage.toStringAsFixed(2)} ms'),
        _kv('Current asset ID', s.currentAssetId ?? '—'),
        if (s.message != null) _kv('Message', s.message!),
      ],
    );
  }
}

class _StageRow extends StatelessWidget {
  const _StageRow({required this.stats});

  final StageStatsMsg stats;

  @override
  Widget build(BuildContext context) {
    final name = switch (stats.stage) {
      ScanStage.indexing => 'Indexing',
      ScanStage.clothing => 'Clothing (M3)',
      ScanStage.faces => 'Faces (M4)',
    };
    final last = stats.lastRunAtMillis;
    return ListTile(
      dense: true,
      title: Text(name),
      subtitle: Text(
        last == null
            ? 'not run yet'
            : 'processed ${stats.processed} · '
                '${stats.avgMsPerImage.toStringAsFixed(2)} ms/item · '
                'delegate ${stats.delegate ?? 'n/a'} · '
                'last ${_time(last)}',
      ),
    );
  }
}

class _ErrorTile extends StatelessWidget {
  const _ErrorTile({required this.entry});

  final ErrorEntryMsg entry;

  @override
  Widget build(BuildContext context) {
    return ExpansionTile(
      title: Text(entry.message, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text('${_time(entry.timestampMillis)} · ${entry.source}'),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText(
          '${entry.message}\n\n${entry.stack ?? '(no stack trace)'}',
          style: const TextStyle(fontFamily: 'monospace', fontSize: 11),
        ),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(text, style: Theme.of(context).textTheme.titleSmall),
    );
  }
}

Widget _kv(String key, String value) => Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 150, child: Text(key)),
          Expanded(child: SelectableText(value)),
        ],
      ),
    );

Widget _futureState(AsyncSnapshot<Object?> snap) => Padding(
      padding: const EdgeInsets.all(16),
      child: snap.hasError
          ? Text('Failed: ${snap.error}')
          : const LinearProgressIndicator(),
    );

String _time(int millis) => DateTime.fromMillisecondsSinceEpoch(millis)
    .toLocal()
    .toString()
    .substring(0, 19);

String _bytes(int b) {
  if (b < 1024) return '$b B';
  if (b < 1024 * 1024) return '${(b / 1024).toStringAsFixed(1)} KB';
  return '${(b / 1024 / 1024).toStringAsFixed(1)} MB';
}
