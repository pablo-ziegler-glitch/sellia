import { ensureObject, ensureString } from "../contracts.validation";

export type ManageTenantOwnershipInputDto = {
  tenantId: string;
  ownerUid: string;
};

export const parseManageTenantOwnershipInput = (payload: unknown): ManageTenantOwnershipInputDto => {
  const body = ensureObject(payload, "orders.manageTenantOwnership.payload");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    ownerUid: ensureString(body.ownerUid, "ownerUid", { min: 3, max: 128 }),
  };
};
