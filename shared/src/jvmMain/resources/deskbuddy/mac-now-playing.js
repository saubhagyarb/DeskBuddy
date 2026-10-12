// KonneKt now-playing helper for macOS (JXA). Once a second, prints one JSON line describing Music or
// Spotify (a playing app wins over a paused one). Never launches a player that is not running.
// Exits when stdout closes (the desktop app quit or killed it).
ObjC.import('Foundation');

var out = $.NSFileHandle.fileHandleWithStandardOutput;
var PLAYERS = ['Spotify', 'Music'];

function emit(obj) {
  out.writeData($(JSON.stringify(obj) + '\n').dataUsingEncoding($.NSUTF8StringEncoding));
}

function str(f) { try { var v = f(); return v === null || v === undefined ? '' : String(v); } catch (e) { return ''; } }
function num(f) { try { var v = Number(f()); return isFinite(v) ? v : 0; } catch (e) { return 0; } }

function running(name) {
  try { return Application(name).running(); } catch (e) { return false; } // not installed
}

function sample(name) {
  if (!running(name)) return null;
  var app = Application(name);
  var playerState = str(function () { return app.playerState(); });
  if (playerState === '' || playerState === 'stopped') return null;
  var track = app.currentTrack;
  var title = str(function () { return track.name(); });
  if (title === '') return null;
  var artist = str(function () { return track.artist(); });
  var album = str(function () { return track.album(); });
  var spotify = name === 'Spotify';
  // Spotify reports duration in milliseconds, Music in seconds; both report position in seconds.
  var durationMs = Math.round(num(function () { return track.duration(); }) * (spotify ? 1 : 1000));
  var positionMs = Math.round(num(function () { return app.playerPosition(); }) * 1000);
  if (durationMs > 0 && positionMs > durationMs) positionMs = durationMs;
  return {
    type: 'state',
    title: title,
    artist: artist,
    album: album,
    app: name,
    status: playerState === 'playing' ? 'Playing' : (playerState === 'paused' ? 'Paused' : 'Stopped'),
    positionMs: positionMs,
    durationMs: durationMs,
    canSeek: durationMs > 0,
    artworkUrl: spotify ? str(function () { return track.artworkUrl(); }) : '',
    trackKey: name + '|' + title + '|' + artist + '|' + album
  };
}

while (true) {
  var samples = PLAYERS.map(sample).filter(function (s) { return s !== null; });
  var playing = samples.filter(function (s) { return s.status === 'Playing'; });
  emit(playing.length > 0 ? playing[0] : (samples.length > 0 ? samples[0] : { type: 'state', status: 'NONE' }));
  delay(1);
}
