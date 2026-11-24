using System;
using System.IO;
using System.Linq;
using System.Threading;
using System.Windows;

namespace SimpleLauncherHTPC
{
    static class Program
    {
        const string MutexName = "CherryUniverse.SimpleLauncherHTPC";
        const string ShowEventName = "CherryUniverse.SimpleLauncherHTPC.Show";

        public static bool ShellMode { get; private set; }

        [STAThread]
        static void Main(string[] args)
        {
            // Only one SimpleLauncher at a time: opening it again brings the running one forward.
            var mutex = new Mutex(true, MutexName, out bool first);
            if (!first)
            {
                try
                {
                    using (var ev = EventWaitHandle.OpenExisting(ShowEventName)) ev.Set();
                }
                catch
                {
                }
                return;
            }

            Settings.Load();
            ShellMode = args.Any(a => a.Equals("--shell", StringComparison.OrdinalIgnoreCase));
            if (ShellMode)
            {
                // Console mode safety net: two starts in a row that never reached the home screen
                // means something is wrong, so put the normal desktop back.
                if (Settings.Current.FailedStarts >= 2)
                {
                    WindowsIntegration.ConsoleMode = false;
                    Settings.Current.FailedStarts = 0;
                    Settings.Save();
                    WindowsIntegration.StartDesktop();
                }
                else
                {
                    Settings.Current.FailedStarts++;
                    Settings.Save();
                }
            }

            var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
            app.DispatcherUnhandledException += (s, e) =>
            {
                Log(e.Exception);
                e.Handled = true;
            };
            AppDomain.CurrentDomain.UnhandledException += (s, e) => Log(e.ExceptionObject as Exception);

            var window = new MainWindow();
            var show = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
            ThreadPool.RegisterWaitForSingleObject(show, (o, timedOut) =>
                window.Dispatcher.BeginInvoke(new Action(() => window.BringBack(true))), null, -1, false);

            app.Run(window);
            GC.KeepAlive(mutex);
        }

        public static void Log(Exception e)
        {
            if (e == null) return;
            try
            {
                Directory.CreateDirectory(Settings.Folder);
                File.AppendAllText(Path.Combine(Settings.Folder, "errors.log"),
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + BuildInfo.BuildTag + Environment.NewLine + e + Environment.NewLine + Environment.NewLine);
            }
            catch
            {
            }
        }
    }
}
