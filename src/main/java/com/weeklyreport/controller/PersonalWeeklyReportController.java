package com.weeklyreport.controller;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.weeklyreport.common.BaseResponse;
import com.weeklyreport.common.ResultUtils;
import com.weeklyreport.entity.Erp;
import com.weeklyreport.exception.ErrorCode;
import com.weeklyreport.exception.ThrowUtils;
import com.weeklyreport.util.IPUtil;
import com.weeklyreport.util.PushUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


/**
 * @Author Lin
 * @Date 2026/4/20 19:20
 * @Descriptions ERP周报相关接口
 */
@Slf4j
@RestController
@RequestMapping("/personal")
@RequiredArgsConstructor
public class PersonalWeeklyReportController {

    /**
     * ERP 基础 URL（配置外提，默认值与历史一致）
     */
    @Value("${erp.base-url:http://220.250.40.152:9392/}")
    private String baseApiUrl;

    /**
     * 外部配置文件路径
     */
    private static final String DATA_RAW_CONFIG_PATH = "dataRow/dataRow.json";

    /**
     * 任务列表请求体（默认配置）
     */
    private static final String DEFAULT_DATA_RAW = """
            {
                  "dataSetId": "07794dcb4e214e33a07ef96b2594268a",
                  "columns": [
            
                  ],
                  "formType": "",
                  "allTablesFlag": true,
                  "queryRequest": {
                      "pageSize": 20,
                      "pageNum": 1,
                      "sortOrder": "",
                      "sortField": ""
                  },
                  "searchFileNames": [
            
                  ],
                  "searchFileValues": [
            
                  ],
                  "searchRules": [
            
                  ],
                  "sumFields": [
            
                  ],
                  "avgFields": [
            
                  ],
                  "maxFields": [
            
                  ],
                  "minFields": [
            
                  ],
                  "countFields": [
            
                  ],
                  "allSearchColsFlag": false,
                  "allSearchColsValue": "",
                  "allSearchCols": [
            
                  ],
                  "componentKeyName": "主表格",
                  "args": "0!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@null!@assigntome!@task_id!@desc",
                  "scriptName": "javaSearch_cmpId1736285135354_17174,javaSearch_cmpId1631792608000_44906",
                  "eventName": "Search",
                  "version": 22,
                  "onAction": "1"
              }
            """;

    /**
     * 实际使用的请求体（优先从外部配置读取，否则使用默认）
     */
    private String dataRaw;

    /**
     * 初始化时加载外部配置
     */
    @PostConstruct
    public void init() {
        this.dataRaw = this.loadDataRawFromConfig();
        log.info("DATA_RAW 加载完成，来源: {}", this.dataRaw.equals(DEFAULT_DATA_RAW) ? "内置默认配置" : "外部配置文件");
    }

    /**
     * 从外部配置文件加载DATA_RAW，读取不到则使用默认
     *
     * @return 请求体JSON字符串
     */
    private String loadDataRawFromConfig() {
        try {
            // 尝试从classpath读取
            ClassPathResource resource = new ClassPathResource(DATA_RAW_CONFIG_PATH);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("成功从外部配置文件加载DATA_RAW");
                    return content;
                }
            }
        } catch (Exception e) {
            log.warn("读取外部配置文件失败，将使用内置默认配置: {}", e.getMessage());
        }
        log.info("使用内置默认DATA_RAW");
        return DEFAULT_DATA_RAW;
    }

    // 周报模板
    private static final String WEEKLY_REPORT_TEMPLATE = """
            1.1.1.禅道需求
            无
            1.1.2.重点任务完成情况
            #{workContent}
            1.1.3.测试任务完成情况
            周末完成
            1.1.4.远程实施维护情况(重点说明未解决的问题)
            见ERP问题管理
            1.1.5.未解决问题以及原因分析
            无
            1.1.6.下周计划
            禅道需求
            """;


    /**
     * 根据用户名密码获取周报详情
     *
     * @param erp 包含用户名和密码的请求实体
     * @return 周报详情
     */
    @PostMapping
    public BaseResponse<?> getPersonalWeeklyReport(@RequestBody Erp erp, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(erp == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(erp.username()), ErrorCode.PARAMS_ERROR, "用户名不能为空");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(erp.password()), ErrorCode.PARAMS_ERROR, "密码不能为空");

        log.info("收到周报查询请求，用户名: {}", erp.username());

        // 1. 登录获取token（失败由 ThrowUtils 抛 BusinessException，统一交全局异常处理器响应）
        Pair<String, String> loginResult = this.login(erp.username(), erp.password());
        ThrowUtils.throwIf(loginResult == null, ErrorCode.OPERATION_ERROR, "登录失败");
        String token = loginResult.getKey();
        String trueName = loginResult.getValue();
        ThrowUtils.throwIf(token == null, ErrorCode.PARAMS_ERROR, "登录失败，请检查用户名密码");

        // 2. 请求任务列表
        String taskResponse = this.requestTaskList(token);
        ThrowUtils.throwIf(taskResponse == null, ErrorCode.SYSTEM_ERROR, "获取任务列表失败");

        // 3. 处理响应内容，提取本周任务
        String workContent = this.getWorkContent(taskResponse);
        workContent = CharSequenceUtil.isBlank(workContent) ? "无" : workContent;
        String weeklyReportContent = WEEKLY_REPORT_TEMPLATE.replace("#{workContent}", workContent);
        // 4. 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("username", erp.username());
        result.put("weeklyReportContent", weeklyReportContent);
        result.put("queryTime", DateUtil.now());

        log.info("周报查询成功: {}", result);

        // 发送推送通知（使用 IPUtil 获取 IP 信息，PushUtil 推送；失败不影响主流程）
        try {
            IPUtil.IpInfo ipInfo = IPUtil.getIpInfo(request);
            PushUtil.pushIpInfo(trueName + " 个人周报查询", ipInfo);
        } catch (Exception pushEx) {
            log.error("推送通知失败", pushEx);
        }

        return ResultUtils.success(result);
    }

    /**
     * 登录方法
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回token，失败返回null
     */
    private Pair<String, String> login(String username, String password) {
        String businessTime = DateUtil.now().formatted("yyyy-MM-dd");
        log.info("当前业务时间：{}", businessTime);
        String loginUrl = baseApiUrl + "api/login";
        HttpRequest request = HttpRequest.post(loginUrl)
                .header("Pragma", "no-cache")
                .header("currentContext", "ecanerp")
                .header("operateContext", "ecanerp")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Edg/137.0.0.0")
                .header("Accept", "*/*")
                .header("Host", "220.250.40.152:9392")
                .header("Connection", "keep-alive")
                .form("username", username)
                .form("password", SecureUtil.md5(password))
                .form("context", "ecanerp")
                .form("businessTime", businessTime)
                .form("businessOfficeId", "10000")
                .timeout(10000);
        try (HttpResponse response = request.execute()) {
            if (response.isOk()) {
                String responseBody = response.body();
                JSONObject rootObj = JSONUtil.parseObj(responseBody);
                boolean isSuccess = rootObj.getBool("success", false);
                if (!isSuccess) {
                    String errorMsg = rootObj.getStr("message", "未知错误");
                    log.error("登录请求返回失败：{}", errorMsg);
                    return null;
                }
                JSONObject dataObj = rootObj.getJSONObject("data");
                if (dataObj == null) {
                    log.error("响应结果中未找到data节点");
                    return null;
                }
                String token = dataObj.getStr("token");
                String userJson = dataObj.getStr("user");
                JSONObject userObj = JSONUtil.parseObj(userJson);
                String trueName = userObj.getStr("trueName");
                if (token != null && !token.isEmpty()) {
                    log.info("登录成功，Token：{}", token);
                    return Pair.of(token, trueName);
                } else {
                    log.error("登录响应中未找到token字段");
                    return null;
                }
            } else {
                log.error("登录请求失败，状态码：{}", response.getStatus());
                return null;
            }
        } catch (Exception e) {
            log.error("登录请求发生异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 发送请求获取任务列表
     *
     * @param token 登录获取的token
     * @return 任务列表响应内容
     */
    private String requestTaskList(String token) {
        String url = baseApiUrl + "api/business";
        try (HttpResponse response = HttpRequest.post(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Authentication", token)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Origin", baseApiUrl)
                .header("Pragma", "no-cache")
                .header("Referer", baseApiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Edg/137.0.0.0")
                .header("currentContext", "ecanerp")
                .header("menuId", "")
                .header("operateContext", "ecanerp")
                .header("pageId", "56f1714658dd44868c62bb05106601d5")
                .body(dataRaw)
                .execute()) {
            return response.body();
        } catch (Exception e) {
            log.error("发送请求失败", e);
            return null;
        }
    }

    /**
     * 处理响应内容，提取出本周的重点任务
     *
     * @param response 任务列表响应
     * @return 本周任务内容
     */
    private String getWorkContent(String response) {
        log.info("正在处理响应内容");
        StringBuilder workContent = new StringBuilder();
        JSONObject jsonResponse = JSONUtil.parseObj(response);

        LocalDate now = LocalDate.now();
        LocalDate startOfWeek = now.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = now.with(DayOfWeek.SUNDAY);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        if (jsonResponse.containsKey("data") && jsonResponse.getJSONObject("data").containsKey("rows")) {
            JSONArray rows = jsonResponse.getJSONObject("data").getJSONArray("rows");
            log.info("获取本周任务信息");
            this.getTaskInfo(rows, formatter, startOfWeek, endOfWeek, workContent);
        }

        if (!workContent.isEmpty()) {
            workContent.deleteCharAt(workContent.length() - 1);
        }
        return workContent.toString();
    }

    /**
     * 获取本周任务信息
     */
    private void getTaskInfo(JSONArray rows, DateTimeFormatter formatter, LocalDate startOfWeek, LocalDate endOfWeek, StringBuilder workContent) {
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String taskId = row.getStr("task_id");
            String taskName = row.getStr("task_name");
            String time = row.getStr("time");
            String deadlineStr = row.getStr("deadline");

            if (time != null && time.contains("/")) {
                time = time.split("/")[1];
            }

            if (deadlineStr != null) {
                try {
                    LocalDate deadline = LocalDate.parse(deadlineStr, formatter);
                    // 过滤掉 deadline 不在本周区间的数据
                    if (!deadline.isBefore(startOfWeek) && !deadline.isAfter(endOfWeek)) {
                        workContent.append("\t").append(taskId)
                                .append(" ").append(taskName)
                                .append(" (").append(time).append("h)\n");
                    }
                } catch (Exception e) {
                    log.error("日期解析异常", e);
                }
            }
        }
        log.info("获取本周任务信息成功");
    }
}