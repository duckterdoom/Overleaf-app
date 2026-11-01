package com.overleaf.client.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * GitHub-style contribution graph used on the home dashboard. Hovering shows a
 * day summary; clicking the grid or pressing Space starts a small snake game
 * played on top of the graph.
 */
public class ActivityHeatmap extends Canvas {

    private static final int ROWS = 7;
    private static final double GAP_RATIO = 0.3;
    private static final double RADIUS_RATIO = 0.28;
    private static final double BOTTOM_LABEL = 20;

    private static final long TICK_MILLIS = 180;
    // Game colors follow the theme and light/dark mode in force when the heat map is built.
    private final boolean dark = ThemeManager.isDark();
    private final Color ink = dark ? Color.web("#E6EDF3") : Color.web("#191917");
    private final Color pixelCream = dark ? Color.web("#161B22") : Color.web("#FCFCF8");
    private final Color pixelShadow = dark ? Color.web("#010409") : Color.web("#C9C9C1");
    private final Color accent = ThemeManager.accent();
    private final Color snakeHead = ThemeManager.accentAt(dark ? 0.78 : 0.16);
    private final Color snakeBody = ThemeManager.accentAt(dark ? 0.60 : 0.26);
    private final Color food = ThemeManager.foodColor();
    private static final Color SKULL_COLOR = Color.web("#B0604A");
    private static final Color TROPHY_COLOR = Color.web("#C9822B");

    private static final String[] SKULL_PIXELS = {
            "....####....",
            "..########..",
            ".##########.",
            "############",
            "###..##..###",
            "###..##..###",
            "############",
            ".##.####.##.",
            ".#..#..#..#.",
            "...##.##....",
            "....#..#...."
    };
    private static final String[] TROPHY_PIXELS = {
            "..########..",
            ".##########.",
            ".##......##.",
            ".##......##.",
            "..##....##..",
            "...######...",
            "....####....",
            ".....###....",
            ".....###....",
            "...#######..",
            "..#########.",
            "..#########."
    };

    private static int bestScore;

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");

    private final Color[] levels = ThemeManager.heatLevels();
    private final Map<LocalDate, Integer> activity;
    private final LocalDate start;
    private final LocalDate today;
    private final int columns;
    private final boolean vi;
    private final Tooltip tooltip = new Tooltip();
    private final Random random = new Random();

    private double cell;

    private int hoveredColumn = -1;
    private int hoveredRow = -1;

    private final Deque<int[]> snake = new ArrayDeque<>();
    private Timeline ticker;
    private int dirX = 1;
    private int dirY = 0;
    private int pendingDirX = 1;
    private int pendingDirY = 0;
    private int foodColumn = -1;
    private int foodRow = -1;
    private int score;
    private boolean gameOn;
    private boolean gameOver;
    private boolean gameWon;
    private final ReadOnlyBooleanWrapper gameActive = new ReadOnlyBooleanWrapper(false);

    public ActivityHeatmap(Map<LocalDate, Integer> activity, int columns, double cell, boolean vi) {
        this.activity = new HashMap<>(activity);
        this.columns = columns;
        this.cell = cell;
        this.vi = vi;
        this.today = LocalDate.now();
        int offset = today.getDayOfWeek().getValue() % 7;
        LocalDate endOfWeek = today.plusDays(6 - offset);
        this.start = endOfWeek.minusDays((long) columns * 7 - 1);

        getStyleClass().add("activity-heatmap");
        tooltip.setAutoHide(true);
        setFocusTraversable(true);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && gameOn) {
                stopGame();
            }
        });

        setOnMouseMoved(this::onMouseMoved);
        setOnMouseExited(e -> {
            hoveredColumn = -1;
            hoveredRow = -1;
            tooltip.hide();
            draw();
        });
        setOnMouseClicked(e -> {
            requestFocus();
            if (e.getClickCount() >= 2 && (!gameOn || gameOver)) {
                startGame();
            }
        });
        setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT -> {
                    if (dirX == 0) {
                        pendingDirX = -1;
                        pendingDirY = 0;
                    }
                }
                case RIGHT -> {
                    if (dirX == 0) {
                        pendingDirX = 1;
                        pendingDirY = 0;
                    }
                }
                case UP -> {
                    if (dirY == 0) {
                        pendingDirX = 0;
                        pendingDirY = -1;
                    }
                }
                case DOWN -> {
                    if (dirY == 0) {
                        pendingDirX = 0;
                        pendingDirY = 1;
                    }
                }
                case SPACE -> {
                    if (!gameOn || gameOver) {
                        startGame();
                    }
                }
                case ESCAPE -> {
                    stopGame();
                }
                default -> {
                    return;
                }
            }
            e.consume();
        });

        applySize();
        draw();
    }

    public ReadOnlyBooleanProperty gameActiveProperty() {
        return gameActive.getReadOnlyProperty();
    }

    public boolean isGameActive() {
        return gameActive.get();
    }

    public static int bestScore() {
        return bestScore;
    }

    public void setCellSize(double size) {
        double resolved = Math.max(7, size);
        if (Math.abs(resolved - cell) < 0.01) {
            return;
        }
        cell = resolved;
        applySize();
        draw();
    }

    private void applySize() {
        setWidth(columns * cell + (columns - 1) * gap());
        setHeight(ROWS * cell + (ROWS - 1) * gap() + BOTTOM_LABEL);
    }

    private void onMouseMoved(MouseEvent event) {
        if (gameOn) {
            return;
        }
        double step = cell + gap();
        int column = (int) Math.floor(event.getX() / step);
        int row = (int) Math.floor(event.getY() / step);
        if (column < 0 || column >= columns || row < 0 || row >= ROWS) {
            tooltip.hide();
            return;
        }
        LocalDate date = start.plusDays((long) column * 7 + row);
        if (date.isAfter(today)) {
            tooltip.hide();
            return;
        }
        if (column != hoveredColumn || row != hoveredRow) {
            hoveredColumn = column;
            hoveredRow = row;
            int count = activity.getOrDefault(date, 0);
            String dayText = vi
                    ? count + " chỉnh sửa · " + date.format(DateTimeFormatter.ofPattern("d/M/yyyy"))
                    : count + (count == 1 ? " edit · " : " edits · ") + date.format(DAY_FORMAT);
            tooltip.setText(count == 0
                    ? (vi ? "Không có chỉnh sửa · " + date.format(DateTimeFormatter.ofPattern("d/M/yyyy"))
                          : "No edits · " + date.format(DAY_FORMAT))
                    : dayText);
            tooltip.show(this, event.getScreenX() + 14, event.getScreenY() + 16);
        }
    }

    public void startGame() {
        if (ticker != null) {
            ticker.stop();
        }
        snake.clear();
        int headColumn = Math.min(4, columns - 1);
        snake.addFirst(new int[]{headColumn, 3});
        snake.addLast(new int[]{headColumn - 1, 3});
        snake.addLast(new int[]{headColumn - 2, 3});
        dirX = 1;
        dirY = 0;
        pendingDirX = 1;
        pendingDirY = 0;
        score = 0;
        gameOn = true;
        gameOver = false;
        gameWon = false;
        gameActive.set(true);
        tooltip.hide();
        pickFood();
        requestFocus();
        if (ticker == null) {
            ticker = new Timeline(new KeyFrame(Duration.millis(TICK_MILLIS), e -> step()));
            ticker.setCycleCount(Timeline.INDEFINITE);
        }
        ticker.playFromStart();
        draw();
    }

    public void stopGame() {
        if (ticker != null) {
            ticker.stop();
        }
        gameOn = false;
        gameOver = false;
        gameWon = false;
        gameActive.set(false);
        draw();
    }

    private void step() {
        if (!gameOn || gameOver) {
            return;
        }
        advance();
        if (!gameOver) {
            draw();
        }
    }

    private void advance() {
        dirX = pendingDirX;
        dirY = pendingDirY;
        int[] head = snake.peekFirst();
        if (head == null) {
            finishGame();
            return;
        }
        int nextColumn = head[0] + dirX;
        int nextRow = head[1] + dirY;
        if (nextColumn < 0 || nextColumn >= columns || nextRow < 0 || nextRow >= ROWS) {
            finishGame();
            return;
        }
        for (int[] part : snake) {
            if (part[0] == nextColumn && part[1] == nextRow) {
                finishGame();
                return;
            }
        }

        boolean ate = nextColumn == foodColumn && nextRow == foodRow;
        snake.addFirst(new int[]{nextColumn, nextRow});
        if (!ate) {
            snake.removeLast();
        } else {
            score++;
            bestScore = Math.max(bestScore, score);
            pickFood();
            if (foodColumn < 0) {
                finishGame(true);
                return;
            }
        }
    }

    private void finishGame() {
        finishGame(false);
    }

    private void finishGame(boolean won) {
        gameOver = true;
        gameWon = won;
        bestScore = Math.max(bestScore, score);
        if (ticker != null) {
            ticker.stop();
        }
        draw();
    }

    private void pickFood() {
        List<int[]> active = new ArrayList<>();
        List<int[]> free = new ArrayList<>();
        long maxIndex = Math.min((long) columns * ROWS - 1, ChronoUnit.DAYS.between(start, today));
        for (int index = 0; index <= maxIndex; index++) {
            int column = index / 7;
            int row = index % 7;
            if (occupied(column, row)) {
                continue;
            }
            int[] point = new int[]{column, row};
            free.add(point);
            if (activity.getOrDefault(start.plusDays(index), 0) > 0) {
                active.add(point);
            }
        }
        List<int[]> pool = active.isEmpty() ? free : active;
        if (pool.isEmpty()) {
            foodColumn = -1;
            foodRow = -1;
            return;
        }
        int[] choice = pool.get(random.nextInt(pool.size()));
        foodColumn = choice[0];
        foodRow = choice[1];
    }

    private boolean occupied(int column, int row) {
        for (int[] part : snake) {
            if (part[0] == column && part[1] == row) {
                return true;
            }
        }
        return false;
    }

    public void draw() {
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        g.setTextAlign(TextAlignment.LEFT);
        g.setGlobalAlpha(1);

        if (gameOn) {
            drawGameChrome(g);
            g.setFill(levels[0]);
            for (int column = 0; column < columns; column++) {
                for (int row = 0; row < ROWS; row++) {
                    drawCell(g, column, row);
                }
            }
            drawFood(g);
            drawSnake(g);
            if (gameOver) {
                drawGameResult(g);
            }
            return;
        }

        drawMonthLabels(g);
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < ROWS; row++) {
                LocalDate date = start.plusDays((long) column * 7 + row);
                int count = date.isAfter(today) ? 0 : activity.getOrDefault(date, 0);
                g.setFill(colorFor(count));
                drawCell(g, column, row);
            }
        }
    }

    private void drawCell(GraphicsContext g, int column, int row) {
        double step = cell + gap();
        double radius = cell * RADIUS_RATIO * 2;
        g.fillRoundRect(column * step, row * step, cell, cell, radius, radius);
    }

    /** Spacing between cells, proportional to the cell size so the grid scales evenly. */
    private double gap() {
        return Math.max(3, cell * GAP_RATIO);
    }

    /** Largest cell size that fits {@code columns} weeks into {@code width} pixels. */
    public static double cellSizeFor(double width, int columns) {
        return width / (columns + (columns - 1) * GAP_RATIO);
    }

    private void drawMonthLabels(GraphicsContext g) {
        g.setFont(Font.font("System", 10));
        g.setFill(Color.web("#A0A09A"));
        double labelY = ROWS * cell + (ROWS - 1) * gap() + 14;
        String lastMonth = "";
        int lastLabelX = -1000;
        for (int column = 0; column < columns; column++) {
            LocalDate weekStart = start.plusDays((long) column * 7);
            String month = weekStart.getMonth().getDisplayName(TextStyle.SHORT,
                    vi ? VI_LOCALE : Locale.ENGLISH);
            int x = (int) (column * (cell + gap()));
            if (!month.equals(lastMonth) && x - lastLabelX >= 30 && x <= getWidth() - 26) {
                g.fillText(month, x, labelY);
                lastMonth = month;
                lastLabelX = x;
            }
        }
    }

    private void drawGameChrome(GraphicsContext g) {
        double y = ROWS * cell + (ROWS - 1) * gap() + 14;
        g.setFont(Font.font("Monospaced", FontWeight.BOLD, 9));
        g.setFill(accent);
        g.fillText("SNAKE", 0, y);
        g.setFill(Color.web("#8B8B85"));
        g.fillText("SCORE " + score, 42, y);
        g.setFill(score > 0 && score >= bestScore ? TROPHY_COLOR : Color.web("#8B8B85"));
        g.fillText("BEST " + bestScore, 104, y);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText(vi ? "Esc để thoát" : "Esc to exit", getWidth(), y);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawFood(GraphicsContext g) {
        if (foodColumn < 0 || foodRow < 0) {
            return;
        }
        double step = cell + gap();
        double centerX = foodColumn * step + cell / 2;
        double centerY = foodRow * step + cell / 2;
        g.setFill(food);
        g.fillOval(centerX - cell * 0.32, centerY - cell * 0.32, cell * 0.64, cell * 0.64);
        g.setFill(food.interpolate(Color.WHITE, 0.55));
        g.fillOval(centerX - cell * 0.12, centerY - cell * 0.18, cell * 0.22, cell * 0.22);
    }

    private void drawSnake(GraphicsContext g) {
        double step = cell + gap();
        int index = 0;
        for (int[] part : snake) {
            g.setFill(index == 0 ? snakeHead : snakeBody);
            g.fillRoundRect(part[0] * step + 1, part[1] * step + 1,
                    cell - 2, cell - 2, 4, 4);
            index++;
        }
    }

    private void drawGameResult(GraphicsContext g) {
        double gridWidth = columns * cell + (columns - 1) * gap();
        double gridHeight = ROWS * cell + (ROWS - 1) * gap();
        g.setFill(Color.rgb(20, 27, 23, 0.45));
        g.fillRect(0, 0, gridWidth, gridHeight);

        double scale = Math.max(0.78, Math.min(1.0, (gridHeight - 6) / 96.0));
        double boxWidth = Math.min(gridWidth - 24, 380);
        double boxHeight = 96 * scale;
        double x = (gridWidth - boxWidth) / 2;
        double y = (gridHeight - boxHeight) / 2;

        g.setFill(pixelShadow);
        g.fillRect(x + 4, y + 4, boxWidth, boxHeight);
        g.setFill(pixelCream);
        g.fillRect(x, y, boxWidth, boxHeight);
        drawPixelFrame(g, x, y, boxWidth, boxHeight);

        double pad = 18 * scale;
        double unit = 4 * scale;
        String[] icon = gameWon ? TROPHY_PIXELS : SKULL_PIXELS;
        double iconWidth = icon[0].length() * unit;
        double iconHeight = icon.length * unit;
        drawPixelIcon(g, icon, x + pad, y + (boxHeight - iconHeight) / 2, unit,
                gameWon ? TROPHY_COLOR : SKULL_COLOR);

        double textX = x + pad + iconWidth + pad;
        g.setTextAlign(TextAlignment.LEFT);

        String title = gameWon
                ? (vi ? "THẮNG RỒI!" : "YOU WIN!")
                : (vi ? "KẾT THÚC" : "GAME OVER");
        g.setFont(Font.font("Monospaced", FontWeight.BOLD, 15 * scale));
        g.setFill(Color.rgb(25, 25, 23, 0.18));
        g.fillText(title, textX + 1, y + 31 * scale);
        g.setFill(ink);
        g.fillText(title, textX, y + 30 * scale);

        String scoreLine = vi
                ? "ĐIỂM " + score + "   KỶ LỤC " + bestScore
                : "SCORE " + score + "   BEST " + bestScore;
        g.setFont(Font.font("Monospaced", FontWeight.BOLD, 11 * scale));
        g.setFill(gameWon ? TROPHY_COLOR : accent);
        g.fillText(scoreLine, textX, y + 48 * scale);

        g.setFill(dark ? Color.web("#30363D") : Color.web("#DFDFD7"));
        g.fillRect(textX, y + 55 * scale, boxWidth - (textX - x) - pad, 1);

        g.setFont(Font.font("Monospaced", 9 * scale));
        g.setFill(dark ? Color.web("#8B949E") : Color.web("#6B6B65"));
        g.fillText(vi ? "SPACE HOẶC NHẤP ĐÚP ĐỂ CHƠI LẠI" : "SPACE OR DOUBLE-CLICK TO RETRY",
                textX, y + 71 * scale);
        g.fillText(vi ? "ESC HOẶC ← QUAY LẠI LƯỚI" : "ESC OR ← BACK TO HEATMAP",
                textX, y + 87 * scale);
    }

    private void drawPixelFrame(GraphicsContext g, double x, double y, double width, double height) {
        g.setFill(ink);
        g.fillRect(x + 4, y, width - 8, 2);
        g.fillRect(x + 4, y + height - 2, width - 8, 2);
        g.fillRect(x, y + 4, 2, height - 8);
        g.fillRect(x + width - 2, y + 4, 2, height - 8);
        g.fillRect(x + 2, y + 2, 2, 2);
        g.fillRect(x + width - 4, y + 2, 2, 2);
        g.fillRect(x + 2, y + height - 4, 2, 2);
        g.fillRect(x + width - 4, y + height - 4, 2, 2);
    }

    private void drawPixelIcon(GraphicsContext g, String[] pixels, double x, double y,
                               double unit, Color color) {
        g.setFill(color);
        for (int row = 0; row < pixels.length; row++) {
            String line = pixels[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') {
                    g.fillRect(x + column * unit, y + row * unit, unit, unit);
                }
            }
        }
    }

    private Color colorFor(int count) {
        if (count <= 0) {
            return levels[0];
        }
        if (count <= 4) {
            return levels[1];
        }
        if (count <= 9) {
            return levels[2];
        }
        if (count <= 19) {
            return levels[3];
        }
        return levels[4];
    }

    public static Color[] legendColors() {
        return ThemeManager.heatLevels();
    }

    public LocalDate rangeStart() {
        return start;
    }
}
