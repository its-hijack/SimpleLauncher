using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace SimpleLauncherHTPC
{
    /// <summary>Signing in to Jellyfin: choose your server, then Quick Connect or your username and password.</summary>
    public class JellyfinView : View
    {
        List<Jellyfin.Server> found;
        bool looking, busy;
        string typed = "", user = "", password = "", error;
        Jellyfin.Server server;
        Jellyfin.QuickConnect quick;
        bool usePassword;
        bool alive = true;

        public override void Build()
        {
            var page = new StackPanel { Margin = new Thickness(56, 40, 56, 30), Width = 760, HorizontalAlignment = HorizontalAlignment.Left };
            Children.Add(page);
            var head = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
            head.Children.Add(Ui.Logo(52));
            var titles = new StackPanel { Margin = new Thickness(22, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            titles.Children.Add(Ui.T("Connect Jellyfin", 32, Colors.White, FontWeights.Medium));
            titles.Children.Add(Ui.T(server == null ? "Choose your server" : server.Name + " · " + server.Address, 15, Ui.Dim));
            head.Children.Add(titles);
            page.Children.Add(head);

            if (server == null) ServerStep(page);
            else if (quick != null && !usePassword) QuickStep(page);
            else PasswordStep(page);

            if (error != null)
            {
                var e = Ui.Wrap(error, 14, Ui.C(0xFF8A80));
                e.Margin = new Thickness(2, 12, 0, 0);
                page.Children.Add(e);
            }
        }

        void ServerStep(Panel p)
        {
            p.Children.Add(Ui.Section("On your network"));
            if (looking) p.Children.Add(Ui.T("Looking for Jellyfin servers…", 15, Ui.Dim));
            else if (found == null || found.Count == 0) p.Children.Add(Ui.T("None found. Type its address below instead.", 15, Ui.Dim));
            else
            {
                foreach (var s in found)
                {
                    var srv = s;
                    var r = Ui.Row(srv.Name, srv.Address, "Connect ›", () => Connect(srv.Address));
                    r.Tag = "srv:" + srv.Address;
                    p.Children.Add(r);
                }
            }
            p.Children.Add(Ui.Section("Or type its address"));
            var row = new DockPanel();
            var go = Ui.Pill(busy ? "Connecting…" : "Connect", () => Connect(Jellyfin.Normalise(typed)), true, 14);
            go.Tag = "go";
            go.Margin = new Thickness(10, 0, 0, 0);
            DockPanel.SetDock(go, Dock.Right);
            row.Children.Add(go);
            var box = Ui.Input(typed, "For example 192.168.1.10:8096");
            box.Tag = "address";
            box.TextChanged += (s, e) => typed = box.Text;
            row.Children.Add(box);
            p.Children.Add(row);
            var again = Ui.Pill("Look again", Discover, false, 14);
            again.Tag = "again";
            again.HorizontalAlignment = HorizontalAlignment.Left;
            again.Margin = new Thickness(0, 14, 0, 0);
            p.Children.Add(again);
        }

        void QuickStep(Panel p)
        {
            p.Children.Add(Ui.Section("Quick Connect"));
            p.Children.Add(Ui.Wrap("In Jellyfin on your phone or computer, open your profile, choose Quick Connect and enter this code:", 15, Ui.Dim));
            var code = Ui.T(quick.Code, 54, Colors.White, FontWeights.SemiBold);
            code.Margin = new Thickness(0, 14, 0, 6);
            p.Children.Add(code);
            p.Children.Add(Ui.T("Waiting for you to approve it…", 13, Ui.Label));
            var pw = Ui.Pill("Use my username and password instead", () =>
            {
                usePassword = true;
                Host.Refresh();
            }, false, 14);
            pw.Tag = "pw";
            pw.HorizontalAlignment = HorizontalAlignment.Left;
            pw.Margin = new Thickness(0, 18, 0, 0);
            p.Children.Add(pw);
        }

        void PasswordStep(Panel p)
        {
            p.Children.Add(Ui.Section("Username"));
            var u = Ui.Input(user);
            u.Tag = "user";
            u.TextChanged += (s, e) => user = u.Text;
            p.Children.Add(u);
            p.Children.Add(Ui.Section("Password"));
            var pw = new PasswordBox
            {
                FontFamily = Ui.Font, FontSize = 15, Foreground = Ui.B(Ui.Text), Background = Ui.B(Ui.Panel), BorderBrush = Ui.B(Ui.Line),
                BorderThickness = new Thickness(2), Padding = new Thickness(12, 10, 12, 10), CaretBrush = Ui.B(Colors.White), Tag = "password"
            };
            pw.PasswordChanged += (s, e) => password = pw.Password;
            p.Children.Add(pw);
            var go = Ui.Pill(busy ? "Signing in…" : "Sign in", SignIn, true);
            go.Tag = "signin";
            go.HorizontalAlignment = HorizontalAlignment.Left;
            go.Margin = new Thickness(0, 16, 0, 0);
            p.Children.Add(go);
        }

        public override void Shown() => Discover();

        public override void Hidden() => alive = false;

        public override bool Back()
        {
            if (server != null)
            {
                server = null;
                quick = null;
                usePassword = false;
                error = null;
                Host.Refresh();
                return true;
            }
            return false;
        }

        async void Discover()
        {
            looking = true;
            Host.Refresh();
            found = await Jellyfin.Discover();
            looking = false;
            if (alive && Host.Current == this) Host.Refresh();
        }

        async void Connect(string address)
        {
            if (busy || string.IsNullOrEmpty(address)) return;
            busy = true;
            error = null;
            Host.Refresh();
            try
            {
                server = await Jellyfin.Check(address);
                if (await Jellyfin.QuickConnectEnabled(address))
                {
                    quick = await Jellyfin.StartQuickConnect(address);
                    busy = false;
                    Host.Refresh();
                    WaitForQuickConnect(server, quick);
                    return;
                }
            }
            catch
            {
                server = null;
                error = "Couldn't connect to a Jellyfin server at " + address + ".";
            }
            busy = false;
            Host.Refresh();
        }

        async void WaitForQuickConnect(Jellyfin.Server srv, Jellyfin.QuickConnect q)
        {
            var until = DateTime.Now.AddMinutes(10);
            while (alive && quick == q && !usePassword && DateTime.Now < until)
            {
                await Task.Delay(2000);
                if (!alive || quick != q || usePassword) return;
                if (!await Jellyfin.QuickConnectApproved(srv.Address, q)) continue;
                try
                {
                    await Jellyfin.FinishQuickConnect(srv, q);
                    Done();
                }
                catch
                {
                    error = "Quick Connect didn't work. Try your username and password.";
                    usePassword = true;
                    Host.Refresh();
                }
                return;
            }
        }

        async void SignIn()
        {
            if (busy) return;
            busy = true;
            error = null;
            Host.Refresh();
            try
            {
                await Jellyfin.SignInWithPassword(server, user.Trim(), password);
                busy = false;
                Done();
                return;
            }
            catch
            {
                error = "That username or password didn't work.";
            }
            busy = false;
            Host.Refresh();
        }

        void Done()
        {
            alive = false;
            server = null;
            quick = null;
            Watching.Forget();
            Host.Toast("Signed in to Jellyfin");
            Host.GoBack();
        }
    }
}
