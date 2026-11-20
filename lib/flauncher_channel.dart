/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import 'dart:async';

import 'package:flutter/services.dart';

class FLauncherChannel {
  static const _methodChannel = MethodChannel('me.efesser.flauncher/method');
  static const _eventChannel = EventChannel('me.efesser.flauncher/event');

  Future<List<dynamic>> getApplications() async => (await _methodChannel.invokeListMethod('getApplications'))!;

  Future<void> launchApp(String packageName) async => await _methodChannel.invokeMethod('launchApp', packageName);

  Future<void> openSettings() async => await _methodChannel.invokeMethod('openSettings');

  Future<void> openAppInfo(String packageName) async => await _methodChannel.invokeMethod('openAppInfo', packageName);

  Future<void> uninstallApp(String packageName) async => await _methodChannel.invokeMethod('uninstallApp', packageName);

  Future<bool> isDefaultLauncher() async => await _methodChannel.invokeMethod('isDefaultLauncher');

  Future<bool> checkForGetContentAvailability() async =>
      await _methodChannel.invokeMethod("checkForGetContentAvailability");

  Future<List<dynamic>> getRemoteButtonMappings() async =>
      (await _methodChannel.invokeListMethod('getRemoteButtonMappings'))!;

  Future<void> setRemoteButtonMapping(int keyCode, String packageName) async =>
      await _methodChannel.invokeMethod('setRemoteButtonMapping', {
        'keyCode': keyCode,
        'packageName': packageName,
      });

  Future<void> clearRemoteButtonMapping(int keyCode) async =>
      await _methodChannel.invokeMethod('clearRemoteButtonMapping', keyCode);

  Future<void> startRemoteButtonCapture() async =>
      await _methodChannel.invokeMethod('startRemoteButtonCapture');

  Future<void> stopRemoteButtonCapture() async =>
      await _methodChannel.invokeMethod('stopRemoteButtonCapture');

  void addAppsChangedListener(void Function(Map<dynamic, dynamic>) listener) =>
      _eventChannel
          .receiveBroadcastStream()
          .map((event) => event as Map<dynamic, dynamic>)
          .listen(listener);

  StreamSubscription<Map<dynamic, dynamic>> addRemoteButtonCapturedListener(
          void Function(Map<dynamic, dynamic>) listener) =>
      _eventChannel
          .receiveBroadcastStream()
          .map((event) => event as Map<dynamic, dynamic>)
          .listen(listener);
}
