import Phaser from "phaser";
import * as THREE from "three";
import { getMageDuelShapeIconPath } from "../protocol.js";
import type {
  MageDuelDuelistState,
  MageDuelEffectState,
  MageDuelGestureColor,
  MageDuelProjectileState,
  MageDuelPublicState,
  MageDuelSpellDefinition,
  MageDuelSpellId
} from "../protocol.js";
import { magicDuellManifest } from "../manifest.js";
import { MageDuel3dAudioSystem } from "./MageDuel3dAudioSystem.js";
import { renderRoundScreens } from "./roundScreens.js";
import { tokens } from "./platformTheme.js";

interface HostClientLike {
  subscribe(callback: (state: HostAppStateLike) => void): () => void;
}

interface HostAppStateLike {
  room?: { language?: "de" | "en" } | null;
  game?: {
    state?: unknown;
  } | null;
}

type DuelistVisual = {
  root: THREE.Group;
  body: THREE.Mesh;
  shield: THREE.Mesh;
  castRing: THREE.Mesh;
  regenerationRing: THREE.Mesh;
  fireWall: THREE.Mesh;
};

type StatusPanel = {
  root: HTMLDivElement;
  name: HTMLDivElement;
  hpFill: HTMLDivElement;
  hpValue: HTMLDivElement;
  manaFill: HTMLDivElement;
  manaValue: HTMLDivElement;
  detail: HTMLDivElement;
  spellRow: HTMLDivElement;
};

const arenaRadius = 6;
const duelistPositions = [
  new THREE.Vector3(-4, 0, 0),
  new THREE.Vector3(4, 0, 0)
] as const;

const spellColorHex = {
  red: "#ef4444",
  blue: "#3b82f6",
  green: "#22c55e"
} as const satisfies Record<MageDuelGestureColor, string>;

function clampPct(value: number): number {
  return Math.max(0, Math.min(100, value));
}

function formatSeconds(ms: number): string {
  return `${Math.max(0, ms / 1000).toFixed(1)}s`;
}

function parseColor(color: string, fallback: string): THREE.Color {
  try {
    return new THREE.Color(color);
  } catch {
    return new THREE.Color(fallback);
  }
}

function createMaterial(color: string, emissive = "#000000", opacity = 1): THREE.MeshStandardMaterial {
  return new THREE.MeshStandardMaterial({
    color,
    emissive,
    roughness: 0.48,
    metalness: 0.08,
    transparent: opacity < 1,
    opacity
  });
}

function disposeObject(object: THREE.Object3D): void {
  object.traverse((child) => {
    const mesh = child as THREE.Mesh;

    if (mesh.geometry) {
      mesh.geometry.dispose();
    }

    const material = mesh.material;

    if (Array.isArray(material)) {
      for (const entry of material) {
        entry.dispose();
      }
    } else if (material) {
      material.dispose();
    }
  });
}

export class MageDuel3dHostScene extends Phaser.Scene {
  private english = false;
  private unsubscribe?: () => void;
  private root?: HTMLDivElement;
  private statusRoot?: HTMLDivElement;
  private statusPanels: StatusPanel[] = [];
  private threeRenderer?: THREE.WebGLRenderer;
  private scene3d?: THREE.Scene;
  private viewCameras: THREE.PerspectiveCamera[] = [];
  private duelists = new Map<string, DuelistVisual>();
  private effects = new Map<string, THREE.Object3D>();
  private projectiles = new Map<string, THREE.Object3D>();
  private latestState: MageDuelPublicState | null = null;
  private audio = new MageDuel3dAudioSystem();
  private knownCastIds = new Set<string>();
  private knownEffects = new Map<string, MageDuelEffectState>();
  private knownProjectiles = new Map<string, MageDuelProjectileState>();
  private animationId: number | null = null;
  private lastWidth = 0;
  private lastHeight = 0;

  constructor() {
    super(magicDuellManifest.hostView);
  }

  create(): void {
    const client = this.registry.get("hostClient") as HostClientLike;

    this.cameras.main.setBackgroundColor(tokens().color.background);
    this.createThreeSurface();
    this.createScene();
    this.audio.attachUnlockListeners();
    this.unsubscribe = client.subscribe((state) => {
      this.english = state.room?.language === "en";
      // Intro and result screens belong to this game, not the platform.
      if (renderRoundScreens(this, state)) {
        return;
      }

      this.latestState = (state.game?.state ?? null) as MageDuelPublicState | null;
      this.updateAudio(this.latestState);
      this.updateStatus();
    });

    this.startLoop();
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.destroyThreeSurface();
    });
  }

  private createThreeSurface(): void {
    const parent = document.getElementById("app");

    if (!parent) {
      throw new Error("Host app root missing.");
    }

    parent.style.position = "relative";

    const root = document.createElement("div");
    Object.assign(root.style, {
      position: "absolute",
      inset: "0",
      zIndex: "1",
      overflow: "hidden",
      background: tokens().color.background,
      pointerEvents: "none"
    } satisfies Partial<CSSStyleDeclaration>);

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x020617, 1);
    renderer.autoClear = false;
    renderer.domElement.style.width = "100%";
    renderer.domElement.style.height = "100%";
    renderer.domElement.style.display = "block";
    root.appendChild(renderer.domElement);

    const statusRoot = document.createElement("div");
    Object.assign(statusRoot.style, {
      position: "absolute",
      left: "20px",
      right: "20px",
      top: "18px",
      display: "grid",
      gridTemplateColumns: "minmax(190px, 340px) minmax(190px, 340px)",
      justifyContent: "space-between",
      alignItems: "start",
      gap: "18px",
      pointerEvents: "none",
      fontFamily: "Inter, system-ui, sans-serif"
    } satisfies Partial<CSSStyleDeclaration>);
    root.appendChild(statusRoot);

    this.statusPanels = [0, 1].map((index) => this.createStatusPanel(statusRoot, index));
    parent.appendChild(root);
    this.root = root;
    this.statusRoot = statusRoot;
    this.threeRenderer = renderer;
  }

  private createStatusPanel(parent: HTMLElement, index: number): StatusPanel {
    const root = document.createElement("div");
    Object.assign(root.style, {
      display: "grid",
      gap: "7px",
      padding: "10px 11px",
      borderRadius: "12px",
      border: "1px solid rgba(148, 163, 184, 0.18)",
      background: "linear-gradient(180deg, rgba(15, 23, 42, 0.74), rgba(2, 6, 23, 0.56))",
      boxShadow: "0 14px 30px rgba(2, 6, 23, 0.28)",
      color: tokens().color.textSoft,
      backdropFilter: "blur(10px)",
      boxSizing: "border-box",
      width: "100%"
    } satisfies Partial<CSSStyleDeclaration>);

    const name = document.createElement("div");
    name.style.fontWeight = "900";
    name.style.fontSize = "14px";
    name.style.lineHeight = "1";

    const hpBar = this.createHudBar("#ef4444", "#7f1d1d");
    const hpFill = hpBar.querySelector<HTMLDivElement>("[data-fill]");
    const hpValue = hpBar.querySelector<HTMLDivElement>("[data-value]");
    const manaBar = this.createHudBar("#38bdf8", "#075985");
    const manaFill = manaBar.querySelector<HTMLDivElement>("[data-fill]");
    const manaValue = manaBar.querySelector<HTMLDivElement>("[data-value]");

    const detail = document.createElement("div");
    detail.style.fontSize = "11px";
    detail.style.color = tokens().color.muted;
    detail.style.minHeight = "13px";

    const spellRow = document.createElement("div");
    Object.assign(spellRow.style, {
      display: "flex",
      flexWrap: "wrap",
      gap: "5px",
      minHeight: "0"
    } satisfies Partial<CSSStyleDeclaration>);

    root.append(name, hpBar, manaBar, detail, spellRow);
    parent.appendChild(root);

    if (index === 1) {
      root.style.textAlign = "right";
      root.style.justifySelf = "end";
      spellRow.style.justifyContent = "flex-end";
    }

    return {
      root,
      name,
      hpFill: hpFill ?? document.createElement("div"),
      hpValue: hpValue ?? document.createElement("div"),
      manaFill: manaFill ?? document.createElement("div"),
      manaValue: manaValue ?? document.createElement("div"),
      detail,
      spellRow
    };
  }

  private createHudBar(fillColor: string, shadowColor: string): HTMLDivElement {
    const bar = document.createElement("div");
    Object.assign(bar.style, {
      position: "relative",
      height: "17px",
      overflow: "hidden",
      borderRadius: "999px",
      border: "1px solid rgba(226, 232, 240, 0.16)",
      background: "rgba(15, 23, 42, 0.82)"
    } satisfies Partial<CSSStyleDeclaration>);

    const fill = document.createElement("div");
    fill.dataset.fill = "true";
    Object.assign(fill.style, {
      position: "absolute",
      inset: "0 auto 0 0",
      width: "100%",
      borderRadius: "999px",
      background: `linear-gradient(90deg, ${shadowColor}, ${fillColor})`,
      boxShadow: `0 0 16px ${fillColor}66`,
      transition: "width 180ms ease"
    } satisfies Partial<CSSStyleDeclaration>);

    const value = document.createElement("div");
    value.dataset.value = "true";
    Object.assign(value.style, {
      position: "relative",
      zIndex: "1",
      height: "100%",
      display: "grid",
      placeItems: "center",
      fontSize: "10px",
      fontWeight: "900",
      letterSpacing: "0",
      color: tokens().color.text,
      textShadow: "0 1px 2px rgba(2, 6, 23, 0.9)"
    } satisfies Partial<CSSStyleDeclaration>);

    bar.append(fill, value);
    return bar;
  }

  private createScene(): void {
    const scene = new THREE.Scene();
    scene.background = new THREE.Color("#020617");
    scene.fog = new THREE.Fog("#020617", 8, 20);

    const ambient = new THREE.HemisphereLight("#bae6fd", "#1e1b4b", 1.2);
    scene.add(ambient);

    const keyLight = new THREE.DirectionalLight("#ffffff", 2.2);
    keyLight.position.set(0, 8, 5);
    scene.add(keyLight);

    const floor = new THREE.Mesh(
      new THREE.CircleGeometry(arenaRadius, 80),
      new THREE.MeshStandardMaterial({
        color: tokens().color.surface,
        emissive: "#06111f",
        roughness: 0.62,
        metalness: 0.05
      })
    );
    floor.rotation.x = -Math.PI / 2;
    scene.add(floor);

    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(arenaRadius, 0.045, 8, 160),
      createMaterial("#38bdf8", "#0ea5e9", 0.92)
    );
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 0.035;
    scene.add(ring);

    const midLine = new THREE.Mesh(
      new THREE.BoxGeometry(0.045, 0.04, arenaRadius * 1.35),
      createMaterial("#64748b", "#0f172a", 0.8)
    );
    midLine.position.y = 0.04;
    scene.add(midLine);

    this.viewCameras = [0, 1].map(() => new THREE.PerspectiveCamera(62, 1, 0.1, 100));
    this.scene3d = scene;
  }

  private startLoop(): void {
    const render = () => {
      this.animationId = window.requestAnimationFrame(render);
      this.updateVisuals();
      this.updateStatus();
      this.renderViews();
    };

    render();
  }

  private ensureRendererSize(): { width: number; height: number } {
    const renderer = this.threeRenderer;
    const root = this.root;

    if (!renderer || !root) {
      return { width: 0, height: 0 };
    }

    const width = Math.max(1, root.clientWidth);
    const height = Math.max(1, root.clientHeight);

    if (width !== this.lastWidth || height !== this.lastHeight) {
      renderer.setSize(width, height, false);
      this.lastWidth = width;
      this.lastHeight = height;
    }

    return { width, height };
  }

  private renderViews(): void {
    const renderer = this.threeRenderer;
    const scene = this.scene3d;

    if (!renderer || !scene) {
      return;
    }

    const { width, height } = this.ensureRendererSize();

    if (width <= 0 || height <= 0) {
      return;
    }

    renderer.clear();
    renderer.setScissorTest(true);

    for (let index = 0; index < this.viewCameras.length; index += 1) {
      const x = index === 0 ? 0 : width / 2;
      const viewWidth = width / 2;
      const camera = this.viewCameras[index];
      camera.aspect = viewWidth / height;
      this.updateViewCamera(camera, index as 0 | 1, camera.aspect);
      camera.updateProjectionMatrix();
      renderer.setViewport(x, 0, viewWidth, height);
      renderer.setScissor(x, 0, viewWidth, height);
      renderer.render(scene, camera);
    }

    renderer.setScissorTest(false);
  }

  private updateViewCamera(camera: THREE.PerspectiveCamera, slot: 0 | 1, aspect: number): void {
    const own = duelistPositions[slot];
    const opponent = duelistPositions[slot === 0 ? 1 : 0];
    const awayFromOpponent = own.clone().sub(opponent).normalize();
    const narrowViewScale = aspect < 0.85 ? 1.22 : 1;
    const zSign = 1;

    camera.fov = aspect < 0.85 ? 70 : 64;
    camera.position.set(
      own.x + awayFromOpponent.x * 4.15 * narrowViewScale,
      5.25 * narrowViewScale,
      zSign * 3.45 * narrowViewScale
    );
    camera.lookAt(new THREE.Vector3(0, 1.12, 0));
  }

  private updateVisuals(): void {
    const scene = this.scene3d;
    const state = this.latestState;

    if (!scene || !state) {
      return;
    }

    const activeIds = new Set<string>();

    for (const duelist of state.duelists) {
      activeIds.add(duelist.playerId);
      let visual = this.duelists.get(duelist.playerId);

      if (!visual) {
        visual = this.createDuelistVisual(duelist.color);
        this.duelists.set(duelist.playerId, visual);
        scene.add(visual.root);
      }

      const position = duelistPositions[duelist.slot] ?? duelistPositions[0];
      visual.root.position.copy(position);
      visual.root.rotation.y = duelist.slot === 0 ? Math.PI / 2 : -Math.PI / 2;
      visual.root.scale.setScalar(duelist.hp <= 0 ? 0.72 : 1);
      (visual.body.material as THREE.MeshStandardMaterial).color = parseColor(duelist.color, duelist.slot === 0 ? "#38bdf8" : "#fb7185");
      visual.shield.visible = duelist.shieldHp > 0;
      visual.shield.scale.setScalar(1 + Math.sin(Date.now() / 130) * 0.025);
      visual.castRing.visible = Boolean(duelist.activeCast);
      visual.castRing.rotation.z += 0.035;
      visual.regenerationRing.visible = duelist.regeneration !== null;
      visual.regenerationRing.rotation.z -= 0.018;
      visual.fireWall.visible = duelist.fireWallEndsAt !== null && duelist.fireWallEndsAt > Date.now();
    }

    for (const [playerId, visual] of this.duelists) {
      if (!activeIds.has(playerId)) {
        scene.remove(visual.root);
        disposeObject(visual.root);
        this.duelists.delete(playerId);
      }
    }

    this.updateEffects(state.effects);
    this.updateProjectiles(state.projectiles);
  }

  private createDuelistVisual(color: string): DuelistVisual {
    const root = new THREE.Group();
    const body = new THREE.Mesh(
      new THREE.CapsuleGeometry(0.48, 1.05, 6, 14),
      createMaterial(color, "#020617")
    );
    body.position.y = 0.95;
    root.add(body);

    const head = new THREE.Mesh(new THREE.SphereGeometry(0.33, 24, 18), createMaterial("#f8fafc", "#111827"));
    head.position.y = 1.82;
    root.add(head);

    const hat = new THREE.Mesh(new THREE.ConeGeometry(0.42, 0.78, 24), createMaterial("#312e81", "#1d1a5a"));
    hat.position.y = 2.35;
    root.add(hat);

    const staff = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.045, 1.85, 12), createMaterial("#d6d3d1", "#111827"));
    staff.position.set(0.42, 1.05, -0.32);
    staff.rotation.z = 0.15;
    root.add(staff);

    const crystal = new THREE.Mesh(new THREE.IcosahedronGeometry(0.18, 1), createMaterial("#67e8f9", "#0891b2"));
    crystal.position.set(0.55, 2.02, -0.34);
    root.add(crystal);

    const shield = new THREE.Mesh(
      new THREE.SphereGeometry(0.95, 32, 20),
      new THREE.MeshStandardMaterial({
        color: "#60a5fa",
        emissive: "#1d4ed8",
        transparent: true,
        opacity: 0.24,
        roughness: 0.15,
        metalness: 0
      })
    );
    shield.position.y = 1.15;
    shield.visible = false;
    root.add(shield);

    const castRing = new THREE.Mesh(
      new THREE.TorusGeometry(0.82, 0.035, 8, 64),
      createMaterial("#facc15", "#ca8a04", 0.85)
    );
    castRing.position.y = 0.08;
    castRing.rotation.x = Math.PI / 2;
    castRing.visible = false;
    root.add(castRing);

    const fireWall = new THREE.Mesh(
      new THREE.BoxGeometry(2.55, 1.45, 0.08),
      new THREE.MeshStandardMaterial({
        color: "#ef4444",
        emissive: "#7f1d1d",
        transparent: true,
        opacity: 0.28,
        roughness: 0.25,
        metalness: 0
      })
    );
    fireWall.position.set(0, 1.02, 1.05);
    fireWall.visible = false;
    root.add(fireWall);

    const regenerationRing = new THREE.Mesh(
      new THREE.TorusGeometry(0.82, 0.045, 8, 72),
      createMaterial("#4ade80", "#16a34a", 0.82)
    );
    regenerationRing.position.y = 0.055;
    regenerationRing.rotation.x = Math.PI / 2;
    regenerationRing.visible = false;
    root.add(regenerationRing);

    return { root, body, shield, castRing, regenerationRing, fireWall };
  }

  private updateProjectiles(projectiles: MageDuelProjectileState[]): void {
    const scene = this.scene3d;

    if (!scene) {
      return;
    }

    const activeIds = new Set(projectiles.map((projectile) => projectile.id));

    for (const projectile of projectiles) {
      let object = this.projectiles.get(projectile.id);

      if (!object) {
        object = this.createProjectileObject(projectile);
        this.projectiles.set(projectile.id, object);
        scene.add(object);
      }

      const owner = this.latestState?.duelists.find((duelist) => duelist.playerId === projectile.ownerPlayerId);
      const target = this.latestState?.duelists.find((duelist) => duelist.playerId === projectile.targetPlayerId);

      if (!owner || !target) {
        continue;
      }

      const start = duelistPositions[owner.slot].clone().setY(1.35);
      const end = duelistPositions[target.slot].clone().setY(1.35);
      const progress = Math.max(
        0,
        Math.min(1, (Date.now() - projectile.createdAt) / Math.max(1, projectile.impactAt - projectile.createdAt))
      );
      const arc = Math.sin(progress * Math.PI) * 0.7;
      object.position.lerpVectors(start, end, progress);
      object.position.y += arc;
      object.rotation.x += 0.08;
      object.rotation.y += 0.12;
    }

    for (const [projectileId, object] of this.projectiles) {
      if (!activeIds.has(projectileId)) {
        scene.remove(object);
        disposeObject(object);
        this.projectiles.delete(projectileId);
      }
    }
  }

  private createProjectileObject(projectile: MageDuelProjectileState): THREE.Object3D {
    const root = new THREE.Group();
    const spell = this.latestState?.spellBook.find((entry) => entry.id === projectile.spellId);
    const color =
      spell?.color === "red" ? "#fb923c" : spell?.color === "green" ? "#86efac" : "#93c5fd";
    const emissive =
      spell?.color === "red" ? "#ef4444" : spell?.color === "green" ? "#16a34a" : "#2563eb";
    const material = createMaterial(color, emissive, 0.95);
    const aura = new THREE.Mesh(new THREE.SphereGeometry(0.38, 18, 12), createMaterial(color, emissive, 0.22));
    aura.scale.setScalar(spell?.shape === "lightning" ? 0.72 : 1);
    root.add(aura);

    switch (spell?.shape) {
      case "square":
        root.add(new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.42, 0.42), material));
        break;
      case "spiral":
        root.add(new THREE.Mesh(new THREE.TorusKnotGeometry(0.18, 0.045, 80, 8), material));
        break;
      case "triple_circle":
        for (let index = 0; index < 3; index += 1) {
          const orb = new THREE.Mesh(new THREE.SphereGeometry(0.13, 14, 10), material.clone());
          orb.position.set((index - 1) * 0.22, Math.sin(index * 2.1) * 0.06, 0);
          root.add(orb);
        }
        break;
      case "triangle_slash": {
        const cone = new THREE.Mesh(new THREE.ConeGeometry(0.27, 0.46, 3), material);
        const slash = new THREE.Mesh(new THREE.BoxGeometry(0.52, 0.045, 0.045), material.clone());
        slash.rotation.z = -0.45;
        root.add(cone, slash);
        break;
      }
      case "lightning": {
        const bolt = new THREE.Mesh(new THREE.OctahedronGeometry(0.28, 0), material);
        bolt.scale.set(0.55, 1.45, 0.55);
        root.add(bolt);
        break;
      }
      case "zigzag":
      case "wave": {
        for (let index = 0; index < 3; index += 1) {
          const segment = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.055, 0.055), material.clone());
          segment.position.x = (index - 1) * 0.2;
          segment.rotation.z = index % 2 === 0 ? 0.62 : -0.62;
          root.add(segment);
        }
        break;
      }
      case "cross": {
        const first = new THREE.Mesh(new THREE.BoxGeometry(0.48, 0.06, 0.06), material);
        const second = new THREE.Mesh(new THREE.BoxGeometry(0.48, 0.06, 0.06), material.clone());
        first.rotation.z = 0.78;
        second.rotation.z = -0.78;
        root.add(first, second);
        break;
      }
      case "chevron": {
        const left = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.055, 0.055), material);
        const right = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.055, 0.055), material.clone());
        left.rotation.z = 0.65;
        right.rotation.z = -0.65;
        left.position.x = -0.11;
        right.position.x = 0.11;
        root.add(left, right);
        break;
      }
      default:
        root.add(new THREE.Mesh(new THREE.SphereGeometry(0.22, 20, 14), material));
        break;
    }

    return root;
  }

  private updateEffects(effects: MageDuelEffectState[]): void {
    const scene = this.scene3d;

    if (!scene) {
      return;
    }

    const activeIds = new Set(effects.map((effect) => effect.id));

    for (const effect of effects) {
      let object = this.effects.get(effect.id);

      if (!object) {
        object = this.createEffectObject(effect);
        this.effects.set(effect.id, object);
        scene.add(object);
      }

      const now = Date.now();
      const progress = 1 - Math.max(0, effect.endsAt - now) / Math.max(1, effect.endsAt - effect.createdAt);
      object.scale.setScalar(0.72 + progress * 0.78);
      object.rotation.y += 0.035;
    }

    for (const [effectId, object] of this.effects) {
      if (!activeIds.has(effectId)) {
        scene.remove(object);
        disposeObject(object);
        this.effects.delete(effectId);
      }
    }
  }

  private createEffectObject(effect: MageDuelEffectState): THREE.Object3D {
    const owner = this.latestState?.duelists.find((duelist) => duelist.playerId === effect.ownerPlayerId);
    const target = this.latestState?.duelists.find((duelist) => duelist.playerId === effect.targetPlayerId);
    const positionDuelist = target ?? owner;
    const position = positionDuelist ? duelistPositions[positionDuelist.slot] : new THREE.Vector3();
    const root = new THREE.Group();
    root.position.copy(position);

    if (effect.kind === "impact") {
      const spell = this.latestState?.spellBook.find((entry) => entry.id === effect.spellId);
      const color = spell?.color === "green" ? "#86efac" : spell?.color === "blue" ? "#93c5fd" : "#fb923c";
      const emissive = spell?.color === "green" ? "#16a34a" : spell?.color === "blue" ? "#2563eb" : "#ef4444";
      const geometry =
        spell?.shape === "square"
          ? new THREE.BoxGeometry(0.48, 0.48, 0.48)
          : spell?.shape === "spiral"
            ? new THREE.TorusKnotGeometry(0.28, 0.055, 64, 8)
            : spell?.shape === "lightning"
              ? new THREE.OctahedronGeometry(0.42, 0)
              : new THREE.SphereGeometry(0.35, 24, 16);
      const mesh = new THREE.Mesh(geometry, createMaterial(color, emissive, 0.92));
      mesh.position.y = 1.25;
      root.add(mesh);
      return root;
    }

    if (effect.kind === "heal" || effect.kind === "mana" || effect.kind === "boost" || effect.kind === "cleanse") {
      const color = effect.spellId === "mana_surge" ? "#60a5fa" : "#4ade80";
      const effectColor =
        effect.kind === "mana" ? "#60a5fa" : effect.kind === "cleanse" ? "#bae6fd" : effect.kind === "boost" ? "#a7f3d0" : color;
      const emissive =
        effect.kind === "mana" ? "#2563eb" : effect.kind === "cleanse" ? "#0891b2" : effect.kind === "boost" ? "#059669" : "#16a34a";
      const mesh = new THREE.Mesh(new THREE.TorusGeometry(0.75, 0.04, 10, 72), createMaterial(effectColor, emissive, 0.9));
      mesh.position.y = 1.2;
      mesh.rotation.x = Math.PI / 2;
      root.add(mesh);
      return root;
    }

    if (effect.kind === "dot") {
      const mesh = new THREE.Mesh(
        new THREE.TorusGeometry(0.9, 0.035, 8, 96),
        createMaterial(effect.spellId === "poison_spiral" ? "#86efac" : "#fb923c", effect.spellId === "poison_spiral" ? "#15803d" : "#dc2626", 0.82)
      );
      mesh.position.y = 0.11;
      mesh.rotation.x = Math.PI / 2;
      root.add(mesh);
      return root;
    }

    if (effect.kind === "shield") {
      const mesh = new THREE.Mesh(
        new THREE.SphereGeometry(1.05, 32, 18),
        new THREE.MeshStandardMaterial({
          color: "#93c5fd",
          emissive: "#2563eb",
          transparent: true,
          opacity: 0.22
        })
      );
      mesh.position.y = 1.15;
      root.add(mesh);
      return root;
    }

    if (effect.kind === "fire_wall") {
      const mesh = new THREE.Mesh(
        new THREE.TorusGeometry(0.75, 0.045, 8, 72),
        createMaterial("#f97316", "#dc2626", 0.86)
      );
      mesh.position.y = 0.2;
      mesh.rotation.x = Math.PI / 2;
      root.add(mesh);
      return root;
    }

    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(0.56, 0.04, 8, 64),
      createMaterial(effect.kind === "fizzle" ? "#94a3b8" : "#fde68a", effect.kind === "fizzle" ? "#475569" : "#f59e0b", 0.86)
    );
    ring.position.y = 0.18;
    ring.rotation.x = Math.PI / 2;
    root.add(ring);
    return root;
  }

  private updateAudio(state: MageDuelPublicState | null): void {
    if (!state) {
      this.audio.stopAll();
      this.knownCastIds.clear();
      this.knownEffects.clear();
      this.knownProjectiles.clear();
      return;
    }

    const nextCastIds = new Set<string>();

    for (const duelist of state.duelists) {
      if (!duelist.activeCast) {
        continue;
      }

      nextCastIds.add(duelist.activeCast.id);

      if (!this.knownCastIds.has(duelist.activeCast.id)) {
        this.audio.playCastStart(duelist.activeCast.spellId);
      }
    }

    const nextProjectiles = new Map<string, MageDuelProjectileState>();

    for (const projectile of state.projectiles) {
      nextProjectiles.set(projectile.id, projectile);

      if (!this.knownProjectiles.has(projectile.id)) {
        this.audio.playProjectileLaunch(projectile);
      }
    }

    for (const [projectileId, projectile] of this.knownProjectiles) {
      if (!nextProjectiles.has(projectileId)) {
        this.audio.stopProjectile(projectile);
      }
    }

    const nextEffects = new Map<string, MageDuelEffectState>();

    for (const effect of state.effects) {
      nextEffects.set(effect.id, effect);

      if (!this.knownEffects.has(effect.id)) {
        this.audio.playEffectStart(effect);
      }
    }

    for (const [effectId, effect] of this.knownEffects) {
      if (!nextEffects.has(effectId)) {
        this.audio.stopEffect(effect);
      }
    }

    this.knownCastIds = nextCastIds;
    this.knownProjectiles = nextProjectiles;
    this.knownEffects = nextEffects;
  }

  private updateStatus(): void {
    const state = this.latestState;
    const now = Date.now();

    for (const [index, panel] of this.statusPanels.entries()) {
      const duelist = state?.duelists[index];
      panel.root.style.visibility = duelist ? "visible" : "hidden";

      if (!duelist) {
        continue;
      }

      panel.name.textContent = duelist.name;
      panel.name.style.color = duelist.color;
      panel.hpValue.textContent = `${Math.ceil(duelist.hp)}/${duelist.maxHp}`;
      panel.hpFill.style.width = `${clampPct((duelist.hp / Math.max(1, duelist.maxHp)) * 100)}%`;
      panel.manaValue.textContent = `${Math.floor(duelist.mana)}/${duelist.maxMana}`;
      panel.manaFill.style.width = `${clampPct((duelist.mana / Math.max(1, duelist.maxMana)) * 100)}%`;
      panel.detail.textContent = duelist.activeCast
        ? `${this.english ? "Casting" : "Wirkt"} ${state?.spellBook.find((spell) => spell.id === duelist.activeCast?.spellId)?.displayName ?? (this.english ? "Spell" : "Zauber")}`
        : duelist.shieldHp > 0
          ? `${this.english ? "Shield" : "Schild"} ${Math.ceil(duelist.shieldHp)}`
          : "";
      panel.detail.style.display = panel.detail.textContent ? "block" : "none";
      this.renderSpellBadges(panel.spellRow, duelist, state?.spellBook ?? [], now);
    }
  }

  private renderSpellBadges(
    parent: HTMLDivElement,
    duelist: MageDuelDuelistState,
    spellBook: MageDuelSpellDefinition[],
    now: number
  ): void {
    parent.replaceChildren();

    const badges: Array<{
      key: string;
      spell: MageDuelSpellDefinition | undefined;
      fallbackColor: string;
      label: string;
      timeMs: number;
      active: boolean;
    }> = [];

    if (duelist.activeCast) {
      const spell = spellBook.find((entry) => entry.id === duelist.activeCast?.spellId);
      badges.push({
        key: `cast:${duelist.activeCast.id}`,
        spell,
        fallbackColor: "#facc15",
        label: this.english ? "Cast" : "Zauber",
        timeMs: duelist.activeCast.resolvesAt - now,
        active: true
      });
    }

    if (duelist.shieldEndsAt && duelist.shieldEndsAt > now) {
      badges.push({
        key: "active:shield",
        spell: spellBook.find((entry) => entry.id === "shield"),
        fallbackColor: "#60a5fa",
        label: this.english ? "Shield" : "Schild",
        timeMs: duelist.shieldEndsAt - now,
        active: true
      });
    }

    if (duelist.regeneration && duelist.regeneration.endsAt > now) {
      badges.push({
        key: "active:regeneration",
        spell: spellBook.find((entry) => entry.id === "regeneration"),
        fallbackColor: "#4ade80",
        label: this.english ? "Regen" : "Heilung",
        timeMs: duelist.regeneration.endsAt - now,
        active: true
      });
    }

    if (duelist.fireWallEndsAt && duelist.fireWallEndsAt > now) {
      badges.push({
        key: "active:fire_wall",
        spell: spellBook.find((entry) => entry.id === "fire_wall"),
        fallbackColor: "#f97316",
        label: this.english ? "Fire" : "Feuer",
        timeMs: duelist.fireWallEndsAt - now,
        active: true
      });
    }

    const visibleSpells = duelist.loadoutSpellIds
      .map((spellId) => spellBook.find((spell) => spell.id === spellId))
      .filter((spell): spell is MageDuelSpellDefinition => Boolean(spell));

    for (const spell of visibleSpells) {
      const remainingMs = (duelist.cooldownReadyAt[spell.id] ?? 0) - now;

      if (remainingMs <= 0) {
        continue;
      }

      badges.push({
        key: `cooldown:${spell.id}`,
        spell,
        fallbackColor: spellColorHex[spell.color],
        label: spell.displayName,
        timeMs: remainingMs,
        active: false
      });
    }

    for (const badge of badges.slice(0, 8)) {
      parent.appendChild(this.createSpellBadge(badge));
    }

    parent.style.display = badges.length > 0 ? "flex" : "none";
  }

  private createSpellBadge(badge: {
    spell: MageDuelSpellDefinition | undefined;
    fallbackColor: string;
    label: string;
    timeMs: number;
    active: boolean;
  }): HTMLDivElement {
    const color = badge.spell ? spellColorHex[badge.spell.color] : badge.fallbackColor;
    const root = document.createElement("div");
    Object.assign(root.style, {
      position: "relative",
      width: "34px",
      height: "34px",
      borderRadius: "8px",
      border: `1px solid ${color}88`,
      background: badge.active
        ? `radial-gradient(circle at 50% 35%, ${color}55, rgba(15, 23, 42, 0.92) 64%)`
        : "rgba(15, 23, 42, 0.82)",
      boxShadow: badge.active ? `0 0 16px ${color}55` : "0 8px 18px rgba(2, 6, 23, 0.22)",
      color: tokens().color.text,
      overflow: "hidden",
      display: "grid",
      placeItems: "center"
    } satisfies Partial<CSSStyleDeclaration>);
    root.title = badge.spell?.displayName ?? badge.label;

    const icon = document.createElement("div");
    Object.assign(icon.style, {
      width: "18px",
      height: "18px",
      backgroundColor: color,
      webkitMask: badge.spell
        ? `url(${getMageDuelShapeIconPath(badge.spell.shape)}) center / contain no-repeat`
        : "none",
      mask: badge.spell
        ? `url(${getMageDuelShapeIconPath(badge.spell.shape)}) center / contain no-repeat`
        : "none",
      transform: "translateY(-4px)"
    } satisfies Partial<CSSStyleDeclaration>);
    icon.textContent = badge.spell ? "" : "*";

    const time = document.createElement("div");
    time.textContent = formatSeconds(badge.timeMs);
    Object.assign(time.style, {
      position: "absolute",
      left: "0",
      right: "0",
      bottom: "3px",
      fontSize: "9px",
      lineHeight: "1",
      textAlign: "center",
      fontWeight: "900",
      color: tokens().color.textSoft,
      textShadow: "0 1px 2px rgba(2, 6, 23, 0.9)"
    } satisfies Partial<CSSStyleDeclaration>);

    root.append(icon, time);
    return root;
  }

  private destroyThreeSurface(): void {
    this.unsubscribe?.();
    this.unsubscribe = undefined;

    if (this.animationId !== null) {
      window.cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }

    for (const visual of this.duelists.values()) {
      disposeObject(visual.root);
    }

    for (const effect of this.effects.values()) {
      disposeObject(effect);
    }

    for (const projectile of this.projectiles.values()) {
      disposeObject(projectile);
    }

    this.audio.stopAll();
    this.knownCastIds.clear();
    this.knownEffects.clear();
    this.knownProjectiles.clear();
    this.duelists.clear();
    this.effects.clear();
    this.projectiles.clear();
    this.scene3d?.clear();
    this.threeRenderer?.dispose();
    this.root?.remove();
    this.root = undefined;
    this.statusRoot = undefined;
    this.statusPanels = [];
    this.threeRenderer = undefined;
    this.scene3d = undefined;
    this.viewCameras = [];
    this.latestState = null;
  }
}

export const hostGame = {
  id: magicDuellManifest.id,
  displayName: magicDuellManifest.displayName,
  sceneKey: magicDuellManifest.hostView,
  scene: MageDuel3dHostScene
} as const;
