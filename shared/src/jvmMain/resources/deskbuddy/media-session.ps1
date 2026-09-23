# DeskBuddy media-session helper. Polls the Windows system media session and prints JSON lines.
# stdin commands: toggle | play | pause | next | prev | seek <ms> | refresh | quit
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Continue'

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, $resultType) {
    $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

function Emit($object) {
    [Console]::Out.WriteLine(($object | ConvertTo-Json -Compress -Depth 3))
    [Console]::Out.Flush()
}

function Get-Session {
    try {
        $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        return $manager.GetCurrentSession()
    } catch { return $null }
}

function Read-Artwork($props) {
    if ($null -eq $props.Thumbnail) { return $null }
    try {
        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $size = [uint32]$stream.Size
        if ($size -le 0 -or $size -gt 4MB) { return $null }
        $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
        Await ($reader.LoadAsync($size)) ([uint32]) | Out-Null
        $bytes = New-Object byte[] $size
        $reader.ReadBytes($bytes)
        $reader.Dispose(); $stream.Dispose()
        $sha = [System.Security.Cryptography.SHA1]::Create()
        $id = [System.BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
        return @{ id = $id; mimeType = [string]$stream.ContentType; data = [Convert]::ToBase64String($bytes) }
    } catch { return $null }
}

function Read-State($session, $props) {
    if ($null -eq $session) {
        return [ordered]@{ type = 'state'; status = 'NONE' }
    }
    $playback = $session.GetPlaybackInfo()
    $timeline = $session.GetTimelineProperties()
    $status = [string]$playback.PlaybackStatus
    $position = [int64]$timeline.Position.TotalMilliseconds
    if ($status -eq 'Playing' -and $timeline.LastUpdatedTime.Year -gt 2000) {
        $position += [int64]([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
    }
    $duration = [int64]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
    if ($duration -gt 0 -and $position -gt $duration) { $position = $duration }
    return [ordered]@{
        type = 'state'
        title = [string]$props.Title
        artist = [string]$props.Artist
        album = [string]$props.AlbumTitle
        app = [string]$session.SourceAppUserModelId
        status = $status
        positionMs = $position
        durationMs = $duration
        canSeek = [bool]$playback.Controls.IsPlaybackPositionEnabled
        artworkId = $null
    }
}

$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput())
$pending = $stdin.ReadLineAsync()
$lastJson = ''
$lastTrackKey = ''
$artworkId = $null

while ($true) {
    $session = Get-Session
    $trackKey = ''
    $props = $null
    if ($null -ne $session) {
        try {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $trackKey = "$($session.SourceAppUserModelId)|$($props.Title)|$($props.Artist)|$($props.AlbumTitle)"
        } catch { $trackKey = '' }
    }
    if ($trackKey -ne $lastTrackKey) {
        $lastTrackKey = $trackKey
        $artworkId = $null
        if ($null -ne $props) {
            $art = Read-Artwork $props
            if ($null -ne $art) {
                $artworkId = $art.id
                Emit ([ordered]@{ type = 'artwork'; artworkId = $art.id; mimeType = $art.mimeType; data = $art.data })
            }
        }
    }
    try {
        $state = Read-State $session $props
        $state.artworkId = $artworkId
        $json = ($state | ConvertTo-Json -Compress -Depth 3)
        if ($json -ne $lastJson) {
            [Console]::Out.WriteLine($json); [Console]::Out.Flush()
            $lastJson = $json
        }
    } catch { }

    if ($pending.IsCompleted) {
        $line = $pending.Result
        if ($null -eq $line) { break }
        $parts = $line.Trim().Split(' ')
        $ok = $false
        try {
            switch ($parts[0]) {
                'quit' { exit 0 }
                'refresh' { $lastJson = ''; $lastTrackKey = ''; $ok = $true }
                'toggle' { if ($session) { $ok = Await ($session.TryTogglePlayPauseAsync()) ([bool]) } }
                'play' { if ($session) { $ok = Await ($session.TryPlayAsync()) ([bool]) } }
                'pause' { if ($session) { $ok = Await ($session.TryPauseAsync()) ([bool]) } }
                'next' { if ($session) { $ok = Await ($session.TrySkipNextAsync()) ([bool]) } }
                'prev' { if ($session) { $ok = Await ($session.TrySkipPreviousAsync()) ([bool]) } }
                'seek' {
                    if ($session -and $parts.Length -gt 1) {
                        $ticks = [int64]$parts[1] * 10000
                        $ok = Await ($session.TryChangePlaybackPositionAsync($ticks)) ([bool])
                    }
                }
            }
        } catch { $ok = $false }
        if ($parts[0] -ne 'refresh') { Emit ([ordered]@{ type = 'result'; ok = [bool]$ok }) }
        $lastJson = ''
        $pending = $stdin.ReadLineAsync()
    }
    Start-Sleep -Milliseconds 500
}
