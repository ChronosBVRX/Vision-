// Vision+ PC - lanzador portable para Windows.
//
// Reproduce el comportamiento del lanzador de XuperTV PC, pero con el motor y
// la interfaz de Vision+:
//   - arranca Node/FFmpeg incluidos en runtime\ sin instalar nada;
//   - abre Vision+ en una ventana limpia de Edge/Chrome (modo --app);
//   - al cerrar o minimizar la ventana la oculta a la bandeja y la
//     reproducción sigue en segundo plano;
//   - doble clic en el icono de la bandeja restaura la ventana;
//   - clic derecho -> Cerrar aplicación detiene también el servidor.
//
// Compilar en Windows con:
//   csc /target:winexe /win32icon:vision_icon.ico /win32manifest:app.manifest ^
//       /out:VisionPlusPC.exe VisionPlusPC.cs ^
//       /r:System.Windows.Forms.dll /r:System.Drawing.dll

using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

namespace VisionPlusPC
{
    internal static class Program
    {
        private const string MutexName = "VisionPlusPC-Launcher-2026";
        private static Mutex _mutex;

        [STAThread]
        private static void Main()
        {
            bool created;
            _mutex = new Mutex(true, MutexName, out created);
            if (!created)
            {
                VisionPlusApp.RestoreExistingWindow();
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
            Application.ThreadException += delegate(object sender, ThreadExceptionEventArgs args)
            {
                VisionPlusApp.WriteLog("Error de interfaz: " + args.Exception);
            };
            AppDomain.CurrentDomain.UnhandledException += delegate(object sender, UnhandledExceptionEventArgs args)
            {
                VisionPlusApp.WriteLog("Error fatal: " + args.ExceptionObject);
            };

            using (var app = new VisionPlusApp())
            {
                Application.Run(app);
            }
        }
    }

    internal sealed class VisionPlusApp : ApplicationContext
    {
        private const int SW_HIDE = 0;
        private const int SW_SHOW = 5;
        private const int SW_RESTORE = 9;
        private const int WH_MOUSE_LL = 14;
        private const int WH_KEYBOARD_LL = 13;
        private const int WM_LBUTTONDOWN = 0x0201;
        private const int WM_KEYDOWN = 0x0100;
        private const int WM_SYSKEYDOWN = 0x0104;
        private const int VK_F4 = 0x73;
        private const int VK_MENU = 0x12;
        private const int VK_CONTROL = 0x11;
        private const int VK_W = 0x57;
        private const int SM_CXSIZE = 30;
        private const int GWL_STYLE = -16;
        private const int WS_CAPTION = 0x00C00000;

        private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
        private delegate IntPtr LowLevelMouseProc(int nCode, IntPtr wParam, IntPtr lParam);
        private delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);

        [StructLayout(LayoutKind.Sequential)]
        private struct POINT
        {
            public int X;
            public int Y;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct RECT
        {
            public int Left;
            public int Top;
            public int Right;
            public int Bottom;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct MSLLHOOKSTRUCT
        {
            public POINT pt;
            public uint mouseData;
            public uint flags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KBDLLHOOKSTRUCT
        {
            public uint vkCode;
            public uint scanCode;
            public uint flags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [DllImport("user32.dll")]
        private static extern IntPtr SetWindowsHookEx(int idHook, Delegate lpfn, IntPtr hMod, uint dwThreadId);
        [DllImport("user32.dll")]
        private static extern bool UnhookWindowsHookEx(IntPtr hhk);
        [DllImport("user32.dll")]
        private static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, IntPtr lParam);
        [DllImport("user32.dll")]
        private static extern IntPtr GetModuleHandle(string lpModuleName);
        [DllImport("user32.dll")]
        private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
        [DllImport("user32.dll")]
        private static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll")]
        private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
        [DllImport("user32.dll")]
        private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
        [DllImport("user32.dll")]
        private static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
        [DllImport("user32.dll")]
        private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);
        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll")]
        private static extern short GetAsyncKeyState(int vKey);
        [DllImport("user32.dll")]
        private static extern int GetWindowLong(IntPtr hWnd, int nIndex);
        [DllImport("user32.dll")]
        private static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
        [DllImport("kernel32.dll")]
        private static extern uint GetCurrentThreadId();
        [DllImport("user32.dll")]
        private static extern int GetSystemMetrics(int nIndex);

        private readonly string _baseDir;
        private readonly string _dataDir;
        private readonly string _portFile;
        private readonly string _iconFile;

        private NotifyIcon _tray;
        private ContextMenuStrip _menu;
        private Process _server;
        private Process _browser;
        private IntPtr _browserHwnd = IntPtr.Zero;
        private int _port = 8910;
        private bool _exiting;
        private bool _browserHidden;
        private bool _trayHintShown;
        private bool _browserHookInstalled;
        private readonly string _shutdownToken;
        private IntPtr _mouseHook = IntPtr.Zero;
        private IntPtr _keyboardHook = IntPtr.Zero;
        private LowLevelMouseProc _mouseProc;
        private LowLevelKeyboardProc _keyboardProc;
        private System.Windows.Forms.Timer _windowTimer;

        public VisionPlusApp()
        {
            _baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
            _dataDir = Path.Combine(_baseDir, "data");
            _portFile = Path.Combine(_dataDir, "port.json");
            _iconFile = Path.Combine(_baseDir, "icon.ico");
            _shutdownToken = Guid.NewGuid().ToString("N");
            Directory.CreateDirectory(_dataDir);

            WriteLog("Vision+ PC iniciado en " + _baseDir);
            SetupTray();

            try
            {
                StartServer();
                _port = WaitForServer(30000);
                StartBrowser();
                InstallHooks();
                _windowTimer = new System.Windows.Forms.Timer();
                _windowTimer.Interval = 1500;
                _windowTimer.Tick += delegate { TickWindow(); };
                _windowTimer.Start();
            }
            catch (Exception error)
            {
                WriteLog("No se pudo iniciar: " + error);
                MessageBox.Show(
                    "No se pudo iniciar Vision+.\r\n\r\n" + error.Message +
                    "\r\n\r\nRevisa data\\launcher.log y data\\server.log.",
                    "Vision+ PC",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                ExitApp();
            }
        }

        /* ------------------------------------------------------------------ */
        /* Arranque                                                            */
        /* ------------------------------------------------------------------ */

        private void StartServer()
        {
            KillPreviousServerFromPortFile();

            string node = Path.Combine(_baseDir, "runtime", "node.exe");
            if (!File.Exists(node))
            {
                node = "node";
            }

            string script = Path.Combine(_baseDir, "server.js");
            if (!File.Exists(script))
            {
                throw new FileNotFoundException("Falta server.js en la carpeta de Vision+.", script);
            }

            var info = new ProcessStartInfo();
            info.FileName = node;
            info.Arguments = "\"" + script + "\"";
            info.WorkingDirectory = _baseDir;
            info.UseShellExecute = false;
            info.CreateNoWindow = true;
            info.RedirectStandardOutput = true;
            info.RedirectStandardError = true;
            info.EnvironmentVariables["VISION_PC"] = "1";
            info.EnvironmentVariables["VISION_APP_DIR"] = Path.Combine(_baseDir, "app");
            info.EnvironmentVariables["VISION_SERVICE_DIR"] = Path.Combine(_baseDir, "service");
            info.EnvironmentVariables["VISION_DATA_DIR"] = _dataDir;
            info.EnvironmentVariables["VISION_PORT"] = "8910";
            info.EnvironmentVariables["PORT"] = "8910";
            info.EnvironmentVariables["VISION_SHUTDOWN_TOKEN"] = _shutdownToken;

            string ffmpeg = Path.Combine(_baseDir, "runtime", "ffmpeg.exe");
            if (File.Exists(ffmpeg))
            {
                info.EnvironmentVariables["VISION_FFMPEG"] = ffmpeg;
            }

            _server = new Process();
            _server.StartInfo = info;
            _server.EnableRaisingEvents = true;
            _server.OutputDataReceived += delegate(object sender, DataReceivedEventArgs args)
            {
                if (!string.IsNullOrEmpty(args.Data)) WriteLog("[server] " + args.Data);
            };
            _server.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs args)
            {
                if (!string.IsNullOrEmpty(args.Data)) WriteLog("[server:error] " + args.Data);
            };
            _server.Exited += delegate
            {
                if (!_exiting)
                {
                    WriteLog("El servidor local terminó inesperadamente.");
                    ShowBalloon("Vision+ se detuvo", "El servidor local se cerró. Abre la app de nuevo.");
                }
            };
            _server.Start();
            _server.BeginOutputReadLine();
            _server.BeginErrorReadLine();
            WriteLog("Servidor lanzado (pid " + _server.Id + ").");
        }

        private int WaitForServer(int timeoutMs)
        {
            var started = Environment.TickCount;
            while (Environment.TickCount - started < timeoutMs)
            {
                if (_server != null && _server.HasExited)
                {
                    throw new Exception("El servidor local terminó antes de estar listo. Revisa data\\server.log.");
                }

                int port = ReadPortFile();
                if (port > 0 && Ping(port))
                {
                    WriteLog("Servidor listo en el puerto " + port + ".");
                    return port;
                }
                Thread.Sleep(250);
            }
            throw new TimeoutException("El servidor local no respondió en 30 segundos.");
        }

        private int ReadPortFile()
        {
            try
            {
                if (!File.Exists(_portFile)) return 0;
                string raw = File.ReadAllText(_portFile, Encoding.UTF8);
                Match match = Regex.Match(raw, "\"port\"\\s*:\\s*(\\d+)");
                if (!match.Success) return 0;
                int port;
                return int.TryParse(match.Groups[1].Value, out port) ? port : 0;
            }
            catch
            {
                return 0;
            }
        }

        private static bool Ping(int port)
        {
            try
            {
                var request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + port + "/ping");
                request.Method = "POST";
                request.ContentType = "application/json";
                request.Timeout = 1200;
                request.ReadWriteTimeout = 1200;
                request.Proxy = null;
                using (var stream = request.GetRequestStream())
                {
                    byte[] body = Encoding.UTF8.GetBytes("{}");
                    stream.Write(body, 0, body.Length);
                }
                using (var response = (HttpWebResponse)request.GetResponse())
                {
                    return response.StatusCode == HttpStatusCode.OK;
                }
            }
            catch
            {
                return false;
            }
        }

        private void KillPreviousServerFromPortFile()
        {
            try
            {
                if (!File.Exists(_portFile)) return;
                string raw = File.ReadAllText(_portFile, Encoding.UTF8);
                if (!Regex.IsMatch(raw, "\"app\"\\s*:\\s*\"Vision\\+ PC\"")) return;
                Match match = Regex.Match(raw, "\"pid\"\\s*:\\s*(\\d+)");
                if (!match.Success) return;
                int pid;
                if (!int.TryParse(match.Groups[1].Value, out pid)) return;
                int previousPort = 8910;
                Match portMatch = Regex.Match(raw, "\"port\"\\s*:\\s*(\\d+)");
                if (portMatch.Success) int.TryParse(portMatch.Groups[1].Value, out previousPort);
                try
                {
                    var previous = Process.GetProcessById(pid);
                    if (previous != null && !previous.HasExited)
                    {
                        string name = "";
                        try { name = previous.ProcessName.ToLowerInvariant(); }
                        catch { }
                        if (name == "node")
                        {
                            WriteLog("Cerrando servidor anterior (pid " + pid + ").");
                            TryGracefulShutdown(previousPort);
                            Thread.Sleep(600);
                            if (!previous.HasExited) previous.Kill();
                        }
                    }
                }
                catch
                {
                    /* el proceso ya no existe */
                }
                try { File.Delete(_portFile); } catch { }
            }
            catch
            {
                /* noop */
            }
        }

        private void StartBrowser()
        {
            string browser = FindBrowser();
            if (string.IsNullOrEmpty(browser))
            {
                throw new Exception(
                    "No se encontró Microsoft Edge ni Google Chrome.\r\n" +
                    "Instala Edge (viene con Windows 10/11) o Chrome para usar Vision+ PC.");
            }

            string url = "http://127.0.0.1:" + _port + "/?platform=pc";
            string profile = Path.Combine(_dataDir, "browser-profile");
            Directory.CreateDirectory(profile);

            var info = new ProcessStartInfo();
            info.FileName = browser;
            info.Arguments =
                "--app=\"" + url + "\" " +
                "--user-data-dir=\"" + profile + "\" " +
                "--window-size=1280,720 " +
                "--no-first-run --no-default-browser-check " +
                "--disable-session-crashed-bubble " +
                "--disable-features=Translate,TranslateUI " +
                "--autoplay-policy=no-user-gesture-required " +
                "--disable-background-timer-throttling " +
                "--disable-renderer-backgrounding " +
                "--disable-backgrounding-occluded-windows";
            info.UseShellExecute = false;
            info.WorkingDirectory = _baseDir;

            _browser = Process.Start(info);
            if (_browser == null)
            {
                throw new Exception("No se pudo abrir la ventana de Vision+.");
            }
            _browser.EnableRaisingEvents = true;
            _browser.Exited += delegate
            {
                if (!_exiting)
                {
                    _browserHwnd = IntPtr.Zero;
                    WriteLog("La ventana del navegador se cerró.");
                    ShowBalloon("Vision+ sigue en la bandeja", "Haz clic en el icono para volver a abrir la ventana.");
                }
            };
            WriteLog("Navegador lanzado (pid " + _browser.Id + "): " + browser);
            _browserHwnd = FindBrowserHwnd(_browser.Id);

            // Si Edge tarda en crear la ventana, se reintenta desde TickWindow().
            Thread.Sleep(800);
            _browserHwnd = FindBrowserHwnd(_browser.Id);
        }

        private static string FindBrowser()
        {
            string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            string programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string[] candidates = new string[]
            {
                Path.Combine(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe"),
                Path.Combine(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"),
                Path.Combine(local, "Microsoft", "Edge", "Application", "msedge.exe"),
                Path.Combine(programFiles, "Google", "Chrome", "Application", "chrome.exe"),
                Path.Combine(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"),
                Path.Combine(local, "Google", "Chrome", "Application", "chrome.exe")
            };
            foreach (string candidate in candidates)
            {
                if (File.Exists(candidate)) return candidate;
            }
            return null;
        }

        private IntPtr FindBrowserHwnd(int processId)
        {
            IntPtr found = IntPtr.Zero;
            IntPtr fallback = IntPtr.Zero;
            EnumWindows(delegate(IntPtr hWnd, IntPtr lParam)
            {
                uint pid;
                GetWindowThreadProcessId(hWnd, out pid);
                if ((int)pid != processId) return true;
                if (!IsWindowVisible(hWnd)) return true;
                var title = new StringBuilder(512);
                GetWindowText(hWnd, title, title.Capacity);
                string text = title.ToString();
                if (text.IndexOf("Vision+", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    text.IndexOf("127.0.0.1", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    found = hWnd;
                    return false;
                }
                if (string.IsNullOrEmpty(text) && fallback == IntPtr.Zero)
                {
                    fallback = hWnd;
                }
                return true;
            }, IntPtr.Zero);
            return found != IntPtr.Zero ? found : fallback;
        }

        /* ------------------------------------------------------------------ */
        /* Bandeja y ventana                                                   */
        /* ------------------------------------------------------------------ */

        private void SetupTray()
        {
            Icon icon;
            try
            {
                icon = File.Exists(_iconFile) ? new Icon(_iconFile) : Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch
            {
                icon = SystemIcons.Application;
            }

            _menu = new ContextMenuStrip();
            var open = new ToolStripMenuItem("Abrir Vision+");
            open.Font = new Font(open.Font, FontStyle.Bold);
            open.Click += delegate { RestoreWindow(); };
            var fullscreen = new ToolStripMenuItem("Pantalla completa (F)");
            fullscreen.Click += delegate { SendFullscreen(); };
            var folder = new ToolStripMenuItem("Abrir carpeta de datos");
            folder.Click += delegate
            {
                try { Process.Start("explorer.exe", "\"" + _dataDir + "\""); } catch { }
            };
            var exit = new ToolStripMenuItem("Cerrar aplicación");
            exit.Click += delegate { ExitApp(); };
            _menu.Items.Add(open);
            _menu.Items.Add(fullscreen);
            _menu.Items.Add(new ToolStripSeparator());
            _menu.Items.Add(folder);
            _menu.Items.Add(new ToolStripSeparator());
            _menu.Items.Add(exit);

            _tray = new NotifyIcon();
            _tray.Icon = icon;
            _tray.Text = "Vision+ PC";
            _tray.ContextMenuStrip = _menu;
            _tray.Visible = true;
            _tray.DoubleClick += delegate { RestoreWindow(); };
            _tray.MouseClick += delegate(object sender, MouseEventArgs args)
            {
                if (args.Button == MouseButtons.Left) RestoreWindow();
            };
        }

        private void TickWindow()
        {
            if (_exiting) return;

            if (_browser == null || _browser.HasExited)
            {
                _browserHwnd = IntPtr.Zero;
                return;
            }

            if (_browserHwnd == IntPtr.Zero || !IsWindow(_browserHwnd))
            {
                _browserHwnd = FindBrowserHwnd(_browser.Id);
            }
        }

        [DllImport("user32.dll")]
        private static extern bool IsWindow(IntPtr hWnd);

        private void HideWindow()
        {
            if (_browserHwnd == IntPtr.Zero) _browserHwnd = _browser != null ? FindBrowserHwnd(_browser.Id) : IntPtr.Zero;
            if (_browserHwnd == IntPtr.Zero) return;
            ShowWindow(_browserHwnd, SW_HIDE);
            _browserHidden = true;
            WriteLog("Ventana oculta; la reproducción continúa.");
            if (!_trayHintShown)
            {
                _trayHintShown = true;
                ShowBalloon("Vision+ sigue reproduciendo", "La ventana se ocultó a la bandeja. Haz clic en el icono para volver.");
            }
        }

        private void RestoreWindow()
        {
            if (_exiting) return;
            if (_browser == null || _browser.HasExited)
            {
                try
                {
                    StartBrowser();
                }
                catch (Exception error)
                {
                    MessageBox.Show("No se pudo abrir Vision+.\r\n\r\n" + error.Message, "Vision+ PC", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
                return;
            }
            if (_browserHwnd == IntPtr.Zero) _browserHwnd = FindBrowserHwnd(_browser.Id);
            if (_browserHwnd == IntPtr.Zero)
            {
                ShowBalloon("Vision+ está abriendo", "Espera un momento y vuelve a intentarlo.");
                return;
            }
            ShowWindow(_browserHwnd, SW_SHOW);
            ShowWindow(_browserHwnd, SW_RESTORE);
            ForceForeground(_browserHwnd);
            _browserHidden = false;
            WriteLog("Ventana restaurada.");
        }

        private static void ForceForeground(IntPtr hWnd)
        {
            try
            {
                uint ignored;
                uint foregroundThread = GetWindowThreadProcessId(GetForegroundWindow(), out ignored);
                uint currentThread = GetCurrentThreadId();
                if (foregroundThread != currentThread)
                {
                    AttachThreadInput(currentThread, foregroundThread, true);
                    SetForegroundWindow(hWnd);
                    AttachThreadInput(currentThread, foregroundThread, false);
                }
                else
                {
                    SetForegroundWindow(hWnd);
                }
            }
            catch
            {
                try { SetForegroundWindow(hWnd); } catch { }
            }
        }

        private void SendFullscreen()
        {
            if (_browserHwnd == IntPtr.Zero) _browserHwnd = _browser != null ? FindBrowserHwnd(_browser.Id) : IntPtr.Zero;
            if (_browserHwnd == IntPtr.Zero) return;
            ForceForeground(_browserHwnd);
            try
            {
                PostMessage(_browserHwnd, WM_KEYDOWN, (IntPtr)0x46, IntPtr.Zero);
                PostMessage(_browserHwnd, 0x0101, (IntPtr)0x46, IntPtr.Zero);
            }
            catch { }
        }

        [DllImport("user32.dll")]
        private static extern bool PostMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

        private void ShowBalloon(string title, string text)
        {
            try
            {
                _tray.ShowBalloonTip(3500, title, text, ToolTipIcon.Info);
            }
            catch { }
        }

        /* ------------------------------------------------------------------ */
        /* Hooks: la X y Alt+F4 ocultan a la bandeja                            */
        /* ------------------------------------------------------------------ */

        private void InstallHooks()
        {
            _mouseProc = MouseHookCallback;
            _keyboardProc = KeyboardHookCallback;
            IntPtr module = GetModuleHandle(null);
            _mouseHook = SetWindowsHookEx(WH_MOUSE_LL, _mouseProc, module, 0);
            _keyboardHook = SetWindowsHookEx(WH_KEYBOARD_LL, _keyboardProc, module, 0);
            _browserHookInstalled = _mouseHook != IntPtr.Zero || _keyboardHook != IntPtr.Zero;
            WriteLog("Hooks instalados: mouse=" + (_mouseHook != IntPtr.Zero) + " teclado=" + (_keyboardHook != IntPtr.Zero));
        }

        private void RemoveHooks()
        {
            _browserHookInstalled = false;
            if (_mouseHook != IntPtr.Zero)
            {
                UnhookWindowsHookEx(_mouseHook);
                _mouseHook = IntPtr.Zero;
            }
            if (_keyboardHook != IntPtr.Zero)
            {
                UnhookWindowsHookEx(_keyboardHook);
                _keyboardHook = IntPtr.Zero;
            }
        }

        private IntPtr MouseHookCallback(int nCode, IntPtr wParam, IntPtr lParam)
        {
            try
            {
                if (nCode >= 0 && wParam == (IntPtr)WM_LBUTTONDOWN && _browserHwnd != IntPtr.Zero && !_exiting)
                {
                    if (GetForegroundWindow() != _browserHwnd)
                    {
                        return CallNextHookEx(_mouseHook, nCode, wParam, lParam);
                    }
                    var data = (MSLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(MSLLHOOKSTRUCT));
                    RECT rect;
                    if (GetWindowRect(_browserHwnd, out rect))
                    {
                        int style = GetWindowLong(_browserHwnd, GWL_STYLE);
                        bool hasCaption = (style & WS_CAPTION) == WS_CAPTION;
                        int captionTop = rect.Top + Math.Max(24, GetSystemMetrics(4) + 8); // SM_CYCAPTION
                        int closeWidth = Math.Max(42, GetSystemMetrics(SM_CXSIZE) * 2 + 10);
                        bool inCaption = data.pt.Y >= rect.Top && data.pt.Y <= captionTop;
                        bool inClose = data.pt.X >= rect.Right - closeWidth && data.pt.X <= rect.Right;
                        bool inMinimize = data.pt.X >= rect.Right - (closeWidth * 3) &&
                                          data.pt.X < rect.Right - (closeWidth * 2);
                        if (hasCaption && inCaption && (inClose || inMinimize))
                        {
                            HideWindow();
                            return (IntPtr)1;
                        }
                    }
                }
            }
            catch { }
            return CallNextHookEx(_mouseHook, nCode, wParam, lParam);
        }

        private IntPtr KeyboardHookCallback(int nCode, IntPtr wParam, IntPtr lParam)
        {
            try
            {
                if (nCode >= 0 && !_exiting && (wParam == (IntPtr)WM_KEYDOWN || wParam == (IntPtr)WM_SYSKEYDOWN))
                {
                    var data = (KBDLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(KBDLLHOOKSTRUCT));
                    IntPtr foreground = GetForegroundWindow();
                    if (foreground != IntPtr.Zero && foreground == _browserHwnd)
                    {
                        bool alt = (GetAsyncKeyState(VK_MENU) & 0x8000) != 0;
                        bool ctrl = (GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0;
                        if (data.vkCode == VK_F4 && alt)
                        {
                            HideWindow();
                            return (IntPtr)1;
                        }
                        if (data.vkCode == VK_W && ctrl)
                        {
                            HideWindow();
                            return (IntPtr)1;
                        }
                    }
                }
            }
            catch { }
            return CallNextHookEx(_keyboardHook, nCode, wParam, lParam);
        }

        /* ------------------------------------------------------------------ */
        /* Cierre                                                              */
        /* ------------------------------------------------------------------ */

        public void ExitApp()
        {
            if (_exiting) return;
            _exiting = true;
            WriteLog("Cerrando Vision+ PC.");
            try { if (_windowTimer != null) _windowTimer.Stop(); } catch { }
            RemoveHooks();

            try
            {
                if (_tray != null)
                {
                    _tray.Visible = false;
                    _tray.Dispose();
                }
            }
            catch { }

            try
            {
                if (_browser != null && !_browser.HasExited)
                {
                    // Cierra todo el árbol del navegador (incluye procesos hijos).
                    try
                    {
                        var killer = Process.Start(new ProcessStartInfo("taskkill", "/PID " + _browser.Id + " /T /F")
                        {
                            UseShellExecute = false,
                            CreateNoWindow = true
                        });
                        if (killer != null) killer.WaitForExit(3000);
                    }
                    catch
                    {
                        try { _browser.Kill(); } catch { }
                    }
                }
            }
            catch { }

            try
            {
                if (_server != null && !_server.HasExited)
                {
                    TryGracefulShutdown(_port);
                    if (!_server.WaitForExit(1500))
                    {
                        try
                        {
                            Process.Start(new ProcessStartInfo("taskkill", "/PID " + _server.Id + " /T /F")
                            {
                                UseShellExecute = false,
                                CreateNoWindow = true
                            });
                        }
                        catch
                        {
                            try { _server.Kill(); } catch { }
                        }
                    }
                }
            }
            catch { }

            try
            {
                if (_portFile != null && File.Exists(_portFile)) File.Delete(_portFile);
            }
            catch { }

            try { ExitThread(); } catch { }
        }

        private void TryGracefulShutdown(int port)
        {
            try
            {
                var request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + port + "/shutdown");
                request.Method = "POST";
                request.Timeout = 1200;
                request.Proxy = null;
                if (!string.IsNullOrEmpty(_shutdownToken))
                {
                    request.Headers["X-Vision-Token"] = _shutdownToken;
                }
                using (var response = (HttpWebResponse)request.GetResponse()) { }
            }
            catch { }
        }

        /* ------------------------------------------------------------------ */
        /* Utilidades                                                          */
        /* ------------------------------------------------------------------ */

        public static void WriteLog(string message)
        {
            try
            {
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                string data = Path.Combine(baseDir, "data");
                Directory.CreateDirectory(data);
                File.AppendAllText(
                    Path.Combine(data, "launcher.log"),
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " + message + Environment.NewLine,
                    Encoding.UTF8);
            }
            catch { }
        }

        public static void RestoreExistingWindow()
        {
            try
            {
                IntPtr found = IntPtr.Zero;
                EnumWindows(delegate(IntPtr hWnd, IntPtr lParam)
                {
                    if (!IsWindowVisible(hWnd)) return true;
                    var title = new StringBuilder(512);
                    GetWindowText(hWnd, title, title.Capacity);
                    string text = title.ToString();
                    if (text.IndexOf("Vision+", StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        found = hWnd;
                        return false;
                    }
                    return true;
                }, IntPtr.Zero);
                if (found != IntPtr.Zero)
                {
                    ShowWindow(found, SW_SHOW);
                    ShowWindow(found, SW_RESTORE);
                    SetForegroundWindow(found);
                }
            }
            catch { }
        }
    }
}
