// Select the compact editor preview dropdown through its accessible controls.
export async function selectPreview(page, format) {
    const toolbar = page.locator('.compile-toolbar');
    await toolbar.getByRole('button', {name: /^(PDF preview|Word preview|Preview format)$/, exact: true}).click();
    await toolbar.getByRole('group', {name: /^(PDF preview|Word preview|Preview format)$/, exact: true})
        .getByRole('button', {name: format + ' preview', exact: true}).click();
}
