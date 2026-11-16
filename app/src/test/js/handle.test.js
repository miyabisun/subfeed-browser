const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readableHandle } = require("../../main/assets/extension/handle.js");

test("reads a handle that was left as it stands in an address", () => {
  assert.equal(
    readableHandle("@%E3%81%82%E3%81%84%E3%81%86_ch123"),
    "@あいう_ch123"
  );
  assert.equal(readableHandle("@caf%C3%A9"), "@café");
  assert.equal(readableHandle("@%e3%81%82"), "@あ");
});

test("keeps the space around the handle", () => {
  assert.equal(readableHandle(" @%E3%81%82\n"), " @あ\n");
});

test("leaves a handle that reads as it is", () => {
  assert.equal(readableHandle("@あいう_ch123"), null);
  assert.equal(readableHandle("@GoogleDevelopers"), null);
  assert.equal(readableHandle("@100%"), null);
});

test("leaves what is not a handle alone", () => {
  assert.equal(readableHandle("%E3%81%82"), null);
  assert.equal(readableHandle("50%OFF"), null);
  assert.equal(readableHandle("@%E3%81%82 さんへ"), null);
  assert.equal(readableHandle("to @%E3%81%82"), null);
  assert.equal(readableHandle("@%E3%81%82/videos"), null);
  assert.equal(readableHandle(""), null);
});

test("leaves escapes that are no text", () => {
  assert.equal(readableHandle("@%E3%82"), null);
  assert.equal(readableHandle("@%FF%FE"), null);
});

test("makes up no handle with a space, a line or a path in it", () => {
  assert.equal(readableHandle("@a%20b"), null);
  assert.equal(readableHandle("@a%0Ab"), null);
  assert.equal(readableHandle("@a%00b"), null);
  assert.equal(readableHandle("@a%2Fb"), null);
});

test("reads a handle whose @ stands in a run of its own before it", () => {
  assert.equal(readableHandle("%E3%81%82%E3%81%84_ch123", "@"), "あい_ch123");
  assert.equal(readableHandle("%E3%81%82\n", "by @"), "あ\n");
});

test("leaves escapes that follow no @ of a handle", () => {
  assert.equal(readableHandle("%E3%81%82", "mail@"), null);
  assert.equal(readableHandle("%E3%81%82", "@ "), null);
  assert.equal(readableHandle(" %E3%81%82", "@"), null);
  assert.equal(readableHandle("%E3%81%82", "50"), null);
});
