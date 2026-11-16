function tellApp(message) {
  browser.runtime.sendNativeMessage("browser", message);
}

// YouTube leaves a video that was opened by its address waiting on its poster.
// The tap that opened it already asked for it to play, so press play for the user.
if (/^\/(watch|shorts\/)/.test(location.pathname)) {
  const giveUp = Date.now() + 15000;
  const timer = setInterval(() => {
    const video = document.querySelector("video");
    if ((video && !video.paused) || Date.now() > giveUp)
      return clearInterval(timer);
    const play = document.querySelector(
      ".ytp-cued-thumbnail-overlay .ytp-large-play-button"
    );
    if (play?.offsetParent) play.click();
  }, 300);
}

// The entry in YouTube's menus that opens the YouTube app has no link to tell it by, only its
// label. Mark it, so youtube.css removes it with the other invitations to the app.
const OPEN_APP = /^\s*(アプリを開く|アプリで開く|Open app|Open in app)\s*$/i;
const CONTROLS = "a, button, input, select, [role=menuitem]";
const marked = new Set();
function controls(element) {
  return (
    element.querySelectorAll(CONTROLS).length +
    (element.matches(CONTROLS) ? 1 : 0)
  );
}
function markOpenApp(root) {
  const texts = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let text = root.nodeType === Node.TEXT_NODE ? root : texts.nextNode();
  for (; text; text = texts.nextNode()) {
    if (!OPEN_APP.test(text.data)) continue;
    // The whole entry: the largest element that says nothing else and holds nothing else to
    // press. A bar of buttons that are only icons says nothing else either.
    let entry = text.parentElement;
    if (!entry) continue;
    const own = controls(entry);
    for (
      let around = entry.parentElement;
      around &&
      OPEN_APP.test(around.textContent) &&
      controls(around) <= Math.max(own, 1);
      around = around.parentElement
    )
      entry = around;
    entry.setAttribute("data-open-app", "");
    marked.add(entry);
  }
}

// YouTube shows a channel's handle as it stands in the channel's address, escapes and all.
// handle.js reads it; what the user is writing stays as written.
const ESCAPE = /%[0-9a-f]{2}/i;
// A handle is short. A script of the page is text too, and long: it is not read for one.
const HANDLE_AT_MOST = 300;
function saidBefore(text) {
  // The "@" may stand in an element of its own, beside the one that holds the name.
  let node = text;
  for (let up = 0; node && !node.previousSibling && up < 3; up++)
    node = node.parentNode;
  return node?.previousSibling?.textContent ?? "";
}
function readHandles(root) {
  const texts = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let text = root.nodeType === Node.TEXT_NODE ? root : texts.nextNode();
  for (; text; text = texts.nextNode()) {
    if (text.data.length > HANDLE_AT_MOST || !ESCAPE.test(text.data)) continue;
    if (text.parentElement?.isContentEditable) continue;
    const readable = readableHandle(text.data, saidBefore(text));
    if (readable !== null) text.data = readable;
  }
}

new MutationObserver((changes) => {
  // YouTube fills a page in piece by piece and reuses its elements: what said nothing else a
  // moment ago may hold more now, to read or to press. Look at each mark again.
  const before = [...marked];
  marked.clear();
  for (const entry of before) entry.removeAttribute("data-open-app");
  for (const entry of before) if (entry.isConnected) markOpenApp(entry);
  for (const change of changes) {
    if (change.type === "characterData") {
      markOpenApp(change.target);
      readHandles(change.target);
    }
    for (const node of change.addedNodes) {
      markOpenApp(node);
      readHandles(node);
    }
  }
  lookAtCommentsSoon();
  lookAtPageSoon();
}).observe(document, { subtree: true, childList: true, characterData: true });

// What else follows the page as YouTube builds and changes it, a moment after it changed.
let pageLook = 0;
function lookAtPageSoon() {
  pageLook ||= setTimeout(() => {
    pageLook = 0;
    lookAtLogo();
    lookAtStage();
    lookAtInvitation();
  }, 250);
}

// YouTube greets a visitor who is not signed in with a dialog that invites to its app, over the
// video the user just opened. youtube.css removes the invitation; invitation.js decides whether
// what is left is closed for the user.
const DIALOGS = "#dialog-container";
const CLOSES = /^\s*(閉じる|close)\s*$/i;
function lookAtInvitation() {
  const dialog = document.querySelector(DIALOGS);
  if (!dialog) return;
  const controls = [...dialog.querySelectorAll("a, button")];
  // The veil behind a dialog is a way to close it too, but not one that shows.
  const shown = controls.filter(
    (control) =>
      control.getClientRects().length > 0 && !control.closest("ytw-scrim")
  );
  const closes = shown.filter((control) =>
    CLOSES.test(control.getAttribute("aria-label") ?? "")
  );
  const close = closesInvitation({
    invites: controls.some((control) =>
      /^(intent|vnd\.youtube):/.test(control.getAttribute("href") ?? "")
    ),
    closes: closes.length,
    others: shown.length - closes.length,
  });
  if (close) closes[0].click();
}

// On a wide screen YouTube keeps a column free beside a video, for the recommendations this app
// removes. Open the comments there: press the way into them, as the user would. comments.js
// decides when; what is noted here is about one video and starts over with the next.
const COMMENTS_BOX = "yt-video-metadata-carousel-view-model";
const comments = { video: null, seen: false, presses: 0, pressedAt: 0 };
let commentsLook = 0;
function showing(element) {
  return (
    element.getClientRects().length > 0 &&
    getComputedStyle(element).visibility !== "hidden"
  );
}
function watchPage() {
  return location.pathname === "/watch"
    ? document.querySelector("ytm-watch")
    : null;
}
// How wide a column YouTube keeps free beside the video, 0 in one column. A video that has the
// whole screen has no such column: the page under it is out of sight.
function columnBeside() {
  const watch = watchPage();
  if (!watch || document.documentElement.hasAttribute("data-stage")) return 0;
  return parseFloat(getComputedStyle(watch).marginRight);
}
function panelsShowing() {
  return [...document.querySelectorAll("ytm-engagement-panel")].filter(showing);
}
function commentsPanel() {
  return panelsShowing().find((panel) =>
    panel.querySelector(".engagement-panel-comments-section")
  );
}
// The way into the comments under the video, if this video has one. It need not be in sight:
// under a video that has the whole screen, the page is there but hidden.
function commentsEntry() {
  return [...document.querySelectorAll(COMMENTS_BOX)].find(
    (box) =>
      box.getClientRects().length > 0 &&
      leadsToComments([...box.querySelectorAll("*")].map((e) => e.localName))
  );
}
function lookAtComments() {
  commentsLook = 0;
  const video = new URLSearchParams(location.search).get("v");
  const beside = columnBeside();
  // youtube.css keeps a pull inside the panel in that column from reading the page again.
  document.documentElement.toggleAttribute("data-comments-beside", beside > 0);
  lookAtHold();
  if (video !== comments.video) {
    // YouTube changes videos without loading a page. What shows now may still be the last
    // one's: look again in a moment.
    Object.assign(comments, { video, seen: false, presses: 0, pressedAt: 0 });
    return lookAtCommentsSoon();
  }
  if (!watchPage()) return;
  const panels = panelsShowing();
  if (commentsPanel()) comments.seen = true;
  const entry = commentsEntry();
  const open = opensComments({
    path: location.pathname,
    beside,
    fullScreen: document.fullscreenElement !== null,
    entry: entry !== undefined,
    panel: panels.length > 0,
    seen: comments.seen,
    presses: comments.presses,
    sincePress: Date.now() - comments.pressedAt,
  });
  if (!open) return;
  comments.presses++;
  comments.pressedAt = Date.now();
  entry.click();
  // If nothing shows, comments.js may want another press.
  setTimeout(lookAtCommentsSoon, PRESS_AGAIN_AFTER);
}
function lookAtCommentsSoon() {
  commentsLook ||= setTimeout(lookAtComments, 250);
}
// Turning the device changes the columns without changing the page.
window.addEventListener("resize", lookAtCommentsSoon);
document.addEventListener("fullscreenchange", lookAtCommentsSoon);

// While a panel is open, YouTube holds the page under it: `inert` on this element, the video's
// title, buttons and description. Nothing in it takes a press then, though beside the video the
// panel covers none of it. comments.js decides when the hold comes off and when it goes back.
// The player and the top bar are outside this element; a dialog holds the whole app instead.
const HELD_PAGE = ".page-container";
let liftedHold = false;
function lookAtHold() {
  const page = document.querySelector(HELD_PAGE);
  if (!page) return;
  const panel = panelsShowing().length > 0;
  const act = holdOnPage({
    beside: columnBeside(),
    panel,
    held: page.hasAttribute("inert"),
    lifted: liftedHold,
  });
  if (act === "lift") page.removeAttribute("inert");
  if (act === "restore") page.setAttribute("inert", "");
  liftedHold = panel && (liftedHold || act === "lift");
}
// YouTube holds the page again whenever a panel opens or closes over another. Its own mark of
// an open panel changes just before it lets go. A like shows in the same way, as an attribute.
new MutationObserver(() => {
  lookAtHold();
  lookAtStage();
}).observe(document, {
  subtree: true,
  attributes: true,
  attributeFilter: ["inert", "engagement-panel-open", "aria-pressed"],
});

// In one column YouTube takes a touch on the player for its own. Beside a free column it leaves
// the touch to the page, and the app took a move down from the player for a pull on the page
// and read it again. pull.js decides which touches this script takes in YouTube's place, at
// their first move: the engine tells the app then whose the touch is.
const PLAYER = "#player-container-id, #player, .html5-video-player";
let touchBegan = null;
function onPlayer(target, touch) {
  if (target instanceof Element && target.closest(PLAYER)) return true;
  // Told by its place as well: YouTube builds its pages differently signed in and signed out.
  const place = document.querySelector("video")?.getBoundingClientRect();
  return (
    place !== undefined &&
    touch.clientX >= place.left &&
    touch.clientX <= place.right &&
    touch.clientY >= place.top &&
    touch.clientY <= place.bottom
  );
}
window.addEventListener(
  "touchstart",
  (event) => {
    const touch = event.touches[0];
    touchBegan = {
      x: touch.clientX,
      y: touch.clientY,
      onPlayer: onPlayer(event.target, touch),
    };
  },
  { capture: true, passive: true }
);
window.addEventListener(
  "touchmove",
  (event) => {
    const began = touchBegan;
    touchBegan = null;
    if (!began || !event.cancelable) return;
    const touch = event.touches[0];
    const take = takesTouch({
      beside: columnBeside(),
      fullScreen: document.fullscreenElement !== null,
      onPlayer: began.onPlayer,
      fingers: event.touches.length,
      scrolled: window.scrollY,
      across: touch.clientX - began.x,
      down: touch.clientY - began.y,
    });
    // The move still reaches the player: its own gestures stay as they are.
    if (take) event.preventDefault();
  },
  { capture: true, passive: false }
);

// A phone on its side is too low for YouTube's page, so the video gets the whole screen there.
// stage.js decides when, and where the video goes; youtube.css lays the page out from what is
// noted on <html> here. The icons in the bands stand in for the page that is out of sight: the
// way back, YouTube's own like button and YouTube's own comments.
const LIKE =
  "ytm-slim-video-action-bar-renderer like-button-view-model button, ytm-slim-video-action-bar-renderer button[aria-pressed]";
const PANEL_CLOSE = ".engagement-panel-section-list-header button";
const staged = { told: false, icons: null };
function stageIcons() {
  if (staged.icons?.isConnected) return staged.icons;
  const icons = document.createElement("div");
  icons.id = "subfeed-stage";
  const button = (name, label) => {
    const made = document.createElement("button");
    made.type = "button";
    made.dataset.stage = name;
    made.setAttribute("aria-label", label);
    return made;
  };
  const pair = document.createElement("div");
  pair.append(button("like", "高く評価"), button("comments", "コメント"));
  icons.append(button("back", "戻る"), pair);
  icons.addEventListener("click", (event) => {
    const pressed = event.target.closest?.("[data-stage]")?.dataset.stage;
    if (pressed === "back") tellApp({ type: "back" });
    if (pressed === "like") document.querySelector(LIKE)?.click();
    // The same icon closes the comments it opened.
    if (pressed === "comments")
      (commentsPanel()?.querySelector(PANEL_CLOSE) ?? commentsEntry())?.click();
  });
  // Beside <body>, not in it: YouTube holds everything in the body under its dialogs.
  document.documentElement.append(icons);
  return (staged.icons = icons);
}
function lookAtStage() {
  const root = document.documentElement;
  if (!root) return lookAtPageSoon();
  const on = stages({
    path: location.pathname,
    screenWidth: screen.width,
    screenHeight: screen.height,
    fullScreen: document.fullscreenElement !== null,
  });
  // The app hides the list's header and the system's bars around the video.
  if (on !== staged.told) {
    staged.told = on;
    tellApp({ type: "stage", on });
  }
  if (!on) {
    root.removeAttribute("data-stage");
    staged.icons?.remove();
    return;
  }
  // The icons' own box carries the strips the device keeps clear for its camera, as padding.
  const icons = stageIcons();
  const strips = getComputedStyle(icons);
  const layout = stageLayout({
    width: innerWidth,
    height: innerHeight,
    panel: panelsShowing().length > 0,
    safe: {
      top: parseFloat(strips.paddingTop) || 0,
      right: parseFloat(strips.paddingRight) || 0,
      bottom: parseFloat(strips.paddingBottom) || 0,
      left: parseFloat(strips.paddingLeft) || 0,
    },
  });
  root.setAttribute("data-stage", layout.bands);
  const places = {
    left: layout.left,
    top: layout.top,
    width: layout.width,
    height: layout.height,
    panel: layout.panel,
    "back-left": layout.back.left,
    "back-top": layout.back.top,
    "pair-right": layout.pair.right,
    "pair-bottom": layout.pair.bottom,
  };
  for (const [name, far] of Object.entries(places))
    root.style.setProperty(`--stage-${name}`, `${far}px`);
  // An icon shows only for what this video has: a live stream has no comments to open.
  const like = document.querySelector(LIKE);
  const open = commentsPanel() !== undefined;
  showIcon(icons, "like", like, like?.getAttribute("aria-pressed") === "true");
  showIcon(icons, "comments", open || commentsEntry(), open);
}
// Writes only what changed: this script watches the page for the same attribute.
function showIcon(icons, name, there, pressed) {
  const icon = icons.querySelector(`[data-stage=${name}]`);
  if (icon.hidden === Boolean(there)) icon.hidden = !there;
  if (icon.getAttribute("aria-pressed") !== String(pressed))
    icon.setAttribute("aria-pressed", String(pressed));
}
// Turning the device is the moment the layout changes: no waiting then.
window.addEventListener("resize", lookAtStage);
screen.orientation?.addEventListener("change", lookAtStage);
document.addEventListener("fullscreenchange", lookAtStage);
lookAtStage();

// YouTube's logo leads to its home, away from the video the list opened. In its place goes the
// way back: a press on it goes where the back gesture goes, and ends at the list. YouTube
// builds its top bar differently signed in and signed out, so the logo is told by more than one
// sign: its element, a name that says logo, a link to the home page, or a label that starts
// with YouTube. youtube.css draws the arrow over what is marked here.
const HOME_LOGO =
  "ytm-home-logo, #home-icon, .mobile-topbar-logo, .mobile-topbar-header-endpoint";
const TOP_BAR =
  "header, [role=banner], ytm-mobile-topbar-renderer, .shorts-header";
const HOME_LABEL = /^\s*YouTube(?!.*(検索|search))/i;
const BACK = "data-subfeed-back";
// Only the bar along the top of the screen: other headers further down keep their links.
function alongTheTop(bar) {
  const place = bar?.getBoundingClientRect();
  return place !== undefined && place.top <= 8 && place.height <= 160;
}
function saysHome(part) {
  if (part.matches(HOME_LOGO)) return true;
  const names = `${part.localName} ${part.id} ${
    part.getAttribute("class") ?? ""
  }`;
  if (/logo/i.test(names)) return true;
  if (HOME_LABEL.test(part.getAttribute("aria-label") ?? "")) return true;
  return (
    part.localName === "a" &&
    part.pathname === "/" &&
    /youtube\.com$/.test(part.hostname)
  );
}
function leadsHome(target) {
  if (!(target instanceof Element)) return false;
  if (target.closest(`[${BACK}], ${HOME_LOGO}`)) return true;
  const bar = target.closest(TOP_BAR);
  if (!alongTheTop(bar)) return false;
  for (let part = target; part && part !== bar; part = part.parentElement)
    if (saysHome(part)) return true;
  return false;
}
// The whole logo is the outermost part of the bar that says so.
function lookAtLogo() {
  for (const bar of document.querySelectorAll(TOP_BAR)) {
    if (!alongTheTop(bar)) continue;
    for (const part of bar.querySelectorAll("*")) {
      if (part.closest(`[${BACK}]`) || !saysHome(part)) continue;
      part.setAttribute(BACK, "");
      part.setAttribute("role", "button");
      part.setAttribute("aria-label", "戻る");
    }
    // The arrow takes the colour of the bar's own icons: over a video YouTube darkens the bar,
    // whatever the colours of the page.
    const peer = [...bar.querySelectorAll("button, a")].find(
      (control) =>
        !control.closest(`[${BACK}]`) && control.getClientRects().length > 0
    );
    const colour = peer ? getComputedStyle(peer).color : "";
    for (const back of bar.querySelectorAll(`[${BACK}]`))
      if (back.style.color !== colour) back.style.color = colour;
  }
}
// This script starts before the page's own, so these listeners hear a press first and keep
// every part of it from YouTube. The touch itself goes on: the click that ends a tap is the
// press that goes back.
for (const type of [
  "click",
  "pointerdown",
  "pointerup",
  "mousedown",
  "mouseup",
  "touchstart",
  "touchend",
])
  window.addEventListener(
    type,
    (event) => {
      if (!leadsHome(event.target)) return;
      event.stopImmediatePropagation();
      if (type !== "click") return;
      event.preventDefault();
      if (event.isTrusted) tellApp({ type: "back" });
    },
    { capture: true }
  );
