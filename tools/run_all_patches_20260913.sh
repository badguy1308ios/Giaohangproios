#!/usr/bin/env bash
set -euo pipefail

# All historical feature patches are already baked into the source.
# Re-running them on every push caused duplicate branches/modifiers.
# Keep only the idempotent cleanup guard so existing features stay intact.
python3 tools/dedupe_generated_mainactivity_20260914.py
python3 tools/fix_order_detail_multiline_rows_20260914.py
python3 tools/preserve_route_stt_after_delivery_20260914.py
python3 tools/show_user_location_in_customer_picker_20260914.py
python3 tools/fix_duplicate_route_numbering_20260914.py
python3 tools/isolate_route_stt_mutation_20260914.py
python3 tools/map_list_to_order_group_search_20260914.py
python3 tools/focus_map_list_by_order_code_20260914.py
python3 tools/fix_single_order_stt_click_20260914.py
python3 tools/fix_edit_stt_focus_isolation_20260914.py
python3 tools/hide_delivered_button_20260914.py
