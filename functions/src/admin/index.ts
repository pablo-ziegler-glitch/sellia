import * as legacy from "../legacyHandlers";
import { RuntimeFlagKeys } from "../config/runtime_flags";

export { parseRuntimeToggleInput, type RuntimeToggleInputDto } from "./contracts";

export const adminRuntimeFlag = RuntimeFlagKeys.ADMIN_ROUTES;
export const setMainLandingConfig = legacy.setMainLandingConfig;
export const setTenantStoreLandingConfig = legacy.setTenantStoreLandingConfig;
export const requestTenantBackup = legacy.requestTenantBackup;
export const requestTenantRestore = legacy.requestTenantRestore;
export const approveTenantRestoreRequest = legacy.approveTenantRestoreRequest;
export const getTenantOnboardingPolicy = legacy.getTenantOnboardingPolicy;
export const setTenantOnboardingPolicy = legacy.setTenantOnboardingPolicy;
export const purgeDeletedProductFromBackups = legacy.purgeDeletedProductFromBackups;
export const setStoreDomain = legacy.setStoreDomain;
export const removeStoreDomain = legacy.removeStoreDomain;
