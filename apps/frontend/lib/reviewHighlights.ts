import { StateEffect, StateField } from "@codemirror/state";
import { Decoration, EditorView, type DecorationSet } from "@codemirror/view";

export const setReviewHighlights =
    StateEffect.define<{ from: number; to: number }[]>();
export const reviewHighlights = StateField.define<DecorationSet>({
    create: () => Decoration.none,
    update(value, transaction) {
        value = value.map(transaction.changes);
        for (const effect of transaction.effects) {
            if (effect.is(setReviewHighlights)) {
                value = Decoration.set(
                    effect.value
                        .filter(
                            (r) =>
                                r.from >= 0 &&
                                r.to > r.from &&
                                r.to <= transaction.state.doc.length,
                        )
                        .map((r) =>
                            Decoration.mark({
                                class: "review-highlight",
                            }).range(r.from, r.to),
                        ),
                    true,
                );
            }
        }
        return value;
    },
    provide: (field) => EditorView.decorations.from(field),
});
