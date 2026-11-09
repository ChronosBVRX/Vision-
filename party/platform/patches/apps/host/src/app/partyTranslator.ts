import Phaser from "phaser";

interface Dictionary {
  exact: Record<string, string>;
  phrases: Record<string, string>;
}

let exact: Record<string, string> = {};
let phraseEntries: Array<[string, string]> = [];
let installed = false;
let gameRef: Phaser.Game | null = null;
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

function translateExistingPhaser(): void {
  if (!gameRef) return;
  const visit = (entries: Phaser.GameObjects.GameObject[]): void => {
    for (const entry of entries) {
      const anyEntry = entry as unknown as { text?: unknown; list?: Phaser.GameObjects.GameObject[]; setText?: (value: string) => void };
      if (typeof anyEntry.text === "string" && typeof anyEntry.setText === "function") {
        const next = translateText(anyEntry.text);
        if (next !== anyEntry.text) anyEntry.setText(next);
      }
      if (Array.isArray(anyEntry.list)) visit(anyEntry.list);
    }
  };
  for (const scene of gameRef.scene.getScenes(true)) {
    visit(scene.children.list);
  }
}

function patchPhaserText(): void {
  const textProto = Phaser.GameObjects.Text?.prototype as unknown as {
    setText?: (value: string) => void;
    __partyPatched?: boolean;
  } | undefined;
  if (textProto && !textProto.__partyPatched) {
    const original = textProto.setText!;
    textProto.setText = function patchedSetText(this: Phaser.GameObjects.Text, value: string): Phaser.GameObjects.Text {
      original.call(this, translateText(value));
      return this;
    };
    textProto.__partyPatched = true;
  }
  const bitmapProto = (Phaser.GameObjects as unknown as { BitmapText?: { prototype: { setText?: (value: string) => void; __partyPatched?: boolean } } }).BitmapText?.prototype;
  if (bitmapProto && !bitmapProto.__partyPatched && bitmapProto.setText) {
    const original = bitmapProto.setText;
    bitmapProto.setText = function patchedBitmapSetText(this: unknown, value: string): void {
      original.call(this, translateText(value));
    };
    bitmapProto.__partyPatched = true;
  }
}

export function installPartyTranslator(getGame: () => Phaser.Game | null): void {
  if (installed) return;
  installed = true;
  gameRef = getGame();
  patchPhaserText();
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
      translateExistingPhaser();
      translateDomNode(document.body);
    })
    .catch(() => {});
}
