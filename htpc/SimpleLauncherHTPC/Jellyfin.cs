using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Jellyfin: finds servers on your network, signs in (Quick Connect or password) and reads what you're part-way through.</summary>
    static class Jellyfin
    {
        public class Server
        {
            public string Name;
            public string Address;
            public string Id;
        }

        public class QuickConnect
        {
            public string Code;
            public string Secret;
        }

        public static bool SignedIn => !string.IsNullOrEmpty(Settings.Current.JfToken) && !string.IsNullOrEmpty(Settings.Current.JfServer);

        static string AuthHeader(string token)
        {
            string h = "MediaBrowser Client=\"SimpleLauncher\", Device=\"Windows PC\", DeviceId=\"" + Settings.Current.JfDeviceId
                + "\", Version=\"" + BuildInfo.BuildTag + "\"";
            return token != null ? h + ", Token=\"" + token + "\"" : h;
        }

        static async Task<string> Request(HttpMethod method, string url, string token, string body, int timeoutMs)
        {
            using (var cts = new CancellationTokenSource(timeoutMs))
            using (var req = new HttpRequestMessage(method, url))
            {
                req.Headers.TryAddWithoutValidation("Accept", "application/json");
                string auth = AuthHeader(token);
                req.Headers.TryAddWithoutValidation("Authorization", auth);
                req.Headers.TryAddWithoutValidation("X-Emby-Authorization", auth);
                if (body != null) req.Content = new StringContent(body, Encoding.UTF8, "application/json");
                using (var res = await Net.Http.SendAsync(req, cts.Token))
                {
                    if (!res.IsSuccessStatusCode) throw new HttpRequestException("HTTP " + (int)res.StatusCode);
                    return await res.Content.ReadAsStringAsync();
                }
            }
        }

        // ------------------------------------------------------------------ finding servers

        /// <summary>Asks the network "who is JellyfinServer?" and collects the answers for a couple of seconds.</summary>
        public static async Task<List<Server>> Discover(int waitMs = 2500)
        {
            var found = new List<Server>();
            try
            {
                using (var udp = new UdpClient())
                {
                    udp.EnableBroadcast = true;
                    byte[] ask = Encoding.UTF8.GetBytes("who is JellyfinServer?");
                    await udp.SendAsync(ask, ask.Length, new IPEndPoint(IPAddress.Broadcast, 7359));
                    var end = DateTime.Now.AddMilliseconds(waitMs);
                    while (DateTime.Now < end)
                    {
                        var remaining = end - DateTime.Now;
                        if (remaining <= TimeSpan.Zero) break;
                        var receive = udp.ReceiveAsync();
                        var done = await Task.WhenAny(receive, Task.Delay(remaining));
                        if (done != receive) break;
                        try
                        {
                            var o = JsonNode.Parse(Encoding.UTF8.GetString(receive.Result.Buffer));
                            var s = new Server { Name = (string)o?["Name"] ?? "Jellyfin", Address = TrimSlash((string)o?["Address"] ?? ""), Id = (string)o?["Id"] ?? "" };
                            if (s.Address.Length == 0) continue;
                            if (!found.Any(f => f.Id == s.Id || f.Address == s.Address)) found.Add(s);
                        }
                        catch
                        {
                        }
                    }
                }
            }
            catch
            {
            }
            return found;
        }

        /// <summary>Turns what someone typed ("192.168.1.10", "jelly.local:8096/") into a full address.</summary>
        public static string Normalise(string typed)
        {
            string s = (typed ?? "").Trim();
            if (s.Length == 0) return null;
            if (!s.StartsWith("http://") && !s.StartsWith("https://")) s = "http://" + s;
            s = TrimSlash(s);
            string hostPart = s.Substring(s.IndexOf("://") + 3);
            if (!hostPart.Contains(":") && !hostPart.Contains("/") && s.StartsWith("http://")) s += ":8096";
            return s;
        }

        public static async Task<Server> Check(string address)
        {
            var o = JsonNode.Parse(await Request(HttpMethod.Get, address + "/System/Info/Public", null, null, 6000));
            return new Server { Address = address, Name = (string)o?["ServerName"] ?? "Jellyfin", Id = (string)o?["Id"] ?? "" };
        }

        // ------------------------------------------------------------------ signing in

        public static async Task<bool> QuickConnectEnabled(string address)
        {
            try
            {
                return (await Request(HttpMethod.Get, address + "/QuickConnect/Enabled", null, null, 5000)).Trim().Equals("true", StringComparison.OrdinalIgnoreCase);
            }
            catch
            {
                return false;
            }
        }

        public static async Task<QuickConnect> StartQuickConnect(string address)
        {
            string body;
            try
            {
                body = await Request(HttpMethod.Post, address + "/QuickConnect/Initiate", null, "", 6000);
            }
            catch
            {
                body = await Request(HttpMethod.Get, address + "/QuickConnect/Initiate", null, null, 6000);
            }
            var o = JsonNode.Parse(body);
            return new QuickConnect { Code = (string)o["Code"], Secret = (string)o["Secret"] };
        }

        public static async Task<bool> QuickConnectApproved(string address, QuickConnect q)
        {
            try
            {
                var o = JsonNode.Parse(await Request(HttpMethod.Get, address + "/QuickConnect/Connect?secret=" + Uri.EscapeDataString(q.Secret), null, null, 6000));
                return o?["Authenticated"] != null && (bool)o["Authenticated"];
            }
            catch
            {
                return false;
            }
        }

        public static async Task FinishQuickConnect(Server server, QuickConnect q)
        {
            var body = new JsonObject { ["Secret"] = q.Secret }.ToJsonString();
            Save(server, JsonNode.Parse(await Request(HttpMethod.Post, server.Address + "/Users/AuthenticateWithQuickConnect", null, body, 8000)));
        }

        public static async Task SignInWithPassword(Server server, string user, string password)
        {
            var body = new JsonObject { ["Username"] = user, ["Pw"] = password ?? "" }.ToJsonString();
            Save(server, JsonNode.Parse(await Request(HttpMethod.Post, server.Address + "/Users/AuthenticateByName", null, body, 8000)));
        }

        static void Save(Server server, JsonNode auth)
        {
            var s = Settings.Current;
            var user = auth["User"];
            s.JfServer = server.Address;
            s.JfServerName = server.Name;
            s.JfToken = (string)auth["AccessToken"];
            s.JfUserId = (string)user["Id"];
            s.JfUser = (string)user["Name"];
            s.JfItems = null;
            Settings.Save();
        }

        public static void SignOut()
        {
            var s = Settings.Current;
            string server = s.JfServer, token = s.JfToken;
            if (server != null && token != null)
            {
                _ = Task.Run(async () =>
                {
                    try { await Request(HttpMethod.Post, server + "/Sessions/Logout", token, "", 4000); } catch { }
                });
            }
            s.JfServer = s.JfServerName = s.JfToken = s.JfUserId = s.JfUser = s.JfItems = null;
            Settings.Save();
        }

        // ------------------------------------------------------------------ Continue Watching

        public static async Task<(List<Watching.Item> items, string error)> ContinueWatching()
        {
            var s = Settings.Current;
            var list = new List<Watching.Item>();
            if (!SignedIn || s.JfUserId == null) return (list, "Sign in to Jellyfin in Settings › External Services.");
            string fields = "&fields=ParentId&enableImageTypes=Primary,Thumb,Backdrop&mediaTypes=Video&limit=12";
            string body;
            try
            {
                try
                {
                    body = await Request(HttpMethod.Get, s.JfServer + "/UserItems/Resume?userId=" + s.JfUserId + fields, s.JfToken, null, 8000);
                }
                catch (HttpRequestException)
                {
                    body = await Request(HttpMethod.Get, s.JfServer + "/Users/" + s.JfUserId + "/Items/Resume?" + fields.Substring(1), s.JfToken, null, 8000);
                }
            }
            catch (Exception e)
            {
                return (list, e.Message.Contains("401")
                    ? "Jellyfin signed you out. Sign in again in Settings › External Services."
                    : "Couldn't reach your Jellyfin server.");
            }
            try
            {
                if (JsonNode.Parse(body)?["Items"] is JsonArray arr)
                {
                    foreach (var m in arr)
                    {
                        var it = ToItem(m, s.JfServer, s.JfToken);
                        if (it != null) list.Add(it);
                    }
                }
            }
            catch
            {
                return (list, "Jellyfin sent something unexpected.");
            }
            return (list.OrderByDescending(i => i.LastViewed).ToList(), null);
        }

        static long L(JsonNode n) => n == null ? 0 : n.GetValueKind() == System.Text.Json.JsonValueKind.Number ? (long)n : 0;

        static Watching.Item ToItem(JsonNode m, string server, string token)
        {
            string id = (string)m?["Id"];
            if (string.IsNullOrEmpty(id)) return null;
            var it = new Watching.Item { Source = "Jellyfin", JellyfinId = id, Key = "jf:" + id };
            var ud = m["UserData"];
            long runtime = L(m["RunTimeTicks"]);
            long position = ud != null ? L(ud["PlaybackPositionTicks"]) : 0;
            var pct = ud?["PlayedPercentage"];
            it.Progress = pct != null && pct.GetValueKind() == System.Text.Json.JsonValueKind.Number ? (int)Math.Round((double)pct)
                : runtime > 0 ? (int)(100 * position / runtime) : 0;
            string played = ud != null ? (string)ud["LastPlayedDate"] : null;
            it.LastViewed = DateTimeOffset.TryParse(played, out var when) ? when.ToUnixTimeSeconds() : 0;
            string left = runtime > position && runtime > 0 ? Watching.TimeLeft((runtime - position) / 10000) : "";
            var tags = m["ImageTags"] as JsonObject;
            string imageId, imageType;
            if ((string)m["Type"] == "Episode")
            {
                it.Title = (string)m["SeriesName"] ?? (string)m["Name"] ?? "";
                string se = "S" + L(m["ParentIndexNumber"]) + " · E" + L(m["IndexNumber"]);
                it.Meta = left.Length == 0 ? se : se + " · " + left;
                if (!string.IsNullOrEmpty((string)m["ParentThumbItemId"]))
                {
                    imageId = (string)m["ParentThumbItemId"];
                    imageType = "Thumb";
                }
                else if (tags != null && tags.ContainsKey("Primary"))
                {
                    imageId = id;
                    imageType = "Primary";
                }
                else if (!string.IsNullOrEmpty((string)m["ParentBackdropItemId"]))
                {
                    imageId = (string)m["ParentBackdropItemId"];
                    imageType = "Backdrop";
                }
                else
                {
                    imageId = (string)m["SeriesId"] ?? id;
                    imageType = "Primary";
                }
            }
            else
            {
                it.Title = (string)m["Name"] ?? "";
                it.Meta = left;
                var backdrops = m["BackdropImageTags"] as JsonArray;
                imageType = tags != null && tags.ContainsKey("Thumb") ? "Thumb" : backdrops != null && backdrops.Count > 0 ? "Backdrop" : "Primary";
                imageId = id;
            }
            it.Image = server + "/Items/" + imageId + "/Images/" + imageType + "?fillWidth=520&fillHeight=292&quality=85&api_key=" + Uri.EscapeDataString(token);
            return it;
        }

        /// <summary>The episode or film in Jellyfin's web app.</summary>
        public static string WebLink(Watching.Item it) => Settings.Current.JfServer + "/web/#/details?id=" + it.JellyfinId;

        static string TrimSlash(string s)
        {
            while (s.EndsWith("/")) s = s.Substring(0, s.Length - 1);
            return s;
        }
    }
}
