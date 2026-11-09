document.documentElement.lang = "es";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ControllerApp } from "./app/controllerBootstrap.js";
import { installPartyTranslator } from "./app/partyTranslator.js";
import "./styles/globals.css";

installPartyTranslator();

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("Root element not found.");
}

createRoot(rootElement).render(
  <StrictMode>
    <ControllerApp />
  </StrictMode>
);
