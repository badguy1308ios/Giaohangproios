# Customer identity and map pin consistency

Reported: STT 41 / PKE1540020182 (Thanh Nguyen) appears near N23 while
customer coordinate picker displays 10.808106, 106.999992 on D18.

Confirmed code defect: groupRepresentative first resolves a customer by phone,
but BaseMapScreen resolves another customer from phone OR name OR address and
chooses the earliest record. An earlier namesake can therefore override the right
customer's coordinates. Build 35's customer-coordinate preference exposed this
unsafe secondary lookup. That earlier coordinate fix was incomplete.

Remove the second lookup entirely. Marker creation consumes the coordinates of
the already-resolved delivery group. Share phone normalization and matching
between delivery groups and opening customer details; include secondary phones,
+84 and 0084. Duplicate phone records consistently use the first record, matching
existing customer-detail behavior (previous grouping used the last). No customer
records or stored coordinates are rewritten or merged by this fix.

Six regression tests exercise the group-to-marker path with earlier namesakes,
identical addresses, secondary/normalized phones, duplicate phones, unknown/empty
phones, coordinate edits and GPS changes. Route numbers and order IDs are retained.

The screenshots alone cannot establish the device's duplicate records or whether
a picker selection was saved. Device check: save picker, save customer form and
confirm coordinate change; reopen to verify the saved pair, then inspect STT 41
and navigation. Cancelling a draft must leave the previous saved pin unchanged.
