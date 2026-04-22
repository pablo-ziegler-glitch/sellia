"use client";

import { useState } from "react";

type Props = {
  productId: string;
  productName: string;
};

export default function ProductInquiryForm({ productId, productName }: Props) {
  const [name, setName] = useState("");
  const [message, setMessage] = useState(`Hola, me interesa ${productName}.`);
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [feedback, setFeedback] = useState("");

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (name.trim().length < 2 || message.trim().length < 4) {
      setStatus("error");
      setFeedback("Completá nombre y mensaje para enviar la consulta.");
      return;
    }

    try {
      setStatus("loading");
      setFeedback("");
      const response = await fetch("/api/product-inquiries", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productId, productName, name, message }),
      });

      if (!response.ok) {
        throw new Error("No se pudo enviar la consulta");
      }

      setStatus("success");
      setFeedback("¡Consulta enviada! Te responderemos por WhatsApp o email.");
      setMessage(`Hola, me interesa ${productName}.`);
    } catch {
      setStatus("error");
      setFeedback("No pudimos enviar tu consulta ahora. Intentá nuevamente.");
    }
  }

  return (
    <form
      onSubmit={onSubmit}
      className="space-y-3 rounded-2xl bg-[var(--surface-low)] p-5"
      style={{ boxShadow: "var(--ambient-shadow)" }}
    >
      <h2 className="text-sm font-semibold uppercase tracking-widest text-[var(--muted)]">
        Consulta rápida
      </h2>
      <label className="block text-sm text-[var(--foreground)]">
        Nombre
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          minLength={2}
          maxLength={80}
          required
          className="mt-1 w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 outline-none focus:border-[var(--primary)]"
        />
      </label>
      <label className="block text-sm text-[var(--foreground)]">
        Mensaje
        <textarea
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          minLength={4}
          maxLength={500}
          rows={4}
          required
          className="mt-1 w-full rounded-lg border border-[color:color-mix(in_srgb,var(--border)_30%,transparent)] bg-[var(--surface-highest)] px-3 py-2 outline-none focus:border-[var(--primary)]"
        />
      </label>
      <button
        type="submit"
        disabled={status === "loading"}
        className="brand-gradient inline-flex w-full items-center justify-center rounded-full px-4 py-2.5 text-sm font-semibold text-white"
        style={{ opacity: status === "loading" ? 0.7 : 1 }}
      >
        {status === "loading" ? "Enviando..." : "Enviar consulta"}
      </button>
      {feedback && (
        <p className="text-xs" style={{ color: status === "error" ? "#ba1a1a" : "var(--secondary)" }}>
          {feedback}
        </p>
      )}
    </form>
  );
}
