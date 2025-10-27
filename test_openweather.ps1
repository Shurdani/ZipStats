# Script PowerShell para probar la API de OpenWeather

Write-Host "🌤️ Test de OpenWeather API" -ForegroundColor Cyan
Write-Host "============================" -ForegroundColor Cyan
Write-Host ""

# Leer API key de local.properties
if (Test-Path "local.properties") {
    $content = Get-Content "local.properties"
    $apiKeyLine = $content | Where-Object { $_ -match "openweather.api.key" }
    
    if ($apiKeyLine) {
        $apiKey = ($apiKeyLine -split "=")[1].Trim()
        
        if ([string]::IsNullOrWhiteSpace($apiKey)) {
            Write-Host "❌ No se encontró openweather.api.key en local.properties" -ForegroundColor Red
            exit 1
        }
        
        if ($apiKey -eq "YOUR_OPENWEATHER_API_KEY") {
            Write-Host "❌ API key no configurada (sigue siendo el placeholder)" -ForegroundColor Red
            Write-Host "Por favor, reemplaza YOUR_OPENWEATHER_API_KEY con tu API key real" -ForegroundColor Yellow
            exit 1
        }
        
        $apiKeyPreview = $apiKey.Substring(0, [Math]::Min(8, $apiKey.Length)) + "..." + $apiKey.Substring([Math]::Max(0, $apiKey.Length - 4))
        Write-Host "✅ API Key encontrada: $apiKeyPreview" -ForegroundColor Green
        Write-Host "   Longitud: $($apiKey.Length) caracteres"
        Write-Host ""
    } else {
        Write-Host "❌ No se encontró la línea openweather.api.key en local.properties" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ Archivo local.properties no encontrado" -ForegroundColor Red
    exit 1
}

# Coordenadas de prueba (Barcelona)
$lat = "41.3851"
$lon = "2.1734"

Write-Host "📍 Probando con coordenadas de Barcelona"
Write-Host "   Latitud: $lat"
Write-Host "   Longitud: $lon"
Write-Host ""

Write-Host "🌐 Realizando petición a OpenWeather..."

$url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=es"

try {
    $response = Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing
    $httpCode = $response.StatusCode
    $body = $response.Content | ConvertFrom-Json
    
    Write-Host "Código HTTP: $httpCode" -ForegroundColor Green
    Write-Host ""
    
    if ($httpCode -eq 200) {
        Write-Host "✅ ¡API funciona correctamente!" -ForegroundColor Green
        Write-Host ""
        Write-Host "📊 Respuesta:" -ForegroundColor Cyan
        $body | ConvertTo-Json -Depth 5
        Write-Host ""
        
        # Extraer info básica
        $temp = $body.main.temp
        $desc = $body.weather[0].description
        $icon = $body.weather[0].icon
        $city = $body.name
        
        Write-Host "🏙️  Ciudad: $city" -ForegroundColor Cyan
        Write-Host "🌡️  Temperatura: ${temp}°C" -ForegroundColor Yellow
        Write-Host "📝 Descripción: $desc" -ForegroundColor White
        Write-Host "🎨 Icono: $icon" -ForegroundColor Magenta
        
        # Mapear icono a emoji
        $emoji = switch ($icon) {
            "01d" { "☀️" }
            "01n" { "🌙" }
            "02d" { "🌤️" }
            "02n" { "☁️" }
            "03d" { "☁️" }
            "03n" { "☁️" }
            "04d" { "☁️" }
            "04n" { "☁️" }
            "09d" { "🌧️" }
            "09n" { "🌧️" }
            "10d" { "🌦️" }
            "10n" { "🌧️" }
            "11d" { "⛈️" }
            "11n" { "⛈️" }
            "13d" { "❄️" }
            "13n" { "❄️" }
            "50d" { "🌫️" }
            "50n" { "🌫️" }
            default { "☁️" }
        }
        
        Write-Host ""
        Write-Host "📱 En la app se mostrará: $emoji ${temp}°C" -ForegroundColor Cyan
    }
} catch {
    $httpCode = $_.Exception.Response.StatusCode.value__
    
    Write-Host "Código HTTP: $httpCode" -ForegroundColor Red
    Write-Host ""
    
    switch ($httpCode) {
        401 {
            Write-Host "❌ Error 401: API key inválida" -ForegroundColor Red
            Write-Host ""
            Write-Host "Posibles causas:" -ForegroundColor Yellow
            Write-Host "  1. La API key es incorrecta"
            Write-Host "  2. La API key está desactivada"
            Write-Host "  3. La API key es nueva y aún no está activa (espera 10-15 min)"
            Write-Host ""
            Write-Host "Verifica tu API key en: https://home.openweathermap.org/api_keys" -ForegroundColor Cyan
        }
        429 {
            Write-Host "❌ Error 429: Límite de llamadas excedido" -ForegroundColor Red
            Write-Host ""
            Write-Host "Plan gratuito de OpenWeather:" -ForegroundColor Yellow
            Write-Host "  • 60 llamadas por minuto"
            Write-Host "  • 1,000 llamadas por día"
            Write-Host ""
            Write-Host "Espera un minuto e intenta de nuevo"
        }
        default {
            Write-Host "❌ Error $httpCode" -ForegroundColor Red
            Write-Host ""
            Write-Host "Mensaje de error:" -ForegroundColor Yellow
            try {
                $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
                $errorBody | ConvertTo-Json
            } catch {
                Write-Host $_.Exception.Message
            }
        }
    }
}

Write-Host ""
Write-Host "============================" -ForegroundColor Cyan
Write-Host "Fin del test"
Write-Host ""
Write-Host "💡 Siguientes pasos:" -ForegroundColor Yellow
Write-Host "  1. Si el test fue exitoso, haz rebuild del proyecto:"
Write-Host "     .\gradlew clean assembleDebug"
Write-Host ""
Write-Host "  2. Desinstala y reinstala la app en el emulador"
Write-Host ""
Write-Host "  3. Abre Logcat y filtra por: WeatherRepository|StatsChips"
Write-Host ""
Write-Host "  4. Abre los detalles de una ruta y observa los logs"

