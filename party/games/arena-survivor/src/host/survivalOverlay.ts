import Phaser from "phaser";
import type { ArenaSurvivorState } from "../protocol.js";
import { frostfireWorld } from "../survivalWorld.js";

const colors = { shop: 0xfbbf24, forge: 0xa78bfa, chest: 0xd6ad72, rare_chest: 0x67e8f9, boss: 0xfb7185 };
export function createSurvivalOverlay(scene: Phaser.Scene) {
  const graphics = scene.add.graphics().setDepth(14);
  const props = new Map<string, Phaser.GameObjects.Image>();
  const names = new Map<string, Phaser.GameObjects.Text>();
  const overlay = document.createElement("div");
  overlay.hidden = true;
  overlay.style.cssText = "position:absolute;inset:0;pointer-events:none;color:#f8fafc;font:600 15px system-ui;z-index:25";
  scene.game.canvas.parentElement?.append(overlay);
  const status = document.createElement("div");
  status.style.cssText = "position:absolute;top:12px;left:50%;transform:translateX(-50%);background:#102335ed;border:1px solid #67e8f9;border-radius:12px;padding:10px 20px;text-align:center;min-width:260px";
  const map = document.createElement("canvas"); map.width = 192; map.height = 144;
  map.style.cssText = "position:absolute;right:12px;top:180px;width:192px;height:144px;background:#102335ed;border:1px solid #67e8f9;border-radius:8px";
  const markers = document.createElement("div"); overlay.append(status, map, markers);
  const xpTrack = document.createElement("div");
  xpTrack.style.cssText = "position:absolute;top:59px;left:50%;transform:translateX(-50%);width:280px;height:6px;background:#102335;border-radius:4px;overflow:hidden";
  const xpFill = document.createElement("div"); xpFill.style.cssText = "height:100%;background:linear-gradient(90deg,#67e8f9,#fb923c)";
  xpTrack.append(xpFill); overlay.append(xpTrack);
  return {
    hide() { overlay.hidden = true; graphics.clear(); props.forEach(p => p.setVisible(false)); names.forEach(p => p.setVisible(false)); },
    update(state: ArenaSurvivorState, language?: "de" | "en") {
      const s = state.survival; overlay.hidden = !s || state.result.outcome !== "running"; graphics.clear();
      if (overlay.hidden || !s) { props.forEach(p => p.setVisible(false)); names.forEach(p => p.setVisible(false)); return; }
      const en = language === "en";
      const labels = { shop: "Shop", forge: en ? "Forge" : "Schmiede", chest: en ? "Chest" : "Kiste", rare_chest: en ? "Rare chest" : "Seltene Kiste", boss: "Boss" };
      const time = `${Math.floor(state.elapsedMs / 60000)}:${Math.floor(state.elapsedMs / 1000 % 60).toString().padStart(2, "0")}`;
      const phase = s.pause === "chest" ? (en ? "Chest loot" : "Kistenfund") : s.pause === "level_up" ? "Level-Up" : s.pause === "victory" ? (en ? "Victory! Continue Endless?" : "Gewonnen! Endlos weiterspielen?") : s.pause === "forge" ? (en ? "Evolution Forge" : "Evolutionsschmiede") : s.pause === "shop" ? "Shop" : s.endless ? (en ? "Endless · ✓ Survival" : "Endlos · ✓ Survival") : "Frostfire Survival";
      status.textContent = `${phase} · ${time} · Lv. ${s.level} · XP ${s.experience}/${s.experienceToNextLevel}${s.pause && s.pause !== "chest" ? ` · ${s.readyPlayerIds.length}/${s.participantIds.length} ✓` : ""}`;
      xpFill.style.width = `${Math.min(100, s.experience / s.experienceToNextLevel * 100)}%`;
      const ctx = map.getContext("2d")!; ctx.clearRect(0, 0, 192, 144);
      if (scene.textures.exists(frostfireWorld.terrainKey)) {
        const ground = scene.textures.get(frostfireWorld.terrainKey).getSourceImage() as HTMLImageElement;
        for (let y = 0; y < 144; y += 20.48) for (let x = 0; x < 192; x += 20.48) ctx.drawImage(ground, x, y, 20.5, 20.5);
      }
      ctx.fillStyle = "#07152555"; ctx.fillRect(0, 0, 192, 144);
      markers.replaceChildren(); const camera = scene.cameras.main;
      const objectiveIds = new Set(s.objectives.map(o => o.id));
      for (const [id, prop] of props) if (!objectiveIds.has(id)) { prop.destroy(); props.delete(id); names.get(id)?.destroy(); names.delete(id); }
      for (const o of s.objectives) {
        const enemy = o.enemyId ? state.enemies.find(e => e.id === o.enemyId) : undefined;
        const x = enemy?.x ?? o.x, y = enemy?.y ?? o.y;
        const color = colors[o.kind];
        const pulse = 0.5 + Math.sin(state.elapsedMs / 650) * 0.15;
        graphics.lineStyle(2, color, pulse); graphics.strokeEllipse(x, y + 8, o.kind === "boss" ? (enemy?.radius ?? 35) * 2 + 24 : 124, 55);
        if (o.kind !== "boss") {
          const key = `survival-${o.kind.replace("_", "-")}`;
          if (scene.textures.exists(key)) {
            let prop = props.get(o.id);
            if (!prop) { prop = scene.add.image(x, y, key).setDepth(3).setOrigin(0.5, 0.7); props.set(o.id, prop); }
            const size = o.kind === "shop" ? 210 : o.kind === "forge" ? 170 : 76;
            prop.setVisible(true).setPosition(x, y).setDisplaySize(size, size);
            if (o.kind === "forge" || o.kind === "rare_chest") {
              graphics.fillStyle(color, pulse * 0.07); graphics.fillCircle(x, y - 20, size * 0.36);
            }
            if (o.kind === "shop" || o.kind === "forge") {
              let label = names.get(o.id);
              if (!label) { label = scene.add.text(x, y + 48, "", { fontFamily: "system-ui", fontSize: "13px", color: "#f8fafc", backgroundColor: "#102335cc", padding: { x: 8, y: 4 } }).setOrigin(0.5).setDepth(15); names.set(o.id, label); }
              label.setVisible(true).setText(labels[o.kind]);
            }
          }
        }
        ctx.fillStyle = `#${color.toString(16).padStart(6, "0")}`; ctx.fillRect(x / state.arenaWidth * 192 - 3, y / state.arenaHeight * 144 - 3, 6, 6);
        if (o.kind.includes("chest")) continue;
        const sx = (x - camera.scrollX) * camera.zoom, sy = (y - camera.scrollY) * camera.zoom;
        if (sx < 30 || sy < 70 || sx > scene.scale.width - 30 || sy > scene.scale.height - 30) {
          const marker = document.createElement("span");
          const angle = Math.atan2(sy - scene.scale.height / 2, sx - scene.scale.width / 2);
          marker.textContent = `${["→", "↘", "↓", "↙", "←", "↖", "↑", "↗"][(Math.round(angle / (Math.PI / 4)) + 8) % 8]} ${labels[o.kind]}`;
          marker.style.cssText = `position:absolute;left:${Math.max(20, Math.min(scene.scale.width - 120, sx))}px;top:${Math.max(80, Math.min(scene.scale.height - 40, sy))}px;background:#102335df;padding:5px 9px;border-radius:6px;color:#${color.toString(16)}`;
          markers.append(marker);
        }
      }
      for (const p of state.players.filter(p => p.alive)) {
        ctx.fillStyle = p.color; ctx.beginPath(); ctx.arc(p.x / state.arenaWidth * 192, p.y / state.arenaHeight * 144, 3, 0, Math.PI * 2); ctx.fill();
        for (const peer of state.players.filter(q => q.alive && q.playerId > p.playerId)) {
          if (Math.hypot(p.x - peer.x, p.y - peer.y) > s.maxGroupDistance - 70) { graphics.lineStyle(3, 0xfbbf24, 0.7); graphics.lineBetween(p.x, p.y, peer.x, peer.y); }
        }
      }
      ctx.strokeStyle = "#ffffff88"; ctx.strokeRect(camera.scrollX / state.arenaWidth * 192, camera.scrollY / state.arenaHeight * 144, scene.scale.width / camera.zoom / state.arenaWidth * 192, scene.scale.height / camera.zoom / state.arenaHeight * 144);
    },
    destroy() { graphics.destroy(); props.forEach(p => p.destroy()); names.forEach(p => p.destroy()); props.clear(); names.clear(); overlay.remove(); }
  };
}
