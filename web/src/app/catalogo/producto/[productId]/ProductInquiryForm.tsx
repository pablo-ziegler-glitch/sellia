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
    <form onSubmit={onSubmit} className="rounded-xl p-5 space-y-3" style={{ background: "var(--surface)", border: "1px solid var(--border)" }}>
      <h2 className="text-sm font-semibold tracking-widest uppercase" style={{ color: "var(--muted)" }}>
        Consulta rápida
      </h2>
      <label className="block text-sm" style={{ color: "var(--foreground)" }}>
        Nombre
        <input
          type="text"
          value={name}
          onChange={(event) => setName(event.target.value)}
          minLength={2}
          maxLength={80}
          required
          className="mt-1 w-full rounded px-3 py-2"
          style={{ background: "var(--background)", border: "1px solid var(--border)" }}
        />
      </label>
      <label className="block text-sm" style={{ color: "var(--foreground)" }}>
        Mensaje
        <textarea
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          minLength={4}
          maxLength={500}
          rows={4}
          required
          className="mt-1 w-full rounded px-3 py-2"
          style={{ background: "var(--background)", border: "1px solid var(--border)" }}
        />
      </label>
      <button
        type="submit"
        disabled={status === "loading"}
        className="inline-flex items-center justify-center w-full rounded py-2.5 px-4 text-sm font-semibold"
        style={{ background: "var(--gold)", color: "#0a0a0a", opacity: status === "loading" ? 0.7 : 1 }}
      >
        {status === "loading" ? "Enviando..." : "Enviar consulta"}
      </button>
      {feedback && (
        <p className="text-xs" style={{ color: status === "error" ? "#ef4444" : "#4ade80" }}>
          {feedback}
        </p>
      )}
    </form>
  );
}
