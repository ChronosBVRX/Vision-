# Lanzador Vision+ PC

`VisionPlusPC.cs` es un lanzador WinForms para .NET Framework 4.0+ que:

- arranca `runtime\node.exe server.js` con el entorno del modo PC;
- espera `data\port.json` y `/ping`;
- abre Edge/Chrome en modo `--app` con perfil propio en `data\browser-profile`;
- oculta la ventana a la bandeja al cerrar/minimizar o con `Alt+F4`/`Ctrl+W`;
- restaura con doble clic y cierra navegador + servidor desde el menú.

Compilar en Windows (sin Visual Studio, con el compilador incluido en .NET
Framework):

```powershell
csc /nologo /target:winexe /win32icon:..\..\vision_icon.ico `
    /win32manifest:app.manifest `
    /out:VisionPlusPC.exe VisionPlusPC.cs `
    /r:System.Windows.Forms.dll /r:System.Drawing.dll
```

El empaquetador `..\tools\build-windows.ps1` busca `csc.exe` automáticamente y
coloca el `.exe` en la carpeta portable. Si no encuentra `csc`, usa este
`VisionPlusPC.exe` precompilado si existe o deja `INICIAR-VISIONPLUS.bat`.

`VisionPlusPC.exe` incluido en esta carpeta se compiló con **Mono 6.12**
(`mcs -target:winexe -win32icon ... -win32manifest:app.manifest`); es un
ensamblado .NET Framework estándar y Windows lo ejecuta con .NET 4.x. Si se
prefiere, se puede recompilar con `csc` en Windows usando el comando de arriba.

> El lanzador usa hooks de teclado/ratón de bajo nivel para que la `X` de la
> ventana no cierre la reproducción. Solo actúa sobre la ventana de Vision+.
