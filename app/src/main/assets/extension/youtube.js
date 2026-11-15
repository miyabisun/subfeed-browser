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
const OPEN_APP = /^\s*(アプリを開く|Open app)\s*$/i;
const marked = new Set();
function markOpenApp(root) {
  const texts = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let text = root.nodeType === Node.TEXT_NODE ? root : texts.nextNode();
  for (; text; text = texts.nextNode()) {
    if (!OPEN_APP.test(text.data)) continue;
    // The whole entry: the largest element that says nothing else.
    let entry = text.parentElement;
    while (OPEN_APP.test(entry?.parentElement?.textContent ?? ""))
      entry = entry.parentElement;
    if (!entry) continue;
    entry.setAttribute("data-open-app", "");
    marked.add(entry);
  }
}
new MutationObserver((changes) => {
  // YouTube fills a page in piece by piece and reuses its elements: what said nothing else a
  // moment ago may hold more now.
  for (const entry of marked) {
    if (entry.isConnected && OPEN_APP.test(entry.textContent)) continue;
    entry.removeAttribute("data-open-app");
    marked.delete(entry);
    if (entry.isConnected) markOpenApp(entry);
  }
  for (const change of changes) {
    if (change.type === "characterData") markOpenApp(change.target);
    for (const node of change.addedNodes) markOpenApp(node);
  }
}).observe(document, { subtree: true, childList: true, characterData: true });
