"use client";

import { useState } from "react";

interface Props {
  url: string;
  title: string;
  text: string;
}

export default function ShareButton({ url, title, text }: Props) {
  const [copied, setCopied] = useState(false);

  async function handleShare() {
    if (navigator.share) {
      try {
        await navigator.share({ title, text, url });
      } catch {
        // User cancelled or share failed — ignore
      }
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard not available
    }
  }

  return (
    <button
      onClick={handleShare}
      className="inline-flex items-center justify-center w-full rounded py-3 px-6 text-sm font-medium transition-all border"
      style={{
        borderColor: "var(--border)",
        color: "var(--muted)",
        background: "transparent",
      }}
    >
      {copied ? "¡Enlace copiado!" : "Compartir"}
    </button>
  );
}
