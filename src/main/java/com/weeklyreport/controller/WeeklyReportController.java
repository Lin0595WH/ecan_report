package com.weeklyreport.controller;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;

import com.weeklyreport.common.ResultUtils;
import com.weeklyreport.entity.DepartmentalWeeklyReport;
import com.weeklyreport.entity.PersonalWeeklyReport;
import com.weeklyreport.exception.BusinessException;
import com.weeklyreport.exception.ErrorCode;
import com.weeklyreport.exception.ThrowUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @Author Lin
 * @Date 2025/6/18 22:09
 * @Descriptions 周报生成接口
 */
@Slf4j
@RestController
@RequestMapping("/api/weekly_report")
@RequiredArgsConstructor
public class WeeklyReportController {

    /**
     * 标题字体：新宋体
     */
    private final String TITLE_FONT = "NSimSun";

    /**
     * 正文字体：宋体
     */
    private final String CONTEXT_FONT = "SimSun";

    /**
     * 提交周报数据并生成会议纪要文档
     *
     * @param report 部门周报数据，包含会议信息和个人周报内容
     * @return 生成的Word文档响应流，或错误信息
     */
    @PostMapping
    public ResponseEntity<?> submitWeeklyReport(@RequestBody DepartmentalWeeklyReport report) {
        try {
            // 参数校验
            this.validateReport(report);

            // 日志记录
            this.logReportData(report);

            // 生成并返回文档
            return this.generateDocxResponse(report);

        } catch (BusinessException e) {
            log.error("业务异常: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("系统异常", e);
            return ResponseEntity.status(500)
                    .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统内部异常，请联系管理员"));
        }
    }

    /**
     * 校验周报请求数据的合法性
     *
     * @param report 部门周报数据
     * @throws BusinessException 当参数不合法时抛出业务异常
     */
    private void validateReport(DepartmentalWeeklyReport report) {
        ThrowUtils.throwIf(report == null, ErrorCode.PARAMS_ERROR, "周报数据不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(report.meetingAddress()), ErrorCode.PARAMS_ERROR, "会议地点不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(report.host()), ErrorCode.PARAMS_ERROR, "主持人不能为空");

        List<PersonalWeeklyReport> personalReports = report.personalWeeklyReports();
        ThrowUtils.throwIf(CollUtil.isEmpty(personalReports), ErrorCode.PARAMS_ERROR, "至少需要一个人员的周报内容");

        for (int i = 0; i < personalReports.size(); i++) {
            PersonalWeeklyReport pr = personalReports.get(i);
            ThrowUtils.throwIf(CharSequenceUtil.isBlank(pr.name()),
                    ErrorCode.PARAMS_ERROR, "第" + (i + 1) + "个人员姓名不能为空");

            ThrowUtils.throwIf(CharSequenceUtil.isBlank(pr.weeklyReportContent()),
                    ErrorCode.PARAMS_ERROR, pr.name() + "的周报内容不能为空");

            ThrowUtils.throwIf(pr.weeklyReportContent().length() > 2000,
                    ErrorCode.PARAMS_ERROR, pr.name() + "的周报内容不能超过2000字符");
        }
    }

    /**
     * 记录周报提交请求的基本信息
     *
     * @param report 部门周报数据
     */
    private void logReportData(DepartmentalWeeklyReport report) {
        log.info("收到周报提交请求:");
        log.info("会议地点: {}", report.meetingAddress());
        log.info("主持人: {}", report.host());
        log.info("周报内容:");

        report.personalWeeklyReports().forEach(pr -> {
            String preview = CharSequenceUtil.maxLength(pr.weeklyReportContent(), 50);
            log.info("- {}: {}", pr.name(), preview);
        });
    }

    /**
     * 生成Word文档响应流
     *
     * @param report 部门周报数据
     * @return 包含Word文档的响应实体
     * @throws IOException 当生成文档过程中发生IO异常时抛出
     */
    private ResponseEntity<InputStreamResource> generateDocxResponse(DepartmentalWeeklyReport report) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XWPFDocument document = this.createDocxDocument(report);
            document.write(bos);

            String fileName = this.generateDocxFileName();
            // 对文件名进行 UTF-8 URL 编码（仅保留 %XX 格式）
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
            HttpHeaders headers = new HttpHeaders();
            // 设置正确的 docx 类型（比 APPLICATION_OCTET_STREAM 更精确）
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

            // 手动构建 Content-Disposition 响应头，避免 MIME 编码
            String contentDisposition = String.format(
                    "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    encodedFileName,  // 兼容旧浏览器（纯 URL 编码）
                    encodedFileName   // 现代浏览器优先解析（显式 UTF-8 声明）
            );
            headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(bos.size())
                    .body(new InputStreamResource(new ByteArrayInputStream(bos.toByteArray())));
        } catch (IOException e) {
            throw new RuntimeException("生成周报文档失败", e);
        }
    }

    /**
     * 创建Word文档对象并填充内容
     *
     * @param report 部门周报数据
     * @return 填充好内容的Word文档对象
     */
    private XWPFDocument createDocxDocument(DepartmentalWeeklyReport report) {
        XWPFDocument document = new XWPFDocument();
        this.addTitle(document);
        this.addMeetingInfo(document, report);
        this.addAttendees(document, report);
        this.addSubtitle(document, "会议纪要如下：");
        this.addPersonalReports(document, report);
        this.addReviewContent(document, report);
        return document;
    }

    /**
     * 添加文档标题
     *
     * @param document Word文档对象
     */
    private void addTitle(XWPFDocument document) {
        XWPFParagraph para = document.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = para.createRun();
        run.setText("HRP开发三部周例会会议纪要");
        run.setFontFamily(TITLE_FONT);
        run.setFontSize(22);
        run.setBold(true);
    }

    /**
     * 添加会议基本信息（时间和地点）
     *
     * @param document Word文档对象
     * @param report   部门周报数据
     */
    private void addMeetingInfo(XWPFDocument document, DepartmentalWeeklyReport report) {
        // 获取当周周五日期
        Date friday = this.getCurrentWeekFriday();
        String dateStr = DateUtil.format(friday, "yyyy年MM月dd日");

        this.addParagraph(document, "会议时间：" + dateStr + "（星期五）");
        this.addParagraph(document, "会议地点：" + report.meetingAddress());
    }

    /**
     * 添加参会人员信息
     *
     * @param document Word文档对象
     * @param report   部门周报数据
     */
    private void addAttendees(XWPFDocument document, DepartmentalWeeklyReport report) {
        String attendees = CharSequenceUtil.join("、",
                report.personalWeeklyReports().stream().map(PersonalWeeklyReport::name).toArray());

        this.addParagraph(document, "出席人员：");
        this.addParagraph(document, "\t林敦龙、" + attendees);
        this.addParagraph(document, "主持人（编写人）：" + report.host());

        // 获取当周周五日期作为编写时间
        Date friday = this.getCurrentWeekFriday();
        this.addParagraph(document, "编写时间：" + DateUtil.format(friday, "yyyy年MM月dd日"));
    }

    /**
     * 添加文档副标题
     *
     * @param document Word文档对象
     * @param subtitle 副标题文本
     */
    private void addSubtitle(XWPFDocument document, String subtitle) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(subtitle);
        run.setFontSize(14);
        run.setFontFamily("NSimSun");
        run.setBold(true);
    }

    /**
     * 添加所有人员的周报内容
     *
     * @param document Word文档对象
     * @param report   部门周报数据
     */
    private void addPersonalReports(XWPFDocument document, DepartmentalWeeklyReport report) {
        for (int i = 0; i < report.personalWeeklyReports().size(); i++) {
            this.addPersonReport(document, report.personalWeeklyReports().get(i), i + 1);
        }
    }

    /**
     * 添加单个人的周报内容
     *
     * @param document Word文档对象
     * @param report   个人周报数据
     * @param index    序号
     */
    private void addPersonReport(XWPFDocument document, PersonalWeeklyReport report, int index) {
        // 添加分页，分隔不同人的周报内容
        this.addPageBreak(document);
        // 生成个人标题，如 "1.3.张三"
        this.addSubtitle(document, String.format("1.%d.%s", index, report.name()));

        // 定义固定的周报标题列表
        List<String> titles = Arrays.asList(
                "禅道需求",
                "重点任务完成情况",
                "测试任务完成情况",
                "远程实施维护情况(重点说明未解决的问题)",
                "未解决问题以及原因分析",
                "下周计划"
        );

        // 构建标题与内容的映射
        Map<String, StringBuilder> contentMap = new LinkedHashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            String key = String.format("1.%d.%d.%s", index, i + 1, titles.get(i));
            contentMap.put(key, new StringBuilder());
        }

        // 当前处理的标题键
        String currentKey = null;

        // 按行处理周报内容
        String[] lines = report.weeklyReportContent().split("\n");
        for (String line : lines) {
            // 检查是否是标题行
            boolean isTitle = false;
            for (String title : titles) {
                if (line.contains(title)) {
                    // 找到匹配的标题，更新当前键
                    String key = String.format("1.%d.%d.%s", index, titles.indexOf(title) + 1, title);
                    currentKey = key;
                    isTitle = true;
                    break;
                }
            }

            // 如果是标题行，跳过（已在key中包含）
            if (!isTitle && currentKey != null) {
                // 非标题行，添加到当前标题的内容
                contentMap.get(currentKey).append(line).append("\n");
            }
        }

        // 按顺序输出所有标题及其内容
        for (Map.Entry<String, StringBuilder> entry : contentMap.entrySet()) {
            String title = entry.getKey();
            String content = entry.getValue().toString();

            // 如果内容不为空或不只是换行符，则添加到文档
            if (CharSequenceUtil.isNotBlank(content) && !content.trim().isEmpty()) {
                this.processTitleAndContent(document, title + "\n" + content, title);
            } else {
                // 内容为空，只添加标题
                this.processTitleAndContent(document, title + "\n无", title);
            }
        }

    }

    /**
     * 添加评审会议纪要
     *
     * @param document Word文档对象
     * @param report   部门周报数据
     * @return
     **/
    private void addReviewContent(XWPFDocument document, DepartmentalWeeklyReport report) {
        String reviewContent = report.reviewContent();
        if (CharSequenceUtil.isNotBlank(reviewContent)){
            this.addPageBreak(document);
            this.addSubtitle(document, "评审会议纪要");
            this.addContentParagraph(document, reviewContent);
        }
    }


    /**
     * 处理标题及其内容
     *
     * @param document Word文档对象
     * @param content  标题及其内容
     * @param title    格式化后的标题
     */
    private void processTitleAndContent(XWPFDocument document, String content, String title) {
        String[] lines = content.split("\n", 2);
        if (lines.length > 0) {
            // 第一行作为标题行，加粗显示
            this.addParagraph(document, title);
            // 如果有内容，添加剩余行
            if (lines.length > 1) {
                // 剩余行作为内容行，普通格式
                String[] contentLines = lines[1].split("\n");
                for (String contentLine : contentLines) {
                    this.addContentParagraph(document, contentLine);
                }
            }
        }
    }

    /**
     * 添加正文内容
     *
     * @param document Word文档对象
     * @param text     段落文本
     */
    private void addContentParagraph(XWPFDocument document, String text) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(12);
        run.setFontFamily(CONTEXT_FONT);
        para.setSpacingBetween(1.2f);
    }

    /**
     * 添加会议信息段落
     *
     * @param document Word文档对象
     * @param text     段落文本
     */
    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(14);
        run.setFontFamily("NSimSun");
        run.setBold(true);
        para.setSpacingBetween(1.2f);
    }

    /**
     * 生成文档文件名
     *
     * @return 格式化后的文件名，包含日期信息
     */
    private String generateDocxFileName() {
        // 获取当周周五日期
        Date friday = this.getCurrentWeekFriday();
        return "hrp开发三部部门例会会议纪要_" +
                DateUtil.format(friday, "yyyyMMdd") + ".docx";
    }

    /**
     * 获取当前周的星期五日期
     *
     * @return 星期五的Date对象
     */
    private Date getCurrentWeekFriday() {
        // 获取当前日期
        Date today = new Date();

        // 获取当前周的最后一天（星期天）
        Date endOfWeek = DateUtil.endOfWeek(today);

        // 从星期天减去2天得到星期五
        return DateUtil.offsetDay(endOfWeek, -2);
    }

    /**
     * 在Word文档中添加分页符
     *
     * @param document 要添加分页符的XWPFDocument对象
     */
    private void addPageBreak(XWPFDocument document) {
        // 创建一个新段落用于分页
        XWPFParagraph breakPara = document.createParagraph();

        // 设置段落格式，避免显示任何内容
        breakPara.setAlignment(ParagraphAlignment.LEFT);
        breakPara.setSpacingAfter(0);
        breakPara.setSpacingBefore(0);

        // 创建运行块并添加分页符
        XWPFRun run = breakPara.createRun();
        run.addBreak(BreakType.PAGE);
    }

}