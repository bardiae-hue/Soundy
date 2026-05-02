import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class SoundBoard extends Application {

    // ── Persistence ──────────────────────────────────────────
    private static final File STORAGE = new File("boards.json");

    // ── Design tokens ─────────────────────────────────────────
    private static final String BG        = "#0f0f0f";
    private static final String BG_CARD   = "#1a1a1a";
    private static final String BG_HOVER  = "#242424";
    private static final String BORDER    = "#2a2a2a";
    private static final String TEXT      = "#e8e8e8";
    private static final String TEXT_DIM  = "#666";
    private static final String ACCENT    = "#a78bfa";   // violet
    private static final String ACCENT_DIM= "#7c5cbf";
    private static final String PLAYING   = "#34d399";   // emerald — active state
    private static final String DANGER    = "#f87171";   // red — delete

    // Tile accent colours (cycling)
    private static final String[] TILE_COLORS = {
            "#a78bfa", "#60a5fa", "#34d399", "#fb923c", "#f472b6",
            "#facc15", "#38bdf8", "#4ade80", "#f87171", "#c084fc"
    };

    // Keyboard shortcut keys (1–9, 0)
    private static final KeyCode[] HOTKEYS = {
            KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4,
            KeyCode.DIGIT5, KeyCode.DIGIT6, KeyCode.DIGIT7, KeyCode.DIGIT8,
            KeyCode.DIGIT9, KeyCode.DIGIT0
    };

    // ── State ─────────────────────────────────────────────────
    private BorderPane root;
    private StackPane  playView, boardsView, soundsView;
    private FlowPane   playFlow, soundsFlow;

    private Map<String, List<JSONObject>> boards = new LinkedHashMap<>();
    private String currentBoard = "Default Board";
    private ComboBox<String> boardSelect;

    // clip → is-looping
    private final Map<AudioClip, Boolean>  loopState    = new HashMap<>();
    // clip → its VBox card (so we can update the visual state)
    private final Map<AudioClip, VBox>     clipCards    = new HashMap<>();
    // index → clip (for hotkeys)
    private final Map<Integer, AudioClip>  hotkeyClips  = new HashMap<>();

    // ── App entry ─────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        loadBoards();

        // TOP BAR
        Label title = styled(new Label("Soundy"), TEXT, 22, true);

        boardSelect = new ComboBox<>();
        boardSelect.getItems().addAll(boards.keySet());
        boardSelect.getSelectionModel().select(currentBoard);
        boardSelect.setStyle(
                "-fx-background-color:" + BG_CARD + ";" +
                        "-fx-border-color:" + BORDER + ";" +
                        "-fx-text-fill:" + TEXT + ";" +
                        "-fx-font-family:'Segoe UI';" +
                        "-fx-font-size:13px;" +
                        "-fx-background-radius:6;" +
                        "-fx-border-radius:6;"
        );
        boardSelect.setOnAction(e -> {
            String sel = boardSelect.getSelectionModel().getSelectedItem();
            if (sel != null) switchBoard(sel);
        });

        // Stop All button — always visible in top bar
        Button stopAll = new Button("◼  Stop All");
        stopAll.setStyle(btnStyle(DANGER, "transparent", DANGER));
        stopAll.setOnMouseEntered(e -> stopAll.setStyle(btnStyle(DANGER, "#3a1a1a", DANGER)));
        stopAll.setOnMouseExited(e ->  stopAll.setStyle(btnStyle(DANGER, "transparent", DANGER)));
        stopAll.setOnAction(e -> stopAllClips());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(14, title, boardSelect, spacer, stopAll);
        top.setPadding(new Insets(12, 16, 12, 16));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color:" + BG + ";-fx-border-color:" + BORDER + ";-fx-border-width:0 0 1 0;");

        // VIEWS
        playFlow   = makeFlow();
        soundsFlow = makeFlow();
        loadPlayFlow();

        ScrollPane playScroll   = scroll(playFlow);
        ScrollPane soundsScroll = scroll(soundsFlow);

        Button addSoundBtn = new Button("＋  Add Sound");
        addSoundBtn.setStyle(btnStyle(ACCENT, "transparent", ACCENT));
        addSoundBtn.setOnMouseEntered(e -> addSoundBtn.setStyle(btnStyle(ACCENT, "#1e1a2e", ACCENT)));
        addSoundBtn.setOnMouseExited(e ->  addSoundBtn.setStyle(btnStyle(ACCENT, "transparent", ACCENT)));
        addSoundBtn.setOnAction(e -> addNewSound());

        // Hotkey hint
        Label hint = new Label("Tip: press 1–9 to trigger sounds");
        hint.setStyle("-fx-text-fill:" + TEXT_DIM + ";-fx-font-size:11px;-fx-font-family:'Segoe UI';");

        HBox playHeader = new HBox(10, addSoundBtn, new Region(), hint);
        HBox.setHgrow(playHeader.getChildren().get(1), Priority.ALWAYS);
        playHeader.setAlignment(Pos.CENTER_LEFT);
        playHeader.setPadding(new Insets(12, 16, 8, 16));
        playHeader.setStyle("-fx-background-color:" + BG + ";");

        VBox playContent = new VBox(playHeader, playScroll);
        VBox.setVgrow(playScroll, Priority.ALWAYS);
        playView = new StackPane(playContent);
        playView.setStyle("-fx-background-color:" + BG + ";");

        VBox soundsContent = new VBox(soundsScroll);
        VBox.setVgrow(soundsScroll, Priority.ALWAYS);
        soundsView = new StackPane(soundsContent);
        soundsView.setStyle("-fx-background-color:" + BG + ";");

        boardsView = buildBoardsView();

        // ROOT
        root = new BorderPane();
        root.setTop(top);
        root.setCenter(playView);
        root.setStyle("-fx-background-color:" + BG + ";");

        // BOTTOM NAV
        HBox nav = new HBox(0);
        nav.setAlignment(Pos.CENTER);
        nav.setStyle("-fx-background-color:" + BG + ";-fx-border-color:" + BORDER + ";-fx-border-width:1 0 0 0;");

        List<VBox> navBtns = new ArrayList<>();
        VBox nb1 = navBtn("▶  Play",   "1", playView,   nav, navBtns);
        VBox nb2 = navBtn("⊞  Boards", "2", boardsView, nav, navBtns);
        VBox nb3 = navBtn("♪  Sounds", "3", soundsView, nav, navBtns);
        navBtns.addAll(List.of(nb1, nb2, nb3));
        nav.getChildren().addAll(nb1, nb2, nb3);
        selectNav(nav, nb1, navBtns);
        root.setBottom(nav);

        Scene scene = new Scene(root, 960, 680, Color.web(BG));
        scene.getStylesheets();  // no external CSS needed

        // Global keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Hotkeys 1–0: trigger sounds
            for (int i = 0; i < HOTKEYS.length; i++) {
                if (event.getCode() == HOTKEYS[i]) {
                    AudioClip clip = hotkeyClips.get(i);
                    if (clip != null) triggerClip(clip);
                    event.consume();
                    return;
                }
            }
            // Escape: stop all
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAllClips();
                event.consume();
            }
        });

        stage.setScene(scene);
        stage.setTitle("Soundy");
        stage.show();
    }

    // ── Style helpers ──────────────────────────────────────────
    private String btnStyle(String textColor, String bg, String border) {
        return "-fx-background-color:" + bg + ";" +
                "-fx-text-fill:" + textColor + ";" +
                "-fx-border-color:" + border + ";" +
                "-fx-border-width:1;" +
                "-fx-border-radius:6;" +
                "-fx-background-radius:6;" +
                "-fx-font-family:'Segoe UI';" +
                "-fx-font-size:13px;" +
                "-fx-cursor:hand;" +
                "-fx-padding:6 14;";
    }

    private <T extends Labeled> T styled(T node, String color, int size, boolean bold) {
        node.setStyle(
                "-fx-text-fill:" + color + ";" +
                        "-fx-font-size:" + size + "px;" +
                        "-fx-font-family:'Segoe UI';" +
                        (bold ? "-fx-font-weight:bold;" : "")
        );
        return node;
    }

    private FlowPane makeFlow() {
        FlowPane fp = new FlowPane();
        fp.setHgap(14); fp.setVgap(14);
        fp.setPadding(new Insets(16));
        fp.setStyle("-fx-background-color:" + BG + ";");
        return fp;
    }

    private ScrollPane scroll(FlowPane fp) {
        ScrollPane sp = new ScrollPane(fp);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + BG + ";-fx-background:" + BG + ";-fx-border-color:transparent;");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    // ── Nav ────────────────────────────────────────────────────
    private VBox navBtn(String label, String shortcut, StackPane target, HBox nav, List<VBox> all) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:" + TEXT_DIM + ";-fx-font-size:13px;-fx-font-family:'Segoe UI';");
        Label sc = new Label(shortcut);
        sc.setStyle("-fx-text-fill:#333;-fx-font-size:9px;-fx-font-family:'Segoe UI';");

        VBox box = new VBox(2, lbl, sc);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 40, 12, 40));
        box.setStyle("-fx-border-color:transparent transparent transparent transparent;-fx-cursor:hand;");
        box.setOnMouseClicked(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(180), target);
            ft.setFromValue(0.6); ft.setToValue(1); ft.play();
            root.setCenter(target);
            selectNav(nav, box, all);
        });
        return box;
    }

    private void selectNav(HBox nav, VBox selected, List<VBox> all) {
        for (VBox b : all) {
            Label l = (Label) b.getChildren().get(0);
            l.setStyle("-fx-text-fill:" + TEXT_DIM + ";-fx-font-size:13px;-fx-font-family:'Segoe UI';");
            b.setStyle("-fx-border-color:transparent;-fx-cursor:hand;-fx-background-color:transparent;");
        }
        Label sl = (Label) selected.getChildren().get(0);
        sl.setStyle("-fx-text-fill:" + ACCENT + ";-fx-font-size:13px;-fx-font-weight:bold;-fx-font-family:'Segoe UI';");
        selected.setStyle("-fx-border-color:transparent transparent " + ACCENT + " transparent;-fx-border-width:0 0 2 0;-fx-cursor:hand;-fx-background-color:#18141f;");
    }

    // ── Load / Save ────────────────────────────────────────────
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
                JSONArray sounds  = obj.getJSONArray("sounds");
                List<JSONObject> list = new ArrayList<>();
                for (int j = 0; j < sounds.length(); j++) list.add(sounds.getJSONObject(j));
                boards.put(boardName, list);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveBoards() {
        try {
            JSONArray arr = new JSONArray();
            for (String name : boards.keySet()) {
                JSONObject obj = new JSONObject();
                obj.put("board", name);
                obj.put("sounds", new JSONArray(boards.get(name)));
                arr.put(obj);
            }
            Files.writeString(STORAGE.toPath(), arr.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchBoard(String name) {
        if (name == null || !boards.containsKey(name)) return;
        stopAllClips();
        currentBoard = name;
        boardSelect.getSelectionModel().select(name);
        loadPlayFlow();
    }

    // ── Play flow (main grid) ──────────────────────────────────
    private void loadPlayFlow() {
        playFlow.getChildren().clear();
        clipCards.clear();
        hotkeyClips.clear();

        List<JSONObject> list = boards.getOrDefault(currentBoard, List.of());
        for (int i = 0; i < list.size(); i++) {
            JSONObject obj  = list.get(i);
            File       file = new File(obj.getString("path"));
            String     name = obj.getString("name");

            if (!file.exists()) {
                playFlow.getChildren().add(missingTile(name, i, list));
                continue;
            }

            AudioClip clip = new AudioClip(file.toURI().toString());
            loopState.put(clip, false);

            VBox card = buildTile(clip, name, file, i, list);
            clipCards.put(clip, card);

            if (i < HOTKEYS.length) hotkeyClips.put(i, clip);

            playFlow.getChildren().add(card);
        }
    }

    // ── Tile builder ───────────────────────────────────────────
    private VBox buildTile(AudioClip clip, String name, File file, int index, List<JSONObject> list) {
        String accentColor = TILE_COLORS[index % TILE_COLORS.length];
        String hotkey      = index < HOTKEYS.length ? String.valueOf(index + 1 == 10 ? 0 : index + 1) : "";

        // Duration label (async, non-blocking)
        Label durationLbl = new Label("—");
        durationLbl.setStyle("-fx-text-fill:" + TEXT_DIM + ";-fx-font-size:11px;-fx-font-family:'Segoe UI';-fx-font-family:'Segoe UI';");
        Media m = new Media(file.toURI().toString());
        MediaPlayer mp = new MediaPlayer(m);
        mp.setOnReady(() -> {
            double sec = m.getDuration().toSeconds();
            Platform.runLater(() -> durationLbl.setText(String.format("%.1fs", sec)));
            mp.dispose();
        });
        mp.setOnError(() -> mp.dispose());

        // Name label
        Label nameLbl = new Label(name);
        nameLbl.setStyle(
                "-fx-text-fill:" + TEXT + ";" +
                        "-fx-font-size:15px;" +
                        "-fx-font-weight:bold;" +
                        "-fx-font-family:'Segoe UI';" +
                        "-fx-wrap-text:true;"
        );
        nameLbl.setMaxWidth(160);

        // Hotkey badge
        Label hkLbl = new Label(hotkey.isEmpty() ? "" : "[" + hotkey + "]");
        hkLbl.setStyle("-fx-text-fill:#333;-fx-font-size:10px;-fx-font-family:'Segoe UI';");

        // Playing indicator bar (hidden by default)
        Region playingBar = new Region();
        playingBar.setPrefHeight(3);
        playingBar.setStyle("-fx-background-color:" + PLAYING + ";-fx-background-radius:2;");
        playingBar.setVisible(false);

        // Action buttons
        Button playBtn = tileBtn("▶", accentColor);
        Button loopBtn = tileBtn("↻", TEXT_DIM);
        Button stopBtn = tileBtn("◼", DANGER);
        stopBtn.setVisible(false);

        playBtn.setOnAction(e -> {
            loopState.put(clip, false);
            clip.setCycleCount(1);
            clip.play();
            updateTileState(clip, playingBar, stopBtn, loopBtn, false);
        });

        loopBtn.setOnAction(e -> {
            boolean isLooping = loopState.get(clip);
            if (isLooping) {
                clip.stop();
                loopState.put(clip, false);
                updateTileState(clip, playingBar, stopBtn, loopBtn, false);
            } else {
                clip.setCycleCount(AudioClip.INDEFINITE);
                clip.play();
                loopState.put(clip, true);
                updateTileState(clip, playingBar, stopBtn, loopBtn, true);
            }
        });

        stopBtn.setOnAction(e -> {
            clip.stop();
            loopState.put(clip, false);
            updateTileState(clip, playingBar, stopBtn, loopBtn, false);
        });

        // Delete button (top-right corner)
        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle(
                "-fx-background-color:transparent;" +
                        "-fx-text-fill:#444;" +
                        "-fx-font-size:12px;" +
                        "-fx-cursor:hand;" +
                        "-fx-padding:0;"
        );
        deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:" + DANGER + ";-fx-font-size:12px;-fx-cursor:hand;-fx-padding:0;"
        ));
        deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle(
                "-fx-background-color:transparent;-fx-text-fill:#444;-fx-font-size:12px;-fx-cursor:hand;-fx-padding:0;"
        ));
        deleteBtn.setOnAction(e -> {
            clip.stop();
            loopState.remove(clip);
            clipCards.remove(clip);
            list.removeIf(obj -> obj.getString("name").equals(name) && obj.getString("path").equals(file.getAbsolutePath()));
            saveBoards();
            loadPlayFlow();
        });

        // Header row: hotkey + name + delete
        HBox header = new HBox(hkLbl, new Region(), deleteBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.TOP_CENTER);

        HBox actions = new HBox(8, playBtn, loopBtn, stopBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox footer = new HBox(actions, new Region(), durationLbl);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER);

        VBox card = new VBox(6, header, nameLbl, new Region(), playingBar, footer);
        card.setPrefSize(190, 130);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setStyle(cardStyle(accentColor, false));
        VBox.setVgrow(card.getChildren().get(2), Priority.ALWAYS);

        // Hover
        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), card);
            st.setToX(1.04); st.setToY(1.04); st.play();
            card.setStyle(cardStyle(accentColor, true));
        });
        card.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), card);
            st.setToX(1.0); st.setToY(1.0); st.play();
            card.setStyle(cardStyle(accentColor, false));
        });

        // Double-click to play (convenience)
        card.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                loopState.put(clip, false);
                clip.setCycleCount(1);
                clip.play();
                updateTileState(clip, playingBar, stopBtn, loopBtn, false);
            }
        });

        return card;
    }

    private void updateTileState(AudioClip clip, Region bar, Button stop, Button loop, boolean isLooping) {
        boolean active = isLooping || clip.isPlaying();
        bar.setVisible(active);
        stop.setVisible(active);
        loop.setStyle(tileBtn("↻", isLooping ? PLAYING : TEXT_DIM).getStyle());
    }

    private void triggerClip(AudioClip clip) {
        loopState.put(clip, false);
        clip.setCycleCount(1);
        clip.play();
        VBox card = clipCards.get(clip);
        if (card != null) {
            // Quick flash feedback
            ScaleTransition st = new ScaleTransition(Duration.millis(80), card);
            st.setToX(0.96); st.setToY(0.96);
            st.setOnFinished(e -> {
                ScaleTransition back = new ScaleTransition(Duration.millis(100), card);
                back.setToX(1.0); back.setToY(1.0); back.play();
            });
            st.play();
        }
    }

    private void stopAllClips() {
        for (AudioClip clip : loopState.keySet()) {
            clip.stop();
            loopState.put(clip, false);
        }
        // Reset all playing bars
        for (Map.Entry<AudioClip, VBox> entry : clipCards.entrySet()) {
            VBox card = entry.getValue();
            // Find and hide playing bar (index 3) and stop btn
            if (card.getChildren().size() > 3) {
                Region bar = (Region) card.getChildren().get(3);
                bar.setVisible(false);
            }
        }
    }

    // ── Missing file tile ──────────────────────────────────────
    private VBox missingTile(String name, int index, List<JSONObject> list) {
        String accentColor = "#444";
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill:#555;-fx-font-size:14px;-fx-font-weight:bold;-fx-font-family:'Segoe UI';");
        Label missing = new Label("File not found");
        missing.setStyle("-fx-text-fill:" + DANGER + ";-fx-font-size:11px;-fx-font-family:'Segoe UI';");
        Button del = tileBtn("✕", DANGER);
        del.setOnAction(e -> {
            list.remove(index);
            saveBoards();
            loadPlayFlow();
        });
        VBox card = new VBox(8, nameLbl, missing, del);
        card.setPrefSize(190, 100);
        card.setPadding(new Insets(12));
        card.setStyle(cardStyle(accentColor, false));
        return card;
    }

    // ── Style strings ──────────────────────────────────────────
    private String cardStyle(String accent, boolean hovered) {
        return "-fx-background-color:" + (hovered ? BG_HOVER : BG_CARD) + ";" +
                "-fx-border-color:" + accent + ";" +
                "-fx-border-width:1.5;" +
                "-fx-background-radius:10;" +
                "-fx-border-radius:10;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0," + (hovered ? "0.55" : "0.3") + "),12,0,0,4);";
    }

    private Button tileBtn(String icon, String color) {
        Button b = new Button(icon);
        b.setStyle(
                "-fx-background-color:transparent;" +
                        "-fx-text-fill:" + color + ";" +
                        "-fx-font-size:16px;" +
                        "-fx-cursor:hand;" +
                        "-fx-padding:2 6;" +
                        "-fx-background-radius:4;"
        );
        b.setOnMouseEntered(e -> b.setStyle(
                "-fx-background-color:#2a2a2a;" +
                        "-fx-text-fill:" + color + ";" +
                        "-fx-font-size:16px;" +
                        "-fx-cursor:hand;" +
                        "-fx-padding:2 6;" +
                        "-fx-background-radius:4;"
        ));
        b.setOnMouseExited(e -> b.setStyle(
                "-fx-background-color:transparent;" +
                        "-fx-text-fill:" + color + ";" +
                        "-fx-font-size:16px;" +
                        "-fx-cursor:hand;" +
                        "-fx-padding:2 6;" +
                        "-fx-background-radius:4;"
        ));
        return b;
    }

    // ── Add sound ──────────────────────────────────────────────
    private void addNewSound() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Sound File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aiff", "*.ogg")
        );
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;

        TextInputDialog dialog = new TextInputDialog(stripExt(file.getName()));
        dialog.setTitle("Name this sound");
        dialog.setHeaderText(null);
        dialog.setContentText("Label:");
        dialog.getDialogPane().setStyle("-fx-background-color:" + BG + ";");
        dialog.showAndWait().ifPresent(name -> {
            String label = name.isBlank() ? stripExt(file.getName()) : name;
            JSONObject obj = new JSONObject();
            obj.put("name", label);
            obj.put("path", file.getAbsolutePath());
            boards.get(currentBoard).add(obj);
            saveBoards();
            loadPlayFlow();
        });
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    // ── Boards view ────────────────────────────────────────────
    private StackPane buildBoardsView() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color:" + BG + ";");

        Label header = styled(new Label("Boards"), TEXT, 20, true);

        FlowPane bubbles = new FlowPane();
        bubbles.setHgap(10); bubbles.setVgap(10);
        bubbles.setPadding(new Insets(4));

        Runnable refresh = () -> refreshBoardBubbles(bubbles);
        refresh.run();

        TextField newBoardField = new TextField();
        newBoardField.setPromptText("New board name…");
        newBoardField.setPrefWidth(200);
        newBoardField.setStyle(
                "-fx-background-color:" + BG_CARD + ";" +
                        "-fx-text-fill:" + TEXT + ";" +
                        "-fx-prompt-text-fill:#444;" +
                        "-fx-font-family:'Segoe UI';" +
                        "-fx-border-color:" + BORDER + ";" +
                        "-fx-border-radius:6;" +
                        "-fx-background-radius:6;" +
                        "-fx-padding:7 10;"
        );

        Button addBtn = new Button("Add Board");
        addBtn.setStyle(btnStyle(ACCENT, "transparent", ACCENT));
        addBtn.setOnMouseEntered(e -> addBtn.setStyle(btnStyle(ACCENT, "#1e1a2e", ACCENT)));
        addBtn.setOnMouseExited(e ->  addBtn.setStyle(btnStyle(ACCENT, "transparent", ACCENT)));

        Runnable doAdd = () -> {
            String name = newBoardField.getText().trim();
            if (!name.isEmpty() && !boards.containsKey(name)) {
                boards.put(name, new ArrayList<>());
                boardSelect.getItems().add(name);
                saveBoards();
                refreshBoardBubbles(bubbles);
                newBoardField.clear();
            }
        };
        addBtn.setOnAction(e -> doAdd.run());
        newBoardField.setOnAction(e -> doAdd.run());  // Enter key in field

        HBox addRow = new HBox(10, newBoardField, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        Label tip = styled(new Label("Click a board to switch. Delete removes it and all its sounds."), TEXT_DIM, 11, false);

        content.getChildren().addAll(header, tip, bubbles, addRow);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + BG + ";-fx-background:" + BG + ";-fx-border-color:transparent;");

        // Store refresh reference so switchBoard can call it
        bubbles.setUserData(refresh);

        return new StackPane(sp);
    }

    private void refreshBoardBubbles(FlowPane container) {
        container.getChildren().clear();
        for (String boardName : boards.keySet()) {
            boolean active = boardName.equals(currentBoard);

            Label lbl = new Label(boardName);
            lbl.setStyle("-fx-text-fill:" + (active ? ACCENT : TEXT) + ";-fx-font-size:13px;-fx-font-family:'Segoe UI';");

            Label count = new Label(boards.get(boardName).size() + " sounds");
            count.setStyle("-fx-text-fill:" + TEXT_DIM + ";-fx-font-size:11px;-fx-font-family:'Segoe UI';");

            Button del = new Button("✕");
            del.setStyle("-fx-background-color:transparent;-fx-text-fill:#444;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:0 0 0 6;");
            del.setOnMouseEntered(e -> del.setStyle("-fx-background-color:transparent;-fx-text-fill:" + DANGER + ";-fx-font-size:11px;-fx-cursor:hand;-fx-padding:0 0 0 6;"));
            del.setOnMouseExited(e ->  del.setStyle("-fx-background-color:transparent;-fx-text-fill:#444;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:0 0 0 6;"));
            del.setOnAction(e -> {
                if (boardName.equals("Default Board")) return;
                boards.remove(boardName);
                boardSelect.getItems().remove(boardName);
                if (currentBoard.equals(boardName)) {
                    currentBoard = boards.keySet().iterator().next();
                    boardSelect.getSelectionModel().select(currentBoard);
                    loadPlayFlow();
                }
                saveBoards();
                refreshBoardBubbles(container);
            });

            VBox info = new VBox(2, lbl, count);

            HBox bubble = new HBox(10, info, del);
            bubble.setAlignment(Pos.CENTER_LEFT);
            bubble.setPadding(new Insets(10, 14, 10, 14));
            bubble.setStyle(bubbleStyle(active, false));
            bubble.setOnMouseEntered(e -> bubble.setStyle(bubbleStyle(active, true)));
            bubble.setOnMouseExited(e ->  bubble.setStyle(bubbleStyle(active, false)));
            bubble.setOnMouseClicked(e -> {
                if (!boardName.equals(currentBoard)) {
                    switchBoard(boardName);
                    refreshBoardBubbles(container);
                }
            });

            container.getChildren().add(bubble);
        }
    }

    private String bubbleStyle(boolean active, boolean hovered) {
        String bg     = active ? "#1e1a2e" : (hovered ? BG_HOVER : BG_CARD);
        String border = active ? ACCENT_DIM : (hovered ? "#3a3a3a" : BORDER);
        return "-fx-background-color:" + bg + ";" +
                "-fx-border-color:" + border + ";" +
                "-fx-border-width:1;" +
                "-fx-border-radius:8;" +
                "-fx-background-radius:8;" +
                "-fx-cursor:hand;";
    }

    public static void main(String[] args) { launch(); }
}