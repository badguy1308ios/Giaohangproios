# Customer coordinate integrity

Reported: saved customer coordinates sometimes change and pins move far away.
The screenshot shows `10.797606666666665, 106.99663333333335`. Precision alone
does not prove corruption. The original fix and a device event history were not
available, so the exact event that changed this customer's record is unconfirmed.

Confirmed code hazards fixed:

- Detail photo callbacks replaced the entire captured customer. They now change
  only the photo on the current customer record.
- An edit form could replace a customer changed since the form opened. Saving
  now compares against the original record and rejects a stale form.
- ZIP import computed a replacement list in the background. It now rejects
  applying that list if any customer was edited, added or deleted meanwhile.
- ZIP merging filled individual missing axes independently. It now imports a
  validated pair only when both existing axes are empty. Existing nonempty or
  partial fixes remain untouched. Filling an empty address now saves the address
  together with its coordinates.
- Coordinate input extracted the first two numbers from arbitrary text, including
  degree/minute/second text or scientific notation. Parsing now accepts complete
  decimal numbers or complete pairs; invalid newly entered fixes cannot be saved.
  No automatic rounding, averaging, geocoding or guessing is introduced.
- Map pins and route points preferred order coordinates while order details
  preferred customer coordinates, sometimes mixing customer and order axes.
  A valid saved primary customer pair now takes priority consistently; fallback
  uses a complete valid order pair.
- The map lookup used a mutable customer list as its Compose cache key. It now
  receives an immutable snapshot, so changing a saved fix invalidates that lookup.
- Saving changes to primary/extra coordinate pairs displays the old and proposed
  pairs for confirmation, including switching the primary address or removing one.

Automatic scheduled sync writes backup snapshots; it does not replace customer
coordinates. Explicit backup restore still intentionally restores the selected
snapshot. Route learning and GPS marker refresh do not write customer coordinates.
Existing incorrect data is not repaired automatically because the original fix
cannot be inferred safely.

Regression coverage: exact long decimal preservation, photo edits, stale forms,
concurrent imports, atomic coordinate pairs, decimal commas, scientific notation,
pair pasting, malformed input, range validation, primary/extra changes, and map
coordinate precedence. CI runs `testDebugUnitTest assembleDebug`.

Device checks:

1. Record both coordinates of one known-good customer. Change its photo, name
   and note, reopen the app and verify both strings and the pin are unchanged.
2. Change the primary or extra address coordinates: verify the old/new dialog;
   cancel must not persist anything, confirm must persist the proposed pair.
3. Load an order for that customer with different coordinates; the saved customer
   primary fix must be used for its pin and route.
4. Import an older ZIP for an existing customer; nonempty coordinates must remain.
   An incomplete existing pair must not be completed using the older ZIP's axis.
5. Paste a complete coordinate pair into either field and verify both values.
   Invalid text must not silently become a plausible but unrelated position.
