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
new MutationObserver((changes) => {
  // YouTube fills a page in piece by piece and reuses its elements: what said nothing else a
  // moment ago may hold more now, to read or to press. Look at each mark again.
  const before = [...marked];
  marked.clear();
  for (const entry of before) entry.removeAttribute("data-open-app");
  for (const entry of before) if (entry.isConnected) markOpenApp(entry);
  for (const change of changes) {
    if (change.type === "characterData") markOpenApp(change.target);
    for (const node of change.addedNodes) markOpenApp(node);
  }
  lookAtCommentsSoon();
}).observe(document, { subtree: true, childList: true, characterData: true });

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
function lookAtComments() {
  commentsLook = 0;
  const video = new URLSearchParams(location.search).get("v");
  const watch =
    location.pathname === "/watch" && document.querySelector("ytm-watch");
  const beside = watch ? parseFloat(getComputedStyle(watch).marginRight) : 0;
  // youtube.css keeps a pull inside the panel in that column from reading the page again.
  document.documentElement.toggleAttribute("data-comments-beside", beside > 0);
  if (video !== comments.video) {
    // YouTube changes videos without loading a page. What shows now may still be the last
    // one's: look again in a moment.
    Object.assign(comments, { video, seen: false, presses: 0, pressedAt: 0 });
    return lookAtCommentsSoon();
  }
  if (!watch) return;
  const panels = [...document.querySelectorAll("ytm-engagement-panel")].filter(
    showing
  );
  if (
    panels.some((panel) =>
      panel.querySelector(".engagement-panel-comments-section")
    )
  )
    comments.seen = true;
  const entry = [...document.querySelectorAll(COMMENTS_BOX)].find(
    (box) =>
      showing(box) &&
      leadsToComments([...box.querySelectorAll("*")].map((e) => e.localName))
  );
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

// YouTube's logo leads to its home, away from the video the list opened. It stays to look at,
// but a press on it goes nowhere. YouTube builds its top bar differently signed in and signed
// out, so the logo is told by more than one sign: its element, a name that says logo, a link to
// the home page, or a label that starts with YouTube.
const HOME_LOGO =
  "ytm-home-logo, #home-icon, .mobile-topbar-logo, .mobile-topbar-header-endpoint";
const TOP_BAR =
  "header, [role=banner], ytm-mobile-topbar-renderer, .shorts-header";
const HOME_LABEL = /^\s*YouTube(?!.*(検索|search))/i;
function leadsHome(target) {
  if (!(target instanceof Element)) return false;
  if (target.closest(HOME_LOGO)) return true;
  const bar = target.closest(TOP_BAR);
  // Only the bar along the top of the screen: other headers further down keep their links.
  const place = bar?.getBoundingClientRect();
  if (!place || place.top > 8 || place.height > 160) return false;
  for (let part = target; part && part !== bar; part = part.parentElement) {
    const names = `${part.localName} ${part.id} ${
      part.getAttribute("class") ?? ""
    }`;
    if (/logo/i.test(names)) return true;
    if (HOME_LABEL.test(part.getAttribute("aria-label") ?? "")) return true;
    if (
      part.localName === "a" &&
      part.pathname === "/" &&
      /youtube\.com$/.test(part.hostname)
    )
      return true;
  }
  return false;
}
// This script starts before the page's own, so these listeners hear a press first.
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
      if (event.cancelable) event.preventDefault();
    },
    { capture: true, passive: false }
  );
