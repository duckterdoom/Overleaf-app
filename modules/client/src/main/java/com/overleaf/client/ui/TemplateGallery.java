package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.ProjectTemplates;
import java.util.*;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Searchable catalog; cards are built exclusively from resource metadata. */
final class TemplateGallery {
    private static final Map<String, String> ICONS =
            Map.of(
                    "journal",
                    "file",
                    "books",
                    "book",
                    "letters",
                    "pen",
                    "assignments",
                    "calc",
                    "posters",
                    "globe",
                    "presentations",
                    "bulb",
                    "reports",
                    "code",
                    "cv",
                    "file",
                    "theses",
                    "cap");

    static void show(StackPane host, boolean vi, String initialCategory, Consumer<String> create) {
        UiModal modal =
                UiModal.create(
                                host,
                                UiSupport.FOLDER,
                                UiModal.Tone.NEUTRAL,
                                vi ? "Thư viện mẫu dự án" : "Project template library",
                                vi
                                        ? "Mẫu tự viết, có nguồn và tài nguyên để biên dịch ngay."
                                        : "Original editable projects with sources and assets ready to compile.")
                        .localized(vi)
                        .width(1000);
        TextField search = new TextField();
        search.setId("template-search");
        search.setPromptText(
                vi ? "Tìm tên, mô tả hoặc thẻ…" : "Search titles, descriptions or tags…");
        search.getStyleClass().add("modal-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        ComboBox<String> category = new ComboBox<>(), tag = new ComboBox<>();
        category.setId("template-category");
        tag.setId("template-tag");
        category.getItems().add("");
        category.getItems()
                .addAll(
                        ProjectTemplates.categories().stream()
                                .map(ProjectTemplates.Category::id)
                                .toList());
        category.setConverter(
                new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(String id) {
                        return id == null || id.isEmpty()
                                ? (vi ? "Tất cả nhóm" : "All categories")
                                : ProjectTemplates.categories().stream()
                                        .filter(c -> c.id().equals(id))
                                        .findFirst()
                                        .orElseThrow()
                                        .title(vi);
                    }

                    @Override
                    public String fromString(String text) {
                        return text;
                    }
                });
        category.setValue(initialCategory == null ? "" : initialCategory);
        tag.getItems().add("");
        tag.getItems()
                .addAll(
                        ProjectTemplates.all().stream()
                                .flatMap(t -> t.tags().stream())
                                .distinct()
                                .sorted()
                                .toList());
        tag.setValue("");
        tag.setPromptText(vi ? "Thẻ" : "Tag");
        tag.setConverter(
                new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(String id) {
                        return id == null || id.isBlank() ? (vi ? "Tất cả thẻ" : "All tags") : id;
                    }

                    @Override
                    public String fromString(String s) {
                        return s;
                    }
                });
        FlowPane cards = new FlowPane(12, 12);
        cards.setId("template-cards");
        cards.setPadding(new Insets(4));
        Label count = new Label();
        count.setId("template-count");
        Runnable filter =
                () -> {
                    cards.getChildren().clear();
                    var matches =
                            ProjectTemplates.search(
                                    search.getText(), category.getValue(), tag.getValue());
                    count.setText(matches.size() + (vi ? " mẫu" : " templates"));
                    for (var template : matches) {
                        Label title = new Label(template.title(vi));
                        title.setWrapText(true);
                        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
                        Label description = new Label(template.description(vi));
                        description.setWrapText(true);
                        description.setMinHeight(60);
                        Label tags =
                                new Label(
                                        template.engine()
                                                + " · "
                                                + String.join(" · ", template.tags()));
                        tags.setWrapText(true);
                        tags.getStyleClass().add("modal-hint");
                        Button button = new Button(vi ? "Tạo dự án" : "Create project");
                        button.setId("create-template-" + template.id());
                        button.getStyleClass().add("btn-primary");
                        button.setOnAction(e -> {
                            modal.close();
                            create.accept(template.id());
                        });
                        VBox card =
                                new VBox(
                                        10,
                                        UiSupport.iconBox(
                                                ProjectAppearance.iconPath(
                                                        ICONS.get(template.category())),
                                                22,
                                                ThemeManager.ink(),
                                                28),
                                        title,
                                        description,
                                        tags,
                                        button);
                        card.setPadding(new Insets(16));
                        card.setPrefWidth(280);
                        card.setMinWidth(240);
                        card.setId("template-card-" + template.id());
                        card.getStyleClass().add("template-gallery-card");
                        cards.getChildren().add(card);
                    }
                };
        search.textProperty().addListener((o, a, b) -> filter.run());
        category.valueProperty().addListener((o, a, b) -> filter.run());
        tag.valueProperty().addListener((o, a, b) -> filter.run());
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(410);
        scroll.maxHeightProperty()
                .bind(javafx.beans.binding.Bindings.max(160, host.heightProperty().subtract(260)));
        modal.node(new HBox(8, search, category, tag)).node(count).node(scroll);
        modal.actions(null, null, vi ? "Đóng" : "Close", UiModal.Tone.NEUTRAL, () -> true);
        filter.run();
        modal.show();
    }
}
