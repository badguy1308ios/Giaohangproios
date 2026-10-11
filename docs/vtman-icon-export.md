# VTMAN export: count the left-hand icons

Agreed with Hải: search the exact waybill; one filtered card, no scrolling, shop
fits on one line. Count the image elements on the LEFT of this card from top to
bottom. Read the text to their right in this order:

1. Shop
2. Recipient
3. Address (all lines)
4. Goods (all lines)
5. Service, if present; otherwise empty

There is no icon-shape classification, parent-row requirement, exact 4–5 count
gate or rejection for equally close text. Nested images at the same position
count once. Positions after number five are ignored. Text is collected until
the next icon's row begins; merged parent labels are not repeated alongside
child text. The call icon on the right and images outside the card are excluded.

The existing checks for the correct waybill, stable content and missing actual
required data remain. Service is optional both while parsing and when saving.
No guessing field types from shop/customer names, address words or service codes.

## Resume

On the first filtered card, locate the image on the RIGHT of recipient row
(left icon number 2) and save its center as the fixed call X/Y. No shape/name
matching, screen-percentage fallback or manual point selection is used.
Tap the exact saved call X/Y. A manual pause, an error, stopping the popup or
accessibility reconnect must not clear that point. Starting again with a saved
point bypasses point selection and re-reads the current waybill with fresh retry
state. Persist X/Y in the same checkpoint as the queue for process recreation.
A newly loaded/scanned waybill list starts a new session and clears the point.
Old checkpoints without the recipient_icon source marker discard their old
coordinates and detect the real call icon from the next filtered card.

## Verification

Parser fixtures cover different icon positions and sizes, nested/extra images,
flat accessibility trees, multiline/single-node addresses, identical names,
absent/empty service, and images outside the order. Queue tests cover retaining
X/Y and the current waybill after failure, discarding partial data for a retry,
a new queue, and saving an order without service.

Android still needs to expose generic image elements with bounds. CI fixtures
do not establish how the installed VTMAN version exposes its screen; real-device
verification remains necessary. Build 44–45 rejected some visible image layouts;
the exact-count, parent-group and distance-tie gates have now been removed.
