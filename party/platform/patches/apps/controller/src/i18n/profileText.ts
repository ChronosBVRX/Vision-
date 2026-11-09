import type { SupportedLanguage } from "@open-party-lab/protocol";

export function getProfileText(language: SupportedLanguage = "es") {
  const en = language === "en";
  const es = language === "es";
  if (es) {
    return {
      title: "Tu nombre y selfie",
      name: "Nombre del jugador",
      changePhoto: "Cambiar foto de personaje",
      remove: "Quitar selfie",
      save: "Guardar perfil",
      saving: "Guardando…",
      preparing: "Preparando foto…",
      saved: "Perfil guardado.",
      photoError: "No se pudo leer esta foto. Elige otra.",
      preview: "Tu selfie de personaje",
      reconnecting: "Se perdió la conexión. Reconectando…",
      hostOffline: "Se perdió la conexión con el anfitrión. Reconectando…"
    };
  }
  return {
    title: en ? "Your name & selfie" : "Dein Name & Selfie",
    name: en ? "Player name" : "Spielername",
    changePhoto: en ? "Change character photo" : "Charakterfoto ändern",
    remove: en ? "Remove selfie" : "Selfie entfernen",
    save: en ? "Save profile" : "Profil speichern",
    saving: en ? "Saving…" : "Wird gespeichert…",
    preparing: en ? "Preparing photo…" : "Foto wird vorbereitet…",
    saved: en ? "Profile saved." : "Profil gespeichert.",
    photoError: en ? "Could not read this photo. Please choose another." : "Das Foto konnte nicht gelesen werden. Bitte wähle ein anderes.",
    preview: en ? "Your character selfie" : "Dein Charakter-Selfie",
    reconnecting: en ? "Connection lost. Reconnecting automatically…" : "Verbindung unterbrochen. Wird automatisch wiederhergestellt…",
    hostOffline: en ? "Host connection lost. The host is reconnecting…" : "Host-Verbindung unterbrochen. Der Host verbindet sich erneut…"
  };
}
