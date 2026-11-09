import * as THREE from "three";

/** Original, code-native scenery and articulated miniatures, matching Emberfall's
 * faceted stone / cloth / brass material language. No external art is required. */
export const arenaPalette = {
  night: "#111a19", stone: "#59605a", darkStone: "#293531",
  brass: "#ba9453", parchment: "#f2e4c5", water: "#182f32"
};

function material(color: THREE.ColorRepresentation, metalness = 0, roughness = 0.85) {
  return new THREE.MeshStandardMaterial({ color, metalness, roughness, flatShading: true });
}

function part(parent: THREE.Object3D, geometry: THREE.BufferGeometry, mat: THREE.Material,
  x = 0, y = 0, z = 0) {
  const mesh = new THREE.Mesh(geometry, mat);
  mesh.position.set(x, y, z);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  parent.add(mesh);
  return mesh;
}

function box(parent: THREE.Object3D, mat: THREE.Material, x: number, y: number, z: number,
  w: number, h: number, d: number) {
  return part(parent, new THREE.BoxGeometry(w, h, d), mat, x, y, z);
}

export interface MageRig {
  root: THREE.Group;
  body: THREE.Group;
  head: THREE.Group;
  leftArm: THREE.Group;
  rightArm: THREE.Group;
  leftLeg: THREE.Group;
  rightLeg: THREE.Group;
  cape: THREE.Mesh;
  crystal: THREE.Mesh;
  phase: number;
}

export function createMage(color: string, seed: string): MageRig {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);
  const hash = [...seed].reduce((value, c) => (value * 31 + c.charCodeAt(0)) >>> 0, 7);
  const cloth = material(new THREE.Color(color).lerp(new THREE.Color("#38443b"), 0.35));
  const dark = material(new THREE.Color(color).multiplyScalar(0.32));
  const skin = material(["#c49673", "#986a4e", "#d5ad8b", "#765240"][hash % 4]);
  const hair = material(["#413329", "#a76235", "#c1b49a", "#252c2b"][hash % 4]);
  const brass = material(arenaPalette.brass, 0.65, 0.38);
  const leather = material("#392e26");
  const steel = material("#747b72", 0.55, 0.5);
  const torso = part(body, new THREE.CylinderGeometry(0.105, 0.14, 0.24, 8), cloth, 0, 0.32);
  torso.scale.z = 0.78;
  part(body, new THREE.CylinderGeometry(0.14, 0.19, 0.2, 8), dark, 0, 0.17);
  part(body, new THREE.CylinderGeometry(0.139, 0.139, 0.035, 8), leather, 0, 0.25);
  box(body, brass, 0, 0.25, 0.115, 0.045, 0.04, 0.018);
  const cape = part(body, new THREE.CylinderGeometry(0.12, 0.21, 0.38, 8, 1, true,
    Math.PI / 2, Math.PI), dark, 0, 0.28, -0.045);
  cape.material.side = THREE.DoubleSide;
  for (const side of [-1, 1]) {
    const shoulder = part(body, new THREE.SphereGeometry(0.075, 8, 6), steel, side * 0.13, 0.42);
    shoulder.scale.set(1.15, 0.55, 1);
  }
  const head = new THREE.Group();
  head.position.y = 0.53;
  body.add(head);
  const face = part(head, new THREE.SphereGeometry(0.092, 10, 8), skin);
  face.scale.set(0.85, 1.18, 0.9);
  const cap = part(head, new THREE.SphereGeometry(0.094, 10, 6), hair, 0, 0.045, -0.018);
  cap.scale.set(1, 0.75, 1);
  part(head, new THREE.SphereGeometry(0.023, 6, 5), skin, 0, -0.005, 0.083);
  for (const side of [-1, 1]) {
    const eye = part(head, new THREE.SphereGeometry(0.016, 6, 5), material("#e9dec3"), side * 0.034, 0.012, 0.074);
    eye.scale.y = 0.65;
    part(head, new THREE.SphereGeometry(0.008, 6, 4), material("#151d1c"), side * 0.034, 0.012, 0.087);
    box(head, hair, side * 0.034, 0.037, 0.079, 0.038, 0.009, 0.01);
  }
  // Three readable silhouettes per party, deterministic across reconnects.
  if (hash % 3 === 0) {
    const hat = part(head, new THREE.ConeGeometry(0.13, 0.23, 8), cloth, 0, 0.15);
    hat.rotation.z = -0.12;
    part(head, new THREE.CylinderGeometry(0.16, 0.16, 0.018, 10), dark, 0, 0.055);
    part(head, new THREE.CylinderGeometry(0.118, 0.13, 0.022, 8), brass, 0, 0.075);
  } else if (hash % 3 === 1) {
    const hood = part(head, new THREE.SphereGeometry(0.105, 8, 6, 0, Math.PI), dark, 0, 0.012, -0.035);
    hood.rotation.y = Math.PI;
    part(head, new THREE.ConeGeometry(0.065, 0.13, 7), hair, 0, -0.1, 0.03);
  } else {
    for (const side of [-1, 1]) {
      const lock = part(head, new THREE.SphereGeometry(0.045, 7, 5), hair, side * 0.075, -0.05, -0.03);
      lock.scale.y = 2.1;
    }
    box(head, brass, 0, 0.045, 0.089, 0.12, 0.016, 0.012);
  }
  const makeArm = (side: number) => {
    const arm = new THREE.Group();
    arm.position.set(side * 0.14, 0.4, 0);
    body.add(arm);
    part(arm, new THREE.CylinderGeometry(0.042, 0.035, 0.16, 7), cloth, 0, -0.085);
    part(arm, new THREE.CylinderGeometry(0.036, 0.031, 0.1, 7), leather, 0, -0.19, 0.02);
    part(arm, new THREE.SphereGeometry(0.036, 7, 6), skin, 0, -0.25, 0.025);
    return arm;
  };
  const leftArm = makeArm(-1), rightArm = makeArm(1);
  part(rightArm, new THREE.CylinderGeometry(0.013, 0.018, 0.66, 7), leather, 0, -0.1, 0.06);
  part(rightArm, new THREE.TorusGeometry(0.055, 0.009, 5, 10), brass, 0, 0.23, 0.06);
  const crystal = part(rightArm, new THREE.OctahedronGeometry(0.045),
    new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 1.4, roughness: 0.3 }), 0, 0.23, 0.06);
  const makeLeg = (side: number) => {
    const leg = new THREE.Group();
    leg.position.set(side * 0.065, 0.16, 0);
    body.add(leg);
    part(leg, new THREE.CylinderGeometry(0.039, 0.031, 0.13, 7), dark, 0, -0.055);
    box(leg, leather, 0, -0.12, 0.025, 0.075, 0.07, 0.13);
    return leg;
  };
  const leftLeg = makeLeg(-1), rightLeg = makeLeg(1);
  return { root, body, head, leftArm, rightArm, leftLeg, rightLeg, cape, crystal, phase: hash % 100 };
}

/** All poses are absolute, so neither dropped frames nor repeated states accumulate transforms. */
export function animateMage(rig: MageRig, time: number, action: string | undefined,
  progress: number, walking: boolean, reducedMotion: boolean) {
  const idle = reducedMotion ? 0 : Math.sin(time * 1.7 + rig.phase) * 0.008;
  const envelope = !reducedMotion && progress > 0 && progress < 1 ? Math.sin(progress * Math.PI) : 0;
  const stride = walking && !reducedMotion ? Math.sin(progress * Math.PI * 12) * 0.45 : 0;
  const melee = action === "punch" || action === "hammer_blow" || action === "dig";
  const cast = action && !melee && !["move", "sprint", "charge_run"].includes(action);
  rig.body.position.y = idle;
  rig.body.rotation.set(melee ? envelope * 0.22 : 0, melee ? -envelope * 0.45 : 0, 0);
  rig.head.rotation.y = reducedMotion ? 0 : Math.sin(time * 0.65 + rig.phase) * 0.055;
  rig.leftLeg.rotation.x = stride;
  rig.rightLeg.rotation.x = -stride;
  rig.leftArm.rotation.set(-stride * 0.65 - (cast ? envelope * 1.3 : 0), 0, -0.12);
  rig.rightArm.rotation.set(stride * 0.65 - envelope * (melee ? 1.8 : cast ? 1.1 : 0), 0, 0.1);
  rig.cape.rotation.x = 0.08 + Math.abs(stride) * 0.3 + idle * 3;
  rig.crystal.rotation.y = reducedMotion ? 0 : time * 0.7;
}

export function createOcean(): THREE.Group {
  const root = new THREE.Group();
  const clock = { value: 0 };
  const waterMaterial = new THREE.MeshStandardMaterial({
    color: "#165873", roughness: 0.58, metalness: 0.18
  });
  waterMaterial.onBeforeCompile = (shader) => {
    shader.uniforms.arenaWaterTime = clock;
    shader.vertexShader = `uniform float arenaWaterTime; varying vec2 arenaWaterCoord;\n${shader.vertexShader}`
      .replace("#include <begin_vertex>", `#include <begin_vertex>
        arenaWaterCoord = position.xy;
        transformed.z += sin(position.x * 1.8 + arenaWaterTime * .7) * .018
          + cos(position.y * 2.1 - arenaWaterTime * .5) * .012;`);
    shader.fragmentShader = `uniform float arenaWaterTime; varying vec2 arenaWaterCoord;\n${shader.fragmentShader}`
      .replace("#include <color_fragment>", `#include <color_fragment>
        float swell = sin(arenaWaterCoord.x * 2.8 + sin(arenaWaterCoord.y * 2.2) + arenaWaterTime * .55);
        float glint = pow(max(0., sin(arenaWaterCoord.y * 14. + swell * 2. + arenaWaterTime * .4)), 22.);
        diffuseColor.rgb *= .88 + swell * .09;
        diffuseColor.rgb += vec3(.10, .19, .20) * glint * .08;`);
  };
  const water = new THREE.Mesh(new THREE.PlaneGeometry(160, 160, 96, 96), waterMaterial);
  water.rotation.x = -Math.PI / 2;
  water.position.y = -0.09;
  water.receiveShadow = true;
  root.add(water);
  root.userData.waterClock = clock;
  return root;
}

export function animateOcean(root: THREE.Group, seconds: number, reducedMotion: boolean): void {
  (root.userData.waterClock as { value: number }).value = reducedMotion ? 0 : seconds;
}
