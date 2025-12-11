package com.backup;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class MainController {
    
    @FXML
    private ListView<String> sourceListView;
    
    @FXML
    private TextField targetField;
    
    private ObservableList<String> sourcePaths;
    
    @FXML
    private Button sourceButton;
    
    @FXML
    private Button targetButton;
    
    @FXML
    private Button backupButton;
    
    @FXML
    private ProgressBar progressBar;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private VBox resultBox;
    
    @FXML
    private Label filesCopiedLabel;
    
    @FXML
    private Label directoriesCreatedLabel;
    
    @FXML
    private Label totalSizeLabel;
    
    private BackupService backupService;
    
    @FXML
    public void initialize() {
        backupService = new BackupService();
        sourcePaths = FXCollections.observableArrayList();
        sourceListView.setItems(sourcePaths);
        resultBox.setVisible(false);
        progressBar.setVisible(false);
    }
    
    @FXML
    private void handleAddFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要备份的源文件夹");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            String path = selected.getAbsolutePath();
            String displayText = "[文件夹] " + path;
            if (!sourcePaths.contains(displayText)) {
                sourcePaths.add(displayText);
            } else {
                showAlert("提示", "该路径已存在");
            }
        }
    }
    
    @FXML
    private void handleAddFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要备份的文件");
        File selected = chooser.showOpenDialog(getStage());
        if (selected != null) {
            String path = selected.getAbsolutePath();
            String displayText = "[文件] " + path;
            if (!sourcePaths.contains(displayText)) {
                sourcePaths.add(displayText);
            } else {
                showAlert("提示", "该路径已存在");
            }
        }
    }
    
    @FXML
    private void handleRemoveSource() {
        String selected = sourceListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            sourcePaths.remove(selected);
        }
    }
    
    @FXML
    private void handleSelectTarget() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择备份目标目录");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            targetField.setText(selected.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleBackup() {
        String target = targetField.getText();
        
        if (sourcePaths.isEmpty()) {
            showAlert("错误", "请至少添加一个要备份的路径");
            return;
        }
        
        if (target.isEmpty()) {
            showAlert("错误", "请选择目标目录");
            return;
        }
        
        for (String source : sourcePaths) {
            if (source.equals(target)) {
                showAlert("错误", "源路径和目标目录不能相同: " + source);
                return;
            }
        }
        
        performBackup(target);
    }
    
    private void performBackup(String target) {
        backupButton.setDisable(true);
        progressBar.setVisible(true);
        resultBox.setVisible(false);
        statusLabel.setText("正在备份...");
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                java.util.List<String> actualPaths = new java.util.ArrayList<>();
                for (String displayPath : sourcePaths) {
                    String actualPath = displayPath.substring(displayPath.indexOf("] ") + 2);
                    actualPaths.add(actualPath);
                }
                return backupService.backupMultiple(actualPaths, target);
            }
        };
        
        backupTask.setOnSucceeded(event -> {
            BackupService.BackupResult result = backupTask.getValue();
            statusLabel.setText(result.getMessage());
            filesCopiedLabel.setText("文件复制: " + result.getFilesCopied());
            directoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            totalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            resultBox.setVisible(true);
            progressBar.setVisible(false);
            backupButton.setDisable(false);
        });
        
        backupTask.setOnFailed(event -> {
            Throwable exception = backupTask.getException();
            statusLabel.setText("备份失败: " + exception.getMessage());
            progressBar.setVisible(false);
            backupButton.setDisable(false);
        });
        
        new Thread(backupTask).start();
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private Stage getStage() {
        return (Stage) sourceListView.getScene().getWindow();
    }
}
