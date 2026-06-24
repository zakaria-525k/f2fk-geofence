## 2.0.0

* **BREAKING (Android only)**: Remove foreground service — geofence transitions are now delivered via a `BroadcastReceiver` using the platform Geofence API
* Fully compliant with Google Play's foreground service policy update (effective October 28, 2026) which no longer permits `FOREGROUND_SERVICE_LOCATION` for geofencing
* Removed `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION` permissions from the plugin
* Removed the `GeofenceForegroundService` Android service — no `<service>` declaration needed in host app manifest
* Fix background geofence triggers not firing until app returns to foreground — Dart callback now runs immediately from the broadcast receiver using synchronous Flutter initialization, a wake lock, and main-thread execution (no deferred WorkManager path)
* No changes to the Dart API — fully backward compatible for consumers
* iOS remains unchanged

## 1.1.3

* Handle error responses

## 1.1.2

* Handle error responses

## 1.1.1

* Add Android setNotificationResponsiveness support

## 1.1.0

* Add iOS support

## 1.0.9

* Pass static analysis

## 1.0.8

* Support android 14 new permissions
* Add the service type to the code

## 1.0.7

* Pass notification icon from flutter side
* Add more useful extensions

## 1.0.6

* Make the GeofenceForegroundService a singleton class

## 1.0.5

* Create GeofenceEventType enum to make handling events easier to understand

## 1.0.3

* README.md enhancements

## 1.0.1

* Add the ability to control the debug mode (enable/disable)
* Add the ability remove a geofence

## 1.0.0

* The API provides the following functionality
  * Initialize the service with their own channel id, title, text and service id
  * Add GeoFences with each having a list of coordinates
