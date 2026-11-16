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
// needs to know where on the screen it ends, and where what it has opened out of itself ends:
// a menu under a header shows over the video while it is open.
const KEEP = "[data-video-host-keep]";
let told;
let measuring = false;
const sizes = new ResizeObserver(measureSoon);
const watched = new Set();

function measure() {
  measuring = false;
  const kept = document.querySelectorAll(KEEP);
  let bottom = 0;
  let over = 0;
  for (const part of kept) {
    const place = part.getBoundingClientRect();
    bottom = Math.max(bottom, place.bottom);
    // What hangs out of the part counts into how far its content reaches.
    over = Math.max(over, place.top + part.clientTop + part.scrollHeight);
  }
  // In pixels of the screen, also while the page is zoomed by a pinch.
  const view = window.visualViewport;
  const onScreen = (y) =>
    Math.max(
      0,
      Math.round(
        (y - (view?.offsetTop ?? 0)) * (view?.scale ?? 1) * devicePixelRatio
      )
    );
  const edges = { bottom: onScreen(bottom), over: onScreen(over) };
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
  const report = `${edges.bottom} ${edges.over}`;
  if (report === told) return;
  told = report;
  tellApp({ type: "keep", ...edges });
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
