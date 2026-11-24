using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Finds the apps on this PC (everything in the Start menu, Store apps included) and opens them.</summary>
    static class Catalog
    {
        static readonly Regex Junk = new Regex(
            @"\b(uninstall|uninstaller|readme|read me|help|manual|documentation|release notes|license|licence|website|web site|support|changelog|what's new|safe mode|troubleshoot|repair|debug)\b",
            RegexOptions.IgnoreCase);

        static readonly string[] Suggested =
        {
            "plex", "jellyfin", "emby", "kodi", "netflix", "disney", "prime video", "youtube", "spotify", "twitch",
            "steam", "xbox", "epic games", "gog galaxy", "playnite", "retroarch", "vlc", "apple tv", "iplayer",
            "itvx", "channel 4", "now", "paramount", "crunchyroll", "tidal", "deezer", "amazon music", "moonlight"
        };

        public static bool Scanning { get; private set; }

        /// <summary>Reads the Start menu again. Takes a second or two, so it runs in the background.</summary>
        public static async Task<List<AppEntry>> ScanAsync()
        {
            Scanning = true;
            try
            {
                var found = await Task.Run(ReadStartApps);
                if (found.Count > 0)
                {
                    Settings.Current.Known = found;
                    Settings.Save();
                }
                return Settings.Current.Known;
            }
            finally
            {
                Scanning = false;
            }
        }

        static List<AppEntry> ReadStartApps()
        {
            var list = new List<AppEntry>();
            try
            {
                var psi = new ProcessStartInfo("powershell.exe",
                    "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"[Console]::OutputEncoding=[Text.Encoding]::UTF8; " +
                    "Get-StartApps | Select-Object Name,AppID | ConvertTo-Json -Compress\"")
                {
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true,
                    StandardOutputEncoding = Encoding.UTF8
                };
                using (var p = Process.Start(psi))
                {
                    string json = p.StandardOutput.ReadToEnd();
                    p.WaitForExit(20000);
                    if (string.IsNullOrWhiteSpace(json)) return list;
                    using (var doc = JsonDocument.Parse(json))
                    {
                        var items = doc.RootElement.ValueKind == JsonValueKind.Array
                            ? doc.RootElement.EnumerateArray().ToList()
                            : new List<JsonElement> { doc.RootElement };
                        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                        foreach (var it in items)
                        {
                            string name = it.TryGetProperty("Name", out var n) ? n.GetString() : null;
                            string id = it.TryGetProperty("AppID", out var a) ? a.GetString() : null;
                            if (string.IsNullOrWhiteSpace(name) || string.IsNullOrWhiteSpace(id)) continue;
                            if (Junk.IsMatch(name) || name.StartsWith("SimpleLauncher", StringComparison.OrdinalIgnoreCase)) continue;
                            string ext = Path.GetExtension(id.Split('?')[0]).ToLowerInvariant();
                            if (ext == ".txt" || ext == ".chm" || ext == ".pdf" || ext == ".html" || ext == ".htm" || ext == ".rtf" || ext == ".ini") continue;
                            if (!seen.Add(name)) continue;
                            string kind = id.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? "website"
                                : id.Contains("!") ? "store" : "program";
                            list.Add(new AppEntry { Id = "start:" + id, Name = name, Kind = kind, Target = id });
                        }
                    }
                }
            }
            catch
            {
            }
            return list.OrderBy(a => a.Name, StringComparer.CurrentCultureIgnoreCase).ToList();
        }

        public static IEnumerable<AppEntry> All() => Settings.Current.Known.Concat(Settings.Current.CustomApps);

        public static AppEntry Find(string id) => All().FirstOrDefault(a => a.Id == id);

        public static List<AppEntry> HomeApps() =>
            Settings.Current.Home.Select(Find).Where(a => a != null).ToList();

        public static bool IsSuggested(AppEntry a)
        {
            string n = a.Name.ToLowerInvariant();
            return Suggested.Any(s => s == "now" ? n == "now" : n.Contains(s));
        }

        /// <summary>The first time round: a sensible starting set for a TV.</summary>
        public static void PickSuggested()
        {
            var s = Settings.Current;
            if (s.Home.Count > 0) return;
            s.Home = s.Known.Where(IsSuggested).Take(12).Select(a => a.Id).ToList();
            Settings.Save();
        }

        public static bool Launch(AppEntry a)
        {
            if (a == null) return false;
            try
            {
                if (!a.Custom && a.Id.StartsWith("start:"))
                {
                    Process.Start(new ProcessStartInfo("explorer.exe", "\"shell:AppsFolder\\" + a.Target + "\"") { UseShellExecute = true });
                    return true;
                }
                if (a.Kind == "store")
                {
                    Process.Start(new ProcessStartInfo("explorer.exe", "\"shell:AppsFolder\\" + a.Target + "\"") { UseShellExecute = true });
                    return true;
                }
                if (a.Kind == "website")
                {
                    string url = a.Target.Contains("://") ? a.Target : "https://" + a.Target;
                    Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
                    return true;
                }
                var psi = new ProcessStartInfo(a.Target) { UseShellExecute = true, Arguments = a.Args ?? "" };
                string dir = Path.GetDirectoryName(a.Target);
                if (!string.IsNullOrEmpty(dir) && Directory.Exists(dir)) psi.WorkingDirectory = dir;
                Process.Start(psi);
                return true;
            }
            catch
            {
                return false;
            }
        }

        /// <summary>The Plex or Jellyfin app on this PC, for Continue Watching.</summary>
        public static AppEntry FindByName(params string[] names)
        {
            foreach (string n in names)
            {
                var hit = All().FirstOrDefault(a => a.Name.IndexOf(n, StringComparison.OrdinalIgnoreCase) >= 0);
                if (hit != null) return hit;
            }
            return null;
        }
    }
}
