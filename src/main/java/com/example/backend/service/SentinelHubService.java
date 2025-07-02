// src/main/java/com/example/backend/service/SentinelHubService.java
package com.example.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SentinelHubService {

    @Value("${sentinelhub.process.api-url}")
    private String processApiUrl;

    private final SentinelHubAuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SentinelHubService(SentinelHubAuthService authService) {
        this.authService = authService;
    }

    /**
     * Запрашивает обработанное изображение (например, NDVI) для заданного полигона.
     *
     * @param polygonGeoJson GeoJSON строка геометрии полигона (только геометрия, не Feature).
     * @param analysisType Тип анализа (например, "NDVI", "TRUE_COLOR").
     * @param dateFrom Начальная дата для выборки данных (YYYY-MM-DD).
     * @param dateTo Конечная дата для выборки данных (YYYY-MM-DD).
     * @param width Ширина выходного изображения в пикселях.
     * @param height Высота выходного изображения в пикселях.
     * @return Массив байтов изображения (PNG).
     */
    public byte[] getProcessedImage(String polygonGeoJson, String analysisType, String dateFrom, String dateTo, int width, int height) {
        String accessToken = authService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        // Явно указываем Accept header для получения PNG изображения
        headers.set(HttpHeaders.ACCEPT, MediaType.IMAGE_PNG_VALUE); 

        try {
            // Создаем JSON-тело запроса для Process API
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Input section
            ObjectNode inputNode = requestBody.putObject("input");
            ObjectNode boundsNode = inputNode.putObject("bounds");
            boundsNode.set("geometry", objectMapper.readTree(polygonGeoJson)); // Вставляем GeoJSON геометрию полигона

            ObjectNode dataArrayNode = inputNode.putArray("data").addObject();
            dataArrayNode.put("type", "sentinel-2-l2a"); // Используем данные Sentinel-2 L2A

            ObjectNode dataFilterNode = dataArrayNode.putObject("dataFilter");
            ObjectNode timeRangeNode = dataFilterNode.putObject("timeRange");
            timeRangeNode.put("from", dateFrom + "T00:00:00Z");
            timeRangeNode.put("to", dateTo + "T23:59:59Z");
            dataFilterNode.put("mosaickingOrder", "leastCC"); // Выбираем наименее облачный снимок

            // Output section
            ObjectNode outputNode = requestBody.putObject("output");
            outputNode.put("width", width);
            outputNode.put("height", height);

            // responses должен быть массивом объектов, а format - объектом
            ArrayNode responsesArray = objectMapper.createArrayNode();
            ObjectNode formatObject = objectMapper.createObjectNode();
            formatObject.put("type", "image/png"); 
            
            responsesArray.addObject()
                    .put("identifier", "default")
                    .set("format", formatObject); 
            outputNode.set("responses", responsesArray); 

            // Evalscript section based on analysisType
            String evalscript = getEvalscriptForAnalysisType(analysisType);
            requestBody.put("evalscript", evalscript);

            log.debug("Sentinel Hub Process API Request Body: {}", requestBody.toPrettyString());

            HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

            // Отправляем POST-запрос и ожидаем массив байтов изображения
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    processApiUrl,
                    HttpMethod.POST,
                    request,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully fetched processed image for analysis type: {}", analysisType);
                return response.getBody();
            } else {
                log.error("Failed to fetch processed image. Status: {}", response.getStatusCode());
                // Попытка прочитать тело ответа для более детальной ошибки от Sentinel Hub
                String errorBody = response.getBody() != null ? new String(response.getBody()) : "[no body]";
                throw new RuntimeException("Failed to fetch processed image from Sentinel Hub. Response: " + errorBody);
            }

        } catch (HttpClientErrorException e) {
            log.error("HTTP error fetching Sentinel Hub image: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get Sentinel Hub image: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
                log.error("Error fetching Sentinel Hub image: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to get Sentinel Hub image", e);
        }
    }

    /**
     * Возвращает evalscript для различных типов анализа.
     * @param analysisType Тип анализа (например, "NDVI", "TRUE_COLOR").
     * @return Строка evalscript.
     */
    private String getEvalscriptForAnalysisType(String analysisType) {
        switch (analysisType.toUpperCase()) {
            case "NDVI":
            case "3_NDVI":
            case "3_NDVI-L1C":
                // Evalscript для NDVI с цветовой палитрой и явной прозрачностью
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [\"B04\", \"B08\", \"dataMask\"],\n" + // Используем dataMask\n" +
                       "    output: [\n" +
                       "      { id: \"default\", bands: 4, sampleType: \"UINT8\" } // RGBA, 8-bit unsigned integer\n" +
                       "    ]\n" +
                       "  };\n" +
                       "}\n" +
                       "\n" +
                       "// Плавный градиент NDVI с явным альфа-каналом\n" +
                       "const ramp = [\n" +
                       "  [-1.0, [0.0, 0.0, 0.0, 0]], // Полностью прозрачный для значений < -0.5\n" +
                       "  [-0.5, [0.0, 0.0, 0.0, 0]], // Полностью прозрачный для значений < -0.5\n" +
                       "  [ 0.0, [0.9, 0.9, 0.9, 0.5]], // Полупрозрачный серый для голой почвы/воды\n" +
                       "  [ 0.1, [0.8, 0.78,0.51, 1]], // Светло-желтый для редкой растительности\n" +
                       "  [ 0.2, [0.57,0.75,0.32, 1]],\n" +
                       "  [ 0.3, [0.44,0.64,0.25, 1]],\n" +
                       "  [ 0.4, [0.31,0.54,0.18, 1]],\n" +
                       "  [ 0.5, [0.19,0.43,0.11, 1]],\n" +
                       "  [ 0.6, [0.06,0.33,0.04, 1]],\n" +
                       "  [ 1.0, [0.0, 0.27, 0.0, 1]]\n" +
                       "];\n" +
                       "const visualizer = new ColorRampVisualizer(ramp);\n" +
                       "\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  let ndvi = index(samples.B08, samples.B04);\n" +
                       "  let rgb_with_alpha = visualizer.process(ndvi); // Получаем RGBA от visualizer\n" +
                       "  \n" +
                       "  // Финальный альфа-канал: произведение альфа из visualizer и dataMask, масштабированное до 0-255\n" +
                       "  let finalAlpha = rgb_with_alpha[3] * samples.dataMask * 255;\n" +
                       "\n" +
                       "  return {\n" +
                       "    default: [rgb_with_alpha[0] * 255, rgb_with_alpha[1] * 255, rgb_with_alpha[2] * 255, finalAlpha] // Масштабируем RGB на 255\n" +
                       "  };\n" +
                       "}";
            case "TRUE_COLOR":
            case "1_TRUE_COLOR":
            case "1_TRUE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  // Масштабирование для лучшей визуализации (умножаем на 255)\n" +
                       "  // Альфа-канал явно из dataMask, масштабированный до 0-255\n" +
                       "  return [samples.B04 * 255, samples.B03 * 255, samples.B02 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR":
            case "2_FALSE_COLOR":
            case "2_FALSE-COLOR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B08\", \"B04\", \"B03\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  // Масштабирование для лучшей визуализации (умножаем на 255)\n" +
                       "  // Альфа-канал явно из dataMask, масштабированный до 0-255\n" +
                       "  return [samples.B08 * 255, samples.B04 * 255, samples.B03 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "FALSE_COLOR_URBAN":
            case "4-FALSE-COLOR-URBAN":
            case "4-FALSE-COLOR-URBAN-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B11\", \"B08\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  // Масштабирование для лучшей визуализации (умножаем на 255)\n" +
                       "  // Альфа-канал явно из dataMask, масштабированный до 0-255\n" +
                       "  return [samples.B11 * 255, samples.B08 * 255, samples.B04 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "MOISTURE_INDEX":
            case "5-MOISTURE-INDEX1":
            case "5-MOISTURE-INDEX1-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B08\", \"B11\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  let val = (samples.B08 - samples.B11) / (samples.B08 + samples.B11);\n" +
                       "  // Цветовая палитра для индекса влажности (пример)\n" +
                       "  let color = colorBlend(val, [-1, -0.2, 0, 0.2, 0.4, 0.6, 0.8, 1], [\n" +
                       "    [0, 0, 0, 0], // Прозрачный\n" +
                       "    [0.9, 0.9, 0.9, 1], // Белый (сухо)\n" +
                       "    [0.9, 0.7, 0.7, 1], // Розовый\n" +
                       "    [0.7, 0.5, 0.5, 1], // Светло-красный\n" +
                       "    [0.5, 0.3, 0.3, 1], // Красный\n" +
                       "    [0.3, 0.1, 0.1, 1], // Темно-красный\n" +
                       "    [0.1, 0.0, 0.0, 1], // Очень темно-красный\n" +
                       "    [0.0, 0.0, 0.0, 1]  // Черный (очень влажно)\n" +
                       "  ]);\n" +
                       "  // Умножаем альфа-канал от colorBlend на samples.dataMask для финальной прозрачности\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * samples.dataMask * 255]; // Масштабируем RGB на 255\n" +
                       "}";
            case "NDSI":
            case "8-NDSI":
            case "8-NDSI-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B03\", \"B11\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  let val = (samples.B03 - samples.B11) / (samples.B03 + samples.B11);\n" +
                       "  // Цветовая палитра для NDSI (пример: от снега к отсутствию снега)\n" +
                       "  let color = colorBlend(val, [-1, 0, 0.2, 0.4, 0.6, 1], [\n" +
                       "    [0, 0, 0, 0], // Прозрачный\n" +
                       "    [0.7, 0.7, 0.7, 1], // Серый (вода)\n" +
                       "    [0.5, 0.8, 0.9, 1], // Голубоватый (не снег)\n" +
                       "    [0.8, 0.9, 1.0, 1], // Светло-голубой (возможен снег)\n" +
                       "    [0.9, 0.9, 1.0, 1], // Белый (снег)\n" +
                       "    [1.0, 1.0, 1.0, 1]  // Ярко-белый (чистый снег)\n" +
                       "  ]);\n" +
                       "  // Умножаем альфа-канал от colorBlend на samples.dataMask для финальной прозрачности\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * samples.dataMask * 255]; // Масштабируем RGB на 255\n" +
                       "}";
            case "NDWI":
            case "7-NDWI":
            case "7-NDWI-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B03\", \"B08\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  let val = (samples.B03 - samples.B08) / (samples.B03 + samples.B08);\n" +
                       "  // Цветовая палитра для NDWI (пример: от воды к суше)\n" +
                       "  let color = colorBlend(val, [-1, -0.2, 0, 0.2, 0.4, 0.6, 1], [\n" +
                       "    [0, 0, 0, 0], // Прозрачный\n" +
                       "    [0.9, 0.9, 0.9, 1], // Белый (суша)\n" +
                       "    [0.7, 0.7, 0.9, 1], // Светло-синий\n" +
                       "    [0.5, 0.5, 0.9, 1], // Средне-синий\n" +
                       "    [0.3, 0.3, 0.7, 1], // Темно-синий\n" +
                       "    [0.1, 0.1, 0.5, 1], // Очень темно-синий\n" +
                       "    [0.0, 0.0, 0.3, 1]  // Самый темный синий (чистая вода)\n" +
                       "  ]);\n" +
                       "  // Умножаем альфа-канал от colorBlend на samples.dataMask для финальной прозрачности\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * samples.dataMask * 255]; // Масштабируем RGB на 255\n" +
                       "}";
            case "SWIR":
            case "6-SWIR":
            case "6-SWIR-L1C":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B12\", \"B11\", \"B08\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  // Масштабирование для лучшей визуализации (умножаем на 255)\n" +
                       "  // Альфа-канал явно из dataMask, масштабированный до 0-255\n" +
                       "  return [samples.B12 * 255, samples.B11 * 255, samples.B08 * 255, samples.dataMask * 255];\n" +
                       "}";
            case "SCENE_CLASSIFICATION":
            case "SCENE-CLASSIFICATION":
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"SCL\", \"dataMask\"] }],\n" + // Добавил dataMask в input
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  let scl = samples.SCL;\n" +
                       "  let color = [0, 0, 0, 0]; // Прозрачный по умолчанию\n" +
                       "  if (scl === 1) color = [0.65, 0.65, 0.65, 1]; // Saturated / Defective\n" +
                       "  else if (scl === 2) color = [0.8, 0.8, 0.8, 1]; // Dark Area Pixels\n" +
                       "  else if (scl === 3) color = [0.9, 0.9, 0.9, 1]; // Cloud Shadows\n" +
                       "  else if (scl === 4) color = [0.1, 0.5, 0.1, 1]; // Vegetation\n" +
                       "  else if (scl === 5) color = [0.8, 0.6, 0.2, 1]; // Not-Vegetated\n" +
                       "  else if (scl === 6) color = [0.1, 0.1, 0.8, 1]; // Water\n" +
                       "  else if (scl === 7) color = [0.9, 0.9, 0.1, 1]; // Unclassified\n" +
                       "  else if (scl === 8) color = [0.7, 0.7, 0.7, 1]; // Medium Probability Clouds\n" +
                       "  else if (scl === 9) color = [0.9, 0.9, 0.9, 1]; // High Probability Clouds\n" +
                       "  else if (scl === 10) color = [0.9, 0.9, 0.9, 1]; // Cirrus\n" +
                       "  else if (scl === 11) color = [0.9, 0.9, 0.9, 1]; // Snow / Ice\n" +
                       "  // Умножаем альфа-канал от SCL на samples.dataMask для финальной прозрачности\n" +
                       "  return [color[0] * 255, color[1] * 255, color[2] * 255, color[3] * samples.dataMask * 255]; // Масштабируем RGB на 255\n" +
                       "}";
            // Highlight Optimized Natural Color (2_TONEMAPPED_NATURAL_COLOR)
            case "HIGHLIGHT_OPTIMIZED_NATURAL_COLOR":
            case "2_TONEMAPPED_NATURAL_COLOR":
            case "2_TONEMAPPED-NATURAL-COLOR-L1C":
                // Используем тот же evalscript, что и для True Color,
                // так как тональная компрессия обычно делается на стороне клиента или
                // требует более сложного evalscript, который выходит за рамки простого примера.
                // Для базового представления, это будет выглядеть как Natural Color.
                return "//VERSION=3\n" +
                       "function setup() {\n" +
                       "  return {\n" +
                       "    input: [{ bands: [\"B02\", \"B03\", \"B04\", \"dataMask\"] }],\n" +
                       "    output: { bands: 4, sampleType: \"UINT8\" }\n" + // Явно UINT8
                       "  };\n" +
                       "}\n" +
                       "function evaluatePixel(samples) {\n" +
                       "  // Альфа-канал явно из dataMask, масштабированный до 0-255\n" +
                       "  return [samples.B04 * 255, samples.B03 * 255, samples.B02 * 255, samples.dataMask * 255]; // Масштабируем RGB на 255\n" +
                       "}";

            default:
                throw new IllegalArgumentException("Unsupported analysis type: " + analysisType);
        }
    }
}
