/** Shared contract for both DOCX editing and LaTeX text-sync previews. */
export type WordPreviewHandle = {
    /** Save pending changes; false means navigation/export must be cancelled. */
    flush: () => Promise<boolean>;
};
