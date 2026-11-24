using System;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Microsoft.Win32;

namespace SimpleLauncherHTPC
{
    /// <summary>First-time setup: Welcome, Your apps, Make it yours, All set.</summary>
    public class SetupView : View
    {
        int step;
        bool introPlayed;

        public override bool UsesPhoto => step == 2;

        public override void Build()
        {
            switch (step)
            {
                case 0: Welcome(); break;
                case 1: Apps(); break;
                case 2: Look(); break;
                default: Done(); break;
            }
        }

        public override bool Back()
        {
            if (step == 0)
            {
                if (Settings.Current.SetupDone) Host.Replace(new HomeView());
                return true;
            }
            step--;
            Host.Refresh();
            return true;
        }

        void GoTo(int s)
        {
            step = s;
            if (s == 1) Catalog.PickSuggested();
            Host.Refresh();
            Host.FocusFirst();
        }

        DockPanel Frame(string title, string sub)
        {
            var page = new DockPanel { Margin = new Thickness(56, 36, 56, 36) };
            var top = new Grid();
            top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var brand = new StackPanel { Orientation = Orientation.Horizontal };
            brand.Children.Add(Ui.Logo(30));
            var name = Ui.T("SimpleLauncher setup", 15, Ui.Text, FontWeights.Medium);
            name.Margin = new Thickness(12, 0, 0, 0);
            name.VerticalAlignment = VerticalAlignment.Center;
            brand.Children.Add(name);
            top.Children.Add(brand);
            var prog = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            prog.Children.Add(Ui.T("Step " + (step + 1) + " of 4", 13, Ui.Dim));
            for (int i = 0; i < 4; i++)
            {
                prog.Children.Add(new Border { Width = 34, Height = 4, CornerRadius = new CornerRadius(2), Margin = new Thickness(i == 0 ? 14 : 6, 0, 0, 0), Background = Ui.B(i <= step ? Ui.Accent : Ui.C(0x2A3040)) });
            }
            Grid.SetColumn(prog, 1);
            top.Children.Add(prog);
            DockPanel.SetDock(top, Dock.Top);
            page.Children.Add(top);

            var heading = new StackPanel { Margin = new Thickness(0, 26, 0, 18) };
            heading.Children.Add(Ui.T(title, 32, Colors.White, FontWeights.Medium));
            var s = Ui.Wrap(sub, 16, Ui.Dim);
            s.Margin = new Thickness(0, 6, 0, 0);
            heading.Children.Add(s);
            DockPanel.SetDock(heading, Dock.Top);
            page.Children.Add(heading);
            Children.Add(page);
            return page;
        }

        void Buttons(DockPanel page, string back, Action onBack, string next, Action onNext)
        {
            var g = new Grid { Margin = new Thickness(0, 18, 0, 0) };
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            if (back != null)
            {
                var b = Ui.Pill(back, onBack);
                b.Tag = "back";
                b.HorizontalAlignment = HorizontalAlignment.Left;
                g.Children.Add(b);
            }
            var n = Ui.Pill(next, onNext, true);
            n.Tag = "next";
            Grid.SetColumn(n, 1);
            g.Children.Add(n);
            DockPanel.SetDock(g, Dock.Bottom);
            page.Children.Add(g);
        }

        public override IInputElement FirstFocus => step == 1 ? null : MainWindow.FindTag(this, "next");

        // ------------------------------------------------------------------ 1 · Welcome

        void Welcome()
        {
            var glow = new Border
            {
                Background = new RadialGradientBrush(Ui.A(0x2F8CFF, 0.22), Ui.A(0x0C0F15, 0)) { RadiusX = 0.6, RadiusY = 0.55, Center = new Point(0.5, 0.42), GradientOrigin = new Point(0.5, 0.42) }
            };
            Children.Add(glow);
            var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 96, 0, 0) };
            var logo = Ui.Logo(110);
            var title = Ui.T("Welcome to SimpleLauncher", 44, Colors.White, FontWeights.Medium);
            title.HorizontalAlignment = HorizontalAlignment.Center;
            title.Margin = new Thickness(0, 20, 0, 0);
            var maker = Ui.T("A PRODUCT OF CHERRY UNIVERSE", 14, Ui.Label);
            maker.HorizontalAlignment = HorizontalAlignment.Center;
            maker.Margin = new Thickness(0, 10, 0, 0);
            var sub = Ui.Wrap("A simple, full-screen home for your PC. Use a mouse, keyboard, controller or remote.", 18, Ui.Dim);
            sub.TextAlignment = TextAlignment.Center;
            sub.MaxWidth = 640;
            sub.Margin = new Thickness(0, 12, 0, 0);
            int count = Settings.Current.Known.Count;
            var card = new Border { Background = Ui.B(Ui.Panel), CornerRadius = new CornerRadius(10), Padding = new Thickness(18, 14, 18, 14), Width = 560, Margin = new Thickness(0, 30, 0, 0) };
            var cs = new StackPanel();
            cs.Children.Add(Ui.T(WindowsIntegration.WindowsName() + " PC detected", 15, Colors.White));
            cs.Children.Add(Ui.Wrap(count > 0 ? "SimpleLauncher found " + count + " apps in your Start menu. You'll pick which ones to show next." : "Looking for the apps on this PC…", 13, Ui.Dim));
            card.Child = cs;
            col.Children.Add(logo);
            col.Children.Add(title);
            col.Children.Add(maker);
            col.Children.Add(sub);
            col.Children.Add(card);
            Children.Add(col);

            var start = Ui.Pill("Get started", () => GoTo(1), true, 17);
            start.Tag = "next";
            start.Padding = new Thickness(40, 14, 40, 15);
            start.CornerRadius = new CornerRadius(26);
            var bottom = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 44) };
            bottom.Children.Add(start);
            var press = Ui.T("Press Enter, A on a controller, or click", 12, Ui.Label);
            press.HorizontalAlignment = HorizontalAlignment.Center;
            press.Margin = new Thickness(0, 12, 0, 0);
            bottom.Children.Add(press);
            Children.Add(bottom);

            if (!introPlayed)
            {
                introPlayed = true;
                int i = 0;
                foreach (UIElement e in new UIElement[] { logo, title, maker, sub, card, bottom })
                {
                    var tt = new TranslateTransform(0, 18);
                    e.RenderTransform = tt;
                    e.Opacity = 0;
                    var begin = TimeSpan.FromMilliseconds(700 + i * 140);
                    var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
                    e.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(520)) { BeginTime = begin, EasingFunction = ease });
                    tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(18, 0, TimeSpan.FromMilliseconds(620)) { BeginTime = begin, EasingFunction = ease });
                    i++;
                }
            }
        }

        // ------------------------------------------------------------------ 2 · Your apps

        void Apps()
        {
            var page = Frame("Your apps", "We've picked the ones that look like TV, music and games. Select any app to add or remove it. Artwork is fetched online once setup's done.");
            Buttons(page, "Back", () => GoTo(0), "Next", () => GoTo(2));
            var s = Settings.Current;
            var wrap = new WrapPanel();
            var apps = s.Known.OrderBy(a => s.Home.Contains(a.Id) ? 0 : 1).ThenBy(a => s.Home.IndexOf(a.Id)).ThenBy(a => a.Name).ToList();
            if (apps.Count == 0) wrap.Children.Add(Ui.T(Catalog.Scanning ? "Looking for the apps on this PC…" : "No apps found. You can add your own after setup.", 15, Ui.Dim));
            foreach (var a in apps)
            {
                var app = a;
                int pos = s.Home.IndexOf(app.Id);
                var t = Ui.Tile(app, 214, 112, () =>
                {
                    if (s.Home.Contains(app.Id)) s.Home.Remove(app.Id);
                    else s.Home.Add(app.Id);
                    Settings.Save();
                    Host.Refresh();
                }, null, pos >= 0 ? (pos + 1).ToString() : null, null, pos < 0);
                Ui.TileOf(t).Tag = "app:" + app.Id;
                t.Margin = new Thickness(0, 0, 24, 16);
                wrap.Children.Add(t);
            }
            var note = Ui.T("Something missing? You can add programs, games and websites yourself in App Selection after setup.", 13, Ui.Label);
            note.Margin = new Thickness(0, 10, 0, 0);
            DockPanel.SetDock(note, Dock.Bottom);
            page.Children.Add(note);
            page.Children.Add(Ui.Scroll(wrap));
        }

        // ------------------------------------------------------------------ 3 · Make it yours

        void Look()
        {
            var page = Frame("Make it yours", "Choose how your home screen looks. Everything can be changed later in Settings.");
            Buttons(page, "Back", () => GoTo(1), "Next", () => GoTo(3));
            var s = Settings.Current;
            var col = new StackPanel { Width = 640, HorizontalAlignment = HorizontalAlignment.Left };
            col.Children.Add(Ui.Section("Background", 0));
            var bgRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
            bgRow.Children.Add(BgChoice("Dark colour", Ui.B(Ui.Bg), s.BgMode == "colour", "bg:colour", () =>
            {
                s.BgMode = "colour";
                s.BgColour = 0;
            }));
            string wp = WindowsIntegration.WallpaperPath();
            if (wp != null)
            {
                bgRow.Children.Add(BgChoice("Windows wallpaper", Ui.ArtBrush("wp", wp, 400), s.BgMode == "wallpaper", "bg:wallpaper", () => s.BgMode = "wallpaper"));
            }
            bool hasPhoto = s.BgPhoto != null && File.Exists(s.BgPhoto);
            bgRow.Children.Add(BgChoice(hasPhoto ? "Your photo" : "Browse for a photo…", hasPhoto ? Ui.ArtBrush("ph", s.BgPhoto, 400) : Ui.B(Ui.C(0x11141B)), s.BgMode == "photo", "bg:photo", () =>
            {
                if (!hasPhoto || s.BgMode == "photo")
                {
                    var dlg = new OpenFileDialog { Title = "Choose a photo for your background", Filter = "Photos|*.jpg;*.jpeg;*.png;*.bmp|All files|*.*", InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures) };
                    if (dlg.ShowDialog(Host) != true) return;
                    string copy = Path.Combine(Settings.BackgroundFolder, Net.Hash(dlg.FileName + DateTime.Now.Ticks) + Path.GetExtension(dlg.FileName));
                    File.Copy(dlg.FileName, copy, true);
                    s.Photos.Add(copy);
                    s.BgPhoto = copy;
                }
                s.BgMode = "photo";
            }));
            col.Children.Add(bgRow);

            col.Children.Add(Ui.Section("Layout and colours", 10));
            var layout = Ui.Choice("Layout", null, new[] { "Default", "Grid" }, s.Grid ? 1 : 0, v => { s.Grid = v == 1; Settings.Save(); });
            layout.Tag = "layout";
            col.Children.Add(layout);
            var size = Ui.Steps("App size", s.AppSize, v => { s.AppSize = v; Settings.Save(); });
            size.Tag = "size";
            col.Children.Add(size);
            var accent = Ui.Choice("Accent colour", null, Ui.AccentNames, s.Accent, v => { s.Accent = v; Settings.Save(); Host.Refresh(); });
            accent.Tag = "accent";
            col.Children.Add(accent);
            var clock = Ui.Toggle("24-hour clock", null, s.Clock24, v => { s.Clock24 = v; Settings.Save(); });
            clock.Tag = "clock";
            col.Children.Add(clock);
            page.Children.Add(Ui.Scroll(col));
        }

        FrameworkElement BgChoice(string label, Brush fill, bool on, string tag, Action pick)
        {
            var c = new Clickable
            {
                Width = 190, Height = 107, CornerRadius = new CornerRadius(8), BorderThickness = new Thickness(3), Lift = true,
                NormalBackground = fill, NormalBorder = Ui.B(on ? Colors.White : Ui.Track), FocusBorder = Ui.B(Colors.White), Tag = tag
            };
            c.Background = fill;
            c.Clicked += () =>
            {
                pick();
                Settings.Save();
                Host.ApplyBackground();
                Host.Refresh();
            };
            var sp = new StackPanel { Margin = new Thickness(0, 0, 16, 0) };
            sp.Children.Add(c);
            var t = Ui.T(label, 13, on ? Colors.White : Ui.Dim);
            t.Margin = new Thickness(2, 7, 0, 0);
            sp.Children.Add(t);
            return sp;
        }

        // ------------------------------------------------------------------ 4 · All set

        void Done()
        {
            var page = Frame("All set", "Here's how SimpleLauncher is set up. You can change any of it in Settings.");
            Buttons(page, "Back", () => GoTo(2), "Go to my home screen", () =>
            {
                Settings.Current.SetupDone = true;
                Settings.Save();
                Host.Replace(new HomeView());
            });
            var s = Settings.Current;
            var col = new StackPanel { Width = 640, HorizontalAlignment = HorizontalAlignment.Left };
            var summary = new Border { Background = Ui.B(Ui.Panel), CornerRadius = new CornerRadius(12), Padding = new Thickness(18, 8, 18, 8) };
            var lines = new StackPanel();
            string bg = s.BgMode == "photo" ? "Your photo" : s.BgMode == "wallpaper" ? "Your Windows wallpaper" : Ui.BgNames[Ui.Clamp(s.BgColour, Ui.BgNames.Length)];
            foreach (var (k, v) in new[]
            {
                ("Your apps", s.Home.Count + (s.Home.Count == 1 ? " app" : " apps") + " on your home screen"),
                ("Look", (s.Grid ? "Grid" : "Default") + " · " + bg + " · " + Ui.AccentNames[Ui.Clamp(s.Accent, 5)]),
                ("Getting back", "Ctrl + Alt + Home, or hold View + Menu on a controller")
            })
            {
                var g = new Grid { Margin = new Thickness(0, 8, 0, 8) };
                g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(150) });
                g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                g.Children.Add(Ui.T(k, 14, Ui.Dim));
                var vt = Ui.T(v, 14, Colors.White);
                Grid.SetColumn(vt, 1);
                g.Children.Add(vt);
                lines.Children.Add(g);
            }
            summary.Child = lines;
            col.Children.Add(summary);
            col.Children.Add(Ui.Section("Starting up", 20));
            var start = Ui.Toggle("Start with Windows", "SimpleLauncher opens when you sign in. Also in Settings › Windows", WindowsIntegration.StartsWithWindows, v => WindowsIntegration.StartsWithWindows = v);
            start.Tag = "startup";
            col.Children.Add(start);
            page.Children.Add(col);
        }
    }
}
