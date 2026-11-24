using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Plex: sign in with a plex.tv/link code, find your servers and read Continue Watching.</summary>
    static class Plex
    {
        public class Pin
        {
            public long Id;
            public string Code;
        }

        public class Server
        {
            public string Id { get; set; }
            public string Name { get; set; }
            public string Uri { get; set; }
            public string Token { get; set; }
        }

        public static bool SignedIn => !string.IsNullOrEmpty(Settings.Current.PlexToken);

        static HttpRequestMessage Request(HttpMethod method, string url, string token)
        {
            var r = new HttpRequestMessage(method, url);
            r.Headers.TryAddWithoutValidation("Accept", "application/json");
            r.Headers.TryAddWithoutValidation("X-Plex-Product", "SimpleLauncher");
            r.Headers.TryAddWithoutValidation("X-Plex-Version", BuildInfo.BuildTag);
            r.Headers.TryAddWithoutValidation("X-Plex-Client-Identifier", Settings.Current.PlexClientId);
            r.Headers.TryAddWithoutValidation("X-Plex-Platform", "Windows");
            r.Headers.TryAddWithoutValidation("X-Plex-Device", "PC");
            r.Headers.TryAddWithoutValidation("X-Plex-Device-Name", "SimpleLauncher (" + Environment.MachineName + ")");
            if (token != null) r.Headers.TryAddWithoutValidation("X-Plex-Token", token);
            return r;
        }

        static async Task<string> Send(HttpMethod method, string url, string token, int timeoutMs)
        {
            using (var cts = new CancellationTokenSource(timeoutMs))
            using (var req = Request(method, url, token))
            using (var res = await Net.Http.SendAsync(req, cts.Token))
            {
                if (!res.IsSuccessStatusCode) throw new HttpRequestException("HTTP " + (int)res.StatusCode);
                return await res.Content.ReadAsStringAsync();
            }
        }

        // ------------------------------------------------------------------ sign in

        public static async Task<Pin> CreatePin()
        {
            var json = JsonNode.Parse(await Send(HttpMethod.Post, "https://plex.tv/api/v2/pins?strong=false", null, 8000));
            return new Pin { Id = (long)json["id"], Code = (string)json["code"] };
        }

        public static async Task<string> CheckPin(long id)
        {
            try
            {
                var json = JsonNode.Parse(await Send(HttpMethod.Get, "https://plex.tv/api/v2/pins/" + id, null, 8000));
                string token = json?["authToken"]?.GetValueKind() == System.Text.Json.JsonValueKind.String ? (string)json["authToken"] : null;
                return string.IsNullOrEmpty(token) ? null : token;
            }
            catch
            {
                return null;
            }
        }

        public static async Task FinishSignIn(string token)
        {
            var s = Settings.Current;
            s.PlexToken = token;
            s.PlexServers = null;
            s.PlexItems = null;
            try
            {
                var json = JsonNode.Parse(await Send(HttpMethod.Get, "https://plex.tv/api/v2/user", token, 8000));
                string name = (string)json?["username"];
                if (string.IsNullOrEmpty(name)) name = (string)json?["title"];
                s.PlexUser = name;
            }
            catch
            {
            }
            Settings.Save();
        }

        public static void SignOut()
        {
            var s = Settings.Current;
            s.PlexToken = null;
            s.PlexUser = null;
            s.PlexServers = null;
            s.PlexItems = null;
            Settings.Save();
        }

        // ------------------------------------------------------------------ Continue Watching

        public static async Task<(List<Watching.Item> items, string error)> ContinueWatching()
        {
            var s = Settings.Current;
            string token = s.PlexToken;
            if (token == null) return (new List<Watching.Item>(), "Sign in to Plex in Settings › External Services.");
            var all = new List<Watching.Item>();

            var saved = Saved();
            bool answered = false;
            foreach (var srv in saved)
            {
                var items = await ItemsFrom(srv);
                if (items == null) continue;
                answered = true;
                all.AddRange(items);
            }

            if (!answered)
            {
                JsonArray resources;
                try
                {
                    resources = JsonNode.Parse(await Send(HttpMethod.Get, "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", token, 10000)) as JsonArray;
                }
                catch
                {
                    return (all, "Couldn't reach Plex. Check the PC's internet connection.");
                }
                var working = new List<Server>();
                bool foundServer = false;
                foreach (var res in resources ?? new JsonArray())
                {
                    if (res == null || !((string)res["provides"] ?? "").Contains("server")) continue;
                    foundServer = true;
                    var srv = new Server
                    {
                        Id = (string)res["clientIdentifier"] ?? "",
                        Name = (string)res["name"] ?? "Plex server",
                        Token = res["accessToken"] != null ? (string)res["accessToken"] : token
                    };
                    srv.Uri = await PickConnection(res["connections"] as JsonArray);
                    if (srv.Uri == null) continue;
                    var items = await ItemsFrom(srv);
                    if (items == null) continue;
                    working.Add(srv);
                    all.AddRange(items);
                }
                if (!foundServer) return (all, "No Plex servers found on your account.");
                if (working.Count == 0) return (all, "Couldn't reach your Plex server. Is it switched on?");
                s.PlexServers = System.Text.Json.JsonSerializer.Serialize(working);
                Settings.Save();
            }
            return (all.OrderByDescending(i => i.LastViewed).Take(12).ToList(), null);
        }

        static List<Server> Saved()
        {
            try
            {
                return string.IsNullOrEmpty(Settings.Current.PlexServers)
                    ? new List<Server>()
                    : System.Text.Json.JsonSerializer.Deserialize<List<Server>>(Settings.Current.PlexServers) ?? new List<Server>();
            }
            catch
            {
                return new List<Server>();
            }
        }

        public static string ServerNames()
        {
            var list = Saved();
            return list.Count == 0 ? null : string.Join(", ", list.Select(x => x.Name));
        }

        /// <summary>Tries every address for a server at once and keeps the first that answers: local first, then remote, then relay.</summary>
        static async Task<string> PickConnection(JsonArray connections)
        {
            if (connections == null) return null;
            var order = new List<string>();
            for (int pass = 0; pass < 3; pass++)
            {
                foreach (var c in connections)
                {
                    if (c == null) continue;
                    bool local = c["local"] != null && (bool)c["local"];
                    bool relay = c["relay"] != null && (bool)c["relay"];
                    bool match = pass == 0 ? local && !relay : pass == 1 ? !local && !relay : relay;
                    string uri = (string)c["uri"];
                    if (match && !string.IsNullOrEmpty(uri) && !order.Contains(uri)) order.Add(uri);
                }
            }
            if (order.Count == 0) return null;
            var tries = order.Select(async uri =>
            {
                try
                {
                    await Send(HttpMethod.Get, uri + "/identity", null, 4000);
                    return uri;
                }
                catch
                {
                    return null;
                }
            }).ToList();
            while (tries.Count > 0)
            {
                var done = await Task.WhenAny(tries);
                tries.Remove(done);
                if (done.Result != null) return done.Result;
            }
            return null;
        }

        static async Task<List<Watching.Item>> ItemsFrom(Server srv)
        {
            string[] paths = { "/hubs/continueWatching/items", "/hubs/home/continueWatching", "/library/onDeck" };
            foreach (string path in paths)
            {
                try
                {
                    var body = await Send(HttpMethod.Get, srv.Uri + path + "?X-Plex-Container-Start=0&X-Plex-Container-Size=20", srv.Token, 6000);
                    var container = JsonNode.Parse(body)?["MediaContainer"];
                    if (container == null) continue;
                    var metadata = container["Metadata"] as JsonArray;
                    if (metadata == null && container["Hub"] is JsonArray hubs && hubs.Count > 0) metadata = hubs[0]?["Metadata"] as JsonArray;
                    if (metadata == null)
                    {
                        if (path == "/library/onDeck") return new List<Watching.Item>();
                        continue;
                    }
                    var items = new List<Watching.Item>();
                    foreach (var m in metadata)
                    {
                        if (m != null) items.Add(ToItem(m, srv));
                    }
                    return items;
                }
                catch
                {
                }
            }
            return null;
        }

        static long L(JsonNode n) => n == null ? 0 : n.GetValueKind() == System.Text.Json.JsonValueKind.Number ? (long)n : 0;

        static Watching.Item ToItem(JsonNode m, Server srv)
        {
            var it = new Watching.Item { Source = "Plex", PlexMachine = srv.Id, PlexKey = (string)m["ratingKey"] ?? "" };
            it.LastViewed = L(m["lastViewedAt"]);
            long duration = L(m["duration"]), offset = L(m["viewOffset"]);
            it.Progress = duration > 0 ? (int)(100 * offset / duration) : 0;
            string left = duration > 0 ? Watching.TimeLeft(duration - offset) : "";
            string image;
            bool episode = (string)m["type"] == "episode";
            if (episode)
            {
                it.Title = (string)m["grandparentTitle"] ?? (string)m["title"] ?? "";
                string se = "S" + L(m["parentIndex"]) + " · E" + L(m["index"]);
                it.Meta = left.Length == 0 ? se : se + " · " + left;
                image = First(m, "thumb", "grandparentArt", "art", "grandparentThumb");
            }
            else
            {
                it.Title = (string)m["title"] ?? "";
                it.Meta = left;
                image = First(m, "art", "thumb");
            }
            if (image != null)
            {
                it.Image = srv.Uri + "/photo/:/transcode?width=520&height=292&minSize=1&upscale=1&url="
                    + Uri.EscapeDataString(image) + "&X-Plex-Token=" + Uri.EscapeDataString(srv.Token ?? "");
            }
            it.Key = "plex:" + srv.Id + ":" + it.PlexKey;
            return it;
        }

        static string First(JsonNode m, params string[] keys)
        {
            foreach (var k in keys)
            {
                string v = (string)m[k];
                if (!string.IsNullOrEmpty(v)) return v;
            }
            return null;
        }

        /// <summary>The episode or film's page in Plex's web app.</summary>
        public static string WebLink(Watching.Item it) =>
            "https://app.plex.tv/desktop/#!/server/" + it.PlexMachine + "/details?key=" + Uri.EscapeDataString("/library/metadata/" + it.PlexKey);
    }
}
