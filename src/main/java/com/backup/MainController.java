package com.backup;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class MainController {
    
    @FXML
    private TextField sourceField;
    
    @FXML
    private TextField targetField;
    
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
        resultBox.setVisible(false);
        progressBar.setVisible(false);
    }
    
    @FXML
    private void handleSelectSource() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要备份的源目录");
        File selected = chooser.showDialog(getStage());
        if (selected != null) {
            sourceField.setText(selected.getAbsolutePath());
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
        String source = sourceField.getText();
        String target = targetField.getText();
        
        if (source.isEmpty() || target.isEmpty()) {
            showAlert("错误", "请选择源目录和目标目录");
            return;
        }
        
        if (source.equals(target)) {
            showAlert("错误", "源目录和目标目录不能相同");
            return;
        }
        
        performBackup(source, target);
    }
    
    private void performBackup(String source, String target) {
        backupButton.setDisable(true);
        progressBar.setVisible(true);
        resultBox.setVisible(false);
        statusLabel.setText("正在备份...");
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                return backupService.backupDirectory(source, target);
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
        return (Stage) sourceField.getScene().getWindow();
    }
}
