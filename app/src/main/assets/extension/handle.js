// YouTube names a channel under a video's title by its handle, which it reads out of the
// channel's address. In this engine it shows the address as it stands: "@%E3%81%82" for "@あ".
// What is decided here touches nothing; youtube.js rewrites the text.

// A run of text that is one word and nothing else, with an escape of an address in it.
const ESCAPED = /^(\s*)([^\s/?#]*%[0-9a-f]{2}[^\s/?#]*)(\s*)$/i;
// The "@" of a handle, where YouTube sets it in a run of its own before the name.
const OPENS_A_HANDLE = /(^|\s)@$/;
// What no handle holds: a space, a line, a control, a step of a path.
const NOT_IN_A_HANDLE = /[\s\u0000-\u001f\u007f/]/;

/**
 * The text to show in place of `text`, one run of text of the page, or null to leave it as it
 * is.
 *
 * - `before`: what the page says right before the run.
 *
 * Only a whole handle is read: a sentence that mentions an address stays what its writer wrote.
 */
function readableHandle(text, before = "") {
  const [, lead, word, trail] = ESCAPED.exec(text) ?? [];
  if (!word) return null;
  const follows = lead === "" && OPENS_A_HANDLE.test(before);
  if (!word.startsWith("@") && !follows) return null;
  let read;
  try {
    read = decodeURIComponent(word);
  } catch {
    return null;
  }
  return NOT_IN_A_HANDLE.test(read) ? null : lead + read + trail;
}

// Node, for the tests. A content script has no module.
if (typeof module === "object") module.exports = { readableHandle };
