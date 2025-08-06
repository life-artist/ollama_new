package org.example.ollama.controller;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.example.ollama.model.ChatMessage;
import org.example.ollama.model.ChatRequest;
import org.example.ollama.model.ChatResponse;
import org.example.ollama.service.DeepSeekService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/chat")
public class OllamaClientController {

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 主页，渲染聊天界面
     */
    @GetMapping("")
    public String chatPage() {
        return "chat"; // 返回 resources/templates/chat.html
    }

    /**
     * 接收前端消息，转发给 Ollama 并返回回复内容
     */
    @PostMapping("/api/message")
    @ResponseBody
    public ChatResponse handleMessage(@RequestBody ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : request.getMessages()) {
            Map<String, String> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            messages.add(message);
        }

        String reply = deepSeekService.generateResponse(messages);
        
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        
        List<Object> memoryList = new ArrayList<>(request.getMessages());
        ChatMessage assistantMsg = new ChatMessage("assistant", reply);
        memoryList.add(assistantMsg);
        response.setChatMemory(memoryList);
        
        return response;
    }

    /**
     * 支持文件上传的聊天接口
     */
    @PostMapping("/message-with-file")
    public ResponseEntity<Map<String, String>> handleMessageWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("message") String message,
            @RequestParam("messages") String messagesJson) {
        try {
            System.out.println("开始处理文件上传请求");
            if (file == null || file.isEmpty()) {
                System.out.println("文件为空");
                return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
            }
            String fileName = file.getOriginalFilename();
            System.out.println("文件名: " + fileName);
            if (fileName == null || (!fileName.endsWith(".txt") && !fileName.endsWith(".pdf") && !fileName.endsWith(".docx"))) {
                System.out.println("不支持的文件类型");
                return ResponseEntity.badRequest().body(Map.of("error", "不支持的文件类型，仅支持 .txt、.pdf 和 .docx"));
            }
            // 解析历史消息
            List<ChatMessage> chatMessages = objectMapper.readValue(messagesJson, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, ChatMessage.class));
            
            // 读取并解析文件内容
            String fileContent = readFileContent(file);
            System.out.println("文件内容: " + fileContent);
            if (fileContent.isEmpty()) {
                System.out.println("文件内容为空");
                return ResponseEntity.badRequest().body(Map.of("error", "文件内容为空或无法解析"));
            }
            
            // 构建完整的用户消息
            String fullMessage = message + "\n\n文件内容：\n" + fileContent;
            System.out.println("完整用户消息: " + fullMessage);
            
            // 构建API消息列表
            List<Map<String, String>> apiMessages = new ArrayList<>();
            for (ChatMessage chatMessage : chatMessages) {
                apiMessages.add(Map.of(
                    "role", chatMessage.getRole(),
                    "content", chatMessage.getContent()
                ));
            }
            // 添加当前消息
            apiMessages.add(Map.of("role", "user", "content", fullMessage));
            
            // 调用DeepSeek API
            String response = deepSeekService.generateResponse(apiMessages);
            System.out.println("API响应: " + response);
            
            // 更新聊天记忆
            chatMessages.add(new ChatMessage("user", fullMessage));
            chatMessages.add(new ChatMessage("assistant", response));
            
            // 保存到历史记录
            saveToHistory(chatMessages);
            
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "处理文件时发生错误: " + e.getMessage()));
        }
    }

    @PostMapping("/api/stop")
    @ResponseBody
    public ResponseEntity<String> stopGeneration() {
        // 由于使用API，不需要实现终止功能
        return ResponseEntity.ok("已终止生成");
    }

    // 读取文件内容的工具方法
    private String readFileContent(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        String content = "";
        if (fileName == null) return content;
        
        try (InputStream is = file.getInputStream()) {
            if (fileName.endsWith(".txt")) {
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
                    scanner.useDelimiter("\\A");
                    content = scanner.hasNext() ? scanner.next() : "";
                }
            } else if (fileName.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    content = stripper.getText(document);
                }
            } else if (fileName.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(is)) {
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                    content = extractor.getText();
                }
            } else {
                throw new IllegalArgumentException("不支持的文件类型，仅支持 .txt、.pdf 和 .docx");
            }
        }
        return content;
    }

    // 聊天历史保存方法（示例实现，可根据实际需求修改）
    private void saveToHistory(List<ChatMessage> chatMessages) {
        // 这里可以实现保存到数据库或文件等操作，目前留空
    }
}
