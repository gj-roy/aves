import 'package:aves/widgets/common/basic/list_tiles/reselectable_radio.dart';
import 'package:aves/widgets/dialogs/selection_dialogs/common.dart';
import 'package:flutter/material.dart';

class SelectionRadioListTile<T> extends StatelessWidget {
  final T value;
  final String title;
  final TextBuilder<T>? optionSubtitleBuilder;
  final bool needConfirmation;
  final bool? dense;
  final T Function() getGroupValue;
  final void Function(T value) setGroupValue;

  const SelectionRadioListTile({
    super.key,
    required this.value,
    required this.title,
    this.optionSubtitleBuilder,
    required this.needConfirmation,
    this.dense,
    required this.getGroupValue,
    required this.setGroupValue,
  });

  @override
  Widget build(BuildContext context) {
    final subtitle = optionSubtitleBuilder?.call(value);
    return ReselectableRadioListTile<T>(
      // key is expected by test driver
      key: Key('$value'),
      value: value,
      groupValue: getGroupValue(),
      onChanged: (v) {
        if (needConfirmation) {
          setGroupValue(v as T);
        } else {
          Navigator.maybeOf(context)?.pop(v);
        }
      },
      reselectable: true,
      title: Text(
        title,
        overflow: TextOverflow.ellipsis,
        maxLines: 2,
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle,
              softWrap: false,
              overflow: TextOverflow.fade,
            )
          : null,
      dense: dense,
    );
  }
}
