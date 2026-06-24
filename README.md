# geofence_foreground_service

![Flutter Version](https://img.shields.io/badge/flutter-%3E%3D3.3.0-blue.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-android-lightgrey.svg)
![Platform](https://img.shields.io/badge/platform-ios-lightgrey.svg)

A Flutter plugin that enables you to easily handle geofencing events in your Flutter app by utilizing native OS APIs on `Android` using the [Geofence API](https://developer.android.com/training/location/geofencing) and [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) APIs. Geofence transitions are delivered to a `BroadcastReceiver` — **no foreground service is required**, making it fully compliant with the [Google Play foreground service policy](https://support.google.com/googleplay/android-developer/answer/13392821) effective October 28, 2026. On `iOS` it utilizes the [CLLocationManager](https://developer.apple.com/documentation/corelocation/cllocationmanager).

It's important to note that the [workmanager](https://pub.dev/packages/workmanager)
and [flutter_foreground_task](https://pub.dev/packages/flutter_foreground_task) plugins were a
great source of inspiration while creating this plugin.

Android|iOS
--|--
![Android Demo](https://github.com/Basel-525k/geofence_foreground_service/blob/main/assets/gifs/Geofencing_Android.gif)|![iOS Demo](https://github.com/Basel-525k/geofence_foreground_service/blob/main/assets/gifs/Geofencing_iOS.gif)

## Features

- **Supports geofencing in foreground as well as background** — triggers are delivered via the platform Geofence API whether the app is in the foreground or the background
- **No foreground service required on Android** — fully compliant with the Google Play policy changes (Oct 2026). No `FOREGROUND_SERVICE` or `FOREGROUND_SERVICE_LOCATION` permissions needed
- **Geofence a circular area**
- **Geofence a polygon** — You can add a geofence using a list of coordinates, the system will calculate the center of them and register it, having full polygon support is a WIP
- **Notification responsiveness** — **Android**: You can set the responsiveness of the android notifications as per the docs [here](https://developers.google.com/android/reference/com/google/android/gms/location/Geofence.Builder#public-geofence.builder-setnotificationresponsiveness-int-notificationresponsivenessms)

## Migration from v1.x (foreground service)

If you are upgrading from a version that used a foreground service:

1. **Remove** the `<service>` declaration for `GeofenceForegroundService` from your `AndroidManifest.xml`
2. **Remove** the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION` permissions from your `AndroidManifest.xml`
3. No changes to your Dart code are needed — the API is fully backward compatible

## Setup

### Android Setup

- Enable MultiDex, you can check how to do
  so [here](https://docs.flutter.dev/deployment/android#enabling-multidex-support)
- Add the required permissions to your `AndroidManifest.xml`

```xml
<!--at least one of the following-->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!--required for background geofencing-->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

- Make sure the `minSdkVersion` in the `app/build.gradle` file is 29+

### iOS Setup

- Navigate to the Podfile and make sure to set the iOS version to 12+
```
platform :ios, '12.0'
```
- Make sure to add the following permission to your Info.plist
```
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app need your location to provide best feature based on location</string>
<key>NSLocationAlwaysUsageDescription</key>
<string>This app need your location to provide best feature based on location</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app need your location to provide best feature based on location</string>
```
- Turn on the `Location updates` and `Background fetch` capabilities from XCode
  ![iOS capabilities](https://github.com/Basel-525k/geofence_foreground_service/blob/main/assets/images/ios_setup_steps.png?raw=true)

## Example

Define the method that will handle the Geofence triggers

```dart
import 'package:geofence_foreground_service/exports.dart';
import 'package:geofence_foreground_service/geofence_foreground_service.dart';
import 'package:geofence_foreground_service/models/zone.dart';

// This method is a top level method
@pragma('vm:entry-point')
void callbackDispatcher() async {
  GeofenceForegroundService().handleTrigger(
    backgroundTriggerHandler: (zoneID, triggerType) {
      log(zoneID, name: 'zoneID');

      if (triggerType == GeofenceEventType.enter) {
        log('enter', name: 'triggerType');
      } else if (triggerType == GeofenceEventType.exit) {
        log('exit', name: 'triggerType');
      } else if (triggerType == GeofenceEventType.dwell) {
        log('dwell', name: 'triggerType');
      } else {
        log('unknown', name: 'triggerType');
      }

      return Future.value(true);
    },
  );
}
```

Then create an instance of the plugin to initiate it and assign GeoFences to it

```dart
final List<LatLng> timesSquarePolygon = [
  const LatLng(40.758078, -73.985640),
  const LatLng(40.757983, -73.985417),
  const LatLng(40.757881, -73.985493),
  const LatLng(40.757956, -73.985688),
];

Future<void> initPlatformState() async {
  // Remember to handle permissions before initiating the plugin

  bool hasServiceStarted = await GeofenceForegroundService().startGeofencingService(
    contentTitle: 'Test app is running in the background',
    contentText: 'Test app will be running to ensure seamless integration with ops team',
    notificationChannelId: 'com.app.geofencing_notifications_channel',
    serviceId: 525600,
    callbackDispatcher: callbackDispatcher,
  );

  if (hasServiceStarted) {
    await GeofenceForegroundService().addGeofenceZone(
      zone: Zone(
        id: 'zone#1_id',
        radius: 10000, // measured in meters
        coordinates: timesSquarePolygon,
      ),
    );
  }
}
```

> Something important to point out is the callbackDispatcher method will run in an entirely
> different isolate than the actual app, so if you were to handle UI related code inside of it
> you'll
> need to use Ports, you can find more
> information
> [here](https://github.com/fluttercommunity/flutter_workmanager/issues/151#issuecomment-612637579)

## Notes

Handling permissions is not a part of the package, so please refer
to [permission_handler](https://pub.dev/packages/permission_handler) plugin to grant the required
permissions (it's used in the example too)

- location
- locationAlways
- notification

## Contributing Guidelines

We welcome contributions from the community. If you'd like to contribute to the development of this
plugin, please feel free to submit a PR to our GitHub repository.
