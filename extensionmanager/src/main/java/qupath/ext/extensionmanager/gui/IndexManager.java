package qupath.ext.extensionmanager.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.extensionmanager.core.ExtensionIndexManager;
import qupath.ext.extensionmanager.core.index.IndexFetcher;
import qupath.ext.extensionmanager.core.index.model.Index;
import qupath.ext.extensionmanager.core.savedentities.SavedIndex;
import qupath.ext.extensionmanager.core.tools.GitHubRawLinkFinder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * A window that allows managing indexes.
 */
public class IndexManager extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);
    private static final String INDEX_FILE_NAME = "index.json";
    private final ExtensionIndexManager extensionIndexManager;
    @FXML
    private TableView<SavedIndex> indexTable;
    @FXML
    private TableColumn<SavedIndex, String> nameColumn;
    @FXML
    private TableColumn<SavedIndex, URI> urlColumn;
    @FXML
    private TableColumn<SavedIndex, String> descriptionColumn;
    @FXML
    private TextField indexURL;

    /**
     * Create the window.
     *
     * @param extensionIndexManager the extension index manager this window should use
     * @param model the model to use when accessing data
     * @throws IOException when an error occurs while creating the window
     */
    public IndexManager(ExtensionIndexManager extensionIndexManager, ExtensionIndexModel model) throws IOException {
        this.extensionIndexManager = extensionIndexManager;

        UiUtils.loadFXML(this, IndexManager.class.getResource("index_manager.fxml"));

        indexTable.setItems(model.getIndexes());

        setColumns();
        setDoubleClickHandler();
        setRightClickHandler();
    }

    @FXML
    private void onAddClicked(ActionEvent ignored) {
        GitHubRawLinkFinder.getRawLinkOfFileInRepository(indexURL.getText(), INDEX_FILE_NAME::equals)
                .exceptionally(error -> {
                    logger.debug("Attempt to get raw link of {} failed. Considering it to be a raw link.", indexURL.getText(), error);

                    return URI.create(indexURL.getText());
                })
                .thenAcceptAsync(uri -> {
                    Index index = IndexFetcher.getIndex(uri).join();

                    if (extensionIndexManager.getIndexes().stream().anyMatch(savedIndex -> savedIndex.name().equals(index.name()))) {
                        Platform.runLater(() -> new Alert(
                                Alert.AlertType.ERROR,
                                String.format("An index with the same name (%s) already exists.", index.name())
                        ).show());
                        return;
                    }

                    try {
                        extensionIndexManager.addIndex(List.of(new SavedIndex(
                                index.name(),
                                index.description(),
                                new URI(indexURL.getText()),
                                uri
                        )));
                    } catch (URISyntaxException | SecurityException | NullPointerException | IOException e) {
                        logger.error(String.format("Error when saving index %s", index.name()), e);

                        Platform.runLater(() -> new Alert(
                                Alert.AlertType.ERROR,
                                String.format("Cannot save index:\n%s", e.getLocalizedMessage())
                        ).show());
                    }
                })
                .exceptionally(error -> {
                    logger.debug("Error when fetching index at {}", indexURL.getText(), error);

                    Platform.runLater(() -> new Alert(
                            Alert.AlertType.ERROR,
                            String.format("Cannot add index:\n%s", error.getLocalizedMessage())
                    ).show());

                    return null;
                });
    }

    private void setColumns() {
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        urlColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().uri()));
        descriptionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().description()));

        urlColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(URI item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
                    setText(item.toString());
                }
            }
        });
    }

    private void setDoubleClickHandler() {
        indexTable.setRowFactory(tv -> {
            TableRow<SavedIndex> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    String url = row.getItem().uri().toString();

                    UiUtils.openLinkInWebBrowser(url).exceptionally(error -> {
                        logger.error("Error when opening {} in browser", url, error);

                        Platform.runLater(() -> new Alert(
                                Alert.AlertType.ERROR,
                                String.format("Cannot open '%s':\n%s", url, error.getLocalizedMessage())
                        ).show());

                        return null;
                    });
                }
            });
            return row;
        });
    }

    private void setRightClickHandler() {
        indexTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ContextMenu menu = new ContextMenu();
        indexTable.setContextMenu(menu);

        MenuItem copyItem = new MenuItem("Copy URL");
        copyItem.setOnAction(ignored -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(indexTable.getSelectionModel().getSelectedItem().uri().toString());
            Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().add(copyItem);

        MenuItem removeItem = new MenuItem("Remove");
        removeItem.setOnAction(ignored -> {
            try {
                extensionIndexManager.removeIndexes(indexTable.getSelectionModel().getSelectedItems());
            } catch (IOException | SecurityException | NullPointerException e) {
                logger.error("Error when removing {}", indexTable.getSelectionModel().getSelectedItems(), e);

                new Alert(
                        Alert.AlertType.ERROR,
                        String.format("Cannot remove selected indexes:\n%s", e.getLocalizedMessage())
                ).show();
            }
        });
        menu.getItems().add(removeItem);
    }
}
