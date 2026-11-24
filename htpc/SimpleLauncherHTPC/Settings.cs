using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

namespace SimpleLauncherHTPC
{
    /// <summary>One app on the PC: found in the Start menu, or added by you.</summary>
    public class AppEntry
    {
        public string Id { get; set; }        // "start:<AppID>" or "custom:<guid>"
        public string Name { get; set; }
        public string Kind { get; set; }      // "program", "store" or "website"
        public string Target { get; set; }    // Start menu AppID, a file path or a web address
        public string Args { get; set; }
        public bool Custom { get; set; }

        public string KindLabel => Custom
            ? "Your own · " + (Kind == "website" ? "website" : Kind == "store" ? "Store app" : "program")
            : Kind == "store" ? "Store app" : Kind == "website" ? "Website" : "Program";
    }

    /// <summary>Everything SimpleLauncher remembers, saved as JSON in %LOCALAPPDATA%\SimpleLauncher.</summary>
    public class Settings
    {
        public static Settings Current { get; private set; } = new Settings();

        public static readonly string Folder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "SimpleLauncher");
        public static string ArtFolder => Dir("art");
        public static string BackgroundFolder => Dir("backgrounds");
        static string FilePath => Path.Combine(Folder, "settings.json");

        static string Dir(string name)
        {
            string d = Path.Combine(Folder, name);
            Directory.CreateDirectory(d);
            return d;
        }

        // First-time setup
        public bool SetupDone { get; set; }

        // Apps
        public List<string> Home { get; set; } = new List<string>();                         // ids, in order
        public List<AppEntry> Known { get; set; } = new List<AppEntry>();                    // found last time
        public List<AppEntry> CustomApps { get; set; } = new List<AppEntry>();
        public Dictionary<string, string> Art { get; set; } = new Dictionary<string, string>();       // id -> image file, "" = plain card
        public Dictionary<string, string> Names { get; set; } = new Dictionary<string, string>();     // id -> your name for it
        public List<string> ArtTried { get; set; } = new List<string>();                     // ids already searched once

        // Home screen
        public bool Grid { get; set; } = true;
        public int AppSize { get; set; } = 2;
        public int CardSize { get; set; } = 2;

        // Appearance
        public int Accent { get; set; }
        public int BgColour { get; set; }
        public string BgMode { get; set; } = "colour";       // "colour", "photo" or "wallpaper"
        public string BgPhoto { get; set; }
        public List<string> Photos { get; set; } = new List<string>();
        public int Dim { get; set; } = 2;
        public int Fit { get; set; }
        public bool Clock24 { get; set; } = true;
        public bool ShowAmPm { get; set; } = true;
        public bool ShowWeekday { get; set; } = true;
        public bool ShowDayMonth { get; set; } = true;
        public bool ShowYear { get; set; }

        // Windows
        public bool FullScreen { get; set; } = true;
        public bool ComeBack { get; set; } = true;
        public bool PowerSleep { get; set; } = true;
        public bool PowerRestart { get; set; } = true;
        public bool PowerShutDown { get; set; } = true;

        // Artwork
        public string SgdbKey { get; set; }

        // Continue Watching (off unless you turn it on)
        public bool ContinueWatching { get; set; }
        public bool OpenInBrowser { get; set; }
        public string PlexToken { get; set; }
        public string PlexUser { get; set; }
        public string PlexClientId { get; set; }
        public string PlexServers { get; set; }
        public string PlexItems { get; set; }
        public string JfServer { get; set; }
        public string JfServerName { get; set; }
        public string JfUserId { get; set; }
        public string JfUser { get; set; }
        public string JfToken { get; set; }
        public string JfDeviceId { get; set; }
        public string JfItems { get; set; }

        // Console mode safety net: starts that never got as far as the home screen
        public int FailedStarts { get; set; }

        static readonly JsonSerializerOptions Options = new JsonSerializerOptions { WriteIndented = true };

        public static void Load()
        {
            try
            {
                if (File.Exists(FilePath))
                {
                    Current = JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath), Options) ?? new Settings();
                }
            }
            catch
            {
                Current = new Settings();
            }
            if (string.IsNullOrEmpty(Current.PlexClientId)) Current.PlexClientId = "simplelauncher-htpc-" + Guid.NewGuid();
            if (string.IsNullOrEmpty(Current.JfDeviceId)) Current.JfDeviceId = "simplelauncher-htpc-" + Guid.NewGuid();
        }

        public static void Save()
        {
            try
            {
                Directory.CreateDirectory(Folder);
                string tmp = FilePath + ".tmp";
                File.WriteAllText(tmp, JsonSerializer.Serialize(Current, Options));
                File.Copy(tmp, FilePath, true);
                File.Delete(tmp);
            }
            catch
            {
            }
        }

        public static void Reset()
        {
            Current = new Settings();
            Load();
            Save();
        }

        public string NameOf(AppEntry a) => a == null ? "" : Names.TryGetValue(a.Id, out var n) && !string.IsNullOrWhiteSpace(n) ? n : a.Name;

        public string ArtOf(string id) => Art.TryGetValue(id, out var p) && !string.IsNullOrEmpty(p) && File.Exists(p) ? p : null;

        public string SearchKey => !string.IsNullOrWhiteSpace(SgdbKey) ? SgdbKey.Trim() : BuildInfo.SgdbKey;
    }
}
