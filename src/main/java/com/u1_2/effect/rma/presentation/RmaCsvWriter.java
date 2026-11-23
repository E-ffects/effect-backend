package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.rma.presentation.dto.RmaListResponse.Item;
import com.u1_2.effect.user.domain.Role;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

final class RmaCsvWriter {

    private static final List<Column> LIST_COLUMNS = List.of(
            new Column("RMA ID", Item::rmaId),
            new Column("접수 시각", Item::openedAt),
            new Column("고객사", Item::customerName),
            new Column("신고 품번", Item::reportedPartNo),
            new Column("불량 발견 단계", Item::detectionStage),
            new Column("불량 유형", Item::majorName),
            new Column("소분류", Item::minorName),
            new Column("분류 상태", Item::classificationStatus),
            new Column("신고 수량", Item::claimedDefectQty),
            new Column("분석 수준", Item::analysisLevel),
            new Column("고객 대응 상태", Item::csStatus),
            new Column("접수 상태", Item::intakeState),
            new Column("분석 상태", Item::analysisState));

    private static final List<Column> QUALITY_COLUMNS = List.of(
            new Column("심각도", Item::severity),
            new Column("QA 상태", Item::qaStatus),
            new Column("승인 상태", Item::approvalStatus),
            new Column("재발 판정", Item::recurrenceVerdict));

    private RmaCsvWriter() {
    }

    static byte[] write(List<Item> items, Role role) {
        List<Column> columns = role == Role.QUALITY
                ? Stream.concat(LIST_COLUMNS.stream(), QUALITY_COLUMNS.stream()).toList()
                : LIST_COLUMNS;
        // 엑셀에서 한글을 UTF-8로 인식하도록 파일 맨 앞에 BOM을 붙임
        var csv = new StringBuilder("\uFEFF");
        appendRow(csv, columns.stream().map(Column::title).toList());
        for (Item item : items) {
            appendRow(csv, columns.stream().map(column -> column.read(item)).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            appendCell(csv, cells.get(index));
        }
        csv.append("\r\n");
    }

    private static void appendCell(StringBuilder csv, String text) {
        String trimmed = text.stripLeading();
        // 고객사명 등 데이터가 스프레드시트 수식으로 실행되지 않게 일반 텍스트로 저장함
        if ((!trimmed.isEmpty() && "=+-@＝＋－＠".indexOf(trimmed.charAt(0)) >= 0)
                || (!text.isEmpty() && "\t\r\n".indexOf(text.charAt(0)) >= 0)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\r") || text.contains("\n")) {
            csv.append('"').append(text.replace("\"", "\"\"")).append('"');
        } else {
            csv.append(text);
        }
    }

    private record Column(String title, Function<Item, ?> value) {
        String read(Item item) {
            Object cell = value.apply(item);
            return cell == null ? "" : cell.toString();
        }
    }
}
