/// Formats a video duration in seconds as `m:ss` or `h:mm:ss`.
String formatDuration(int totalSeconds) {
  final secs = totalSeconds < 0 ? 0 : totalSeconds;
  final h = secs ~/ 3600;
  final m = (secs % 3600) ~/ 60;
  final s = secs % 60;
  String two(int v) => v.toString().padLeft(2, '0');
  return h > 0 ? '$h:${two(m)}:${two(s)}' : '$m:${two(s)}';
}
