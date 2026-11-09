interface Dictionary {
  exact: Record<string, string>;
  phrases: Record<string, string>;
}

let exact: Record<string, string> = {};
let phraseEntries: Array<[string, string]> = [];
let installed = false;
const translated = new WeakSet<Node>();

function replacePhrase(value: string, from: string, to: string): string {
  if (!value.includes(from)) return value;
  const leading = from.match(/^\s+/)?.[0] ?? "";
  const trailing = from.match(/\s+$/)?.[0] ?? "";
  let replacement = to;
  if (leading && !replacement.startsWith(leading)) replacement = leading + replacement.replace(/^\s+/, "");
  if (trailing && !replacement.endsWith(trailing)) replacement = replacement.replace(/\s+$/, "") + trailing;
  return value.split(from).join(replacement);
}

function translateText(value: unknown): string {
  const raw = String(value ?? "");
  if (!raw) return raw;
  if (exact[raw]) return exact[raw];
  let out = raw;
  for (const [from, to] of phraseEntries) out = replacePhrase(out, from, to);
  return out;
}

function skipElement(element: Element | null): boolean {
  if (!element) return false;
  const tag = element.tagName;
  return tag === "SCRIPT" || tag === "STYLE" || tag === "TEXTAREA" || tag === "CODE"
    || tag === "PRE" || element.hasAttribute("data-party-no-translate");
}

function translateDomNode(node: Node): void {
  if (node.nodeType === Node.TEXT_NODE) {
    const parent = node.parentElement;
    if (!parent || skipElement(parent)) return;
    const current = node.nodeValue ?? "";
    const next = translateText(current);
    if (next !== current) node.nodeValue = next;
    return;
  }
  if (!(node instanceof Element)) return;
  if (skipElement(node)) return;
  for (const attr of ["placeholder", "title", "aria-label", "alt"]) {
    const current = node.getAttribute(attr);
    if (current) {
      const next = translateText(current);
      if (next !== current) node.setAttribute(attr, next);
    }
  }
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
  let child = walker.nextNode();
  while (child) {
    translateDomNode(child);
    child = walker.nextNode();
  }
}

export function installPartyTranslator(): void {
  if (installed) return;
  installed = true;
  if (typeof document !== "undefined" && document.body) translateDomNode(document.body);
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === "characterData") translateDomNode(mutation.target);
      else for (const node of mutation.addedNodes) translateDomNode(node);
    }
  });
  observer.observe(document.body, { childList: true, subtree: true, characterData: true });
  void fetch("/party-i18n/es.json?v=20261107-1", { cache: "no-cache" })
    .then((response) => response.ok ? response.json() as Promise<Dictionary> : null)
    .then((dictionary) => {
      if (!dictionary) return;
      exact = dictionary.exact ?? {};
      phraseEntries = Object.entries(dictionary.phrases ?? {}).sort((a, b) => b[0].length - a[0].length);
      translateDomNode(document.body);
    })
    .catch(() => {});
}
