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
    "purpur-server\src\main\java\org\purpurmc\purpur\PurpurConfig.java"
)

Write-Host "Compiling Universe classes..."
& $javac -cp $cp -d "build_universe" $files
Write-Host "Compilation finished with exit code: $LASTEXITCODE"

function Update-ZipFile($zipPath, $sourceDir) {
    $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $classes = Get-ChildItem -Recurse -File $sourceDir
        foreach ($f in $classes) {
            $rel = $f.FullName.Substring((Resolve-Path $sourceDir).Path.Length + 1).Replace('\', '/')
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

Write-Host "Injecting classes into purpur-api-26.1.2.local-SNAPSHOT.jar..."
Update-ZipFile $apiJar "build_universe"

Write-Host "Copying to libraries folder..."
$targetLibJar = "libraries\org\purpurmc\purpur\purpur-api\1.21.4-R0.1-SNAPSHOT\purpur-api-1.21.4-R0.1-SNAPSHOT.jar"
Copy-Item -Force $apiJar $targetLibJar

Write-Host "Injecting classes into versions\1.21.4\purpur-1.21.4.jar..."
Update-ZipFile $serverJar "build_universe"

# Calculate new hash for purpur-api jar
$newLibHash = (Get-FileHash -Algorithm SHA256 $targetLibJar).Hash.ToLower()
Write-Host "New purpur-api hash: $newLibHash"

# Calculate new hash for purpur-server jar
$newServerHash = (Get-FileHash -Algorithm SHA256 $serverJar).Hash.ToLower()
Write-Host "New purpur-server hash: $newServerHash"

Write-Host "Injecting classes and libraries into universepurpur.jar..."
# First, classes to root
Update-ZipFile "universepurpur.jar" "build_universe"

# Next, update META-INF/libraries/.../purpur-api...jar inside universepurpur.jar
$univZip = [System.IO.Compression.ZipFile]::Open("universepurpur.jar", [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $libEntryPath = "META-INF/libraries/org/purpurmc/purpur/purpur-api/1.21.4-R0.1-SNAPSHOT/purpur-api-1.21.4-R0.1-SNAPSHOT.jar"
    $existing = $univZip.GetEntry($libEntryPath)
    if ($existing) { $existing.Delete() }
    $libBytes = [System.IO.File]::ReadAllBytes($targetLibJar)
    $entry = $univZip.CreateEntry($libEntryPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $entry.Open()
    $s.Write($libBytes, 0, $libBytes.Length)
    $s.Close()

    # Update META-INF/libraries.list with new hash
    $listEntry = $univZip.GetEntry("META-INF/libraries.list")
    $reader = New-Object System.IO.StreamReader($listEntry.Open())
    $lines = $reader.ReadToEnd() -split "`n"
    $reader.Close()

    $newLines = @()
    foreach ($line in $lines) {
        if ($line -like "*org.purpurmc.purpur:purpur-api:1.21.4-R0.1-SNAPSHOT*") {
            $parts = $line.Trim() -split "`t"
            if ($parts.Length -ge 3) {
                $newLines += "$newLibHash`t$($parts[1])`t$($parts[2])"
            } else {
                $newLines += $line
            }
        } else {
            $newLines += $line
        }
    }
    $listEntry.Delete()
    $newListText = ($newLines -join "`n")
    $newListBytes = [System.Text.Encoding]::UTF8.GetBytes($newListText)
    $newListEntry = $univZip.CreateEntry("META-INF/libraries.list", [System.IO.Compression.CompressionLevel]::Optimal)
    $s = $newListEntry.Open()
    $s.Write($newListBytes, 0, $newListBytes.Length)
    $s.Close()

    # Update META-INF/versions.list with new hash
    $verEntry = $univZip.GetEntry("META-INF/versions.list")
    $readerVer = New-Object System.IO.StreamReader($verEntry.Open())
    $verLines = $readerVer.ReadToEnd() -split "`n"
    $readerVer.Close()

    $newVerLines = @()
    foreach ($line in $verLines) {
        if ($line -like "*1.21.4/purpur-1.21.4.jar*") {
            $parts = $line.Trim() -split "`t"
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
