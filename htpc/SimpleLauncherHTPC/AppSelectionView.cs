using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace SimpleLauncherHTPC
{
    /// <summary>App Selection: every app found on the PC plus your own. Select to add or remove; Menu or right-click for more.</summary>
    public class AppSelectionView : View
    {
        static readonly string[] Filters = { "All", "Programs", "Store apps", "Websites", "Your own" };
        int filter;

        public override void Build()
        {
            var s = Settings.Current;
            var page = new DockPanel { Margin = new Thickness(56, 40, 56, 30) };
            Children.Add(page);

            var head = new StackPanel { Orientation = Orientation.Horizontal };
            head.Children.Add(Ui.Logo(52));
            var titles = new StackPanel { Margin = new Thickness(22, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            titles.Children.Add(Ui.T("App Selection", 32, Colors.White, FontWeights.Medium));
            titles.Children.Add(Ui.T("Select an app to add it to or remove it from your home screen. Press Menu or right-click to move it or change its artwork.", 15, Ui.Dim));
            head.Children.Add(titles);
            DockPanel.SetDock(head, Dock.Top);
            page.Children.Add(head);

            var pills = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 20, 0, 18) };
            for (int i = 0; i < Filters.Length; i++)
            {
                int idx = i;
                var p = Ui.Pill(Filters[i], () =>
                {
                    filter = idx;
                    Host.Refresh();
                }, idx == filter, 14);
                p.MinHeight = 36;
                p.Padding = new Thickness(18, 7, 18, 8);
                p.Margin = new Thickness(0, 0, 8, 0);
                p.Tag = "filter:" + i;
                pills.Children.Add(p);
            }
            DockPanel.SetDock(pills, Dock.Top);
            page.Children.Add(pills);

            // Footer
            var foot = new Grid { Margin = new Thickness(0, 14, 0, 0) };
            foot.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            foot.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            string status = Catalog.Scanning && s.Known.Count == 0
                ? "Looking for the apps on this PC…"
                : "Found " + s.Known.Count + " apps in your Start menu. Numbered apps show on your home screen in that order.";
            var st = Ui.T(status, 14, Ui.Dim);
            st.VerticalAlignment = VerticalAlignment.Center;
            foot.Children.Add(st);
            var done = Ui.Pill("Done", () => Host.GoBack(), true);
            done.Tag = "done";
            Grid.SetColumn(done, 1);
            foot.Children.Add(done);
            DockPanel.SetDock(foot, Dock.Bottom);
            page.Children.Add(foot);

            // The apps
            var wrap = new WrapPanel();
            const double w = 214, h = 116;
            var apps = Catalog.All().Where(Keep).OrderBy(a => s.Home.Contains(a.Id) ? 0 : 1)
                .ThenBy(a => s.Home.IndexOf(a.Id)).ThenBy(a => s.NameOf(a), StringComparer.CurrentCultureIgnoreCase).ToList();
            foreach (var a in apps)
            {
                var app = a;
                int pos = s.Home.IndexOf(app.Id);
                var t = Ui.Tile(app, w, h, () => Toggle(app), () => Menu(app), pos >= 0 ? (pos + 1).ToString() : null, app.KindLabel, pos < 0);
                Ui.TileOf(t).Tag = "app:" + app.Id;
                t.Margin = new Thickness(0, 0, 24, 18);
                wrap.Children.Add(t);
            }
            if (filter == 0 || filter == 4)
            {
                var add = Ui.AddTile("Add your own", "A program, game or website", w, h, () => Host.Go(new AddAppView(null)));
                Ui.TileOf(add).Tag = "add";
                wrap.Children.Add(add);
            }
            page.Children.Add(Ui.Scroll(wrap));
        }

        bool Keep(AppEntry a)
        {
            switch (filter)
            {
                case 1: return !a.Custom && a.Kind == "program";
                case 2: return !a.Custom && a.Kind == "store";
                case 3: return a.Kind == "website";
                case 4: return a.Custom;
                default: return true;
            }
        }

        void Toggle(AppEntry a)
        {
            var home = Settings.Current.Home;
            if (home.Contains(a.Id)) home.Remove(a.Id);
            else home.Add(a.Id);
            Settings.Save();
            Host.Refresh();
            if (home.Contains(a.Id)) FetchArt(a);
        }

        async void FetchArt(AppEntry a)
        {
            if (await Artwork.FillIn(new[] { a }) > 0 && Host.Current == this && !Host.OverlayOpen) Host.Refresh();
        }

        void Menu(AppEntry a)
        {
            var s = Settings.Current;
            var opts = new List<(string, Action)>();
            int i = s.Home.IndexOf(a.Id);
            opts.Add((i >= 0 ? "Remove from the home screen" : "Add to the home screen", () => Toggle(a)));
            if (i > 0) opts.Add(("Move earlier", () => Move(a.Id, -1)));
            if (i >= 0 && i < s.Home.Count - 1) opts.Add(("Move later", () => Move(a.Id, 1)));
            opts.Add(("Change its artwork or name", () => Host.Go(new AddAppView(a))));
            if (a.Custom)
            {
                opts.Add(("Delete this app from SimpleLauncher", () => Host.Confirm("Delete " + s.NameOf(a) + "?",
                    "It's only removed from SimpleLauncher. The program or website itself isn't touched.", "Delete", () =>
                    {
                        s.CustomApps.RemoveAll(x => x.Id == a.Id);
                        s.Home.Remove(a.Id);
                        Settings.Save();
                        Host.Refresh();
                    })));
            }
            Host.ShowMenu(s.NameOf(a), a.KindLabel, opts);
        }

        void Move(string id, int by)
        {
            var ids = Settings.Current.Home;
            int i = ids.IndexOf(id), j = i + by;
            if (i < 0 || j < 0 || j >= ids.Count) return;
            ids[i] = ids[j];
            ids[j] = id;
            Settings.Save();
            Host.Refresh();
        }

        public override async void Shown()
        {
            if (Settings.Current.Known.Count == 0)
            {
                await Catalog.ScanAsync();
                if (Host.Current == this) Host.Refresh();
            }
        }
    }
}
