package com.sorenkai.infrastructure.dto.mappers

import com.sorenkai.core.model.reports.Report
import com.sorenkai.infrastructure.dto.ReportMongoEntity
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Report.toMongoEntity(): ReportMongoEntity =
    ReportMongoEntity(
        id = ObjectId(this.id),
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        createdAt = createdAt,
        createdBy = createdBy,
        status = status,
    )

@OptIn(ExperimentalTime::class)
fun ReportMongoEntity.toCoreModel(): Report =
    Report(
        id = id.toHexString(),
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        createdAt = createdAt,
        createdBy = createdBy,
        status = status,
    )
