using System;
using System.Windows.Input;
using System.Windows.Threading;

namespace SimpleLauncherHTPC
{
    /// <summary>
    /// Xbox-style controllers (and most others, through XInput). Polled about 30 times a second.
    /// D-pad or left stick moves, A selects, B goes back, Y or Menu opens options.
    /// Holding View + Menu for a second brings SimpleLauncher back from anywhere.
    /// </summary>
    class Gamepad
    {
        const ushort Up = 0x0001, Down = 0x0002, Left = 0x0004, Right = 0x0008, Start = 0x0010, Back = 0x0020,
            A = 0x1000, B = 0x2000, X = 0x4000, Y = 0x8000;
        const short Stick = 16000;

        public event Action<FocusNavigationDirection> Move;
        public event Action Select, GoBack, Options, ComeBack;

        readonly DispatcherTimer timer;
        bool available = true;
        ushort last;
        FocusNavigationDirection? held;
        DateTime nextRepeat;
        DateTime comboSince = DateTime.MaxValue;
        bool comboFired;

        public Gamepad()
        {
            timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(33) };
            timer.Tick += (s, e) => Poll();
            timer.Start();
        }

        /// <summary>Navigation only goes to SimpleLauncher while it's on screen; the come-back combo works always.</summary>
        public bool Active { get; set; }

        void Poll()
        {
            if (!available) return;
            ushort buttons = 0;
            short lx = 0, ly = 0;
            bool any = false;
            try
            {
                for (uint i = 0; i < 4; i++)
                {
                    if (Native.XInputGetState(i, out var st) != 0) continue;
                    any = true;
                    buttons |= st.Gamepad.wButtons;
                    if (Math.Abs((int)st.Gamepad.sThumbLX) > Math.Abs((int)lx)) lx = st.Gamepad.sThumbLX;
                    if (Math.Abs((int)st.Gamepad.sThumbLY) > Math.Abs((int)ly)) ly = st.Gamepad.sThumbLY;
                }
            }
            catch (DllNotFoundException)
            {
                available = false;
                return;
            }
            catch (EntryPointNotFoundException)
            {
                available = false;
                return;
            }
            if (!any)
            {
                last = 0;
                held = null;
                return;
            }

            // View + Menu held for a second: come back.
            bool combo = (buttons & Back) != 0 && (buttons & Start) != 0;
            if (combo)
            {
                if (comboSince == DateTime.MaxValue) comboSince = DateTime.Now;
                if (!comboFired && (DateTime.Now - comboSince).TotalMilliseconds > 900)
                {
                    comboFired = true;
                    ComeBack?.Invoke();
                }
            }
            else
            {
                comboSince = DateTime.MaxValue;
                comboFired = false;
            }

            ushort pressed = (ushort)(buttons & ~last);
            last = buttons;
            if (!Active || combo) return;

            if ((pressed & A) != 0) Select?.Invoke();
            if ((pressed & B) != 0) GoBack?.Invoke();
            if ((pressed & (Y | X)) != 0) Options?.Invoke();
            if ((pressed & Start) != 0 && (buttons & Back) == 0) Options?.Invoke();

            FocusNavigationDirection? dir = null;
            if ((buttons & Up) != 0 || ly > Stick) dir = FocusNavigationDirection.Up;
            else if ((buttons & Down) != 0 || ly < -Stick) dir = FocusNavigationDirection.Down;
            else if ((buttons & Left) != 0 || lx < -Stick) dir = FocusNavigationDirection.Left;
            else if ((buttons & Right) != 0 || lx > Stick) dir = FocusNavigationDirection.Right;

            if (dir == null)
            {
                held = null;
                return;
            }
            if (held != dir)
            {
                held = dir;
                nextRepeat = DateTime.Now.AddMilliseconds(380);
                Move?.Invoke(dir.Value);
            }
            else if (DateTime.Now >= nextRepeat)
            {
                nextRepeat = DateTime.Now.AddMilliseconds(110);
                Move?.Invoke(dir.Value);
            }
        }
    }
}
