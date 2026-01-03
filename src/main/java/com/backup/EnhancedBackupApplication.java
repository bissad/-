package com.backup;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 增强版备份应用程序 - 支持打包功能
 */
public class EnhancedBackupApplication extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // 加载增强版的FXML文件
        Parent root = FXMLLoader.load(getClass().getResource("/main-view-enhanced.fxml"));
        
        primaryStage.setTitle("文件备份软件 - 增强版 (支持打包功能)");
        primaryStage.setScene(new Scene(root, 900, 750));
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(500);
        
        // 设置全屏提示
        primaryStage.setFullScreenExitHint("按ESC键退出全屏模式");
        
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}