# VTMAN export: ordered icon rows

Agreed workflow: enter the exact waybill, wait for the filtered result, do not
scroll; shop is one line. Read the left image-widget column in order: shop,
recipient, address, goods, optional service. Use the smallest enclosing
accessibility row containing that icon and its text; retain all child text lines.
The right-hand call image is not a field icon. Tap the exact saved call X/Y;
do not search for the recipient name to calculate another Y.

An absent or empty fifth service row is valid. Missing required rows retain the
current waybill and report a parsing failure; they are not treated as no data.
There is no text-order fallback masquerading as icon recognition.

Capability limit: Android must expose ImageView/ImageButton elements and their
row hierarchy. Include-not-important-views is requested, but cannot force a
custom-drawn icon to exist in the accessibility tree. Without that structure,
export retries then pauses with an image-count diagnostic. Actual VTMAN device
verification remains required; unit fixtures cannot establish icon exposure.

Device checks: export a card with wrapped address/goods, one without service,
and a card with identical shop/recipient names. Verify all CSV fields and phone,
including the existing two-number picker flow. If a row-structure error appears,
record the error and obtain a VTMAN accessibility hierarchy before changing the
parser; do not silently switch to guessed text positions.

## Build 45 correction
The device log reported 10 image elements but no parsed rows. That establishes
failure before the call tap, not which hierarchy assumption failed. Restrict
images to the card above its completion action and allow flattened rows to be
matched by icon/text bounds. Reject text overlapping multiple field icons.
Hierarchy grouping remains preferred; geometry never substitutes text ordinal
positions for icons. Tests cover flattened multiline rows and unrelated images.
