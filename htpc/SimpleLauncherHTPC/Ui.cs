using System;
using System.Collections.Generic;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;

namespace SimpleLauncherHTPC
{
    /// <summary>
    /// Anything you can select: with a mouse (hovering highlights it), the keyboard, a controller or a remote.
    /// Tiles with Lift set float up and glow when highlighted.
    /// </summary>
    public class Clickable : Border
    {
        public event Action Clicked;
        public event Action MenuRequested;

        /// <summary>Left/right on a slider or choice row: -1 or +1. Return true if it used the press.</summary>
        public Func<int, bool> Adjust;
        public Action<bool> Highlighted;
        public bool Lift;
        public Brush NormalBackground = Brushes.Transparent, FocusBackground;
        public Brush NormalBorder = Brushes.Transparent, FocusBorder;

        readonly ScaleTransform scale = new ScaleTransform(1, 1);
        readonly TranslateTransform move = new TranslateTransform();
        bool fromMouse;

        public bool HasMenu => MenuRequested != null;

        public Clickable()
        {
            Focusable = true;
            FocusVisualStyle = null;
            Cursor = Cursors.Hand;
            Background = Brushes.Transparent;
            BorderBrush = Brushes.Transparent;
            SnapsToDevicePixels = true;
            var g = new TransformGroup();
            g.Children.Add(scale);
            g.Children.Add(move);
            RenderTransform = g;
            RenderTransformOrigin = new Point(0.5, 0.5);

            MouseEnter += (s, e) =>
            {
                if (!IsEnabled || IsKeyboardFocused) return;
                fromMouse = true;
                Focus();
                fromMouse = false;
            };
            MouseLeftButtonUp += (s, e) =>
            {
                e.Handled = true;
                Clicked?.Invoke();
            };
            MouseRightButtonUp += (s, e) =>
            {
                if (MenuRequested == null) return;
                e.Handled = true;
                MenuRequested();
            };
            KeyDown += (s, e) =>
            {
                if (e.Key == Key.Enter || e.Key == Key.Space)
                {
                    e.Handled = true;
                    Clicked?.Invoke();
                }
                else if (e.Key == Key.Apps && MenuRequested != null)
                {
                    e.Handled = true;
                    MenuRequested();
                }
            };
            GotKeyboardFocus += (s, e) =>
            {
                Show(true);
                if (!fromMouse) BringIntoView(new Rect(-40, -40, ActualWidth + 80, ActualHeight + 80));
            };
            LostKeyboardFocus += (s, e) => Show(false);
            Loaded += (s, e) => Show(IsKeyboardFocused);
        }

        public void Click() => Clicked?.Invoke();

        public void Menu() => MenuRequested?.Invoke();

        void Show(bool on)
        {
            Background = on && FocusBackground != null ? FocusBackground : NormalBackground;
            BorderBrush = on && FocusBorder != null ? FocusBorder : NormalBorder;
            Highlighted?.Invoke(on);
            if (!Lift) return;
            var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
            var t = TimeSpan.FromMilliseconds(170);
            scale.BeginAnimation(ScaleTransform.ScaleXProperty, new DoubleAnimation(on ? 1.07 : 1, t) { EasingFunction = ease });
            scale.BeginAnimation(ScaleTransform.ScaleYProperty, new DoubleAnimation(on ? 1.07 : 1, t) { EasingFunction = ease });
            move.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(on ? -6 : 0, t) { EasingFunction = ease });
            Effect = on ? new DropShadowEffect { Color = Ui.Accent, BlurRadius = 34, ShadowDepth = 0, Opacity = 0.85 } : null;
            Panel.SetZIndex(Parent as UIElement ?? this, on ? 10 : 0);
        }
    }

    /// <summary>Colours, text and the building blocks every screen is made from (the same look as SimpleLauncher on TV).</summary>
    static class Ui
    {
        public static readonly Color Bg = C(0x0C0F15), Panel = C(0x151922), Card = C(0x181C25), Selected = C(0x1E2430),
            Text = C(0xE6E9EF), Dim = C(0xA3ABBA), Label = C(0x8A93A6), Line = C(0x232836), Track = C(0x3A4150), Faint = C(0x5A6275);

        public static readonly string[] AccentNames = { "Blue", "Amber", "Purple", "Green", "White" };
        public static readonly uint[] AccentColors = { 0x2F8CFF, 0xE5A00D, 0x8B5CF6, 0x22C55E, 0xF2F4F7 };
        public static readonly string[] BgNames = { "Dark", "Midnight blue", "Charcoal", "Deep purple", "Forest green", "Slate", "Black" };
        public static readonly uint[] BgColors = { 0x0C0F15, 0x0B1426, 0x17191D, 0x160F24, 0x0D1A14, 0x1B2230, 0x000000 };
        public static readonly string[] SizeNames = { "Smallest", "Small", "Medium", "Large", "Largest" };
        public static readonly string[] DimNames = { "Off", "Light", "Medium", "Strong" };
        public static readonly double[] DimLevels = { 0, 0.25, 0.45, 0.65 };
        public static readonly string[] FitNames = { "Fill the screen", "Fit, blurred edges", "Fit, plain edges" };

        public static readonly FontFamily Font = new FontFamily("Segoe UI Variable Display, Segoe UI");

        public static Color C(uint rgb) => Color.FromRgb((byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);

        public static Color A(uint rgb, double alpha)
        {
            var c = C(rgb);
            c.A = (byte)Math.Round(alpha * 255);
            return c;
        }

        public static SolidColorBrush B(Color c)
        {
            var b = new SolidColorBrush(c);
            b.Freeze();
            return b;
        }

        public static Color Accent => C(AccentColors[Clamp(Settings.Current.Accent, AccentColors.Length)]);

        public static int Clamp(int v, int count) => Math.Max(0, Math.Min(count - 1, v));

        // ------------------------------------------------------------------ text

        public static TextBlock T(string s, double size, Color? color = null, FontWeight? weight = null)
        {
            return new TextBlock
            {
                Text = s ?? "",
                FontSize = size,
                FontFamily = Font,
                Foreground = B(color ?? Text),
                FontWeight = weight ?? FontWeights.Normal,
                TextTrimming = TextTrimming.CharacterEllipsis
            };
        }

        public static TextBlock Wrap(string s, double size, Color? color = null)
        {
            var t = T(s, size, color);
            t.TextWrapping = TextWrapping.Wrap;
            t.TextTrimming = TextTrimming.None;
            t.LineHeight = size * 1.45;
            return t;
        }

        public static TextBlock Section(string s, double top = 18)
        {
            var t = T(s.ToUpperInvariant(), 12, Label, FontWeights.SemiBold);
            t.Margin = new Thickness(2, top, 0, 7);
            return t;
        }

        public static Image Logo(double height)
        {
            var img = new Image { Height = height, Stretch = Stretch.Uniform };
            try
            {
                img.Source = new BitmapImage(new Uri("pack://application:,,,/Assets/logo.png"));
            }
            catch
            {
            }
            RenderOptions.SetBitmapScalingMode(img, BitmapScalingMode.HighQuality);
            return img;
        }

        public static BitmapSource Bitmap(string path, int decodeWidth = 0)
        {
            try
            {
                if (string.IsNullOrEmpty(path)) return null;
                var bi = new BitmapImage();
                bi.BeginInit();
                bi.CacheOption = BitmapCacheOption.OnLoad;
                bi.CreateOptions = BitmapCreateOptions.IgnoreImageCache;
                bi.UriSource = path.StartsWith("http") ? new Uri(path) : new Uri(System.IO.Path.GetFullPath(path));
                if (decodeWidth > 0) bi.DecodePixelWidth = decodeWidth;
                bi.EndInit();
                if (!path.StartsWith("http")) bi.Freeze();
                return bi;
            }
            catch
            {
                return null;
            }
        }

        public static System.Windows.Shapes.Path Icon(string data, double size, Color color, double stroke = 2)
        {
            return new System.Windows.Shapes.Path
            {
                Data = Geometry.Parse(data),
                Stroke = B(color),
                StrokeThickness = stroke,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round,
                StrokeLineJoin = PenLineJoin.Round,
                Width = size,
                Height = size,
                Stretch = Stretch.Uniform
            };
        }

        public const string PowerIcon = "M12,3 L12,12 M6.3,6.8 A8,8 0 1 0 17.7,6.8";
        public const string PlusIcon = "M12,5 L12,19 M5,12 L19,12";
        public const string FolderIcon = "M3,7 A2,2 0 0 1 5,5 L9,5 L11,7 L19,7 A2,2 0 0 1 21,9 L21,17 A2,2 0 0 1 19,19 L5,19 A2,2 0 0 1 3,17 Z";
        public const string TickIcon = "M5,12 L10,17 L19,7";

        // ------------------------------------------------------------------ buttons

        /// <summary>A rounded button like the ones at the top of the home screen. Highlighted: white with dark text.</summary>
        public static Clickable Pill(string text, Action click, bool primary = false, double size = 15)
        {
            var label = T(text, size, Text, primary ? FontWeights.SemiBold : FontWeights.Normal);
            var c = new Clickable
            {
                CornerRadius = new CornerRadius(22),
                Padding = new Thickness(22, 11, 22, 12),
                MinHeight = 44,
                Child = label,
                NormalBackground = B(A(0xFFFFFF, primary ? 0.18 : 0.12)),
                FocusBackground = B(Colors.White),
                BorderThickness = new Thickness(0)
            };
            c.Highlighted = on => label.Foreground = B(on ? Bg : Text);
            c.Clicked += click;
            return c;
        }

        public static Clickable IconPill(string iconData, string tooltip, Action click)
        {
            var icon = Icon(iconData, 20, Text);
            var c = new Clickable
            {
                Width = 44,
                Height = 44,
                CornerRadius = new CornerRadius(22),
                Child = icon,
                NormalBackground = B(A(0xFFFFFF, 0.12)),
                FocusBackground = B(Colors.White)
            };
            c.Highlighted = on => icon.Stroke = B(on ? Bg : Text);
            c.Clicked += click;
            System.Windows.Automation.AutomationProperties.SetName(c, tooltip);
            return c;
        }

        // ------------------------------------------------------------------ settings rows

        static Clickable RowShell(UIElement left, UIElement right)
        {
            var g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.Children.Add(left);
            if (right != null)
            {
                Grid.SetColumn(right, 1);
                if (right is FrameworkElement fe)
                {
                    fe.VerticalAlignment = VerticalAlignment.Center;
                    fe.Margin = new Thickness(16, 0, 0, 0);
                }
                g.Children.Add(right);
            }
            return new Clickable
            {
                Child = g,
                CornerRadius = new CornerRadius(9),
                Padding = new Thickness(16, 9, 16, 9),
                MinHeight = 46,
                Margin = new Thickness(0, 0, 0, 5),
                BorderThickness = new Thickness(2),
                NormalBackground = B(Panel),
                FocusBackground = B(Selected),
                FocusBorder = B(Colors.White)
            };
        }

        static StackPanel Titles(string title, string sub)
        {
            var sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(T(title, 15, Text));
            if (!string.IsNullOrEmpty(sub))
            {
                var s = Wrap(sub, 12, Dim);
                s.Margin = new Thickness(0, 3, 0, 0);
                sp.Children.Add(s);
            }
            return sp;
        }

        public static Clickable Row(string title, string sub, string value, Action click)
        {
            var v = value == null ? null : T(value, 14, Dim);
            var row = RowShell(Titles(title, sub), v);
            if (click != null) row.Clicked += click;
            else row.Cursor = Cursors.Arrow;
            return row;
        }

        public static Clickable Toggle(string title, string sub, bool value, Action<bool> set)
        {
            bool on = value;
            var track = new Border { Width = 40, Height = 22, CornerRadius = new CornerRadius(11) };
            var knob = new Border { Width = 18, Height = 18, CornerRadius = new CornerRadius(9), Background = Brushes.White, HorizontalAlignment = HorizontalAlignment.Left };
            track.Child = knob;
            void Draw()
            {
                track.Background = B(on ? Accent : Track);
                knob.Margin = new Thickness(on ? 20 : 2, 0, 0, 0);
            }
            Draw();
            var row = RowShell(Titles(title, sub), track);
            row.Clicked += () =>
            {
                on = !on;
                Draw();
                set(on);
            };
            return row;
        }

        /// <summary>A choice of a few options. Click, or left and right, to change it.</summary>
        public static Clickable Choice(string title, string sub, string[] options, int value, Action<int> set, bool wrapAround = true)
        {
            int current = Clamp(value, options.Length);
            var pills = new StackPanel { Orientation = Orientation.Horizontal };
            var parts = new List<(Border b, TextBlock t)>();
            foreach (string o in options)
            {
                var t = T(o, 14, Text, FontWeights.Medium);
                var b = new Border { CornerRadius = new CornerRadius(16), Padding = new Thickness(14, 6, 14, 7), Margin = new Thickness(6, 0, 0, 0), Child = t };
                parts.Add((b, t));
                pills.Children.Add(b);
            }
            void Draw()
            {
                for (int i = 0; i < parts.Count; i++)
                {
                    parts[i].b.Background = B(i == current ? Colors.White : Line);
                    parts[i].t.Foreground = B(i == current ? Bg : Text);
                }
            }
            Draw();
            var row = RowShell(Titles(title, sub), pills);
            void Change(int to)
            {
                if (to == current) return;
                current = to;
                Draw();
                set(current);
            }
            row.Clicked += () => Change((current + 1) % options.Length);
            row.Adjust = d =>
            {
                int to = current + d;
                if (to < 0 || to >= options.Length) return false;
                Change(to);
                return true;
            };
            for (int i = 0; i < parts.Count; i++)
            {
                int idx = i;
                parts[i].b.MouseLeftButtonUp += (s, e) =>
                {
                    e.Handled = true;
                    Change(idx);
                };
            }
            return row;
        }

        /// <summary>Five steps, Smallest to Largest. Left and right change it; so does clicking a dot.</summary>
        public static Clickable Steps(string title, int value, Action<int> set)
        {
            int current = Clamp(value, 5);
            var name = T(SizeNames[current], 14, Colors.White, FontWeights.SemiBold);
            name.Width = 74;
            name.TextAlignment = TextAlignment.Right;
            var dots = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            var bars = new List<Border>();
            for (int i = 0; i < 5; i++)
            {
                int idx = i;
                var b = new Border { Width = 22, Height = 8, CornerRadius = new CornerRadius(4), Margin = new Thickness(0, 0, 5, 0) };
                b.MouseLeftButtonUp += (s, e) =>
                {
                    e.Handled = true;
                    current = idx;
                    Draw();
                    set(current);
                };
                bars.Add(b);
                dots.Children.Add(b);
            }
            void Draw()
            {
                for (int i = 0; i < 5; i++) bars[i].Background = B(i <= current ? Accent : Track);
                name.Text = SizeNames[current];
            }
            Draw();
            var right = new StackPanel { Orientation = Orientation.Horizontal };
            right.Children.Add(dots);
            right.Children.Add(name);
            var row = RowShell(Titles(title, "Left and right to change"), right);
            row.Adjust = d =>
            {
                int to = current + d;
                if (to < 0 || to > 4) return false;
                current = to;
                Draw();
                set(current);
                return true;
            };
            row.Clicked += () =>
            {
                current = (current + 1) % 5;
                Draw();
                set(current);
            };
            return row;
        }

        public static TextBox Input(string text, string hint = null)
        {
            var tb = new TextBox
            {
                Text = text ?? "",
                FontFamily = Font,
                FontSize = 15,
                Foreground = B(Text),
                Background = B(Panel),
                BorderBrush = B(Line),
                BorderThickness = new Thickness(2),
                Padding = new Thickness(12, 10, 12, 10),
                CaretBrush = B(Colors.White),
                SelectionBrush = B(Accent),
                ToolTip = hint
            };
            tb.GotKeyboardFocus += (s, e) => tb.BorderBrush = B(Accent);
            tb.LostKeyboardFocus += (s, e) => tb.BorderBrush = B(Line);
            return tb;
        }

        // ------------------------------------------------------------------ app tiles

        static readonly (uint a, uint b)[] Gradients =
        {
            (0x2B3550, 0x151A26), (0x3A2B44, 0x17131F), (0x24403A, 0x121D1B), (0x44392B, 0x1F1A13),
            (0x2B3F4A, 0x131C21), (0x3A2F2B, 0x1C1513), (0x1E2430, 0x2E3647), (0x2B1F4F, 0x0F3B57)
        };

        public static Brush ArtBrush(string id, string artPath, int decodeWidth)
        {
            var bmp = artPath != null ? Bitmap(artPath, decodeWidth) : null;
            if (bmp != null) return new ImageBrush(bmp) { Stretch = Stretch.UniformToFill };
            int hash = 0;
            foreach (char ch in id ?? "") hash = unchecked(hash * 31 + ch);
            var g = Gradients[(int)((uint)hash % (uint)Gradients.Length)];
            return new LinearGradientBrush(C(g.a), C(g.b), 45);
        }

        /// <summary>An app tile with its name underneath. Badge: its position on the home screen; tag: what kind of app it is.</summary>
        public static FrameworkElement Tile(AppEntry a, double w, double h, Action click, Action menu, string badge = null, string tag = null, bool faded = false)
        {
            string name = Settings.Current.NameOf(a);
            string art = Settings.Current.ArtOf(a.Id);
            var inner = new Grid();
            if (art == null)
            {
                var mark = T(name.ToUpperInvariant(), Math.Max(13, Math.Min(20, w / 9)), Colors.White, FontWeights.Bold);
                mark.HorizontalAlignment = HorizontalAlignment.Center;
                mark.VerticalAlignment = VerticalAlignment.Center;
                mark.TextAlignment = TextAlignment.Center;
                mark.TextWrapping = TextWrapping.Wrap;
                mark.Margin = new Thickness(10);
                mark.MaxHeight = h - 16;
                inner.Children.Add(mark);
            }
            if (badge != null)
            {
                var bt = T(badge, 13, Colors.White, FontWeights.SemiBold);
                bt.HorizontalAlignment = HorizontalAlignment.Center;
                bt.VerticalAlignment = VerticalAlignment.Center;
                inner.Children.Add(new Border
                {
                    Width = 26, Height = 26, CornerRadius = new CornerRadius(13), Background = B(Accent),
                    HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top,
                    Margin = new Thickness(0, 7, 7, 0), Child = bt
                });
            }
            if (tag != null)
            {
                inner.Children.Add(new Border
                {
                    Background = B(A(0xFFFFFF, 0.85)), CornerRadius = new CornerRadius(4), Padding = new Thickness(6, 2, 6, 2),
                    HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Bottom,
                    Margin = new Thickness(7, 0, 0, 7), Child = T(tag, 10, Bg, FontWeights.SemiBold)
                });
            }
            var tile = new Clickable
            {
                Width = w,
                Height = h,
                CornerRadius = new CornerRadius(10),
                BorderThickness = new Thickness(3),
                Lift = true,
                FocusBorder = B(Colors.White),
                Child = inner,
                Opacity = faded ? 0.55 : 1
            };
            System.Windows.Automation.AutomationProperties.SetName(tile, name);
            tile.NormalBackground = ArtBrush(a.Id, art, (int)(w * 2));
            tile.Background = tile.NormalBackground;
            if (click != null) tile.Clicked += click;
            if (menu != null) tile.MenuRequested += menu;

            var label = T(name, 13, faded ? Dim : C(0xC9CFDA));
            label.Width = w;
            label.Margin = new Thickness(2, 9, 0, 0);
            tile.Highlighted = on => label.Foreground = B(on ? Colors.White : faded ? Dim : C(0xC9CFDA));

            var sp = new StackPanel { Width = w };
            sp.Children.Add(tile);
            sp.Children.Add(label);
            return sp;
        }

        public static Clickable TileOf(FrameworkElement tileWithLabel) =>
            (tileWithLabel as StackPanel)?.Children[0] as Clickable;

        /// <summary>The dashed "add" tile.</summary>
        public static FrameworkElement AddTile(string title, string sub, double w, double h, Action click)
        {
            var sp = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            var icon = Icon(PlusIcon, 26, Text);
            icon.HorizontalAlignment = HorizontalAlignment.Center;
            sp.Children.Add(icon);
            var t = T(title, 15, Text, FontWeights.Medium);
            t.HorizontalAlignment = HorizontalAlignment.Center;
            t.Margin = new Thickness(0, 6, 0, 0);
            sp.Children.Add(t);
            var rect = new Rectangle { Stroke = B(Track), StrokeThickness = 3, StrokeDashArray = new DoubleCollection { 3, 2 }, RadiusX = 10, RadiusY = 10 };
            var g = new Grid();
            g.Children.Add(rect);
            g.Children.Add(sp);
            var tile = new Clickable { Width = w, Height = h, CornerRadius = new CornerRadius(10), Lift = true, Child = g };
            tile.Highlighted = on => rect.Stroke = B(on ? Colors.White : Track);
            tile.Clicked += click;
            var label = T(sub, 13, Dim);
            label.Margin = new Thickness(2, 9, 0, 0);
            var outer = new StackPanel { Width = w };
            outer.Children.Add(tile);
            outer.Children.Add(label);
            return outer;
        }

        public static ScrollViewer Scroll(UIElement content, bool horizontal = false)
        {
            return new ScrollViewer
            {
                Content = content,
                VerticalScrollBarVisibility = horizontal ? ScrollBarVisibility.Disabled : ScrollBarVisibility.Hidden,
                HorizontalScrollBarVisibility = horizontal ? ScrollBarVisibility.Hidden : ScrollBarVisibility.Disabled,
                Focusable = false,
                CanContentScroll = false,
                Padding = new Thickness(40, 30, 40, 30),
                Margin = new Thickness(-40, -30, -40, -30)
            };
        }

        // ------------------------------------------------------------------ clock

        static readonly System.Globalization.CultureInfo UK = new System.Globalization.CultureInfo("en-GB");

        public static string TimeText(DateTime now) => now.ToString(Settings.Current.Clock24 ? "HH:mm" : "h:mm", UK);

        public static string AmPmText(DateTime now) => !Settings.Current.Clock24 && Settings.Current.ShowAmPm ? now.ToString("tt", UK).ToUpperInvariant() : "";

        /// <summary>The date under the clock, as you've chosen it, e.g. "Tuesday 24 November 2026". Null: no date.</summary>
        public static string DateText(DateTime now)
        {
            var s = Settings.Current;
            var parts = new List<string>();
            if (s.ShowWeekday) parts.Add(now.ToString("dddd", UK));
            if (s.ShowDayMonth) parts.Add(now.ToString("d MMMM", UK));
            if (s.ShowYear) parts.Add(now.ToString("yyyy", UK));
            return parts.Count > 0 ? string.Join(" ", parts) : null;
        }

        // ------------------------------------------------------------------ little pictures of the home screen

        /// <summary>A small drawing of the home screen in either layout, like the one in the TV's settings.</summary>
        public static FrameworkElement LayoutPicture(bool grid, double w, double h, Color bg)
        {
            var c = new Canvas { Width = w, Height = h, ClipToBounds = true, Background = B(bg) };
            double s = w / 1280.0;
            void R(double x, double y, double rw, double rh, Color col, double r = 3)
            {
                var rect = new Rectangle { Width = rw, Height = rh, RadiusX = r, RadiusY = r, Fill = B(col) };
                Canvas.SetLeft(rect, x);
                Canvas.SetTop(rect, y);
                c.Children.Add(rect);
            }
            double pad = 56 * s, top = 44 * s;
            R(pad, top, 150 * s, 48 * s, Track, 4 * s);
            double bx = w - pad;
            for (int i = 0; i < 4; i++)
            {
                double bw = i == 0 ? 44 * s : 120 * s;
                R(bx - bw, top + 12 * s, bw, 26 * s, C(0x2A3040), 13 * s);
                bx -= bw + 14 * s;
            }
            double tw = (grid ? 168 : 208) * s, th = tw * 9 / 16, gap = (grid ? 32 : 24) * s;
            if (grid)
            {
                int across = 6;
                double total = across * tw + (across - 1) * gap;
                double x0 = (w - total) / 2, y = top + 120 * s;
                for (int row = 0; row < 3 && y + th < h; row++)
                {
                    for (int i = 0; i < across; i++) R(x0 + i * (tw + gap), y, tw, th, row == 0 && i == 0 ? Text : Line);
                    y += th + 46 * s;
                }
            }
            else
            {
                double y = top + 210 * s;
                for (int i = 0; pad + i * (tw + gap) + tw <= w - pad + 1; i++) R(pad + i * (tw + gap), y, tw, th, i == 0 ? Text : Line);
            }
            return c;
        }

        /// <summary>A small home screen drawn on a colour or photo, for the background picker.</summary>
        public static FrameworkElement MiniHome(double w)
        {
            double s = w / 268.0;
            var g = new Grid { Margin = new Thickness(12 * s, 11 * s, 12 * s, 0) };
            var sp = new StackPanel();
            var top = new StackPanel { Orientation = Orientation.Horizontal };
            top.Children.Add(Logo(16 * s));
            var t = T("19:42", 13 * s, Colors.White, FontWeights.Light);
            t.Margin = new Thickness(7 * s, 0, 0, 0);
            t.VerticalAlignment = VerticalAlignment.Center;
            top.Children.Add(t);
            var bars = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            for (int i = 0; i < 3; i++) bars.Children.Add(new Border { Width = 22 * s, Height = 7 * s, CornerRadius = new CornerRadius(4 * s), Background = B(A(0xFFFFFF, 0.16)), Margin = new Thickness(4 * s, 0, 0, 0) });
            var head = new Grid();
            head.Children.Add(top);
            head.Children.Add(bars);
            sp.Children.Add(head);
            var tiles = new UniformGrid { Columns = 4, Margin = new Thickness(0, 14 * s, 0, 0) };
            for (int i = 0; i < 4; i++)
            {
                tiles.Children.Add(new Border
                {
                    Height = 26 * s, Margin = new Thickness(0, 0, 5 * s, 0), CornerRadius = new CornerRadius(3 * s),
                    Background = B(A(0xFFFFFF, i == 0 ? 0.22 : 0.12)),
                    BorderBrush = B(Colors.White), BorderThickness = new Thickness(i == 0 ? 1.5 : 0)
                });
            }
            sp.Children.Add(tiles);
            var row = new UniformGrid { Columns = 3, Margin = new Thickness(0, 7 * s, 0, 0) };
            for (int i = 0; i < 3; i++) row.Children.Add(new Border { Height = 20 * s, Margin = new Thickness(0, 0, 5 * s, 0), CornerRadius = new CornerRadius(3 * s), Background = B(A(0xFFFFFF, 0.08)) });
            sp.Children.Add(row);
            g.Children.Add(sp);
            g.IsHitTestVisible = false;
            return g;
        }
    }
}
