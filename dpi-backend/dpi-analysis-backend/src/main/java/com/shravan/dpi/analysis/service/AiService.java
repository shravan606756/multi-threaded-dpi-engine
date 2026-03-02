package com.shravan.dpi.analysis.service;

import com.shravan.dpi.analysis.dto.response.AiExplanation;
import com.shravan.dpi.analysis.dto.response.DpiResult;
import com.shravan.dpi.analysis.exception.AiServiceException;
import com.shravan.dpi.analysis.util.HashUtil;
import com.shravan.dpi.analysis.util.RedisKeys;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {

    private final ChatClient chatClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public AiService(ChatClient.Builder chatClientBuilder,
                     RedisTemplate<String, Object> redisTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.redisTemplate = redisTemplate;
    }

    public AiExplanation generateExplanation(DpiResult dpiResult) {
        try {
            String prompt = generatePrompt(dpiResult);
            String cacheKey = HashUtil.generateCacheKey(prompt);
            String redisKey = RedisKeys.aiCache(cacheKey);

            // Check cache
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                System.out.println("[AI] Cache hit - using cached result");
                return (AiExplanation) cached;
            }

            System.out.println("[AI] Cache miss - calling Groq Llama 3...");

            // ✅ Call Groq via Spring AI (one line!)
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            System.out.println("\n=== AI RESPONSE ===");
            System.out.println(response);
            System.out.println("===================\n");

            AiExplanation explanation = extractStructuredInsights(response, dpiResult);

            // Cache for 1 hour
            redisTemplate.opsForValue().set(redisKey, explanation, 1, TimeUnit.HOURS);

            return explanation;

        } catch (Exception e) {
            System.err.println("[AI] Error calling Groq: " + e.getMessage());
            e.printStackTrace();
            return generateFallbackExplanation(dpiResult);
        }
    }

    private String generatePrompt(DpiResult dpiResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a network security analyst. Analyze this network traffic data.\n\n");

        prompt.append("Traffic Statistics:\n");
        prompt.append("- Total Packets: ").append(dpiResult.getTotalPackets()).append("\n");
        prompt.append("- Connections: ").append(dpiResult.getTotalConnections()).append("\n");
        prompt.append("- Classified: ").append(dpiResult.getClassified()).append("\n");
        prompt.append("- Unknown: ").append(dpiResult.getUnknown()).append("\n\n");

        if (dpiResult.getApplicationDistribution() != null && !dpiResult.getApplicationDistribution().isEmpty()) {
            prompt.append("Application Distribution:\n");
            dpiResult.getApplicationDistribution().forEach((app, count) ->
                    prompt.append("  - ").append(app).append(": ").append(count).append(" packets\n")
            );
        }

        prompt.append("\nProvide:\n");
        prompt.append("1. Brief summary (2-3 sentences)\n");
        prompt.append("2. Risk assessment (LOW/MEDIUM/HIGH)\n");
        prompt.append("3. Three key insights\n");

        return prompt.toString();
    }

    private AiExplanation extractStructuredInsights(String text, DpiResult dpiResult) {
        String summary = extractSummary(text);
        String riskLevel = determineRiskLevel(dpiResult);
        List<String> insights = extractInsights(text, dpiResult);

        return new AiExplanation(summary, riskLevel, insights);
    }

    private String extractSummary(String text) {
        String[] sentences = text.split("\\.");
        if (sentences.length > 0) {
            StringBuilder summary = new StringBuilder();
            int count = 0;
            for (String sentence : sentences) {
                sentence = sentence.trim();
                if (!sentence.isEmpty() &&
                        !sentence.toLowerCase().startsWith("here") &&
                        !sentence.toLowerCase().startsWith("based on")) {
                    summary.append(sentence).append(". ");
                    count++;
                    if (count >= 2) break;
                }
            }
            return summary.toString().trim();
        }
        return "Network traffic analysis completed successfully.";
    }

    private String determineRiskLevel(DpiResult dpiResult) {
        if (dpiResult.getTotalPackets() == 0) return "LOW";

        int unknownPercentage = (dpiResult.getUnknown() * 100) / dpiResult.getTotalPackets();

        if (unknownPercentage > 50) return "HIGH";
        else if (unknownPercentage > 20) return "MEDIUM";
        else return "LOW";
    }

    private List<String> extractInsights(String text, DpiResult dpiResult) {
        List<String> insights = new ArrayList<>();

        // Insight 1: Classification rate
        if (dpiResult.getTotalPackets() > 0) {
            int classificationRate = (dpiResult.getClassified() * 100) / dpiResult.getTotalPackets();
            insights.add(String.format("Successfully classified %d%% of network traffic", classificationRate));
        }

        // Insight 2: Top application
        if (dpiResult.getApplicationDistribution() != null && !dpiResult.getApplicationDistribution().isEmpty()) {
            Map.Entry<String, Integer> topApp = dpiResult.getApplicationDistribution().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (topApp != null) {
                insights.add(String.format("Highest activity: %s with %d packets",
                        topApp.getKey(), topApp.getValue()));
            }
        }

        // Insight 3: Connection count
        insights.add(String.format("Tracked %d unique network connections",
                dpiResult.getTotalConnections()));

        return insights;
    }

    private AiExplanation generateFallbackExplanation(DpiResult dpiResult) {
        System.out.println("[AI] Generating fallback explanation (AI service unavailable)");

        String summary = String.format(
                "Processed %d packets across %d connections. Classification success: %d%%.",
                dpiResult.getTotalPackets(),
                dpiResult.getTotalConnections(),
                dpiResult.getTotalPackets() > 0
                        ? (dpiResult.getClassified() * 100 / dpiResult.getTotalPackets())
                        : 0
        );

        String riskLevel = determineRiskLevel(dpiResult);
        List<String> insights = extractInsights("", dpiResult);

        return new AiExplanation(summary, riskLevel, insights);
    }
}