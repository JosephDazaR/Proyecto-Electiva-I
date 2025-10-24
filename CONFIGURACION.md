# Configuración de la Aplicación MapLibre Data Integrator

## Descripción
Esta aplicación Android integra MapLibre, Retrofit, Room y WorkManager para mostrar datos turísticos, ambientales y gastronómicos en tiempo real y offline.

## Características Implementadas
- ✅ **MapLibre**: Mapa interactivo con capas personalizadas
- ✅ **Room Database**: Almacenamiento local de lugares
- ✅ **Retrofit**: Cliente HTTP para APIs externas
- ✅ **WorkManager**: Sincronización automática cada 6 horas
- ✅ **Cache Offline**: Tiles del mapa disponibles sin conexión
- ✅ **Filtros Inteligentes**: Por tipo de lugar y modo de visualización
- ✅ **Búsqueda**: Texto libre en nombres y descripciones

## APIs Integradas
1. **OpenStreetMap (Overpass API)**: Lugares turísticos, parques, restaurantes, cafés (GRATUITO)
2. **OpenAQ**: Calidad del aire (GRATUITO)

## Configuración Requerida

### 1. API Keys
Edita el archivo `ApiConfig.kt` y reemplaza la siguiente clave:

```kotlin
// MapLibre/Mapbox Token (gratuito)
const val MAPLIBRE_ACCESS_TOKEN = "TU_TOKEN_REAL_AQUI"
```

### 2. Obtener API Keys

#### MapLibre Token:
1. Ve a https://account.mapbox.com/
2. Crea una cuenta gratuita
3. Obtén tu access token

### 3. Configuración de Ubicación
En `ApiConfig.kt`, ajusta las coordenadas por defecto:

```kotlin
// Coordenadas por defecto (Bogotá)
const val DEFAULT_LAT = 4.7110  // Tu latitud
const val DEFAULT_LON = -74.0721  // Tu longitud
```

**¡IMPORTANTE!** OpenStreetMap y OpenAQ son completamente gratuitos, no requieren API keys.

## Estructura del Proyecto

```
app/src/main/java/com/example/proyectoelectivai/
├── data/
│   ├── model/           # Entidades de datos
│   ├── local/           # Room Database
│   ├── network/         # Retrofit y APIs
│   └── repository/      # Lógica de negocio
├── ui/map/              # Interfaz del mapa
└── MainActivity.kt     # Actividad principal
```

## Funcionalidades

### Mapa Interactivo
- **Puntos**: Lugares individuales
- **Clústeres**: Agrupación automática
- **Heatmap**: Densidad de lugares
- **Offline**: Funciona sin conexión

### Tipos de Lugares
- 🏛️ **Turísticos**: Museos, monumentos, atracciones
- 🌬️ **Calidad del Aire**: Estaciones de monitoreo
- 🌳 **Parques**: Áreas verdes y recreativas
- 🍽️ **Restaurantes**: Comida y bebida
- ☕ **Cafés**: Cafeterías y bares

### Filtros y Búsqueda
- Filtro por tipo de lugar
- Búsqueda por texto libre
- Modos de visualización
- Favoritos

## Uso de la Aplicación

### 1. Primera Ejecución
- La app solicitará permisos de ubicación
- Descargará tiles offline automáticamente
- Sincronizará datos de las APIs

### 2. Navegación
- **Tocar lugar**: Ver detalles en bottom sheet
- **FAB Filtros**: Abrir panel de filtros
- **FAB Capas**: Cambiar modo de visualización
- **FAB Ubicación**: Ir a ubicación actual

### 3. Modo Offline
- Los tiles se descargan automáticamente
- Los datos se almacenan en Room
- La app funciona sin conexión

## Sincronización Automática
- **Frecuencia**: Cada 6 horas
- **Condición**: Solo con conexión a internet
- **Datos**: Lugares turísticos, calidad del aire, OSM

## Solución de Problemas

### Error de API Key
```
Error: Invalid API key
```
**Solución**: Verifica que las API keys estén correctamente configuradas en `ApiConfig.kt`

### Sin datos offline
```
No se muestran lugares sin conexión
```
**Solución**: Asegúrate de que la app haya sincronizado al menos una vez con internet

### Permisos de ubicación
```
App no muestra ubicación actual
```
**Solución**: Ve a Configuración > Apps > [Tu App] > Permisos > Ubicación

## Personalización

### Agregar Nuevo Tipo de Lugar
1. Edita `Place.kt` para agregar el nuevo tipo
2. Actualiza `PlacesRepository.kt` con la nueva API
3. Agrega el color en `colors.xml`
4. Actualiza `FilterBottomSheet.kt`

### Cambiar Estilo del Mapa
1. Edita `MapActivity.kt`
2. Cambia el estilo en `setupMapLayers()`
3. Usa estilos de MapLibre disponibles

### Modificar Sincronización
1. Edita `WorkManagerModule.kt`
2. Cambia `SYNC_INTERVAL_HOURS`
3. Ajusta constraints según necesidades

## Dependencias Principales
- **MapLibre**: 11.5.1
- **Room**: 2.6.1
- **Retrofit**: 2.11.0
- **WorkManager**: 2.9.1
- **Material Design**: 1.13.0

## Notas Importantes
- La app requiere Android API 24+ (Android 7.0)
- Los datos se sincronizan solo con conexión a internet
- El cache offline se descarga automáticamente
- Las API keys son gratuitas con límites de uso

## Soporte
Para problemas o dudas, revisa:
1. Logs de Android Studio
2. Configuración de API keys
3. Permisos de la aplicación
4. Conectividad a internet
