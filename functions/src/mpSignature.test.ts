import test from "node:test";
import assert from "node:assert/strict";
import * as crypto from "crypto";
import {
  MP_SIGNATURE_WINDOW_MS,
  validateMpSignature,
} from "./mpSignature";

const WEBHOOK_SECRET = "secret_test";
const REQUEST_ID = "request-123";
const DATA_ID = "payment-456";

const buildSignatureHeader = (ts: number | string, secret = WEBHOOK_SECRET): string => {
  const signature = crypto
    .createHmac("sha256", secret)
    .update(`${ts}.${REQUEST_ID}.${DATA_ID}`)
    .digest("hex");
  return `ts=${ts},v1=${signature}`;
};

test("validateMpSignature acepta firma válida dentro de la ventana", () => {
  const nowMs = Date.now();
  const ts = Math.floor(nowMs / 1000);
  const signatureHeader = buildSignatureHeader(ts);

  const result = validateMpSignature({
    signatureHeader,
    requestId: REQUEST_ID,
    dataId: DATA_ID,
    webhookSecret: WEBHOOK_SECRET,
    nowMs,
  });

  assert.equal(result.isValid, true);
  assert.equal(result.ts, ts);
});

test("validateMpSignature rechaza firma con digest inválido", () => {
  const nowMs = Date.now();
  const ts = Math.floor(nowMs / 1000);
  const signatureHeader = buildSignatureHeader(ts, "wrong-secret");

  const result = validateMpSignature({
    signatureHeader,
    requestId: REQUEST_ID,
    dataId: DATA_ID,
    webhookSecret: WEBHOOK_SECRET,
    nowMs,
  });

  assert.equal(result.isValid, false);
  assert.equal(result.reason, "signature_mismatch");
});

test("validateMpSignature rechaza firma expirada fuera de 5 minutos", () => {
  const nowMs = Date.now();
  const ts = Math.floor((nowMs - MP_SIGNATURE_WINDOW_MS - 1000) / 1000);
  const signatureHeader = buildSignatureHeader(ts);

  const result = validateMpSignature({
    signatureHeader,
    requestId: REQUEST_ID,
    dataId: DATA_ID,
    webhookSecret: WEBHOOK_SECRET,
    nowMs,
  });

  assert.equal(result.isValid, false);
  assert.equal(result.reason, "signature_expired");
});

test("validateMpSignature rechaza ts no numérico", () => {
  const nowMs = Date.now();
  const signatureHeader = buildSignatureHeader("abc");

  const result = validateMpSignature({
    signatureHeader,
    requestId: REQUEST_ID,
    dataId: DATA_ID,
    webhookSecret: WEBHOOK_SECRET,
    nowMs,
  });

  assert.equal(result.isValid, false);
  assert.equal(result.reason, "invalid_ts");
});

test("validateMpSignature rechaza firmas con ts en el futuro", () => {
  const nowMs = Date.now();
  const ts = Math.floor((nowMs + 60_000) / 1000);
  const signatureHeader = buildSignatureHeader(ts);

  const result = validateMpSignature({
    signatureHeader,
    requestId: REQUEST_ID,
    dataId: DATA_ID,
    webhookSecret: WEBHOOK_SECRET,
    nowMs,
  });

  assert.equal(result.isValid, false);
  assert.equal(result.reason, "signature_expired");
});
