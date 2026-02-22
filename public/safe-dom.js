(function initSafeDom(global) {
  const ALLOWED_PROTOCOLS = new Set(["https:"]);

  function sanitizeText(value) {
    if (value === null || value === undefined) return "";
    return String(value);
  }

  function toAllowedHttpsUrl(rawUrl) {
    if (typeof rawUrl !== "string" || rawUrl.trim() === "") return null;
    try {
      const parsed = new URL(rawUrl, window.location.origin);
      return ALLOWED_PROTOCOLS.has(parsed.protocol) ? parsed.toString() : null;
    } catch (_error) {
      return null;
    }
  }

  function setSafeUrl(element, attribute, rawUrl) {
    if (!element) return false;
    const safeUrl = toAllowedHttpsUrl(rawUrl);
    if (!safeUrl) {
      element.removeAttribute(attribute);
      return false;
    }
    element.setAttribute(attribute, safeUrl);
    return true;
  }

  function sanitizeRichHtml(dirtyHtml, options = {}) {
    const cleanInput = sanitizeText(dirtyHtml);
    if (global.DOMPurify && typeof global.DOMPurify.sanitize === "function") {
      return global.DOMPurify.sanitize(cleanInput, {
        ALLOWED_TAGS: options.allowedTags || ["b", "strong", "i", "em", "u", "br", "p", "ul", "ol", "li"],
        ALLOWED_ATTR: options.allowedAttrs || []
      });
    }
    return sanitizeText(cleanInput).replace(/[&<>"']/g, (char) => {
      const entities = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
      };
      return entities[char] || char;
    });
  }

  global.SafeDom = {
    sanitizeText,
    toAllowedHttpsUrl,
    setSafeUrl,
    sanitizeRichHtml
  };
})(window);
