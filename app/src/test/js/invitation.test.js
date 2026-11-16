const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  closesInvitation,
} = require("../../main/assets/extension/invitation.js");

test("closes a dialog that only invited to the app", () => {
  assert.equal(closesInvitation({ invites: true, closes: 1, others: 0 }), true);
});

test("leaves a dialog that offers something else, as a sign-in", () => {
  assert.equal(
    closesInvitation({ invites: true, closes: 0, others: 2 }),
    false
  );
  assert.equal(
    closesInvitation({ invites: true, closes: 1, others: 1 }),
    false
  );
});

test("leaves a dialog that is no invitation", () => {
  assert.equal(
    closesInvitation({ invites: false, closes: 1, others: 0 }),
    false
  );
});

test("presses nothing where there is no way to close", () => {
  assert.equal(
    closesInvitation({ invites: true, closes: 0, others: 0 }),
    false
  );
});
