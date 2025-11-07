import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class SoundBoard extends Application {

    private static final String BUTTON_STYLE =
            "-fx-background-color: #CC5500;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-padding: 10px 20px;" +
                    "-fx-background-radius: 10;";

    private static final String BUTTON_HOVER_STYLE =
            "-fx-background-color: #FF7700" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-padding: 10px 20px;" +
                    "-fx-background-radius: 10;";

    @Override
    public void start(Stage stage) {
        // Container for all sound buttons
        FlowPane soundButtonsPane = new FlowPane();
        soundButtonsPane.setHgap(10);
        soundButtonsPane.setVgap(10);
        soundButtonsPane.setPadding(new Insets(12));
        soundButtonsPane.setPrefWrapLength(380);

        // Controls for adding new sounds
        TextField nameField = new TextField();
        nameField.setPromptText("Enter sound name");

        Button chooseFileBtn = new Button("Choose File");
        Label chosenLabel = new Label("No file chosen");
        chosenLabel.setStyle("-fx-text-fill: white;");

        Button addBtn = new Button("Add Sound");
        addBtn.setDisable(true);

        // Store the selected file.
        ObjectProperty<File> selectedFile = new SimpleObjectProperty<>(null);

        // File chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select sound file");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aiff", "*.m4a")
        );

        // Choose file button action
        chooseFileBtn.setOnAction(e -> {
            File f = fileChooser.showOpenDialog(stage);
            if (f != null) {
                selectedFile.set(f);
                chosenLabel.setText(f.getName());
                addBtn.setDisable(false);
            }
        });

        // Add sound button action
        addBtn.setOnAction(e -> {
            File f = selectedFile.get();
            if (f == null) return;

            String label = nameField.getText().trim();
            if (label.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Please enter a sound name before adding.").showAndWait();
                return;
            }

            // Try to load the audio file
            AudioClip clip;
            try {
                clip = new AudioClip(f.toURI().toString());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Cannot load audio: " + ex.getMessage()).showAndWait();
                return;
            }

            Button soundBtn = new Button(label);
            soundBtn.setStyle(BUTTON_STYLE);
            soundBtn.setOnMouseEntered(ev -> soundBtn.setStyle(BUTTON_HOVER_STYLE));
            soundBtn.setOnMouseExited(ev -> soundBtn.setStyle(BUTTON_STYLE));
            soundBtn.setOnAction(ev -> clip.play());

            // Right-click (context menu) to remove sound
            ContextMenu ctx = new ContextMenu();
            MenuItem remove = new MenuItem("Remove");
            remove.setOnAction(ae -> soundButtonsPane.getChildren().remove(soundBtn));
            ctx.getItems().add(remove);
            soundBtn.setOnContextMenuRequested((ContextMenuEvent cmEvent) ->
                    ctx.show(soundBtn, cmEvent.getScreenX(), cmEvent.getScreenY())
            );

            // Add new sound button to layout
            soundButtonsPane.getChildren().add(soundBtn);

            // Reset controls for next sound
            nameField.clear();
            selectedFile.set(null);
            chosenLabel.setText("No file chosen");
            addBtn.setDisable(true);
        });

        // Layout
        HBox controls = new HBox(10, nameField, chooseFileBtn, chosenLabel, addBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(10));
        HBox.setHgrow(nameField, Priority.ALWAYS);

        VBox root = new VBox(10, controls, new Separator(), soundButtonsPane);
        root.setPadding(new Insets(12));
        // Background color
        root.setStyle("-fx-background-color: #222222;");

        Scene scene = new Scene(root, 600, 400, Color.WHITE);
        stage.setScene(scene);
        stage.setTitle("Soundy 🎵");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}


