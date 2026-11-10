import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router.dart';
import '../albums/domain/media.dart';
import '../albums/presentation/media_thumbnail.dart';
import '../media/media_grid.dart';
import '../media/media_source.dart';
import '../scan/domain/analysis.dart';
import '../scan/presentation/providers.dart';
import '../scan/presentation/scan_banner.dart';

class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(categorySummariesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Categories')),
      body: RefreshIndicator(
        onRefresh: () async => ref.read(analysisEditsProvider.notifier).bump(),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            const SliverToBoxAdapter(child: ScanBanner()),
            ...async.when<List<Widget>>(
              loading: () => const [
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: CircularProgressIndicator()),
                ),
              ],
              error: (e, _) => [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text('Could not load categories: $e'),
                  ),
                ),
              ],
              data: (list) => list.isEmpty
                  ? const [
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: EdgeInsets.all(24),
                          child: Text(
                            'The clothing model is not available in this '
                            'build. See the debug screen for details.',
                          ),
                        ),
                      ),
                    ]
                  : [
                      SliverPadding(
                        padding: const EdgeInsets.all(8),
                        sliver: SliverGrid.builder(
                          gridDelegate:
                              const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 2,
                            mainAxisSpacing: 8,
                            crossAxisSpacing: 8,
                            childAspectRatio: 0.82,
                          ),
                          itemCount: list.length,
                          itemBuilder: (context, i) =>
                              _CategoryCard(summary: list[i]),
                        ),
                      ),
                    ],
            ),
          ],
        ),
      ),
    );
  }
}

class _CategoryCard extends StatelessWidget {
  const _CategoryCard({required this.summary});

  final CategorySummary summary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cover = summary.cover;
    final enabled = summary.count > 0;
    final subtitle = summary.isUncertain
        ? 'below confidence'
        : summary.isClothing
            ? null
            : 'landscapes, screenshots…';
    return Opacity(
      opacity: enabled ? 1 : 0.45,
      child: Card(
        clipBehavior: Clip.antiAlias,
        margin: EdgeInsets.zero,
        child: InkWell(
          onTap: enabled
              ? () => context.push(Routes.category(summary.key, summary.label))
              : null,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: cover == null
                    ? ColoredBox(
                        color: theme.colorScheme.surfaceContainerHighest,
                        child: Icon(
                          summary.isClothing
                              ? Icons.checkroom_outlined
                              : Icons.landscape_outlined,
                          size: 40,
                        ),
                      )
                    : MediaThumbnail(
                        item: MediaItem(
                          id: cover.assetId,
                          kind: MediaKind.image,
                          width: cover.width,
                          height: cover.height,
                        ),
                      ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(summary.label, style: theme.textTheme.titleSmall),
                    Text(
                      '${summary.count} photos'
                      '${subtitle == null ? '' : ' · $subtitle'}',
                      style: theme.textTheme.bodySmall,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class CategoryScreen extends StatelessWidget {
  const CategoryScreen({super.key, required this.categoryKey, required this.title});

  final String categoryKey;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: MediaGrid(
        source: CategorySource(categoryKey),
        showScore: true,
        emptyText: 'No photos in this category yet.',
      ),
    );
  }
}
