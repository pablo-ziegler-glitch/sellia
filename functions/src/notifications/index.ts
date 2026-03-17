import * as legacy from "../legacyHandlers";

export { parseNotificationDispatchInput, type NotificationDispatchInputDto } from "./contracts";

export const processTenantBackupRequest = legacy.processTenantBackupRequest;
export const createDailyTenantBackups = legacy.createDailyTenantBackups;
export const archiveAndPurgeTenantBackups = legacy.archiveAndPurgeTenantBackups;
