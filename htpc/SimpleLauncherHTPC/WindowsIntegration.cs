using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using Microsoft.Win32;

namespace SimpleLauncherHTPC
{
    /// <summary>Start with Windows, console mode, wallpaper and power.</summary>
    static class WindowsIntegration
    {
        const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        const string WinlogonKey = @"Software\Microsoft\Windows NT\CurrentVersion\Winlogon";
        const string ValueName = "SimpleLauncher";

        public static string ExePath => Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule.FileName;

        // ------------------------------------------------------------------ start with Windows

        public static bool StartsWithWindows
        {
            get
            {
                using (var k = Registry.CurrentUser.OpenSubKey(RunKey))
                {
                    return k?.GetValue(ValueName) is string v && v.IndexOf("SimpleLauncher", StringComparison.OrdinalIgnoreCase) >= 0;
                }
            }
            set
            {
                try
                {
                    using (var k = Registry.CurrentUser.CreateSubKey(RunKey))
                    {
                        if (value) k.SetValue(ValueName, "\"" + ExePath + "\" --startup");
                        else k.DeleteValue(ValueName, false);
                    }
                }
                catch
                {
                }
            }
        }

        // ------------------------------------------------------------------ console mode

        /// <summary>SimpleLauncher replaces the desktop for this Windows account (takes effect at the next sign-in).</summary>
        public static bool ConsoleMode
        {
            get
            {
                using (var k = Registry.CurrentUser.OpenSubKey(WinlogonKey))
                {
                    return k?.GetValue("Shell") is string v && v.IndexOf("SimpleLauncher", StringComparison.OrdinalIgnoreCase) >= 0;
                }
            }
            set
            {
                try
                {
                    using (var k = Registry.CurrentUser.CreateSubKey(WinlogonKey))
                    {
                        if (value) k.SetValue("Shell", "\"" + ExePath + "\" --shell");
                        else if (ConsoleMode) k.DeleteValue("Shell", false);
                    }
                }
                catch
                {
                }
            }
        }

        public static bool DesktopRunning => HasTaskbar();

        /// <summary>The taskbar's window exists whenever the desktop is running.</summary>
        static bool HasTaskbar() => FindTaskbar() != IntPtr.Zero;

        [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode)]
        static extern IntPtr FindWindow(string cls, string title);

        static IntPtr FindTaskbar() => FindWindow("Shell_TrayWnd", null);

        /// <summary>In console mode the desktop isn't running: this starts it.</summary>
        public static void StartDesktop()
        {
            if (HasTaskbar()) return;
            try
            {
                Process.Start(new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "explorer.exe"))
                {
                    UseShellExecute = true
                });
            }
            catch
            {
            }
        }

        // ------------------------------------------------------------------ wallpaper

        public static string WallpaperPath()
        {
            try
            {
                var sb = new StringBuilder(520);
                if (Native.SystemParametersInfo(Native.SPI_GETDESKWALLPAPER, (uint)sb.Capacity, sb, 0))
                {
                    string p = sb.ToString();
                    if (File.Exists(p)) return p;
                }
                string cached = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    @"Microsoft\Windows\Themes\TranscodedWallpaper");
                if (File.Exists(cached)) return cached;
            }
            catch
            {
            }
            return null;
        }

        // ------------------------------------------------------------------ power

        public static void Sleep()
        {
            try { Native.SetSuspendState(false, false, false); } catch { }
        }

        public static void Restart() => Run("shutdown.exe", "/r /t 0");

        public static void ShutDown() => Run("shutdown.exe", "/s /t 0");

        static void Run(string exe, string args)
        {
            try
            {
                Process.Start(new ProcessStartInfo(exe, args) { CreateNoWindow = true, UseShellExecute = false });
            }
            catch
            {
            }
        }

        public static string WindowsName()
        {
            var v = Environment.OSVersion.Version;
            return v.Major >= 10 && v.Build >= 22000 ? "Windows 11" : v.Major >= 10 ? "Windows 10" : "Windows";
        }

        public static void OpenUrl(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
        }
    }
}
