const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  opensComments,
  leadsToComments,
  holdOnPage,
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

// A panel open beside the video, as YouTube leaves the page then: held, nothing in it to press.
const underPanel = { beside: 500, panel: true, held: true, lifted: false };

test("lifts YouTube's hold on the page while a panel shows beside the video", () => {
  assert.equal(holdOnPage(underPanel), "lift");
  assert.equal(holdOnPage({ ...underPanel, beside: 256 }), "lift");
  // YouTube holds the page again each time a panel opens or closes over another.
  assert.equal(holdOnPage({ ...underPanel, lifted: true }), "lift");
});

test("has nothing to lift from a page that is not held", () => {
  assert.equal(holdOnPage({ ...underPanel, held: false }), null);
  assert.equal(holdOnPage({ ...underPanel, held: false, lifted: true }), null);
});

test("leaves YouTube's hold alone when a panel covers the page in one column", () => {
  assert.equal(holdOnPage({ ...underPanel, beside: 0 }), null);
});

test("gives the hold back when the panel it lifted it for comes to cover the page", () => {
  // The device was turned upright with the panel open.
  assert.equal(
    holdOnPage({ beside: 0, panel: true, held: false, lifted: true }),
    "restore"
  );
  assert.equal(
    holdOnPage({ beside: 0, panel: true, held: true, lifted: true }),
    null
  );
});

test("does not hold a page in one column that YouTube never held", () => {
  assert.equal(
    holdOnPage({ beside: 0, panel: true, held: false, lifted: false }),
    null
  );
});

test("leaves a hold that is not a panel's alone", () => {
  // A dialog holds what it covers by itself.
  assert.equal(holdOnPage({ ...underPanel, panel: false }), null);
  assert.equal(
    holdOnPage({ beside: 0, panel: false, held: false, lifted: true }),
    null
  );
});

test("takes back a hold it gave once no panel is left to give it for", () => {
  // YouTube lets go of the page when a panel closes. Should it have done so before this
  // script gave the hold back, nothing else would ever lift it.
  assert.equal(
    holdOnPage({ beside: 0, panel: false, held: true, lifted: true }),
    "lift"
  );
  assert.equal(
    holdOnPage({ beside: 500, panel: false, held: true, lifted: true }),
    "lift"
  );
});
