package com.kuvaszuptime.kuvasz.controllers.maintenance

import io.micronaut.http.client.annotation.Client

@Client("/api/v2/maintenance-windows")
interface MaintenanceWindowClient : MaintenanceWindowOperations
