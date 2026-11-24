# =====================================================================================
#  SimpleScript for Windows, by Cherry Universe
#  Sets up SimpleLauncher on your TV, step by step: gets the tools your PC needs,
#  connects to your TV, then installs SimpleLauncher. Supported devices get extras.
#
#  Run it: right-click SimpleScript.ps1 and choose "Run with PowerShell".
#  Or in PowerShell:  powershell -ExecutionPolicy Bypass -File .\SimpleScript.ps1
#
#  This file is made from windows/SimpleScript.template.ps1 and the lists in
#  SimpleScript.sh by windows/build.sh. Edit those, not this file.
# =====================================================================================

$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }
$ProgressPreference = 'SilentlyContinue'   # makes downloads much faster in Windows PowerShell

$SlPkg = 'com.simplehome.tv'
$SlService = 'com.simplehome.tv/com.simplehome.tv.HomeButtonService'
$DataDir = Join-Path $env:LOCALAPPDATA 'SimpleScript'

# @@LISTS@@

# =====================================================================================
#  Looks
# =====================================================================================
$Fancy = [bool]$env:WT_SESSION              # Windows Terminal can show ticks; the old console can't
$TickMark = if ($Fancy) { [string][char]0x2713 } else { 'OK' }
$CrossMark = if ($Fancy) { [string][char]0x2717 } else { 'X' }
$Arrow = [string][char]0x203A
$Dot = [string][char]0x00B7

function Say([string]$t = '') { Write-Host $t }
function Blank { Write-Host '' }
function Ok([string]$t) { Write-Host "  $TickMark " -ForegroundColor Green -NoNewline; Write-Host $t }
function Bad([string]$t) { Write-Host "  $CrossMark " -ForegroundColor Red -NoNewline; Write-Host $t }
function Note([string]$t) { Write-Host '  * ' -ForegroundColor Cyan -NoNewline; Write-Host $t }
function Warn([string]$t) { Write-Host '  ! ' -ForegroundColor Yellow -NoNewline; Write-Host $t }
function Dim([string]$t) { Write-Host $t -ForegroundColor DarkGray }
function Heading([string]$t) { Blank; Write-Host $t -ForegroundColor White; Write-Host ('-' * $t.Length) }
function Step([int]$n, [string]$t) { Blank; Write-Host "Step $n of 4 $Dot $t" -ForegroundColor Cyan }

function Finish([int]$code = 0) {
    Blank
    Read-Host '  Press Enter to close this window' | Out-Null
    exit $code
}

function AskYN([string]$q, [string]$def = 'y') {
    $hint = if ($def -eq 'y') { 'Y/n' } else { 'y/N' }
    while ($true) {
        $a = (Read-Host "  $q [$hint]").Trim().ToLower()
        if ($a -eq '') { $a = $def }
        if ($a -eq 'y' -or $a -eq 'yes') { return $true }
        if ($a -eq 'n' -or $a -eq 'no') { return $false }
        Say '  Please type y or n.'
    }
}

function Pause-Here([string]$msg = 'Press Enter to carry on.') { Read-Host "  $msg" | Out-Null }

# Returns 1..max, or 0 for q.
function Choose([string]$prompt, [int]$max) {
    while ($true) {
        $a = (Read-Host "  $($prompt.TrimEnd(':'))").Trim().ToLower()   # Read-Host adds its own ": "
        if ($a -eq 'q') { return 0 }
        $n = 0
        if ([int]::TryParse($a, [ref]$n) -and $n -ge 1 -and $n -le $max) { return $n }
        Say "  Please type a number from 1 to $max, or q to quit."
    }
}

# =====================================================================================
#  Step 1: the tools this PC needs
# =====================================================================================
$script:Adb = $null

function Find-Adb {
    if ($env:SIMPLESCRIPT_ADB -and (Test-Path $env:SIMPLESCRIPT_ADB)) { $script:Adb = $env:SIMPLESCRIPT_ADB; return $true }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $script:Adb = $cmd.Source; return $true }
    $candidates = @(
        (Join-Path $DataDir 'platform-tools\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    )
    $winget = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages') -Directory -Filter 'Google.PlatformTools*' -ErrorAction SilentlyContinue
    foreach ($w in $winget) { $candidates += (Join-Path $w.FullName 'platform-tools\adb.exe') }
    foreach ($c in $candidates) { if (Test-Path $c) { $script:Adb = $c; return $true } }
    return $false
}

function Install-AdbZip {
    Note "Downloading Google's platform tools (about 15 MB)..."
    New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
    $zip = Join-Path $DataDir 'platform-tools.zip'
    try {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
    } catch {
        Bad "The download didn't work. Check this PC is online."
        Remove-Item $zip -ErrorAction SilentlyContinue
        return $false
    }
    Remove-Item (Join-Path $DataDir 'platform-tools') -Recurse -Force -ErrorAction SilentlyContinue
    try {
        Expand-Archive -Path $zip -DestinationPath $DataDir -Force
    } catch {
        Bad "Couldn't unpack the download."
        return $false
    } finally {
        Remove-Item $zip -ErrorAction SilentlyContinue
    }
    return (Find-Adb)
}

function Install-Adb {
    Blank
    Say '  SimpleScript talks to your TV with ADB, a free tool from Google.'
    Say "  It isn't on this PC yet. How would you like to get it?"
    Blank
    $options = @()
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        $options += 'winget'; Say "  $($options.Count)) Install it for me with winget          (recommended)"
    }
    $options += 'zip'; Say "  $($options.Count)) Download it from Google for me         (kept in $DataDir, nothing else changed)"
    $options += 'manual'; Say "  $($options.Count)) I'll install it myself"
    Blank
    $c = Choose "Choose 1-$($options.Count) (or q to quit):" $options.Count
    if ($c -eq 0) { Say "  No problem. Run SimpleScript again whenever you're ready."; Finish 0 }
    switch ($options[$c - 1]) {
        'winget' {
            Note 'Running: winget install Google.PlatformTools'
            & winget install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements
        }
        'zip' {
            Say "  By downloading, you accept Google's terms for the Android SDK:"
            Dim '  https://developer.android.com/studio/terms'
            if (-not (AskYN 'Download it now?' 'y')) { Say '  OK, nothing downloaded.'; Finish 0 }
            Install-AdbZip | Out-Null
        }
        'manual' {
            Blank
            Say '  1. Download SDK Platform-Tools for Windows from:'
            Dim '     https://developer.android.com/tools/releases/platform-tools'
            Say '  2. Unzip it, and either add the folder to your PATH or move it to:'
            Dim "     $(Join-Path $DataDir 'platform-tools')"
            Pause-Here "Press Enter when that's done."
        }
    }
    return (Find-Adb)
}

function Check-Tools {
    Step 1 'Checking this PC'
    Ok 'Windows'
    while (-not (Find-Adb)) {
        Bad "ADB isn't installed"
        if (Install-Adb) { break }
        Blank
        if (-not (AskYN "ADB still isn't available. Try again?" 'y')) { Say '  Run SimpleScript again once ADB is installed.'; Finish 1 }
    }
    $v = (& $script:Adb version 2>$null | Select-Object -First 1) -replace 'Android Debug Bridge ', ''
    Ok "ADB ready ($v)"
    & $script:Adb start-server 2>$null | Out-Null
}

# =====================================================================================
#  Talking to the TV
# =====================================================================================
$script:Serial = $null

# adb never gets the keyboard ($null is piped in), so it can't swallow your answers.
function AdbRaw { ($null | & $script:Adb @args 2>&1 | ForEach-Object { "$_" }) -join "`n" -replace "`r", '' }
function A { AdbRaw -s $script:Serial @args }
function S { ($null | & $script:Adb -s $script:Serial shell @args 2>$null | ForEach-Object { "$_" }) -join "`n" -replace "`r", '' }

function StateOf([string]$s) { (($null | & $script:Adb -s $s get-state 2>$null) -join '').Trim() }

function DevicesIn([string]$state) {
    $out = @()
    foreach ($l in ((AdbRaw devices) -split "`n" | Select-Object -Skip 1)) {
        $f = $l.Trim() -split '\s+'
        if ($f.Count -ge 2 -and $f[1] -eq $state) { $out += $f[0] }
    }
    return , $out
}

function DeviceLabel([string]$s) {
    $m = (($null | & $script:Adb -s $s shell getprop ro.product.model 2>$null) -join '').Trim()
    $mf = (($null | & $script:Adb -s $s shell getprop ro.product.manufacturer 2>$null) -join '').Trim()
    return "$mf $m ($s)"
}

# =====================================================================================
#  Steps 2 and 3: switching on ADB debugging, and connecting
# =====================================================================================
function Guide-Fire {
    Blank
    Say '  On your Fire TV, using the remote:'
    Blank
    Say "  1. Go to Settings $Arrow My Fire TV $Arrow About."
    Say '  2. Select your device''s name (the first line, e.g. "Fire TV Stick") 7 times'
    Say "     until it says you're now a developer. (Skip this if Developer options is already there.)"
    Say '  3. Press Back, open Developer options and switch ADB debugging to On.'
    Say "  4. Go to Settings $Arrow My Fire TV $Arrow About $Arrow Network and note the IP address."
    Dim '     It looks like 192.168.1.23'
}

function Guide-Android {
    Blank
    Say '  On your Android TV, Google TV or Android device:'
    Blank
    Say "  1. Go to Settings $Arrow System $Arrow About (on phones: Settings $Arrow About phone)."
    Say "  2. Select Build (or Build number) 7 times until it says you're now a developer."
    Say "  3. Open Settings $Arrow System $Arrow Developer options and switch on USB debugging."
    Dim '     Some TVs also have "Network debugging" or "ADB over network": switch that on too.'
    Say "  4. Find its IP address under Settings $Arrow Network & Internet (your Wi-Fi network)."
    Dim "     Phone or tablet? You can plug it in with a USB cable instead (Windows may need its maker's USB driver)."
}

function Wait-ForApproval([string]$s) {
    $st = StateOf $s
    if ($st -eq 'device') { return $true }
    if ($st -eq 'unauthorized' -or $st -eq 'authorizing') {
        Blank
        Say '  Look at your TV: it''s asking "Allow USB debugging?"'
        Say '  Tick "Always allow from this computer", then select OK.'
    }
    Write-Host '  Waiting' -NoNewline
    for ($t = 0; $t -lt 60; $t += 2) {
        if ((StateOf $s) -eq 'device') { Write-Host ''; return $true }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 2
    }
    Write-Host ''
    return $false
}

function Connect-ByIp {
    while ($true) {
        Blank
        $ip = (Read-Host "  Type your TV's IP address (or q to quit)").Replace(' ', '')
        if ($ip -eq 'q') { Finish 0 }
        if ($ip -notmatch '^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?$') {
            Say "  That doesn't look like an IP address. It's four numbers with dots, e.g. 192.168.1.23"
            continue
        }
        if ($ip -notmatch ':') { $ip = "${ip}:5555" }
        Note "Connecting to $ip..."
        $out = (AdbRaw connect $ip).ToLower()
        if ($out -match 'connected to|already connected') {
            if (Wait-ForApproval $ip) { $script:Serial = $ip; Ok 'Connected'; return }
            Bad "The TV didn't allow the connection."
            Say '  If you missed the message, it may still be on screen. If you selected Cancel,'
            Say '  unplug the TV for a moment, or switch ADB debugging off and on, then try again.'
            AdbRaw disconnect $ip | Out-Null
        } else {
            Bad "Couldn't reach the TV at $ip."
            if ($out -match 'refused') {
                Say '  The TV answered but refused. Check ADB debugging is switched On.'
            } else {
                Say '  Check the IP address, that the TV is switched on, and that this PC is on the same'
                Say '  Wi-Fi or network as the TV.'
            }
        }
        if (-not (AskYN 'Try again?' 'y')) { Finish 0 }
    }
}

function Prepare-AndConnect {
    Step 2 'Getting your TV ready'
    $existing = DevicesIn 'device'
    if ($existing.Count -gt 0) {
        Say '  Already connected:'
        for ($i = 0; $i -lt $existing.Count; $i++) { Say "  $($i + 1)) $(DeviceLabel $existing[$i])" }
        Say "  $($existing.Count + 1)) Connect to a different device"
        Blank
        $c = Choose "Which one? (1-$($existing.Count + 1), q to quit)" ($existing.Count + 1)
        if ($c -eq 0) { Finish 0 }
        if ($c -le $existing.Count) {
            $script:Serial = $existing[$c - 1]
            Step 3 'Connecting'
            Ok "Using $(DeviceLabel $script:Serial)"
            return
        }
    }
    Say '  What are you setting up?'
    Say '  1) An Amazon Fire TV (Fire TV Stick, Cube, Fire TV smart TV)'
    Say '  2) Another Android TV, Google TV, phone or tablet'
    Blank
    $kind = Choose 'Choose 1 or 2:' 2
    if ($kind -eq 0) { Finish 0 }
    if ($kind -eq 1) { Guide-Fire } else { Guide-Android }
    Blank
    Pause-Here 'Press Enter when ADB debugging is on and you have the IP address.'

    Step 3 'Connecting'
    if ($kind -eq 2) {
        $d = DevicesIn 'device'
        if ($d.Count -gt 0) { $script:Serial = $d[0]; Ok "Found $(DeviceLabel $script:Serial) over USB"; return }
        $u = DevicesIn 'unauthorized'
        if ($u.Count -gt 0 -and (Wait-ForApproval $u[0])) { $script:Serial = $u[0]; Ok 'Connected over USB'; return }
    }
    Connect-ByIp
}

# =====================================================================================
#  Step 4: what is it, and what's it allowed
# =====================================================================================
$script:Model = ''; $script:Sdk = 0; $script:Support = 'none'
$script:SupportedName = ''; $script:CanDebloat = $false; $script:CanHomeCatch = $false; $script:FireMajor = 0; $script:IsFire = $false; $script:Forcing = $false; $script:GoogleTv = $false

function FireMajor([int]$sdk) {
    switch ($sdk) { 22 { return 5 } 25 { return 6 } 28 { return 7 } 30 { return 8 } default { return 0 } }
}

function Detect-Device {
    Step 4 'Checking your device'
    $script:Model = S getprop ro.product.model
    $maker = S getprop ro.product.manufacturer
    $android = S getprop ro.build.version.release
    $sdkText = S getprop ro.build.version.sdk
    $n = 0; if ([int]::TryParse($sdkText, [ref]$n)) { $script:Sdk = $n }
    $name = S settings get global device_name
    if ($name -eq 'null') { $name = '' }

    $fire = ($maker -eq 'Amazon' -and $script:Model -like 'AFT*') -or ((S pm list features) -match 'amazon.hardware.fire_tv')
    $script:IsFire = $fire   # kept for the menu (Extras are for Fire TVs)
    $reason = ''
    $fireOs = ''
    if ($fire) {
        $major = FireMajor $script:Sdk
        $fireOs = S getprop ro.build.version.name
        if (-not $fireOs) { $fireOs = "Fire OS $major" }
        $script:Support = 'app'
        $reason = "This Fire TV hasn't been tested with SimpleScript yet."
        foreach ($e in $SupportedDevices) {
            $f = $e -split '\|'
            if ($f[0] -ne $script:Model) { continue }
            if ($f[2] -eq "$major") {
                $script:Support = 'full'; $script:SupportedName = $f[1]
                $script:CanDebloat = ($f[3] -eq 'yes'); $script:CanHomeCatch = ($f[4] -eq 'yes'); $reason = ''
            } else {
                $script:SupportedName = $f[1]
                $reason = "The $($f[1]) has only been tested on Fire OS $($f[2]), and this one runs Fire OS $major."
            }
        }
        if ($script:Support -eq 'app') {
            foreach ($e in $TestingDevices) {
                $f = $e -split '\|'
                if ($f[0] -eq $script:Model -and ($f[2] -eq "$major" -or $f[2] -eq '*')) {
                    $script:Support = 'testing'; $script:SupportedName = $f[1]
                    $script:CanHomeCatch = $true; $script:CanDebloat = $false; $reason = ''
                }
            }
        }
        $script:FireMajor = $major
    } else {
        $script:Support = 'generic'
        # Google TV / Android TV ignore the home app setting, so HomeCatch (being tested) does the job.
        $pk = PackageSet
        foreach ($g in @('com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher', 'com.google.android.leanbacklauncher')) {
            if ($pk.ContainsKey($g)) { $script:GoogleTv = $true; $script:CanHomeCatch = $true }
        }
        # Android TV sets with the TV maker's own home screen (leanback) count too.
        if ((S pm list features) -match 'android\.software\.leanback') { $script:GoogleTv = $true; $script:CanHomeCatch = $true }
    }

    $nick = if ($name) { "  $Dot  `"$name`"" } else { '' }
    Blank
    if ($script:Support -eq 'full' -or $script:Support -eq 'testing') { Write-Host "  $($script:SupportedName)$nick" -ForegroundColor White }
    elseif ($fire) { Write-Host "  Amazon Fire TV$nick" -ForegroundColor White }
    else { Write-Host "  $maker $($script:Model)$nick" -ForegroundColor White }
    if ($fire) { Dim "  Model $($script:Model) $Dot $fireOs $Dot Android $android" } else { Dim "  Model $($script:Model) $Dot Android $android" }
    Blank
    switch ($script:Support) {
        'full' {
            Ok 'Supported device'
            if ($script:CanDebloat) { Ok 'Extras for this device: Debloat and Restore' }
            if ($script:CanHomeCatch) { Ok 'HomeCatch available (the Home button opens SimpleLauncher)' }
        }
        'testing' {
            Warn "HomeCatch is being tested on the $($script:SupportedName) with Fire OS $($script:FireMajor)."
            Note "You can install SimpleLauncher with HomeCatch. If anything doesn't work, switch"
            Note "HomeCatch off in SimpleLauncher's Settings $Arrow Danger Zone and tell us at $RepoUrl/issues"
        }
        'app' {
            Warn $reason
            Note 'You can install SimpleLauncher as an app. HomeCatch stays off on devices'
            Note "that haven't been tested yet, so nothing can go wrong."
            Note "Want it supported? Tell us the model ($($script:Model)) at $RepoUrl/issues"
        }
        'generic' {
            if ($script:GoogleTv) {
                Ok 'SimpleLauncher can be your home screen on this Google TV'
                Warn 'HomeCatch is being tested on Google TV.'
                Note "Google TV ignores the home app setting, so HomeCatch makes the Home button open"
                Note "SimpleLauncher. If anything doesn't work, switch it off in Settings $Arrow Danger Zone"
                Note "and tell us at $RepoUrl/issues"
            } else {
                Ok 'SimpleLauncher can be your home screen on this device'
            }
        }
    }
    if ($script:Sdk -gt 0 -and $script:Sdk -lt 25) {
        Blank
        Bad "SimpleLauncher needs Android 7.1 or newer, and this device runs Android $android."
        Say "  SimpleScript can't set it up, sorry."
        Finish 1
    }
}

# =====================================================================================
#  Helpers on the TV
# =====================================================================================
function PackageSet([string]$flag = '') {
    $set = @{}
    $list = if ($flag) { S pm list packages $flag } else { S pm list packages }
    foreach ($l in ($list -split "`n")) {
        $p = $l.Trim() -replace '^package:', ''
        if ($p) { $set[$p] = $true }
    }
    return $set
}
function SlInstalled { return (PackageSet).ContainsKey($SlPkg) }
function SlVersion {
    foreach ($l in ((S dumpsys package $SlPkg) -split "`n")) {
        $i = $l.IndexOf('versionName=')
        if ($i -ge 0) { return $l.Substring($i + 12).Trim() }
    }
    return ''
}
function Set-Animations([string]$scale) {
    foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { S settings put global $k $scale | Out-Null }
}
function HomeCatch-On {
    $cur = S settings get secure enabled_accessibility_services
    $next = $SlService
    if ($cur -and $cur -ne 'null') { $next = if ($cur -like "*$SlService*") { $cur } else { "${cur}:$SlService" } }
    S "settings put secure enabled_accessibility_services '$next'" | Out-Null
    S settings put secure accessibility_enabled 1 | Out-Null
    return ((S settings get secure enabled_accessibility_services) -like "*$SlService*")
}
function HomeCatch-Off {
    $cur = S settings get secure enabled_accessibility_services
    $keep = @($cur -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $SlService })
    if ($keep.Count -eq 0) {
        S settings delete secure enabled_accessibility_services | Out-Null
        S settings put secure accessibility_enabled 0 | Out-Null
    } else {
        S "settings put secure enabled_accessibility_services '$($keep -join ':')'" | Out-Null
    }
}
function Offer-Restart {
    Blank
    if (AskYN 'Restart the TV now so everything takes effect?' 'y') {
        A reboot | Out-Null
        Ok "Restarting. It'll be back in about a minute."
        Dim '  SimpleScript will need to reconnect afterwards. Run it again if you want to do more.'
        Finish 0
    }
}

function ChoicesFile { Join-Path $DataDir "choices-$($script:Model).txt" }
function Load-Choices {
    $m = @{}
    $f = ChoicesFile
    if (Test-Path $f) {
        foreach ($l in (Get-Content $f)) {
            $kv = $l.Trim() -split '=', 2
            if ($kv.Count -eq 2) { $m[$kv[0]] = $kv[1] }
        }
    }
    return $m
}
function Save-Choices($decisions) {
    $m = Load-Choices
    foreach ($d in $decisions) { $m[$d.Pkg] = $d.Answer }
    New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
    ($m.Keys | Sort-Object | ForEach-Object { "$_=$($m[$_])" }) | Set-Content -Path (ChoicesFile) -Encoding ASCII
}

# =====================================================================================
#  Debloat (supported devices only)
# =====================================================================================
function Debloat {
    Heading 'Debloat'
    Say "  I'll go through what's on your TV. y switches it off, n keeps it."
    Say '  Nothing is deleted, and Restore switches everything back on.'
    $present = PackageSet
    $disabled = PackageSet '-d'
    $items = @()
    foreach ($line in $DebloatList) {
        $f = $line -split '\|'
        if ($present.ContainsKey($f[1])) { $items += , $f }
    }
    if ($items.Count -eq 0) { Ok "Nothing from the list is on this TV. It's already clean."; return }

    $saved = Load-Choices
    $haveSaved = Test-Path (ChoicesFile)
    $newCount = @($items | Where-Object { -not $saved.ContainsKey($_[1]) }).Count

    Blank
    Say '  How would you like to choose?'
    $modes = @('recommended')
    Say '  1) Recommended (switch off adverts, tracking, pop-ups and forced updates; keep apps and Alexa)'
    if ($haveSaved) {
        $modes += 'saved'
        if ($newCount -gt 0) { Say "  $($modes.Count)) Same as last time (and ask me about the $newCount new item(s))" } else { Say "  $($modes.Count)) Same as last time" }
    }
    $modes += 'each'; Say "  $($modes.Count)) Let me choose each one"
    Blank
    $c = Choose "Choose 1-$($modes.Count) (or q to go back):" $modes.Count
    if ($c -eq 0) { return }
    $mode = $modes[$c - 1]

    $decisions = @()
    foreach ($g in $DebloatGroups) {
        $gf = $g -split '\|', 3
        $shown = $false
        foreach ($f in $items) {
            if ($f[0] -ne $gf[0]) { continue }
            $pkg = $f[1]; $desc = $f[2]; $rec = $f[3]; $how = $f[4]
            $hasLast = $saved.ContainsKey($pkg)
            $last = if ($hasLast) { $saved[$pkg] } else { '' }
            $isNew = (-not $hasLast) -and $haveSaved
            $def = if ($hasLast) { $last } else { $rec }
            $answer = ''
            if ($mode -eq 'recommended') { $answer = $rec } elseif ($mode -eq 'saved') { $answer = $last }
            if (-not $answer) {
                if (-not $shown) { Blank; Write-Host "  $($gf[1])" -ForegroundColor White; Dim "  $($gf[2])"; $shown = $true }
                $extra = ''
                if ($disabled.ContainsKey($pkg)) { $extra += ' (off now)' }
                if ($isNew) { $extra += ' NEW' }
                $verb = if ($how -eq 'uninstall') { 'Remove' } else { 'Switch off' }
                $answer = if (AskYN "$verb ${desc}?$extra" $def) { 'y' } else { 'n' }
            }
            $decisions += [pscustomobject]@{ Pkg = $pkg; Answer = $answer; How = $how; Desc = $desc }
        }
    }

    $toOff = @(); $toOn = @(); $toRemove = @()
    foreach ($d in $decisions) {
        if ($d.How -eq 'uninstall') { if ($d.Answer -eq 'y') { $toRemove += $d } }
        elseif ($d.Answer -eq 'y' -and -not $disabled.ContainsKey($d.Pkg)) { $toOff += $d }
        elseif ($d.Answer -eq 'n' -and $disabled.ContainsKey($d.Pkg)) { $toOn += $d }
    }
    Blank
    $faster = AskYN 'Also make menus feel faster (half-length animations)?' 'y'

    Heading "Here's the plan"
    if ($toOff.Count) { Say "  Switch off ($($toOff.Count)):"; $toOff | ForEach-Object { Say "    - $($_.Desc)" } }
    if ($toOn.Count) { Say "  Switch back on ($($toOn.Count)):"; $toOn | ForEach-Object { Say "    + $($_.Desc)" } }
    if ($toRemove.Count) { Say "  Remove ($($toRemove.Count)):"; $toRemove | ForEach-Object { Say "    x $($_.Desc)" } }
    if ($faster) { Say '  Faster animations' }
    if (($toOff.Count + $toOn.Count + $toRemove.Count) -eq 0 -and -not $faster) {
        Say '  Nothing to change. Your TV already matches your choices.'
        Save-Choices $decisions
        return
    }
    Blank
    if (-not (AskYN 'Go ahead?' 'y')) { Say '  OK, nothing was changed.'; return }

    Blank
    $fails = 0
    foreach ($d in $toOff) { if ((S pm disable-user --user 0 $d.Pkg) -match 'disabled') { Ok "Off: $($d.Desc)" } else { Bad "Fire OS refused: $($d.Desc)"; $fails++ } }
    foreach ($d in $toOn) { if ((S pm enable $d.Pkg) -match 'enabled') { Ok "On: $($d.Desc)" } else { Bad "Couldn't switch on: $($d.Desc)"; $fails++ } }
    foreach ($d in $toRemove) { if ((A uninstall $d.Pkg) -match 'Success') { Ok "Removed: $($d.Desc)" } else { Bad "Couldn't remove: $($d.Desc)"; $fails++ } }
    if ($faster) { Set-Animations '0.5'; Ok 'Faster animations' }
    S pm trim-caches 999G | Out-Null
    Save-Choices $decisions
    Blank
    if ($fails -eq 0) { Ok 'Debloat finished.' } else { Warn "Debloat finished with $fails item(s) Fire OS wouldn't change." }
    Offer-Restart
}

function Restore {
    Heading 'Restore to stock'
    Say '  This switches back on everything SimpleScript can switch off, puts the animation'
    Say '  speed back to normal and gives the Home button back to Amazon.'
    Blank
    if (-not (AskYN 'Restore your TV?' 'y')) { Say '  OK, nothing was changed.'; return }
    $disabled = PackageSet '-d'
    Blank
    $count = 0
    foreach ($line in $DebloatList) {
        $f = $line -split '\|'
        if ($f[4] -ne 'disable' -or -not $disabled.ContainsKey($f[1])) { continue }
        if ((S pm enable $f[1]) -match 'enabled') { Ok "On: $($f[2])"; $count++ } else { Bad "Couldn't switch on: $($f[2])" }
    }
    if ($count -eq 0) { Ok 'Nothing was switched off' }
    Set-Animations '1.0'; Ok 'Normal animation speed'
    if (SlInstalled) {
        HomeCatch-Off; Ok "HomeCatch off (the Home button opens Amazon's home screen)"
        Blank
        if (AskYN 'Also uninstall SimpleLauncher?' 'n') {
            if ((A uninstall $SlPkg) -match 'Success') { Ok 'SimpleLauncher removed' } else { Bad "Couldn't remove SimpleLauncher" }
        }
    }
    Remove-Item (ChoicesFile) -ErrorAction SilentlyContinue
    Dim "  Apps you removed yourself (like Downloader) aren't reinstalled. Get them from the Appstore."
    Offer-Restart
}

# =====================================================================================
#  Install SimpleLauncher
# =====================================================================================
$script:Apk = $null

function Download-Latest {
    Note 'Asking GitHub for the latest SimpleLauncher...'
    try {
        $rel = Invoke-RestMethod -UseBasicParsing -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = 'SimpleScript' }
    } catch {
        Bad "Couldn't reach GitHub. Check this PC is online."
        return $false
    }
    $asset = $rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
    if (-not $asset) { Bad "The latest release doesn't have an APK attached."; return $false }
    New-Item -ItemType Directory -Force -Path (Join-Path $DataDir 'downloads') | Out-Null
    $script:Apk = Join-Path $DataDir "downloads\$($asset.name)"
    Note "Downloading build $($rel.tag_name)..."
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $script:Apk
    } catch {
        Bad "The download didn't finish."
        Remove-Item $script:Apk -ErrorAction SilentlyContinue
        return $false
    }
    Ok "Downloaded build $($rel.tag_name)"
    return $true
}

function Newest-LocalApk {
    $patterns = @()
    foreach ($desk in @((Join-Path $HOME 'Desktop'), (Join-Path $HOME 'OneDrive\Desktop'))) {
        $patterns += (Join-Path $desk 'SimpleLauncher-*.apk')
        $patterns += (Join-Path $desk 'SimpleLauncher\app\build\outputs\apk\debug\app-debug.apk')
        $patterns += (Join-Path $desk 'SimpleLauncher\app\build\outputs\apk\release\app-release.apk')
    }
    $patterns += (Join-Path $HOME 'Downloads\SimpleLauncher-*.apk')
    $found = Get-ChildItem $patterns -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($found) { return $found.FullName }
    return $null
}

function Pick-LocalApk {
    $found = Newest-LocalApk
    if ($found) {
        Say "  Found: $found"
        if (AskYN 'Use this file?' 'y') { $script:Apk = $found; return $true }
    }
    while ($true) {
        Say '  Drag the APK file into this window, or type its path, then press Enter.'
        $p = (Read-Host '  File').Trim().Trim('"').Trim("'")
        if ($p -and (Test-Path $p -PathType Leaf)) { $script:Apk = $p; return $true }
        Bad "There's no file at that path."
        if (-not (AskYN 'Try again?' 'y')) { return $false }
    }
}

function Install-Launcher {
    Heading 'Install SimpleLauncher'
    if (SlInstalled) { Note "SimpleLauncher $(SlVersion) is installed. This updates it and keeps your settings." }
    Blank
    Say '  Where should SimpleLauncher come from?'
    Say '  1) The latest release on GitHub (recommended)'
    Say '  2) An APK file on this PC'
    Blank
    $c = Choose 'Choose 1 or 2 (or q to go back):' 2
    if ($c -eq 0) { return }

    # Ask everything up front, then do it all in one go.
    $wantHomeCatch = $false; $blockUpdates = $false
    if ($script:CanHomeCatch -and ($script:Support -eq 'full' -or $script:Support -eq 'testing' -or $script:GoogleTv)) {
        $whose = if ($script:GoogleTv) { "your TV's own" } else { "Amazon's" }
        Blank
        Say '  HomeCatch makes the Home button on your remote open SimpleLauncher'
        Say "  instead of $whose home screen. You can switch it off any time in"
        Say "  SimpleLauncher's Settings $Arrow Danger Zone."
        if ($script:Support -eq 'testing' -or $script:GoogleTv) { Dim "  It's still being tested on this device." }
        $wantHomeCatch = AskYN 'Switch HomeCatch on?' 'y'
        if ((PackageSet).ContainsKey($UpdaterPkg)) {
            if ((PackageSet '-d').ContainsKey($UpdaterPkg)) {
                Note "Amazon's automatic updates are already switched off on this TV."
            } else {
                Blank
                Say '  Automatic Fire OS updates install by themselves and can undo your changes or'
                Say "  stop HomeCatch working. You can still update by hand in Settings $Arrow My Fire TV."
                $blockUpdates = AskYN 'Stop Fire OS updates installing automatically?' 'y'
            }
        }
    }

    Blank
    if ($c -eq 1) { if (-not (Download-Latest)) { return } } else { if (-not (Pick-LocalApk)) { return } }

    Blank
    Note 'Installing on your TV...'
    $out = A install -r $script:Apk
    if ($out -notmatch 'Success') {
        if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            Bad 'This copy is signed differently from the SimpleLauncher already on your TV.'
            Say '  To switch, the old one has to be removed first. This clears its settings.'
            if (-not (AskYN 'Remove the old SimpleLauncher and install this one?' 'n')) { return }
            A uninstall $SlPkg | Out-Null
            $out = A install $script:Apk
        } elseif ($out -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
            Bad 'Your TV already has a newer SimpleLauncher than this file.'
            return
        }
    }
    if ($out -notmatch 'Success') {
        Bad "The install didn't work:"
        Dim "  $(($out -split "`n" | Where-Object { $_ } | Select-Object -Last 1))"
        return
    }
    Ok "SimpleLauncher $(SlVersion) installed"

    if ($script:Sdk -ge 26) {
        if (-not (S appops set $SlPkg REQUEST_INSTALL_PACKAGES allow)) {
            Ok "Software update allowed (Settings $Arrow Information $Arrow Software update)"
        } else {
            Warn "Couldn't allow Software update. SimpleLauncher will ask on the TV the first time."
        }
    }

    switch ($script:Support) {
        { $_ -eq 'full' -or $_ -eq 'testing' } {
            if ($wantHomeCatch) {
                S pm grant $SlPkg android.permission.WRITE_SECURE_SETTINGS | Out-Null
                if (HomeCatch-On) { Ok 'HomeCatch on' } else { Bad "Fire OS didn't accept HomeCatch" }
            }
            if ($blockUpdates) {
                if ((S pm disable-user --user 0 $UpdaterPkg) -match 'disabled') { Ok 'Automatic Fire OS updates off' }
                else { Bad "Fire OS wouldn't switch off automatic updates" }
            }
        }
        'app' {
            if (-not $script:Forcing) {
                Note "HomeCatch stays off on this Fire TV because it hasn't been tested yet."
                Note 'Open SimpleLauncher from Your Apps on the Fire TV home screen.'
            }
        }
        'generic' {
            if ($script:GoogleTv) {
                if ($wantHomeCatch) {
                    S pm grant $SlPkg android.permission.WRITE_SECURE_SETTINGS | Out-Null
                    if (HomeCatch-On) { Ok 'HomeCatch on' } else { Bad "Google TV didn't accept HomeCatch" }
                } else {
                    Note "HomeCatch is off. You can switch it on later in SimpleLauncher's Settings $Arrow Danger Zone."
                }
            } else {
                Blank
                Say '  To make SimpleLauncher your home screen, choose it on the TV:'
                S am start -a android.settings.HOME_SETTINGS | Out-Null
                Say "  Settings $Arrow Apps $Arrow Default apps $Arrow Home app $Arrow SimpleLauncher"
                Dim "  (I've tried to open that screen for you. Some TVs keep it somewhere else.)"
            }
        }
    }
    S am start -n "$SlPkg/.MainActivity" | Out-Null
    Blank
    Ok 'All done. SimpleLauncher is open on your TV.'
}

# Extras: HomeCatch on a Fire TV that isn't on either list, at your own risk.
function Force-HomeCatch {
    Heading 'Force Install HomeCatch'
    if (-not $script:CanHomeCatch) {
        Warn "HomeCatch hasn't been tested on this Fire TV, so it might not work properly."
        Say "  If anything goes wrong, switch it off in SimpleLauncher's Settings $Arrow Danger Zone,"
        Say "  or choose Remove SimpleLauncher here. Tell us how it went: $RepoUrl/issues"
        Blank
        if (-not (AskYN 'Switch HomeCatch on anyway?' 'n')) { Say '  OK, nothing was changed.'; return }
    }
    if (-not (SlInstalled)) {
        Note "SimpleLauncher isn't installed yet, so let's install it first."
        $script:Forcing = $true
        Install-Launcher
        $script:Forcing = $false
        if (-not (SlInstalled)) { Bad "SimpleLauncher isn't installed, so HomeCatch can't be switched on."; return }
    }
    Blank
    S pm grant $SlPkg android.permission.WRITE_SECURE_SETTINGS | Out-Null
    if (HomeCatch-On) { Ok 'HomeCatch on. Press Home on the remote to try it.' }
    else { Bad "Fire OS didn't accept HomeCatch on this device." }
}

function Uninstall-Launcher {
    Heading 'Remove SimpleLauncher'
    if (-not (AskYN 'Uninstall SimpleLauncher and its settings from this device?' 'n')) { return }
    HomeCatch-Off
    if ((A uninstall $SlPkg) -match 'Success') { Ok 'SimpleLauncher removed' } else { Bad "Couldn't remove it" }
    if ((PackageSet '-d').ContainsKey($UpdaterPkg)) {
        if (AskYN "Also turn Amazon's automatic updates back on?" 'y') {
            if ((S pm enable $UpdaterPkg) -match 'enabled') { Ok 'Automatic Fire OS updates on' } else { Bad "Couldn't turn them back on" }
        }
    }
}

# =====================================================================================
#  Menu
# =====================================================================================
function Show-Menu {
    while ($true) {
        Heading 'What would you like to do?'
        $actions = @()
        $installed = SlInstalled
        $extras = ($script:Support -eq 'full' -and $script:CanDebloat)
        if ($installed) { $actions += 'install'; Say "  $($actions.Count)) Update SimpleLauncher (installed: $(SlVersion))" }
        else { $actions += 'install'; Say "  $($actions.Count)) Install SimpleLauncher" }
        if ($installed -and -not $extras) { $actions += 'uninstall'; Say "  $($actions.Count)) Remove SimpleLauncher" }
        $actions += 'reboot'; Say "  $($actions.Count)) Restart the TV"
        if ($script:IsFire) {
            Blank
            Write-Host '  Extras' -ForegroundColor White
            if ($extras) {
                $actions += 'debloat'; Say "  $($actions.Count)) Debloat (choose what to switch off)"
                $actions += 'restore'; Say "  $($actions.Count)) Restore to stock"
            }
            $actions += 'force'; Say "  $($actions.Count)) Force Install HomeCatch (on any Fire TV, even untested ones)"
        }
        Say '  q) Quit'
        Blank
        $c = Choose "Choose 1-$($actions.Count):" $actions.Count
        if ($c -eq 0) { break }
        switch ($actions[$c - 1]) {
            'install' { Install-Launcher }
            'debloat' { Debloat }
            'restore' { Restore }
            'uninstall' { Uninstall-Launcher }
            'force' { Force-HomeCatch }
            'reboot' { A reboot | Out-Null; Ok 'Restarting your TV.'; Blank; Say "  Thanks for using SimpleScript. Problems or ideas: $RepoUrl/issues"; Finish 0 }
        }
    }
    Blank
    Say "  Thanks for using SimpleScript. Problems or ideas: $RepoUrl/issues"
}

# =====================================================================================
#  Start
# =====================================================================================
Blank
Write-Host 'SimpleScript' -ForegroundColor White -NoNewline; Dim " v$ScriptVersion $Dot by Cherry Universe"
Say 'Sets up SimpleLauncher on your TV, one step at a time.'
Dim 'Close this window at any point to stop.'

Check-Tools
Prepare-AndConnect
Detect-Device
Show-Menu
Finish 0
