// The app reads a YouTube page again when it is pulled down from its top, unless the page takes
// the touch for its own. What is decided here touches nothing; youtube.js reads the page and
// takes the touch.

/**
 * Whether the page takes a touch for its own at its first move, so the app does not take it
 * for a pull. A touch on the player is the player's. In one column YouTube takes it itself.
 * Beside a free column it does not, and a move down from the player read the page again.
 *
 * - `beside`: how wide a column YouTube keeps free beside the video, 0 in one column.
 * - `fullScreen`: the video fills the screen. The app pulls nothing then.
 * - `onPlayer`: the touch began on the player.
 * - `fingers`: how many fingers touch the screen.
 * - `scrolled`: how far the page is scrolled from its top. Read on, a move down scrolls back.
 * - `across`, `down`: how far the finger moved since it touched. A move up scrolls the page
 *   and stays the page's to scroll; any other may become a pull.
 */
function takesTouch({
  beside,
  fullScreen,
  onPlayer,
  fingers,
  scrolled,
  across,
  down,
}) {
  if (!(beside > 0) || fullScreen || !onPlayer || fingers !== 1) return false;
  if (scrolled > 0) return false;
  const up = -down > Math.abs(across);
  return !up;
}

// Node, for the tests. A content script has no module.
if (typeof module === "object") module.exports = { takesTouch };
