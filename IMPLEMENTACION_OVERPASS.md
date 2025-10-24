# Implementación de Overpass API con Sistema de Caché Inteligente

## 🎯 Resumen

Se ha implementado exitosamente la integración con **Overpass API** de OpenStreetMap para obtener lugares turísticos en tiempo real con las siguientes características:

- ✅ Solo lugares turísticos (museos, monumentos, parques, estatuas, etc.)
- ✅ Estructura JSON fija de Overpass API
- ✅ Sistema de caché inteligente basado en viewport
- ✅ Detección de movimientos del mapa
- ✅ Renderización desde Room (base de datos local)
- ✅ Límite de área offline a la ciudad del usuario
- ✅ Optimización de memoria RAM

## 📁 Arquitectura

### Componentes Creados

#### 1. **Modelos de Datos** (`data/model/`)
- `OverpassResponse`: Respuesta de la API
- `OverpassElement`: Elemento individual de OSM
- `TouristPlaceType`: Enum con tipos de lugares turísticos soportados

#### 2. **Servicio API** (`data/network/`)
- `OverpassApiService`: Interfaz Retrofit simplificada
- `OverpassQueryBuilder`: Construye queries optimizadas para Overpass
- `NetworkModule`: Configuración actualizada con timeout de 60s

#### 3. **Sistema de Caché** (`data/cache/`)
- `ViewportCache`: Trackea áreas ya descargadas usando cuadrícula de ~1km²
- `OfflineAreaManager`: Limita descarga offline al radio de la ciudad del usuario
- `BoundingBox`: Representa áreas rectangulares en coordenadas geográficas

#### 4. **Repositorio** (`data/repository/`)
- `PlacesRepository`: Simplificado para usar solo Overpass API
  - Maneja descarga inteligente basada en viewport
  - Verifica caché antes de descargar
  - Respeta límites de área offline

#### 5. **ViewModel** (`ui/map/`)
- `MapViewModel`: Actualizado con métodos basados en viewport
  - `loadPlacesForViewport()`: Carga datos para área visible
  - `setOfflineCenter()`: Configura ciudad base para offline
  - `preloadArea()`: Descarga área específica

#### 6. **Activity** (`ui/map/`)
- `MapActivity`: Detecta movimientos del mapa
  - Listeners de cámara (idle, move started)
  - Carga automática al mover el mapa
  - Configuración de área offline al obtener ubicación

## 🔄 Flujo de Funcionamiento

### Carga de Datos

```
1. Usuario mueve el mapa
   ↓
2. MapActivity detecta fin de movimiento (onCameraIdle)
   ↓
3. Extrae coordenadas del viewport visible
   ↓
4. MapViewModel.loadPlacesForViewport()
   ↓
5. PlacesRepository verifica:
   - ¿Área ya en caché? → Devuelve datos de Room
   - ¿No en caché + hay red? → Descarga de Overpass + guarda en Room
   - ¿Sin red? → Devuelve lo disponible en Room
   ↓
6. MapActivity renderiza lugares desde Room
```

### Sistema de Caché

**ViewportCache** divide el mundo en celdas de ~1km x 1km:

```
Viewport visible: [4.6°, 4.7°] x [-74.1°, -74.0°]
                      ↓
Celdas cubiertas:  46,740 | 46,741 | 46,742
                   47,740 | 47,741 | 47,742
                      ↓
¿Todas en caché? → NO descargar
¿Alguna falta? → Descargar solo las faltantes
```

### Límite Offline

**OfflineAreaManager** controla el radio máximo:

```
Usuario en Bogotá (4.71, -74.07)
Radio: 15 km (configurable)
              ↓
Área permitida: círculo de 15km alrededor
              ↓
Cualquier descarga fuera de este radio → bloqueada
```

## 🎨 Tipos de Lugares Turísticos Soportados

| Tipo | Ejemplo |
|------|---------|
| Museos | Museo del Oro, Museo Nacional |
| Monumentos | Monumento a Bolívar |
| Atracciones | Torre Colpatria |
| Parques | Parque Simón Bolívar |
| Estatuas | Estatua de la Pola |
| Castillos | Castillo de San Felipe |
| Ruinas | Ruinas de Teyuna |
| Galerías | Galería de Arte Moderno |
| Zoológicos | Zoológico de Bogotá |
| Miradores | Mirador La Calera |

## 📊 Queries de Overpass

### Query Completa (áreas < 25 km²)
```overpassql
[out:json][timeout:60];
(
  node["tourism"="museum"](bbox);
  node["tourism"="monument"](bbox);
  node["historic"="monument"](bbox);
  node["leisure"="park"](bbox);
  // ... más tipos
);
out center;
```

### Query Ligera (áreas > 25 km²)
```overpassql
[out:json][timeout:30];
(
  node["tourism"="museum"](bbox);
  node["tourism"="monument"](bbox);
  node["tourism"="attraction"](bbox);
  node["historic"="monument"](bbox);
  node["leisure"="park"](bbox);
);
out center;
```

## 🚀 Uso

### Configurar Área Offline
```kotlin
// En MapActivity, al obtener ubicación
viewModel.setOfflineCenter(lat, lon)
```

### Cargar Datos para Viewport
```kotlin
// Automático al mover el mapa
mapLibreMap.addOnCameraIdleListener {
    loadDataForCurrentViewport()
}
```

### Precargar Área para Offline
```kotlin
// Manualmente
viewModel.preloadArea(minLat, maxLat, minLon, maxLon)
```

### Obtener Info del Caché
```kotlin
val info = viewModel.getCacheInfo()
println("Celdas cacheadas: ${info.cachedCells}")
println("Área cubierta: ${info.cachedAreaKm2} km²")
```

## ⚡ Optimizaciones

### Memoria RAM
- Límite de 10,000 celdas en caché (~10,000 km²)
- Limpieza automática de áreas más antiguas
- Agrupación de celdas adyacentes para reducir requests

### Red
- Timeout de 60s para Overpass (API puede ser lenta)
- Queries optimizadas según tamaño del área
- Caché HTTP de OkHttp (10 MB)
- Solo descarga áreas no cacheadas

### Base de Datos
- Índices en campos `type`, `name` para búsquedas rápidas
- Estrategia `REPLACE` para evitar duplicados
- Flow para actualizaciones reactivas

## 🔧 Configuración

### Radios Predefinidos
```kotlin
OfflineAreaManager.RADIUS_SMALL_CITY    // 5 km
OfflineAreaManager.RADIUS_MEDIUM_CITY   // 15 km (default)
OfflineAreaManager.RADIUS_LARGE_CITY    // 30 km
OfflineAreaManager.RADIUS_METROPOLIS    // 50 km
```

### Tamaño de Celda
```kotlin
ViewportCache.GRID_SIZE = 0.01  // ~1.11 km
```

## 📱 Experiencia de Usuario

1. **Primera apertura**: 
   - Solicita ubicación
   - Configura área offline en ciudad actual
   - Carga lugares turísticos visibles

2. **Usuario mueve el mapa**:
   - Descarga automática en segundo plano
   - Sin intervención del usuario
   - Indicador de carga mientras descarga

3. **Modo offline**:
   - Muestra todos los lugares descargados previamente
   - Radio limitado a la ciudad configurada
   - No descarga nuevas áreas

4. **Búsqueda**:
   - Busca en base de datos local
   - Resultados instantáneos
   - Filtros por tipo de lugar

## 🎯 Ventajas de esta Implementación

✅ **Simplicidad**: Solo una API (Overpass), sin complejidad innecesaria
✅ **Performance**: Caché inteligente evita descargas duplicadas
✅ **Offline-first**: Datos en Room, siempre disponibles
✅ **Optimizado**: Memoria y red controladas
✅ **Escalable**: Fácil agregar más tipos de lugares
✅ **Mantenible**: Código limpio y bien documentado

## 📝 Notas Importantes

- **Overpass API tiene rate limits**: No abusar con requests muy frecuentes
- **Timeout de 60s**: Queries grandes pueden tardar
- **Área offline**: Configurar al inicio para mejor experiencia
- **Limpieza periódica**: Considerar limpiar caché antiguo

## 🔜 Posibles Mejoras Futuras

- [ ] WorkManager para descargas en segundo plano
- [ ] Sincronización periódica de datos
- [ ] Clustering de marcadores para mejor visualización
- [ ] Filtros avanzados (rating, distancia, etc.)
- [ ] Rutas entre lugares turísticos
- [ ] Modo offline con descarga manual de ciudades

---

**Implementado**: Octubre 2025
**Estado**: ✅ Completamente funcional

