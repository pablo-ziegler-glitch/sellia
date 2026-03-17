import { ensureObject, ensureString } from "../contracts.validation";

export type PublicCatalogQueryDto = {
  tenantId: string;
  sort: string;
};

export const parsePublicCatalogQuery = (payload: unknown): PublicCatalogQueryDto => {
  const body = ensureObject(payload, "catalog.publicCatalog.query");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    sort: ensureString(body.sort ?? "name_asc", "sort", { min: 3, max: 40 }),
  };
};
