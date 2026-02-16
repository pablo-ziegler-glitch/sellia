import * as crypto from "crypto";

export const MP_SIGNATURE_WINDOW_MS = 5 * 60 * 1000;

export type ValidateMpSignatureInput = {
  signatureHeader: string;
  requestId: string;
  dataId: string;
  webhookSecret: string;
  nowMs?: number;
  maxAgeMs?: number;
};

type MpSignatureValidationSuccess = {
  isValid: true;
  ts: number;
  signature: string;
};

type MpSignatureValidationFailure = {
  isValid: false;
  reason: "missing_signature" | "invalid_ts" | "signature_expired" | "signature_mismatch";
  ts?: number;
  signature?: string;
};

export type MpSignatureValidationResult =
  | MpSignatureValidationSuccess
  | MpSignatureValidationFailure;

type ParsedSignature = {
  ts?: number;
  hasTs: boolean;
  v1: string;
};

const parseSignature = (signatureHeader: string): ParsedSignature => {
  const parts = signatureHeader
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  let rawTs = "";
  let v1 = "";

  for (const part of parts) {
    const [key, value] = part.split("=");
    if (!key || !value) {
      continue;
    }
    if (key === "ts") {
      rawTs = value;
    }
    if (key === "v1") {
      v1 = value;
    }
  }

  const ts = Number(rawTs);
  return {
    ts: Number.isFinite(ts) ? ts : undefined,
    hasTs: rawTs.length > 0,
    v1,
  };
};

const timingSafeEqual = (a: string, b: string): boolean => {
  const bufferA = Buffer.from(a, "utf8");
  const bufferB = Buffer.from(b, "utf8");
  if (bufferA.length !== bufferB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufferA, bufferB);
};

export const validateMpSignature = ({
  signatureHeader,
  requestId,
  dataId,
  webhookSecret,
  nowMs = Date.now(),
  maxAgeMs = MP_SIGNATURE_WINDOW_MS,
}: ValidateMpSignatureInput): MpSignatureValidationResult => {
  const { ts, hasTs, v1 } = parseSignature(signatureHeader);

  if (!requestId || !dataId || !v1) {
    return { isValid: false, reason: "missing_signature" };
  }

  if (!hasTs || ts === undefined || !Number.isInteger(ts) || ts <= 0) {
    return { isValid: false, reason: "invalid_ts" };
  }

  const requestAgeMs = nowMs - ts * 1000;
  if (requestAgeMs < 0 || requestAgeMs > maxAgeMs) {
    return { isValid: false, reason: "signature_expired", ts, signature: v1 };
  }

  const manifest = `${ts}.${requestId}.${dataId}`;
  const expectedSignature = crypto
    .createHmac("sha256", webhookSecret)
    .update(manifest)
    .digest("hex");

  if (!timingSafeEqual(expectedSignature, v1)) {
    return { isValid: false, reason: "signature_mismatch", ts, signature: v1 };
  }

  return { isValid: true, ts, signature: v1 };
};
