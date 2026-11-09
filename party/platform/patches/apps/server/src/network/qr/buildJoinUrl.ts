export function buildJoinUrl(origin: string, roomCode: string): string {
  const normalizedOrigin = origin.replace(/\/$/, "");
  const base = normalizedOrigin.replace(/\/controller$/i, "");
  return `${base}/join.html?room=${encodeURIComponent(roomCode)}`;
}
