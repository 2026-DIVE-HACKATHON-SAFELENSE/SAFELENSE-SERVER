// 저장된 분석 결과를 한글 PDF 리포트 바이트로 변환하는 서비스
package com.safelense.analysis

import java.io.ByteArrayOutputStream
import org.openpdf.text.Document
import org.openpdf.text.Font
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.pdf.BaseFont
import org.openpdf.text.pdf.PdfWriter
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

private const val REPORT_FONT_PATH =
    "META-INF/resources/webjars/nanum-gothic-coding/4.0.0/fonts/NanumGothicCoding-Regular.ttf"

@Service
class AnalysisReportService {
    fun create(detail: AnalysisResultDetail): ByteArray {
        val output = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 48f, 48f, 48f, 48f)
        PdfWriter.getInstance(document, output)
        val fontBytes = ClassPathResource(REPORT_FONT_PATH).inputStream.use { it.readBytes() }
        val baseFont = BaseFont.createFont(
            "NanumGothicCoding-Regular.ttf",
            BaseFont.IDENTITY_H,
            BaseFont.EMBEDDED,
            false,
            fontBytes,
            null,
        )
        val titleFont = Font(baseFont, 20f, Font.BOLD)
        val sectionFont = Font(baseFont, 14f, Font.BOLD)
        val bodyFont = Font(baseFont, 11f, Font.NORMAL)

        document.addTitle("세이프렌즈 위험 분석 리포트")
        document.open()
        try {
            document.add(Paragraph("세이프렌즈 위험 분석 리포트", titleFont))
            document.add(Paragraph("분석 ID  ${detail.id}", bodyFont))
            document.add(Paragraph("계약 단계  ${detail.stage}", bodyFont))
            document.add(Paragraph("위험 점수  ${detail.score ?: "판정 불가"}", bodyFont))
            document.add(Paragraph("위험 등급  ${detail.grade}", bodyFont))
            document.add(Paragraph("신뢰도  ${detail.confidence}", bodyFont))
            document.add(Paragraph("분석 시각  ${detail.analyzedAt}", bodyFont))
            document.add(Paragraph("요약", sectionFont))
            document.add(Paragraph(detail.summary, bodyFont))
            document.addItems("발견 사항", detail.findings, sectionFont, bodyFont)
            document.addItems("권고 사항", detail.recommendations, sectionFont, bodyFont)
        } finally {
            document.close()
        }
        return output.toByteArray()
    }

    private fun Document.addItems(
        title: String,
        items: List<String>,
        sectionFont: Font,
        bodyFont: Font,
    ) {
        add(Paragraph(title, sectionFont))
        if (items.isEmpty()) {
            add(Paragraph("없음", bodyFont))
        } else {
            items.forEach { add(Paragraph("- $it", bodyFont)) }
        }
    }
}
