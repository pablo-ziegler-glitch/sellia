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
    // Fallback: copy to clipboard
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
      className="inline-flex items-center justify-center w-full border border-gray-300 text-gray-700 font-medium rounded-lg px-6 py-3 hover:border-blue-400 hover:text-blue-600 transition-colors"
    >
      {copied ? "¡Enlace copiado!" : "Compartir"}
    </button>
  );
}
