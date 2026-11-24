using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Continue Watching: Plex and Jellyfin together, most recent first.</summary>
    static class Watching
    {
        public class Item
        {
            public string Key;
            public string Source;      // "Plex" or "Jellyfin"
            public string Title;
            public string Meta;
            public string Image;
            public int Progress;
            public long LastViewed;
            public string PlexMachine, PlexKey, JellyfinId;
        }

        public static List<Item> Items { get; private set; } = new List<Item>();
        public static string Message { get; private set; } = "Loading…";
        static DateTime loaded = DateTime.MinValue;

        public static bool Stale => (DateTime.Now - loaded).TotalSeconds > 60;

        public static async Task RefreshAsync()
        {
            var tasks = new List<Task<(List<Item> items, string error)>>();
            if (Plex.SignedIn) tasks.Add(Plex.ContinueWatching());
            if (Jellyfin.SignedIn) tasks.Add(Jellyfin.ContinueWatching());
            if (tasks.Count == 0)
            {
                Items = new List<Item>();
                Message = "Sign in to Plex or Jellyfin in Settings › External Services.";
                loaded = DateTime.Now;
                return;
            }
            var results = await Task.WhenAll(tasks);
            Items = results.SelectMany(r => r.items).OrderByDescending(i => i.LastViewed).Take(16).ToList();
            Message = results.Select(r => r.error).FirstOrDefault(e => e != null) ?? "Nothing to continue right now.";
            loaded = DateTime.Now;
        }

        public static void Forget()
        {
            Items = new List<Item>();
            Message = "Loading…";
            loaded = DateTime.MinValue;
        }

        /// <summary>"CONTINUE WATCHING (PLEX)", "(JELLYFIN)", or plain when both are connected.</summary>
        public static string RowTitle()
        {
            bool p = Plex.SignedIn, j = Jellyfin.SignedIn;
            return p && j ? "Continue Watching" : p ? "Continue Watching (Plex)" : j ? "Continue Watching (Jellyfin)" : "Continue Watching";
        }

        public static void Open(Item it, MainWindow host)
        {
            bool plex = it.Source == "Plex";
            string web = plex ? Plex.WebLink(it) : Jellyfin.WebLink(it);
            if (Settings.Current.OpenInBrowser)
            {
                WindowsIntegration.OpenUrl(web);
                host.Toast("Opening " + it.Title + " in your browser…");
                return;
            }
            var app = plex ? Catalog.FindByName("Plex HTPC", "Plex") : Catalog.FindByName("Jellyfin Media Player", "Jellyfin");
            if (app != null)
            {
                host.Launch(app);
                return;
            }
            WindowsIntegration.OpenUrl(web);
            host.Toast("The " + it.Source + " app isn't installed, so it's opening in your browser");
        }

        public static string TimeLeft(long ms)
        {
            long mins = Math.Max(1, (long)Math.Round(ms / 60000.0));
            if (mins < 60) return mins + " min left";
            long h = mins / 60, m = mins % 60;
            return m == 0 ? h + " hr left" : h + " hr " + m.ToString("00") + " min left";
        }
    }
}
