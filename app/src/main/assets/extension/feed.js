// Runs in pages that are not YouTube or Google: the list of videos and its sign-in.
// Tells the page it is inside this app, and passes on its requests to open a video.
document.documentElement.dataset.videoHost = "1";

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
  browser.runtime.sendNativeMessage("browser", {
    type: message.type,
    url: message.url,
  });
});
