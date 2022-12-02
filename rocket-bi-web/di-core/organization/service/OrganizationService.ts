import { PlanDetail, PlanInfo } from '../domain';
import { Inject } from 'typescript-ioc';
import { OrganizationRepository } from '../repository';
import { Log } from '@core/utils';
import { Organization } from '@core/common/domain';
import { UpdateOrganizationRequest } from '@core/organization/domain/request/UpdateOrganizationRequest';
import { UnsubscribePlanResp } from '@core/organization/domain/plan/UnsubscribePlanResp';
import { PlanType } from '@core/organization/domain/plan/PlanType';
import { SubscribePlanResp } from '@core/organization/domain/plan/SubscribePlanResp';

export abstract class OrganizationService {
  abstract getPlan(): Promise<PlanInfo>;

  abstract getPlanDetail(): Promise<PlanDetail>;

  abstract subscribePlan(planType: PlanType): Promise<SubscribePlanResp>;

  abstract revisePlan(planType: PlanType): Promise<SubscribePlanResp>;

  abstract unsubscribePlan(): Promise<UnsubscribePlanResp>;

  abstract getOrganization(): Promise<Organization>;

  abstract updateOrganization(request: UpdateOrganizationRequest): Promise<Organization>;
}

export class OrganizationServiceImpl extends OrganizationService {
  constructor(@Inject private organizationRepository: OrganizationRepository) {
    super();
    Log.info('OrganizationServiceImpl', organizationRepository);
  }

  getPlan(): Promise<PlanInfo> {
    return this.organizationRepository.getPlan();
  }

  getPlanDetail(): Promise<PlanDetail> {
    return this.organizationRepository.getPlanDetail();
  }

  subscribePlan(planType: PlanType): Promise<SubscribePlanResp> {
    return this.organizationRepository.subscribePlan(planType);
  }

  revisePlan(planType: PlanType): Promise<SubscribePlanResp> {
    return this.organizationRepository.revisePlan(planType);
  }

  unsubscribePlan(): Promise<UnsubscribePlanResp> {
    return this.organizationRepository.unsubscribePlan();
  }

  getOrganization(): Promise<Organization> {
    return this.organizationRepository.getOrganization();
  }

  updateOrganization(request: UpdateOrganizationRequest): Promise<Organization> {
    return this.organizationRepository.updateOrganization(request);
  }
}
