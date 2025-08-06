package org.example.ollama.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/chat/api/history")
public class ChatHistoryController {

    @Value("${chat.history.path:chat_history.json}")
    private String historyFilePath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory() {
        try {
            File file = new File(historyFilePath);
            if (!file.exists()) {
                return ResponseEntity.ok(new HashMap<>());
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            Map<String, Object> history = new HashMap<>();
            if (!content.isEmpty()) {
                history = objectMapper.readValue(content, Map.class);
            }
            return ResponseEntity.ok(history);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<String> saveHistory(@RequestBody Map<String, Object> history) {
        try {
            // 确保目录存在
            File file = new File(historyFilePath);
            file.getParentFile().mkdirs();

            // 将历史记录写入文件
            String content = objectMapper.writeValueAsString(history);
            Files.write(file.toPath(), content.getBytes());
            return ResponseEntity.ok("保存成功");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<String> deleteChat(@PathVariable String chatId) {
        try {
            File file = new File(historyFilePath);
            if (!file.exists()) {
                return ResponseEntity.ok("对话不存在");
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            Map<String, Object> history = objectMapper.readValue(content, Map.class);
            history.remove(chatId);

            // 保存更新后的历史记录
            String updatedContent = objectMapper.writeValueAsString(history);
            Files.write(file.toPath(), updatedContent.getBytes());
            return ResponseEntity.ok("删除成功");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
} 