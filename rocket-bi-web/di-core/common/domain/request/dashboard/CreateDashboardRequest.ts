import {
  BoostInfo,
  Dashboard,
  DashboardSetting,
  DateFilter,
  DIMap,
  DirectoryId,
  DirectoryType,
  MainDateFilter,
  Position,
  Widget
} from '@core/common/domain/model';

export class CreateDashboardRequest {
  readonly name: string;
  readonly parentDirectoryId: DirectoryId;
  readonly mainDateFilter?: MainDateFilter;
  readonly widgets?: Widget[];
  readonly widgetPositions?: DIMap<Position>;
  readonly directoryType?: DirectoryType;
  readonly setting?: DashboardSetting;
  readonly boostInfo?: BoostInfo;

  constructor(
    directoryType: DirectoryType,
    name: string,
    parentDirectoryId: DirectoryId,
    mainDateFilter?: MainDateFilter,
    widgets?: Widget[],
    widgetPositions?: DIMap<Position>,
    setting?: DashboardSetting,
    boostInfo?: BoostInfo
  ) {
    this.name = name;
    this.parentDirectoryId = parentDirectoryId;
    this.mainDateFilter = mainDateFilter;
    this.widgets = widgets;
    this.widgetPositions = widgetPositions;
    this.directoryType = directoryType;
    this.setting = setting;
    this.boostInfo = boostInfo;
  }

  static createDashboardRequest(payload: {
    name: string;
    parentDirectoryId: DirectoryId;
    mainDateFilter?: MainDateFilter;
    widgets?: Widget[];
    widgetPositions?: DIMap<Position>;
    setting?: DashboardSetting;
    boostInfo?: BoostInfo;
  }) {
    return new CreateDashboardRequest(
      DirectoryType.Dashboard,
      payload.name,
      payload.parentDirectoryId,
      payload.mainDateFilter,
      payload.widgets,
      payload.widgetPositions,
      payload.setting,
      payload.boostInfo
    );
  }

  static createQueryRequest(payload: { name: string; parentDirectoryId: DirectoryId; widgets: Widget[] }) {
    return new CreateDashboardRequest(DirectoryType.Query, payload.name, payload.parentDirectoryId, void 0, payload.widgets, void 0, void 0, void 0);
  }

  static fromDashboard(type: DirectoryType, parentId: number, dashboard: Dashboard) {
    return new CreateDashboardRequest(
      type,
      `${dashboard.name} copy`,
      parentId,
      dashboard.mainDateFilter,
      dashboard.widgets,
      dashboard.widgetPositions,
      dashboard.setting,
      dashboard.boostInfo
    );
  }
}
