using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace SimpleLauncherHTPC
{
    /// <summary>
    /// Software update: looks for the newest "htpc-" release on GitHub (the TV's releases are left alone),
    /// downloads its installer and runs it quietly. SimpleLauncher reopens when it's done.
    /// </summary>
    static class Updater
    {
        public class Release
        {
            public string Tag;
            public string Build;
            public string Url;
            public string Notes;
        }

        public static async Task<Release> Latest()
        {
            string json = await Net.GetString("https://api.github.com/repos/" + BuildInfo.Repo + "/releases?per_page=30");
            using (var doc = JsonDocument.Parse(json))
            {
                Release best = null;
                foreach (var r in doc.RootElement.EnumerateArray())
                {
                    string tag = r.GetProperty("tag_name").GetString() ?? "";
                    if (!tag.StartsWith(BuildInfo.TagPrefix)) continue;
                    if (r.TryGetProperty("draft", out var d) && d.GetBoolean()) continue;
                    string url = null;
                    foreach (var a in r.GetProperty("assets").EnumerateArray())
                    {
                        string name = a.GetProperty("name").GetString() ?? "";
                        if (name.EndsWith("-Setup.exe", StringComparison.OrdinalIgnoreCase)) url = a.GetProperty("browser_download_url").GetString();
                    }
                    if (url == null) continue;
                    var rel = new Release
                    {
                        Tag = tag,
                        Build = tag.Substring(BuildInfo.TagPrefix.Length),
                        Url = url,
                        Notes = r.TryGetProperty("body", out var b) ? b.GetString() : null
                    };
                    if (best == null || Compare(rel.Build, best.Build) > 0) best = rel;
                }
                return best;
            }
        }

        /// <summary>Builds look like DDMMYY-major.minor. Newer date first, then the bigger build number.</summary>
        public static int Compare(string a, string b)
        {
            (int date, Version v) Parse(string s)
            {
                var parts = (s ?? "").Split('-');
                int date = 0;
                if (parts[0].Length == 6 && int.TryParse(parts[0], out _))
                {
                    date = int.Parse(parts[0].Substring(4, 2) + parts[0].Substring(2, 2) + parts[0].Substring(0, 2));
                }
                Version v = parts.Length > 1 && Version.TryParse(parts[1].Contains(".") ? parts[1] : parts[1] + ".0", out var vv) ? vv : new Version(0, 0);
                return (date, v);
            }
            var x = Parse(a);
            var y = Parse(b);
            int c = x.date.CompareTo(y.date);
            return c != 0 ? c : x.v.CompareTo(y.v);
        }

        public static async void CheckAndOffer(MainWindow host, bool userAsked)
        {
            if (userAsked) host.Toast("Checking for updates…");
            Release r;
            try
            {
                r = await Latest();
            }
            catch
            {
                if (userAsked) host.ShowMenu("Couldn't check for updates", "Check the PC's internet connection and try again. If it keeps failing, please leave a bug report on GitHub.", new[] { ("OK", (Action)null) });
                return;
            }
            if (r == null || Compare(r.Build, BuildInfo.BuildTag) <= 0)
            {
                if (userAsked) host.ShowMenu("You're up to date", "SimpleLauncher " + BuildInfo.Release + " (build " + BuildInfo.BuildTag + ") is the newest version for Windows.", new[] { ("OK", (Action)null) });
                return;
            }
            host.ShowMenu("Update available", "Build " + r.Build + " is ready (you have " + BuildInfo.BuildTag + "). SimpleLauncher closes while it installs, then opens again.",
                new[]
                {
                    ("Download and install", (Action)(() => Install(host, r))),
                    ("Not now", (Action)null)
                });
        }

        static async void Install(MainWindow host, Release r)
        {
            var sp = new StackPanel();
            sp.Children.Add(Ui.T("Downloading build " + r.Build, 22, Colors.White, FontWeights.Medium));
            var status = Ui.T("Starting…", 14, Ui.Dim);
            status.Margin = new Thickness(0, 10, 0, 12);
            sp.Children.Add(status);
            var track = new Grid { Height = 6, Background = Ui.B(Ui.Track) };
            var fill = new Border { Background = Ui.B(Ui.Accent), HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
            track.Children.Add(fill);
            sp.Children.Add(track);
            host.ShowPanel(sp, 520);

            string file = Path.Combine(Path.GetTempPath(), "SimpleLauncherHTPC-" + r.Build + "-Setup.exe");
            try
            {
                using (var res = await Net.Http.GetAsync(r.Url, HttpCompletionOption.ResponseHeadersRead))
                {
                    res.EnsureSuccessStatusCode();
                    long total = res.Content.Headers.ContentLength ?? -1;
                    using (var input = await res.Content.ReadAsStreamAsync())
                    using (var output = File.Create(file))
                    {
                        var buf = new byte[81920];
                        long done = 0;
                        int n;
                        while ((n = await input.ReadAsync(buf, 0, buf.Length)) > 0)
                        {
                            await output.WriteAsync(buf, 0, n);
                            done += n;
                            if (total > 0)
                            {
                                fill.Width = 460 * done / (double)total;
                                status.Text = (done / 1048576) + " of " + (total / 1048576) + " MB";
                            }
                        }
                    }
                }
                status.Text = "Installing… SimpleLauncher will open again in a moment.";
                Process.Start(new ProcessStartInfo(file, "/SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS") { UseShellExecute = true });
                await Task.Delay(800);
                Application.Current.Shutdown();
            }
            catch
            {
                host.ShowMenu("The update didn't work",
                    "Try again in a little while. If it keeps failing, please leave a bug report on GitHub, or download the installer from the releases page and run it yourself.",
                    new[]
                    {
                        ("Open the releases page", (Action)(() => WindowsIntegration.OpenUrl("https://github.com/" + BuildInfo.Repo + "/releases"))),
                        ("OK", (Action)null)
                    });
            }
        }
    }
}
