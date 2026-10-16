# VTMAN in-app phone picker

Confirmed control-flow defect: readPhone recognized the in-app phone picker but still rejected it when phoneScreenSignature matched the pre-tap baseline. That signature includes only the first 24 texts, so an unchanged order card can mask a newly opened bottom sheet. Existing picker tests covered number parsing but not this gate.

The picker now bypasses the external-dialer signature requirement. External dialer baseline checks remain. Picker lookup also checks VTMAN application windows, enabled through flagRetrieveInteractiveWindows, without combining rows from unrelated windows. The same lookup is used while waiting for dismissal. The existing flow reads the first phone row, sends one Back, waits for the picker to disappear, then resumes the queue, including the final item.

Regression test reproduces unchanged first 24 order texts plus a bottom sheet containing 0868217663 then 0969859601. It verifies the surface is accepted and the top number selected. Another test retains stale-dialer rejection. Existing parser tests cover reversed traversal order, combined descriptions and formatted numbers.

CI runs all unit tests and assembles the APK. Device validation is still needed for the actual VTMAN accessibility tree: two-number order between normal orders and as the final item; top phone saved, no outgoing call, one Back, queue advances correctly.
