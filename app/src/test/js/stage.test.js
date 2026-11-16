const { test } = require("node:test");
const assert = require("node:assert/strict");
const { stages, stageLayout } = require("../../main/assets/extension/stage.js");

// A video on a phone on its side: 411 x 923 dp, as a Pixel 9.
const phone = {
  path: "/watch",
  screenWidth: 923,
  screenHeight: 411,
  fullScreen: false,
};

test("gives a video the whole screen of a phone on its side", () => {
  assert.equal(stages(phone), true);
  assert.equal(stages({ ...phone, screenWidth: 800, screenHeight: 360 }), true);
  assert.equal(stages({ ...phone, screenWidth: 960, screenHeight: 411 }), true);
});

test("leaves a phone held upright as it is", () => {
  assert.equal(
    stages({ ...phone, screenWidth: 411, screenHeight: 923 }),
    false
  );
  assert.equal(
    stages({ ...phone, screenWidth: 500, screenHeight: 500 }),
    false
  );
});

test("leaves a tablet to YouTube's own columns", () => {
  assert.equal(
    stages({ ...phone, screenWidth: 1216, screenHeight: 761 }),
    false
  );
  assert.equal(
    stages({ ...phone, screenWidth: 960, screenHeight: 600 }),
    false
  );
  assert.equal(stages({ ...phone, screenWidth: 960, screenHeight: 599 }), true);
});

test("leaves pages that are no video as they are", () => {
  assert.equal(stages({ ...phone, path: "/shorts/aqz-KE-bpKQ" }), false);
  assert.equal(stages({ ...phone, path: "/results" }), false);
  assert.equal(stages({ ...phone, path: "/" }), false);
  assert.equal(stages({ ...phone, path: "/@BlenderOfficial/videos" }), false);
});

test("leaves a video that YouTube shows full screen to YouTube", () => {
  assert.equal(stages({ ...phone, fullScreen: true }), false);
});

// What a layout says about the video, apart from where the icons go.
function video(layout) {
  const { left, top, width, height, panel, bands } = layout;
  return { left, top, width, height, panel, bands };
}

test("fills the height and leaves a band on each side", () => {
  assert.deepEqual(
    video(stageLayout({ width: 923, height: 411, panel: false })),
    {
      left: 96,
      top: 0,
      width: 731,
      height: 411,
      panel: 0,
      bands: "sides",
    }
  );
  assert.deepEqual(
    video(stageLayout({ width: 960, height: 411, panel: false })),
    {
      left: 115,
      top: 0,
      width: 731,
      height: 411,
      panel: 0,
      bands: "sides",
    }
  );
});

test("keeps bands wide enough for the icons on a screen as wide as the video", () => {
  assert.deepEqual(
    video(stageLayout({ width: 640, height: 360, panel: false })),
    {
      left: 56,
      top: 32,
      width: 528,
      height: 297,
      panel: 0,
      bands: "sides",
    }
  );
});

test("puts the bands above and below where that leaves a larger video", () => {
  assert.deepEqual(
    video(stageLayout({ width: 480, height: 360, panel: false })),
    {
      left: 20,
      top: 56,
      width: 441,
      height: 248,
      panel: 0,
      bands: "ends",
    }
  );
});

test("makes room for the comments beside the video", () => {
  assert.deepEqual(
    video(stageLayout({ width: 923, height: 411, panel: true })),
    {
      left: 0,
      top: 58,
      width: 523,
      height: 294,
      panel: 400,
      bands: "ends",
    }
  );
  assert.deepEqual(
    video(stageLayout({ width: 640, height: 360, panel: true })),
    {
      left: 0,
      top: 81,
      width: 352,
      height: 198,
      panel: 288,
      bands: "ends",
    }
  );
});

test("shrinks with the room a keyboard leaves", () => {
  assert.deepEqual(
    video(stageLayout({ width: 923, height: 150, panel: false })),
    {
      left: 328,
      top: 0,
      width: 267,
      height: 150,
      panel: 0,
      bands: "sides",
    }
  );
});

test("never lays out a video of a negative size", () => {
  const { width, height } = stageLayout({
    width: 100,
    height: 60,
    panel: true,
  });
  assert.ok(width >= 0 && height >= 0);
});

test("puts the way back in the top left corner and the pair in the bottom right", () => {
  const beside = stageLayout({ width: 923, height: 411, panel: false });
  assert.deepEqual(beside.back, { left: 24, top: 12 });
  assert.deepEqual(beside.pair, { right: 24, bottom: 12 });
  const along = stageLayout({ width: 923, height: 411, panel: true });
  assert.deepEqual(along.back, { left: 8, top: 5 });
  assert.deepEqual(along.pair, { right: 8, bottom: 5 });
});

// The camera of a phone on its side sits in a strip along one of the short edges.
const camera = { top: 0, right: 0, bottom: 0, left: 54 };

test("keeps the video centred on the screen when a band covers the camera's strip", () => {
  const layout = stageLayout({
    width: 923,
    height: 411,
    panel: false,
    safe: camera,
  });
  assert.deepEqual(video(layout), {
    left: 96,
    top: 0,
    width: 731,
    height: 411,
    panel: 0,
    bands: "sides",
  });
  assert.deepEqual(
    layout.back,
    { left: 48, top: 12 },
    "as far from the camera as the band allows, never over the video"
  );
  assert.deepEqual(layout.pair, { right: 24, bottom: 12 });
});

test("keeps the video out of the camera's strip beside the comments", () => {
  const layout = stageLayout({
    width: 923,
    height: 411,
    panel: true,
    safe: camera,
  });
  assert.deepEqual(video(layout), {
    left: 54,
    top: 74,
    width: 469,
    height: 264,
    panel: 400,
    bands: "ends",
  });
  assert.deepEqual(layout.back, { left: 54, top: 13 });
  assert.deepEqual(layout.pair, { right: 8, bottom: 13 });
});

test("keeps the pair out of a strip on the other side", () => {
  const safe = { top: 0, right: 54, bottom: 0, left: 0 };
  const layout = stageLayout({ width: 923, height: 411, panel: false, safe });
  assert.equal(layout.left, 96);
  assert.deepEqual(layout.back, { left: 24, top: 12 });
  assert.deepEqual(layout.pair, { right: 48, bottom: 12 });
});

test("gives a strip wider than a band the room it needs", () => {
  const safe = { top: 0, right: 0, bottom: 0, left: 120 };
  const layout = stageLayout({ width: 923, height: 411, panel: false, safe });
  assert.equal(layout.left, 120, "the video starts where the strip ends");
  assert.equal(layout.width, 731);
});
