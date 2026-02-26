import { describe, expect, it, vi } from "vitest";

const { axiosPostMock, axiosGetMock } = vi.hoisted(() => ({
  axiosPostMock: vi.fn(),
  axiosGetMock: vi.fn(),
}));

vi.mock("axios", () => ({
  default: {
    post: axiosPostMock,
    get: axiosGetMock,
  },
}));

import { createMpPaymentIntent, fetchMpPayment } from "../src/payments/payments-mp-adapter";

describe("payments-mp-adapter", () => {
  it("translates internal create intent payload into MP preference payload", async () => {
    axiosPostMock.mockResolvedValueOnce({
      data: {
        id: "pref-1",
        init_point: "https://mp/init",
        sandbox_init_point: "https://mp/sandbox",
      },
    });

    const result = await createMpPaymentIntent({
      accessToken: "token",
      tenantId: "tenant-a",
      orderId: "order-1",
      intentId: "intent-1",
      amount: 250,
      currency: "ARS",
      items: [{ title: "Plan", quantity: 1, unit_price: 250 }],
      metadata: { custom: true },
      description: "My plan",
      payerEmail: "user@example.com",
    });

    expect(axiosPostMock).toHaveBeenCalledWith(
      "https://api.mercadopago.com/checkout/preferences",
      expect.objectContaining({
        external_reference: "tenant-a::order-1::intent-1",
        metadata: expect.objectContaining({
          tenantId: "tenant-a",
          orderId: "order-1",
          intentId: "intent-1",
          custom: true,
        }),
      }),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer token" }),
      })
    );

    expect(result.preferenceId).toBe("pref-1");
    expect(result.initPoint).toBe("https://mp/init");
    expect(result.sandboxInitPoint).toBe("https://mp/sandbox");
  });

  it("normalizes MP payment response into canonical payload", async () => {
    axiosGetMock.mockResolvedValueOnce({
      data: {
        id: 123,
        status: "approved",
        transaction_amount: 500,
        currency_id: "ARS",
        external_reference: "tenant-a::order-9::intent-3",
        metadata: {},
      },
    });

    const result = await fetchMpPayment("token", "123");

    expect(result).toEqual(
      expect.objectContaining({
        providerPaymentId: "123",
        providerStatus: "approved",
        tenantId: "tenant-a",
        orderId: "order-9",
        intentId: "intent-3",
        amount: 500,
        currency: "ARS",
      })
    );
  });
});
