export function dungeonCopy(language: string, de: string, en: string): string { return language === "en" ? en : de; }
export function bossPhaseName(language: string, index: number): string { return (language === "en" ? ["The Contract", "Dragonfire", "The Final Clause"] : ["el contrato", "El fuego del dragón", "La cláusula final"])[index] ?? ""; }
