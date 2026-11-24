using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Wide artwork for app tiles, searched by name on SteamGridDB.</summary>
    static class Artwork
    {
        const string Api = "https://www.steamgriddb.com/api/v2";

        public class Option
        {
            public string Thumb;
            public string Url;
            public string Game;
        }

        public static bool HasKey => !string.IsNullOrWhiteSpace(Settings.Current.SearchKey);

        /// <summary>Banner-shaped pictures for a name, best matches first. Empty if nothing was found.</summary>
        public static async Task<List<Option>> Search(string name, int max = 9)
        {
            var results = new List<Option>();
            string key = Settings.Current.SearchKey;
            if (string.IsNullOrWhiteSpace(key) || string.IsNullOrWhiteSpace(name)) return results;

            string json = await Net.GetString(Api + "/search/autocomplete/" + Uri.EscapeDataString(name.Trim()), key);
            var games = new List<(int id, string name)>();
            using (var doc = JsonDocument.Parse(json))
            {
                if (doc.RootElement.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Array)
                {
                    foreach (var g in data.EnumerateArray().Take(3))
                    {
                        games.Add((g.GetProperty("id").GetInt32(), g.TryGetProperty("name", out var n) ? n.GetString() : name));
                    }
                }
            }

            foreach (var game in games)
            {
                foreach (string path in new[] { "/grids/game/" + game.id + "?dimensions=460x215,920x430", "/heroes/game/" + game.id })
                {
                    if (results.Count >= max) break;
                    try
                    {
                        string body = await Net.GetString(Api + path, key);
                        using (var doc = JsonDocument.Parse(body))
                        {
                            if (!doc.RootElement.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Array) continue;
                            foreach (var img in data.EnumerateArray())
                            {
                                if (results.Count >= max) break;
                                string url = img.TryGetProperty("url", out var u) ? u.GetString() : null;
                                string thumb = img.TryGetProperty("thumb", out var t) ? t.GetString() : url;
                                if (url == null || url.EndsWith(".webm") || url.EndsWith(".webp")) continue;
                                results.Add(new Option { Url = url, Thumb = thumb ?? url, Game = game.name });
                            }
                        }
                    }
                    catch
                    {
                    }
                }
                if (results.Count >= max) break;
            }
            return results;
        }

        public static async Task<string> Save(string appId, string url)
        {
            string path = await Net.Download(url, Settings.ArtFolder, appId + "|" + url);
            Settings.Current.Art[appId] = path;
            Settings.Save();
            return path;
        }

        /// <summary>Uses a picture from this PC: copied in, so it stays if the original moves.</summary>
        public static string SaveLocal(string appId, string file)
        {
            string path = Path.Combine(Settings.ArtFolder, Net.Hash(appId + "|" + file) + Path.GetExtension(file));
            File.Copy(file, path, true);
            Settings.Current.Art[appId] = path;
            Settings.Save();
            return path;
        }

        /// <summary>Fetches the first picture for each app that hasn't been tried yet. Returns how many changed.</summary>
        public static async Task<int> FillIn(IEnumerable<AppEntry> apps)
        {
            if (!HasKey) return 0;
            int changed = 0;
            foreach (var a in apps.ToList())
            {
                var s = Settings.Current;
                if (s.Art.ContainsKey(a.Id) || s.ArtTried.Contains(a.Id)) continue;
                s.ArtTried.Add(a.Id);
                try
                {
                    var found = await Search(s.NameOf(a), 1);
                    if (found.Count > 0)
                    {
                        await Save(a.Id, found[0].Url);
                        changed++;
                    }
                }
                catch
                {
                }
                Settings.Save();
            }
            return changed;
        }
    }
}
