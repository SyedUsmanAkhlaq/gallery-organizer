/// Pure paging logic shared by every paged grid (albums now, categories and
/// people later). No Flutter imports, so it is unit-tested directly.
library;

/// Items fetched per page.
const int kPageSize = 60;

/// Start fetching the next page when the user is within this many items of
/// the end of what is loaded.
const int kPrefetchThreshold = 12;

class PagedState<T> {
  const PagedState({
    required this.items,
    required this.nextPage,
    required this.hasMore,
    required this.isLoading,
    this.error,
  });

  factory PagedState.initial() => PagedState<T>(
        items: List<T>.unmodifiable(const []),
        nextPage: 0,
        hasMore: true,
        isLoading: false,
      );

  final List<T> items;
  final int nextPage;
  final bool hasMore;
  final bool isLoading;
  final Object? error;

  PagedState<T> loading() => PagedState<T>(
        items: items,
        nextPage: nextPage,
        hasMore: hasMore,
        isLoading: true,
      );

  PagedState<T> failed(Object error) => PagedState<T>(
        items: items,
        nextPage: nextPage,
        hasMore: hasMore,
        isLoading: false,
        error: error,
      );
}

/// Appends a freshly fetched [page]. A page shorter than [pageSize] means the
/// source is exhausted.
PagedState<T> appendPage<T>(PagedState<T> state, List<T> page, int pageSize) {
  return PagedState<T>(
    items: List<T>.unmodifiable([...state.items, ...page]),
    nextPage: state.nextPage + 1,
    hasMore: page.length >= pageSize,
    isLoading: false,
  );
}

/// Whether showing the item at [index] should trigger loading the next page.
bool shouldPrefetch<T>(
  PagedState<T> state,
  int index, {
  int threshold = kPrefetchThreshold,
}) {
  if (!state.hasMore || state.isLoading || state.error != null) return false;
  return index >= state.items.length - threshold;
}
