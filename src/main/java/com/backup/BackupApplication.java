package com.backup;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class BackupApplication extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/main-view.fxml"));
        
        primaryStage.setTitle("文件备份软件");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        
        // 设置全屏提示
        primaryStage.setFullScreenExitHint("按ESC键退出全屏模式");
        
        primaryStage.show();
        
        // 延迟设置全屏，确保窗口完全初始化
        // 使用更稳健的方式，避免可能的闪退
        javafx.application.Platform.runLater(() -> {
            try {
                // 先等待一小段时间确保窗口完全初始化
                Thread.sleep(500);
                javafx.application.Platform.runLater(() -> {
                    try {
                        primaryStage.setFullScreen(true);
                        System.out.println("全屏模式已启用");
                    } catch (Exception e) {
                        System.err.println("设置全屏失败: " + e.getMessage());
                        // 如果全屏失败，至少确保窗口正常显示
                    }
                });
            } catch (InterruptedException e) {
                // 忽略中断
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("全屏设置过程中出错: " + e.getMessage());
            }
        });
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
