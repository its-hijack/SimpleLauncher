using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace SimpleLauncherHTPC
{
    /// <summary>The home screen: clock, buttons, your apps and (if you turn it on) Continue Watching.</summary>
    public class HomeView : View
    {
        static readonly CultureInfo UK = new CultureInfo("en-GB");
        static readonly int[] GridTile = { 120, 144, 168, 196, 228 };
        static readonly int[] RowTile = { 150, 180, 208, 244, 280 };
        static readonly int[] GridCard = { 160, 188, 214, 250, 290 };
        static readonly int[] RowCard = { 200, 230, 260, 300, 340 };

        TextBlock clock, ampm, date;
        DispatcherTimer tick;

        public override bool UsesPhoto => true;

        public override void Build()
        {
            var s = Settings.Current;
            var page = new DockPanel { Margin = new Thickness(56, 44, 56, 24), LastChildFill = true };
            Children.Add(page);

            // Header: logo, clock and the buttons
            var header = new Grid();
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var left = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            left.Children.Add(Ui.Logo(56));
            var times = new StackPanel { Margin = new Thickness(22, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            clock = Ui.T("", 44, Colors.White, FontWeights.Light);
            ampm = Ui.T("", 18, Colors.White, FontWeights.Light);
            ampm.Margin = new Thickness(6, 0, 0, 5);
            ampm.VerticalAlignment = VerticalAlignment.Bottom;
            date = Ui.T("", 16, Ui.C(0xC9CFDA));
            var timeRow = new StackPanel { Orientation = Orientation.Horizontal };
            timeRow.Children.Add(clock);
            timeRow.Children.Add(ampm);
            times.Children.Add(timeRow);
            times.Children.Add(date);
            left.Children.Add(times);
            header.Children.Add(left);
            UpdateClock();

            var buttons = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            AddButton(buttons, Ui.Pill("Desktop", () => Host.ShowDesktop()), "btn:desktop");
            AddButton(buttons, Ui.Pill("App Selection", () => Host.Go(new AppSelectionView())), "btn:apps");
            AddButton(buttons, Ui.Pill("Settings", () => Host.Go(new SettingsView())), "btn:settings");
            if (s.PowerSleep || s.PowerRestart || s.PowerShutDown)
            {
                AddButton(buttons, Ui.IconPill(Ui.PowerIcon, "Power", PowerMenu), "btn:power");
            }
            Grid.SetColumn(buttons, 1);
            header.Children.Add(buttons);
            DockPanel.SetDock(header, Dock.Top);
            page.Children.Add(header);

            var hint = Ui.T("Ctrl + Alt + Home, or holding View + Menu on a controller, brings you back here from anywhere", 12, Ui.A(0xFFFFFF, 0.55));
            hint.HorizontalAlignment = HorizontalAlignment.Right;
            hint.Margin = new Thickness(0, 10, 0, 0);
            DockPanel.SetDock(hint, Dock.Bottom);
            page.Children.Add(hint);

            // Body
            var body = new StackPanel { Margin = new Thickness(0, 34, 0, 0) };
            var apps = Catalog.HomeApps();
            int size = Ui.Clamp(s.AppSize, 5);
            var watch = s.ContinueWatching ? WatchingSection(s.Grid) : null;
            if (s.Grid)
            {
                if (watch != null) body.Children.Add(watch);
                body.Children.Add(AppsSection(apps, true, size, watch != null));
            }
            else
            {
                body.Children.Add(AppsSection(apps, false, size, false));
                if (watch != null)
                {
                    watch.Margin = new Thickness(0, 26, 0, 0);
                    body.Children.Add(watch);
                }
            }
            page.Children.Add(Ui.Scroll(body));
        }

        static void AddButton(Panel row, Clickable b, string tag)
        {
            b.Tag = tag;
            b.Margin = new Thickness(12, 0, 0, 0);
            row.Children.Add(b);
        }

        public override IInputElement FirstFocus
        {
            get
            {
                var first = Catalog.HomeApps().FirstOrDefault();
                return first != null ? MainWindow.FindTag(this, "app:" + first.Id) : null;
            }
        }

        // ------------------------------------------------------------------ apps

        FrameworkElement AppsSection(List<AppEntry> apps, bool grid, int size, bool afterWatching)
        {
            double w = grid ? GridTile[size] : RowTile[size];
            double h = Math.Round(w * 9 / 16);
            var sec = new StackPanel();
            int across = Math.Max(1, (int)Math.Floor((1168 + 32) / (w + 32) + 0.001));
            if (grid && apps.Count > 0)
            {
                // A centred block: as wide as the rows of tiles that fit, so the grid sits in the middle.
                across = Math.Min(across, apps.Count);
                sec.Width = across * w + (across - 1) * 32;
                sec.HorizontalAlignment = HorizontalAlignment.Center;
            }
            sec.Children.Add(Ui.Section("Your apps", afterWatching ? 26 : 0));
            if (apps.Count == 0)
            {
                var msg = Ui.T("Nothing here yet. Choose the apps you want on your home screen.", 16, Ui.C(0xC9CFDA));
                msg.Margin = new Thickness(2, 6, 0, 16);
                sec.Children.Add(msg);
                var pick = Ui.Pill("Choose your apps", () => Host.Go(new AppSelectionView()), true);
                pick.HorizontalAlignment = HorizontalAlignment.Left;
                pick.Tag = "empty:pick";
                sec.Children.Add(pick);
                return sec;
            }
            Panel holder = grid
                ? (Panel)new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) }
                : new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
            foreach (var a in apps)
            {
                var app = a;
                var t = Ui.Tile(app, w, h, () => Host.Launch(app), () => AppMenu(app));
                Ui.TileOf(t).Tag = "app:" + app.Id;
                t.Margin = grid ? new Thickness(0, 0, 32, 20) : new Thickness(0, 0, 24, 0);
                if (grid && (holder.Children.Count + 1) % across == 0) t.Margin = new Thickness(0, 0, 0, 20);
                holder.Children.Add(t);
            }
            sec.Children.Add(grid ? (UIElement)holder : Ui.Scroll(holder, true));
            return sec;
        }

        void AppMenu(AppEntry a)
        {
            var ids = Settings.Current.Home;
            int i = ids.IndexOf(a.Id);
            var opts = new List<(string, Action)>();
            bool grid = Settings.Current.Grid;
            if (i > 0) opts.Add((grid ? "Move earlier" : "Move left", () => MoveApp(a.Id, -1)));
            if (i >= 0 && i < ids.Count - 1) opts.Add((grid ? "Move later" : "Move right", () => MoveApp(a.Id, 1)));
            opts.Add(("Change its artwork or name", () => Host.Go(new AddAppView(a))));
            opts.Add(("Remove from the home screen", () =>
            {
                ids.Remove(a.Id);
                Settings.Save();
                Host.Refresh();
            }));
            Host.ShowMenu(Settings.Current.NameOf(a), null, opts);
        }

        void MoveApp(string id, int by)
        {
            var ids = Settings.Current.Home;
            int i = ids.IndexOf(id), j = i + by;
            if (i < 0 || j < 0 || j >= ids.Count) return;
            ids[i] = ids[j];
            ids[j] = id;
            Settings.Save();
            Host.Refresh();
        }

        void PowerMenu()
        {
            var s = Settings.Current;
            var opts = new List<(string, Action)>();
            if (s.PowerSleep) opts.Add(("Sleep", WindowsIntegration.Sleep));
            if (s.PowerRestart) opts.Add(("Restart", () => Host.Confirm("Restart the PC?", "Anything you haven't saved in other apps will be lost.", "Restart", WindowsIntegration.Restart)));
            if (s.PowerShutDown) opts.Add(("Shut down", () => Host.Confirm("Shut down the PC?", "Anything you haven't saved in other apps will be lost.", "Shut down", WindowsIntegration.ShutDown)));
            opts.Add(("Cancel", null));
            Host.ShowMenu("Power", null, opts);
        }

        // ------------------------------------------------------------------ Continue Watching

        FrameworkElement WatchingSection(bool grid)
        {
            var items = Watching.Items;
            var sec = new StackPanel();
            sec.Children.Add(Ui.Section(Watching.RowTitle(), 0));
            if (items.Count == 0)
            {
                var msg = Ui.T(Watching.Message ?? "Nothing to continue right now.", 14, Ui.C(0xC9CFDA));
                msg.Margin = new Thickness(2, 2, 0, 4);
                sec.Children.Add(msg);
                return sec;
            }
            int size = Ui.Clamp(Settings.Current.CardSize, 5);
            double w = grid ? GridCard[size] : RowCard[size];
            double h = Math.Round(w * 9 / 16);
            var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
            bool both = items.Select(i => i.Source).Distinct().Count() > 1;
            foreach (var item in items)
            {
                var it = item;
                var inner = new Grid();
                if (both)
                {
                    inner.Children.Add(new Border
                    {
                        Background = Ui.B(Ui.A(0xFFFFFF, 0.85)), CornerRadius = new CornerRadius(4), Padding = new Thickness(7, 3, 7, 3),
                        HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(8),
                        Child = Ui.T(it.Source.ToUpperInvariant(), 11, Ui.Bg, FontWeights.SemiBold)
                    });
                }
                var bar = new Grid { Height = 4, VerticalAlignment = VerticalAlignment.Bottom, Background = Ui.B(Ui.A(0xFFFFFF, 0.18)) };
                bar.Children.Add(new Border { Background = Ui.B(Ui.Accent), HorizontalAlignment = HorizontalAlignment.Left, Width = Math.Max(0, (w - 6) * it.Progress / 100.0) });
                inner.Children.Add(bar);
                var card = new Clickable
                {
                    Width = w, Height = h, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(3), Lift = true,
                    FocusBorder = Ui.B(Colors.White), Child = inner, Tag = "watch:" + it.Key, ClipToBounds = false
                };
                var bmp = it.Image != null ? Ui.Bitmap(it.Image, (int)(w * 2)) : null;
                card.NormalBackground = bmp != null ? (Brush)new ImageBrush(bmp) { Stretch = Stretch.UniformToFill } : new LinearGradientBrush(Ui.C(0x2B3550), Ui.C(0x151A26), 45);
                card.Background = card.NormalBackground;
                card.Clicked += () => Watching.Open(it, Host);

                var title = Ui.T(it.Title, 15, Ui.Text, FontWeights.Medium);
                title.Margin = new Thickness(2, 8, 0, 0);
                title.Width = w;
                var meta = Ui.T(it.Meta, 13, Ui.Dim);
                meta.Margin = new Thickness(2, 2, 0, 0);
                meta.Width = w;
                card.Highlighted = on => title.Foreground = Ui.B(on ? Colors.White : Ui.Text);
                var sp = new StackPanel { Width = w, Margin = new Thickness(0, 0, 18, 0) };
                sp.Children.Add(card);
                sp.Children.Add(title);
                sp.Children.Add(meta);
                row.Children.Add(sp);
            }
            sec.Children.Add(Ui.Scroll(row, true));
            return sec;
        }

        // ------------------------------------------------------------------ coming and going

        void UpdateClock()
        {
            var now = DateTime.Now;
            clock.Text = Ui.TimeText(now);
            ampm.Text = Ui.AmPmText(now);
            ampm.Visibility = ampm.Text.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
            string d = Ui.DateText(now);
            date.Text = d ?? "";
            date.Visibility = d != null ? Visibility.Visible : Visibility.Collapsed;
        }

        public override void Shown()
        {
            tick = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
            tick.Tick += (s, e) => UpdateClock();
            tick.Start();
            LoadExtras();
        }

        public override void Hidden()
        {
            tick?.Stop();
        }

        async void LoadExtras()
        {
            bool changed = false;
            if (Settings.Current.ContinueWatching && Watching.Stale)
            {
                await Watching.RefreshAsync();
                changed = true;
            }
            if (await Artwork.FillIn(Catalog.HomeApps()) > 0) changed = true;
            if (changed && Host?.Current == this && !Host.OverlayOpen) Host.Refresh();
        }
    }
}
