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
@RequestMapping("/chat/api/memories")
public class ChatMemoryController {

    @Value("${chat.memory.path:chat_memory.json}")
    private String memoryFilePath;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMemories() {
        try {
            File file = new File(memoryFilePath);
            if (!file.exists()) {
                return ResponseEntity.ok(new HashMap<>());
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            Map<String, Object> memories = new HashMap<>();
            if (!content.isEmpty()) {
                memories = objectMapper.readValue(content, Map.class);
            }
            return ResponseEntity.ok(memories);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<String> saveMemories(@RequestBody Map<String, Object> memories) {
        try {
            // 确保目录存在
            File file = new File(memoryFilePath);
            file.getParentFile().mkdirs();

            // 将记忆写入文件
            String content = objectMapper.writeValueAsString(memories);
            Files.write(file.toPath(), content.getBytes());
            return ResponseEntity.ok("保存成功");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
} 