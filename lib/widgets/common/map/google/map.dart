import 'dart:async';
import 'dart:typed_data';

import 'package:aves/model/entry.dart';
import 'package:aves/model/entry_images.dart';
import 'package:aves/model/settings/enums.dart';
import 'package:aves/utils/change_notifier.dart';
import 'package:aves/widgets/common/map/buttons.dart';
import 'package:aves/widgets/common/map/controller.dart';
import 'package:aves/widgets/common/map/decorator.dart';
import 'package:aves/widgets/common/map/geo_entry.dart';
import 'package:aves/widgets/common/map/geo_map.dart';
import 'package:aves/widgets/common/map/google/marker_generator.dart';
import 'package:aves/widgets/common/map/zoomed_bounds.dart';
import 'package:collection/collection.dart';
import 'package:fluster/fluster.dart';
import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:latlong2/latlong.dart' as ll;

class EntryGoogleMap extends StatefulWidget {
  final AvesMapController? controller;
  final ValueNotifier<ZoomedBounds> boundsNotifier;
  final bool interactive;
  final double? minZoom, maxZoom;
  final EntryMapStyle style;
  final EntryMarkerBuilder markerBuilder;
  final Fluster<GeoEntry> markerCluster;
  final List<AvesEntry> markerEntries;
  final UserZoomChangeCallback? onUserZoomChange;
  final void Function(GeoEntry geoEntry)? onMarkerTap;

  const EntryGoogleMap({
    Key? key,
    this.controller,
    required this.boundsNotifier,
    required this.interactive,
    this.minZoom,
    this.maxZoom,
    required this.style,
    required this.markerBuilder,
    required this.markerCluster,
    required this.markerEntries,
    this.onUserZoomChange,
    this.onMarkerTap,
  }) : super(key: key);

  @override
  State<StatefulWidget> createState() => _EntryGoogleMapState();
}

class _EntryGoogleMapState extends State<EntryGoogleMap> with WidgetsBindingObserver {
  GoogleMapController? _googleMapController;
  final List<StreamSubscription> _subscriptions = [];
  final Map<MarkerKey, Uint8List> _markerBitmaps = {};
  final AChangeNotifier _markerBitmapChangeNotifier = AChangeNotifier();

  ValueNotifier<ZoomedBounds> get boundsNotifier => widget.boundsNotifier;

  ZoomedBounds get bounds => boundsNotifier.value;

  bool get interactive => widget.interactive;

  static const uninitializedLatLng = LatLng(0, 0);

  @override
  void initState() {
    super.initState();
    final avesMapController = widget.controller;
    if (avesMapController != null) {
      _subscriptions.add(avesMapController.moveEvents.listen((event) => _moveTo(_toGoogleLatLng(event.latLng))));
    }
  }

  @override
  void didUpdateWidget(covariant EntryGoogleMap oldWidget) {
    super.didUpdateWidget(oldWidget);
    const eq = DeepCollectionEquality();
    if (!eq.equals(widget.markerEntries, oldWidget.markerEntries)) {
      _markerBitmaps.clear();
    }
  }

  @override
  void dispose() {
    _googleMapController?.dispose();
    _subscriptions
      ..forEach((sub) => sub.cancel())
      ..clear();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.inactive:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        break;
      case AppLifecycleState.resumed:
        // workaround for blank Google Maps when resuming app
        // cf https://github.com/flutter/flutter/issues/40284
        _googleMapController?.setMapStyle(null);
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ZoomedBounds?>(
      valueListenable: boundsNotifier,
      builder: (context, visibleRegion, child) {
        final allEntries = widget.markerEntries;
        final geoEntries = visibleRegion != null ? widget.markerCluster.clusters(visibleRegion.boundingBox, visibleRegion.zoom.round()) : <GeoEntry>[];
        final geoEntryByMarkerKey = Map.fromEntries(geoEntries.map((v) {
          if (v.isCluster!) {
            final uri = v.childMarkerId;
            final entry = allEntries.firstWhere((v) => v.uri == uri);
            return MapEntry(MarkerKey(entry, v.pointsSize), v);
          }
          return MapEntry(MarkerKey(v.entry!, null), v);
        }));

        return Stack(
          children: [
            MarkerGeneratorWidget<MarkerKey>(
              markers: geoEntryByMarkerKey.keys.map(widget.markerBuilder).toList(),
              isReadyToRender: (key) => key.entry.isThumbnailReady(extent: GeoMap.markerImageExtent),
              onRendered: (key, bitmap) {
                _markerBitmaps[key] = bitmap;
                _markerBitmapChangeNotifier.notifyListeners();
              },
            ),
            MapDecorator(
              interactive: interactive,
              child: _buildMap(geoEntryByMarkerKey),
            ),
            MapButtonPanel(
              boundsNotifier: boundsNotifier,
              zoomBy: _zoomBy,
              resetRotation: interactive ? _resetRotation : null,
            ),
          ],
        );
      },
    );
  }

  Widget _buildMap(Map<MarkerKey, GeoEntry> geoEntryByMarkerKey) {
    return AnimatedBuilder(
      animation: _markerBitmapChangeNotifier,
      builder: (context, child) {
        final markers = <Marker>{};
        final onEntryTap = widget.onMarkerTap;
        geoEntryByMarkerKey.forEach((markerKey, geoEntry) {
          final bytes = _markerBitmaps[markerKey];
          if (bytes != null) {
            final latLng = LatLng(geoEntry.latitude!, geoEntry.longitude!);
            markers.add(Marker(
              markerId: MarkerId(geoEntry.markerId!),
              icon: BitmapDescriptor.fromBytes(bytes),
              position: latLng,
              onTap: onEntryTap != null ? () => onEntryTap(geoEntry) : null,
            ));
          }
        });

        final interactive = widget.interactive;
        return GoogleMap(
          initialCameraPosition: CameraPosition(
            target: _toGoogleLatLng(bounds.center),
            zoom: bounds.zoom,
          ),
          onMapCreated: (controller) async {
            _googleMapController = controller;
            final zoom = await controller.getZoomLevel();
            await _updateVisibleRegion(zoom: zoom, rotation: 0);
            setState(() {});
          },
          // compass disabled to use provider agnostic controls
          compassEnabled: false,
          mapToolbarEnabled: false,
          mapType: _toMapType(widget.style),
          minMaxZoomPreference: MinMaxZoomPreference(widget.minZoom, widget.maxZoom),
          rotateGesturesEnabled: true,
          scrollGesturesEnabled: interactive,
          // zoom controls disabled to use provider agnostic controls
          zoomControlsEnabled: false,
          zoomGesturesEnabled: interactive,
          // lite mode disabled because it lacks camera animation
          liteModeEnabled: false,
          // tilt disabled to match leaflet
          tiltGesturesEnabled: false,
          myLocationEnabled: false,
          myLocationButtonEnabled: false,
          markers: markers,
          onCameraMove: (position) => _updateVisibleRegion(zoom: position.zoom, rotation: -position.bearing),
        );
      },
    );
  }

  Future<void> _updateVisibleRegion({required double zoom, required double rotation}) async {
    if (!mounted) return;

    final bounds = await _googleMapController?.getVisibleRegion();
    if (bounds != null && (bounds.northeast != uninitializedLatLng || bounds.southwest != uninitializedLatLng)) {
      boundsNotifier.value = ZoomedBounds(
        west: bounds.southwest.longitude,
        south: bounds.southwest.latitude,
        east: bounds.northeast.longitude,
        north: bounds.northeast.latitude,
        zoom: zoom,
        rotation: rotation,
      );
    } else {
      // the visible region is sometimes uninitialized when queried right after creation,
      // so we query it again next frame
      WidgetsBinding.instance!.addPostFrameCallback((_) {
        _updateVisibleRegion(zoom: zoom, rotation: rotation);
      });
    }
  }

  Future<void> _resetRotation() async {
    final controller = _googleMapController;
    if (controller == null) return;

    final bounds = boundsNotifier.value;
    await controller.animateCamera(CameraUpdate.newCameraPosition(CameraPosition(
      target: _toGoogleLatLng(bounds.center),
      zoom: bounds.zoom,
    )));
  }

  Future<void> _zoomBy(double amount) async {
    final controller = _googleMapController;
    if (controller == null) return;

    widget.onUserZoomChange?.call(await controller.getZoomLevel() + amount);
    await controller.animateCamera(CameraUpdate.zoomBy(amount));
  }

  Future<void> _moveTo(LatLng latLng) async {
    final controller = _googleMapController;
    if (controller == null) return;

    await controller.animateCamera(CameraUpdate.newLatLng(latLng));
  }

  // `LatLng` used by `google_maps_flutter` is not the one from `latlong2` package
  LatLng _toGoogleLatLng(ll.LatLng latLng) => LatLng(latLng.latitude, latLng.longitude);

  MapType _toMapType(EntryMapStyle style) {
    switch (style) {
      case EntryMapStyle.googleNormal:
        return MapType.normal;
      case EntryMapStyle.googleHybrid:
        return MapType.hybrid;
      case EntryMapStyle.googleTerrain:
        return MapType.terrain;
      default:
        return MapType.none;
    }
  }
}
