package com.zipstats.app

import android.app.Application

/** Application mínima para tests unitarios (evita inicializar Mapbox/Firebase). */
class TestApplication : Application()
