import 'package:flutter/material.dart';

import '../../media/media_grid.dart';
import '../../media/media_source.dart';

class AlbumScreen extends StatelessWidget {
  const AlbumScreen({super.key, required this.albumId, required this.title});

  final String albumId;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: MediaGrid(
        source: AlbumSource(albumId),
        emptyText: 'This album is empty.',
      ),
    );
  }
}
