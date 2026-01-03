package com.backup;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 修复版主控制器 - 将部分字段和方法改为protected以便子类访问
 */
public class MainControllerFixed {
    
    @FXML
    protected ListView<String> sourceListView;
    
    @FXML
    protected TextField targetField;
    
    protected ObservableList<String> sourcePaths;
    
    @FXML
    protected Button sourceButton;
    
    @FXML
    protected Button targetButton;
    
    @FXML
    protected Button backupButton;
    
    @FXML
    protected CheckBox includeSpecialFilesCheck;
    
    @FXML
    protected CheckBox preserveMetadataCheck;
    
    @FXML
    protected ProgressBar progressBar;
    
    @FXML
    protected Label statusLabel;
    
    @FXML
    protected VBox resultBox;
    
    @FXML
    protected Label filesCopiedLabel;
    
    @FXML
    protected Label directoriesCreatedLabel;
    
    @FXML
    protected Label totalSizeLabel;
    
    @FXML
    protected ListView<BackupItem> restoreSourceListView;
    
    @FXML
    protected TextField restoreTargetField;
    
    @FXML
    protected Button selectAllRestoreButton;
    
    @FXML
    protected Button removeRestoreSelectedButton;
    
    @FXML
    protected Button refreshRestoreButton;
    
    protected ObservableList<BackupItem> restoreBackupItems;
    
    @FXML
    protected Button restoreButton;
    
    @FXML
    protected CheckBox restorePreserveMetadataCheck;
    
    @FXML
    protected ProgressBar restoreProgressBar;
    
    @FXML
    protected Label restoreStatusLabel;
    
    @FXML
    protected VBox restoreResultBox;
    
    @FXML
    protected Label restoreFilesCopiedLabel;
    
    @FXML
    protected Label restoreDirectoriesCreatedLabel;
    
    @FXML
    protected Label restoreTotalSizeLabel;
    
    protected BackupService backupService;
    
    @FXML
    public void initialize() {
        backupService = new BackupService();
        
        sourcePaths = FXCollections.observableArrayList();
        sourceListView.setItems(sourcePaths);
        resultBox.setVisible(false);
        progressBar.setVisible(false);
        
        restoreBackupItems = FXCollections.observableArrayList();
        restoreSourceListView.setItems(restoreBackupItems);
        restoreResultBox.setVisible(false);
        restoreProgressBar.setVisible(false);
        
        // 修复复选框功能，避免监听器重复添加
        restoreSourceListView.setCellFactory(listView -> new javafx.scene.control.ListCell<BackupItem>() {
            private final javafx.scene.control.CheckBox checkBox = new javafx.scene.control.CheckBox();
            private BackupItem currentItem = null;
            private javafx.beans.value.ChangeListener<Boolean> checkboxListener = null;
            private javafx.beans.value.ChangeListener<Boolean> itemListener = null;
            
            {
                setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                setGraphic(checkBox);
                setText(null);
            }
            
            @Override
            protected void updateItem(BackupItem item, boolean empty) {
                super.updateItem(item, empty);
                
                // 移除旧item的监听器
                if (currentItem != null && itemListener != null) {
                    currentItem.selectedProperty().removeListener(itemListener);
                    itemListener = null;
                }
                
                // 移除复选框的旧监听器
                if (checkboxListener != null) {
                    checkBox.selectedProperty().removeListener(checkboxListener);
                    checkboxListener = null;
                }
                
                currentItem = item;
                
                if (empty || item == null) {
                    setText(null);
                    checkBox.setText(null);
                    checkBox.setSelected(false);
                    setGraphic(null);
                } else {
                    setText(item.getPath());
                    checkBox.setText(null);
                    
                    // 检查文件是否存在
                    java.io.File file = new java.io.File(item.getPath());
                    if (file.exists()) {
                        setGraphic(checkBox);
                        // 同步复选框状态
                        checkBox.setSelected(item.isSelected());
                        
                        // 监听复选框变化 -> 更新item
                        checkboxListener = (obs, oldVal, newVal) -> {
                            if (item.isSelected() != newVal) {
                                item.setSelected(newVal);
                            }
                        };
                        checkBox.selectedProperty().addListener(checkboxListener);
                        
                        // 监听item状态变化 -> 更新复选框
                        itemListener = (obs, oldVal, newVal) -> {
                            if (checkBox.isSelected() != newVal) {
                                checkBox.setSelected(newVal);
                            }
                        };
                        item.selectedProperty().addListener(itemListener);
                    } else {
                        setGraphic(null);
                        item.setSelected(false);
                    }
                }
            }
        });
        
        // 在后台线程中加载还原项列表，避免阻塞UI初始化
        javafx.concurrent.Task<Void> loadRestoreTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    List<String> restoreItems = backupService.getAvailableRestoreItems();
                    javafx.application.Platform.runLater(() -> {
                        for (String path : restoreItems) {
                            restoreBackupItems.add(new BackupItem(path));
                        }
                        if (restoreBackupItems.isEmpty()) {
                            restoreStatusLabel.setText("没有备份记录，请先进行备份操作");
                        } else {
                            restoreStatusLabel.setText("已加载备份记录");
                        }
                    });
                } catch (IOException e) {
                    javafx.application.Platform.runLater(() -> {
                        restoreStatusLabel.setText("加载备份记录失败: " + e.getMessage());
                    });
                }
                return null;
            }
        };
        
        Thread loadThread = new Thread(loadRestoreTask);
        loadThread.setDaemon(true);
        loadThread.start();
    }
    
    @FXML
    protected void handleAddFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择要备份的文件夹");
        
        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            String path = selectedDirectory.getAbsolutePath();
            if (!sourcePaths.contains(path)) {
                sourcePaths.add(path);
                showTempStatusMessage(statusLabel, "已添加文件夹: " + selectedDirectory.getName(), "#27ae60");
            }
        }
    }
    
    @FXML
    protected void handleAddFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要备份的文件");
        
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(getStage());
        if (selectedFiles != null) {
            for (File file : selectedFiles) {
                String path = file.getAbsolutePath();
                if (!sourcePaths.contains(path)) {
                    sourcePaths.add(path);
                }
            }
            showTempStatusMessage(statusLabel, "已添加 " + selectedFiles.size() + " 个文件", "#27ae60");
        }
    }
    
    @FXML
    protected void handleRemoveSource() {
        String selected = sourceListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            sourcePaths.remove(selected);
            showTempStatusMessage(statusLabel, "已移除: " + new File(selected).getName(), "#e74c3c");
        }
    }
    
    @FXML
    protected void handleSelectTarget() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择备份目标目录");
        
        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            targetField.setText(selectedDirectory.getAbsolutePath());
        }
    }
    
    @FXML
    protected void handleBackup() {
        // 检查源路径
        if (sourcePaths.isEmpty()) {
            showAlert("错误", "请先添加要备份的源路径");
            return;
        }
        
        // 检查目标路径
        String target = targetField.getText();
        if (target == null || target.trim().isEmpty()) {
            showAlert("错误", "请选择备份目标目录");
            return;
        }
        
        // 检查目标目录是否存在，如果不存在则创建
        File targetDir = new File(target);
        if (!targetDir.exists()) {
            try {
                if (!targetDir.mkdirs()) {
                    showAlert("错误", "无法创建目标目录: " + target);
                    return;
                }
            } catch (SecurityException e) {
                showAlert("错误", "没有权限创建目标目录: " + e.getMessage());
                return;
            }
        }
        
        // 检查目标目录是否可写
        if (!targetDir.canWrite()) {
            showAlert("错误", "目标目录不可写: " + target);
            return;
        }
        
        performBackup(target);
    }
    
    protected void performBackup(String target) {
        backupButton.setDisable(true);
        progressBar.setVisible(true);
        resultBox.setVisible(false);
        statusLabel.setText("正在备份...");
        
        // 创建备份选项
        final BackupService.BackupOptions options = new BackupService.BackupOptions(
            includeSpecialFilesCheck.isSelected(),
            preserveMetadataCheck.isSelected()
        );
        
        Task<BackupService.BackupResult> backupTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                List<String> actualPaths = new ArrayList<>(sourcePaths);
                return backupService.backupMultiple(actualPaths, target, options);
            }
        };
        
        backupTask.setOnSucceeded(event -> {
            BackupService.BackupResult result = backupTask.getValue();
            statusLabel.setText(result.getMessage());
            filesCopiedLabel.setText("文件备份: " + result.getFilesCopied());
            directoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            totalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            resultBox.setVisible(true);
            progressBar.setVisible(false);
            backupButton.setDisable(false);
            
            // 保存历史记录
            try {
                for (String sourcePath : result.getSuccessfulPaths()) {
                    backupService.saveBackupHistory(sourcePath, target);
                }
            } catch (IOException e) {
                System.err.println("保存历史记录失败: " + e.getMessage());
            }
        });
        
        backupTask.setOnFailed(event -> {
            Throwable exception = backupTask.getException();
            statusLabel.setText("备份失败: " + exception.getMessage());
            progressBar.setVisible(false);
            backupButton.setDisable(false);
        });
        
        Thread backupThread = new Thread(backupTask);
        backupThread.setDaemon(true);
        backupThread.start();
    }
    
    @FXML
    protected void handleSelectAllRestore() {
        for (BackupItem item : restoreBackupItems) {
            java.io.File file = new java.io.File(item.getPath());
            if (file.exists()) {
                item.setSelected(true);
            }
        }
    }
    
    @FXML
    protected void handleRemoveRestoreSelected() {
        List<BackupItem> itemsToRemove = new ArrayList<>();
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                itemsToRemove.add(item);
            }
        }
        
        if (!itemsToRemove.isEmpty()) {
            try {
                for (BackupItem item : itemsToRemove) {
                    backupService.removeRestoreItem(item.getPath());
                }
                restoreBackupItems.removeAll(itemsToRemove);
                showTempStatusMessage(restoreStatusLabel, "已移除 " + itemsToRemove.size() + " 个选中项", "#e74c3c");
            } catch (IOException e) {
                showAlert("错误", "移除选中项失败: " + e.getMessage());
            }
        }
    }
    
    @FXML
    protected void handleRefreshRestoreList() {
        refreshRestoreList();
    }
    
    @FXML
    protected void handleSelectRestoreTarget() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择还原目标目录");
        
        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            restoreTargetField.setText(selectedDirectory.getAbsolutePath());
        }
    }
    
    @FXML
    protected void handleRestore() {
        // 检查是否有选中的还原项
        boolean hasSelected = false;
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                hasSelected = true;
                break;
            }
        }
        
        if (!hasSelected) {
            showAlert("错误", "请先勾选要还原的备份项");
            return;
        }
        
        // 检查目标路径
        String target = restoreTargetField.getText();
        if (target == null || target.trim().isEmpty()) {
            showAlert("错误", "请选择还原目标目录");
            return;
        }
        
        // 检查目标目录是否存在，如果不存在则创建
        File targetDir = new File(target);
        if (!targetDir.exists()) {
            try {
                if (!targetDir.mkdirs()) {
                    showAlert("错误", "无法创建目标目录: " + target);
                    return;
                }
            } catch (SecurityException e) {
                showAlert("错误", "没有权限创建目标目录: " + e.getMessage());
                return;
            }
        }
        
        // 检查目标目录是否可写
        if (!targetDir.canWrite()) {
            showAlert("错误", "目标目录不可写: " + target);
            return;
        }
        
        // 检查选中的文件是否存在
        List<String> missingFiles = new ArrayList<>();
        for (BackupItem item : restoreBackupItems) {
            if (item.isSelected()) {
                File file = new File(item.getPath());
                if (!file.exists()) {
                    missingFiles.add(item.getPath());
                }
            }
        }
        
        if (!missingFiles.isEmpty()) {
            // 询问用户是否继续
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("文件不存在");
            alert.setHeaderText("以下选中的备份文件不存在:");
            alert.setContentText(String.join("\n", missingFiles) + "\n\n是否继续还原其他文件?");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    // 移除不存在的文件
                    List<BackupItem> itemsToRemove = new ArrayList<>();
                    for (BackupItem item : restoreBackupItems) {
                        if (missingFiles.contains(item.getPath())) {
                            itemsToRemove.add(item);
                        }
                    }
                    restoreBackupItems.removeAll(itemsToRemove);
                    
                    // 延迟执行还原，避免UI阻塞
                    new Thread(() -> {
                        try {
                            Thread.sleep(100);
                            javafx.application.Platform.runLater(() -> {
                                performRestore(target);
                            });
                        } catch (InterruptedException e) {
                            // 忽略中断
                        }
                    }).start();
                    return;
                }
            });
            return;
        }
        
        performRestore(target);
    }
    
    protected void performRestore(String target) {
        restoreButton.setDisable(true);
        restoreProgressBar.setVisible(true);
        restoreResultBox.setVisible(false);
        restoreStatusLabel.setText("正在还原...");
        
        // 创建还原选项
        final BackupService.BackupOptions options = new BackupService.BackupOptions(
            true,  // 还原时总是包含特殊文件记录
            restorePreserveMetadataCheck.isSelected()
        );
        
        Task<BackupService.BackupResult> restoreTask = new Task<>() {
            @Override
            protected BackupService.BackupResult call() throws Exception {
                ArrayList<String> actualPaths = new ArrayList<>();
                for (BackupItem item : restoreBackupItems) {
                    if (item.isSelected()) {
                        actualPaths.add(item.getPath());
                    }
                }
                return backupService.restoreMultiple(actualPaths, target, options);
            }
        };
        
        restoreTask.setOnSucceeded(event -> {
            BackupService.BackupResult result = restoreTask.getValue();
            restoreStatusLabel.setText(result.getMessage());
            restoreFilesCopiedLabel.setText("文件还原: " + result.getFilesCopied());
            restoreDirectoriesCreatedLabel.setText("目录创建: " + result.getDirectoriesCreated());
            restoreTotalSizeLabel.setText("总大小: " + formatSize(result.getTotalSize()));
            restoreResultBox.setVisible(true);
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
        });
        
        restoreTask.setOnFailed(event -> {
            Throwable exception = restoreTask.getException();
            restoreStatusLabel.setText("还原失败: " + exception.getMessage());
            restoreProgressBar.setVisible(false);
            restoreButton.setDisable(false);
        });
        
        Thread restoreThread = new Thread(restoreTask);
        restoreThread.setDaemon(true);
        restoreThread.start();
    }
    
    protected String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    protected void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    protected void refreshRestoreList() {
        try {
            List<String> restoreItems = backupService.getAvailableBackupSources();
            Set<String> existingPaths = new HashSet<>();
            
            // 收集现有路径
            for (BackupItem item : restoreBackupItems) {
                existingPaths.add(item.getPath());
            }
            
            // 添加新的还原项
            for (String path : restoreItems) {
                if (!existingPaths.contains(path)) {
                    restoreBackupItems.add(new BackupItem(path));
                }
            }
            
            // 检查并删除不存在的文件（这些文件在getAvailableRestoreItems中已经被过滤掉了）
            // 所以这里只需要处理UI列表中可能存在的无效项
            List<BackupItem> itemsToRemove = new ArrayList<>();
            for (BackupItem item : restoreBackupItems) {
                File file = new File(item.getPath());
                if (!file.exists()) {
                    itemsToRemove.add(item);
                }
            }
            if (!itemsToRemove.isEmpty()) {
                restoreBackupItems.removeAll(itemsToRemove);
            }
            
            // 更新状态标签
            if (restoreBackupItems.isEmpty()) {
                restoreStatusLabel.setText("没有备份记录，请先进行备份操作");
            } else {
                restoreStatusLabel.setText("已加载备份记录");
            }
        } catch (IOException e) {
            restoreStatusLabel.setText("刷新备份记录失败: " + e.getMessage());
        }
    }
    
    protected Stage getStage() {
        return (Stage) sourceListView.getScene().getWindow();
    }
    
    protected void showTempStatusMessage(Label label, String message, String color) {
        javafx.application.Platform.runLater(() -> {
            label.setText(message);
            label.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: " + color + ";");
        });
        
        // 创建守护线程来清除提示
        Thread clearThread = new Thread(() -> {
            try {
                Thread.sleep(3000);
                javafx.application.Platform.runLater(() -> {
                    if (label.getText().equals(message)) {
                        label.setText("");
                        label.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                    }
                });
            } catch (InterruptedException e) {
                // 忽略中断
            }
        });
        clearThread.setDaemon(true);
        clearThread.start();
    }
    
    protected static class BackupItem {
        private final String path;
        private final javafx.beans.property.BooleanProperty selected;
        
        public BackupItem(String path) {
            this.path = path;
            this.selected = new javafx.beans.property.SimpleBooleanProperty(false);
        }
        
        public String getPath() { return path; }
        public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }
        
        @Override
        public String toString() {
            return path;
        }
    }
}