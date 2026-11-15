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
