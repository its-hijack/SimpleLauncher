using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Microsoft.Win32;

namespace SimpleLauncherHTPC
{
    /// <summary>First-time setup: Welcome, Your apps, Background, Layout clock and date, All set.</summary>
    public class SetupView : View
    {
        const int Steps = 5;
        int step;
        bool introPlayed;
        bool pictures = Settings.Current.BgMode != "colour";
        List<string> leftOrder;

        public override bool UsesPhoto => false;

        public override void Build()
        {
            switch (step)
            {
                case 0: Welcome(); break;
                case 1: Apps(); break;
                case 2: Background(); break;
                case 3: LayoutClock(); break;
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
            GoTo(step - 1);
            return true;
        }

        void GoTo(int s)
        {
            step = s;
            if (s == 1) Catalog.PickSuggested();
            Host.Refresh(s == 1 ? FirstLeftTag() : "next");
        }

        string FirstLeftTag()
        {
            var first = LeftApps().FirstOrDefault();
            return first != null ? "left:" + first.Id : "next";
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
            prog.Children.Add(Ui.T("Step " + (step + 1) + " of " + Steps, 13, Ui.Dim));
            for (int i = 0; i < Steps; i++)
            {
                prog.Children.Add(new Border { Width = 30, Height = 4, CornerRadius = new CornerRadius(2), Margin = new Thickness(i == 0 ? 14 : 6, 0, 0, 0), Background = Ui.B(i <= step ? Ui.Accent : Ui.C(0x2A3040)) });
            }
            Grid.SetColumn(prog, 1);
            top.Children.Add(prog);
            DockPanel.SetDock(top, Dock.Top);
            page.Children.Add(top);

            var heading = new StackPanel { Margin = new Thickness(0, 22, 0, 16) };
            heading.Children.Add(Ui.T(title, 30, Colors.White, FontWeights.Medium));
            var s = Ui.Wrap(sub, 15, Ui.Dim);
            s.Margin = new Thickness(0, 6, 0, 0);
            heading.Children.Add(s);
            DockPanel.SetDock(heading, Dock.Top);
            page.Children.Add(heading);
            Children.Add(page);
            return page;
        }

        void Buttons(DockPanel page, string next, Action onNext)
        {
            var g = new Grid { Margin = new Thickness(0, 16, 0, 0) };
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var b = Ui.Pill("Back", () => GoTo(step - 1));
            b.Tag = "back";
            b.HorizontalAlignment = HorizontalAlignment.Left;
            g.Children.Add(b);
            var n = Ui.Pill(next, onNext, true);
            n.Tag = "next";
            Grid.SetColumn(n, 1);
            g.Children.Add(n);
            DockPanel.SetDock(g, Dock.Bottom);
            page.Children.Add(g);
        }

        public override IInputElement FirstFocus => MainWindow.FindTag(this, step == 1 ? FirstLeftTag() : "next");

        // ------------------------------------------------------------------ 1 · Welcome

        void Welcome()
        {
            Children.Add(new Border
            {
                Background = new RadialGradientBrush(Ui.A(0x2F8CFF, 0.22), Ui.A(0x0C0F15, 0)) { RadiusX = 0.6, RadiusY = 0.55, Center = new Point(0.5, 0.46), GradientOrigin = new Point(0.5, 0.46) }
            });
            var col = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 0, 40) };
            var logo = Ui.Logo(120);
            var title = Ui.T("Welcome to SimpleLauncher", 46, Colors.White, FontWeights.Medium);
            title.HorizontalAlignment = HorizontalAlignment.Center;
            title.Margin = new Thickness(0, 24, 0, 0);
            var maker = Ui.T("A PRODUCT OF CHERRY UNIVERSE", 14, Ui.Label);
            maker.HorizontalAlignment = HorizontalAlignment.Center;
            maker.Margin = new Thickness(0, 12, 0, 0);
            var sub = Ui.Wrap("A simple, full-screen home for your PC. Use a mouse, keyboard, controller or remote.", 18, Ui.Dim);
            sub.TextAlignment = TextAlignment.Center;
            sub.MaxWidth = 640;
            sub.Margin = new Thickness(0, 14, 0, 0);
            col.Children.Add(logo);
            col.Children.Add(title);
            col.Children.Add(maker);
            col.Children.Add(sub);
            Children.Add(col);

            var start = Ui.Pill("Get started", () => GoTo(1), true, 17);
            start.Tag = "next";
            start.Padding = new Thickness(40, 14, 40, 15);
            start.CornerRadius = new CornerRadius(26);
            var bottom = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 48) };
            bottom.Children.Add(start);
            var press = Ui.T("Press Enter, A on a controller, or click", 12, Ui.Label);
            press.HorizontalAlignment = HorizontalAlignment.Center;
            press.Margin = new Thickness(0, 12, 0, 0);
            bottom.Children.Add(press);
            Children.Add(bottom);

            if (introPlayed) return;
            introPlayed = true;
            int i = 0;
            foreach (UIElement e in new UIElement[] { logo, title, maker, sub, bottom })
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

        // ------------------------------------------------------------------ 2 · Your apps

        /// <summary>Every app on the PC in a fixed order, so nothing moves while you pick.</summary>
        List<AppEntry> LeftApps()
        {
            var all = Settings.Current.Known.ToList();
            if (leftOrder == null || leftOrder.Count == 0)
            {
                leftOrder = all.OrderBy(a => Catalog.IsSuggested(a) ? 0 : 1).ThenBy(a => a.Name, StringComparer.CurrentCultureIgnoreCase).Select(a => a.Id).ToList();
            }
            foreach (var a in all) if (!leftOrder.Contains(a.Id)) leftOrder.Add(a.Id);
            var byId = all.GroupBy(a => a.Id).ToDictionary(g => g.Key, g => g.First());
            return leftOrder.Where(byId.ContainsKey).Select(id => byId[id]).ToList();
        }

        void Apps()
        {
            var page = Frame("Your apps", "Select apps on the left to add them to your home screen. They line up on the right in the order you choose them.");
            Buttons(page, "Next", () => GoTo(2));
            var s = Settings.Current;

            var cols = new Grid();
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(29) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(470) });
            page.Children.Add(cols);

            // Left: everything on the PC
            var left = new DockPanel();
            var lt = Ui.Section("On this PC", 0);
            DockPanel.SetDock(lt, Dock.Top);
            left.Children.Add(lt);
            var wrap = new WrapPanel();
            var apps = LeftApps();
            if (apps.Count == 0) wrap.Children.Add(Ui.T(Catalog.Scanning ? "Looking for the apps on this PC…" : "No apps found. You can add your own after setup.", 15, Ui.Dim));
            for (int i = 0; i < apps.Count; i++)
            {
                var app = apps[i];
                string nextTag = i + 1 < apps.Count ? "left:" + apps[i + 1].Id : "next";
                int pos = s.Home.IndexOf(app.Id);
                var t = Ui.Tile(app, 140, 79, () =>
                {
                    if (s.Home.Contains(app.Id)) s.Home.Remove(app.Id);
                    else s.Home.Add(app.Id);
                    Settings.Save();
                    Host.Refresh(nextTag);
                }, null, pos >= 0 ? (pos + 1).ToString() : null, null, pos < 0);
                Ui.TileOf(t).Tag = "left:" + app.Id;
                t.Margin = new Thickness(0, 0, 16, 10);
                wrap.Children.Add(t);
            }
            left.Children.Add(Ui.Scroll(wrap));
            cols.Children.Add(left);

            var line = new Border { Width = 1, Background = Ui.B(Ui.Line), HorizontalAlignment = HorizontalAlignment.Center };
            Grid.SetColumn(line, 1);
            cols.Children.Add(line);

            // Right: what's on the home screen, in order
            var right = new DockPanel();
            var head = new Grid();
            head.Children.Add(Ui.Section("On your home screen", 0));
            var count = Ui.T(s.Home.Count + (s.Home.Count == 1 ? " app" : " apps"), 12, Ui.Label);
            count.HorizontalAlignment = HorizontalAlignment.Right;
            head.Children.Add(count);
            DockPanel.SetDock(head, Dock.Top);
            right.Children.Add(head);
            var note = Ui.Wrap("Press Menu or right-click an app here to move it or take it off.", 12, Ui.Label);
            note.Margin = new Thickness(2, 8, 0, 0);
            DockPanel.SetDock(note, Dock.Bottom);
            right.Children.Add(note);

            var chosenWrap = new WrapPanel { Margin = new Thickness(14) };
            var chosen = Catalog.HomeApps();
            for (int i = 0; i < Math.Max(8, chosen.Count); i++)
            {
                if (i < chosen.Count)
                {
                    var app = chosen[i];
                    var t = Ui.Tile(app, 98, 55, null, () => ChosenMenu(app), (i + 1).ToString());
                    Ui.TileOf(t).Tag = "right:" + app.Id;
                    t.Margin = new Thickness(0, 0, 12, 8);
                    chosenWrap.Children.Add(t);
                }
                else
                {
                    chosenWrap.Children.Add(new Border
                    {
                        Width = 98, Height = 55, CornerRadius = new CornerRadius(7), BorderThickness = new Thickness(2),
                        BorderBrush = Ui.B(Ui.C(0x2A3040)), Margin = new Thickness(0, 0, 12, 30)
                    });
                }
            }
            var box = new Border { Background = Ui.B(Ui.C(0x11141B)), CornerRadius = new CornerRadius(12), Child = Ui.Scroll(chosenWrap) };
            right.Children.Add(box);
            Grid.SetColumn(right, 2);
            cols.Children.Add(right);
        }

        void ChosenMenu(AppEntry a)
        {
            var ids = Settings.Current.Home;
            int i = ids.IndexOf(a.Id);
            var opts = new List<(string, Action)>();
            if (i > 0) opts.Add(("Move earlier", () => Swap(i, i - 1)));
            if (i >= 0 && i < ids.Count - 1) opts.Add(("Move later", () => Swap(i, i + 1)));
            opts.Add(("Take it off the home screen", () =>
            {
                ids.Remove(a.Id);
                Settings.Save();
                Host.Refresh();
            }));
            Host.ShowMenu(Settings.Current.NameOf(a), null, opts);
        }

        void Swap(int i, int j)
        {
            var ids = Settings.Current.Home;
            (ids[i], ids[j]) = (ids[j], ids[i]);
            Settings.Save();
            Host.Refresh();
        }

        // ------------------------------------------------------------------ 3 · Background

        void Background()
        {
            var page = Frame("Background", "Choose a colour theme, or use a picture: your Windows wallpaper or one of your photos.");
            Buttons(page, "Next", () => GoTo(3));
            var s = Settings.Current;
            var body = new StackPanel();

            var modes = new Grid { Margin = new Thickness(0, 0, 0, 16) };
            modes.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            modes.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(16) });
            modes.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            modes.Children.Add(ModeCard("Colour theme", "One of the seven colours from the TV",
                new LinearGradientBrush(new GradientStopCollection { new GradientStop(Ui.C(0x0B1426), 0), new GradientStop(Ui.C(0x160F24), 0.5), new GradientStop(Ui.C(0x0D1A14), 1) }, 45),
                !pictures, "mode:colour", () =>
                {
                    pictures = false;
                    s.BgMode = "colour";
                }));
            var pic = ModeCard("Wallpaper or photo", "Your Windows wallpaper, or a photo from this PC",
                new LinearGradientBrush(Ui.C(0x3D5A80), Ui.C(0xC99B6D), 45), pictures, "mode:photo", () =>
                {
                    pictures = true;
                    if (s.BgMode == "colour") s.BgMode = WindowsIntegration.WallpaperPath() != null ? "wallpaper" : s.BgPhoto != null ? "photo" : "colour";
                });
            Grid.SetColumn(pic, 2);
            modes.Children.Add(pic);
            body.Children.Add(modes);

            const double w = 205, h = 115;
            var grid = new WrapPanel();
            if (!pictures)
            {
                body.Children.Add(Ui.Section("Colours · " + Ui.BgNames[Ui.Clamp(s.BgColour, Ui.BgNames.Length)], 0));
                for (int i = 0; i < Ui.BgColors.Length; i++)
                {
                    int idx = i;
                    grid.Children.Add(Picture(Ui.BgNames[i], Ui.B(Ui.C(Ui.BgColors[i])), s.BgMode == "colour" && s.BgColour == i, w, h, "colour:" + i, false, () =>
                    {
                        s.BgMode = "colour";
                        s.BgColour = idx;
                    }));
                }
            }
            else
            {
                body.Children.Add(Ui.Section("Pictures · darkened a little so the clock stays easy to read", 0));
                grid.Children.Add(Picture("Browse this PC…", Ui.B(Ui.C(0x11141B)), false, w, h, "browse", false, BrowsePhoto, true));
                string wp = WindowsIntegration.WallpaperPath();
                if (wp != null)
                {
                    grid.Children.Add(Picture("Your Windows wallpaper", Ui.ArtBrush("wp", wp, 420), s.BgMode == "wallpaper", w, h, "wallpaper", true, () => s.BgMode = "wallpaper"));
                }
                foreach (string ph in s.Photos.Where(File.Exists).ToList())
                {
                    string path = ph;
                    grid.Children.Add(Picture("Your photo", Ui.ArtBrush(path, path, 420), s.BgMode == "photo" && s.BgPhoto == path, w, h, "photo:" + path, true, () =>
                    {
                        s.BgMode = "photo";
                        s.BgPhoto = path;
                    }));
                }
            }
            body.Children.Add(grid);
            page.Children.Add(Ui.Scroll(body));
        }

        Clickable ModeCard(string name, string sub, Brush swatch, bool on, string tag, Action pick)
        {
            var g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.Children.Add(new Border { Width = 46, Height = 46, CornerRadius = new CornerRadius(10), Background = swatch });
            var texts = new StackPanel { Margin = new Thickness(16, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            texts.Children.Add(Ui.T(name, 17, Colors.White, FontWeights.SemiBold));
            var st = Ui.T(sub, 13, Ui.Dim);
            st.Margin = new Thickness(0, 3, 0, 0);
            texts.Children.Add(st);
            Grid.SetColumn(texts, 1);
            g.Children.Add(texts);
            if (on)
            {
                var tick = new Border
                {
                    Width = 26, Height = 26, CornerRadius = new CornerRadius(13), Background = Ui.B(Ui.Accent), VerticalAlignment = VerticalAlignment.Center,
                    Child = Ui.Icon(Ui.TickIcon, 14, Colors.White, 3)
                };
                Grid.SetColumn(tick, 2);
                g.Children.Add(tick);
            }
            var c = new Clickable
            {
                Child = g, Padding = new Thickness(18, 14, 18, 14), CornerRadius = new CornerRadius(12), BorderThickness = new Thickness(3), Lift = true,
                NormalBackground = Ui.B(on ? Ui.Selected : Ui.Panel), NormalBorder = Ui.B(on ? Colors.White : Ui.Line), FocusBorder = Ui.B(Colors.White), Tag = tag
            };
            c.Background = c.NormalBackground;
            c.Clicked += () =>
            {
                pick();
                Settings.Save();
                Host.ApplyBackground();
                Host.Refresh();
            };
            return c;
        }

        FrameworkElement Picture(string label, Brush fill, bool on, double w, double h, string tag, bool photo, Action pick, bool browse = false)
        {
            var inner = new Grid { ClipToBounds = true };
            if (browse)
            {
                var sp = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
                var icon = Ui.Icon(Ui.FolderIcon, 28, Ui.Dim);
                icon.HorizontalAlignment = HorizontalAlignment.Center;
                sp.Children.Add(icon);
                var t = Ui.T("Browse this PC…", 13, Ui.Dim);
                t.Margin = new Thickness(0, 6, 0, 0);
                sp.Children.Add(t);
                inner.Children.Add(sp);
            }
            else
            {
                if (photo) inner.Children.Add(new Border { Background = Brushes.Black, Opacity = 0.45 });
                inner.Children.Add(Ui.MiniHome(w - 6));
            }
            var c = new Clickable
            {
                Width = w, Height = h, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(3), Lift = true,
                NormalBackground = fill, NormalBorder = Ui.B(on ? Colors.White : browse ? Ui.Track : Ui.A(0xFFFFFF, 0.10)), FocusBorder = Ui.B(Colors.White),
                Child = inner, Tag = tag
            };
            c.Background = fill;
            c.Clicked += () =>
            {
                pick();
                Settings.Save();
                Host.ApplyBackground();
                Host.Refresh();
            };
            var l = Ui.T(label, 14, on ? Colors.White : Ui.Dim, FontWeights.Medium);
            l.Margin = new Thickness(2, 7, 0, 0);
            l.Width = w;
            var outer = new StackPanel { Width = w, Margin = new Thickness(0, 6, 18, 8) };
            outer.Children.Add(c);
            outer.Children.Add(l);
            return outer;
        }

        void BrowsePhoto()
        {
            var s = Settings.Current;
            var dlg = new OpenFileDialog { Title = "Choose a photo for your background", Filter = "Photos|*.jpg;*.jpeg;*.png;*.bmp|All files|*.*", InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures) };
            if (dlg.ShowDialog(Host) != true) return;
            try
            {
                string copy = Path.Combine(Settings.BackgroundFolder, Net.Hash(dlg.FileName + DateTime.Now.Ticks) + Path.GetExtension(dlg.FileName));
                File.Copy(dlg.FileName, copy, true);
                s.Photos.Add(copy);
                s.BgPhoto = copy;
                s.BgMode = "photo";
            }
            catch
            {
                Host.Toast("Couldn't use that photo");
            }
        }

        // ------------------------------------------------------------------ 4 · Layout, clock and date

        void LayoutClock()
        {
            var page = Frame("Layout, clock and date", "How your home screen is laid out, and how the time and date look at the top.");
            Buttons(page, "Next", () => GoTo(4));
            var s = Settings.Current;
            var cols = new Grid();
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(560) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(30) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            var left = new StackPanel();
            left.Children.Add(Ui.Section("Layout", 0));
            left.Children.Add(SettingsView.LayoutPictures(this));
            var size = Ui.Steps("App size", s.AppSize, v => { s.AppSize = v; Settings.Save(); });
            size.Tag = "size";
            left.Children.Add(size);
            var accent = Ui.Choice("Accent colour", null, Ui.AccentNames, s.Accent, v => { s.Accent = v; Settings.Save(); Host.Refresh(); });
            accent.Tag = "accent";
            left.Children.Add(accent);
            cols.Children.Add(Ui.Scroll(left));

            var right = new StackPanel();
            right.Children.Add(Ui.Section("Clock and date", 0));
            var now = DateTime.Now;
            var preview = new Border { Background = Ui.B(Ui.C(0x0A0C11)), BorderBrush = Ui.B(Ui.Line), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(10), Padding = new Thickness(18, 14, 18, 14), Margin = new Thickness(0, 0, 0, 10) };
            var pr = new StackPanel { Orientation = Orientation.Horizontal };
            pr.Children.Add(Ui.Logo(34));
            var times = new StackPanel { Margin = new Thickness(14, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            var tr = new StackPanel { Orientation = Orientation.Horizontal };
            tr.Children.Add(Ui.T(Ui.TimeText(now), 30, Colors.White, FontWeights.Light));
            string ap = Ui.AmPmText(now);
            if (ap.Length > 0)
            {
                var a = Ui.T(ap, 13, Colors.White, FontWeights.Light);
                a.Margin = new Thickness(5, 0, 0, 4);
                a.VerticalAlignment = VerticalAlignment.Bottom;
                tr.Children.Add(a);
            }
            times.Children.Add(tr);
            times.Children.Add(Ui.T(Ui.DateText(now) ?? "Date hidden", 13, Ui.Dim));
            pr.Children.Add(times);
            preview.Child = pr;
            right.Children.Add(preview);
            SettingsView.ClockRows(right, this);
            var rs = Ui.Scroll(right);
            Grid.SetColumn(rs, 2);
            cols.Children.Add(rs);
            page.Children.Add(cols);
        }

        // ------------------------------------------------------------------ 5 · All set

        void Done()
        {
            var page = Frame("All set", "Here's how SimpleLauncher is set up. You can change any of it in Settings.");
            Buttons(page, "Go to my home screen", () =>
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
            var now = DateTime.Now;
            string clock = Ui.TimeText(now) + (Ui.AmPmText(now).Length > 0 ? " " + Ui.AmPmText(now) : "") + " · " + (Ui.DateText(now) ?? "no date");
            foreach (var (k, v) in new[]
            {
                ("Your apps", s.Home.Count + (s.Home.Count == 1 ? " app" : " apps") + " on your home screen"),
                ("Look", (s.Grid ? "Grid" : "Default") + " · " + bg + " · " + Ui.AccentNames[Ui.Clamp(s.Accent, 5)]),
                ("Clock", clock),
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
