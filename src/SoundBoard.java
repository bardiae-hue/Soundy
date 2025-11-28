import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton; // New Import for MouseButton
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

    // Map to keep track of currently playing/looping AudioClips to stop them
    private Map<AudioClip, String> playingClips = new HashMap<>();
    // Map to track the looping state of each sound tile (Added for this request)
    private Map<AudioClip, Boolean> loopState = new HashMap<>();
    @Override
    public void start(Stage stage) {
        loadBoards();

        // -------------------------------------
        // TOP BAR
        // -------------------------------------
        Label title = new Label("Soundboards");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        boardSelect = new ComboBox<>();
        boardSelect.getItems().addAll(boards.keySet());

        // Set the currently selected board
        boardSelect.setValue(currentBoard);

        // Styling for dark theme and readable selected text
        // Keep the main ComboBox styling simple here:
        boardSelect.setStyle(
                "-fx-background-color: #333;" + // dropdown button background
                        "-fx-font-family: 'Segoe UI';" +
                        "-fx-font-size: 14px;"
        );

        // Use Platform.runLater to apply styles to internal components (.label)
        // AFTER the scene is set up, preventing the NullPointerException.
        javafx.application.Platform.runLater(() -> {
            // Find the internal Label component that displays the selected value
            javafx.scene.Node label = boardSelect.lookup(".label");
            if (label != null) {
                // Ensure the selected text is visible (white)
                label.setStyle("-fx-text-fill: white;");
            }
            // Optional: Ensure the dropdown arrow button is also styled
            javafx.scene.Node arrowButton = boardSelect.lookup(".arrow-button");
            if (arrowButton != null) {
                arrowButton.setStyle("-fx-background-color: #333;");
            }
        });

        // Update board when selection changes
        boardSelect.setOnAction(e -> {
            String selected = boardSelect.getSelectionModel().getSelectedItem();
            if (selected != null) {
                switchBoard(selected);
            }
        });
        HBox top = new HBox(12, title, boardSelect);
        top.setPadding(new Insets(10));
        top.setAlignment(Pos.CENTER_LEFT);

        // -------------------------------------
        // PLAY VIEW
        // -------------------------------------
        playGrid = new GridPane();
        playGrid.setHgap(20);
        playGrid.setVgap(20);
        playGrid.setPadding(new Insets(20));
        playGrid.setStyle("-fx-background-color: #181818;");

        loadPlayGrid();

        Button addSoundBtn = new Button("+ Add Sound");
        addSoundBtn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-family: 'Segoe UI';");
        addSoundBtn.setOnAction(e -> addNewSound());

        VBox playContent = new VBox(10, addSoundBtn, playGrid);
        playContent.setPadding(new Insets(10));
        playView = new StackPane(playContent);
        playView.setStyle("-fx-background-color: #181818;");

        // -------------------------------------
        // SOUNDS VIEW
        // -------------------------------------
        soundsGrid = new GridPane();
        soundsGrid.setHgap(20);
        soundsGrid.setVgap(20);
        soundsGrid.setPadding(new Insets(20));
        soundsGrid.setStyle("-fx-background-color: #181818;");

        loadSoundsGrid();

        soundsView = new StackPane(soundsGrid);
        soundsView.setStyle("-fx-background-color: #181818;");

        // -------------------------------------
        // BOARDS VIEW
        // -------------------------------------
        boardsView = boardsViewContent();

        // -------------------------------------
        // ROOT LAYOUT
        // -------------------------------------
        root = new BorderPane();
        root.setTop(top);
        root.setCenter(playView); // default

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
        root.setStyle("-fx-background-color: #181818;");

        Scene scene = new Scene(root, 900, 700, Color.BLACK);
        stage.setScene(scene);
        stage.setTitle("Soundy 🎵");
        stage.show();
    }

    // ----------------------------
    // NAVIGATION BUTTONS
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
        currentBoard = boardName;
        // Update ComboBox display to show the newly selected board.
        boardSelect.setValue(boardName);
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

        // Initialize loop state for this clip (false = not looping)
        loopState.put(clip, false);

        Label endTime = new Label("...");
        endTime.setStyle("-fx-text-fill: white; -fx-font-family: 'Segoe UI';");
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
                "-fx-background-color: #222;" +
                        "-fx-border-color: " + border + ";" +
                        "-fx-border-width: 3;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;"
        );

        Label start = new Label("0.0s");
        start.setStyle("-fx-text-fill: white; -fx-font-family: 'Segoe UI';");

        HBox timeBar = new HBox(start, new Region(), endTime);
        HBox.setHgrow(timeBar.getChildren().get(1), Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        Button playBtn = icon("▶", () -> {
            // Stop any existing loop first
            if(loopState.get(clip)) {
                clip.stop();
                clip.setCycleCount(1);
                loopState.put(clip, false);
                playingClips.remove(clip);
            }
            clip.play();
        });

        // --- MODIFICATION: Loop Button Toggle Logic ---
        Button loopBtn = icon("↻", () -> {
            boolean isLooping = loopState.get(clip);

            if (isLooping) {
                // Second click: Stop the loop and reset state
                clip.stop();
                clip.setCycleCount(1);
                loopState.put(clip, false);
                playingClips.remove(clip);
                System.out.println("Loop stopped for: " + name);
            } else {
                // First click: Start the loop
                clip.setCycleCount(AudioClip.INDEFINITE);
                clip.play();
                loopState.put(clip, true);
                playingClips.put(clip, name); // Track looping clip
                System.out.println("Loop started for: " + name);
            }
        });
        // ---------------------------------------------

        Button deleteBtn = icon("✖", () -> {
            // Stop sound before deleting
            clip.stop();
            list.removeIf(obj -> obj.getString("name").equals(name));
            playingClips.remove(clip);
            loopState.remove(clip); // Remove loop state
            saveBoards();
            loadPlayGrid();
            loadSoundsGrid();
        });

        // Add double-click handler to stop all clips (Kept from previous request)
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                // Stop the specific sound if it's playing/looping
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
    // ICON BUTTON
    // ----------------------------
    private Button icon(String text, Runnable action) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 20; -fx-font-family: 'Segoe UI';");
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
    // BOARDS VIEW CONTENT
    // ----------------------------
    private StackPane boardsViewContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #D3D3D3;"); // light gray background

        Label header = new Label("Boards");
        header.setStyle("-fx-text-fill: black; -fx-font-size: 24px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';");

        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(boards.keySet());
        listView.setStyle("-fx-background-color: #D3D3D3; -fx-control-inner-background: #D3D3D3; -fx-font-family: 'Segoe UI';");

        // --- MODIFICATION 3: Ensure the current board is selected in the ListView ---
        listView.getSelectionModel().select(currentBoard);
        // --------------------------------------------------------------------------

        TextField newBoardField = new TextField();
        newBoardField.setPromptText("New board name");
        newBoardField.setStyle("-fx-font-family: 'Segoe UI';");

        Button addBoardBtn = new Button("Add Board");
        addBoardBtn.setStyle("-fx-font-family: 'Segoe UI';");
        addBoardBtn.setOnAction(e -> {
            String name = newBoardField.getText().trim();
            if (!name.isEmpty() && !boards.containsKey(name)) {
                boards.put(name, new ArrayList<>());
                listView.getItems().add(name);
                saveBoards();
                newBoardField.clear();
                boardSelect.getItems().add(name); // update top ComboBox
            }
        });

        Button deleteBoardBtn = new Button("Delete Selected Board");
        deleteBoardBtn.setStyle("-fx-font-family: 'Segoe UI';");
        deleteBoardBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals("Default Board")) {
                boards.remove(selected);
                listView.getItems().remove(selected);
                boardSelect.getItems().remove(selected);

                // Switch to another board if the current was deleted
                if (currentBoard.equals(selected)) {
                    currentBoard = boards.keySet().iterator().next(); // pick first board
                    switchBoard(currentBoard); // Call switchBoard to update ComboBox and grids
                }

                saveBoards();
            }
        });

        HBox addBox = new HBox(10, newBoardField, addBoardBtn, deleteBoardBtn);
        addBox.setAlignment(Pos.CENTER_LEFT);

        // Update top ComboBox and switch board when a board is clicked in the list
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(currentBoard)) {
                switchBoard(newVal);
            }
        });

        content.getChildren().addAll(header, listView, addBox);

        return new StackPane(content);
    }

    public static void main(String[] args) {
        launch();
    }
}