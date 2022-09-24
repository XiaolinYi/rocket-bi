/*
 * @author: tvc12 - Thien Vi
 * @created: 8/24/21, 5:31 PM
 */

import { Routers, Status } from '@/shared';
import { DIException, DirectoryId, DirectoryPagingRequest } from '@core/domain';
import { DirectoryModule } from '@/screens/Directory/store/DirectoryStore';
import { Log } from '@core/utils';
import { RouterUtils } from '@/utils/RouterUtils';
import { DirectoryListingHandler } from '@/screens/Directory/views/MyData/DirectoryListingHandler/DirectoryListingHandler';
import { DataManager, DirectoryService } from '@core/services';
import { DI } from '@core/modules';

export class MyDataDirectoryListingHandler extends DirectoryListingHandler {
  protected readonly directoryService: DirectoryService;
  protected readonly dataManager: DataManager;

  constructor() {
    super();
    this.directoryService = DI.get(DirectoryService);
    this.dataManager = DI.get(DataManager);
  }

  get isSupportedClickRow(): boolean {
    return true;
  }

  get title(): string {
    return 'All Data';
  }

  get headerIcon(): string {
    return 'di-icon-my-data';
  }

  getRootName(): Routers {
    return Routers.AllData;
  }

  async loadDirectoryListing(directoryId: DirectoryId, paginationRequest: DirectoryPagingRequest, force?: boolean) {
    try {
      const status = force ? Status.Loading : Status.Updating;
      DirectoryModule.setStatus(status);
      await Promise.all([this.loadBreadcrumb(directoryId), this.loadDirectories(directoryId, paginationRequest)]);
      DirectoryModule.setStatus(Status.Loaded);
    } catch (ex) {
      const exception = DIException.fromObject(ex);
      Log.error('Load all data error', exception.message);
      DirectoryModule.setErrorMessage(exception.message ?? 'Load All Data failed');
    }
  }

  async loadBreadcrumb(directoryId: DirectoryId): Promise<void> {
    // hardcode: directory must positive
    if (RouterUtils.isLogin() && directoryId >= 0) {
      const parentDirectories = await this.directoryService.getParents(directoryId);
      DirectoryModule.setParents(parentDirectories);
    } else {
      DirectoryModule.setParents(null);
    }
  }

  async loadDirectories(directoryId: DirectoryId, paginationRequest: DirectoryPagingRequest): Promise<void> {
    const directories = await this.directoryService.list(directoryId, paginationRequest);
    DirectoryModule.setDirectories(directories);
  }
}