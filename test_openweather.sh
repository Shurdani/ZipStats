#!/bin/bash
# Script para probar la API de OpenWeather

echo "🌤️ Test de OpenWeather API"
echo "============================"
echo ""

# Leer API key de local.properties
if [ -f "local.properties" ]; then
    API_KEY=$(grep "openweather.api.key" local.properties | cut -d'=' -f2)
    
    if [ -z "$API_KEY" ]; then
        echo "❌ No se encontró openweather.api.key en local.properties"
        exit 1
    fi
    
    if [ "$API_KEY" == "YOUR_OPENWEATHER_API_KEY" ]; then
        echo "❌ API key no configurada (sigue siendo el placeholder)"
        echo "Por favor, reemplaza YOUR_OPENWEATHER_API_KEY con tu API key real"
        exit 1
    fi
    
    echo "✅ API Key encontrada: ${API_KEY:0:8}...${API_KEY: -4}"
    echo "   Longitud: ${#API_KEY} caracteres"
    echo ""
else
    echo "❌ Archivo local.properties no encontrado"
    exit 1
fi

# Coordenadas de prueba (Barcelona)
LAT="41.3851"
LON="2.1734"

echo "📍 Probando con coordenadas de Barcelona"
echo "   Latitud: $LAT"
echo "   Longitud: $LON"
echo ""

echo "🌐 Realizando petición a OpenWeather..."
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
    "https://api.openweathermap.org/data/2.5/weather?lat=$LAT&lon=$LON&appid=$API_KEY&units=metric&lang=es")

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE" | cut -d':' -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE/d')

echo "Código HTTP: $HTTP_CODE"
echo ""

case $HTTP_CODE in
    200)
        echo "✅ ¡API funciona correctamente!"
        echo ""
        echo "📊 Respuesta:"
        echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY" | jq . 2>/dev/null || echo "$BODY"
        echo ""
        
        # Extraer info básica
        TEMP=$(echo "$BODY" | grep -o '"temp":[0-9.]*' | cut -d':' -f2)
        DESC=$(echo "$BODY" | grep -o '"description":"[^"]*"' | cut -d'"' -f4)
        ICON=$(echo "$BODY" | grep -o '"icon":"[^"]*"' | cut -d'"' -f4)
        
        if [ ! -z "$TEMP" ]; then
            echo "🌡️  Temperatura: ${TEMP}°C"
            echo "📝 Descripción: $DESC"
            echo "🎨 Icono: $ICON"
        fi
        ;;
    401)
        echo "❌ Error 401: API key inválida"
        echo ""
        echo "Posibles causas:"
        echo "  1. La API key es incorrecta"
        echo "  2. La API key está desactivada"
        echo "  3. La API key es nueva y aún no está activa (espera 10-15 min)"
        echo ""
        echo "Verifica tu API key en: https://home.openweathermap.org/api_keys"
        ;;
    429)
        echo "❌ Error 429: Límite de llamadas excedido"
        echo ""
        echo "Plan gratuito de OpenWeather:"
        echo "  • 60 llamadas por minuto"
        echo "  • 1,000 llamadas por día"
        echo ""
        echo "Espera un minuto e intenta de nuevo"
        ;;
    *)
        echo "❌ Error $HTTP_CODE"
        echo ""
        echo "Respuesta del servidor:"
        echo "$BODY"
        ;;
esac

echo ""
echo "============================"
echo "Fin del test"

