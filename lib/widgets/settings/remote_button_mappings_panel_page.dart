/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import 'dart:async';

import 'package:flauncher/database.dart';
import 'package:flauncher/flauncher_channel.dart';
import 'package:flauncher/providers/apps_service.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class RemoteButtonMappingsPanelPage extends StatefulWidget {
  static const String routeName = "remote_button_mappings";

  @override
  State<RemoteButtonMappingsPanelPage> createState() => _RemoteButtonMappingsPanelPageState();
}

class _RemoteButtonMappingsPanelPageState extends State<RemoteButtonMappingsPanelPage> {
  StreamSubscription<Map<dynamic, dynamic>>? _eventSubscription;
  bool _capturing = false;
  List<Map<dynamic, dynamic>> _mappings = [];

  @override
  void initState() {
    super.initState();
    _loadMappings();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  Future<void> _loadMappings() async {
    final channel = context.read<FLauncherChannel>();
    final mappings = await channel.getRemoteButtonMappings();
    if (mounted) {
      setState(() => _mappings = mappings.cast<Map<dynamic, dynamic>>());
    }
  }

  void _listenForCapturedButton() {
    final channel = context.read<FLauncherChannel>();
    _eventSubscription ??= channel.addRemoteButtonCapturedListener((event) {
      if (event['action'] == 'REMOTE_BUTTON_CAPTURED' && mounted) {
        setState(() => _capturing = false);
        _chooseApp(
          event['keyCode'] as int,
          event['keyLabel'] as String,
        );
      }
    });
  }

  Future<void> _startCapture() async {
    _listenForCapturedButton();
    setState(() => _capturing = true);
    await context.read<FLauncherChannel>().startRemoteButtonCapture();
  }

  Future<void> _chooseApp(int keyCode, String keyLabel) async {
    final application = await showDialog<App>(
      context: context,
      builder: (context) {
        final applications = context.read<AppsService>().applications.toList()
          ..sort((left, right) => left.name.toLowerCase().compareTo(right.name.toLowerCase()));
        return SimpleDialog(
          title: Text("Map $keyLabel to"),
          children: applications
              .map((app) => SimpleDialogOption(
                    onPressed: () => Navigator.of(context).pop(app),
                    child: Row(
                      children: [
                        if (app.icon != null) ...[
                          Image.memory(app.icon!, width: 32, height: 32),
                          SizedBox(width: 12),
                        ],
                        Expanded(child: Text(app.name)),
                      ],
                    ),
                  ))
              .toList(),
        );
      },
    );

    if (application != null) {
      await context.read<FLauncherChannel>().setRemoteButtonMapping(keyCode, application.packageName);
      await _loadMappings();
    }
  }

  Future<void> _clearMapping(int keyCode) async {
    await context.read<FLauncherChannel>().clearRemoteButtonMapping(keyCode);
    await _loadMappings();
  }

  @override
  Widget build(BuildContext context) {
    final applications = context.watch<AppsService>().applications;

    return Column(
      children: [
        Text("Remote Button Mappings", style: Theme.of(context).textTheme.headline6),
        Divider(),
        Text(
          "Press a dedicated remote button while FLauncher is open, then choose an app.",
          style: Theme.of(context).textTheme.caption,
        ),
        SizedBox(height: 8),
        TextButton(
          onPressed: _capturing ? null : _startCapture,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.settings_remote),
              SizedBox(width: 8),
              Text(_capturing ? "Press a remote button..." : "Capture remote button"),
            ],
          ),
        ),
        Expanded(
          child: _mappings.isEmpty
              ? Center(child: Text("No remote buttons mapped"))
              : ListView(
                  children: _mappings.map((mapping) {
                    final keyCode = mapping['keyCode'] as int;
                    final keyLabel = mapping['keyLabel'] as String;
                    final packageName = mapping['packageName'] as String;
                    final app = applications.firstWhere(
                      (application) => application.packageName == packageName,
                      orElse: () => App(
                        packageName: packageName,
                        name: packageName,
                        version: '',
                        hidden: false,
                        sideloaded: false,
                      ),
                    );
                    return Card(
                      child: ListTile(
                        leading: Icon(Icons.settings_remote),
                        title: Text(keyLabel),
                        subtitle: Text(app.name),
                        trailing: IconButton(
                          icon: Icon(Icons.delete_outline),
                          onPressed: () => _clearMapping(keyCode),
                        ),
                      ),
                    );
                  }).toList(),
                ),
        ),
      ],
    );
  }
}
