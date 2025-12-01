import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class SoundBoard extends Application {

    private static final File STORAGE = new File("boards.json");

    private BorderPane root;

    private StackPane playView;
    private StackPane boardsView;
    private StackPane soundsView;

    private GridPane playGrid;
    private GridPane soundsGrid;

    private Map<String, List<JSONObject>> boards = new LinkedHashMap<>();
    private String currentBoard = "Default Board";

    private ComboBox<String> boardSelect;

    private Map<AudioClip, String> playingClips = new HashMap<>();
    private Map<AudioClip, Boolean> loopState = new HashMap<>();

    @Override
    public void start(Stage stage) {
        loadBoards();

        // -------------------------------------
        // TOP BAR
        // -------------------------------------
        Label title = new Label("Soundboards");
        title.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 26px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        boardSelect = new ComboBox<>();
        boardSelect.getItems().addAll(boards.keySet());
        boardSelect.getSelectionModel().select(currentBoard);
        boardSelect.setValue(currentBoard);
        boardSelect.setStyle("-fx-background-color: #333; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");

        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node label = boardSelect.lookup(".label");
            if (label != null) label.setStyle("-fx-text-fill: white;");
            javafx.scene.Node arrowButton = boardSelect.lookup(".arrow-button");
            if (arrowButton != null) arrowButton.setStyle("-fx-background-color: #333;");
        });

        boardSelect.setOnAction(e -> {
            String selected = boardSelect.getSelectionModel().getSelectedItem();
            if (selected != null) switchBoard(selected);
        });

        HBox top = new HBox(12, title, boardSelect);
        top.setPadding(new Insets(10));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color: linear-gradient(to right, #121212, #1A1A1A);");

        // -------------------------------------
        // PLAY VIEW
        // -------------------------------------
        playGrid = new GridPane();
        playGrid.setHgap(20);
        playGrid.setVgap(20);
        playGrid.setPadding(new Insets(20));
        playGrid.setStyle("-fx-background-color: #121212;");

        loadPlayGrid();

        Button addSoundBtn = new Button("+ Add Sound");
        addSoundBtn.setStyle(
                "-fx-background-color: #333;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-font-size: 16px;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 2);"
        );
        addSoundBtn.setOnAction(e -> addNewSound());

        addSoundBtn.setOnMouseEntered(e -> addSoundBtn.setStyle(
                "-fx-background-color: #444;" +
                        "-fx-text-fill: #B388FF;" +
                        "-fx-font-size: 16px;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 6, 0, 0, 3);"
        ));
        addSoundBtn.setOnMouseExited(e -> addSoundBtn.setStyle(
                "-fx-background-color: #333;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-font-size: 16px;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 2);"
        ));

        VBox playContent = new VBox(10, addSoundBtn, playGrid);
        playContent.setPadding(new Insets(10));
        playView = new StackPane(playContent);
        playView.setStyle("-fx-background-color: #121212;");

        // -------------------------------------
        // SOUNDS VIEW
        // -------------------------------------
        soundsGrid = new GridPane();
        soundsGrid.setHgap(20);
        soundsGrid.setVgap(20);
        soundsGrid.setPadding(new Insets(20));
        soundsGrid.setStyle("-fx-background-color: #121212;");

        loadSoundsGrid();

        soundsView = new StackPane(soundsGrid);
        soundsView.setStyle("-fx-background-color: #121212;");

        // -------------------------------------
        // BOARDS VIEW
        // -------------------------------------
        boardsView = boardsViewContent();

        // -------------------------------------
        // ROOT LAYOUT
        // -------------------------------------
        root = new BorderPane();
        root.setTop(top);
        root.setCenter(playView);

        // -------------------------------------
        // BOTTOM NAV
        // -------------------------------------
        HBox nav = new HBox(50);
        nav.setPadding(new Insets(15));
        nav.setAlignment(Pos.CENTER);
        nav.setStyle("-fx-background-color: #111;");

        nav.getChildren().addAll(
                navButton("PLAY", playView),
                navButton("BOARDS", boardsView),
                navButton("SOUNDS", soundsView)
        );

        root.setBottom(nav);
        root.setStyle("-fx-background-color: #121212;");

        Scene scene = new Scene(root, 900, 700, Color.BLACK);
        stage.setScene(scene);
        stage.setTitle("Soundy 🎵");
        stage.show();
    }

    // ----------------------------
    // NAV BUTTONS
    // ----------------------------
    private VBox navButton(String text, StackPane targetView) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #888; -fx-font-size: 14px; -fx-font-family: 'Segoe UI';");

        VBox box = new VBox(lbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(5, 20, 5, 20));

        box.setOnMouseClicked(e -> {
            switchTo(targetView);
            updateNavSelection((HBox) box.getParent(), box);
        });

        return box;
    }

    private void updateNavSelection(HBox nav, VBox selected) {
        for (var node : nav.getChildren()) {
            VBox box = (VBox) node;
            Label lbl = (Label) box.getChildren().get(0);
            lbl.setStyle("-fx-text-fill: #888; -fx-font-size: 14px; -fx-font-family: 'Segoe UI';");
            box.setStyle("-fx-background-color: transparent;");
        }

        Label sLbl = (Label) selected.getChildren().get(0);
        sLbl.setStyle("-fx-text-fill: #B388FF; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");
        selected.setStyle("-fx-background-color: #222;");
    }

    private void switchTo(StackPane view) {
        FadeTransition ft = new FadeTransition(Duration.millis(250), view);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
        root.setCenter(view);
    }

    // ----------------------------
    // LOAD & SAVE BOARDS
    // ----------------------------
    private void loadBoards() {
        boards.clear();
        try {
            if (!STORAGE.exists()) {
                boards.put("Default Board", new ArrayList<>());
                saveBoards();
                return;
            }

            JSONArray arr = new JSONArray(Files.readString(STORAGE.toPath()));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String boardName = obj.getString("board");
                JSONArray sounds = obj.getJSONArray("sounds");

                List<JSONObject> list = new ArrayList<>();
                for (int j = 0; j < sounds.length(); j++) {
                    list.add(sounds.getJSONObject(j));
                }
                boards.put(boardName, list);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveBoards() {
        try {
            JSONArray arr = new JSONArray();
            for (String boardName : boards.keySet()) {
                JSONObject obj = new JSONObject();
                obj.put("board", boardName);
                obj.put("sounds", new JSONArray(boards.get(boardName)));
                arr.put(obj);
            }
            Files.writeString(STORAGE.toPath(), arr.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchBoard(String boardName) {
        if (boardName == null || !boards.containsKey(boardName)) return;

        currentBoard = boardName;
        if (boardSelect != null) {
            boardSelect.getSelectionModel().select(currentBoard);
            boardSelect.setValue(currentBoard);
        }

        loadPlayGrid();
        loadSoundsGrid();
    }

    // ----------------------------
    // PLAY GRID
    // ----------------------------
    private void loadPlayGrid() {
        playGrid.getChildren().clear();
        List<JSONObject> list = boards.get(currentBoard);
        for (int i = 0; i < list.size(); i++) {
            JSONObject obj = list.get(i);
            File f = new File(obj.getString("path"));
            addSoundTile(playGrid, i, obj.getString("name"), f, list);
        }
    }

    // ----------------------------
    // SOUNDS GRID
    // ----------------------------
    private void loadSoundsGrid() {
        soundsGrid.getChildren().clear();
        List<JSONObject> list = boards.get(currentBoard);
        for (int i = 0; i < list.size(); i++) {
            JSONObject obj = list.get(i);
            File f = new File(obj.getString("path"));
            addSoundTile(soundsGrid, i, obj.getString("name"), f, list);
        }
    }

    // ----------------------------
    // ADD SOUND TILE
    // ----------------------------
    private void addSoundTile(GridPane grid, int index, String name, File file, List<JSONObject> list) {
        AudioClip clip = new AudioClip(file.toURI().toString());
        Media media = new Media(file.toURI().toString());
        MediaPlayer mp = new MediaPlayer(media);
        loopState.put(clip, false);

        Label endTime = new Label("...");
        endTime.setStyle("-fx-text-fill: #E0E0E0; -fx-font-family: 'Segoe UI';");
        mp.setOnReady(() -> {
            double s = media.getDuration().toSeconds();
            endTime.setText(String.format("%.1fs", s));
            mp.dispose();
        });

        String[] borders = {"#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA"};
        String border = borders[index % borders.length];

        VBox card = new VBox();
        card.setSpacing(10);
        card.setPadding(new Insets(12));
        card.setPrefSize(200, 150);
        card.setStyle(
                "-fx-background-color: #1E1E1E;" +
                        "-fx-border-color: " + border + ";" +
                        "-fx-border-width: 3;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;"
        );

        // Hover animation
        card.setOnMouseEntered(e -> card.setScaleX(1.05));
        card.setOnMouseEntered(e -> card.setScaleY(1.05));
        card.setOnMouseExited(e -> card.setScaleX(1));
        card.setOnMouseExited(e -> card.setScaleY(1));

        Label start = new Label("0.0s");
        start.setStyle("-fx-text-fill: #E0E0E0; -fx-font-family: 'Segoe UI';");

        HBox timeBar = new HBox(start, new Region(), endTime);
        HBox.setHgrow(timeBar.getChildren().get(1), Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        Button playBtn = icon("▶", () -> {
            if(loopState.get(clip)) {
                clip.stop();
                clip.setCycleCount(1);
                loopState.put(clip, false);
                playingClips.remove(clip);
            }
            clip.play();
        });

        Button loopBtn = icon("↻", () -> {
            boolean isLooping = loopState.get(clip);
            if (isLooping) {
                clip.stop();
                clip.setCycleCount(1);
                loopState.put(clip, false);
                playingClips.remove(clip);
            } else {
                clip.setCycleCount(AudioClip.INDEFINITE);
                clip.play();
                loopState.put(clip, true);
                playingClips.put(clip, name);
            }
        });

        Button deleteBtn = icon("x", () -> {
            clip.stop();
            list.removeIf(obj -> obj.getString("name").equals(name));
            playingClips.remove(clip);
            loopState.remove(clip);
            saveBoards();
            loadPlayGrid();
            loadSoundsGrid();
        });

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (clip.isPlaying()) {
                    clip.stop();
                    loopState.put(clip, false);
                    playingClips.remove(clip);
                }
            }
        });

        HBox actions = new HBox(15, loopBtn, playBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(timeBar, nameLabel, new Region(), actions);
        VBox.setVgrow(card.getChildren().get(2), Priority.ALWAYS);

        int row = index / 2;
        int col = index % 2;
        grid.add(card, col, row);
    }

    // ----------------------------
    // ICON BUTTONS
    // ----------------------------
    private Button icon(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle(
                "-fx-background-color: #333;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-font-size: 20;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 50;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 2);"
        );
        b.setOnMouseEntered(e -> b.setStyle(
                "-fx-background-color: #444;" +
                        "-fx-text-fill: #B388FF;" +
                        "-fx-font-size: 20;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 50;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 6, 0, 0, 3);"
        ));
        b.setOnMouseExited(e -> b.setStyle(
                "-fx-background-color: #333;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-font-size: 20;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 50;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 4, 0, 0, 2);"
        ));
        b.setOnAction(e -> action.run());
        return b;
    }

    // ----------------------------
    // ADD NEW SOUND
    // ----------------------------
    private void addNewSound() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Sound File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aiff"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());

        if (file != null) {
            TextInputDialog dialog = new TextInputDialog(file.getName());
            dialog.setTitle("Sound Name");
            dialog.setHeaderText("Enter a name for this sound:");
            dialog.setContentText("Name:");
            dialog.showAndWait().ifPresent(name -> {
                if (name.isBlank()) name = file.getName();
                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("path", file.getAbsolutePath());
                boards.get(currentBoard).add(obj);
                saveBoards();
                loadPlayGrid();
                loadSoundsGrid();
            });
        }
    }

    // ----------------------------
    // BOARDS VIEW
    // ----------------------------
    private StackPane boardsViewContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #121212;");

        Label header = new Label("Boards");
        header.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 26px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        FlowPane bubbleContainer = new FlowPane();
        bubbleContainer.setHgap(15);
        bubbleContainer.setVgap(15);
        bubbleContainer.setPadding(new Insets(10));
        bubbleContainer.setStyle("-fx-background-color: #121212;");
        bubbleContainer.setPrefWrapLength(800);

        refreshBoardBubbles(bubbleContainer);

        TextField newBoardField = new TextField();
        newBoardField.setPromptText("New board name");
        newBoardField.setStyle(
                "-fx-background-color: #1E1E1E;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-prompt-text-fill: #777;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 12;" +
                        "-fx-padding: 6;"
        );

        Button addBtn = new Button("Add");
        addBtn.setStyle(
                "-fx-background-color: #333;" +
                        "-fx-text-fill: #E0E0E0;" +
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-background-radius: 12;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 6 12;"
        );
        addBtn.setOnAction(e -> {
            String name = newBoardField.getText().trim();
            if (!name.isEmpty() && !boards.containsKey(name)) {
                boards.put(name, new ArrayList<>());
                boardSelect.getItems().add(name);
                saveBoards();
                refreshBoardBubbles(bubbleContainer);
                newBoardField.clear();
            }
        });

        HBox addBox = new HBox(10, newBoardField, addBtn);
        addBox.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(header, bubbleContainer, addBox);

        return new StackPane(content);
    }

    private void refreshBoardBubbles(FlowPane container) {
        container.getChildren().clear();

        for (String boardName : boards.keySet()) {
            HBox bubble = new HBox();
            bubble.setAlignment(Pos.CENTER_LEFT);
            bubble.setSpacing(10);
            bubble.setPadding(new Insets(10, 16, 10, 16));
            bubble.setStyle(
                    "-fx-background-color: " + (boardName.equals(currentBoard) ? "#333" : "#222") + ";" +
                            "-fx-background-radius: 20;" +
                            "-fx-border-radius: 20;" +
                            "-fx-border-color: #555;" +
                            "-fx-border-width: 2;" +
                            "-fx-cursor: hand;"
            );

            Label label = new Label(boardName);
            label.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 16px; -fx-font-family: 'Segoe UI';");

            Button del = new Button("✖");
            del.setStyle("-fx-background-color: transparent; -fx-text-fill: #E57373; -fx-font-size: 16;");
            del.setOnAction(e -> {
                if (!boardName.equals("Default Board")) {
                    boards.remove(boardName);
                    boardSelect.getItems().remove(boardName);
                    if (currentBoard.equals(boardName)) {
                        currentBoard = boards.keySet().iterator().next();
                        switchBoard(currentBoard);
                    }
                    saveBoards();
                    refreshBoardBubbles(container);
                }
            });

            bubble.setOnMouseEntered(e -> bubble.setStyle(
                    "-fx-background-color: #444; -fx-background-radius: 20; -fx-border-radius: 20; -fx-border-color: #888; -fx-border-width: 2; -fx-cursor: hand;"
            ));
            bubble.setOnMouseExited(e -> bubble.setStyle(
                    "-fx-background-color: " + (boardName.equals(currentBoard) ? "#333" : "#222") + ";" +
                            "-fx-background-radius: 20; -fx-border-radius: 20; -fx-border-color: #555; -fx-border-width: 2; -fx-cursor: hand;"
            ));

            bubble.setOnMouseClicked(e -> {
                if (!boardName.equals(currentBoard)) {
                    switchBoard(boardName);
                    refreshBoardBubbles(container);
                }
            });

            bubble.getChildren().addAll(label, del);
            container.getChildren().add(bubble);
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
