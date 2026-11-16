const { test } = require("node:test");
const assert = require("node:assert/strict");
const { takesTouch } = require("../../main/assets/extension/pull.js");

// One finger on the player of a video on a wide screen, the page at its top, moving down.
const down = {
  beside: 500,
  fullScreen: false,
  onPlayer: true,
  fingers: 1,
  scrolled: 0,
  across: 0,
  down: 12,
};

test("takes a move down that began on the player beside a free column", () => {
  assert.equal(takesTouch(down), true);
  assert.equal(takesTouch({ ...down, beside: 256, across: -5 }), true);
});

test("takes a move that begins sideways, which may turn down later", () => {
  assert.equal(takesTouch({ ...down, across: 12, down: 0 }), true);
  assert.equal(takesTouch({ ...down, across: -12, down: -3 }), true);
  assert.equal(takesTouch({ ...down, across: 0, down: 0 }), true);
});

test("leaves a move up to scroll the page", () => {
  assert.equal(takesTouch({ ...down, down: -12 }), false);
  assert.equal(takesTouch({ ...down, across: 5, down: -12 }), false);
});

test("leaves a page that was read on to scroll back up", () => {
  assert.equal(takesTouch({ ...down, scrolled: 40 }), false);
});

test("leaves a touch that began beside or under the player alone", () => {
  assert.equal(takesTouch({ ...down, onPlayer: false }), false);
});

test("leaves a video in one column to YouTube, which takes the touch itself", () => {
  assert.equal(takesTouch({ ...down, beside: 0 }), false);
});

test("leaves a video that fills the screen alone", () => {
  assert.equal(takesTouch({ ...down, fullScreen: true }), false);
});

test("leaves two fingers alone", () => {
  assert.equal(takesTouch({ ...down, fingers: 2 }), false);
});
