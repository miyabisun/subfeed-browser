// On a wide screen YouTube keeps a column free beside a video, for the recommendations this app
// removes. The comments go there instead. What is decided here touches nothing; youtube.js
// reads the page and presses.

// A press that showed nothing is tried again, a few times: the page may not have been ready.
const PRESS_AGAIN_AFTER = 2000;
const PRESSES = 3;

/**
 * Whether to press the way into the comments now, so they open beside the video.
 *
 * - `path`: the path of the page. Only a video (`/watch`) has the column; Shorts do not.
 * - `beside`: how wide a column YouTube keeps free beside the video, 0 in one column.
 * - `fullScreen`: the video fills the screen. There is no column to see then.
 * - `entry`: the page shows a way into its comments. A live stream and a video with comments
 *   turned off show none.
 * - `panel`: a panel is showing already, the comments or another.
 * - `seen`: the comments were open on this page before. They are gone because the user closed
 *   them, and stay closed.
 * - `presses`, `sincePress`: how often the way in was pressed for this page, and how many
 *   milliseconds ago.
 */
function opensComments({
  path,
  beside,
  fullScreen,
  entry,
  panel,
  seen,
  presses,
  sincePress,
}) {
  if (path !== "/watch" || !(beside > 0)) return false;
  if (fullScreen || !entry || panel || seen) return false;
  return (
    presses === 0 || (presses < PRESSES && sincePress >= PRESS_AGAIN_AFTER)
  );
}

/**
 * Whether the box under a video leads to its comments, from the names of the elements in it.
 * It holds a comment to tease with, or a place to write the first one. With comments turned
 * off it holds a notice instead (`yt-comments-entry-point-message-view-model`), sometimes
 * beside an invitation to another app: pressing those opens no comments.
 */
function leadsToComments(names) {
  return names.some((name) => name.startsWith("yt-comment-"));
}

// Node, for the tests. A content script has no module.
if (typeof module === "object")
  module.exports = { opensComments, leadsToComments };
