package com.weeklyreport.entity;


import java.util.List;

/**
 * 部门周报实体
 *
 * @param meetingAddress        会议地点
 * @param host                  主持人
 * @param reviewContent         评审内容
 * @param personalWeeklyReports 参会人员周报
 */
public record DepartmentalWeeklyReport(String meetingAddress, String host, String reviewContent,
                                       List<PersonalWeeklyReport> personalWeeklyReports) {
}
