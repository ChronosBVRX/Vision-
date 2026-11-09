import assert from 'node:assert/strict';
import { generateTransitMap } from '../dist/map/generateTransitMap.js';
import { alignTransportStops, buildCityNetwork, networkRoutePoints, roadKey, routeKey, sectionLane } from '../dist/map/cityNetwork.js';

for (const count of [16, 32, 64]) {
  const map = generateTransitMap('network-check', count);
  const network = buildCityNetwork(map);
  assert.deepEqual(alignTransportStops(map), map, 'Aligning stops must produce a stable network');
  const sections = new Map(network.sections.map(section => [section.key, section]));
  assert.equal(sections.size, map.links.filter(link => link.mode === 'taxi').length);
  for (const station of map.stations) {
    assert.ok(new Set(map.neighbours[station.id].taxi).size >= 2, `Station ${station.id} has a dead end`);
    if (count === 64) {
      const bearings = network.sections.filter(section => section.a === station.id || section.b === station.id).map(section => {
        const next = section.a === station.id ? section.points[1] : section.points.at(-2);
        return Math.atan2(next.y - station.y, next.x - station.x);
      });
      const spread = Math.max(...bearings.flatMap(a => bearings.map(b => Math.acos(Math.cos(a - b)))));
      assert.ok(spread >= 0.4, `Station ${station.id} has visually merged approaches`);
    }
  }
  for (const link of map.links) {
    const route = network.routes.get(routeKey(link));
    assert.equal(route[0], link.a);
    assert.equal(route.at(-1), link.b);
    assert.ok(route.slice(1, -1).every(id => !map.stations.find(station => station.id === id).modes.includes(link.mode)),
      'A ticket must lead to the next matching stop, without invisible shortcuts');
    for (let index = 1; index < route.length; index++) {
      const section = sections.get(roadKey(route[index - 1], route[index]));
      assert.ok(section?.modes.has(link.mode), 'Every express leg must use an existing road lane');
    }
    const points = networkRoutePoints(network, link);
    assert.ok(points.every(point => Number.isFinite(point.x) && Number.isFinite(point.y)));
    const from = map.stations.find(station => station.id === link.a);
    const to = map.stations.find(station => station.id === link.b);
    assert.deepEqual({ x: points[0].x, y: points[0].y }, { x: from.x, y: from.y });
    assert.deepEqual({ x: points.at(-1).x, y: points.at(-1).y }, { x: to.x, y: to.y });
  }
  // A lane cannot switch sides when the same section is traversed backwards.
  for (const section of network.sections) {
    for (const mode of ['taxi', 'bus', 'metro']) {
      const reverse = sectionLane({ ...section, from: section.to, to: section.from, points: [...section.points].reverse() }, mode).reverse();
      const forward = sectionLane(section, mode);
      forward.forEach((point, index) => {
        assert.ok(Math.hypot(point.x - reverse[index].x, point.y - reverse[index].y) < 1e-8);
      });
    }
  }
  if (count === 64) {
    const station = map.stations.find(station => station.id === 6);
    const bearings = map.neighbours[6].taxi.map(id => {
      const neighbour = map.stations.find(candidate => candidate.id === id);
      return Math.atan2(neighbour.y - station.y, neighbour.x - station.x);
    });
    assert.ok(Math.max(...bearings) - Math.min(...bearings) > Math.PI / 2, 'Station 6 needs visibly different approaches');
  }
}
console.log('PASS: connected shared roads, no dead ends, express lanes, direction-independent offsets, station 6 approaches');
