using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Microsoft.Win32;

namespace SimpleLauncherHTPC
{
    /// <summary>Add your own app (a program, game, Store app or website), or change an app's name and artwork.</summary>
    public class AddAppView : View
    {
        static readonly string[] Kinds = { "Program or game", "Store app", "Website" };
        static readonly string[] KindKeys = { "program", "store", "website" };

        readonly AppEntry editing;
        int kind;
        string location = "", name = "", args = "";
        List<Artwork.Option> options = new List<Artwork.Option>();
        int chosen = -2;            // -2 keep what it has, -1 plain card, 0+ an online option
        string localImage;
        bool searching;
        string searchedFor;
        string searchError;
        bool saving;

        public AddAppView(AppEntry editing)
        {
            this.editing = editing;
            if (editing != null)
            {
                kind = Math.Max(0, Array.IndexOf(KindKeys, editing.Kind));
                location = editing.Target ?? "";
                args = editing.Args ?? "";
                name = Settings.Current.NameOf(editing);
            }
        }

        bool Locked => editing != null && !editing.Custom;

        public override void Build()
        {
            var page = new DockPanel { Margin = new Thickness(56, 40, 56, 30) };
            Children.Add(page);

            var head = new StackPanel { Orientation = Orientation.Horizontal };
            head.Children.Add(Ui.Logo(52));
            var titles = new StackPanel { Margin = new Thickness(22, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            titles.Children.Add(Ui.T(editing == null ? "Add your own app" : "Artwork and name", 32, Colors.White, FontWeights.Medium));
            titles.Children.Add(Ui.T(editing == null
                ? "For anything SimpleLauncher didn't find: a program, a game or a website."
                : Locked ? Settings.Current.NameOf(editing) + " · from your Start menu" : Settings.Current.NameOf(editing) + " · " + editing.KindLabel, 15, Ui.Dim));
            head.Children.Add(titles);
            DockPanel.SetDock(head, Dock.Top);
            page.Children.Add(head);

            var foot = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 16, 0, 0) };
            var cancel = Ui.Pill("Cancel", () => Host.GoBack());
            cancel.Tag = "cancel";
            var save = Ui.Pill(saving ? "Saving…" : editing == null ? "Add to home screen" : "Save", Save, true);
            save.Tag = "save";
            save.Margin = new Thickness(12, 0, 0, 0);
            foot.Children.Add(cancel);
            foot.Children.Add(save);
            DockPanel.SetDock(foot, Dock.Bottom);
            page.Children.Add(foot);

            var cols = new Grid { Margin = new Thickness(0, 24, 0, 0) };
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(440) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
            cols.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            page.Children.Add(cols);

            // Left: what it is, where it is, what it's called
            var left = new StackPanel();
            if (!Locked)
            {
                left.Children.Add(Ui.Section("What is it?", 0));
                var k = Ui.Choice("Type", null, Kinds, kind, v =>
                {
                    kind = v;
                    location = "";
                    Host.Refresh();
                });
                k.Tag = "kind";
                left.Children.Add(k);

                left.Children.Add(Ui.Section(kind == 0 ? "Program" : kind == 1 ? "Store app" : "Web address", 14));
                if (kind == 1)
                {
                    var pick = Ui.Row(string.IsNullOrEmpty(location) ? "Pick a Store app" : name, "Choose from the apps installed from the Microsoft Store", "Pick ›", PickStoreApp);
                    pick.Tag = "location";
                    left.Children.Add(pick);
                }
                else
                {
                    var row = new DockPanel();
                    if (kind == 0)
                    {
                        var browse = Ui.Pill("Browse…", Browse, false, 14);
                        browse.Tag = "browse";
                        browse.Margin = new Thickness(8, 0, 0, 0);
                        DockPanel.SetDock(browse, Dock.Right);
                        row.Children.Add(browse);
                    }
                    var loc = Ui.Input(location, kind == 0 ? @"C:\Games\Something\game.exe" : "https://www.example.com");
                    loc.Tag = "locationText";
                    loc.TextChanged += (s, e) => location = loc.Text;
                    row.Children.Add(loc);
                    left.Children.Add(row);
                    var hint = Ui.T(kind == 0 ? "Opens the Windows file picker. Shortcuts (.lnk) and .bat files work too." : "Opens in your web browser.", 12, Ui.Label);
                    hint.Margin = new Thickness(2, 6, 0, 0);
                    left.Children.Add(hint);
                }
            }

            left.Children.Add(Ui.Section("Name", Locked ? 0 : 14));
            var nameBox = Ui.Input(name, "What to call it");
            nameBox.Tag = "name";
            nameBox.TextChanged += (s, e) => name = nameBox.Text;
            left.Children.Add(nameBox);
            var again = Ui.Pill("Search for artwork with this name", () => Search(true), false, 14);
            again.Tag = "search";
            again.HorizontalAlignment = HorizontalAlignment.Left;
            again.Margin = new Thickness(0, 8, 0, 0);
            left.Children.Add(again);

            if (!Locked && kind == 0)
            {
                left.Children.Add(Ui.Section("Start options (optional)", 14));
                var argBox = Ui.Input(args, "For example --fullscreen");
                argBox.Tag = "args";
                argBox.TextChanged += (s, e) => args = argBox.Text;
                left.Children.Add(argBox);
            }
            cols.Children.Add(Ui.Scroll(left));

            // Right: artwork
            var right = new StackPanel();
            var artHead = new DockPanel();
            var found = Ui.T(searchedFor != null ? "Found online for \"" + searchedFor + "\"" : "", 12, Ui.Label);
            DockPanel.SetDock(found, Dock.Right);
            artHead.Children.Add(found);
            artHead.Children.Add(Ui.Section("Artwork", 0));
            right.Children.Add(artHead);

            var wrap = new WrapPanel();
            // The current picture (when changing an app), a plain card, then what was found online.
            if (editing != null && Settings.Current.ArtOf(editing.Id) != null)
            {
                wrap.Children.Add(Option("Current", Ui.ArtBrush(editing.Id, Settings.Current.ArtOf(editing.Id), 400), chosen == -2, () => Choose(-2), "art:current"));
            }
            if (localImage != null)
            {
                wrap.Children.Add(Option("From this PC", Ui.ArtBrush("local", localImage, 400), chosen == -3, () => Choose(-3), "art:local"));
            }
            wrap.Children.Add(Option("Plain card", Ui.ArtBrush(editing?.Id ?? name, null, 0), chosen == -1 || (chosen == -2 && (editing == null || Settings.Current.ArtOf(editing.Id) == null) && options.Count == 0), () => Choose(-1), "art:plain", name));
            for (int i = 0; i < options.Count; i++)
            {
                int idx = i;
                var bmp = Ui.Bitmap(options[i].Thumb, 400);
                Brush b = bmp != null ? new ImageBrush(bmp) { Stretch = Stretch.UniformToFill } : Ui.ArtBrush("x" + i, null, 0);
                wrap.Children.Add(Option(options[i].Game, b, chosen == idx, () => Choose(idx), "art:" + i));
            }
            right.Children.Add(wrap);

            string status = searching ? "Searching…"
                : !Artwork.HasKey ? "Artwork search needs a SteamGridDB key. Add your own in Settings › Home screen."
                : searchError ?? (searchedFor != null && options.Count == 0 ? "Nothing found for \"" + searchedFor + "\". Try a shorter name, or use a picture from this PC." : null);
            if (status != null)
            {
                var st = Ui.Wrap(status, 13, Ui.Dim);
                st.Margin = new Thickness(2, 2, 0, 10);
                right.Children.Add(st);
            }
            var pc = Ui.Pill("Choose an image on this PC…", ChooseImage, false, 14);
            pc.Tag = "pc";
            pc.HorizontalAlignment = HorizontalAlignment.Left;
            right.Children.Add(pc);
            Grid.SetColumn(right, 2);
            var rs = Ui.Scroll(right);
            Grid.SetColumn(rs, 2);
            cols.Children.Add(rs);
        }

        FrameworkElement Option(string label, Brush art, bool selected, Action pick, string tag, string mark = null)
        {
            var inner = new Grid();
            if (mark != null)
            {
                var t = Ui.T((mark ?? "").ToUpperInvariant(), 16, Colors.White, FontWeights.Bold);
                t.HorizontalAlignment = HorizontalAlignment.Center;
                t.VerticalAlignment = VerticalAlignment.Center;
                inner.Children.Add(t);
            }
            if (selected)
            {
                var tick = Ui.Icon(Ui.TickIcon, 14, Colors.White, 3);
                inner.Children.Add(new Border
                {
                    Width = 26, Height = 26, CornerRadius = new CornerRadius(13), Background = Ui.B(Ui.Accent),
                    HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(7), Child = tick
                });
            }
            var c = new Clickable
            {
                Width = 196, Height = 110, CornerRadius = new CornerRadius(10), BorderThickness = new Thickness(3), Lift = true,
                NormalBackground = art, NormalBorder = Ui.B(selected ? Colors.White : Colors.Transparent), FocusBorder = Ui.B(Colors.White),
                Child = inner, Tag = tag
            };
            c.Background = art;
            c.Clicked += pick;
            var l = Ui.T(label, 12, selected ? Colors.White : Ui.Dim);
            l.Margin = new Thickness(2, 6, 0, 0);
            l.Width = 196;
            var sp = new StackPanel { Width = 196, Margin = new Thickness(0, 0, 16, 14) };
            sp.Children.Add(c);
            sp.Children.Add(l);
            return sp;
        }

        void Choose(int i)
        {
            chosen = i;
            Host.Refresh();
        }

        public override void Shown()
        {
            if (!string.IsNullOrWhiteSpace(name)) Search(false);
        }

        async void Search(bool always)
        {
            string term = (name ?? "").Trim();
            if (term.Length == 0 || !Artwork.HasKey) return;
            if (!always && term == searchedFor) return;
            searching = true;
            searchError = null;
            Host.Refresh();
            try
            {
                options = await Artwork.Search(term);
                if (editing == null && options.Count > 0 && chosen == -2) chosen = 0;
            }
            catch
            {
                options = new List<Artwork.Option>();
                searchError = "Couldn't search for artwork just now. Check the internet connection.";
            }
            searchedFor = term;
            searching = false;
            if (Host.Current == this) Host.Refresh();
        }

        void Browse()
        {
            var dlg = new OpenFileDialog
            {
                Title = "Choose a program, game or shortcut",
                Filter = "Programs and shortcuts|*.exe;*.lnk;*.bat;*.cmd;*.url|All files|*.*"
            };
            if (dlg.ShowDialog(Host) != true) return;
            location = dlg.FileName;
            if (string.IsNullOrWhiteSpace(name))
            {
                name = Path.GetFileNameWithoutExtension(dlg.FileName);
                Search(true);
            }
            Host.Refresh();
        }

        void PickStoreApp()
        {
            var store = Settings.Current.Known.Where(a => a.Kind == "store").OrderBy(a => a.Name).ToList();
            if (store.Count == 0)
            {
                Host.Toast("No Store apps found yet");
                return;
            }
            Host.ShowMenu("Pick a Store app", null, store.Select(a => (a.Name, (Action)(() =>
            {
                location = a.Target;
                name = a.Name;
                Host.Refresh();
                Search(true);
            }))).ToList());
        }

        void ChooseImage()
        {
            var dlg = new OpenFileDialog { Title = "Choose a picture for this app", Filter = "Pictures|*.png;*.jpg;*.jpeg;*.bmp;*.gif|All files|*.*" };
            if (dlg.ShowDialog(Host) != true) return;
            localImage = dlg.FileName;
            chosen = -3;
            Host.Refresh();
        }

        async void Save()
        {
            if (saving) return;
            var s = Settings.Current;
            string n = (name ?? "").Trim();
            if (n.Length == 0)
            {
                Host.Toast("Give it a name first");
                return;
            }
            AppEntry target = editing;
            if (editing == null || editing.Custom)
            {
                string loc = (location ?? "").Trim().Trim('"');
                if (loc.Length == 0)
                {
                    Host.Toast(kind == 1 ? "Pick a Store app first" : kind == 0 ? "Choose the program first" : "Type the web address first");
                    return;
                }
                if (kind == 0 && !File.Exists(loc))
                {
                    Host.Toast("Couldn't find that file");
                    return;
                }
                if (editing == null)
                {
                    target = new AppEntry { Id = "custom:" + Guid.NewGuid().ToString("N"), Custom = true };
                    s.CustomApps.Add(target);
                    s.Home.Add(target.Id);
                }
                target.Name = n;
                target.Kind = KindKeys[kind];
                target.Target = loc;
                target.Args = kind == 0 ? (args ?? "").Trim() : null;
            }
            else
            {
                if (n == editing.Name) s.Names.Remove(editing.Id);
                else s.Names[editing.Id] = n;
            }

            saving = true;
            Host.Refresh();
            try
            {
                if (!s.ArtTried.Contains(target.Id)) s.ArtTried.Add(target.Id);
                if (chosen == -1) s.Art[target.Id] = "";
                else if (chosen == -3 && localImage != null) Artwork.SaveLocal(target.Id, localImage);
                else if (chosen >= 0 && chosen < options.Count) await Artwork.Save(target.Id, options[chosen].Url);
            }
            catch
            {
                Host.Toast("Saved, but the artwork couldn't be downloaded");
            }
            saving = false;
            Settings.Save();
            Host.GoBack();
        }
    }
}
