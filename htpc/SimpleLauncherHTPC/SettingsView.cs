using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace SimpleLauncherHTPC
{
    /// <summary>Settings: categories on the left, their settings on the right. Same layout as on TV.</summary>
    public class SettingsView : View
    {
        string category = "Home screen";

        List<string> Categories()
        {
            var list = new List<string> { "Home screen", "Appearance", "Windows" };
            if (Settings.Current.ContinueWatching) list.Add("External Services");
            list.Add("Danger Zone");
            list.Add("Information");
            return list;
        }

        public override void Build()
        {
            var cats = Categories();
            if (!cats.Contains(category)) category = cats[0];

            var page = new Grid { Margin = new Thickness(56, 40, 56, 30) };
            page.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(260) });
            page.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
            page.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            Children.Add(page);

            var left = new StackPanel();
            var head = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 20) };
            head.Children.Add(Ui.Logo(40));
            var h = Ui.T("Settings", 28, Colors.White, FontWeights.Medium);
            h.Margin = new Thickness(14, 0, 0, 0);
            h.VerticalAlignment = VerticalAlignment.Center;
            head.Children.Add(h);
            left.Children.Add(head);
            foreach (string c in cats)
            {
                string cat = c;
                bool on = cat == category;
                var label = Ui.T(cat, 16, on ? Colors.White : Ui.Dim, FontWeights.Medium);
                var arrow = Ui.T("›", 18, on ? Colors.White : Ui.Faint);
                var g = new Grid();
                g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                g.Children.Add(label);
                Grid.SetColumn(arrow, 1);
                g.Children.Add(arrow);
                var row = new Clickable
                {
                    Child = g, MinHeight = 46, Padding = new Thickness(18, 11, 18, 11), CornerRadius = new CornerRadius(10),
                    Margin = new Thickness(0, 0, 0, 4), BorderThickness = new Thickness(2),
                    NormalBackground = on ? Ui.B(Ui.Selected) : Brushes.Transparent, FocusBackground = Ui.B(Ui.Selected), FocusBorder = Ui.B(Colors.White),
                    Tag = "cat:" + cat
                };
                row.Highlighted = f => label.Foreground = Ui.B(f || on ? Colors.White : Ui.Dim);
                row.Clicked += () =>
                {
                    category = cat;
                    Host.Refresh();
                };
                left.Children.Add(row);
            }
            page.Children.Add(left);

            var right = new StackPanel();
            switch (category)
            {
                case "Home screen": HomeScreen(right); break;
                case "Appearance": Appearance(right); break;
                case "Windows": WindowsPage(right); break;
                case "External Services": Services(right); break;
                case "Danger Zone": DangerZone(right); break;
                default: Information(right); break;
            }
            var scroll = Ui.Scroll(right);
            Grid.SetColumn(scroll, 2);
            page.Children.Add(scroll);
        }

        public override IInputElement FirstFocus => MainWindow.FindTag(this, "cat:" + category);

        static T Tagged<T>(T e, string tag) where T : FrameworkElement
        {
            e.Tag = tag;
            return e;
        }

        // ------------------------------------------------------------------ Home screen

        void HomeScreen(Panel p)
        {
            var s = Settings.Current;
            p.Children.Add(Ui.Section("Layout", 4));
            p.Children.Add(LayoutPictures(this));
            p.Children.Add(Tagged(Ui.Steps("App size", s.AppSize, v =>
            {
                s.AppSize = v;
                Settings.Save();
            }), "appsize"));
            if (s.ContinueWatching)
            {
                p.Children.Add(Tagged(Ui.Steps("Continue Watching size", s.CardSize, v =>
                {
                    s.CardSize = v;
                    Settings.Save();
                }), "cardsize"));
            }

            p.Children.Add(Ui.Section("Extras"));
            p.Children.Add(Tagged(Ui.Toggle("Continue Watching",
                "Shows what you're watching on Plex or Jellyfin. On a PC, selecting a show opens the app rather than the episode (or the episode in your browser), so it's off unless you want it.",
                s.ContinueWatching, v =>
                {
                    s.ContinueWatching = v;
                    Settings.Save();
                    Watching.Forget();
                    Host.Refresh();
                }), "cw"));

            p.Children.Add(Ui.Section("Artwork"));
            bool own = !string.IsNullOrWhiteSpace(s.SgdbKey);
            p.Children.Add(Tagged(Ui.Row("Artwork search",
                "Pictures for your apps come from SteamGridDB. " + (own ? "Using your own key." : string.IsNullOrEmpty(BuildInfo.SgdbKey) ? "This copy has no built-in key, so add your own (it's free)." : "Using the built-in key. You can add your own free key if you like."),
                own ? "Your key ›" : "Built-in key ›", KeyDialog), "sgdb"));
            p.Children.Add(Tagged(Ui.Row("Look for artwork again", "For apps on your home screen that still have a plain card", null, async () =>
            {
                var s2 = Settings.Current;
                foreach (var a in Catalog.HomeApps().Where(a => s2.ArtOf(a.Id) == null && !(s2.Art.TryGetValue(a.Id, out var v) && v == "")))
                {
                    s2.ArtTried.Remove(a.Id);
                }
                Host.Toast("Looking for artwork…");
                int n = await Artwork.FillIn(Catalog.HomeApps());
                Host.Toast(n == 0 ? "No new artwork found" : n == 1 ? "Found artwork for 1 app" : "Found artwork for " + n + " apps");
            }), "refetch"));
        }

        /// <summary>The two layouts side by side as little pictures of the home screen. Select one to use it.</summary>
        public static FrameworkElement LayoutPictures(View view)
        {
            var s = Settings.Current;
            var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
            foreach (bool grid in new[] { false, true })
            {
                bool g = grid, on = s.Grid == grid;
                var pic = Ui.LayoutPicture(g, 240, 135, Ui.C(0x0A0C11));
                var c = new Clickable
                {
                    Width = 252, Height = 147, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(3), Lift = true,
                    Padding = new Thickness(3), NormalBorder = Ui.B(on ? Colors.White : Ui.Line), FocusBorder = Ui.B(Colors.White),
                    Child = new Border { CornerRadius = new CornerRadius(6), ClipToBounds = true, Child = pic }, Tag = g ? "layout:grid" : "layout:default"
                };
                c.Clicked += () =>
                {
                    s.Grid = g;
                    Settings.Save();
                    view.Host.Refresh();
                };
                var label = Ui.T(g ? "Grid" : "Default", 15, on ? Colors.White : Ui.Dim, FontWeights.Medium);
                label.Margin = new Thickness(2, 8, 0, 0);
                var sub = Ui.T(g ? "Your apps fill the screen in rows" : "One row of apps across the middle", 12, Ui.Label);
                var sp = new StackPanel { Margin = new Thickness(0, 0, 20, 0) };
                sp.Children.Add(c);
                sp.Children.Add(label);
                sp.Children.Add(sub);
                row.Children.Add(sp);
            }
            return row;
        }

        /// <summary>The clock and date options, the same as on TV.</summary>
        public static void ClockRows(Panel p, View view)
        {
            var s = Settings.Current;
            p.Children.Add(Tagged(Ui.Toggle("24-hour clock", null, s.Clock24, v =>
            {
                s.Clock24 = v;
                Settings.Save();
                view.Host.Refresh();
            }), "clock24"));
            if (!s.Clock24)
            {
                p.Children.Add(Tagged(Ui.Toggle("Show AM / PM", "Next to the time", s.ShowAmPm, v =>
                {
                    s.ShowAmPm = v;
                    Settings.Save();
                }), "ampm"));
            }
            p.Children.Add(Ui.Section("Date"));
            string now = Ui.DateText(DateTime.Now) ?? "Date hidden";
            p.Children.Add(Tagged(Ui.Toggle("Show the day of the week", "Now showing: " + now, s.ShowWeekday, v =>
            {
                s.ShowWeekday = v;
                Settings.Save();
                view.Host.Refresh();
            }), "weekday"));
            p.Children.Add(Tagged(Ui.Toggle("Show the day and month", null, s.ShowDayMonth, v =>
            {
                s.ShowDayMonth = v;
                Settings.Save();
                view.Host.Refresh();
            }), "daymonth"));
            p.Children.Add(Tagged(Ui.Toggle("Show the year", null, s.ShowYear, v =>
            {
                s.ShowYear = v;
                Settings.Save();
                view.Host.Refresh();
            }), "year"));
        }

        void KeyDialog()
        {
            var s = Settings.Current;
            var sp = new StackPanel();
            sp.Children.Add(Ui.T("Your SteamGridDB key", 22, Colors.White, FontWeights.Medium));
            var info = Ui.Wrap("Make a free account at steamgriddb.com, then copy the API key from Preferences › API. Leave this empty to use the built-in key.", 14, Ui.Dim);
            info.Margin = new Thickness(0, 8, 0, 14);
            sp.Children.Add(info);
            var box = Ui.Input(s.SgdbKey, "Paste your key here");
            sp.Children.Add(box);
            var buttons = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 0) };
            var save = Ui.Pill("Save", () =>
            {
                s.SgdbKey = string.IsNullOrWhiteSpace(box.Text) ? null : box.Text.Trim();
                s.ArtTried.RemoveAll(id => s.ArtOf(id) == null);
                Settings.Save();
                Host.CloseOverlay();
                Host.Refresh();
            }, true);
            var site = Ui.Pill("Open steamgriddb.com", () => WindowsIntegration.OpenUrl("https://www.steamgriddb.com/profile/preferences/api"));
            site.Margin = new Thickness(10, 0, 0, 0);
            buttons.Children.Add(save);
            buttons.Children.Add(site);
            sp.Children.Add(buttons);
            Host.ShowPanel(sp, 560);
        }

        // ------------------------------------------------------------------ Appearance

        void Appearance(Panel p)
        {
            var s = Settings.Current;
            p.Children.Add(Ui.Section("Background", 4));
            string bg = s.BgMode == "photo" ? "Your photo" : s.BgMode == "wallpaper" ? "Your Windows wallpaper" : Ui.BgNames[Ui.Clamp(s.BgColour, Ui.BgNames.Length)];
            p.Children.Add(Tagged(Ui.Row("Background", "A colour, your Windows wallpaper or one of your photos", bg + " ›", () => Host.Go(new BackgroundView())), "bg"));
            p.Children.Add(Tagged(Ui.Choice("Accent colour", "The glow and highlights", Ui.AccentNames, s.Accent, v =>
            {
                s.Accent = v;
                Settings.Save();
                Host.Refresh();
            }), "accent"));
            p.Children.Add(Ui.Section("Clock"));
            ClockRows(p, this);
        }

        // ------------------------------------------------------------------ Windows

        void WindowsPage(Panel p)
        {
            var s = Settings.Current;
            p.Children.Add(Ui.Section("Starting up", 4));
            p.Children.Add(Tagged(Ui.Toggle("Start with Windows", "SimpleLauncher opens when you sign in",
                WindowsIntegration.StartsWithWindows, v => WindowsIntegration.StartsWithWindows = v), "startup"));
            p.Children.Add(Tagged(Ui.Toggle("Full screen", "Covers the taskbar, like a TV. Off: a window you can resize",
                s.FullScreen, v =>
                {
                    s.FullScreen = v;
                    Settings.Save();
                    Host.ApplyWindowMode();
                }), "fullscreen"));

            p.Children.Add(Ui.Section("Getting back to SimpleLauncher"));
            p.Children.Add(Tagged(Ui.Toggle("Come back when an app closes", "Closing Plex, a game or a website brings SimpleLauncher back",
                s.ComeBack, v =>
                {
                    s.ComeBack = v;
                    Settings.Save();
                }), "comeback"));
            p.Children.Add(Tagged(Ui.Row("Keyboard shortcut", "Brings SimpleLauncher back from anywhere", "Ctrl + Alt + Home", null), "hotkey"));
            p.Children.Add(Tagged(Ui.Row("Controller", "Hold both buttons for a second, from anywhere", "View + Menu", null), "combo"));

            p.Children.Add(Ui.Section("Power button"));
            p.Children.Add(Tagged(Ui.Toggle("Sleep", null, s.PowerSleep, v => { s.PowerSleep = v; Settings.Save(); }), "psleep"));
            p.Children.Add(Tagged(Ui.Toggle("Restart", null, s.PowerRestart, v => { s.PowerRestart = v; Settings.Save(); }), "prestart"));
            p.Children.Add(Tagged(Ui.Toggle("Shut down", null, s.PowerShutDown, v => { s.PowerShutDown = v; Settings.Save(); }), "pshut"));
        }

        // ------------------------------------------------------------------ External Services

        void Services(Panel p)
        {
            var s = Settings.Current;
            p.Children.Add(Ui.Section("Plex", 4));
            if (Plex.SignedIn)
            {
                p.Children.Add(Tagged(Ui.Row("Signed in", (s.PlexUser ?? "Your Plex account") + (Plex.ServerNames() is string n ? " · " + n : ""), null, null), "plex:status"));
                p.Children.Add(Tagged(Ui.Row("Sign out of Plex", null, null, () =>
                {
                    Plex.SignOut();
                    Watching.Forget();
                    Host.Refresh();
                }), "plex:out"));
            }
            else
            {
                p.Children.Add(Tagged(Ui.Row("Sign in to Plex", "You'll get a code to enter at plex.tv/link", "Sign in ›", PlexSignIn), "plex:in"));
            }

            p.Children.Add(Ui.Section("Jellyfin"));
            if (Jellyfin.SignedIn)
            {
                p.Children.Add(Tagged(Ui.Row("Signed in", (s.JfUser ?? "Your account") + " · " + (s.JfServerName ?? s.JfServer), null, null), "jf:status"));
                p.Children.Add(Tagged(Ui.Row("Sign out of Jellyfin", null, null, () =>
                {
                    Jellyfin.SignOut();
                    Watching.Forget();
                    Host.Refresh();
                }), "jf:out"));
            }
            else
            {
                p.Children.Add(Tagged(Ui.Row("Sign in to Jellyfin", "Finds your server on the network, then Quick Connect or your password", "Sign in ›", () => Host.Go(new JellyfinView())), "jf:in"));
            }

            p.Children.Add(Ui.Section("Selecting a show"));
            p.Children.Add(Tagged(Ui.Choice("Opens", s.OpenInBrowser ? "The episode itself, in Plex or Jellyfin's web player" : "The Plex or Jellyfin app on this PC (not the episode)",
                new[] { "The app", "In the browser" }, s.OpenInBrowser ? 1 : 0, v =>
                {
                    s.OpenInBrowser = v == 1;
                    Settings.Save();
                    Host.Refresh();
                }), "open"));
        }

        async void PlexSignIn()
        {
            Plex.Pin pin;
            try
            {
                pin = await Plex.CreatePin();
            }
            catch
            {
                Host.Toast("Couldn't reach Plex. Check the internet connection.");
                return;
            }
            bool open = true;
            var sp = new StackPanel();
            sp.Children.Add(Ui.T("Sign in to Plex", 22, Colors.White, FontWeights.Medium));
            var how = Ui.Wrap("On your phone or computer, go to plex.tv/link and enter this code:", 15, Ui.Dim);
            how.Margin = new Thickness(0, 8, 0, 14);
            sp.Children.Add(how);
            var code = Ui.T(pin.Code, 54, Colors.White, FontWeights.SemiBold);
            code.HorizontalAlignment = HorizontalAlignment.Center;
            sp.Children.Add(code);
            var wait = Ui.T("Waiting for you to enter the code…", 13, Ui.Label);
            wait.HorizontalAlignment = HorizontalAlignment.Center;
            wait.Margin = new Thickness(0, 10, 0, 16);
            sp.Children.Add(wait);
            var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
            buttons.Children.Add(Ui.Pill("Open plex.tv/link here", () => WindowsIntegration.OpenUrl("https://plex.tv/link")));
            var cancel = Ui.Pill("Cancel", () => Host.CloseOverlay());
            cancel.Margin = new Thickness(10, 0, 0, 0);
            buttons.Children.Add(cancel);
            sp.Children.Add(buttons);
            Host.ShowPanel(sp, 560, () => open = false);

            var until = DateTime.Now.AddMinutes(10);
            while (open && DateTime.Now < until)
            {
                await Task.Delay(2000);
                if (!open) return;
                string token = await Plex.CheckPin(pin.Id);
                if (token == null) continue;
                await Plex.FinishSignIn(token);
                Watching.Forget();
                Host.CloseOverlay();
                Host.Toast("Signed in to Plex");
                Host.Refresh();
                return;
            }
            if (open)
            {
                Host.CloseOverlay();
                Host.Toast("The Plex code ran out. Try again.");
            }
        }

        // ------------------------------------------------------------------ Danger Zone

        void DangerZone(Panel p)
        {
            p.Children.Add(Ui.Section("Console mode", 4));
            p.Children.Add(Tagged(Ui.Toggle("Use SimpleLauncher instead of the desktop",
                "For this Windows account only. When you sign in you get SimpleLauncher, like a games console, and the desktop and taskbar stay away until you press Desktop. Takes effect next time you sign in.",
                WindowsIntegration.ConsoleMode, v =>
                {
                    if (!v)
                    {
                        WindowsIntegration.ConsoleMode = false;
                        Host.Toast("Console mode off. The normal desktop is back next time you sign in.");
                        return;
                    }
                    Host.ShowMenu("Turn on console mode?",
                        "Next time you sign in, SimpleLauncher starts instead of the Windows desktop. The Desktop button still opens it whenever you want, Ctrl + Alt + Delete and Task Manager keep working, and if SimpleLauncher fails to start twice in a row Windows goes back to normal by itself.",
                        new List<(string, Action)>
                        {
                            ("Turn on console mode", () =>
                            {
                                WindowsIntegration.ConsoleMode = true;
                                Host.Toast(WindowsIntegration.ConsoleMode ? "Console mode on from your next sign-in" : "Windows didn't allow console mode");
                                Host.Refresh();
                            }),
                            ("Cancel", () => Host.Refresh())
                        });
                }), "console"));
            var note = Ui.Wrap("You can always get out: the Desktop button opens the full Windows desktop, Ctrl + Alt + Delete and Task Manager keep working, and switching this off puts everything back.", 13, Ui.Dim);
            note.Margin = new Thickness(4, 4, 0, 6);
            p.Children.Add(note);

            p.Children.Add(Ui.Section("Start again"));
            p.Children.Add(Tagged(Ui.Row("Run setup again", "Go through first-time setup with your current choices", "Start ›", () => Host.Replace(new SetupView())), "setup"));
            p.Children.Add(Tagged(Ui.Row("Reset SimpleLauncher", "Forgets your apps, artwork, look and sign-ins, then starts setup", "Reset ›", () =>
                Host.Confirm("Reset SimpleLauncher?", "Everything goes back to how it was when you installed it. Your other apps aren't affected.", "Reset", () =>
                {
                    WindowsIntegration.ConsoleMode = false;
                    WindowsIntegration.StartsWithWindows = false;
                    Settings.Reset();
                    Watching.Forget();
                    Host.ApplyWindowMode();
                    Host.ApplyBackground();
                    Host.Replace(new SetupView());
                })), "reset"));
        }

        // ------------------------------------------------------------------ Information

        void Information(Panel p)
        {
            p.Children.Add(Ui.Section("SimpleLauncher", 4));
            p.Children.Add(Tagged(Ui.Row("Version", "A product of Cherry Universe", BuildInfo.Release + " (build " + BuildInfo.BuildTag + ")", null), "version"));
            p.Children.Add(Tagged(Ui.Row("Software update", "Checks GitHub for a newer SimpleLauncher for Windows", "Check now ›", () => Updater.CheckAndOffer(Host, true)), "update"));
            p.Children.Add(Tagged(Ui.Row("Report a problem or suggest something", "github.com/" + BuildInfo.Repo, "Open ›", () => WindowsIntegration.OpenUrl("https://github.com/" + BuildInfo.Repo + "/issues")), "issues"));
            p.Children.Add(Ui.Section("This PC"));
            p.Children.Add(Tagged(Ui.Row("Windows", Environment.MachineName, WindowsIntegration.WindowsName() + " (build " + Environment.OSVersion.Version.Build + ")", null), "windows"));
            p.Children.Add(Tagged(Ui.Row("Apps found", "In your Start menu, including Store apps", Settings.Current.Known.Count.ToString(), null), "found"));
        }
    }
}
