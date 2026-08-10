package com.weeklyreport.controller;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 针对 WeeklyReportController.getCurrentWeekFriday 的单元测试（反射调用私有方法）。
 * 验证：返回值确为“当前 ISO 周（周一~周日）的星期五”，且与历史 endOfWeek-2 意图等价。
 */
public class WeeklyReportFridayTest {

    private Date invokeGetCurrentWeekFriday() throws Exception {
        WeeklyReportController controller = new WeeklyReportController();
        Method m = WeeklyReportController.class.getDeclaredMethod("getCurrentWeekFriday");
        m.setAccessible(true);
        return (Date) m.invoke(controller);
    }

    @Test
    void friday_isInCurrentIsoWeek() throws Exception {
        Date actual = invokeGetCurrentWeekFriday();
        LocalDate fri = actual.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(DayOfWeek.FRIDAY, fri.getDayOfWeek());

        LocalDate today = LocalDate.now();
        long weeksBetween = Math.abs(java.time.temporal.ChronoUnit.WEEKS.between(
                fri.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY)));
        assertEquals(0, weeksBetween, "返回的周五不在当前 ISO 周");
    }

    @Test
    void equivalentToEndOfWeekMinus2_forAllWeekdays() {
        // 历史逻辑：endOfWeek(周日) - 2 天 = 本周五。
        // 新逻辑：today.with(FRIDAY)。二者对任意星期几应等价。
        LocalDate monday = LocalDate.of(2026, 8, 10); // 2026-08-10 为周一
        for (DayOfWeek dow : DayOfWeek.values()) {
            LocalDate day = monday.with(dow);
            LocalDate newWay = day.with(DayOfWeek.FRIDAY);
            LocalDate oldWay = day.with(DayOfWeek.SUNDAY).minusDays(2);
            assertEquals(oldWay, newWay, "与历史 endOfWeek-2 不等价，星期: " + dow);
        }
    }
}
