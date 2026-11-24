using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Threading;

namespace SimpleLauncherHTPC
{
    /// <summary>A screen: Home, App Selection, Settings and so on. Build() lays it out again from the settings.</summary>
    public abstract class View : Grid
    {
        public MainWindow Host;
        /// <summary>Show the background photo behind this screen (the home screen does).</summary>
        public virtual bool UsesPhoto => false;
        public abstract void Build();
        /// <summary>Back pressed: return true if the screen dealt with it itself.</summary>
        public virtual bool Back() => false;
        public virtual void Shown() { }
        public virtual void Hidden() { }
        /// <summary>What to highlight when the screen opens. Null: the first thing on it.</summary>
        public virtual IInputElement FirstFocus => null;
    }

    public class MainWindow : Window
    {
        const int Width0 = 1280, Height0 = 720;

        readonly Grid root = new Grid();
        readonly Grid photoLayer = new Grid { Visibility = Visibility.Collapsed };
        readonly Image photo = new Image { Stretch = Stretch.UniformToFill };
        readonly Image photoBlur = new Image { Stretch = Stretch.UniformToFill };
        readonly Rectangle dim = new Rectangle { Fill = Brushes.Black };
        readonly Grid stage = new Grid { Width = Width0, Height = Height0 };
        readonly Grid overlayHost = new Grid { Visibility = Visibility.Collapsed };
        readonly Grid overlay = new Grid { Width = Width0, Height = Height0 };
        readonly Border toast;
        readonly TextBlock toastText;
        readonly List<View> stack = new List<View>();
        readonly Gamepad pad = new Gamepad();
        readonly DispatcherTimer watcher = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(800) };
        Action overlayClosed;
        bool desktopMode;
        DateTime launchedAt = DateTime.MinValue;
        IntPtr hwnd;

        public View Current => stack.Count > 0 ? stack[stack.Count - 1] : null;
        public bool OverlayOpen => overlayHost.Visibility == Visibility.Visible;

        public MainWindow()
        {
            Title = "SimpleLauncher";
            try
            {
                Icon = BitmapFrame.Create(new Uri("pack://application:,,,/Assets/icon.ico"));
            }
            catch
            {
            }
            FontFamily = Ui.Font;
            UseLayoutRounding = true;
            TextOptions.SetTextFormattingMode(this, TextFormattingMode.Ideal);

            photoLayer.Children.Add(photoBlur);
            photoLayer.Children.Add(photo);
            photoLayer.Children.Add(dim);
            root.Children.Add(photoLayer);

            toastText = Ui.T("", 15, Colors.White);
            toast = new Border
            {
                Background = Ui.B(Ui.A(0x1E2430, 0.96)),
                CornerRadius = new CornerRadius(20),
                Padding = new Thickness(20, 10, 20, 11),
                Child = toastText,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(0, 0, 0, 34),
                Opacity = 0,
                IsHitTestVisible = false
            };
            var ui = new Grid { Width = Width0, Height = Height0 };
            ui.Children.Add(stage);
            ui.Children.Add(toast);
            root.Children.Add(new Viewbox { Stretch = Stretch.Uniform, Child = ui });

            overlayHost.Children.Add(new Rectangle { Fill = Ui.B(Ui.A(0x05070B, 0.72)) });
            overlayHost.Children.Add(new Viewbox { Stretch = Stretch.Uniform, Child = overlay });
            overlayHost.MouseDown += (s, e) =>
            {
                if (e.OriginalSource is Rectangle) CloseOverlay();
            };
            root.Children.Add(overlayHost);
            Content = root;

            ApplyWindowMode();
            ApplyBackground();

            PreviewKeyDown += OnKey;
            PreviewMouseDown += (s, e) =>
            {
                if (e.ChangedButton == MouseButton.XButton1)
                {
                    e.Handled = true;
                    GoBack();
                }
            };
            Activated += (s, e) =>
            {
                desktopMode = false;
                pad.Active = true;
            };
            Deactivated += (s, e) => pad.Active = false;

            pad.Move += Navigate;
            pad.Select += () => (Keyboard.FocusedElement as Clickable)?.Click();
            pad.GoBack += GoBack;
            pad.Options += () =>
            {
                if (Keyboard.FocusedElement is Clickable c && c.HasMenu) c.Menu();
            };
            pad.ComeBack += () => BringBack(true);

            watcher.Tick += (s, e) => Watch();
            watcher.Start();

            Loaded += (s, e) =>
            {
                if (Settings.Current.SetupDone) Replace(new HomeView());
                else Replace(new SetupView());
                StartupWork();
            };
        }

        async void StartupWork()
        {
            await Catalog.ScanAsync();
            if (!Settings.Current.SetupDone && Current is SetupView) Refresh();
            // The home screen made it: console mode's safety net can relax.
            await System.Threading.Tasks.Task.Delay(10000);
            if (Settings.Current.FailedStarts != 0)
            {
                Settings.Current.FailedStarts = 0;
                Settings.Save();
            }
        }

        protected override void OnSourceInitialized(EventArgs e)
        {
            base.OnSourceInitialized(e);
            hwnd = new WindowInteropHelper(this).Handle;
            HwndSource.FromHwnd(hwnd)?.AddHook(WndProc);
            Native.RegisterHotKey(hwnd, 1, Native.MOD_CONTROL | Native.MOD_ALT | Native.MOD_NOREPEAT, Native.VK_HOME);
        }

        protected override void OnClosed(EventArgs e)
        {
            Native.UnregisterHotKey(hwnd, 1);
            base.OnClosed(e);
        }

        IntPtr WndProc(IntPtr h, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
        {
            if (msg == Native.WM_HOTKEY && wParam.ToInt32() == 1)
            {
                handled = true;
                BringBack(true);
            }
            return IntPtr.Zero;
        }

        // ------------------------------------------------------------------ window and background

        public void ApplyWindowMode()
        {
            if (Settings.Current.FullScreen)
            {
                WindowState = WindowState.Normal;
                WindowStyle = WindowStyle.None;
                ResizeMode = ResizeMode.NoResize;
                WindowState = WindowState.Maximized;
            }
            else
            {
                WindowStyle = WindowStyle.SingleBorderWindow;
                ResizeMode = ResizeMode.CanResize;
                WindowState = WindowState.Normal;
                Width = 1280;
                Height = 760;
                WindowStartupLocation = WindowStartupLocation.CenterScreen;
            }
        }

        public void ApplyBackground()
        {
            var s = Settings.Current;
            Background = Ui.B(Ui.C(Ui.BgColors[Ui.Clamp(s.BgColour, Ui.BgColors.Length)]));
            string path = s.BgMode == "photo" ? s.BgPhoto : s.BgMode == "wallpaper" ? WindowsIntegration.WallpaperPath() : null;
            var bmp = path != null ? Ui.Bitmap(path, 2560) : null;
            photo.Source = bmp;
            photoBlur.Source = bmp;
            bool fit = s.Fit != 0;
            photo.Stretch = fit ? Stretch.Uniform : Stretch.UniformToFill;
            photoBlur.Visibility = s.Fit == 1 && bmp != null ? Visibility.Visible : Visibility.Collapsed;
            photoBlur.Effect = new BlurEffect { Radius = 60 };
            dim.Opacity = Ui.DimLevels[Ui.Clamp(s.Dim, Ui.DimLevels.Length)] + (s.Fit == 1 ? 0.1 : 0);
            UpdatePhotoVisibility();
        }

        void UpdatePhotoVisibility()
        {
            bool show = Current != null && Current.UsesPhoto && photo.Source != null;
            photoLayer.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
        }

        // ------------------------------------------------------------------ moving between screens

        public void Go(View v)
        {
            Current?.Hidden();
            stack.Add(v);
            Present(v, true);
        }

        public void Replace(View v)
        {
            Current?.Hidden();
            stack.Clear();
            stack.Add(v);
            Present(v, true);
        }

        public void GoHome()
        {
            CloseOverlay();
            if (!Settings.Current.SetupDone) return;
            if (stack.Count == 1 && Current is HomeView) return;
            Replace(new HomeView());
        }

        public void GoBack()
        {
            if (OverlayOpen)
            {
                CloseOverlay();
                return;
            }
            if (Current != null && Current.Back()) return;
            if (stack.Count <= 1) return;
            Current.Hidden();
            stack.RemoveAt(stack.Count - 1);
            Present(Current, true);
        }

        /// <summary>Lays the current screen out again, keeping the highlight where it was.</summary>
        public void Refresh(string focusTag = null)
        {
            if (Current == null) return;
            string tag = focusTag ?? (Keyboard.FocusedElement as FrameworkElement)?.Tag as string;
            Present(Current, false, tag);
        }

        void Present(View v, bool animate, string focusTag = null)
        {
            v.Host = this;
            v.Children.Clear();
            v.RowDefinitions.Clear();
            v.ColumnDefinitions.Clear();
            v.Build();
            if (stage.Children.Count != 1 || stage.Children[0] != v)
            {
                stage.Children.Clear();
                stage.Children.Add(v);
            }
            UpdatePhotoVisibility();
            if (animate)
            {
                v.Opacity = 0;
                var tt = new TranslateTransform(0, 14);
                v.RenderTransform = tt;
                var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
                v.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
                tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(14, 0, TimeSpan.FromMilliseconds(260)) { EasingFunction = ease });
                v.Shown();
            }
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (OverlayOpen) return;
                var target = focusTag != null ? FindTag(v, focusTag) : null;
                if (target != null)
                {
                    target.Focus();
                    if (target is TextBox tb) tb.CaretIndex = tb.Text.Length;
                }
                else FocusFirst();
            }), DispatcherPriority.Loaded);
        }

        public void FocusFirst()
        {
            var v = Current;
            if (v == null) return;
            if (v.FirstFocus is IInputElement f && f.Focusable)
            {
                Keyboard.Focus(f);
                return;
            }
            var first = Descendants(v).OfType<Clickable>().FirstOrDefault(c => c.IsVisible && c.IsEnabled);
            if (first != null) first.Focus();
        }

        public static UIElement FindTag(DependencyObject parent, string tag) =>
            Descendants(parent).OfType<FrameworkElement>().FirstOrDefault(f => tag.Equals(f.Tag as string) && f.Focusable && f.IsVisible);

        public static IEnumerable<DependencyObject> Descendants(DependencyObject parent)
        {
            foreach (object child in LogicalTreeHelper.GetChildren(parent))
            {
                if (child is DependencyObject d)
                {
                    yield return d;
                    foreach (var dd in Descendants(d)) yield return dd;
                }
            }
        }

        // ------------------------------------------------------------------ keyboard, remote and controller

        void OnKey(object sender, KeyEventArgs e)
        {
            var focused = Keyboard.FocusedElement as DependencyObject;
            bool inText = focused is TextBox;
            switch (e.Key)
            {
                case Key.Escape:
                case Key.BrowserBack:
                    e.Handled = true;
                    GoBack();
                    return;
                case Key.BrowserHome:
                    e.Handled = true;
                    GoHome();
                    return;
                case Key.Back:
                    if (!inText)
                    {
                        e.Handled = true;
                        GoBack();
                    }
                    return;
                case Key.Enter:
                    if (inText)
                    {
                        e.Handled = true;
                        Navigate(FocusNavigationDirection.Down);
                    }
                    return;
                case Key.Left:
                case Key.Right:
                    if (inText) return;
                    if (focused is Clickable c && c.Adjust != null && c.Adjust(e.Key == Key.Left ? -1 : 1))
                    {
                        e.Handled = true;
                        return;
                    }
                    e.Handled = true;
                    Navigate(e.Key == Key.Left ? FocusNavigationDirection.Left : FocusNavigationDirection.Right);
                    return;
                case Key.Up:
                case Key.Down:
                    e.Handled = true;
                    Navigate(e.Key == Key.Up ? FocusNavigationDirection.Up : FocusNavigationDirection.Down);
                    return;
            }
        }

        public void Navigate(FocusNavigationDirection dir)
        {
            var focused = Keyboard.FocusedElement as UIElement;
            DependencyObject scope = OverlayOpen ? (DependencyObject)overlay : stage;
            if (focused == null || !IsInside(focused, scope))
            {
                if (OverlayOpen) Descendants(overlay).OfType<Clickable>().FirstOrDefault()?.Focus();
                else FocusFirst();
                return;
            }
            if (focused is Clickable cl && (dir == FocusNavigationDirection.Left || dir == FocusNavigationDirection.Right) && cl.Adjust != null)
            {
                if (cl.Adjust(dir == FocusNavigationDirection.Left ? -1 : 1)) return;
            }
            focused.MoveFocus(new TraversalRequest(dir));
        }

        static bool IsInside(DependencyObject child, DependencyObject parent)
        {
            for (var d = child; d != null; d = LogicalTreeHelper.GetParent(d) ?? VisualTreeHelper.GetParent(d))
            {
                if (d == parent) return true;
            }
            return false;
        }

        // ------------------------------------------------------------------ pop-ups

        /// <summary>A pop-up list of choices, like Android's options menu.</summary>
        public void ShowMenu(string title, string message, IList<(string label, Action act)> options)
        {
            var sp = new StackPanel();
            sp.Children.Add(Ui.T(title, 22, Colors.White, FontWeights.Medium));
            if (!string.IsNullOrEmpty(message))
            {
                var m = Ui.Wrap(message, 14, Ui.Dim);
                m.Margin = new Thickness(0, 8, 0, 0);
                sp.Children.Add(m);
            }
            var list = new StackPanel { Margin = new Thickness(0, 18, 0, 0) };
            foreach (var o in options)
            {
                var act = o.act;
                list.Children.Add(Ui.Row(o.label, null, null, () =>
                {
                    CloseOverlay();
                    act?.Invoke();
                }));
            }
            sp.Children.Add(options.Count > 7 ? (UIElement)new ScrollViewer
            {
                Content = list,
                MaxHeight = 430,
                VerticalScrollBarVisibility = ScrollBarVisibility.Hidden,
                Focusable = false
            } : list);
            ShowPanel(sp, 540);
        }

        public void Confirm(string title, string message, string yes, Action onYes)
        {
            ShowMenu(title, message, new List<(string, Action)> { (yes, onYes), ("Cancel", null) });
        }

        /// <summary>Shows any content in a pop-up. onClosed runs however it closes.</summary>
        public void ShowPanel(UIElement content, double width, Action onClosed = null)
        {
            CloseOverlay();
            overlay.Children.Clear();
            var panel = new Border
            {
                Width = width,
                Background = Ui.B(Ui.C(0x11141B)),
                BorderBrush = Ui.B(Ui.C(0x2A3040)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(30, 26, 30, 24),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Child = content,
                Effect = new DropShadowEffect { BlurRadius = 60, ShadowDepth = 0, Opacity = 0.6 }
            };
            overlay.Children.Add(panel);
            overlayClosed = onClosed;
            stage.IsEnabled = false;
            overlayHost.Visibility = Visibility.Visible;
            panel.Opacity = 0;
            panel.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(160)));
            Dispatcher.BeginInvoke(new Action(() =>
                Descendants(overlay).OfType<Clickable>().FirstOrDefault()?.Focus()), DispatcherPriority.Loaded);
        }

        public void CloseOverlay()
        {
            if (!OverlayOpen) return;
            overlayHost.Visibility = Visibility.Collapsed;
            overlay.Children.Clear();
            stage.IsEnabled = true;
            var closed = overlayClosed;
            overlayClosed = null;
            closed?.Invoke();
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (!(Keyboard.FocusedElement is UIElement f) || !IsInside(f, stage)) FocusFirst();
            }), DispatcherPriority.Loaded);
        }

        public void Toast(string text)
        {
            toastText.Text = text;
            var a = new DoubleAnimationUsingKeyFrames();
            a.KeyFrames.Add(new LinearDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(150))));
            a.KeyFrames.Add(new LinearDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(2800))));
            a.KeyFrames.Add(new LinearDoubleKeyFrame(0, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(3200))));
            toast.BeginAnimation(OpacityProperty, a);
        }

        // ------------------------------------------------------------------ apps, the desktop and coming back

        public void Launch(AppEntry a)
        {
            launchedAt = DateTime.Now;
            string name = Settings.Current.NameOf(a);
            if (Catalog.Launch(a)) Toast("Opening " + name + "…");
            else Toast("Couldn't open " + name);
        }

        /// <summary>The Desktop button: steps aside so you can use Windows.</summary>
        public void ShowDesktop()
        {
            desktopMode = true;
            if (!WindowsIntegration.DesktopRunning) WindowsIntegration.StartDesktop();
            WindowState = WindowState.Minimized;
        }

        /// <summary>The shortcut, the controller combo or opening SimpleLauncher again.</summary>
        public void BringBack(bool home)
        {
            desktopMode = false;
            if (!IsVisible) Show();
            if (WindowState == WindowState.Minimized) WindowState = Settings.Current.FullScreen ? WindowState.Maximized : WindowState.Normal;
            Activate();
            Topmost = true;
            Topmost = false;
            if (hwnd != IntPtr.Zero) Native.ForceForeground(hwnd);
            if (home) GoHome();
        }

        /// <summary>"Come back when an app closes": if Windows ends up showing the bare desktop, SimpleLauncher steps back in.</summary>
        void Watch()
        {
            var s = Settings.Current;
            if (!s.ComeBack || desktopMode || IsActive || !s.SetupDone) return;
            if ((DateTime.Now - launchedAt).TotalSeconds < 6) return;
            IntPtr fg = Native.GetForegroundWindow();
            string cls = fg == IntPtr.Zero ? "" : Native.ClassOf(fg);
            if (fg == IntPtr.Zero || cls == "Progman" || cls == "WorkerW") BringBack(false);
        }
    }
}
