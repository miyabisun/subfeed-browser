// A phone on its side is too low for YouTube's page: the video does not fit, and what is under
// it is out of sight. There the video gets the whole screen, with a way back and the ways to
// like and to comment in the bands around it. What is decided here touches nothing; youtube.js
// reads the page and lays it out.

// Android draws the line between a phone and a tablet at a shorter side of 600 dp.
const PHONE_BELOW = 600;
// The room an icon needs beside the video, or above and below it.
const BAND = 48 + 8;
// The comments open beside the video and take this share of the width, up to this much.
const PANEL_SHARE = 0.45;
const PANEL_MOST = 400;

/**
 * Whether the video gets the whole screen.
 *
 * - `path`: the path of the page. Only a video (`/watch`) does; Shorts fill a screen themselves.
 * - `screenWidth`, `screenHeight`: the size of the device's screen as it is held now, not of
 *   the page: a keyboard or the system's bars do not change the answer.
 * - `fullScreen`: YouTube shows the video full screen itself.
 */
function stages({ path, screenWidth, screenHeight, fullScreen }) {
  if (path !== "/watch" || fullScreen) return false;
  return screenWidth > screenHeight && screenHeight < PHONE_BELOW;
}

// How far the icons keep from the edges of the screen.
const ICON = 48;
const EDGE = { sides: 12, ends: 8 };
const NO_STRIPS = { top: 0, right: 0, bottom: 0, left: 0 };

/**
 * Where the video and the icons go in a page `width` by `height`.
 *
 * - `panel`: the comments are open. They go beside the video, `panel` pixels wide in the answer.
 * - `safe`: the strips along the edges that the device asks to keep clear, for its camera
 *   (`env(safe-area-inset-*)`).
 *
 * The video is as large as it can be with a band left free on two sides for the icons: `bands`
 * is "sides" for left and right, "ends" for above and below. It stays out of the strips, and in
 * the middle of the screen where they allow. `back` is the top left corner of the way back,
 * `pair` how far the like and the comments keep from the right and the bottom of the room
 * beside the panel. They keep out of the strips too, as far as a band has room: never over
 * the video.
 */
function stageLayout({ width, height, panel, safe = NO_STRIPS }) {
  const beside = panel
    ? Math.min(PANEL_MOST, Math.round(width * PANEL_SHARE))
    : 0;
  const room = Math.max(0, width - beside);
  // The comments cover the strip on their side, and keep their own text out of it.
  const strip = { ...safe, right: panel ? 0 : safe.right };
  const within = (value, least, most) => Math.max(least, Math.min(value, most));
  const place = (bands) => {
    const [across, along] = bands === "sides" ? [BAND, 0] : [0, BAND];
    const free = {
      left: Math.max(across, strip.left),
      right: Math.max(across, strip.right),
      top: Math.max(along, strip.top),
      bottom: Math.max(along, strip.bottom),
    };
    const wide = Math.max(
      0,
      Math.min(
        room - free.left - free.right,
        ((height - free.top - free.bottom) * 16) / 9
      )
    );
    const tall = (wide * 9) / 16;
    return {
      bands,
      wide,
      tall,
      left: within((room - wide) / 2, free.left, room - free.right - wide),
      top: within((height - tall) / 2, free.top, height - free.bottom - tall),
    };
  };
  const [sides, ends] = [place("sides"), place("ends")];
  const video = sides.wide >= ends.wide ? sides : ends;
  const bandRight = room - video.left - video.wide;
  const bandBottom = height - video.top - video.tall;
  // In a band beside the video: in its middle, or further in where a strip asks, up to the
  // video's edge. In a band above or below it: in the corner, in the middle of the band.
  const inBand = (band, least) =>
    Math.max(0, Math.min(Math.max((band - ICON) / 2, least), band - ICON));
  const icons =
    video.bands === "sides"
      ? {
          back: {
            left: inBand(video.left, strip.left),
            top: Math.max(EDGE.sides, strip.top),
          },
          pair: {
            right: inBand(bandRight, strip.right),
            bottom: Math.max(EDGE.sides, strip.bottom),
          },
        }
      : {
          back: {
            left: Math.max(EDGE.ends, strip.left),
            top: inBand(video.top, strip.top),
          },
          pair: {
            right: Math.max(EDGE.ends, strip.right),
            bottom: inBand(bandBottom, strip.bottom),
          },
        };
  const whole = (place) =>
    Object.fromEntries(
      Object.entries(place).map(([side, far]) => [side, Math.round(far)])
    );
  return {
    left: Math.round(video.left),
    top: Math.round(video.top),
    width: Math.round(video.wide),
    height: Math.round(video.tall),
    panel: beside,
    bands: video.bands,
    back: whole(icons.back),
    pair: whole(icons.pair),
  };
}

// Node, for the tests. A content script has no module.
if (typeof module === "object") module.exports = { stages, stageLayout };
