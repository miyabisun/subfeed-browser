// YouTube greets a visitor who is not signed in with a dialog over the video that invites to
// its app. youtube.css takes the invitation out, as everywhere. What is decided here touches
// nothing; youtube.js reads the dialog and presses.

/**
 * Whether to close a dialog for the user: it was an invitation to the app, and with that gone
 * there is nothing left to do in it but to close it.
 *
 * - `invites`: the dialog holds a link that opens another app.
 * - `closes`: how many ways to close it show.
 * - `others`: how many other things to press show. A dialog that also offers to sign in, as the
 *   one a like brings up, has those.
 */
function closesInvitation({ invites, closes, others }) {
  return invites && closes > 0 && others === 0;
}

// Node, for the tests. A content script has no module.
if (typeof module === "object") module.exports = { closesInvitation };
