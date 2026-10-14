$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$jdk = "C:\Program Files\Java\jdk-24\bin"
$javac = "$jdk\javac.exe"

$ann = "C:\Users\Jacob\.gradle\caches\modules-2\files-2.1\org.jetbrains\annotations\24.1.0\7af6a669488450c4a07c2c3254e2151df42d7d04\annotations-24.1.0.jar"
$apiJar = "purpur-api\build\libs\purpur-api-26.1.2.local-SNAPSHOT.jar"
$serverJar = "versions\1.21.4\purpur-1.21.4.jar"
$libs = (Get-ChildItem -Recurse -Path "libraries" -Filter "*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$adventureLibs = (Get-ChildItem -Recurse -Path "C:\Users\Jacob\.gradle\caches\modules-2\files-2.1\net.kyori" -Filter "*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$cp = "$apiJar;$serverJar;$ann;$adventureLibs;$libs"

if (Test-Path "build_universe") { Remove-Item -Recurse -Force "build_universe" }
New-Item -ItemType Directory -Path "build_universe" | Out-Null

$files = @(
    "purpur-server\src\main\java\org\purpurmc\purpur\UniversePurpurConfig.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\multiversion\UniverseMultiVersionManager.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\multiversion\UniverseGeyserManager.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniversePurpurCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniverseVersionCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniversePluginsCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniverseHelpCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniverseTpsCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniverseSpigotCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\UniverseLinkCommand.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\network\AntiCrashShield.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\network\PacketRateLimiter.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\task\UniverseGovernorTask.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\task\UniverseMemoryGovernor.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\util\SpatialGrid.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\util\UniverseBanner.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\web\UniverseDiscordLinker.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\web\UniverseUserManager.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\web\UniverseWebPanel.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\PurpurConfig.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\PurpurVersionFetcher.java",
    "purpur-server\src\main\java\org\purpurmc\purpur\command\PurpurCommand.java",
    "purpur-server\src\main\java\io\papermc\paper\ServerBuildInfoImpl.java",
    "purpur-server\src\main\java\io\papermc\paper\PaperBootstrap.java"
)

Write-Host "Compiling Universe classes..."
& $javac -cp $cp -d "build_universe" $files
Write-Host "Compilation finished with exit code: $LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }

# Extract all Adventure 4.26.1 classes
Write-Host "Extracting Adventure 4.26.1 classes..."
if (Test-Path "build_adventure") { Remove-Item -Recurse -Force "build_adventure" }
New-Item -ItemType Directory -Path "build_adventure" | Out-Null

$advDir = "C:\Users\Jacob\.gradle\caches\modules-2\files-2.1\net.kyori"
$advJars = Get-ChildItem -Recurse -Path $advDir -Filter "*4.26.1*.jar"
foreach ($aj in $advJars) {
    Write-Host "  Extracting $($aj.Name)..."
    $z = [System.IO.Compression.ZipFile]::OpenRead($aj.FullName)
    foreach ($entry in $z.Entries) {
        if ($entry.FullName.EndsWith("/") -or $entry.FullName.StartsWith("META-INF")) { continue }
        $outPath = Join-Path "build_adventure" $entry.FullName
        $outDir = Split-Path $outPath
        if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $outPath, $true)
    }
    $z.Dispose()
}

function Update-ZipFile($zipPath, $sourceDir) {
    $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $classes = Get-ChildItem -Recurse -File $sourceDir
        $baseLen = (Resolve-Path $sourceDir).Path.Length + 1
        foreach ($f in $classes) {
            $rel = $f.FullName.Substring($baseLen).Replace('\', '/')
            if ($rel.StartsWith("META-INF/") -or $rel -eq "META-INF") { continue }
            $existing = $zip.GetEntry($rel)
            if ($existing) { $existing.Delete() }
            $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
            $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
            $s = $entry.Open()
            $s.Write($bytes, 0, $bytes.Length)
            $s.Close()
        }
    } finally {
        $zip.Dispose()
    }
}

Write-Host "Injecting classes into purpur-api jar..."
Update-ZipFile $apiJar "build_adventure"
Update-ZipFile $apiJar "build_universe"

Write-Host "Copying to libraries folder..."
$targetLibJar = "libraries\org\purpurmc\purpur\purpur-api\1.21.4-R0.1-SNAPSHOT\purpur-api-1.21.4-R0.1-SNAPSHOT.jar"
Copy-Item -Force $apiJar $targetLibJar

Write-Host "Injecting classes into versions\1.21.4\purpur-1.21.4.jar..."
Update-ZipFile $serverJar "build_adventure"
Update-ZipFile $serverJar "build_universe"

# Calculate hashes
$newLibHash = (Get-FileHash -Algorithm SHA256 $targetLibJar).Hash.ToLower()
$newServerHash = (Get-FileHash -Algorithm SHA256 $serverJar).Hash.ToLower()
Write-Host "New purpur-api hash: $newLibHash"
Write-Host "New purpur-server hash: $newServerHash"

# Prepare updated Adventure 4.26.1 libraries map
$advMapping = @{
    "net.kyori:adventure-api:4.20.0" = @{
        NewKey = "net.kyori:adventure-api:4.26.1"
        NewJar = "net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-api\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-key:4.20.0" = @{
        NewKey = "net.kyori:adventure-key:4.26.1"
        NewJar = "net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-key\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-logger-slf4j:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-logger-slf4j:4.26.1"
        NewJar = "net/kyori/adventure-text-logger-slf4j/4.26.1/adventure-text-logger-slf4j-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-logger-slf4j\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-minimessage:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-minimessage:4.26.1"
        NewJar = "net/kyori/adventure-text-minimessage/4.26.1/adventure-text-minimessage-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-minimessage\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-ansi:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-ansi:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-ansi/4.26.1/adventure-text-serializer-ansi-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-ansi\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-commons:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-commons:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-commons/4.26.1/adventure-text-serializer-commons-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-commons\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-gson:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-gson:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-gson/4.26.1/adventure-text-serializer-gson-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-gson\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-json:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-json:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-json/4.26.1/adventure-text-serializer-json-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-json\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-legacy:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-legacy:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-legacy/4.26.1/adventure-text-serializer-legacy-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-legacy\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
    "net.kyori:adventure-text-serializer-plain:4.20.0" = @{
        NewKey = "net.kyori:adventure-text-serializer-plain:4.26.1"
        NewJar = "net/kyori/adventure-text-serializer-plain/4.26.1/adventure-text-serializer-plain-4.26.1.jar"
        File = (Get-ChildItem -Recurse -Path "$advDir\adventure-text-serializer-plain\4.26.1" -Filter "*.jar" | Select-Object -First 1).FullName
    }
}

Write-Host "Updating libraries and versions inside universepurpur.jar..."
Update-ZipFile "universepurpur.jar" "build_universe"

$univZip = [System.IO.Compression.ZipFile]::Open("universepurpur.jar", [System.IO.Compression.ZipArchiveMode]::Update)
try {
    # 1. Update purpur-api in META-INF/libraries
    $libEntryPath = "META-INF/libraries/org/purpurmc/purpur/purpur-api/1.21.4-R0.1-SNAPSHOT/purpur-api-1.21.4-R0.1-SNAPSHOT.jar"
    $existing = $univZip.GetEntry($libEntryPath)
    if ($existing) { $existing.Delete() }
    $libBytes = [System.IO.File]::ReadAllBytes($targetLibJar)
    $entry = $univZip.CreateEntry($libEntryPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $entry.Open()
    $s.Write($libBytes, 0, $libBytes.Length)
    $s.Close()

    # 2. Add adventure 4.26.1 jars into META-INF/libraries
    foreach ($k in $advMapping.Keys) {
        $info = $advMapping[$k]
        $entryPath = "META-INF/libraries/" + $info.NewJar
        $ex = $univZip.GetEntry($entryPath)
        if ($ex) { $ex.Delete() }
        $bytes = [System.IO.File]::ReadAllBytes($info.File)
        $ent = $univZip.CreateEntry($entryPath, [System.IO.Compression.CompressionLevel]::Optimal)
        $str = $ent.Open()
        $str.Write($bytes, 0, $bytes.Length)
        $str.Close()
        Write-Host "  Added $($info.NewJar) to jar"
    }

    # 3. Add asm-9.7.1 and jopt-simple-6.0-alpha-3 directly into META-INF/libraries
    $asmJar = "libraries\org\ow2\asm\asm\9.7.1\asm-9.7.1.jar"
    if (Test-Path $asmJar) {
        $asmEntry = "META-INF/libraries/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar"
        $ex = $univZip.GetEntry($asmEntry)
        if ($ex) { $ex.Delete() }
        $bytes = [System.IO.File]::ReadAllBytes($asmJar)
        $ent = $univZip.CreateEntry($asmEntry, [System.IO.Compression.CompressionLevel]::Optimal)
        $str = $ent.Open()
        $str.Write($bytes, 0, $bytes.Length)
        $str.Close()
    }

    $joptJar = "libraries\net\sf\jopt-simple\jopt-simple\6.0-alpha-3\jopt-simple-6.0-alpha-3.jar"
    if (Test-Path $joptJar) {
        $joptEntry = "META-INF/libraries/net/sf/jopt-simple/jopt-simple/6.0-alpha-3/jopt-simple-6.0-alpha-3.jar"
        $ex = $univZip.GetEntry($joptEntry)
        if ($ex) { $ex.Delete() }
        $bytes = [System.IO.File]::ReadAllBytes($joptJar)
        $ent = $univZip.CreateEntry($joptEntry, [System.IO.Compression.CompressionLevel]::Optimal)
        $str = $ent.Open()
        $str.Write($bytes, 0, $bytes.Length)
        $str.Close()
    }

    # 4. Put pre-patched and customized purpur-1.21.4.jar directly into META-INF/versions/1.21.4/purpur-1.21.4.jar
    $srvEntryPath = "META-INF/versions/1.21.4/purpur-1.21.4.jar"
    $ex = $univZip.GetEntry($srvEntryPath)
    if ($ex) { $ex.Delete() }
    $srvBytes = [System.IO.File]::ReadAllBytes($serverJar)
    $srvEntry = $univZip.CreateEntry($srvEntryPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $str = $srvEntry.Open()
    $str.Write($srvBytes, 0, $srvBytes.Length)
    $str.Close()
    Write-Host "  Added pre-patched $srvEntryPath ($($srvBytes.Length) bytes)"

    # 5. Remove server-1.21.4.jar.patch to avoid patch downloading and save space
    $patchEntry = $univZip.GetEntry("META-INF/versions/1.21.4/server-1.21.4.jar.patch")
    if ($patchEntry) {
        $patchEntry.Delete()
        Write-Host "  Removed server-1.21.4.jar.patch"
    }

    # 6. Ensure download-context is present so Paperclip can extract vanilla libraries from mojang jar
    $dlEntry = $univZip.GetEntry("META-INF/download-context")
    if ($dlEntry) { $dlEntry.Delete() }
    $dlContent = "1066970b09e9c671844572291c4a871cc1ac2b85838bf7004fa0e778e10f1358`thttps://piston-data.mojang.com/v1/objects/4707d00eb834b446575d89a61a11b5d548d8c001/server.jar`tmojang_1.21.4.jar"
    $dlBytes = [System.Text.Encoding]::UTF8.GetBytes($dlContent)
    $dlNew = $univZip.CreateEntry("META-INF/download-context", [System.IO.Compression.CompressionLevel]::Optimal)
    $dls = $dlNew.Open()
    $dls.Write($dlBytes, 0, $dlBytes.Length)
    $dls.Close()
    Write-Host "  Restored META-INF/download-context"

    # 7. Update META-INF/patches.list with ONLY library patches (no versions patch so server jar is extracted directly)
    $pEntry = $univZip.GetEntry("META-INF/patches.list")
    if ($pEntry) { $pEntry.Delete() }
    $pContent = "libraries`t3c6fac2424db3d4a853b669f4e3d1d9c3c552235e19a319673f887083c2303a1`t431aeef7a311cbf278db6e041515a7272b89450b6ec4897880a6eec9b5f9bb74`t8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281`torg/ow2/asm/asm/9.6/asm-9.6.jar`torg/ow2/asm/asm/9.7.1/asm-9.6.jar.patch`torg/ow2/asm/asm/9.7.1/asm-9.7.1.jar`nlibraries`tdf26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28`t7a9609c6759899c1c756e79fac6cc74ef5e4221775eae12cea5f6c8e8e2d35ab`td440ceb839d6adb5cfa81aa321813d60255e68a3e9ca564c91126599310abde5`tnet/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar`tnet/sf/jopt-simple/jopt-simple/6.0-alpha-3/jopt-simple-5.0.4.jar.patch`tnet/sf/jopt-simple/jopt-simple/6.0-alpha-3/jopt-simple-6.0-alpha-3.jar`n"
    $pBytes = [System.Text.Encoding]::UTF8.GetBytes($pContent)
    $pNew = $univZip.CreateEntry("META-INF/patches.list", [System.IO.Compression.CompressionLevel]::Optimal)
    $ps = $pNew.Open()
    $ps.Write($pBytes, 0, $pBytes.Length)
    $ps.Close()
    Write-Host "  Updated META-INF/patches.list (libraries only)"

    # 8. Update META-INF/libraries.list with new hashes
    $listEntry = $univZip.GetEntry("META-INF/libraries.list")
    $reader = New-Object System.IO.StreamReader($listEntry.Open())
    $lines = $reader.ReadToEnd() -split "`n"
    $reader.Close()

    $newLines = @()
    foreach ($line in $lines) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }

        if ($trimmed -like "*org.purpurmc.purpur:purpur-api:1.21.4-R0.1-SNAPSHOT*") {
            $parts = $trimmed -split "`t"
            if ($parts.Length -ge 3) {
                $newLines += "$newLibHash`t$($parts[1])`t$($parts[2])"
            } else {
                $newLines += $line
            }
        } else {
            $matched = $false
            foreach ($oldKey in $advMapping.Keys) {
                if ($trimmed -like "*$oldKey*") {
                    $info = $advMapping[$oldKey]
                    $h = (Get-FileHash -Algorithm SHA256 $info.File).Hash.ToLower()
                    $newLines += "$h`t$($info.NewKey)`t$($info.NewJar)"
                    $matched = $true
                    break
                }
            }
            if (-not $matched) {
                $newLines += $line
            }
        }
    }
    $listEntry.Delete()
    $newListText = ($newLines -join "`n")
    $newListBytes = [System.Text.Encoding]::UTF8.GetBytes($newListText)
    $newListEntry = $univZip.CreateEntry("META-INF/libraries.list", [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $newListEntry.Open()
    $s.Write($newListBytes, 0, $newListBytes.Length)
    $s.Close()

    # 9. Update META-INF/versions.list with new hash
    $verEntry = $univZip.GetEntry("META-INF/versions.list")
    $readerVer = New-Object System.IO.StreamReader($verEntry.Open())
    $verLines = $readerVer.ReadToEnd() -split "`n"
    $readerVer.Close()

    $newVerLines = @()
    foreach ($line in $verLines) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
        if ($trimmed -like "*1.21.4/purpur-1.21.4.jar*") {
            $parts = $trimmed -split "`t"
            if ($parts.Length -ge 3) {
                $newVerLines += "$newServerHash`t$($parts[1])`t$($parts[2])"
            } else {
                $newVerLines += $line
            }
        } else {
            $newVerLines += $line
        }
    }
    $verEntry.Delete()
    $newVerText = ($newVerLines -join "`n")
    $newVerBytes = [System.Text.Encoding]::UTF8.GetBytes($newVerText)
    $newVerEntry = $univZip.CreateEntry("META-INF/versions.list", [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $newVerEntry.Open()
    $s.Write($newVerBytes, 0, $newVerBytes.Length)
    $s.Close()

} finally {
    $univZip.Dispose()
}

Write-Host "Build and packaging completed successfully!"
