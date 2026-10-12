// KonneKt window helper (JXA, run by osascript -l JavaScript). Inputs arrive in argv only.
//   list                                              -> JSON array of apps with windows (frontmost app first)
//   closeWindow <pid> <index> <title>                 -> true | false
//   quitApp <bundleId | pid:<n>>                      -> true | false
//   closeTab <bundleId> <windowIndex> <tabIndex> <title> -> true | false
// Window indexes are positions in System Events' window list; browser window indexes are positions
// in the browser's own scripting dictionary. Needs Accessibility (UI scripting) and Automation.

var CHROMIUM = ['com.google.Chrome', 'com.microsoft.edgemac', 'com.brave.Browser', 'com.vivaldi.Vivaldi', 'com.operasoftware.Opera'];
var SAFARI = 'com.apple.Safari';

function safe(f, fallback) {
    try { return f(); } catch (e) { return fallback; }
}

function isBrowser(bundleId) {
    return bundleId === SAFARI || CHROMIUM.indexOf(bundleId) >= 0;
}

/** Tab titles of one browser window: Chromium names them `title`, Safari `name`. */
function tabTitles(bundleId, browserWindow) {
    var titles = bundleId === SAFARI ? browserWindow.tabs.name() : browserWindow.tabs.title();
    return titles.map(function (t) { return t || ''; });
}

/** [{name, tabs}] for every window of a running browser, in its dictionary order. */
function browserWindows(bundleId) {
    var app = Application(bundleId);
    if (!app.running()) return [];
    return app.windows().map(function (bw) {
        return { name: safe(function () { return bw.name(); }, '') || '', tabs: safe(function () { return tabTitles(bundleId, bw); }, []) };
    });
}

/** System Events windows that are ordinary document windows (subrole check skipped when unreadable). */
function standardWindows(proc) {
    var all = safe(function () { return proc.windows(); }, []);
    var out = [];
    for (var i = 0; i < all.length; i++) {
        var w = all[i];
        var subrole = safe(function () { return w.subrole(); }, null);
        if (subrole && subrole !== 'AXStandardWindow') continue;
        out.push({ index: i, title: safe(function () { return w.name(); }, '') || '' });
    }
    return out;
}

/** Pairs each System Events window with a browser window: same title first, else the same position. */
function attachTabs(windows, browser) {
    var used = {};
    windows.forEach(function (w) {
        var match = -1;
        for (var b = 0; b < browser.length; b++) {
            if (!used[b] && browser[b].name === w.title) { match = b; break; }
        }
        if (match < 0) {
            var pos = windows.indexOf(w);
            if (pos < browser.length && !used[pos]) match = pos;
        }
        if (match >= 0) {
            used[match] = true;
            w.browserWindow = match;
            w.tabs = browser[match].tabs;
        }
    });
}

function list() {
    var se = Application('System Events');
    var procs = se.applicationProcesses.whose({ backgroundOnly: false })();
    var apps = [];
    procs.forEach(function (proc) {
        try {
            var windows = standardWindows(proc);
            if (windows.length === 0) return;
            var bundleId = safe(function () { return proc.bundleIdentifier(); }, '') || '';
            if (isBrowser(bundleId)) {
                safe(function () { attachTabs(windows, browserWindows(bundleId)); }, null);
            }
            apps.push({
                pid: proc.unixId(),
                bundleId: bundleId,
                name: safe(function () { return proc.displayedName(); }, '') || safe(function () { return proc.name(); }, ''),
                frontmost: safe(function () { return proc.frontmost(); }, false),
                windows: windows,
            });
        } catch (e) { /* one unreadable process must not hide the rest */ }
    });
    apps.sort(function (a, b) { return (b.frontmost ? 1 : 0) - (a.frontmost ? 1 : 0); });
    return JSON.stringify(apps);
}

function processByPid(pid) {
    var found = Application('System Events').applicationProcesses.whose({ unixId: pid })();
    return found.length > 0 ? found[0] : null;
}

function closeWindow(pid, index, title) {
    var proc = processByPid(pid);
    if (proc === null) return false;
    var w = proc.windows()[index];
    if (!w || (w.name() || '') !== title) return false;
    var buttons = w.buttons.whose({ subrole: 'AXCloseButton' })();
    if (buttons.length === 0) return false;
    buttons[0].click();
    return true;
}

function quitApp(target) {
    var bundleId = target;
    if (target.indexOf('pid:') === 0) {
        var proc = processByPid(parseInt(target.substring(4), 10));
        bundleId = proc === null ? '' : (proc.bundleIdentifier() || '');
    }
    if (!bundleId) return false;
    var app = Application(bundleId);
    if (!app.running()) return false;
    app.quit();
    return true;
}

function closeTab(bundleId, windowIndex, tabIndex, title) {
    if (!isBrowser(bundleId)) return false;
    var app = Application(bundleId);
    if (!app.running()) return false;
    var bw = app.windows()[windowIndex];
    if (!bw) return false;
    var titles = tabTitles(bundleId, bw);
    var target = titles[tabIndex] === title ? tabIndex : titles.indexOf(title);
    if (target < 0) return false;
    bw.tabs[target].close();
    return true;
}

function run(argv) {
    switch (argv[0]) {
        case 'list': return list();
        case 'closeWindow': return closeWindow(parseInt(argv[1], 10), parseInt(argv[2], 10), argv[3] || '');
        case 'quitApp': return quitApp(argv[1] || '');
        case 'closeTab': return closeTab(argv[1] || '', parseInt(argv[2], 10), parseInt(argv[3], 10), argv[4] || '');
        default: return false;
    }
}
