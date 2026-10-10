# Hyphenated VTMAN waybills

Reported CSV contains SGSIND09102026-01 and 154069642893 but imports one item.
Both CSV validation and visible-card scanning previously accepted only letters
and digits. They now share validation allowing internal hyphen-separated segments
with the existing 8–24 character limit and at least one digit. Preserve the full
suffix through scan, CSV, queue and exact result matching. Exact matching must not
accept the prefix SGSIND09102026 or confuse -01 with -02.

Six regression tests cover the reported CSV, separate/merged status rows, complete
identity, malformed codes, existing codes/deduplication and full record parsing.
Device verification: reload the same CSV (expect 2), scan a list containing the
hyphenated card (expect the full code), export both and check the saved records.
