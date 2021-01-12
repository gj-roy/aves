import 'dart:math';

import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/image_entry.dart';
import 'package:aves/model/settings/screen_on.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/utils/change_notifier.dart';
import 'package:aves/widgets/collection/collection_page.dart';
import 'package:aves/widgets/common/gesture_area_protector.dart';
import 'package:aves/widgets/common/magnifier/pan/scroll_physics.dart';
import 'package:aves/widgets/viewer/entry_action_delegate.dart';
import 'package:aves/widgets/viewer/entry_scroller.dart';
import 'package:aves/widgets/viewer/info/info_page.dart';
import 'package:aves/widgets/viewer/info/notifications.dart';
import 'package:aves/widgets/viewer/multipage.dart';
import 'package:aves/widgets/viewer/overlay/bottom.dart';
import 'package:aves/widgets/viewer/overlay/panorama.dart';
import 'package:aves/widgets/viewer/overlay/top.dart';
import 'package:aves/widgets/viewer/overlay/video.dart';
import 'package:aves/widgets/viewer/visual/state.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter_ijkplayer/flutter_ijkplayer.dart';
import 'package:provider/provider.dart';
import 'package:screen/screen.dart';
import 'package:tuple/tuple.dart';

class EntryViewerStack extends StatefulWidget {
  final CollectionLens collection;
  final ImageEntry initialEntry;

  const EntryViewerStack({
    Key key,
    this.collection,
    this.initialEntry,
  }) : super(key: key);

  @override
  _EntryViewerStackState createState() => _EntryViewerStackState();
}

class _EntryViewerStackState extends State<EntryViewerStack> with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  final ValueNotifier<ImageEntry> _entryNotifier = ValueNotifier(null);
  int _currentHorizontalPage;
  ValueNotifier<int> _currentVerticalPage;
  PageController _horizontalPager, _verticalPager;
  final AChangeNotifier _verticalScrollNotifier = AChangeNotifier();
  final ValueNotifier<bool> _overlayVisible = ValueNotifier(true);
  AnimationController _overlayAnimationController;
  Animation<double> _topOverlayScale, _bottomOverlayScale;
  Animation<Offset> _bottomOverlayOffset;
  EdgeInsets _frozenViewInsets, _frozenViewPadding;
  EntryActionDelegate _actionDelegate;
  final List<Tuple2<String, IjkMediaController>> _videoControllers = [];
  final List<Tuple2<String, MultiPageController>> _multiPageControllers = [];
  final List<Tuple2<String, ValueNotifier<ViewState>>> _viewStateNotifiers = [];

  CollectionLens get collection => widget.collection;

  bool get hasCollection => collection != null;

  List<ImageEntry> get entries => hasCollection ? collection.sortedEntries : [widget.initialEntry];

  static const int transitionPage = 0;

  static const int imagePage = 1;

  static const int infoPage = 2;

  @override
  void initState() {
    super.initState();
    final entry = widget.initialEntry;
    _entryNotifier.value = entry;
    _currentHorizontalPage = max(0, entries.indexOf(entry));
    _currentVerticalPage = ValueNotifier(imagePage);
    _horizontalPager = PageController(initialPage: _currentHorizontalPage);
    _verticalPager = PageController(initialPage: _currentVerticalPage.value)..addListener(_onVerticalPageControllerChange);
    _overlayAnimationController = AnimationController(
      duration: Durations.viewerOverlayAnimation,
      vsync: this,
    );
    _topOverlayScale = CurvedAnimation(
      parent: _overlayAnimationController,
      // a little bounce at the top
      curve: Curves.easeOutBack,
    );
    _bottomOverlayScale = CurvedAnimation(
      parent: _overlayAnimationController,
      // no bounce at the bottom, to avoid video controller displacement
      curve: Curves.easeOutQuad,
    );
    _bottomOverlayOffset = Tween(begin: Offset(0, 1), end: Offset(0, 0)).animate(CurvedAnimation(
      parent: _overlayAnimationController,
      curve: Curves.easeOutQuad,
    ));
    _overlayVisible.addListener(_onOverlayVisibleChange);
    _actionDelegate = EntryActionDelegate(
      collection: collection,
      showInfo: () => _goToVerticalPage(infoPage),
    );
    _initViewStateControllers();
    _registerWidget(widget);
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _initOverlay());
    if (settings.keepScreenOn == KeepScreenOn.viewerOnly) {
      Screen.keepOn(true);
    }
  }

  @override
  void didUpdateWidget(covariant EntryViewerStack oldWidget) {
    super.didUpdateWidget(oldWidget);
    _unregisterWidget(oldWidget);
    _registerWidget(widget);
  }

  @override
  void dispose() {
    _overlayAnimationController.dispose();
    _overlayVisible.removeListener(_onOverlayVisibleChange);
    _videoControllers.forEach((kv) => kv.item2.dispose());
    _videoControllers.clear();
    _multiPageControllers.forEach((kv) => kv.item2.dispose());
    _multiPageControllers.clear();
    _verticalPager.removeListener(_onVerticalPageControllerChange);
    WidgetsBinding.instance.removeObserver(this);
    _unregisterWidget(widget);
    super.dispose();
  }

  void _registerWidget(EntryViewerStack widget) {
    widget.collection?.addListener(_onCollectionChange);
  }

  void _unregisterWidget(EntryViewerStack widget) {
    widget.collection?.removeListener(_onCollectionChange);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      _pauseVideoControllers();
    }
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () {
        if (_currentVerticalPage.value == infoPage) {
          // back from info to image
          _goToVerticalPage(imagePage);
          return SynchronousFuture(false);
        }
        _onLeave();
        return SynchronousFuture(true);
      },
      child: NotificationListener(
        onNotification: (notification) {
          if (notification is FilterNotification) {
            _goToCollection(notification.filter);
          } else if (notification is ViewStateNotification) {
            _updateViewState(notification.uri, notification.viewState);
          }
          return false;
        },
        child: Stack(
          children: [
            ViewerVerticalPageView(
              collection: collection,
              entryNotifier: _entryNotifier,
              videoControllers: _videoControllers,
              multiPageControllers: _multiPageControllers,
              verticalPager: _verticalPager,
              horizontalPager: _horizontalPager,
              onVerticalPageChanged: _onVerticalPageChanged,
              onHorizontalPageChanged: _onHorizontalPageChanged,
              onImageTap: () => _overlayVisible.value = !_overlayVisible.value,
              onImagePageRequested: () => _goToVerticalPage(imagePage),
              onViewDisposed: (uri) => _updateViewState(uri, null),
            ),
            _buildTopOverlay(),
            _buildBottomOverlay(),
            BottomGestureAreaProtector(),
          ],
        ),
      ),
    );
  }

  void _updateViewState(String uri, ViewState viewState) {
    final viewStateNotifier = _viewStateNotifiers.firstWhere((kv) => kv.item1 == uri, orElse: () => null)?.item2;
    viewStateNotifier?.value = viewState ?? ViewState.zero;
  }

  Widget _buildTopOverlay() {
    final child = ValueListenableBuilder<ImageEntry>(
      valueListenable: _entryNotifier,
      builder: (context, entry, child) {
        if (entry == null) return SizedBox.shrink();

        final multiPageController = _getMultiPageController(entry);

        final viewStateNotifier = _viewStateNotifiers.firstWhere((kv) => kv.item1 == entry.uri, orElse: () => null)?.item2;
        return ViewerTopOverlay(
          entry: entry,
          scale: _topOverlayScale,
          canToggleFavourite: hasCollection,
          viewInsets: _frozenViewInsets,
          viewPadding: _frozenViewPadding,
          onActionSelected: (action) => _actionDelegate.onActionSelected(context, entry, action),
          viewStateNotifier: viewStateNotifier,
          multiPageController: multiPageController,
        );
      },
    );
    return ValueListenableBuilder<int>(
      valueListenable: _currentVerticalPage,
      builder: (context, page, child) {
        return Visibility(
          visible: page == imagePage,
          child: child,
        );
      },
      child: child,
    );
  }

  Widget _buildBottomOverlay() {
    Widget bottomOverlay = ValueListenableBuilder<ImageEntry>(
      valueListenable: _entryNotifier,
      builder: (context, entry, child) {
        if (entry == null) return SizedBox.shrink();

        final multiPageController = _getMultiPageController(entry);

        Widget extraBottomOverlay;
        if (entry.isVideo) {
          final videoController = _videoControllers.firstWhere((kv) => kv.item1 == entry.uri, orElse: () => null)?.item2;
          if (videoController != null) {
            extraBottomOverlay = VideoControlOverlay(
              entry: entry,
              controller: videoController,
              scale: _bottomOverlayScale,
            );
          }
        } else if (entry.is360) {
          extraBottomOverlay = PanoramaOverlay(
            entry: entry,
            scale: _bottomOverlayScale,
          );
        }

        final child = Column(
          children: [
            if (extraBottomOverlay != null)
              ExtraBottomOverlay(
                viewInsets: _frozenViewInsets,
                viewPadding: _frozenViewPadding,
                child: extraBottomOverlay,
              ),
            SlideTransition(
              position: _bottomOverlayOffset,
              child: ViewerBottomOverlay(
                entries: entries,
                index: _currentHorizontalPage,
                showPosition: hasCollection,
                viewInsets: _frozenViewInsets,
                viewPadding: _frozenViewPadding,
                multiPageController: multiPageController,
              ),
            ),
          ],
        );
        return ValueListenableBuilder<double>(
          valueListenable: _overlayAnimationController,
          builder: (context, animation, child) {
            return Visibility(
              visible: _overlayAnimationController.status != AnimationStatus.dismissed,
              child: child,
            );
          },
          child: child,
        );
      },
    );

    bottomOverlay = Selector<MediaQueryData, double>(
      selector: (c, mq) => mq.size.height,
      builder: (c, mqHeight, child) {
        // when orientation change, the `PageController` offset is not updated right away
        // and it does not trigger its listeners when it does, so we force a refresh in the next frame
        WidgetsBinding.instance.addPostFrameCallback((_) => _onVerticalPageControllerChange());
        return AnimatedBuilder(
          animation: _verticalScrollNotifier,
          builder: (context, child) => Positioned(
            // TODO TLAD replace when using Flutter version adapted for null safety
            // bottom: (_verticalPager.position.hasPixels ? _verticalPager.offset : 0) - mqHeight,
            bottom: (_verticalPager.offset ?? 0) - mqHeight,
            child: child,
          ),
          child: child,
        );
      },
      child: bottomOverlay,
    );
    return bottomOverlay;
  }

  MultiPageController _getMultiPageController(ImageEntry entry) {
    return entry.isMultipage ? _multiPageControllers.firstWhere((kv) => kv.item1 == entry.uri, orElse: () => null)?.item2 : null;
  }

  void _onVerticalPageControllerChange() {
    _verticalScrollNotifier.notifyListeners();
  }

  void _goToCollection(CollectionFilter filter) {
    _showSystemUI();
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(
        settings: RouteSettings(name: CollectionPage.routeName),
        builder: (context) => CollectionPage(collection.derive(filter)),
      ),
      (route) => false,
    );
  }

  Future<void> _goToVerticalPage(int page) {
    return _verticalPager.animateToPage(
      page,
      duration: Durations.viewerPageAnimation,
      curve: Curves.easeInOut,
    );
  }

  Future<void> _onVerticalPageChanged(int page) async {
    _currentVerticalPage.value = page;
    if (page == transitionPage) {
      await _actionDelegate.dismissFeedback();
      _onLeave();
      Navigator.pop(context);
    }
  }

  void _onHorizontalPageChanged(int page) {
    _currentHorizontalPage = page;
    _updateEntry();
  }

  void _onCollectionChange() {
    _updateEntry();
  }

  void _updateEntry() {
    if (_currentHorizontalPage != null && entries.isNotEmpty && _currentHorizontalPage >= entries.length) {
      // as of Flutter v1.22.2, `PageView` does not call `onPageChanged` when the last page is deleted
      // so we manually track the page change, and let the entry update follow
      _onHorizontalPageChanged(entries.length - 1);
      return;
    }

    final newEntry = _currentHorizontalPage != null && _currentHorizontalPage < entries.length ? entries[_currentHorizontalPage] : null;
    if (_entryNotifier.value == newEntry) return;
    _entryNotifier.value = newEntry;
    _pauseVideoControllers();
    _initViewStateControllers();
  }

  void _onLeave() {
    if (Navigator.canPop(context)) {
      _showSystemUI();
      if (settings.keepScreenOn == KeepScreenOn.viewerOnly) {
        Screen.keepOn(false);
      }
    } else {
      // exit app when trying to pop a viewer page for a single entry
      SystemNavigator.pop();
    }
  }

  // system UI

  static void _showSystemUI() => SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);

  static void _hideSystemUI() => SystemChrome.setEnabledSystemUIOverlays([]);

  // overlay

  Future<void> _initOverlay() async {
    // wait for MaterialPageRoute.transitionDuration
    // to show overlay after hero animation is complete
    await Future.delayed(ModalRoute.of(context).transitionDuration * timeDilation);
    await _onOverlayVisibleChange();
  }

  Future<void> _onOverlayVisibleChange({bool animate = true}) async {
    if (_overlayVisible.value) {
      _showSystemUI();
      if (animate) {
        _overlayAnimationController.forward();
      } else {
        _overlayAnimationController.value = _overlayAnimationController.upperBound;
      }
    } else {
      final mediaQuery = Provider.of<MediaQueryData>(context, listen: false);
      setState(() {
        _frozenViewInsets = mediaQuery.viewInsets;
        _frozenViewPadding = mediaQuery.viewPadding;
      });
      _hideSystemUI();
      if (animate) {
        await _overlayAnimationController.reverse();
      } else {
        _overlayAnimationController.reset();
      }
      setState(() {
        _frozenViewInsets = null;
        _frozenViewPadding = null;
      });
    }
  }

  // state controllers/monitors

  void _initViewStateControllers() {
    final entry = _entryNotifier.value;
    if (entry == null) return;

    final uri = entry.uri;
    _initViewSpecificController<ValueNotifier<ViewState>>(
      uri,
      _viewStateNotifiers,
      () => ValueNotifier<ViewState>(ViewState.zero),
      (_) => _.dispose(),
    );
    if (entry.isVideo) {
      _initViewSpecificController<IjkMediaController>(
        uri,
        _videoControllers,
        () => IjkMediaController(),
        (_) => _.dispose(),
      );
    }
    if (entry.isMultipage) {
      _initViewSpecificController<MultiPageController>(
        uri,
        _multiPageControllers,
        () => MultiPageController(entry),
        (_) => _.dispose(),
      );
    }

    setState(() {});
  }

  void _initViewSpecificController<T>(String uri, List<Tuple2<String, T>> controllers, T Function() builder, void Function(T controller) disposer) {
    var controller = controllers.firstWhere((kv) => kv.item1 == uri, orElse: () => null);
    if (controller != null) {
      controllers.remove(controller);
    } else {
      controller = Tuple2(uri, builder());
    }
    controllers.insert(0, controller);
    while (controllers.length > 3) {
      disposer?.call(controllers.removeLast().item2);
    }
  }

  void _pauseVideoControllers() => _videoControllers.forEach((e) => e.item2.pause());
}

class ViewerVerticalPageView extends StatefulWidget {
  final CollectionLens collection;
  final ValueNotifier<ImageEntry> entryNotifier;
  final List<Tuple2<String, IjkMediaController>> videoControllers;
  final List<Tuple2<String, MultiPageController>> multiPageControllers;
  final PageController horizontalPager, verticalPager;
  final void Function(int page) onVerticalPageChanged, onHorizontalPageChanged;
  final VoidCallback onImageTap, onImagePageRequested;
  final void Function(String uri) onViewDisposed;

  const ViewerVerticalPageView({
    @required this.collection,
    @required this.entryNotifier,
    @required this.videoControllers,
    @required this.multiPageControllers,
    @required this.verticalPager,
    @required this.horizontalPager,
    @required this.onVerticalPageChanged,
    @required this.onHorizontalPageChanged,
    @required this.onImageTap,
    @required this.onImagePageRequested,
    @required this.onViewDisposed,
  });

  @override
  _ViewerVerticalPageViewState createState() => _ViewerVerticalPageViewState();
}

class _ViewerVerticalPageViewState extends State<ViewerVerticalPageView> {
  final ValueNotifier<Color> _backgroundColorNotifier = ValueNotifier(Colors.black);
  final ValueNotifier<bool> _infoPageVisibleNotifier = ValueNotifier(false);
  ImageEntry _oldEntry;

  CollectionLens get collection => widget.collection;

  bool get hasCollection => collection != null;

  ImageEntry get entry => widget.entryNotifier.value;

  @override
  void initState() {
    super.initState();
    _registerWidget(widget);
  }

  @override
  void didUpdateWidget(covariant ViewerVerticalPageView oldWidget) {
    super.didUpdateWidget(oldWidget);
    _unregisterWidget(oldWidget);
    _registerWidget(widget);
  }

  @override
  void dispose() {
    _unregisterWidget(widget);
    super.dispose();
  }

  void _registerWidget(ViewerVerticalPageView widget) {
    widget.verticalPager.addListener(_onVerticalPageControllerChanged);
    widget.entryNotifier.addListener(_onEntryChanged);
    if (_oldEntry != entry) _onEntryChanged();
  }

  void _unregisterWidget(ViewerVerticalPageView widget) {
    widget.verticalPager.removeListener(_onVerticalPageControllerChanged);
    widget.entryNotifier.removeListener(_onEntryChanged);
    _oldEntry?.imageChangeNotifier?.removeListener(_onImageChanged);
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      // fake page for opacity transition between collection and viewer
      SizedBox(),
      hasCollection
          ? MultiEntryScroller(
              collection: collection,
              pageController: widget.horizontalPager,
              onTap: widget.onImageTap,
              onPageChanged: widget.onHorizontalPageChanged,
              videoControllers: widget.videoControllers,
              multiPageControllers: widget.multiPageControllers,
              onViewDisposed: widget.onViewDisposed,
            )
          : SingleEntryScroller(
              entry: entry,
              onTap: widget.onImageTap,
              videoControllers: widget.videoControllers,
              multiPageControllers: widget.multiPageControllers,
            ),
      NotificationListener(
        onNotification: (notification) {
          if (notification is BackUpNotification) widget.onImagePageRequested();
          return false;
        },
        child: InfoPage(
          collection: collection,
          entryNotifier: widget.entryNotifier,
          visibleNotifier: _infoPageVisibleNotifier,
        ),
      ),
    ];
    return ValueListenableBuilder<Color>(
      valueListenable: _backgroundColorNotifier,
      builder: (context, backgroundColor, child) => Container(
        color: backgroundColor,
        child: child,
      ),
      child: PageView(
        key: Key('vertical-pageview'),
        scrollDirection: Axis.vertical,
        controller: widget.verticalPager,
        physics: MagnifierScrollerPhysics(parent: PageScrollPhysics()),
        onPageChanged: (page) {
          widget.onVerticalPageChanged(page);
          _infoPageVisibleNotifier.value = page == pages.length - 1;
        },
        children: pages,
      ),
    );
  }

  void _onVerticalPageControllerChanged() {
    final opacity = min(1.0, widget.verticalPager.page);
    _backgroundColorNotifier.value = _backgroundColorNotifier.value.withOpacity(opacity * opacity);
  }

  // when the entry changed (e.g. by scrolling through the PageView, or if the entry got deleted)
  void _onEntryChanged() {
    _oldEntry?.imageChangeNotifier?.removeListener(_onImageChanged);
    _oldEntry = entry;

    if (entry != null) {
      entry.imageChangeNotifier.addListener(_onImageChanged);
      // make sure to locate the entry,
      // so that we can display the address instead of coordinates
      // even when background locating has not reached this entry yet
      entry.locate();
    } else {
      Navigator.pop(context);
    }
  }

  // when the entry image itself changed (e.g. after rotation)
  void _onImageChanged() async {
    // rebuild to refresh the Image inside ImagePage
    setState(() {});
  }
}
