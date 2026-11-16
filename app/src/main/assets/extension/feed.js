// Runs in pages that are not YouTube or Google: the list of videos and its sign-in.
// Tells the page it is inside this app, and passes on its requests to open a video.
document.documentElement.dataset.videoHost = "1";

function tellApp(message) {
  browser.runtime.sendNativeMessage("browser", message);
}

window.addEventListener("message", (event) => {
  if (event.source !== window || event.origin !== location.origin) return;
  const message = event.data;
  if (
    !message ||
    message.source !== "youtube-sub-feed" ||
    message.type !== "open-video"
  )
    return;
  if (typeof message.url !== "string") return;
  // The app checks the address again before it opens anything.
  tellApp({ type: message.type, url: message.url });
});

// What the page marks with data-video-host-keep stays in view above an open video. The app
// needs to know where on the screen it ends, and when the user presses it.
const KEEP = "[data-video-host-keep]";
let told;
let measuring = false;
const sizes = new ResizeObserver(measureSoon);
const watched = new Set();

function measure() {
  measuring = false;
  const kept = document.querySelectorAll(KEEP);
  let bottom = 0;
  for (const part of kept)
    bottom = Math.max(bottom, part.getBoundingClientRect().bottom);
  // In pixels of the screen, also while the page is zoomed by a pinch.
  const view = window.visualViewport;
  const edge = Math.max(
    0,
    Math.round(
      (bottom - (view?.offsetTop ?? 0)) * (view?.scale ?? 1) * devicePixelRatio
    )
  );
  // Watching a part reports its size once more: only start with the parts that are new.
  for (const part of watched) {
    if (part.isConnected && part.matches(KEEP)) continue;
    sizes.unobserve(part);
    watched.delete(part);
  }
  for (const part of kept) {
    if (watched.has(part)) continue;
    sizes.observe(part);
    watched.add(part);
  }
  if (edge === told) return;
  told = edge;
  tellApp({ type: "keep", bottom: edge });
}

// A timer, not the next frame: behind a video that covers it, the page draws no frames.
function measureSoon() {
  if (measuring) return;
  measuring = true;
  setTimeout(measure, 50);
}

new MutationObserver(measureSoon).observe(document, {
  subtree: true,
  childList: true,
  attributes: true,
  attributeFilter: ["data-video-host-keep", "class", "style", "hidden"],
});
window.addEventListener("resize", measureSoon);
window.addEventListener("scroll", measureSoon, {
  capture: true,
  passive: true,
});
window.visualViewport?.addEventListener("resize", measureSoon);
window.visualViewport?.addEventListener("scroll", measureSoon);
measureSoon();

// Only a press by the user counts, not a click the page makes up. The page still gets the click.
window.addEventListener(
  "click",
  (event) => {
    if (event.isTrusted && event.target?.closest?.(KEEP))
      tellApp({ type: "keep-pressed" });
  },
  true
);
