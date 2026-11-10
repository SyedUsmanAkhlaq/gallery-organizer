import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router.dart';
import '../scan/domain/analysis.dart';
import '../scan/presentation/providers.dart';
import '../scan/presentation/scan_banner.dart';
import 'face_crop.dart';

class PeopleScreen extends ConsumerWidget {
  const PeopleScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(peopleProvider);
    final showHidden = ref.watch(showHiddenPeopleProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('People'),
        actions: [
          IconButton(
            tooltip: showHidden ? 'Hide hidden people' : 'Show hidden people',
            icon: Icon(showHidden ? Icons.visibility : Icons.visibility_off),
            onPressed: () => ref.read(showHiddenPeopleProvider.notifier).toggle(),
          ),
        ],
      ),
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
                    child: Text('Could not load people: $e'),
                  ),
                ),
              ],
              data: (people) => people.isEmpty
                  ? const [
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: EdgeInsets.all(24),
                          child: Text(
                            'No people yet. Faces are grouped while photos '
                            'are analyzed; a person appears once they are in '
                            'at least two photos (changeable in Settings).',
                          ),
                        ),
                      ),
                    ]
                  : [
                      SliverPadding(
                        padding: const EdgeInsets.all(12),
                        sliver: SliverGrid.builder(
                          gridDelegate:
                              const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 3,
                            mainAxisSpacing: 16,
                            crossAxisSpacing: 12,
                            childAspectRatio: 0.72,
                          ),
                          itemCount: people.length,
                          itemBuilder: (context, i) => _PersonCard(person: people[i]),
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

class _PersonCard extends StatelessWidget {
  const _PersonCard({required this.person});

  final Person person;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => context.push(Routes.person(person.id)),
      child: Opacity(
        opacity: person.hidden ? 0.5 : 1,
        child: Column(
          children: [
            LayoutBuilder(
              builder: (context, c) =>
                  PersonAvatar(person: person, size: c.maxWidth),
            ),
            const SizedBox(height: 6),
            Text(
              person.displayName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium,
            ),
            Text('${person.photoCount} photos', style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}
