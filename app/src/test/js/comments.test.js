const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  opensComments,
  leadsToComments,
} = require("../../main/assets/extension/comments.js");

// A video on a wide screen, as YouTube first shows it: a free column beside it, nothing open.
const beside = {
  path: "/watch",
  beside: 500,
  fullScreen: false,
  entry: true,
  panel: false,
  seen: false,
  presses: 0,
  sincePress: Infinity,
};

test("opens the comments in the column YouTube keeps free beside a video", () => {
  assert.equal(opensComments(beside), true);
  assert.equal(opensComments({ ...beside, beside: 256 }), true);
});

test("leaves a video in one column alone", () => {
  assert.equal(opensComments({ ...beside, beside: 0 }), false);
});

test("waits while the video fills the screen", () => {
  assert.equal(opensComments({ ...beside, fullScreen: true }), false);
});

test("leaves Shorts and pages that are not a video alone", () => {
  for (const path of ["/shorts/abc", "/shorts/", "/", "/results", "/@a/videos"])
    assert.equal(opensComments({ ...beside, path }), false, path);
});

test("has nothing to open on a video without a way into its comments", () => {
  assert.equal(opensComments({ ...beside, entry: false }), false);
});

test("leaves the column to a panel that is showing there", () => {
  assert.equal(opensComments({ ...beside, panel: true }), false);
});

test("does not open the comments again once they were open on this page", () => {
  // The user closed them, or another panel took their place.
  assert.equal(opensComments({ ...beside, seen: true }), false);
  assert.equal(opensComments({ ...beside, seen: true, presses: 1 }), false);
});

test("presses again only after a press had time to show the comments", () => {
  assert.equal(
    opensComments({ ...beside, presses: 1, sincePress: 300 }),
    false
  );
  assert.equal(
    opensComments({ ...beside, presses: 1, sincePress: 2000 }),
    true
  );
});

test("gives up after three presses that showed nothing", () => {
  assert.equal(
    opensComments({ ...beside, presses: 2, sincePress: 60000 }),
    true
  );
  assert.equal(
    opensComments({ ...beside, presses: 3, sincePress: 60000 }),
    false
  );
});

test("tells the way into the comments by what the box under a video holds", () => {
  // A comment to tease with, or a place to write the first one.
  assert.equal(
    leadsToComments([
      "div",
      "yt-carousel-item-view-model",
      "yt-comment-teaser-carousel-item-view-model",
      "comments-entry-point-teaser-view-model",
    ]),
    true
  );
  assert.equal(
    leadsToComments(["yt-comment-input-box-carousel-item-view-model", "span"]),
    true
  );
});

test("does not take a notice or an advertisement in that box for the way in", () => {
  // Comments turned off: a notice with a link to read more.
  assert.equal(
    leadsToComments([
      "yt-carousel-item-view-model",
      "yt-comments-entry-point-message-view-model",
      "span",
      "a",
    ]),
    false
  );
  // An invitation to another app, beside that notice.
  assert.equal(
    leadsToComments([
      "yt-text-carousel-item-view-model",
      "button-view-model",
      "a",
      "yt-comments-entry-point-message-view-model",
    ]),
    false
  );
  assert.equal(leadsToComments([]), false);
});
