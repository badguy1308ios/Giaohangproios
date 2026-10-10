# VTMAN order 154069642893

Video: the result card is visible with shop `Thành công`, recipient `LONG LAST`,
COD `395,000 đ`, and service `COD,PXD`. The export does not enter the dialer for
this card and ends with a no-data skip.

Confirmed parser defect: both positioned and text parsers treated the first
`Thành công` string as the footer. The positioned parser cut before the shop,
and screen-noise filtering also discarded that shop name. Identical text at
separate positions was additionally deduplicated, potentially shifting fields.

Fix: preserve content rows (including equal names); recognize an explicit Button
or a completion label after the four content rows as the footer. Keep the next
order header as a hard boundary. Regression tests transcribe the video and cover
both parsers, equal names, goods named Thành công, missing footer, and a partial
card ending in an explicit button.

The video does not expose the accessibility tree or installed app version, so the
exact path producing the final no-data log cannot be confirmed from video alone.
The service now skips only on an explicit empty-result label with no matching
card after the wait. A timeout without that evidence retries/pauses the current
item instead of discarding it. A matching card takes priority over empty labels.

Device check: load 154069642893 plus one known-good order, run Export, verify
shop/recipient/address/goods/COD/service and the phone obtained from the call UI.
The number embedded in goods is the shop contact and must not become the recipient
phone. Expect both items saved; call/back behavior is unchanged.
