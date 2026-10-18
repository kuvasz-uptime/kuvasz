package com.kuvaszuptime.kuvasz.controllers.maintenance

import io.micronaut.http.client.annotation.Client

@Client("/api/v3/maintenance-windows")
interface MaintenanceWindowV3Client : MaintenanceWindowV3Operations
