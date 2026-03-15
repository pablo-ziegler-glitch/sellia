const EMAIL_KEY_PATTERN = /email|mail/i;
const PHONE_KEY_PATTERN = /phone|telefono|tel|mobile|whatsapp/i;
const IDENTIFIER_KEY_PATTERN = /uid|user.?id|owner.?uid|identifier|document|dni|ssn/i;

const REDACTED = "[REDACTED]";

const normalizeString = (value: unknown): string => String(value ?? "").trim();

export const maskEmail = (value: unknown): string => {
  const email = normalizeString(value).toLowerCase();
  if (!email) {
    return "";
  }

  const [local, domain] = email.split("@");
  if (!local || !domain) {
    return REDACTED;
  }

  const visiblePrefix = local.slice(0, Math.min(2, local.length));
  return `${visiblePrefix}***@${domain}`;
};

export const maskPhone = (value: unknown): string => {
  const input = normalizeString(value);
  if (!input) {
    return "";
  }

  const digitsOnly = input.replace(/\D/g, "");
  if (!digitsOnly) {
    return REDACTED;
  }

  const visibleSuffix = digitsOnly.slice(-2);
  return `${"*".repeat(Math.max(0, digitsOnly.length - 2))}${visibleSuffix}`;
};

export const maskIdentifier = (value: unknown): string => {
  const input = normalizeString(value);
  if (!input) {
    return "";
  }

  if (input.length <= 4) {
    return `${input.charAt(0)}***`;
  }

  return `${input.slice(0, 2)}***${input.slice(-2)}`;
};

const shouldMaskEmail = (key: string): boolean => EMAIL_KEY_PATTERN.test(key);
const shouldMaskPhone = (key: string): boolean => PHONE_KEY_PATTERN.test(key);
const shouldMaskIdentifier = (key: string): boolean => IDENTIFIER_KEY_PATTERN.test(key);

export const redactObject = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    return value.map((entry) => redactObject(entry));
  }

  if (!value || typeof value !== "object") {
    return value;
  }

  const source = value as Record<string, unknown>;
  return Object.fromEntries(
    Object.entries(source).map(([key, nestedValue]) => {
      if (shouldMaskEmail(key)) {
        return [key, maskEmail(nestedValue)];
      }
      if (shouldMaskPhone(key)) {
        return [key, maskPhone(nestedValue)];
      }
      if (shouldMaskIdentifier(key)) {
        return [key, maskIdentifier(nestedValue)];
      }
      if (nestedValue && typeof nestedValue === "object") {
        return [key, redactObject(nestedValue)];
      }
      return [key, nestedValue];
    })
  );
};

export type TenantOwnershipAction =
  | "ASSOCIATE_OWNER"
  | "TRANSFER_PRIMARY_OWNER"
  | "DELEGATE_STORE";

export const buildRoleScopedOwnershipResponse = (
  input: {
    tenantId: string;
    action: TenantOwnershipAction;
    targetUid: string;
    ownerUids: string[];
    delegatedStoreUids: string[];
  },
  isGlobalAdmin: boolean
) => {
  if (isGlobalAdmin) {
    return {
      tenantId: input.tenantId,
      action: input.action,
      targetUid: input.targetUid,
      ownerUids: input.ownerUids,
      delegatedStoreUids: input.delegatedStoreUids,
    };
  }

  return {
    tenantId: input.tenantId,
    action: input.action,
    targetUid: maskIdentifier(input.targetUid),
    ownerUids: input.ownerUids.map((uid) => maskIdentifier(uid)),
    delegatedStoreUids: input.delegatedStoreUids.map((uid) => maskIdentifier(uid)),
  };
};
