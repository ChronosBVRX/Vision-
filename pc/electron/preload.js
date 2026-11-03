"use strict";

const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("visionPC", {
  getVersion: () => ipcRenderer.invoke("pc-update:version"),
  checkForUpdates: () => ipcRenderer.invoke("pc-update:check"),
  downloadUpdate: () => ipcRenderer.invoke("pc-update:download"),
  installUpdate: () => ipcRenderer.invoke("pc-update:install"),
  lastUpdateStatus: () => ipcRenderer.invoke("pc-update:last"),
  onUpdateStatus: (callback) => {
    const listener = (_event, status) => {
      try {
        callback(status);
      } catch (error) {
        /* la UI no debe romper el proceso de actualización */
      }
    };
    ipcRenderer.on("pc-update:status", listener);
    return () => ipcRenderer.removeListener("pc-update:status", listener);
  },
});
