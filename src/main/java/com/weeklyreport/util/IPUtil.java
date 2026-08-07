package com.weeklyreport.util;

import cn.hutool.core.text.CharSequenceUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * IP 工具类（基于 ip2region 离线库）
 *
 * <p>能力：
 * <ol>
 *     <li>根据 HttpServletRequest 获取真实客户端 IP 的归属地 / 运营商等信息（内置 IP 提取，支持多级代理，不依赖其它工具类）</li>
 *     <li>根据任意 IP 返回归属地、运营商等信息</li>
 * </ol>
 *
 * <p>依赖：org.lionsoul:ip2region:3.3.7。数据文件需放在 classpath 根目录：
 * <ul>
 *     <li>ip2region_v4.xdb（IPv4，必选）</li>
 *     <li>ip2region_v6.xdb（IPv6，可选，缺失时仅 IPv4 可用）</li>
 * </ul>
 * 数据字符串格式（新版）：国家|省|市|运营商|国家代码，未知字段用 "0" 表示。
 */
public class IPUtil {

    /** classpath 下的 xdb 数据文件名 */
    private static final String V4_DB = "ip2region_v4.xdb";
    private static final String V6_DB = "ip2region_v6.xdb";

    /** 全局并发安全的查询对象（整库缓存模式） */
    private static volatile Searcher v4Searcher;
    private static volatile Searcher v6Searcher;
    private static volatile boolean initialized = false;

    /**
     * 懒加载并缓存 Searcher，避免 xdb 缺失时影响整个应用启动。
     */
    private static void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (IPUtil.class) {
            if (initialized) {
                return;
            }
            v4Searcher = loadSearcher(V4_DB, Version.IPv4);
            v6Searcher = loadSearcher(V6_DB, Version.IPv6);
            initialized = true;
        }
    }

    /**
     * 从 classpath 读取 xdb，拷贝到临时文件后整库载入内存。
     * 临时文件仅用于初始化，载入后即可删除（数据已在内存中）。
     *
     * @return 成功返回 Searcher，失败（文件缺失 / 载入异常）返回 null
     */
    private static Searcher loadSearcher(String dbName, Version version) {
        try (InputStream is = IPUtil.class.getClassLoader().getResourceAsStream(dbName)) {
            if (is == null) {
                // 缺失某一份 xdb 不影响另一份（例如没有 v6 数据时 IPv4 仍可用）
                return null;
            }
            Path tmp = Files.createTempFile("ip2region_" + dbName, ".xdb");
            try {
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                // loadContentFromFile 将整库读入内存并返回 LongByteArray
                var cBuff = Searcher.loadContentFromFile(tmp.toString());
                // newWithBuffer 创建的查询对象可安全用于并发，可作为全局对象
                return Searcher.newWithBuffer(version, cBuff);
            } finally {
                // 数据已在内存，删除临时文件避免残留
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            System.err.println("IPUtil 加载 ip2region 数据失败 (" + dbName + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * 从请求中提取真实客户端 IP（支持多级代理）。
     * 依次尝试 X-Forwarded-For、Proxy-Client-IP、WL-Proxy-Client-IP，最后回退到对端地址。
     */
    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 1. 根据请求获取真实 IP 的归属地 / 运营商等信息。
     *
     * @param request 当前请求
     * @return IP 信息（含归属地、运营商等）
     */
    public static IpInfo getIpInfo(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        return getIpInfo(clientIp);
    }

    /**
     * 判断是否为环回地址（127.x.x.x 或 ::1 等本机地址）。
     * 使用 {@link InetAddress#isLoopbackAddress()} 可同时覆盖 IPv4 / IPv6 各种写法。
     */
    private static boolean isLoopback(String ip) {
        try {
            return InetAddress.getByName(ip).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 2. 根据 IP 返回归属地、运营商等信息。
     *
     * @param ip IPv4 或 IPv6 地址
     * @return IP 信息（含归属地、运营商等）；查询失败 / 库未就绪时返回未知信息
     */
    public static IpInfo getIpInfo(String ip) {
        ensureInit();
        if (CharSequenceUtil.isBlank(ip)) {
            return IpInfo.unknown(ip);
        }
        // 环回地址（本机）直接返回友好提示，无需查库
        if (isLoopback(ip)) {
            return IpInfo.localhost(ip);
        }
        boolean isIpv6 = ip.contains(":");
        Searcher searcher = isIpv6 ? v6Searcher : v4Searcher;
        if (searcher == null) {
            return IpInfo.unknown(ip);
        }
        try {
            String region = searcher.search(ip);
            return IpInfo.fromRegion(ip, region);
        } catch (Exception e) {
            return IpInfo.unknown(ip);
        }
    }

    /**
     * IP 查询结果（归属地、运营商等结构化信息）。
     * 可直接作为 JSON 返回（Spring 使用 Jackson 序列化 record）。
     */
    public record IpInfo(
            String ip,
            String country,
            String province,
            String city,
            String isp,
            String countryCode,
            String address,
            String rawRegion
    ) {
        /** 未知 / 查询失败时的占位结果 */
        public static IpInfo unknown(String ip) {
            return new IpInfo(ip, "", "", "", "", "", "未知", "");
        }

        /** 环回地址（本机 / localhost）的友好结果 */
        public static IpInfo localhost(String ip) {
            return new IpInfo(ip, "", "", "", "", "", "本机/localhost", "");
        }

        /** 由 ip2region 返回的 region 字符串解析为结构化信息 */
        public static IpInfo fromRegion(String ip, String region) {
            if (region == null || region.isBlank()) {
                return unknown(ip);
            }
            String[] parts = region.split("\\|");
            String country = field(parts, 0);
            String province = field(parts, 1);
            String city = field(parts, 2);
            String isp = field(parts, 3);
            String countryCode = field(parts, 4);
            String address = buildAddress(country, province, city);
            return new IpInfo(ip, country, province, city, isp, countryCode, address, region);
        }

        private static String field(String[] parts, int idx) {
            if (idx < parts.length) {
                String v = parts[idx];
                // ip2region 用 "0" 表示未知字段
                return ("0".equals(v) || v.isBlank()) ? "" : v;
            }
            return "";
        }

        /** 拼接归属地：国家 + 省 + 市（自动跳过空值） */
        private static String buildAddress(String country, String province, String city) {
            StringBuilder sb = new StringBuilder();
            if (!country.isEmpty()) {
                sb.append(country);
            }
            if (!province.isEmpty()) {
                sb.append(province);
            }
            if (!city.isEmpty()) {
                sb.append(city);
            }
            return sb.length() == 0 ? "未知" : sb.toString();
        }
    }
}
