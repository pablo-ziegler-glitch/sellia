import { describe, expect, it } from "vitest";
import { migrateLandingConfigDocument } from "../src/landingConfig";

const validLandingVersion = {
  hero: { title: "Hero", subtitle: "Sub", primaryCtaLabel: "Comprar" },
  story: { title: "Nuestra historia", paragraphs: ["Texto"] },
  highlights: { items: [{ title: "Envio", description: "24hs" }] },
  catalogPreview: { title: "Destacados", productIds: ["p1"] },
  socialProof: { testimonials: [{ quote: "Excelente", author: "Ana" }] },
  faq: { items: [{ question: "¿Envian?", answer: "Sí" }] },
  ctaFinal: { title: "Listo para comprar", buttonLabel: "Ver catálogo" },
  footer: { copyright: "© Sellia" },
};

describe("landingConfig schema + migration", () => {
  it("migra de v1 a v2 agregando metadata base", () => {
    const migrated = migrateLandingConfigDocument({
      schemaVersion: 1,
      draftVersion: validLandingVersion,
      publishedVersion: null,
    });

    expect(migrated.schemaVersion).toBe(2);
    expect(migrated.metadata).toEqual({ experimentTags: [] });
  });

  it("rechaza draftVersion inválido", () => {
    expect(() =>
      migrateLandingConfigDocument({
        schemaVersion: 1,
        draftVersion: { ...validLandingVersion, hero: { subtitle: "sin title", primaryCtaLabel: "x" } },
        publishedVersion: null,
      })
    ).toThrow(/draftVersion inválido/i);
  });
});
