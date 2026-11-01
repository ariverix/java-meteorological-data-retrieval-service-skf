package ru.ariverix.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class WeatherApplication {
    private static final String API_URL = "https://api.weather.yandex.ru/v2/forecast";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String apiKey = System.getenv("YANDEX_WEATHER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Укажите ключ API в переменной окружения YANDEX_WEATHER_API_KEY.");
            System.exit(1);
        }

        try {
            double lat = args.length > 0 ? Double.parseDouble(args[0]) : 55.75;
            double lon = args.length > 1 ? Double.parseDouble(args[1]) : 37.62;
            int limit = args.length > 2 ? Integer.parseInt(args[2]) : 3;
            validateArguments(lat, lon, limit);

            String response = fetchForecast(apiKey, lat, lon, limit);
            System.out.println("Полный ответ сервиса:");
            System.out.println(response);

            JsonNode weather = MAPPER.readTree(response);
            JsonNode currentTemperature = weather.path("fact").path("temp");
            if (!currentTemperature.isNumber()) {
                throw new IllegalStateException("В ответе отсутствует числовое поле fact.temp.");
            }

            System.out.println("Текущая температура: " + currentTemperature.asDouble() + " °C");
            double average = calculateAverage(weather, limit);
            System.out.printf(Locale.US,
                    "Средняя дневная температура за %d дн.: %.2f °C%n", limit, average);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Запрос был прерван.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Не удалось получить или обработать прогноз: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void validateArguments(double lat, double lon, int limit) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Широта должна быть от -90 до 90.");
        }
        if (!Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Долгота должна быть от -180 до 180.");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Количество дней должно быть положительным.");
        }
    }

    private static String fetchForecast(String apiKey, double lat, double lon, int limit)
            throws IOException, InterruptedException {
        String url = String.format(Locale.US, "%s?lat=%s&lon=%s&limit=%d",
                API_URL, lat, lon, limit);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("X-Yandex-Weather-Key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API Яндекс Погоды вернул HTTP " + response.statusCode()
                    + ". Проверьте ключ, тариф и параметры запроса.");
        }
        return response.body();
    }

    static double calculateAverage(JsonNode weather, int limit) {
        JsonNode forecasts = weather.path("forecasts");
        if (!forecasts.isArray() || forecasts.size() < limit) {
            throw new IllegalStateException("Сервис вернул меньше " + limit + " суток прогноза.");
        }

        double sum = 0;
        for (int day = 0; day < limit; day++) {
            JsonNode temperature = forecasts.get(day).path("parts").path("day").path("temp_avg");
            if (!temperature.isNumber()) {
                throw new IllegalStateException("Нет parts.day.temp_avg для дня " + (day + 1) + ".");
            }
            sum += temperature.asDouble();
        }
        return sum / limit;
    }
}
