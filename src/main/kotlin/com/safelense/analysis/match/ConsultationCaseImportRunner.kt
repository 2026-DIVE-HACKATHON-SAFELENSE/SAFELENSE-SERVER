// 명시된 외부 XLSX를 한 번 적재하고 결과 건수만 기록하는 실행기
package com.safelense.analysis.match

import java.nio.file.Path
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.consultation-import", name = ["file"])
class ConsultationCaseImportRunner(
    private val service: ConsultationCaseImportService,
    @param:Value("\${app.consultation-import.file}") private val file: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val result = service.import(Path.of(file))
        logger.info(
            "Consultation import completed. read={}, upserted={}, failed={}, failedRows={}",
            result.read,
            result.upserted,
            result.failed,
            result.failedRows,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConsultationCaseImportRunner::class.java)
    }
}
