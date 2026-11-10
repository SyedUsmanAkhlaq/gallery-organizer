/// Domain types for the Categories and People tabs.
library;

class CategorySummary {
  const CategorySummary({
    required this.key,
    required this.label,
    required this.isClothing,
    required this.count,
    this.cover,
  });

  /// `_uncertain` = clothing photos below the confidence threshold.
  final String key;
  final String label;
  final bool isClothing;
  final int count;
  final CoverRef? cover;

  bool get isUncertain => key == '_uncertain';
}

class CoverRef {
  const CoverRef({required this.assetId, required this.width, required this.height});

  final String assetId;
  final int width;
  final int height;
}

/// Normalized square face crop inside the photo (0..1).
class FaceBox {
  const FaceBox(this.left, this.top, this.right, this.bottom);

  final double left;
  final double top;
  final double right;
  final double bottom;

  double get width => right - left;
  double get height => bottom - top;
}

class Person {
  const Person({
    required this.id,
    required this.name,
    required this.hidden,
    required this.photoCount,
    this.cover,
    this.coverBox,
  });

  final int id;
  final String? name;
  final bool hidden;
  final int photoCount;
  final CoverRef? cover;
  final FaceBox? coverBox;

  String get displayName => (name == null || name!.isEmpty) ? 'Unnamed' : name!;
}

class LabelScore {
  const LabelScore(this.key, this.label, this.score);

  final String key;
  final String label;
  final double score;
}

class AssetDetails {
  const AssetDetails({required this.analyzed, required this.labels, required this.people});

  final bool analyzed;
  final List<LabelScore> labels;
  final List<Person> people;
}

class AnalysisSettings {
  const AnalysisSettings({
    required this.clusteringSensitivity,
    required this.clothingThreshold,
    required this.showSinglePhotoPeople,
    required this.useAcceleration,
  });

  final double clusteringSensitivity;
  final double clothingThreshold;
  final bool showSinglePhotoPeople;
  final bool useAcceleration;

  AnalysisSettings copyWith({
    double? clusteringSensitivity,
    double? clothingThreshold,
    bool? showSinglePhotoPeople,
    bool? useAcceleration,
  }) =>
      AnalysisSettings(
        clusteringSensitivity: clusteringSensitivity ?? this.clusteringSensitivity,
        clothingThreshold: clothingThreshold ?? this.clothingThreshold,
        showSinglePhotoPeople: showSinglePhotoPeople ?? this.showSinglePhotoPeople,
        useAcceleration: useAcceleration ?? this.useAcceleration,
      );
}

/// Score as a short percentage, e.g. 0.834 → "83%".
String formatScore(double score) => '${(score * 100).round()}%';
