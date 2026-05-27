# Dissertation Presentation Pack (15 minutes)

This folder contains a ready-to-use presentation draft aligned with your updated scope:
- no dedicated comparison-with-tools section
- 1-2 minute live demo at the end

## Files

- `slides.md` - 11-slide structure with timing, on-slide content, image placement, and presenter notes.
- `results_chart_data.csv` - health-score data for a quick bar chart.
- `demo_checklist.md` - concise live-demo runbook and fallback plan.

## Suggested flow

- Main talk: ~13 minutes
- Live demo: ~2 minutes

## How to use this draft quickly

1. Create slides in PowerPoint/Google Slides from `slides.md`.
2. Insert the referenced images from `thesis/figures/images/...`.
3. Add a bar chart using `results_chart_data.csv`.
4. Keep `demo_checklist.md` open during the live demo.

## Generator behavior (robust mode)

- If `--template` points to a missing or unreadable file, the script prints a warning and falls back to the default PowerPoint template.
- If an image path cannot be resolved in `thesis/figures/images/...`, the script also tries `out/figures/images/...` automatically.
- If an image is invalid/corrupted, the script skips it with a warning and continues generating the deck.

## Referenced image paths

- `thesis/figures/images/chapter2/monolith_vs_microservices.drawio.png`
- `thesis/figures/images/chapter2/cyclic-dependency.drawio.png`
- `thesis/figures/images/chapter2/shared_db.drawio.png`
- `thesis/figures/images/chapter3/analysis_pipeline.drawio.png`
- `thesis/figures/images/chapter3/dependency_graph_example.png`
- `thesis/figures/images/chapter4/high-level-diagram.drawio.png`
- `thesis/figures/images/chapter4/clone_page.png`
- `thesis/figures/images/chapter4/analysis_page.png`
- `thesis/figures/images/chapter4/history_page.png`

