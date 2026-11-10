import 'package:flutter/material.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:photo_manager_image_provider/photo_manager_image_provider.dart';

import '../scan/domain/analysis.dart';

/// Thumbnail size used to cut face crops from (faces are 10-40% of a photo).
const int kFaceSourcePx = 600;

/// Shows the face region of a photo. The box is a pixel-square crop stored
/// in normalized coordinates, so scaling the whole image by 1/box.width and
/// 1/box.height keeps the aspect ratio.
class FaceCrop extends StatelessWidget {
  const FaceCrop({super.key, required this.cover, required this.box});

  final CoverRef cover;
  final FaceBox box;

  @override
  Widget build(BuildContext context) {
    final entity = AssetEntity(
      id: cover.assetId,
      typeInt: AssetType.image.index,
      width: cover.width,
      height: cover.height,
    );
    final image = AssetEntityImageProvider(
      entity,
      isOriginal: false,
      thumbnailSize: const ThumbnailSize.square(kFaceSourcePx),
      thumbnailFormat: ThumbnailFormat.jpeg,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final w = constraints.maxWidth;
        final h = constraints.maxHeight;
        final bw = box.width <= 0 ? 1.0 : box.width;
        final bh = box.height <= 0 ? 1.0 : box.height;
        final fullW = w / bw;
        final fullH = h / bh;
        return ClipRect(
          child: OverflowBox(
            alignment: Alignment.topLeft,
            minWidth: 0,
            minHeight: 0,
            maxWidth: double.infinity,
            maxHeight: double.infinity,
            child: Transform.translate(
              offset: Offset(-box.left * fullW, -box.top * fullH),
              child: SizedBox(
                width: fullW,
                height: fullH,
                child: Image(
                  image: image,
                  fit: BoxFit.fill,
                  gaplessPlayback: true,
                  errorBuilder: (context, error, stackTrace) =>
                      const ColoredBox(color: Color(0xFF222222)),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Round person avatar (face crop, or an icon when there is none).
class PersonAvatar extends StatelessWidget {
  const PersonAvatar({super.key, required this.person, this.size = 48});

  final Person person;
  final double size;

  @override
  Widget build(BuildContext context) {
    final cover = person.cover;
    final box = person.coverBox;
    return ClipOval(
      child: SizedBox.square(
        dimension: size,
        child: cover != null && box != null
            ? FaceCrop(cover: cover, box: box)
            : ColoredBox(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: Icon(Icons.person, size: size * 0.6),
              ),
      ),
    );
  }
}
