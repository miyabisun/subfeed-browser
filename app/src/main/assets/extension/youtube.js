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
}).observe(document, { subtree: true, childList: true, characterData: true });
