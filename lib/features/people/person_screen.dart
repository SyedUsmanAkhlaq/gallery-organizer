import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../albums/domain/media.dart';
import '../media/media_grid.dart';
import '../media/media_source.dart';
import '../scan/domain/analysis.dart';
import '../scan/presentation/providers.dart';
import 'face_crop.dart';

/// All photos of one person across every album, with correction controls:
/// rename, merge, hide, and "not this person" (long-press a photo).
class PersonScreen extends ConsumerWidget {
  const PersonScreen({super.key, required this.personId});

  final int personId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Look the person up in the list (includes hidden ones when needed).
    final all = ref.watch(_allPeopleProvider);
    final person = all.value?.where((p) => p.id == personId).firstOrNull;
    final title = person?.displayName ?? 'Person';

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            tooltip: 'Rename',
            icon: const Icon(Icons.edit_outlined),
            onPressed: person == null ? null : () => _rename(context, ref, person),
          ),
          PopupMenuButton<String>(
            onSelected: (v) {
              if (person == null) return;
              switch (v) {
                case 'merge':
                  _merge(context, ref, person);
                case 'hide':
                  _setHidden(context, ref, person, !person.hidden);
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'merge', child: Text('Merge with another person…')),
              PopupMenuItem(
                value: 'hide',
                child: Text(person?.hidden == true ? 'Unhide person' : 'Hide person'),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text(
              'Long-press a photo if it is not this person.',
              style: TextStyle(fontSize: 12),
            ),
          ),
          Expanded(
            child: MediaGrid(
              source: PersonSource(personId),
              showAlbumName: true,
              emptyText: 'No photos for this person.',
              onLongPress: (item) => _notThisPerson(context, ref, item),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _rename(BuildContext context, WidgetRef ref, Person person) async {
    final controller = TextEditingController(text: person.name ?? '');
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Name this person'),
        content: TextField(
          controller: controller,
          autofocus: true,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(hintText: 'Name'),
          onSubmitted: (v) => Navigator.of(context).pop(v),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null) return;
    await ref.read(nativeRepositoryProvider).renamePerson(person.id, name);
    ref.read(analysisEditsProvider.notifier).bump();
  }

  Future<void> _setHidden(BuildContext context, WidgetRef ref, Person person, bool hidden) async {
    await ref.read(nativeRepositoryProvider).setPersonHidden(person.id, hidden);
    ref.read(analysisEditsProvider.notifier).bump();
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(hidden ? 'Hidden from People' : 'Visible again'),
      ));
    }
  }

  Future<void> _merge(BuildContext context, WidgetRef ref, Person person) async {
    final target = await pickPerson(
      context,
      ref,
      title: 'Merge "${person.displayName}" into…',
      excludeId: person.id,
    );
    if (target == null || !context.mounted) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Merge people?'),
        content: Text(
          'All photos of "${person.displayName}" will be moved to '
          '"${target.displayName}".',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Merge')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(nativeRepositoryProvider).mergePeople(person.id, target.id);
    ref.read(analysisEditsProvider.notifier).bump();
    if (context.mounted) context.pushReplacement('/people/${target.id}');
  }

  Future<void> _notThisPerson(BuildContext context, WidgetRef ref, MediaItem item) async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(title: Text('Not this person')),
            ListTile(
              leading: const Icon(Icons.person_remove_outlined),
              title: const Text('Remove from this person'),
              subtitle: const Text('The face becomes its own person'),
              onTap: () => Navigator.of(context).pop('remove'),
            ),
            ListTile(
              leading: const Icon(Icons.swap_horiz),
              title: const Text('Move to another person…'),
              onTap: () => Navigator.of(context).pop('move'),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !context.mounted) return;
    int? moveTo;
    if (choice == 'move') {
      final target = await pickPerson(context, ref, title: 'Move photo to…', excludeId: personId);
      if (target == null) return;
      moveTo = target.id;
    }
    await ref.read(nativeRepositoryProvider).removeFromPerson(personId, item.id, moveTo: moveTo);
    ref.read(analysisEditsProvider.notifier).bump();
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Photo removed from this person')),
      );
    }
  }
}

/// Every person including hidden and single-photo ones (for pickers/titles).
final _allPeopleProvider = FutureProvider.autoDispose<List<Person>>((ref) {
  ref.watch(analysisEditsProvider);
  return ref.watch(nativeRepositoryProvider).people(includeHidden: true, includeSingles: true);
});

/// Bottom sheet listing people to choose from.
Future<Person?> pickPerson(
  BuildContext context,
  WidgetRef ref, {
  required String title,
  int? excludeId,
}) async {
  final people = (await ref.read(nativeRepositoryProvider).people(includeHidden: true, includeSingles: true))
      .where((p) => p.id != excludeId)
      .toList();
  if (!context.mounted) return null;
  return showModalBottomSheet<Person>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      builder: (context, scroll) => Column(
        children: [
          ListTile(title: Text(title)),
          Expanded(
            child: people.isEmpty
                ? const Center(child: Text('No other people yet.'))
                : ListView.builder(
                    controller: scroll,
                    itemCount: people.length,
                    itemBuilder: (context, i) {
                      final p = people[i];
                      return ListTile(
                        leading: PersonAvatar(person: p, size: 44),
                        title: Text(p.displayName),
                        subtitle: Text('${p.photoCount} photos${p.hidden ? ' · hidden' : ''}'),
                        onTap: () => Navigator.of(context).pop(p),
                      );
                    },
                  ),
          ),
        ],
      ),
    ),
  );
}
