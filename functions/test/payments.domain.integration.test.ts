import { describe, expect, it } from "vitest";
import { createPaymentIntent, parseCreatePaymentIntentInput, paymentsRuntimeFlag } from "../src/payments";

describe("payments domain integration", () => {
  it("mantiene export de handler y contrato", () => {
    expect(createPaymentIntent).toBeTruthy();
    expect(paymentsRuntimeFlag).toBe("payments.routes.enabled");
    expect(parseCreatePaymentIntentInput({ tenantId: "tenant-a", orderId: "ord-1", amount: 1200 })).toEqual({
      tenantId: "tenant-a",
      orderId: "ord-1",
      amount: 1200,
    });
  });
});
