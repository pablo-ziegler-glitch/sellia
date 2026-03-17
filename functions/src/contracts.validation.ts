export class ContractValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ContractValidationError";
  }
}

export const ensureObject = (value: unknown, context: string): Record<string, unknown> => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new ContractValidationError(`${context} must be an object`);
  }
  return value as Record<string, unknown>;
};

export const ensureString = (value: unknown, field: string, options?: { min?: number; max?: number }): string => {
  if (typeof value !== "string") {
    throw new ContractValidationError(`${field} must be a string`);
  }
  const normalized = value.trim();
  const min = options?.min ?? 1;
  const max = options?.max ?? 512;
  if (normalized.length < min || normalized.length > max) {
    throw new ContractValidationError(`${field} length must be between ${min} and ${max}`);
  }
  return normalized;
};

export const ensureBoolean = (value: unknown, field: string): boolean => {
  if (typeof value !== "boolean") {
    throw new ContractValidationError(`${field} must be a boolean`);
  }
  return value;
};

export const ensureNumber = (value: unknown, field: string, options?: { min?: number; max?: number }): number => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ContractValidationError(`${field} must be a finite number`);
  }
  if (typeof options?.min === "number" && value < options.min) {
    throw new ContractValidationError(`${field} must be >= ${options.min}`);
  }
  if (typeof options?.max === "number" && value > options.max) {
    throw new ContractValidationError(`${field} must be <= ${options.max}`);
  }
  return value;
};
