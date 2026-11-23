// SimpleScript for Windows, by Cherry Universe.
//
// The same guided setup as SimpleScript.sh: gets ADB, helps you switch on ADB debugging on your TV,
// connects, then installs SimpleLauncher and (on supported devices only) debloats or restores.
//
// The supported-device list and debloat list are read from SimpleScript.sh when this is built
// (see build.sh), so both versions always offer exactly the same things.
package main

import (
	"archive/zip"
	"bufio"
	_ "embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"
)

//go:embed lists.sh
var listsSource string

const (
	slPkg     = "com.simplehome.tv"
	slService = "com.simplehome.tv/com.simplehome.tv.HomeButtonService"
)

var (
	scriptVersion = "1.0"
	repo          = "its-hijack/SimpleLauncher"
	repoURL       = "https://github.com/its-hijack/SimpleLauncher"

	supportedDevices []string
	debloatGroups    []string
	debloatList      []string
)

// ------------------------------------------------------------------ the lists from SimpleScript.sh

func loadLists() {
	supportedDevices = bashArray(listsSource, "SUPPORTED_DEVICES")
	debloatGroups = bashArray(listsSource, "DEBLOAT_GROUPS")
	debloatList = bashArray(listsSource, "DEBLOAT_LIST")
	if v := bashValue(listsSource, "SCRIPT_VERSION"); v != "" {
		scriptVersion = v
	}
	if v := bashValue(listsSource, "REPO"); v != "" {
		repo = v
		repoURL = "https://github.com/" + v
	}
}

func bashArray(src, name string) []string {
	var out []string
	in := false
	for _, line := range strings.Split(src, "\n") {
		t := strings.TrimSpace(line)
		if !in {
			if t == name+"=(" {
				in = true
			}
			continue
		}
		if t == ")" {
			break
		}
		if strings.HasPrefix(t, "\"") && strings.HasSuffix(t, "\"") && len(t) >= 2 {
			out = append(out, strings.ReplaceAll(t[1:len(t)-1], `\"`, `"`))
		}
	}
	return out
}

func bashValue(src, name string) string {
	m := regexp.MustCompile(`(?m)^` + name + `="([^"]*)"`).FindStringSubmatch(src)
	if m == nil {
		return ""
	}
	return m[1]
}

func fields(s string, n int) []string {
	f := strings.SplitN(s, "|", n)
	for len(f) < n {
		f = append(f, "")
	}
	return f
}

// ------------------------------------------------------------------ looks

var B, D, R, GREEN, RED, YELLOW, BLUE string

func say(s string)     { fmt.Println(s) }
func blank()           { fmt.Println() }
func ok(s string)      { fmt.Printf("  %s✓%s %s\n", GREEN, R, s) }
func bad(s string)     { fmt.Printf("  %s✗%s %s\n", RED, R, s) }
func note(s string)    { fmt.Printf("  %s•%s %s\n", BLUE, R, s) }
func warn(s string)    { fmt.Printf("  %s!%s %s\n", YELLOW, R, s) }
func dim(s string)     { fmt.Printf("%s%s%s\n", D, s, R) }
func step(n, t string) { blank(); fmt.Printf("%s%sStep %s of 4 · %s%s\n", B, BLUE, n, t, R) }
func heading(t string) {
	blank()
	fmt.Printf("%s%s%s\n", B, t, R)
	fmt.Println(strings.Repeat("-", len([]rune(t))))
}

var stdin = bufio.NewReader(os.Stdin)

func readLine(prompt string) string {
	fmt.Print(prompt + " ")
	line, err := stdin.ReadString('\n')
	if err != nil && line == "" {
		blank()
		say("Stopped. Anything not finished was left as it was.")
		exit(0)
	}
	return strings.TrimRight(line, "\r\n")
}

func askYN(q, def string) bool {
	hint := "Y/n"
	if def != "y" {
		hint = "y/N"
	}
	for {
		a := strings.ToLower(strings.TrimSpace(readLine("  " + q + " [" + hint + "]")))
		if a == "" {
			a = def
		}
		switch a {
		case "y", "yes":
			return true
		case "n", "no":
			return false
		}
		say("  Please type y or n.")
	}
}

func pause(msg string) {
	if msg == "" {
		msg = "Press Enter to carry on."
	}
	readLine("  " + msg)
}

// choose returns 1..max, or 0 for q.
func choose(prompt string, max int) int {
	for {
		a := strings.ToLower(strings.TrimSpace(readLine("  " + prompt)))
		if a == "q" {
			return 0
		}
		if n, err := strconv.Atoi(a); err == nil && n >= 1 && n <= max {
			return n
		}
		say(fmt.Sprintf("  Please type a number from 1 to %d, or q to quit.", max))
	}
}

func exit(code int) {
	holdWindowOpen()
	os.Exit(code)
}

// ------------------------------------------------------------------ files and folders

func home() string {
	h, _ := os.UserHomeDir()
	return h
}

func dataDir() string {
	if isWindows {
		if l := os.Getenv("LOCALAPPDATA"); l != "" {
			return filepath.Join(l, "SimpleScript")
		}
	}
	return filepath.Join(home(), ".simplescript")
}

func tidyPath(p string) string {
	h := home()
	if h != "" && strings.HasPrefix(p, h) {
		if isWindows {
			return "%USERPROFILE%" + p[len(h):]
		}
		return "~" + p[len(h):]
	}
	return p
}

func exists(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

// ------------------------------------------------------------------ step 1: ADB

var adb string

func adbName() string {
	if isWindows {
		return "adb.exe"
	}
	return "adb"
}

func findAdb() bool {
	if p := os.Getenv("SIMPLESCRIPT_ADB"); p != "" && exists(p) {
		adb = p
		return true
	}
	if p, err := exec.LookPath("adb"); err == nil {
		adb = p
		return true
	}
	candidates := []string{filepath.Join(dataDir(), "platform-tools", adbName())}
	if isWindows {
		la := os.Getenv("LOCALAPPDATA")
		candidates = append(candidates,
			filepath.Join(la, "Microsoft", "WinGet", "Links", "adb.exe"),
			filepath.Join(la, "Android", "Sdk", "platform-tools", "adb.exe"))
		if m, _ := filepath.Glob(filepath.Join(la, "Microsoft", "WinGet", "Packages", "Google.PlatformTools*", "platform-tools", "adb.exe")); len(m) > 0 {
			candidates = append(candidates, m...)
		}
	} else {
		candidates = append(candidates,
			filepath.Join(home(), "Library", "Android", "sdk", "platform-tools", "adb"),
			filepath.Join(home(), "Android", "Sdk", "platform-tools", "adb"))
	}
	for _, c := range candidates {
		if exists(c) {
			adb = c
			return true
		}
	}
	return false
}

func have(cmd string) bool {
	_, err := exec.LookPath(cmd)
	return err == nil
}

// download saves url to path with a percentage counter.
func download(url, path, label string) error {
	client := &http.Client{Timeout: 10 * time.Minute}
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("User-Agent", "SimpleScript/"+scriptVersion)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	total := resp.ContentLength
	var done int64
	buf := make([]byte, 64*1024)
	last := -1
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				f.Close()
				return werr
			}
			done += int64(n)
			if total > 0 {
				if pct := int(done * 100 / total); pct != last {
					last = pct
					fmt.Printf("\r  %s… %d%%", label, pct)
				}
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			f.Close()
			return rerr
		}
	}
	fmt.Print("\r" + strings.Repeat(" ", len(label)+16) + "\r")
	return f.Close()
}

func unzipTo(zipPath, dest string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		target := filepath.Join(dest, f.Name)
		if !strings.HasPrefix(target, filepath.Clean(dest)+string(os.PathSeparator)) {
			continue // ignore anything that tries to escape the folder
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(target, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(target), 0o755)
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode()|0o600)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		rc.Close()
		out.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

func installAdbZip() bool {
	name := "platform-tools-latest-linux.zip"
	switch runtime.GOOS {
	case "windows":
		name = "platform-tools-latest-windows.zip"
	case "darwin":
		name = "platform-tools-latest-darwin.zip"
	}
	note("Downloading Google's platform tools (about 15 MB)…")
	tmp := filepath.Join(dataDir(), "platform-tools.zip")
	if err := download("https://dl.google.com/android/repository/"+name, tmp, "Downloading"); err != nil {
		bad("The download didn't work. Check this computer is online.")
		os.Remove(tmp)
		return false
	}
	os.RemoveAll(filepath.Join(dataDir(), "platform-tools"))
	if err := unzipTo(tmp, dataDir()); err != nil {
		bad("Couldn't unpack the download.")
		os.Remove(tmp)
		return false
	}
	os.Remove(tmp)
	return findAdb()
}

func runVisible(name string, args ...string) error {
	c := exec.Command(name, args...)
	c.Stdin, c.Stdout, c.Stderr = os.Stdin, os.Stdout, os.Stderr
	return c.Run()
}

func installAdb() bool {
	blank()
	say("  SimpleScript talks to your TV with " + B + "ADB" + R + ", a free tool from Google.")
	say("  It isn't on this computer yet. How would you like to get it?")
	blank()
	var options []string
	n := 0
	if isWindows && have("winget") {
		n++
		options = append(options, "winget")
		say(fmt.Sprintf("  %d) Install it for me with winget          %s(recommended)%s", n, D, R))
	}
	n++
	options = append(options, "zip")
	say(fmt.Sprintf("  %d) Download it from Google for me         %s(kept in %s, nothing else changed)%s", n, D, tidyPath(dataDir()), R))
	n++
	options = append(options, "manual")
	say(fmt.Sprintf("  %d) I'll install it myself", n))
	blank()
	c := choose(fmt.Sprintf("Choose 1-%d (or q to quit):", n), n)
	if c == 0 {
		say("  No problem. Run SimpleScript again whenever you're ready.")
		exit(0)
	}
	switch options[c-1] {
	case "winget":
		note("Running: winget install Google.PlatformTools")
		if err := runVisible("winget", "install", "--id", "Google.PlatformTools", "-e",
			"--accept-source-agreements", "--accept-package-agreements"); err != nil {
			bad("winget couldn't install it.")
		}
	case "zip":
		say("  By downloading, you accept Google's terms for the Android SDK:")
		say("  " + D + "https://developer.android.com/studio/terms" + R)
		if !askYN("Download it now?", "y") {
			say("  OK, nothing downloaded.")
			exit(0)
		}
		installAdbZip()
	case "manual":
		blank()
		say("  1. Download " + B + "SDK Platform-Tools" + R + " for your computer from:")
		say("     " + D + "https://developer.android.com/tools/releases/platform-tools" + R)
		say("  2. Unzip it, and either add the folder to your PATH or move it to:")
		say("     " + D + filepath.Join(dataDir(), "platform-tools") + R)
		pause("Press Enter when that's done.")
	}
	return findAdb()
}

func checkTools() {
	step("1", "Checking this computer")
	switch runtime.GOOS {
	case "windows":
		ok("Windows")
	case "darwin":
		ok("macOS")
	default:
		ok(runtime.GOOS)
	}
	for !findAdb() {
		bad("ADB isn't installed")
		if installAdb() {
			break
		}
		blank()
		if !askYN("ADB still isn't available. Try again?", "y") {
			say("  Run SimpleScript again once ADB is installed.")
			exit(1)
		}
	}
	v, _ := exec.Command(adb, "version").Output()
	first := strings.TrimPrefix(strings.SplitN(strings.TrimSpace(string(v)), "\n", 2)[0], "Android Debug Bridge ")
	ok("ADB ready " + D + "(" + strings.TrimSpace(first) + ")" + R)
	exec.Command(adb, "start-server").Run()
}

// ------------------------------------------------------------------ talking to the TV

var serial string

func adbRun(args ...string) string {
	all := append([]string{"-s", serial}, args...)
	out, _ := exec.Command(adb, all...).CombinedOutput()
	return strings.ReplaceAll(string(out), "\r", "")
}

// sh runs a command in the TV's shell (output only, no errors).
func sh(args ...string) string {
	all := append([]string{"-s", serial, "shell"}, args...)
	out, _ := exec.Command(adb, all...).Output()
	return strings.TrimSpace(strings.ReplaceAll(string(out), "\r", ""))
}

func adbPlain(args ...string) string {
	out, _ := exec.Command(adb, args...).CombinedOutput()
	return strings.ReplaceAll(string(out), "\r", "")
}

func stateOf(s string) string {
	out, _ := exec.Command(adb, "-s", s, "get-state").Output()
	return strings.TrimSpace(strings.ReplaceAll(string(out), "\r", ""))
}

func devicesIn(state string) []string {
	var out []string
	lines := strings.Split(adbPlain("devices"), "\n")
	for _, l := range lines[1:] {
		f := strings.Fields(l)
		if len(f) >= 2 && f[1] == state {
			out = append(out, f[0])
		}
	}
	return out
}

func deviceLabel(s string) string {
	m, _ := exec.Command(adb, "-s", s, "shell", "getprop", "ro.product.model").Output()
	mf, _ := exec.Command(adb, "-s", s, "shell", "getprop", "ro.product.manufacturer").Output()
	clean := func(b []byte) string { return strings.TrimSpace(strings.ReplaceAll(string(b), "\r", "")) }
	return fmt.Sprintf("%s %s %s(%s)%s", clean(mf), clean(m), D, s, R)
}

// ------------------------------------------------------------------ steps 2 and 3

func guideFire() {
	blank()
	say("  On your " + B + "Fire TV" + R + ", using the remote:")
	blank()
	say("  1. Go to " + B + "Settings › My Fire TV › About" + R + ".")
	say("  2. Select your device's name (the first line, e.g. \"Fire TV Stick\") " + B + "7 times" + R)
	say("     until it says you're now a developer. " + D + "(Skip this if Developer options is already there.)" + R)
	say("  3. Press Back, open " + B + "Developer options" + R + " and switch " + B + "ADB debugging" + R + " to " + B + "On" + R + ".")
	say("  4. Go to " + B + "Settings › My Fire TV › About › Network" + R + " and note the " + B + "IP address" + R + ".")
	dim("     It looks like 192.168.1.23")
}

func guideAndroid() {
	blank()
	say("  On your " + B + "Android TV, Google TV or Android device" + R + ":")
	blank()
	say("  1. Go to " + B + "Settings › System › About" + R + " (on phones: " + B + "Settings › About phone" + R + ").")
	say("  2. Select " + B + "Build" + R + " (or " + B + "Build number" + R + ") " + B + "7 times" + R + " until it says you're now a developer.")
	say("  3. Open " + B + "Settings › System › Developer options" + R + " and switch on " + B + "USB debugging" + R + ".")
	say("     " + D + "Some TVs also have \"Network debugging\" or \"ADB over network\": switch that on too." + R)
	say("  4. Find its " + B + "IP address" + R + " under " + B + "Settings › Network & Internet" + R + " (your Wi-Fi network).")
	say("     " + D + "Phone or tablet? You can plug it in with a USB cable instead." + R)
	if isWindows {
		say("     " + D + "(Over USB, Windows may need your phone maker's USB driver.)" + R)
	}
}

func waitForApproval(s string) bool {
	st := stateOf(s)
	if st == "device" {
		return true
	}
	if st == "unauthorized" || st == "authorizing" {
		blank()
		say("  " + B + "Look at your TV:" + R + " it's asking " + B + "\"Allow USB debugging?\"" + R)
		say("  Tick " + B + "Always allow from this computer" + R + ", then select " + B + "OK" + R + ".")
	}
	fmt.Print("  Waiting")
	for t := 0; t < 60; t += 2 {
		if stateOf(s) == "device" {
			fmt.Println()
			return true
		}
		fmt.Print(".")
		time.Sleep(2 * time.Second)
	}
	fmt.Println()
	return false
}

var ipPattern = regexp.MustCompile(`^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?$`)

func connectByIP() {
	for {
		blank()
		ip := strings.ReplaceAll(readLine("  Type your TV's IP address (or q to quit):"), " ", "")
		if ip == "q" {
			exit(0)
		}
		if !ipPattern.MatchString(ip) {
			say("  That doesn't look like an IP address. It's four numbers with dots, e.g. 192.168.1.23")
			continue
		}
		if !strings.Contains(ip, ":") {
			ip += ":5555"
		}
		note("Connecting to " + ip + "…")
		out := strings.ToLower(adbPlain("connect", ip))
		if strings.Contains(out, "connected to") || strings.Contains(out, "already connected") {
			if waitForApproval(ip) {
				serial = ip
				ok("Connected")
				return
			}
			bad("The TV didn't allow the connection.")
			say("  If you missed the message, it may still be on screen. If you selected Cancel,")
			say("  unplug the TV for a moment, or switch ADB debugging off and on, then try again.")
			adbPlain("disconnect", ip)
		} else {
			bad("Couldn't reach the TV at " + ip + ".")
			if strings.Contains(out, "refused") {
				say("  The TV answered but refused. Check " + B + "ADB debugging" + R + " is switched " + B + "On" + R + ".")
			} else {
				say("  Check the IP address, that the TV is switched on, and that this computer is on the same")
				say("  Wi-Fi or network as the TV.")
			}
		}
		if !askYN("Try again?", "y") {
			exit(0)
		}
	}
}

func prepareAndConnect() {
	step("2", "Getting your TV ready")
	existing := devicesIn("device")
	if len(existing) > 0 {
		say("  Already connected:")
		for i, s := range existing {
			say(fmt.Sprintf("  %d) %s", i+1, deviceLabel(s)))
		}
		say(fmt.Sprintf("  %d) Connect to a different device", len(existing)+1))
		blank()
		c := choose(fmt.Sprintf("Which one? (1-%d, q to quit)", len(existing)+1), len(existing)+1)
		if c == 0 {
			exit(0)
		}
		if c <= len(existing) {
			serial = existing[c-1]
			step("3", "Connecting")
			ok("Using " + deviceLabel(serial))
			return
		}
	}

	say("  What are you setting up?")
	say("  1) An Amazon Fire TV " + D + "(Fire TV Stick, Cube, Fire TV smart TV)" + R)
	say("  2) Another Android TV, Google TV, phone or tablet")
	blank()
	kind := choose("Choose 1 or 2:", 2)
	if kind == 0 {
		exit(0)
	}
	if kind == 1 {
		guideFire()
	} else {
		guideAndroid()
	}
	blank()
	pause("Press Enter when ADB debugging is on and you have the IP address.")

	step("3", "Connecting")
	if kind == 2 {
		if d := devicesIn("device"); len(d) > 0 {
			serial = d[0]
			ok("Found " + deviceLabel(serial) + " over USB")
			return
		}
		if u := devicesIn("unauthorized"); len(u) > 0 && waitForApproval(u[0]) {
			serial = u[0]
			ok("Connected over USB")
			return
		}
	}
	connectByIP()
}

// ------------------------------------------------------------------ step 4

var (
	model, maker, deviceName, androidVer string
	sdk, fireMajor                       int
	fireOS                               string
	support                              = "none" // full, app, generic
	supportedName, supportReason         string
	canDebloat, canHomeCatch             bool
)

func fireMajorFromSDK(s int) int {
	switch s {
	case 22:
		return 5
	case 25:
		return 6
	case 28:
		return 7
	case 30:
		return 8
	}
	return 0
}

func detectDevice() {
	step("4", "Checking your device")
	model = sh("getprop", "ro.product.model")
	maker = sh("getprop", "ro.product.manufacturer")
	androidVer = sh("getprop", "ro.build.version.release")
	sdk, _ = strconv.Atoi(sh("getprop", "ro.build.version.sdk"))
	deviceName = sh("settings", "get", "global", "device_name")
	if deviceName == "null" {
		deviceName = ""
	}

	fire := maker == "Amazon" && strings.HasPrefix(model, "AFT")
	if strings.Contains(sh("pm", "list", "features"), "amazon.hardware.fire_tv") {
		fire = true
	}

	if fire {
		fireMajor = fireMajorFromSDK(sdk)
		fireOS = sh("getprop", "ro.build.version.name")
		if fireOS == "" {
			fireOS = fmt.Sprintf("Fire OS %d", fireMajor)
		}
		support = "app"
		supportReason = "This Fire TV hasn't been tested with SimpleScript yet."
		for _, e := range supportedDevices {
			f := fields(e, 5)
			if f[0] != model {
				continue
			}
			if f[2] == strconv.Itoa(fireMajor) {
				support, supportedName, supportReason = "full", f[1], ""
				canDebloat, canHomeCatch = f[3] == "yes", f[4] == "yes"
			} else {
				supportedName = f[1]
				supportReason = fmt.Sprintf("The %s has only been tested on Fire OS %s, and this one runs Fire OS %d.", f[1], f[2], fireMajor)
			}
		}
	} else {
		support = "generic"
	}

	nick := ""
	if deviceName != "" {
		nick = "  ·  \"" + deviceName + "\""
	}
	blank()
	switch {
	case support == "full":
		say("  " + B + supportedName + R + nick)
	case fire:
		say("  " + B + "Amazon Fire TV" + R + nick)
	default:
		say("  " + B + maker + " " + model + R + nick)
	}
	if fire {
		dim(fmt.Sprintf("  Model %s · %s · Android %s", model, fireOS, androidVer))
	} else {
		dim(fmt.Sprintf("  Model %s · Android %s", model, androidVer))
	}
	blank()
	switch support {
	case "full":
		ok("Supported device")
		if canDebloat {
			ok("Debloat and Restore available")
		}
		if canHomeCatch {
			ok("HomeCatch available " + D + "(the Home button opens SimpleLauncher)" + R)
		}
	case "app":
		warn(supportReason)
		note("You can install SimpleLauncher as an app. Debloat and HomeCatch stay off on")
		note("devices that haven't been tested, so nothing can go wrong.")
		note("Want it supported? Tell us the model (" + model + ") at " + repoURL + "/issues")
	case "generic":
		ok("SimpleLauncher can be your home screen on this device")
		note("Debloat and HomeCatch are only for supported Fire TV devices.")
	}
	if sdk > 0 && sdk < 25 {
		blank()
		bad("SimpleLauncher needs Android 7.1 or newer, and this device runs Android " + androidVer + ".")
		say("  SimpleScript can't set it up, sorry.")
		exit(1)
	}
}

// ------------------------------------------------------------------ helpers on the TV

func packageSet(args ...string) map[string]bool {
	set := map[string]bool{}
	for _, l := range strings.Split(sh(append([]string{"pm", "list", "packages"}, args...)...), "\n") {
		if p := strings.TrimPrefix(strings.TrimSpace(l), "package:"); p != "" {
			set[p] = true
		}
	}
	return set
}

func slInstalled() bool { return packageSet()[slPkg] }

func slVersion() string {
	for _, l := range strings.Split(sh("dumpsys", "package", slPkg), "\n") {
		if i := strings.Index(l, "versionName="); i >= 0 {
			return strings.TrimSpace(l[i+len("versionName="):])
		}
	}
	return ""
}

func animations(scale string) {
	for _, k := range []string{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"} {
		sh("settings", "put", "global", k, scale)
	}
}

func homeCatchOn() bool {
	cur := sh("settings", "get", "secure", "enabled_accessibility_services")
	next := slService
	if cur != "" && cur != "null" {
		next = cur
		if !strings.Contains(cur, slService) {
			next = cur + ":" + slService
		}
	}
	sh("settings put secure enabled_accessibility_services '" + next + "'")
	sh("settings", "put", "secure", "accessibility_enabled", "1")
	return strings.Contains(sh("settings", "get", "secure", "enabled_accessibility_services"), slService)
}

func homeCatchOff() {
	cur := sh("settings", "get", "secure", "enabled_accessibility_services")
	var keep []string
	for _, s := range strings.Split(cur, ":") {
		if s != "" && s != "null" && s != slService {
			keep = append(keep, s)
		}
	}
	if len(keep) == 0 {
		sh("settings", "delete", "secure", "enabled_accessibility_services")
		sh("settings", "put", "secure", "accessibility_enabled", "0")
	} else {
		sh("settings put secure enabled_accessibility_services '" + strings.Join(keep, ":") + "'")
	}
}

func offerRestart() {
	blank()
	if askYN("Restart the TV now so everything takes effect?", "y") {
		adbRun("reboot")
		ok("Restarting. It'll be back in about a minute.")
		say("  " + D + "SimpleScript will need to reconnect afterwards. Run it again if you want to do more." + R)
		exit(0)
	}
}

func choicesFile() string { return filepath.Join(dataDir(), "choices-"+model+".txt") }

func loadChoices() map[string]string {
	m := map[string]string{}
	b, err := os.ReadFile(choicesFile())
	if err != nil {
		return m
	}
	for _, l := range strings.Split(string(b), "\n") {
		if k, v, found := strings.Cut(strings.TrimSpace(l), "="); found {
			m[k] = v
		}
	}
	return m
}

func saveChoices(decisions [][4]string) {
	m := loadChoices()
	for _, d := range decisions {
		m[d[0]] = d[1]
	}
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var sb strings.Builder
	for _, k := range keys {
		sb.WriteString(k + "=" + m[k] + "\n")
	}
	os.MkdirAll(dataDir(), 0o755)
	os.WriteFile(choicesFile(), []byte(sb.String()), 0o644)
}

// ------------------------------------------------------------------ debloat

func debloat() {
	heading("Debloat")
	say("  I'll go through what's on your TV. " + B + "y" + R + " switches it off, " + B + "n" + R + " keeps it.")
	say("  Nothing is deleted, and " + B + "Restore" + R + " switches everything back on.")

	present := packageSet()
	disabled := packageSet("-d")
	var items [][]string
	for _, line := range debloatList {
		f := fields(line, 5)
		if present[f[1]] {
			items = append(items, f)
		}
	}
	if len(items) == 0 {
		ok("Nothing from the list is on this TV. It's already clean.")
		return
	}

	saved := loadChoices()
	_, statErr := os.Stat(choicesFile())
	haveSaved := statErr == nil
	newCount := 0
	for _, f := range items {
		if _, found := saved[f[1]]; !found {
			newCount++
		}
	}

	blank()
	say("  How would you like to choose?")
	var modes []string
	n := 1
	modes = append(modes, "recommended")
	say("  1) Recommended " + D + "(switch off adverts, tracking, pop-ups and forced updates; keep apps and Alexa)" + R)
	if haveSaved {
		n++
		modes = append(modes, "saved")
		if newCount > 0 {
			say(fmt.Sprintf("  %d) Same as last time %s(and ask me about the %d new item(s))%s", n, D, newCount, R))
		} else {
			say(fmt.Sprintf("  %d) Same as last time", n))
		}
	}
	n++
	modes = append(modes, "each")
	say(fmt.Sprintf("  %d) Let me choose each one", n))
	blank()
	c := choose(fmt.Sprintf("Choose 1-%d (or q to go back):", n), n)
	if c == 0 {
		return
	}
	mode := modes[c-1]

	var decisions [][4]string // package, y/n, how, description
	for _, g := range debloatGroups {
		gf := fields(g, 3)
		shown := false
		for _, f := range items {
			grp, pkg, desc, rec, how := f[0], f[1], f[2], f[3], f[4]
			if grp != gf[0] {
				continue
			}
			last, hasLast := saved[pkg]
			isNew := !hasLast && haveSaved
			def := rec
			if hasLast {
				def = last
			}
			answer := ""
			switch mode {
			case "recommended":
				answer = rec
			case "saved":
				answer = last
			}
			if answer == "" {
				if !shown {
					blank()
					fmt.Printf("  %s%s%s\n", B, gf[1], R)
					dim("  " + gf[2])
					shown = true
				}
				extra := ""
				if disabled[pkg] {
					extra += " " + D + "(off now)" + R
				}
				if isNew {
					extra += " " + YELLOW + "NEW" + R
				}
				verb := "Switch off"
				if how == "uninstall" {
					verb = "Remove"
				}
				if askYN(verb+" "+B+desc+R+"?"+extra, def) {
					answer = "y"
				} else {
					answer = "n"
				}
			}
			decisions = append(decisions, [4]string{pkg, answer, how, desc})
		}
	}

	var toOff, toOn, toRemove [][4]string
	for _, d := range decisions {
		switch {
		case d[2] == "uninstall":
			if d[1] == "y" {
				toRemove = append(toRemove, d)
			}
		case d[1] == "y" && !disabled[d[0]]:
			toOff = append(toOff, d)
		case d[1] == "n" && disabled[d[0]]:
			toOn = append(toOn, d)
		}
	}

	blank()
	faster := askYN("Also make menus feel faster "+D+"(half-length animations)"+R+"?", "y")

	heading("Here's the plan")
	list := func(title, mark string, ds [][4]string) {
		if len(ds) == 0 {
			return
		}
		say(fmt.Sprintf("  %s (%d):", title, len(ds)))
		for _, d := range ds {
			say("    " + mark + " " + d[3])
		}
	}
	list("Switch off", "-", toOff)
	list("Switch back on", "+", toOn)
	list("Remove", "x", toRemove)
	if faster {
		say("  Faster animations")
	}
	if len(toOff)+len(toOn)+len(toRemove) == 0 && !faster {
		say("  Nothing to change. Your TV already matches your choices.")
		saveChoices(decisions)
		return
	}
	blank()
	if !askYN("Go ahead?", "y") {
		say("  OK, nothing was changed.")
		return
	}

	blank()
	fails := 0
	for _, d := range toOff {
		if strings.Contains(sh("pm", "disable-user", "--user", "0", d[0]), "disabled") {
			ok("Off: " + d[3])
		} else {
			bad("Fire OS refused: " + d[3])
			fails++
		}
	}
	for _, d := range toOn {
		if strings.Contains(sh("pm", "enable", d[0]), "enabled") {
			ok("On: " + d[3])
		} else {
			bad("Couldn't switch on: " + d[3])
			fails++
		}
	}
	for _, d := range toRemove {
		if strings.Contains(adbRun("uninstall", d[0]), "Success") {
			ok("Removed: " + d[3])
		} else {
			bad("Couldn't remove: " + d[3])
			fails++
		}
	}
	if faster {
		animations("0.5")
		ok("Faster animations")
	}
	sh("pm", "trim-caches", "999G")
	saveChoices(decisions)
	blank()
	if fails == 0 {
		ok("Debloat finished.")
	} else {
		warn(fmt.Sprintf("Debloat finished with %d item(s) Fire OS wouldn't change.", fails))
	}
	offerRestart()
}

// ------------------------------------------------------------------ restore

func restore() {
	heading("Restore to stock")
	say("  This switches back on everything SimpleScript can switch off, puts the animation")
	say("  speed back to normal and gives the Home button back to Amazon.")
	blank()
	if !askYN("Restore your TV?", "y") {
		say("  OK, nothing was changed.")
		return
	}
	disabled := packageSet("-d")
	blank()
	count := 0
	for _, line := range debloatList {
		f := fields(line, 5)
		if f[4] != "disable" || !disabled[f[1]] {
			continue
		}
		if strings.Contains(sh("pm", "enable", f[1]), "enabled") {
			ok("On: " + f[2])
			count++
		} else {
			bad("Couldn't switch on: " + f[2])
		}
	}
	if count == 0 {
		ok("Nothing was switched off")
	}
	animations("1.0")
	ok("Normal animation speed")
	if slInstalled() {
		homeCatchOff()
		ok("HomeCatch off " + D + "(the Home button opens Amazon's home screen)" + R)
		blank()
		if askYN("Also uninstall SimpleLauncher?", "n") {
			if strings.Contains(adbRun("uninstall", slPkg), "Success") {
				ok("SimpleLauncher removed")
			} else {
				bad("Couldn't remove SimpleLauncher")
			}
		}
	}
	os.Remove(choicesFile())
	dim("  Apps you removed yourself (like Downloader) aren't reinstalled. Get them from the Appstore.")
	offerRestart()
}

// ------------------------------------------------------------------ install SimpleLauncher

var apk string

func downloadLatest() bool {
	note("Asking GitHub for the latest SimpleLauncher…")
	req, _ := http.NewRequest("GET", "https://api.github.com/repos/"+repo+"/releases/latest", nil)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "SimpleScript/"+scriptVersion)
	resp, err := (&http.Client{Timeout: 30 * time.Second}).Do(req)
	if err != nil {
		bad("Couldn't reach GitHub. Check this computer is online.")
		return false
	}
	defer resp.Body.Close()
	var rel struct {
		Tag    string `json:"tag_name"`
		Assets []struct {
			Name string `json:"name"`
			URL  string `json:"browser_download_url"`
		} `json:"assets"`
	}
	if resp.StatusCode != 200 || json.NewDecoder(resp.Body).Decode(&rel) != nil {
		bad("Couldn't read the latest release from GitHub.")
		return false
	}
	url, name := "", ""
	for _, a := range rel.Assets {
		if strings.HasSuffix(strings.ToLower(a.Name), ".apk") {
			url, name = a.URL, a.Name
			break
		}
	}
	if url == "" {
		bad("The latest release doesn't have an APK attached.")
		return false
	}
	apk = filepath.Join(dataDir(), "downloads", name)
	if err := download(url, apk, "Downloading build "+rel.Tag); err != nil {
		bad("The download didn't finish.")
		os.Remove(apk)
		return false
	}
	ok("Downloaded build " + rel.Tag)
	return true
}

func newestLocalAPK() string {
	h := home()
	var patterns []string
	for _, desk := range []string{filepath.Join(h, "Desktop"), filepath.Join(h, "OneDrive", "Desktop")} {
		patterns = append(patterns,
			filepath.Join(desk, "SimpleLauncher-*.apk"),
			filepath.Join(desk, "SimpleLauncher", "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
			filepath.Join(desk, "SimpleLauncher", "app", "build", "outputs", "apk", "release", "app-release.apk"))
	}
	patterns = append(patterns, filepath.Join(h, "Downloads", "SimpleLauncher-*.apk"))
	best, bestTime := "", time.Time{}
	for _, p := range patterns {
		matches, _ := filepath.Glob(p)
		for _, m := range matches {
			if st, err := os.Stat(m); err == nil && st.ModTime().After(bestTime) {
				best, bestTime = m, st.ModTime()
			}
		}
	}
	return best
}

func pickLocalAPK() bool {
	if found := newestLocalAPK(); found != "" {
		say("  Found: " + B + tidyPath(found) + R)
		if askYN("Use this file?", "y") {
			apk = found
			return true
		}
	}
	for {
		say("  Drag the APK file into this window, or type its path, then press Enter.")
		p := strings.TrimSpace(readLine("  File:"))
		p = strings.Trim(p, "\"'")
		if !isWindows {
			p = strings.ReplaceAll(p, `\ `, " ")
		}
		if strings.HasPrefix(p, "~") {
			p = home() + p[1:]
		}
		if exists(p) {
			apk = p
			return true
		}
		bad("There's no file at that path.")
		if !askYN("Try again?", "y") {
			return false
		}
	}
}

func installLauncher() {
	heading("Install SimpleLauncher")
	if slInstalled() {
		note("SimpleLauncher " + slVersion() + " is installed. This updates it and keeps your settings.")
	}
	blank()
	say("  Where should SimpleLauncher come from?")
	say("  1) The latest release on GitHub " + D + "(recommended)" + R)
	say("  2) An APK file on this computer")
	blank()
	c := choose("Choose 1 or 2 (or q to go back):", 2)
	if c == 0 {
		return
	}
	if c == 1 && !downloadLatest() || c == 2 && !pickLocalAPK() {
		return
	}

	blank()
	note("Installing on your TV…")
	out := adbRun("install", "-r", apk)
	if !strings.Contains(out, "Success") {
		if strings.Contains(out, "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
			bad("This copy is signed differently from the SimpleLauncher already on your TV.")
			say("  To switch, the old one has to be removed first. " + B + "This clears its settings." + R)
			if !askYN("Remove the old SimpleLauncher and install this one?", "n") {
				return
			}
			adbRun("uninstall", slPkg)
			out = adbRun("install", apk)
		} else if strings.Contains(out, "INSTALL_FAILED_VERSION_DOWNGRADE") {
			bad("Your TV already has a newer SimpleLauncher than this file.")
			return
		}
	}
	if !strings.Contains(out, "Success") {
		bad("The install didn't work:")
		lines := strings.Split(strings.TrimSpace(out), "\n")
		dim("  " + lines[len(lines)-1])
		return
	}
	ok("SimpleLauncher " + slVersion() + " installed")

	if sdk >= 26 {
		if sh("appops", "set", slPkg, "REQUEST_INSTALL_PACKAGES", "allow") == "" {
			ok("Software update allowed " + D + "(Settings › Information › Software update)" + R)
		} else {
			warn("Couldn't allow Software update. SimpleLauncher will ask on the TV the first time.")
		}
	}

	switch support {
	case "full":
		if canHomeCatch {
			blank()
			say("  " + B + "HomeCatch" + R + " makes the Home button on your remote open SimpleLauncher")
			say("  instead of Amazon's home screen. You can switch it off any time in")
			say("  SimpleLauncher's Settings › Danger Zone.")
			if askYN("Switch HomeCatch on?", "y") {
				sh("pm", "grant", slPkg, "android.permission.WRITE_SECURE_SETTINGS")
				if homeCatchOn() {
					ok("HomeCatch on")
				} else {
					bad("Fire OS didn't accept HomeCatch")
				}
			}
		}
	case "app":
		note("HomeCatch stays off on this Fire TV because it hasn't been tested yet.")
		note("Open SimpleLauncher from " + B + "Your Apps" + R + " on the Fire TV home screen.")
	case "generic":
		blank()
		say("  To make SimpleLauncher your home screen, choose it on the TV:")
		sh("am", "start", "-a", "android.settings.HOME_SETTINGS")
		say("  " + B + "Settings › Apps › Default apps › Home app › SimpleLauncher" + R)
		dim("  (I've tried to open that screen for you. Some TVs keep it somewhere else.)")
	}
	sh("am", "start", "-n", slPkg+"/.MainActivity")
	blank()
	ok("All done. SimpleLauncher is open on your TV.")
}

func uninstallLauncher() {
	heading("Remove SimpleLauncher")
	if !askYN("Uninstall SimpleLauncher and its settings from this device?", "n") {
		return
	}
	homeCatchOff()
	if strings.Contains(adbRun("uninstall", slPkg), "Success") {
		ok("SimpleLauncher removed")
	} else {
		bad("Couldn't remove it")
	}
}

// ------------------------------------------------------------------ menu

func menu() {
	for {
		heading("What would you like to do?")
		var actions []string
		add := func(action, label string) {
			actions = append(actions, action)
			say(fmt.Sprintf("  %d) %s", len(actions), label))
		}
		installed := slInstalled()
		if installed {
			add("install", "Update SimpleLauncher "+D+"(installed: "+slVersion()+")"+R)
		} else {
			add("install", "Install SimpleLauncher")
		}
		if support == "full" && canDebloat {
			add("debloat", "Debloat "+D+"(choose what to switch off)"+R)
			add("restore", "Restore to stock")
		} else if installed {
			add("uninstall", "Remove SimpleLauncher")
		}
		add("reboot", "Restart the TV")
		say("  q) Quit")
		switch support {
		case "app":
			dim("  Debloat isn't offered because this Fire TV hasn't been tested with SimpleScript.")
		case "generic":
			dim("  Debloat is only for supported Fire TV devices.")
		}
		blank()
		c := choose(fmt.Sprintf("Choose 1-%d:", len(actions)), len(actions))
		if c == 0 {
			break
		}
		switch actions[c-1] {
		case "install":
			installLauncher()
		case "debloat":
			debloat()
		case "restore":
			restore()
		case "uninstall":
			uninstallLauncher()
		case "reboot":
			adbRun("reboot")
			ok("Restarting your TV.")
			blank()
			say("  Thanks for using SimpleScript. " + D + "Problems or ideas: " + repoURL + "/issues" + R)
			exit(0)
		}
	}
	blank()
	say("  Thanks for using SimpleScript. " + D + "Problems or ideas: " + repoURL + "/issues" + R)
}

// ------------------------------------------------------------------ start

func main() {
	if setupConsole() {
		B, D, R = "\033[1m", "\033[2m", "\033[0m"
		GREEN, RED, YELLOW, BLUE = "\033[32m", "\033[31m", "\033[33m", "\033[36m"
	}
	loadLists()

	if len(os.Args) > 1 && (os.Args[1] == "-h" || os.Args[1] == "--help" || os.Args[1] == "/?") {
		say("SimpleScript " + scriptVersion + ". Run it with no options and it walks you through everything.")
		return
	}

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	go func() {
		<-stop
		blank()
		say("Stopped. Anything not finished was left as it was.")
		os.Exit(130)
	}()

	blank()
	say(B + "SimpleScript" + R + " " + D + "v" + scriptVersion + " · by Cherry Universe" + R)
	say("Sets up " + B + "SimpleLauncher" + R + " on your TV, one step at a time.")
	dim("Press Ctrl+C at any point to stop.")

	checkTools()
	prepareAndConnect()
	detectDevice()
	menu()
	holdWindowOpen()
}
