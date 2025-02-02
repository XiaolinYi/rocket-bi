import { VizSettingType } from '@core/common/domain/model/chart-option/VizSettingType.ts';

export abstract class DashboardOptions {
  static readonly EDIT_TITLE = 'Edit title';
  static readonly EDIT_TEXT = 'Edit text';
  static readonly CONFIG_FILTER = 'Config filter';
  static readonly CONFIG_CHART = 'Config chart';
  static readonly DUPLICATE_CHART = 'Duplicate chart';
  static readonly DUPLICATE = 'Duplicate';
  static readonly DELETE = 'Delete';
  static readonly ADD_CHART = 'Add chart';
  static readonly ADD_CONTROL = 'Add control';
  static readonly ADD_RULER = 'Add ruler';
  static readonly ADD_TEXT = 'Add text';
  static readonly ADD_LINK = 'Add link';
  static readonly ADD_IMAGE = 'Add image';
  static readonly ZOOM = 'Zoom';
  static readonly DRILLDOWN = 'Drilldown';
  static readonly ADD_FILTER_WIDGET = 'Add chart filter';
  static readonly UPDATE_FILTER_WIDGET = 'Update chart filter';
  static readonly DELETE_FILTER_WIDGET = 'Delete chart filter';
  static readonly ADD_TAB = 'Add tab';
  static readonly ADD_DYNAMIC_CONTROL = 'Add control';
  // static readonly ADD_DYNAMIC_CONTROL = 'Add tab';
}

export const DefaultSize: [number, number] = [16, 10];
export const SizeAsMap = new Map([
  [VizSettingType.TabFilterSetting, [12, 5]],
  [VizSettingType.SlicerFilterSetting, [12, 4]],
  [VizSettingType.DateSelectFilterSetting, [12, 2]],
  [VizSettingType.NumberSetting, [8, 8]],
  [VizSettingType.PieSetting, [10, 10]],
  [VizSettingType.InputFilterSetting, [10, 2]],
  [VizSettingType.BulletSetting, [16, 8]],
  [VizSettingType.TabMeasurementSetting, [12, 2]],
  [VizSettingType.InputControlSetting, [15, 2]]
]);
