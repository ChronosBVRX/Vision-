import * as THREE from "three";

/** Fits the island itself, rather than framing the former decorative outer ring. */
export function fitIslandOverview(radius: number, aspect: number): { position: THREE.Vector3; look: THREE.Vector3 } {
  const camera = new THREE.PerspectiveCamera(48, Math.max(0.2, aspect), 0.1, 100);
  const look = new THREE.Vector3(0, 0.3, 0);
  const direction = new THREE.Vector3(0, 1, 1.15).normalize();
  const corners: THREE.Vector3[] = [];
  for (const [q, r] of [[radius, 0], [radius, -radius], [0, -radius], [-radius, 0], [-radius, radius], [0, radius]]) {
    for (let i = 0; i < 6; i++) {
      const angle = Math.PI / 6 + i * Math.PI / 3;
      for (const y of [0, 1.35]) {
        corners.push(new THREE.Vector3(0.52 * Math.sqrt(3) * (q + r / 2) + Math.cos(angle) * 0.52,
          y, 0.52 * 1.5 * r + Math.sin(angle) * 0.52));
      }
    }
  }
  let near = 1, far = 70;
  for (let step = 0; step < 18; step++) {
    const distance = (near + far) / 2;
    camera.position.copy(look).addScaledVector(direction, distance);
    camera.lookAt(look);
    camera.updateMatrixWorld();
    const fits = corners.every((point) => {
      const screen = point.clone().project(camera);
      return screen.z > -1 && screen.z < 1 && Math.abs(screen.x) <= 0.94 && Math.abs(screen.y) <= 0.9;
    });
    if (fits) far = distance; else near = distance;
  }
  return { position: look.clone().addScaledVector(direction, far), look };
}

/** Rotation sampled from elapsed time, independent of delivered frame rate. */
export function effectRotation(ageMs: number, radiansPerFrame: number): number {
  return ageMs / (1000 / 60) * radiansPerFrame;
}
