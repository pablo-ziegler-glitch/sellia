import { ensureObject, ensureString } from "../contracts.validation";

export type NotificationDispatchInputDto = {
  tenantId: string;
  templateId: string;
};

export const parseNotificationDispatchInput = (payload: unknown): NotificationDispatchInputDto => {
  const body = ensureObject(payload, "notifications.dispatch.payload");
  return {
    tenantId: ensureString(body.tenantId, "tenantId", { min: 3, max: 80 }),
    templateId: ensureString(body.templateId, "templateId", { min: 3, max: 120 }),
  };
};
