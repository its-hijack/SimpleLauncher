using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;

namespace SimpleLauncherHTPC
{
    /// <summary>The background: a colour, your Windows wallpaper or your own photos.</summary>
    public class BackgroundView : View
    {
        bool photos = Settings.Current.BgMode != "colour";
        const double W = 268, H = 151;

        public override void Build()
        {
            var s = Settings.Current;
            var page = new DockPanel { Margin = new Thickness(56, 36, 56, 30) };
            Children.Add(page);

            var head = new Grid();
            head.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            head.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var left = new StackPanel();
            left.Children.Add(Ui.T("Background", 30, Colors.White, FontWeights.Medium));
            var tabs = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 14, 0, 0) };
            var t1 = Ui.Pill("Colours", () => { photos = false; Host.Refresh(); }, !photos);
            t1.Tag = "tab:colours";
            var t2 = Ui.Pill("Photos", () => { photos = true; Host.Refresh(); }, photos);
            t2.Tag = "tab:photos";
            t2.Margin = new Thickness(8, 0, 0, 0);
            tabs.Children.Add(t1);
            tabs.Children.Add(t2);
            left.Children.Add(tabs);
            head.Children.Add(left);
            var hint = Ui.T("Select to apply · Esc or B to go back", 14, Ui.Dim);
            hint.VerticalAlignment = VerticalAlignment.Bottom;
            Grid.SetColumn(hint, 1);
            head.Children.Add(hint);
            DockPanel.SetDock(head, Dock.Top);
            page.Children.Add(head);

            var body = new StackPanel { Margin = new Thickness(0, 20, 0, 0) };
            var wrap = new WrapPanel();
            if (!photos)
            {
                for (int i = 0; i < Ui.BgColors.Length; i++)
                {
                    int idx = i;
                    bool on = s.BgMode == "colour" && s.BgColour == i;
                    wrap.Children.Add(Card(Ui.BgNames[i], null, Ui.B(Ui.C(Ui.BgColors[i])), on, () =>
                    {
                        s.BgMode = "colour";
                        s.BgColour = idx;
                        Apply();
                    }, null, "colour:" + i));
                }
            }
            else
            {
                wrap.Children.Add(AddCard());
                string wp = WindowsIntegration.WallpaperPath();
                if (wp != null)
                {
                    wrap.Children.Add(Card("Your Windows wallpaper", "Same as your desktop", Ui.ArtBrush("wp", wp, 600), s.BgMode == "wallpaper", () =>
                    {
                        s.BgMode = "wallpaper";
                        Apply();
                    }, null, "wallpaper", true));
                }
                foreach (string ph in s.Photos.Where(File.Exists).ToList())
                {
                    string path = ph;
                    wrap.Children.Add(Card(Path.GetFileNameWithoutExtension(path), null, Ui.ArtBrush(path, path, 600), s.BgMode == "photo" && s.BgPhoto == path, () =>
                    {
                        s.BgMode = "photo";
                        s.BgPhoto = path;
                        Apply();
                    }, () => Host.ShowMenu("This photo", null, new List<(string, Action)>
                    {
                        ("Remove it from SimpleLauncher", () =>
                        {
                            s.Photos.Remove(path);
                            if (s.BgPhoto == path) s.BgMode = "colour";
                            try { File.Delete(path); } catch { }
                            Apply();
                        }),
                        ("Cancel", null)
                    }), "photo:" + path, true));
                }
            }
            body.Children.Add(wrap);
            if (photos)
            {
                body.Children.Add(Ui.Choice("Darken the photo", "Keeps the clock and app names easy to read", Ui.DimNames, s.Dim, v =>
                {
                    s.Dim = v;
                    Settings.Save();
                    Host.ApplyBackground();
                }));
                body.Children.Add(Ui.Choice("Fit", "For photos that aren't the same shape as your screen", Ui.FitNames, s.Fit, v =>
                {
                    s.Fit = v;
                    Settings.Save();
                    Host.ApplyBackground();
                }));
            }
            page.Children.Add(Ui.Scroll(body));
        }

        void Apply()
        {
            Settings.Save();
            Host.ApplyBackground();
            Host.Refresh();
        }

        FrameworkElement Card(string name, string sub, Brush fill, bool current, Action pick, Action menu, string tag, bool photo = false)
        {
            var inner = new Grid { ClipToBounds = true };
            if (photo) inner.Children.Add(new Border { Background = Brushes.Black, Opacity = Ui.DimLevels[Ui.Clamp(Settings.Current.Dim, 4)], CornerRadius = new CornerRadius(7) });
            inner.Children.Add(Ui.MiniHome(W - 6));
            if (current)
            {
                inner.Children.Add(new Border
                {
                    Width = 24, Height = 24, CornerRadius = new CornerRadius(12), Background = Brushes.White,
                    HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(8),
                    Child = Ui.Icon(Ui.TickIcon, 12, Ui.Bg, 3)
                });
            }
            var c = new Clickable
            {
                Width = W, Height = H, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(3), Lift = true,
                NormalBackground = fill, NormalBorder = Ui.B(current ? Colors.White : Ui.A(0xFFFFFF, 0.10)), FocusBorder = Ui.B(Colors.White),
                Child = inner, Tag = tag
            };
            c.Background = fill;
            c.Clicked += pick;
            if (menu != null) c.MenuRequested += menu;
            var sp = new StackPanel { Width = W, Margin = new Thickness(0, 0, 24, 18) };
            sp.Children.Add(c);
            var t = Ui.T(name, 15, current ? Colors.White : Ui.Dim, FontWeights.Medium);
            t.Margin = new Thickness(2, 8, 0, 0);
            sp.Children.Add(t);
            if (sub != null) sp.Children.Add(Ui.T(sub, 12, Ui.Label));
            return sp;
        }

        FrameworkElement AddCard()
        {
            var g = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            var icon = Ui.Icon(Ui.FolderIcon, 30, Ui.Dim);
            icon.HorizontalAlignment = HorizontalAlignment.Center;
            g.Children.Add(icon);
            var t = Ui.T("Browse this PC…", 13, Ui.Dim);
            t.Margin = new Thickness(0, 8, 0, 0);
            g.Children.Add(t);
            var c = new Clickable
            {
                Width = W, Height = H, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(2), Lift = true,
                NormalBackground = Ui.B(Ui.C(0x11141B)), NormalBorder = Ui.B(Ui.Track), FocusBorder = Ui.B(Colors.White), Child = g, Tag = "add"
            };
            c.Background = c.NormalBackground;
            c.Clicked += AddPhoto;
            var sp = new StackPanel { Width = W, Margin = new Thickness(0, 0, 24, 18) };
            sp.Children.Add(c);
            var n = Ui.T("Add a photo", 15, Ui.Text, FontWeights.Medium);
            n.Margin = new Thickness(2, 8, 0, 0);
            sp.Children.Add(n);
            sp.Children.Add(Ui.T("Opens the Windows file picker", 12, Ui.Label));
            return sp;
        }

        void AddPhoto()
        {
            var dlg = new OpenFileDialog
            {
                Title = "Choose a photo for your background",
                Filter = "Photos|*.jpg;*.jpeg;*.png;*.bmp;*.gif;*.tif;*.tiff|All files|*.*",
                InitialDirectory = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures)
            };
            if (dlg.ShowDialog(Host) != true) return;
            try
            {
                var s = Settings.Current;
                string copy = Path.Combine(Settings.BackgroundFolder, Net.Hash(dlg.FileName + DateTime.Now.Ticks) + Path.GetExtension(dlg.FileName));
                File.Copy(dlg.FileName, copy, true);
                s.Photos.Add(copy);
                s.BgMode = "photo";
                s.BgPhoto = copy;
                Apply();
            }
            catch
            {
                Host.Toast("Couldn't use that photo");
            }
        }
    }
}
