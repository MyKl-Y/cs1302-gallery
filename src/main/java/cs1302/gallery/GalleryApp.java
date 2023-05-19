package cs1302.gallery;

import java.io.IOException;
import java.lang.InterruptedException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.net.URLEncoder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.geometry.Pos;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.util.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    // Instance Variables
    private Stage stage;
    private Scene scene;
    private VBox root = new VBox();
    HBox topHBox;
    HBox midHBox;
    HBox bottomHBox;
    GridPane imageGrid;
    Button playButton;
    Button getImagesButton;
    TextField urlField;
    ComboBox<String> dropdownMenu;
    Text search;
    Text instructions;
    Text defaultProvided;
    ImageView[][] images = new ImageView[5][4];
    Image defaultImage = new Image("file:resources/default.png");
    ProgressBar progressBar;
    String uriString;
    String[] currentURLS = new String[200];
    boolean isPlaying = false;
    boolean testFailed = false;
    Thread test;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox();
        this.topHBox = new HBox(10);
        this.midHBox = new HBox();
        this.bottomHBox = new HBox();
        this.playButton = new Button("Play");
        this.urlField = new TextField("joji");
        this.search = new Text("Search:");
        this.getImagesButton = new Button("Get Images");
        this.dropdownMenu = new ComboBox<String>();
        this.dropdownMenu.getItems().addAll("movie", "podcast", "music", "musicVideo",
            "audiobook", "shortFilm", "tvShow", "software", "ebook", "all");
        this.instructions = new Text("Type in a term, select a media type, then click the button.");
        this.progressBar = new ProgressBar(0);
        this.defaultProvided = new Text("Images provided by iTunes API.");
        this.imageGrid = new GridPane();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 4; j++) {
                ImageView temp = new ImageView(defaultImage);
                temp.setFitWidth(100);
                temp.setFitHeight(100);
                images[i][j] = temp;
                imageGrid.add(images[i][j], i, j);
            } // for
        } // for
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        this.playButton.setDisable(true);
        this.dropdownMenu.setPrefWidth(100);
        this.dropdownMenu.getSelectionModel().select(2);
        this.topHBox.getChildren().addAll(this.playButton, this.search, this.urlField,
            this.dropdownMenu, this.getImagesButton);
        this.midHBox.getChildren().addAll(this.instructions);
        this.progressBar.setPrefWidth(275);
        this.bottomHBox.getChildren().addAll(this.progressBar, this.defaultProvided);
        this.root.getChildren().addAll(this.topHBox, this.midHBox, this.imageGrid, this.bottomHBox);
        getImagesButton.setOnAction(event -> {
            instructions.setText("Getting images...");
            String url = urlField.getText();
            String type = dropdownMenu.getSelectionModel().getSelectedItem().toString();
            getImagesButton.setDisable(true);
            playButton.setDisable(true);
            new Thread(() -> {
                progressBar.setProgress(0);
                searchiTunes(url, type);
                String newURI = uriString;
                Platform.runLater(() -> {
                    if (testFailed == false) {
                        instructions.setText(newURI);
                    } else {
                        instructions.setText("Last attempt to get images failed...");
                    } // if
                    testFailed = false;
                    getImagesButton.setDisable(false);
                    playButton.setDisable(false);
                    if (images[4][3].getImage().getUrl().equals("file:resources/default.png")) {
                        playButton.setDisable(true);
                    } // if
                });
            }).start();
        });
        playButton.setOnAction(event -> {
            if (!isPlaying) {
                playButton.setText("Pause");
                isPlaying = true;
                test = new Thread(() -> {
                    while (isPlaying) {
                        randomizeImages();
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            break;
                        } // try
                    } // while
                });
                test.start();
            } else {
                playButton.setText("Play");
                isPlaying = false;
                if (test != null) {
                    test.interrupt();
                } // if
            } // if
        });
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.root);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

    /**
     * Sends a request to the iTunes API with the specified search term and category, and returns
     * a JSON representation of the data as a String.
     *
     * @param searchTerm the term to search for
     * @param category the category to search in
     * @return true if the number of results is greater than or equal to 21, false otherwise
     */
    public boolean searchiTunes(String searchTerm, String category) {
        searchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        URI uri;
        if (category.equals("all")) {
            uri = URI.create("https://itunes.apple.com/search?term=" + searchTerm + "&limit=200");
            uriString = uri.toString();
        } else {
            uri = URI.create("https://itunes.apple.com/search?term=" + searchTerm + "&media="
            + category + "&limit=200");
            uriString = uri.toString();
        } // if
        String jsonString = "";
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
            jsonString = response.body();
            ItunesResponse itunesResponse = GSON.fromJson(jsonString, ItunesResponse.class);
            if (itunesResponse.resultCount < 21) {
                testFailed = true;
                instructions.setText("Last attempt to get images failed...");
                playButton.setDisable(false);
                getImagesButton.setDisable(false);
                progressBar.setProgress(100);
                showErrorBox(uri.toString(), itunesResponse.resultCount);
                return false;
            } else {
                int count = 0;
                double progress = 0.0;
                double increment = 100.0 / itunesResponse.results.length;
                ItunesResult result;
                String url;
                for (int i = 0; i < itunesResponse.results.length && i < 200; i++) {
                    result = itunesResponse.results[i];
                    url = result.artworkUrl100;
                    currentURLS[i] = url;
                    progress += increment;
                    progressBar.setProgress(progress);
                } // for
                parseItunesResponse(itunesResponse);
                for (int j = 0; j < 4; j++) {
                    for (int k = 0; k < 5; k++) {
                        images[k][j].setImage(new Image(currentURLS[count]));
                        count++;
                    } // for
                } // for
                return true;
            } // if
        } catch (IOException | InterruptedException e) {
            System.err.println(e);
            return false;
        } // try
    } // searchiTunes

    /**
     * Class which traverses urlString and assigns a URI to the images
     * IF the image URI is unique, no replicates.
     * @param itunesResponse is the ItunesResponse object used in the method above.
     */
    public void parseItunesResponse(ItunesResponse itunesResponse) {
        String[] urls = new String[200];
        int counter = 0;
        for (int i = 0; i < 200; i++) {
            ItunesResult result = itunesResponse.results[i];
            Image temp = new Image(result.artworkUrl100);
            String url = temp.getUrl();
            boolean duplicate = false;
            for (int a =  0; a < counter; a++) {
                if (url.equals(urls[a])) {
                    duplicate = true;
                    break;
                } // if
            } // for
            if (!duplicate) {
                urls[counter] = url;
                counter++;
                if (counter == 20) {
                    break;
                } // if
            } // if
        } // for
        currentURLS = urls;
    } // parseItunesResponse

    /**
     * Class which randomizes the current images displayed.
     */
    public void randomizeImages() {
        int counter = 0;
        for (int i = 0; i < currentURLS.length; i++) {
            if (currentURLS[i] != null) {
                counter++;
            } // if
        } // for
        Random rand = new Random();
        int b = rand.nextInt(counter);
        int l = rand.nextInt(4);
        int w = rand.nextInt(5);
        Image temp = new Image(currentURLS[b]);
        images[l][w].setImage(new Image(currentURLS[b]));
    } // randomizeImages

    /**
     * Class which opens small GUI for errors if not enough results are found.
     * @param uri is the String uri.
     * @param count is how many results were found.
     */
    public void showErrorBox(String uri, int count) {
        Platform.runLater(() -> {
            Stage errorStage = new Stage();
            errorStage.setTitle("Error");
            errorStage.initModality(Modality.APPLICATION_MODAL);
            VBox errorBox = new VBox();
            Text errorMessage = new Text("URI: " + uri);
            Text uriText = new Text("Exception: java.lang.IllegalArgumentException: " + count +
                " distinct results were found, but 21 or more are needed.");
            Button closeButton = new Button("OK");
            closeButton.setOnAction(event -> errorStage.close());
            errorBox.getChildren().addAll(errorMessage, uriText, closeButton);
            errorBox.setDisable(true);
            Scene errorScene = new Scene(errorBox);
            errorStage.setScene(errorScene);
            errorStage.setOnShown(event -> {
                errorBox.setDisable(false);
            });
            errorStage.showAndWait();
        });
    } // showErrorBox
} // GalleryApp
