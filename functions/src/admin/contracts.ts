import { ensureBoolean, ensureObject, ensureString } from "../contracts.validation";

export type RuntimeToggleInputDto = {
  tenantId: string;
  enabled: boolean;
};

export const parseRuntimeToggleInput = (payload: unknown): RuntimeToggleInputDto => {
  const body = ensureObject(payload, "admin.runtimeToggle.payload");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    enabled: ensureBoolean(body.enabled, "enabled"),
  };
};
