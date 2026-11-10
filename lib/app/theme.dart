import 'package:flutter/material.dart';

/// Black, neutral (monochrome) Material 3 theme. The app always uses it,
/// regardless of the system light/dark setting.
ThemeData buildBlackTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: Colors.grey,
    brightness: Brightness.dark,
    dynamicSchemeVariant: DynamicSchemeVariant.monochrome,
  ).copyWith(
    surface: Colors.black,
    surfaceContainerLowest: Colors.black,
    surfaceContainerLow: const Color(0xFF0A0A0A),
    surfaceContainer: const Color(0xFF121212),
    surfaceContainerHigh: const Color(0xFF1A1A1A),
    surfaceContainerHighest: const Color(0xFF222222),
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: Colors.black,
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.black,
      surfaceTintColor: Colors.transparent,
    ),
    navigationBarTheme: const NavigationBarThemeData(
      backgroundColor: Color(0xFF0A0A0A),
      surfaceTintColor: Colors.transparent,
    ),
    cardTheme: const CardThemeData(
      color: Color(0xFF151515),
      surfaceTintColor: Colors.transparent,
    ),
  );
}
